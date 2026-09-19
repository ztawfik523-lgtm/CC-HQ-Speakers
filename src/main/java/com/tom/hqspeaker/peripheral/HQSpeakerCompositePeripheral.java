package com.tom.hqspeaker.peripheral;

import com.tom.hqspeaker.network.HQSpeakerAudioPacket;
import dan200.computercraft.api.lua.IArguments;
import dan200.computercraft.api.lua.ILuaContext;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.lua.MethodResult;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IDynamicPeripheral;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.core.asm.PeripheralMethodSupplier;
import dan200.computercraft.core.methods.MethodSupplier;
import dan200.computercraft.core.methods.PeripheralMethod;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral;

import javax.annotation.Nullable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * The physical CC speaker exposed to Lua.
 *
 * Standard speaker calls stay on CC:T's original SpeakerPeripheral. HQ-specific continuous sources have one
 * explicit owner so old/prepared finite state cannot accidentally capture controls belonging to a later source.
 * ComputerCraft may call this peripheral from multiple computer threads, so ownership-changing entry points are
 * synchronized to keep stop -> start -> owner changes in one order.
 */
public final class HQSpeakerCompositePeripheral implements IDynamicPeripheral {
    private static final MethodSupplier<PeripheralMethod> METHOD_SUPPLIER = PeripheralMethodSupplier.create(List.of());
    private static final Set<String> STANDARD = Set.of("playNote", "playSound", "playAudio", "stop");
    private static final Set<String> FINITE_CONTROLS = Set.of(
        "audioStatus", "audioPause", "audioResume", "audioSeek", "audioSetVolume", "audioSetLooping", "audioStop"
    );
    private static final Set<String> FINITE_START = Set.of(
        "speakMp3", "speakOgg", "speakAudio", "speakFile", "speakPacked", "speakWav"
    );
    private static final Set<String> RAW_START = Set.of("speakPCM");
    private static final Set<String> STREAM_START = Set.of("speakStream", "speakHLS", "speakTS");
    private static final Set<String> STREAM_DIRECT = Set.of(
        "speakStreamAll", "speakHLSAll", "speakTSAll", "speakStreamAt", "speakHLSAt", "speakTSAt"
    );
    private static final Set<String> FINITE_ALL_CONTROLS = Set.of(
        "audioStatusAll", "audioPauseAll", "audioResumeAll", "audioSeekAll",
        "audioSetVolumeAll", "audioSetLoopingAll", "audioStopAll"
    );
    private static final Set<String> FINITE_AT_CONTROLS = Set.of(
        "audioStatusAt", "audioPauseAt", "audioResumeAt", "audioSeekAt",
        "audioSetVolumeAt", "audioSetLoopingAt", "audioStopAt"
    );
    /** Dynamic calls which observe state/capabilities but do not supersede an in-flight stream start. */
    private static final Set<String> READ_ONLY_DYNAMIC = Set.of(
        "audioStatus", "audioStatusAll", "audioStatusAt",
        "getPeripheralType", "getPos", "getSpeakerCount",
        "getStreamArtist", "getStreamFormats", "getStreamGenre", "getStreamMeta", "getStreamMetaSerial",
        "getStreamSong", "getStreamStation", "getStreamTitle", "getStreamUrl", "isStreaming",
        "speakIsPlaying", "speakMaxAudioBytes", "speakMaxFileBytes", "speakMaxOggBytes", "speakMaxSamples",
        "speakQueueSize", "speakSampleRate", "speakSupportedFiles"
    );
    private static final String[] SUPPORTED_FINITE_FILES = { "wav", "ogg", "mp3", "aiff", "aif", "au", "snd" };

    /** Exact inherited single-speaker RAW limits. */
    private static final int HQ_RAW_MAX_SAMPLES = 131_072;
    private static final int HQ_RAW_QUEUE_LIMIT = 16;
    /** One maximum speakPCM call plus 100 ms so the next packet can arrive before the previous chunk runs dry. */
    private static final long HQ_RAW_BUFFER_SAMPLES = HQ_RAW_MAX_SAMPLES + 2L * RawFeedLifetime.SAMPLES_PER_TICK;

