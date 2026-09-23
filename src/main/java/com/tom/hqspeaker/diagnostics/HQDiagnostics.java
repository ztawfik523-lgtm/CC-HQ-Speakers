package com.tom.hqspeaker.diagnostics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-process runtime diagnostics for the real HQ audio pipeline.
 *
 * <p>The normal release carries this code, but it is dormant until explicitly enabled from the speaker peripheral.
 * The client and integrated server share these static structures in singleplayer, which lets ComputerCraft query
 * client/OpenAL evidence without adding another network payload to protocol v10.</p>
 */
public final class HQDiagnostics {
    public record SourceIdentity(
        UUID source,
        String kind,
        String group,
        int blockX,
        int blockY,
        int blockZ,
        int sampleRate,
        double contentStartSeconds
    ) {
        public SourceIdentity(
            UUID source, String kind, String group,
            int blockX, int blockY, int blockZ, int sampleRate
        ) {
            this(source, kind, group, blockX, blockY, blockZ, sampleRate, 0.0);
        }

        public SourceIdentity {
            if (source == null) throw new NullPointerException("source");
            kind = kind == null || kind.isBlank() ? "unknown" : kind;
            group = group == null ? "" : group;
            if (sampleRate < 0) sampleRate = 0;
            if (!Double.isFinite(contentStartSeconds) || contentStartSeconds < 0.0) contentStartSeconds = 0.0;
        }
    }

    /** One simultaneous OpenAL sample for one HQ source. */
    public record ChannelSample(
        SourceIdentity identity,
        String state,
        int queuedBuffers,
        int processedBuffers,
        long fixedSampleOffset,
        double secondsOffset,
        double outputLatencySeconds,
        float requestedX,
        float requestedY,
        float requestedZ,
        float actualX,
        float actualY,
        float actualZ,
        int directFilter,
        float directGain,
        float directGainHF
    ) {
        public ChannelSample {
            if (identity == null) throw new NullPointerException("identity");
            state = state == null ? "unknown" : state;
            if (!Double.isFinite(secondsOffset)) secondsOffset = -1.0;
            if (!Double.isFinite(outputLatencySeconds) || outputLatencySeconds < 0.0) {
                outputLatencySeconds = 0.0;
            }
        }
    }

    private static final AtomicBoolean ENABLED = new AtomicBoolean();
    private static final AtomicLong EPOCH = new AtomicLong();
    private static final AtomicLong RESET_NANOS = new AtomicLong(System.nanoTime());
    private static final AtomicLong SOUND_ENGINE_RELOADS = new AtomicLong();

    private static final ConcurrentHashMap<UUID, SourceMetrics> SOURCES = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, SyncMetrics> GROUPS = new ConcurrentHashMap<>();

    private static volatile boolean clientSeen;
    private static volatile boolean integratedServerSeen;
    private static volatile boolean sourceLatencyAvailable;
    private static volatile boolean soundPhysicsLoaded;
    private static volatile String openAlVendor = "";
    private static volatile String openAlRenderer = "";

    private HQDiagnostics() {}

    public static boolean enabled() {
        return ENABLED.get();
    }

    public static long setEnabled(boolean enabled) {
        boolean changed = ENABLED.getAndSet(enabled) != enabled;
        if (changed || enabled) return reset();
        return EPOCH.get();
    }

    public static long reset() {
        long epoch = EPOCH.incrementAndGet();
        RESET_NANOS.set(System.nanoTime());
        SOUND_ENGINE_RELOADS.set(0L);
        SOURCES.clear();
        GROUPS.clear();
        return epoch;
    }

    public static long epoch() {
        return EPOCH.get();
    }

    public static void updateClientCapabilities(
        boolean integratedServer,
        boolean latencyAvailable,
        boolean sprLoaded,
        String alVendor,
        String alRenderer
    ) {
        clientSeen = true;
        integratedServerSeen = integratedServer;
        sourceLatencyAvailable = latencyAvailable;
        soundPhysicsLoaded = sprLoaded;
        openAlVendor = alVendor == null ? "" : alVendor;
        openAlRenderer = alRenderer == null ? "" : alRenderer;
    }

    public static void soundEngineReloaded() {
        if (!enabled()) return;
        SOUND_ENGINE_RELOADS.incrementAndGet();
    }