    private static final Set<HQSpeakerCompositePeripheral> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Map<Integer, Set<HQSpeakerCompositePeripheral>> COMPUTER_SPEAKERS = new ConcurrentHashMap<>();

    private enum Owner {
        NONE,
        RAW,
        LEGACY_FINITE,
        STAGED_FINITE,
        STREAM
    }

    private final HQSpeakerPeripheral legacy;
    private final SpeakerPeripheral vanilla;
    private final HQFiniteMediaServer finite;
    private final HQMediaStaging staging;
    private final Map<String, PeripheralMethod> legacyMethods;
    private final String[] dynamicNames;
    private final Map<IComputerAccess, IComputerAccess> legacyComputerViews = new ConcurrentHashMap<>();
    /** Requested sample count for each computer currently waiting for RAW capacity. */
    private final Map<IComputerAccess, Integer> rawCapacityWaiters = new ConcurrentHashMap<>();
    private final RawFeedLifetime rawLifetime = new RawFeedLifetime();
    /**
     * Serializes normal Lua audio command commits without coupling them to the ownership monitor used by
     * server tick/cleanup. Blocking URL validation must never hold this lock: audioPlayPrepared is a CC:T
     * main-thread method, so letting DNS hold commandLock could otherwise stall the Minecraft server indirectly.
     */
    private final Object commandLock = new Object();
    /**
     * Monotonic mutation sequence used to reject a normal single-speaker stream start whose DNS validation finishes
     * after a newer playback/control command has already been issued.
     */
    private final AtomicLong commandRevision = new AtomicLong();

    private volatile Owner owner = Owner.NONE;

    public HQSpeakerCompositePeripheral(HQSpeakerPeripheral legacy, SpeakerPeripheral vanilla,
                                        HQFiniteMediaServer finite, HQMediaStaging staging) {
        this.legacy = legacy;
        this.vanilla = vanilla;
        this.finite = finite;
        this.staging = staging;
        this.legacyMethods = METHOD_SUPPLIER.getSelfMethods(legacy);
        LinkedHashSet<String> names = new LinkedHashSet<>(legacyMethods.keySet());
        names.addAll(STANDARD);
        dynamicNames = names.toArray(String[]::new);
        ACTIVE.add(this);
    }

    @Override public String getType() { return "speaker"; }
    @Override public boolean equals(@Nullable IPeripheral other) { return this == other; }
    @Override public String[] getMethodNames() { return dynamicNames.clone(); }

    boolean usesVanilla(SpeakerPeripheral candidate) { return vanilla == candidate; }

    /** Tick only ownership/lifecycle state. Audio dispatch remains in the existing server implementations. */
    public static void tickAll() {
        for (HQSpeakerCompositePeripheral peripheral : ACTIVE) peripheral.tickOwnership();
    }

    private synchronized void tickOwnership() {
        if (owner != Owner.RAW) return;

        boolean queueHasData = legacy.speakIsPlaying();

        if (rawLifetime.tick(queueHasData)) {
            legacy.speakStop();
            rawCapacityWaiters.clear();
            rawLifetime.clear();
            owner = Owner.NONE;
            return;
        }

        if (!rawCapacityWaiters.isEmpty() && legacy.speakQueueSize() < HQ_RAW_QUEUE_LIMIT) {
            for (Map.Entry<IComputerAccess, Integer> entry : rawCapacityWaiters.entrySet()) {
                IComputerAccess computer = entry.getKey();
                int requestedSamples = entry.getValue();
                if (requestedSamples > 0
                        && rawLifetime.canAccept(requestedSamples, HQ_RAW_BUFFER_SAMPLES)
                        && rawCapacityWaiters.remove(computer, requestedSamples)) {
                    computer.queueEvent("hqspeaker_audio_empty", computer.getAttachmentName());
                }
            }
        }
    }

    @Override
    public void attach(IComputerAccess computer) {
        vanilla.attach(computer);
        staging.attach(computer);
        finite.attach(computer);
        COMPUTER_SPEAKERS.computeIfAbsent(computer.getID(), ignored -> ConcurrentHashMap.newKeySet()).add(this);
        IComputerAccess filtered = legacyComputerViews.computeIfAbsent(computer, this::filteredLegacyAccess);
        legacy.attach(filtered);
    }

    @Override
    public void detach(IComputerAccess computer) {
        rawCapacityWaiters.remove(computer);
        finite.detach(computer);
        staging.detach(computer);
        unregisterComputer(computer.getID(), this);
        IComputerAccess filtered = legacyComputerViews.remove(computer);
        if (filtered != null) legacy.detach(filtered);
        vanilla.detach(computer);
    }

    public synchronized void cleanup() {
        ACTIVE.remove(this);
        for (IComputerAccess computer : new ArrayList<>(legacyComputerViews.keySet())) {
            unregisterComputer(computer.getID(), this);
        }
        rawCapacityWaiters.clear();
        owner = Owner.NONE;
        rawLifetime.clear();
        finite.cleanup();
        staging.cleanup();
        legacy.cleanup();
        legacyComputerViews.clear();
    }

    private static void unregisterComputer(int computerId, HQSpeakerCompositePeripheral peripheral) {
        Set<HQSpeakerCompositePeripheral> members = COMPUTER_SPEAKERS.get(computerId);
        if (members == null) return;
        members.remove(peripheral);
        if (members.isEmpty()) COMPUTER_SPEAKERS.remove(computerId, members);
    }

    private static List<HQSpeakerCompositePeripheral> membersFor(IComputerAccess computer) {
        Set<HQSpeakerCompositePeripheral> members = COMPUTER_SPEAKERS.get(computer.getID());
        if (members == null || members.isEmpty()) return List.of();
        ArrayList<HQSpeakerCompositePeripheral> out = new ArrayList<>(members);
        out.sort(Comparator
            .comparingInt((HQSpeakerCompositePeripheral p) -> p.finite.position().getX())
            .thenComparingInt(p -> p.finite.position().getY())
            .thenComparingInt(p -> p.finite.position().getZ()));
        return out;
    }

    private static HQSpeakerCompositePeripheral memberAt(IComputerAccess computer, int index) throws LuaException {
        List<HQSpeakerCompositePeripheral> members = membersFor(computer);
        if (index < 1 || index > members.size()) throw new LuaException("speaker index out of range");
        return members.get(index - 1);
    }