    public static void registerSource(SourceIdentity identity) {
        if (!enabled() || identity == null) return;
        SOURCES.compute(identity.source(), (ignored, current) -> {
            if (current == null) return new SourceMetrics(identity);
            current.updateIdentity(identity);
            return current;
        });
        if (!identity.group().isBlank()) GROUPS.computeIfAbsent(identity.group(), ignored -> new SyncMetrics(identity.group()));
    }

    public static void channelStarted(SourceIdentity identity, int directFilter, float directGain, float directGainHF) {
        if (!enabled() || identity == null) return;
        registerSource(identity);
        SourceMetrics metrics = SOURCES.get(identity.source());
        if (metrics != null) metrics.channelStarted(directFilter, directGain, directGainHF);
    }

    public static void recordBatch(List<ChannelSample> samples) {
        recordBatch(epoch(), samples);
    }

    /**
     * Record one sound-executor sample batch only if it still belongs to the diagnostic epoch which scheduled it.
     *
     * <p>The OpenAL query runs asynchronously. A Lua test can reset diagnostics while an old query is already queued,
     * so the scheduling epoch must be checked here or that stale batch could repopulate a freshly-cleared snapshot.</p>
     */
    public static synchronized void recordBatch(long expectedEpoch, List<ChannelSample> samples) {
        if (!enabled() || expectedEpoch != epoch() || samples == null || samples.isEmpty()) return;
        long now = System.nanoTime();

        HashMap<String, ArrayList<ChannelSample>> grouped = new HashMap<>();
        for (ChannelSample sample : samples) {
            if (sample == null) continue;
            registerSource(sample.identity());
            SourceMetrics metrics = SOURCES.get(sample.identity().source());
            if (metrics != null) metrics.sample(sample, now);
            if (!sample.identity().group().isBlank()) {
                grouped.computeIfAbsent(sample.identity().group(), ignored -> new ArrayList<>()).add(sample);
            }
        }

        for (Map.Entry<String, ArrayList<ChannelSample>> entry : grouped.entrySet()) {
            SyncMetrics metrics = GROUPS.computeIfAbsent(entry.getKey(), SyncMetrics::new);
            metrics.sample(entry.getValue(), sourceCountForGroup(entry.getKey()));
        }
    }

    public static void pcmInput(UUID source, long bytes) {
        SourceMetrics metrics = active(source);
        if (metrics != null && bytes > 0L) metrics.pcmInput(bytes);
    }

    public static void pcmRead(UUID source, long dataBytes, long silenceBytes, boolean emptyRead) {
        SourceMetrics metrics = active(source);
        if (metrics != null) metrics.pcmRead(dataBytes, silenceBytes, emptyRead);
    }

    public static void pumpWake(UUID source) {
        SourceMetrics metrics = active(source);
        if (metrics != null) metrics.pumpWake();
    }

    public static void channelDetached(UUID source) {
        SourceMetrics metrics = active(source);
        if (metrics != null) metrics.channelDetached();
    }

    public static void decoderRestart(UUID source) {
        SourceMetrics metrics = active(source);
        if (metrics != null) metrics.decoderRestart();
    }

    public static void recoveryRejoin(UUID source) {
        SourceMetrics metrics = active(source);
        if (metrics != null) metrics.recoveryRejoin();
    }

    public static void decoderFailure(UUID source) {
        SourceMetrics metrics = active(source);
        if (metrics != null) metrics.decoderFailure();
    }

    public static void eof(UUID source) {
        SourceMetrics metrics = active(source);
        if (metrics != null) metrics.eof();
    }

    private static SourceMetrics active(UUID source) {
        if (!enabled() || source == null) return null;
        return SOURCES.get(source);
    }

    private static int sourceCountForGroup(String group) {
        int count = 0;
        for (SourceMetrics source : SOURCES.values()) {
            if (group.equals(source.group())) count++;
        }
        return count;
    }

    public static Map<String, Object> capabilities() {
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", enabled());
        out.put("epoch", epoch());
        out.put("clientSeen", clientSeen);
        out.put("integratedServer", integratedServerSeen);
        out.put("sourceLatency", sourceLatencyAvailable);
        out.put("soundPhysicsLoaded", soundPhysicsLoaded);
        out.put("openAlVendor", openAlVendor);
        out.put("openAlRenderer", openAlRenderer);
        out.put("transport", "in-process-singleplayer");
        return out;
    }

    public static Map<String, Object> snapshot() {
        long reset = RESET_NANOS.get();
        long now = System.nanoTime();

        ArrayList<SourceMetrics> sources = new ArrayList<>(SOURCES.values());
        sources.sort(Comparator
            .comparingInt(SourceMetrics::blockX)
            .thenComparingInt(SourceMetrics::blockY)
            .thenComparingInt(SourceMetrics::blockZ)
            .thenComparing(SourceMetrics::sourceString));

        ArrayList<Map<String, Object>> sourceRows = new ArrayList<>(sources.size());
        for (SourceMetrics source : sources) sourceRows.add(source.snapshot(reset));

        ArrayList<SyncMetrics> groups = new ArrayList<>(GROUPS.values());
        groups.sort(Comparator.comparing(SyncMetrics::group));
        ArrayList<Map<String, Object>> groupRows = new ArrayList<>(groups.size());
        for (SyncMetrics group : groups) groupRows.add(group.snapshot(SOURCES));

        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("enabled", enabled());
        out.put("epoch", epoch());
        out.put("elapsedMs", nanosToMillis(Math.max(0L, now - reset)));
        out.put("soundEngineReloads", SOUND_ENGINE_RELOADS.get());
        out.put("sourceCount", sourceRows.size());
        out.put("sources", sourceRows);
        out.put("groups", groupRows);
        out.put("capabilities", capabilities());
        return out;
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static double distance(
        float ax, float ay, float az,
        float bx, float by, float bz
    ) {
        double dx = (double) ax - bx;
        double dy = (double) ay - by;
        double dz = (double) az - bz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private static final class SourceMetrics {
        private SourceIdentity identity;
        private long channelStarts;
        private long firstChannelNanos;
        private long firstPlayingNanos;
        private long lastPlayingNanos;
        private long samples;
        private long playingSamples;
        private long pausedSamples;
        private long stoppedSamples;
        private int minQueued = Integer.MAX_VALUE;
        private int maxQueued;
        private int maxProcessed;
        private long lastFixedSampleOffset;
        private double lastSecondsOffset = -1.0;
        private double lastOutputLatencySeconds;
        private String lastState = "none";

        private boolean positionInitialized;
        private float firstRequestedX;
        private float firstRequestedY;
        private float firstRequestedZ;
        private float firstActualX;
        private float firstActualY;
        private float firstActualZ;
        private double requestedMovement;
        private double actualMovement;
        private double maxPositionError;

        private long pcmInputBytes;
        private long pcmReadBytes;
        private long silenceReadBytes;
        private long emptyReads;
        private long pumpWakes;
        private long channelDetaches;
        private long decoderRestarts;
        private long recoveryRejoins;
        private long decoderFailures;
        private long eofCount;

        private long soundPhysicsChannelStarts;
        private long soundPhysicsSamples;
        private long playingToStoppedTransitions;
        private int lastDirectFilter;
        private float lastDirectGain = 1.0f;
        private float lastDirectGainHF = 1.0f;
        private float minDirectGain = Float.POSITIVE_INFINITY;
        private float minDirectGainHF = Float.POSITIVE_INFINITY;
        private float maxDirectGain;
        private float maxDirectGainHF;

        private SourceMetrics(SourceIdentity identity) {
            this.identity = identity;
        }

        synchronized void updateIdentity(SourceIdentity next) {
            String currentGroup = identity.group();
            String nextGroup = next.group();
            if (nextGroup != null && !nextGroup.isBlank()) {
                // A new explicit group means a new playback/group identity for this physical source.
                identity = next;
                return;
            }
            // Preserve an existing useful group key across later RAW continuation packets with startTick=0.
            identity = new SourceIdentity(next.source(), next.kind(), currentGroup, next.blockX(), next.blockY(), next.blockZ(),
                next.sampleRate() > 0 ? next.sampleRate() : identity.sampleRate(), next.contentStartSeconds());
        }

        synchronized void channelStarted(int directFilter, float directGain, float directGainHF) {
            channelStarts++;
            if (firstChannelNanos == 0L) firstChannelNanos = System.nanoTime();
            if (directFilter != 0) soundPhysicsChannelStarts++;
            observeSoundPhysics(directFilter, directGain, directGainHF, false);
        }

        private void observeSoundPhysics(int directFilter, float directGain, float directGainHF, boolean sample) {
            lastDirectFilter = directFilter;
            lastDirectGain = directGain;
            lastDirectGainHF = directGainHF;
            if (directFilter != 0 && sample) soundPhysicsSamples++;
            if (Float.isFinite(directGain)) {
                minDirectGain = Math.min(minDirectGain, directGain);
                maxDirectGain = Math.max(maxDirectGain, directGain);
            }
            if (Float.isFinite(directGainHF)) {
                minDirectGainHF = Math.min(minDirectGainHF, directGainHF);
                maxDirectGainHF = Math.max(maxDirectGainHF, directGainHF);
            }
        }

        synchronized void sample(ChannelSample sample, long now) {
            samples++;
            if ("playing".equals(lastState) && "stopped".equals(sample.state())) {
                playingToStoppedTransitions++;
            }
            lastState = sample.state();
            switch (sample.state()) {
                case "playing" -> {
                    playingSamples++;
                    if (firstPlayingNanos == 0L) firstPlayingNanos = now;
                    lastPlayingNanos = now;
                }
                case "paused" -> pausedSamples++;
                case "stopped", "initial" -> stoppedSamples++;
                default -> { }
            }

            minQueued = Math.min(minQueued, Math.max(0, sample.queuedBuffers()));
            maxQueued = Math.max(maxQueued, Math.max(0, sample.queuedBuffers()));
            maxProcessed = Math.max(maxProcessed, Math.max(0, sample.processedBuffers()));
            lastFixedSampleOffset = sample.fixedSampleOffset();
            lastSecondsOffset = sample.secondsOffset();
            lastOutputLatencySeconds = sample.outputLatencySeconds();

            if (!positionInitialized) {
                positionInitialized = true;
                firstRequestedX = sample.requestedX();
                firstRequestedY = sample.requestedY();
                firstRequestedZ = sample.requestedZ();
                firstActualX = sample.actualX();
                firstActualY = sample.actualY();
                firstActualZ = sample.actualZ();
            }
            requestedMovement = Math.max(requestedMovement, distance(
                firstRequestedX, firstRequestedY, firstRequestedZ,
                sample.requestedX(), sample.requestedY(), sample.requestedZ()));
            actualMovement = Math.max(actualMovement, distance(
                firstActualX, firstActualY, firstActualZ,
                sample.actualX(), sample.actualY(), sample.actualZ()));
            maxPositionError = Math.max(maxPositionError, distance(
                sample.requestedX(), sample.requestedY(), sample.requestedZ(),
                sample.actualX(), sample.actualY(), sample.actualZ()));

            observeSoundPhysics(sample.directFilter(), sample.directGain(), sample.directGainHF(), true);
        }

        synchronized void pcmInput(long bytes) { pcmInputBytes += bytes; }

        synchronized void pcmRead(long dataBytes, long silenceBytes, boolean emptyRead) {
            if (dataBytes > 0L) pcmReadBytes += dataBytes;
            if (silenceBytes > 0L) silenceReadBytes += silenceBytes;
            if (emptyRead) emptyReads++;
        }

        synchronized void pumpWake() { pumpWakes++; }
        synchronized void channelDetached() { channelDetaches++; }
        synchronized void decoderRestart() { decoderRestarts++; }
        synchronized void recoveryRejoin() { recoveryRejoins++; }
        synchronized void decoderFailure() { decoderFailures++; }
        synchronized void eof() { eofCount++; }

        synchronized String group() { return identity.group(); }
        synchronized int blockX() { return identity.blockX(); }
        synchronized int blockY() { return identity.blockY(); }
        synchronized int blockZ() { return identity.blockZ(); }
        synchronized String sourceString() { return identity.source().toString(); }

        synchronized Map<String, Object> snapshot(long resetNanos) {
            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            out.put("source", identity.source().toString());
            out.put("kind", identity.kind());
            out.put("group", identity.group());
            out.put("blockX", identity.blockX());
            out.put("blockY", identity.blockY());
            out.put("blockZ", identity.blockZ());
            out.put("sampleRate", identity.sampleRate());
            out.put("contentStartSeconds", identity.contentStartSeconds());

            out.put("channelStarts", channelStarts);
            out.put("firstChannelMs", firstChannelNanos == 0L ? -1.0 : nanosToMillis(firstChannelNanos - resetNanos));
            out.put("firstPlayingMs", firstPlayingNanos == 0L ? -1.0 : nanosToMillis(firstPlayingNanos - resetNanos));
            out.put("lastPlayingMs", lastPlayingNanos == 0L ? -1.0 : nanosToMillis(lastPlayingNanos - resetNanos));
            out.put("samples", samples);
            out.put("playingSamples", playingSamples);
            out.put("pausedSamples", pausedSamples);
            out.put("stoppedSamples", stoppedSamples);
            out.put("playingToStoppedTransitions", playingToStoppedTransitions);
            out.put("lastState", lastState);
            out.put("minQueuedBuffers", minQueued == Integer.MAX_VALUE ? 0 : minQueued);
            out.put("maxQueuedBuffers", maxQueued);
            out.put("maxProcessedBuffers", maxProcessed);
            out.put("lastFixedSampleOffset", lastFixedSampleOffset);
            out.put("lastSecondsOffset", lastSecondsOffset);
            out.put("lastLogicalSeconds", lastSecondsOffset < 0.0 ? -1.0 : identity.contentStartSeconds() + lastSecondsOffset);
            out.put("lastOutputLatencySeconds", lastOutputLatencySeconds);

            out.put("requestedMovement", requestedMovement);
            out.put("actualMovement", actualMovement);
            out.put("maxPositionError", maxPositionError);

            out.put("pcmInputBytes", pcmInputBytes);
            out.put("pcmReadBytes", pcmReadBytes);
            out.put("silenceReadBytes", silenceReadBytes);
            out.put("emptyReads", emptyReads);
            out.put("pumpWakes", pumpWakes);
            out.put("channelDetaches", channelDetaches);
            out.put("decoderRestarts", decoderRestarts);
            out.put("recoveryRejoins", recoveryRejoins);
            out.put("decoderFailures", decoderFailures);
            out.put("eofCount", eofCount);

            out.put("soundPhysicsProcessed", soundPhysicsChannelStarts > 0L || soundPhysicsSamples > 0L);
            out.put("soundPhysicsChannelStarts", soundPhysicsChannelStarts);
            out.put("soundPhysicsSamples", soundPhysicsSamples);
            out.put("directFilter", lastDirectFilter);
            out.put("directGain", lastDirectGain);
            out.put("directGainHF", lastDirectGainHF);
            out.put("minDirectGain", minDirectGain == Float.POSITIVE_INFINITY ? 1.0 : minDirectGain);
            out.put("maxDirectGain", maxDirectGain);
            out.put("minDirectGainHF", minDirectGainHF == Float.POSITIVE_INFINITY ? 1.0 : minDirectGainHF);
            out.put("maxDirectGainHF", maxDirectGainHF);
            double gainRange = minDirectGain == Float.POSITIVE_INFINITY ? 0.0 : Math.max(0.0, maxDirectGain - minDirectGain);
            double gainHFRange = minDirectGainHF == Float.POSITIVE_INFINITY ? 0.0 : Math.max(0.0, maxDirectGainHF - minDirectGainHF);
            out.put("directGainRange", gainRange);
            out.put("directGainHFRange", gainHFRange);
            out.put("soundPhysicsChanged", gainRange > 1.0e-4 || gainHFRange > 1.0e-4);
            return out;
        }
    }

    private static final class SyncMetrics {
        private final String group;
        private long batches;
        private long completeBatches;
        private long mixedStateBatches;
        private double maxSecondsOffsetSpreadMs;
        private double maxAudibleOffsetSpreadMs;
        private double maxLogicalOffsetSpreadMs;
        private double maxLogicalAudibleOffsetSpreadMs;

        private SyncMetrics(String group) {
            this.group = group;
        }

        String group() { return group; }

        synchronized void sample(List<ChannelSample> samples, int expectedSources) {
            batches++;
            if (samples.size() < expectedSources || expectedSources < 2) return;
            completeBatches++;

            String state = samples.getFirst().state();
            boolean mixed = false;
            for (ChannelSample sample : samples) {
                if (!state.equals(sample.state())) {
                    mixed = true;
                    break;
                }
            }
            if (mixed) mixedStateBatches++;

            ArrayList<ChannelSample> playing = new ArrayList<>();
            for (ChannelSample sample : samples) {
                if ("playing".equals(sample.state()) && sample.secondsOffset() >= 0.0) playing.add(sample);
            }
            if (playing.size() < 2) return;

            double minOffset = Double.POSITIVE_INFINITY;
            double maxOffset = Double.NEGATIVE_INFINITY;
            double minAudible = Double.POSITIVE_INFINITY;
            double maxAudible = Double.NEGATIVE_INFINITY;
            double minLogical = Double.POSITIVE_INFINITY;
            double maxLogical = Double.NEGATIVE_INFINITY;
            double minLogicalAudible = Double.POSITIVE_INFINITY;
            double maxLogicalAudible = Double.NEGATIVE_INFINITY;
            for (ChannelSample sample : playing) {
                minOffset = Math.min(minOffset, sample.secondsOffset());
                maxOffset = Math.max(maxOffset, sample.secondsOffset());
                double audible = sample.secondsOffset() - sample.outputLatencySeconds();
                minAudible = Math.min(minAudible, audible);
                maxAudible = Math.max(maxAudible, audible);

                double logical = sample.identity().contentStartSeconds() + sample.secondsOffset();
                double logicalAudible = logical - sample.outputLatencySeconds();
                minLogical = Math.min(minLogical, logical);
                maxLogical = Math.max(maxLogical, logical);
                minLogicalAudible = Math.min(minLogicalAudible, logicalAudible);
                maxLogicalAudible = Math.max(maxLogicalAudible, logicalAudible);
            }
            maxSecondsOffsetSpreadMs = Math.max(maxSecondsOffsetSpreadMs, (maxOffset - minOffset) * 1000.0);
            maxAudibleOffsetSpreadMs = Math.max(maxAudibleOffsetSpreadMs, (maxAudible - minAudible) * 1000.0);
            maxLogicalOffsetSpreadMs = Math.max(maxLogicalOffsetSpreadMs, (maxLogical - minLogical) * 1000.0);
            maxLogicalAudibleOffsetSpreadMs = Math.max(
                maxLogicalAudibleOffsetSpreadMs, (maxLogicalAudible - minLogicalAudible) * 1000.0);
        }

        synchronized Map<String, Object> snapshot(ConcurrentHashMap<UUID, SourceMetrics> sources) {
            ArrayList<SourceMetrics> members = new ArrayList<>();
            for (SourceMetrics source : sources.values()) {
                if (group.equals(source.group())) members.add(source);
            }

            double firstMin = Double.POSITIVE_INFINITY;
            double firstMax = Double.NEGATIVE_INFINITY;
            double channelMin = Double.POSITIVE_INFINITY;
            double channelMax = Double.NEGATIVE_INFINITY;
            long readMin = Long.MAX_VALUE;
            long readMax = Long.MIN_VALUE;
            int playingMembers = 0;
            int channelMembers = 0;
            for (SourceMetrics member : members) {
                Map<String, Object> row = member.snapshot(RESET_NANOS.get());
                double channel = ((Number) row.get("firstChannelMs")).doubleValue();
                if (channel >= 0.0) {
                    channelMin = Math.min(channelMin, channel);
                    channelMax = Math.max(channelMax, channel);
                    channelMembers++;
                }
                double first = ((Number) row.get("firstPlayingMs")).doubleValue();
                if (first >= 0.0) {
                    firstMin = Math.min(firstMin, first);
                    firstMax = Math.max(firstMax, first);
                    playingMembers++;
                }
                long read = ((Number) row.get("pcmReadBytes")).longValue();
                readMin = Math.min(readMin, read);
                readMax = Math.max(readMax, read);
            }

            LinkedHashMap<String, Object> out = new LinkedHashMap<>();
            out.put("group", group);
            out.put("sourceCount", members.size());
            out.put("playingMembers", playingMembers);
            out.put("channelMembers", channelMembers);
            out.put("batches", batches);
            out.put("completeBatches", completeBatches);
            out.put("mixedStateBatches", mixedStateBatches);
            out.put("channelStartSkewMs", channelMembers >= 2 ? channelMax - channelMin : -1.0);
            out.put("startSkewMs", playingMembers >= 2 ? firstMax - firstMin : -1.0);
            out.put("maxSecondsOffsetSpreadMs", maxSecondsOffsetSpreadMs);
            out.put("maxAudibleOffsetSpreadMs", maxAudibleOffsetSpreadMs);
            out.put("maxLogicalOffsetSpreadMs", maxLogicalOffsetSpreadMs);
            out.put("maxLogicalAudibleOffsetSpreadMs", maxLogicalAudibleOffsetSpreadMs);
            out.put("pcmReadBytesSpread", members.size() >= 2 ? Math.max(0L, readMax - readMin) : 0L);
            return out;
        }
    }
}