    private IComputerAccess filteredLegacyAccess(IComputerAccess delegate) {
        return (IComputerAccess) Proxy.newProxyInstance(
            IComputerAccess.class.getClassLoader(), new Class<?>[]{ IComputerAccess.class },
            (proxy, method, args) -> {
                if ("queueEvent".equals(method.getName()) && args != null && args.length > 0
                        && "speaker_audio_empty".equals(args[0])) return null;
                try {
                    return method.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
            });
    }

    @LuaFunction
    public final String audioMountPath(IComputerAccess computer) throws LuaException {
        return staging.mountPath(computer);
    }

    @LuaFunction
    public final String audioPrepareStaged(IComputerAccess computer, String path,
                                           Optional<Boolean> consume) throws LuaException {
        return staging.prepareAsset(computer, path, consume.orElse(true));
    }

    @LuaFunction
    public final Map<String, Object> audioPreparedInfo(String assetId) throws LuaException {
        return staging.preparedInfo(assetId);
    }

    @LuaFunction(mainThread = true)
    public final boolean audioPlayPrepared(String assetId, Optional<Double> volume) throws LuaException {
        // This direct CC:T main-thread method must invalidate any older stream still blocked in DNS before it waits
        // for the short command commit lock.
        commandRevision.incrementAndGet();
        synchronized (commandLock) {
            synchronized (this) {
                try (HQFiniteMediaServer.PreparedStart prepared =
                         finite.preparePreparedStart(assetId, volume.orElse(1.0))) {
                    beginReplacingHQ(Owner.STAGED_FINITE);
                    if (!finite.commitPreparedStart(prepared)) {
                        throw new IllegalStateException("admitted prepared replacement could not be committed");
                    }
                    owner = Owner.STAGED_FINITE;
                    return true;
                }
            }
        }
    }

    @LuaFunction(mainThread = true)
    public final boolean audioPlayPreparedAll(IComputerAccess computer, String assetId,
                                              Optional<Double> volume) throws LuaException {
        List<HQSpeakerCompositePeripheral> members = membersFor(computer);
        if (members.isEmpty()) members = List.of(this);

        for (HQSpeakerCompositePeripheral member : members) member.commandRevision.incrementAndGet();
        List<HQFiniteMediaServer> targets = members.stream().map(member -> member.finite).toList();

        try (HQFiniteMediaServer.PreparedGroupStart prepared =
                 finite.preparePreparedGroupStart(targets, assetId, volume.orElse(1.0))) {
            // Admission and asset retention succeeded for the complete snapshot before any current output is replaced.
            for (HQSpeakerCompositePeripheral member : members) {
                synchronized (member.commandLock) {
                    synchronized (member) {
                        member.beginReplacingHQ(Owner.STAGED_FINITE);
                    }
                }
            }

            if (!HQFiniteMediaServer.commitPreparedGroupStart(prepared)) {
                throw new IllegalStateException("admitted prepared multispeaker replacement could not be committed");
            }
            for (HQSpeakerCompositePeripheral member : members) {
                synchronized (member) {
                    member.owner = Owner.STAGED_FINITE;
                }
            }
            return true;
        }
    }

    @LuaFunction
    public final boolean audioSetMuted(boolean muted) {
        commandRevision.incrementAndGet();
        synchronized (commandLock) {
            synchronized (this) {
                return owner == Owner.STAGED_FINITE && finite.setMuted(muted);
            }
        }
    }

    @LuaFunction
    public final boolean audioSetMutedAll(IComputerAccess computer, boolean muted) {
        commandRevision.incrementAndGet();
        synchronized (commandLock) {
            synchronized (this) {
                if (owner != Owner.STAGED_FINITE) return false;
            }

            boolean changed = false;
            for (HQSpeakerCompositePeripheral member : membersFor(computer)) {
                synchronized (member) {
                    if (member.owner == Owner.STAGED_FINITE && finite.sharesPlaybackWith(member.finite)) {
                        changed = member.finite.setMuted(muted) || changed;
                    }
                }
            }
            return changed;
        }
    }

    @LuaFunction
    public final boolean audioSetMutedAt(IComputerAccess computer, int index, boolean muted) throws LuaException {
        HQSpeakerCompositePeripheral member = memberAt(computer, index);
        synchronized (member) {
            return member.owner == Owner.STAGED_FINITE && member.finite.setMuted(muted);
        }
    }

    @LuaFunction
    public final boolean audioReleasePrepared(IComputerAccess computer, String assetId) throws LuaException {
        return staging.releasePrepared(computer, assetId);
    }

    @LuaFunction
    public final long audioMaxStagedBytes() {
        return staging.maxStagedBytes();
    }

    @Override
    public MethodResult callMethod(IComputerAccess computer, ILuaContext context, int method, IArguments args) throws LuaException {
        if (method < 0 || method >= dynamicNames.length) throw new LuaException("invalid peripheral method");
        String name = dynamicNames[method];

        // DNS may block. Keep it outside commandLock as well as the ownership monitor, otherwise a direct
        // main-thread audioPlayPrepared call could wait behind DNS and stall the Minecraft server.
        if (STREAM_START.contains(name)) {
            long revision = commandRevision.incrementAndGet();
            return startStreamReplacing(name, args, revision);
        }
        if (STREAM_DIRECT.contains(name)) {
            // These inherited multi/At helpers remain legacy, but they still supersede any older normal stream
            // validation which is in flight.
            commandRevision.incrementAndGet();
            return invokeLegacy(name, computer, context, args);
        }

        if (!READ_ONLY_DYNAMIC.contains(name)) commandRevision.incrementAndGet();
        synchronized (commandLock) {
            return callMethodOrdered(computer, context, method, args);
        }
    }

    private MethodResult callMethodOrdered(IComputerAccess computer, ILuaContext context, int method, IArguments args)
            throws LuaException {
        if (method < 0 || method >= dynamicNames.length) throw new LuaException("invalid peripheral method");
        String name = dynamicNames[method];

        if (STANDARD.contains(name)) {
            synchronized (this) { return callStandard(name, context, args); }
        }
        if (FINITE_CONTROLS.contains(name)) {
            synchronized (this) { return callFiniteControl(name, computer, context, args); }
        }
        if (FINITE_ALL_CONTROLS.contains(name)) {
            return callFiniteAllControl(name, computer, context, args);
        }
        if (FINITE_AT_CONTROLS.contains(name)) {
            return callFiniteAtControl(name, computer, context, args);
        }

        if ("speakMaxSamples".equals(name)) return MethodResult.of(HQ_RAW_MAX_SAMPLES);
        if ("speakSupportedFiles".equals(name)) return MethodResult.of((Object) SUPPORTED_FINITE_FILES.clone());

        if ("speakStop".equals(name)) {
            synchronized (this) { stopEverything(); }
            return MethodResult.of();
        }
        if ("speakIsPlaying".equals(name)) {
            synchronized (this) { return MethodResult.of(isHQContinuousActive()); }
        }

        if ("setLooping".equals(name)) {
            synchronized (this) {
                if (owner == Owner.STAGED_FINITE) return MethodResult.of(finite.setLooping(args.getBoolean(0)));
            }
        }

        if (RAW_START.contains(name)) {
            synchronized (this) { return startRaw(computer, context, name, args); }
        }
        if (FINITE_START.contains(name)) {
            synchronized (this) { return startLegacyReplacing(Owner.LEGACY_FINITE, computer, context, name, args); }
        }
        synchronized (this) { return invokeLegacy(name, computer, context, args); }
    }

    private MethodResult callStandard(String name, ILuaContext context, IArguments args) throws LuaException {
        return switch (name) {
            case "playNote" -> MethodResult.of(vanilla.playNote(context, args.getString(0), args.optDouble(1), args.optDouble(2)));
            case "playSound" -> {
                if (isHQContinuousActive()) yield MethodResult.of(false);
                boolean accepted = vanilla.playSound(context, args.getString(0), args.optDouble(1), args.optDouble(2));
                if (accepted) clearTerminalOwnership();
                yield MethodResult.of(accepted);
            }
            case "playAudio" -> {
                if (isHQContinuousActive()) yield MethodResult.of(false);
                boolean accepted = vanilla.playAudio(context, args.getTableUnsafe(0), args.optDouble(1));
                if (accepted) clearTerminalOwnership();
                yield MethodResult.of(accepted);
            }
            case "stop" -> {
                stopEverything();
                yield MethodResult.of();
            }
            default -> throw new LuaException("No such method " + name);
        };
    }

    private MethodResult startRaw(IComputerAccess computer, ILuaContext context, String name, IArguments args) throws LuaException {
        HQSpeakerPeripheral.PreparedPcm prepared = legacy.preparePcm(args);
        int samples = prepared.samples();
        if (samples <= 0 || samples > HQ_RAW_MAX_SAMPLES) throw new LuaException(name + ": table too large");

        if (owner == Owner.RAW) {
            if (legacy.speakQueueSize() >= HQ_RAW_QUEUE_LIMIT
                    || !rawLifetime.canAccept(samples, HQ_RAW_BUFFER_SAMPLES)) {
                rawCapacityWaiters.put(computer, samples);
                return MethodResult.of(false);
            }
        } else {
            // All normal RAW rejection/validation has happened. Replacing now clears the old queue and lifetime.
            beginReplacingHQ(Owner.RAW);
        }

        if (!legacy.enqueuePreparedPcm(prepared)) {
            if (owner == Owner.RAW) rawCapacityWaiters.put(computer, samples);
            return MethodResult.of(false);
        }

        owner = Owner.RAW;
        rawCapacityWaiters.remove(computer);
        rawLifetime.acceptedSamples(samples);
        return MethodResult.of(true);
    }

    private MethodResult startStreamReplacing(String name, IArguments args, long expectedCommandRevision) throws LuaException {
        String url = args.getString(0);
        Optional<Double> volume = args.optDouble(1);
        long expectedLifecycle = legacy.lifecycleEpochSnapshot();
        HQSpeakerPeripheral.validateStreamUrl(url, name);

        HQSpeakerAudioPacket.AudioFormat format = switch (name) {
            case "speakStream" -> HQSpeakerAudioPacket.AudioFormat.MP3_STREAM;
            case "speakHLS" -> HQSpeakerAudioPacket.AudioFormat.HLS_STREAM;
            case "speakTS" -> HQSpeakerAudioPacket.AudioFormat.TS_STREAM;
            default -> throw new LuaException("No such stream method " + name);
        };

        // Validation is complete, so the remaining commit is short and may safely rejoin normal command ordering.
        synchronized (commandLock) {
            if (commandRevision.get() != expectedCommandRevision) return MethodResult.of(false);
            synchronized (this) {
                if (!legacy.lifecycleEpochMatches(expectedLifecycle)) return MethodResult.of(false);
                beginReplacingHQ(Owner.STREAM);
                boolean started = legacy.startValidatedStream(url, volume, format, name, expectedLifecycle);
                if (started) owner = Owner.STREAM;
                return MethodResult.of(started);
            }
        }
    }

    private MethodResult startLegacyReplacing(Owner requested, IComputerAccess computer, ILuaContext context,
                                              String name, IArguments args) throws LuaException {
        beginReplacingHQ(requested);
        MethodResult result = invokeLegacy(name, computer, context, args);
        if (immediateTrue(result)) owner = requested;
        return result;
    }

    private void beginReplacingHQ(Owner requested) {
        if (requested == Owner.RAW && owner == Owner.RAW) return;
        stopCurrentHQ();
        vanilla.stop();
    }

    private void stopCurrentHQ() {
        switch (owner) {
            case STAGED_FINITE -> finite.stop();
            case RAW, LEGACY_FINITE, STREAM -> legacy.speakStop();
            case NONE -> { }
        }
        rawCapacityWaiters.clear();
        rawLifetime.clear();
        owner = Owner.NONE;
    }

    private void stopEverything() {
        vanilla.stop();
        if (owner == Owner.STAGED_FINITE) stopFinitePlaybackAndClearOwners();
        else finite.stop();
        legacy.speakStop();
        rawCapacityWaiters.clear();
        rawLifetime.clear();
        owner = Owner.NONE;
    }

    private void stopFinitePlaybackAndClearOwners() {
        List<HQSpeakerCompositePeripheral> affected = new ArrayList<>();
        for (HQSpeakerCompositePeripheral candidate : ACTIVE) {
            if (candidate.owner == Owner.STAGED_FINITE && finite.sharesPlaybackWith(candidate.finite)) {
                affected.add(candidate);
            }
        }
        finite.stopPlayback();
        for (HQSpeakerCompositePeripheral candidate : affected) {
            synchronized (candidate) {
                if (!candidate.finite.isActive()) candidate.owner = Owner.NONE;
            }
        }
    }

    private boolean isHQContinuousActive() {
        return switch (owner) {
            case NONE -> false;
            case RAW -> rawLifetime.active(legacy.speakIsPlaying());
            case LEGACY_FINITE -> legacy.speakIsPlaying();
            case STAGED_FINITE -> finite.isActive();
            case STREAM -> legacy.isStreaming();
        };
    }

    private void clearTerminalOwnership() {
        if (!isHQContinuousActive()) {
            owner = Owner.NONE;
            rawCapacityWaiters.clear();
            rawLifetime.clear();
        }
    }

    private MethodResult callFiniteControl(String name, IComputerAccess computer, ILuaContext context,
                                           IArguments args) throws LuaException {
        if ("audioStatus".equals(name)) {
            return switch (owner) {
                case STAGED_FINITE -> MethodResult.of(finite.hasStatus() ? finite.status() : idleStatus());
                case LEGACY_FINITE, STREAM -> invokeLegacy(name, computer, context, args);
                case RAW -> MethodResult.of(rawStatus());
                case NONE -> MethodResult.of(idleStatus());
            };
        }

        if ("audioStop".equals(name)) {
            if (owner == Owner.STAGED_FINITE) stopFinitePlaybackAndClearOwners();
            else stopCurrentHQ();
            return MethodResult.of();
        }

        if (owner == Owner.STAGED_FINITE) {
            return switch (name) {
                case "audioPause" -> MethodResult.of(finite.pause());
                case "audioResume" -> MethodResult.of(finite.resume());
                case "audioSeek" -> MethodResult.of(finite.seek(args.getDouble(0)));
                case "audioSetVolume" -> MethodResult.of(finite.setVolume(args.getDouble(0)));
                case "audioSetLooping" -> MethodResult.of(finite.setLooping(args.getBoolean(0)));
                default -> MethodResult.of(false);
            };
        }

        if (owner == Owner.LEGACY_FINITE) return invokeLegacy(name, computer, context, args);
        return MethodResult.of(false);
    }

    private MethodResult callFiniteAllControl(String name, IComputerAccess computer, ILuaContext context,
                                              IArguments args) throws LuaException {
        if (owner != Owner.STAGED_FINITE) return invokeLegacy(name, computer, context, args);

        return switch (name) {
            case "audioStatusAll" -> MethodResult.of(finite.hasStatus() ? finite.status() : idleStatus());
            case "audioPauseAll" -> MethodResult.of(finite.pause());
            case "audioResumeAll" -> MethodResult.of(finite.resume());
            case "audioSeekAll" -> MethodResult.of(finite.seek(args.getDouble(0)));
            case "audioSetLoopingAll" -> MethodResult.of(finite.setLooping(args.getBoolean(0)));
            case "audioSetVolumeAll" -> {
                double volume = args.getDouble(0);
                boolean changed = false;
                for (HQSpeakerCompositePeripheral member : membersFor(computer)) {
                    synchronized (member) {
                        if (member.owner == Owner.STAGED_FINITE && finite.sharesPlaybackWith(member.finite)) {
                            changed = member.finite.setVolume(volume) || changed;
                        }
                    }
                }
                yield MethodResult.of(changed);
            }
            case "audioStopAll" -> {
                stopFinitePlaybackAndClearOwners();
                yield MethodResult.of();
            }
            default -> invokeLegacy(name, computer, context, args);
        };
    }

    private MethodResult callFiniteAtControl(String name, IComputerAccess computer, ILuaContext context,
                                             IArguments args) throws LuaException {
        int index = args.getInt(0);
        HQSpeakerCompositePeripheral member = memberAt(computer, index);

        synchronized (member) {
            if (member.owner != Owner.STAGED_FINITE) return invokeLegacy(name, computer, context, args);

            return switch (name) {
                case "audioStatusAt" -> MethodResult.of(member.finite.hasStatus() ? member.finite.status() : idleStatus());
                case "audioPauseAt" -> MethodResult.of(member.finite.pause());
                case "audioResumeAt" -> MethodResult.of(member.finite.resume());
                case "audioSeekAt" -> MethodResult.of(member.finite.seek(args.getDouble(1)));
                case "audioSetVolumeAt" -> MethodResult.of(member.finite.setVolume(args.getDouble(1)));
                case "audioSetLoopingAt" -> MethodResult.of(member.finite.setLooping(args.getBoolean(1)));
                case "audioStopAt" -> {
                    member.stopCurrentHQ();
                    yield MethodResult.of();
                }
                default -> invokeLegacy(name, computer, context, args);
            };
        }
    }

    private Map<String, Object> rawStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("state", isHQContinuousActive() ? "playing" : "idle");
        status.put("kind", "raw");
        status.put("observed", false);
        status.put("canPause", false);
        status.put("canSeek", false);
        status.put("canLoop", false);
        return status;
    }

    private static Map<String, Object> idleStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("state", "idle");
        status.put("kind", "none");
        status.put("observed", false);
        status.put("canPause", false);
        status.put("canSeek", false);
        status.put("canLoop", false);
        return status;
    }

    private static int contiguousRawSamples(IArguments args) throws LuaException {
        Map<?, ?> table = args.getTable(0);
        int length = 0;
        while (length <= HQ_RAW_MAX_SAMPLES
                && (table.containsKey((long) (length + 1)) || table.containsKey((double) (length + 1)))) {
            length++;
        }
        return length;
    }

    private static boolean immediateTrue(MethodResult result) {
        Object[] values = result.getResult();
        return values != null && values.length > 0 && Boolean.TRUE.equals(values[0]);
    }

    private MethodResult invokeLegacy(String name, IComputerAccess computer, ILuaContext context, IArguments args) throws LuaException {
        PeripheralMethod legacyMethod = legacyMethods.get(name);
        if (legacyMethod == null) throw new LuaException("No such method " + name);
        return legacyMethod.apply(legacy, context, computer, args);
    }
}
