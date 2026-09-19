package com.tom.hqspeaker.client;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.compat.MovingSourcePosition;
import com.tom.hqspeaker.media.FiniteDecodeDescriptor;
import com.tom.hqspeaker.media.FiniteRangeLimits;
import com.tom.hqspeaker.media.FiniteRangeWindow;
import com.tom.hqspeaker.network.HQFiniteMediaBeginPacket;
import com.tom.hqspeaker.network.HQFiniteMediaControlPacket;
import com.tom.hqspeaker.network.HQFiniteMediaRangeDataPacket;
import com.tom.hqspeaker.network.HQFiniteMediaRangeRequestPacket;
import com.tom.hqspeaker.network.HQFiniteMediaStatePacket;
import com.tom.hqspeaker.network.HQFiniteMediaStatusPacket;
import com.tom.hqspeaker.network.HQSpeakerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** M1F bounded transport plus the local M1G decoder and Minecraft renderer epoch. */
@OnlyIn(Dist.CLIENT)
public final class HQFiniteMediaClient {
    private static final ConcurrentHashMap<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, SharedTimeline> TIMELINES = new ConcurrentHashMap<>();
    private static final int MAX_SESSIONS = 128;
    private static final int MAX_IN_FLIGHT_REQUESTS = 2;
    private static final long REQUEST_TIMEOUT_NANOS = 2_000_000_000L;
    private static final int MIN_PCM_QUEUE_BYTES = 32 * 1024;
    private static final int MAX_PCM_QUEUE_BYTES = 256 * 1024;
    private static final float FIXED_ATTENUATION_DISTANCE = 32.0f;
    private static final long RENDERER_START_GRACE_NANOS = 1_000_000_000L;
    private static final long LONG_STARVATION_NANOS = 5_000_000_000L;
    private static final long REJOIN_READY_RETRY_NANOS = 1_000_000_000L;
    private static final ExecutorService DECODERS = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("hqspeaker-finite-decoder-", 0L).factory());

    private HQFiniteMediaClient() {}

    /**
     * One client-projected clock per server playback. STATE packets for multiple physical endpoints with the same
     * revision do not re-anchor this clock to their individual arrival times.
     */
    private static final class SharedTimeline {
        final UUID playbackId;
        long stateRevision;
        double position;
        double duration;
        long snapshotNanos;
        boolean paused;
        boolean looping;

        SharedTimeline(UUID playbackId) {
            this.playbackId = playbackId;
        }

        synchronized boolean observe(HQFiniteMediaStatePacket packet, long nowNanos) {
            if (packet.stateRevision() < stateRevision) return false;
            if (stateRevision == 0L || packet.stateRevision() > stateRevision) {
                stateRevision = packet.stateRevision();
                position = packet.position();
                duration = packet.duration();
                paused = packet.state() == HQFiniteMediaStatePacket.PlaybackState.PAUSED;
                looping = packet.looping();
                snapshotNanos = nowNanos;
            }
            return true;
        }

        synchronized long revision() {
            return stateRevision;
        }

        synchronized double projected(long nowNanos) {
            double result = position;
            if (!paused && snapshotNanos > 0L) {
                result += Math.max(0L, nowNanos - snapshotNanos) / 1_000_000_000.0;
            }
            if (duration > 0.0) {
                if (looping) {
                    result %= duration;
                    if (result < 0.0) result += duration;
                } else {
                    result = Math.min(result, duration);
                }
            }
            return Math.max(0.0, result);
        }
    }

    private static final class Session {
        final HQFiniteMediaBeginPacket begin;
        final SharedTimeline timeline;
        final FiniteRangeWindow window;
        final BlockPos blockPos;
        final Vector3d movingPosition = new Vector3d();
        long anchorOffset;
        double anchorTime;
        double targetPosition;
        double duration;
        double statePosition;
        long stateSnapshotNanos;
        long stateRevision;
        double pcmTimelineStart;
        long pcmDiscardedBytes;
        final FiniteDecodeCoordinator coordinator = new FiniteDecodeCoordinator();
        final FiniteRecoveryCoordinator recovery = new FiniteRecoveryCoordinator();
        boolean anchorReady;
        boolean terminal;
        boolean localExhausted;
        boolean desiredPaused;
        boolean looping;
        float volume;
        FiniteEncodedInputStream encodedInput;
        FinitePcmQueue pcmQueue;
        Future<?> decoderTask;
        FinitePcmAudioStream rendererStream;
        FiniteSpeakerSound sound;
        boolean rendererStarted;
        long rendererStartNanos;
        boolean rendererActiveSeen;
        boolean fixedAttenuationApplied;
        Boolean appliedPause;

        Session(HQFiniteMediaBeginPacket begin) {
            this.begin = begin;
            this.timeline = TIMELINES.computeIfAbsent(begin.playbackId(), SharedTimeline::new);
            this.window = new FiniteRangeWindow(begin.totalBytes(), FiniteRangeLimits.CLIENT_WINDOW_BYTES);
            this.blockPos = new BlockPos(begin.blockX(), begin.blockY(), begin.blockZ());
            this.desiredPaused = begin.paused();
            this.looping = begin.looping();
            this.volume = begin.volume();
        }

        void stopRenderer() {
            FiniteSpeakerSound currentSound = sound;
            sound = null;
            if (currentSound != null) {
                currentSound.stopLocally();
                Minecraft.getInstance().getSoundManager().stop(currentSound);
            }
            FinitePcmAudioStream stream = rendererStream;
            rendererStream = null;
            if (stream != null) stream.close();
            rendererStarted = false;
            rendererStartNanos = 0L;
            rendererActiveSeen = false;
            fixedAttenuationApplied = false;
            appliedPause = null;
        }

        void cancelDecodeEpoch() {
            coordinator.invalidateLocalEpoch();
            stopRenderer();
            Future<?> task = decoderTask;
            decoderTask = null;
            if (task != null) task.cancel(true);
            FiniteEncodedInputStream input = encodedInput;
            encodedInput = null;
            if (input != null) input.cancel();
            FinitePcmQueue pcm = pcmQueue;
            pcmQueue = null;
            if (pcm != null) pcm.cancel();
        }

        void cancelAll() {
            cancelDecodeEpoch();
            window.cancel();
        }
    }

    public static void begin(HQFiniteMediaBeginPacket packet) {
        Minecraft.getInstance().execute(() -> begin0(packet));
    }

    public static void rangeData(HQFiniteMediaRangeDataPacket packet) {
        Minecraft.getInstance().execute(() -> rangeData0(packet));
    }

    public static void control(HQFiniteMediaControlPacket packet) {
        Minecraft.getInstance().execute(() -> control0(packet));
    }

    public static void state(HQFiniteMediaStatePacket packet) {
        Minecraft.getInstance().execute(() -> state0(packet));
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            stopAll();
            return;
        }
        long now = System.nanoTime();
        SESSIONS.forEach((source, session) -> {
            if (session.terminal) return;
            if (session.recovery.awaitingState()) {
                sendReadyIfDue(session, now);
                return;
            }
            if (!session.anchorReady || session.volume <= 0.0f || session.localExhausted) return;

            updateMovingPosition(session, minecraft.level);

            FinitePcmAudioStream stream = session.rendererStream;
            if (session.rendererStarted && stream != null && stream.closed() && !stream.reachedEof()) {
                HQSpeakerMod.warn("M1H finite renderer stream closed unexpectedly; rejoining current server time source="
                    + session.begin.source() + " generation=" + session.begin.generation());
                requestAuthoritativeRejoin(session, now);
                return;
            }
            if (!session.desiredPaused && stream != null
                    && session.recovery.longStarved(now, LONG_STARVATION_NANOS)) {
                HQSpeakerMod.warn("M1H finite renderer stayed starved; rejoining current server time source="
                    + session.begin.source() + " generation=" + session.begin.generation());
                requestAuthoritativeRejoin(session, now);
                return;
            }

            session.window.expireRequests(now, REQUEST_TIMEOUT_NANOS);
            pump(session, now);
            tryStartRenderer(session, now);
            observeRendererLifecycle(session, now);
            applyRendererState(session);
        });
    }

    public static void stopAll() {
        SESSIONS.forEach((source, session) -> session.cancelAll());
        SESSIONS.clear();
        TIMELINES.clear();
    }

    private static void begin0(HQFiniteMediaBeginPacket packet) {
        if (packet == null || !packet.sensible()) return;
        if (!SESSIONS.containsKey(packet.source()) && SESSIONS.size() >= MAX_SESSIONS) return;

        Session old = SESSIONS.remove(packet.source());
        if (old != null) {
            old.cancelAll();
            releaseTimelineIfUnused(old);
        }

        Session session = new Session(packet);
        SESSIONS.put(packet.source(), session);
        report(session, HQFiniteMediaStatusPacket.Transition.READY, "");
    }

    private static void state0(HQFiniteMediaStatePacket packet) {
        Session session = SESSIONS.get(packet.source());
        if (!matches(session, packet.mediaId(), packet.generation()) || session.terminal
                || !session.begin.playbackId().equals(packet.playbackId())) return;

        long now = System.nanoTime();
        if (!session.timeline.observe(packet, now)) return;

        if (packet.state() == HQFiniteMediaStatePacket.PlaybackState.ENDED
                || packet.state() == HQFiniteMediaStatePacket.PlaybackState.ERROR) {
            stopTerminalPlayback(packet.playbackId());
            return;
        }

        if (packet.anchorOffset() < 0L || packet.anchorOffset() > session.begin.totalBytes()) {
            fail(session, "server supplied encoded anchor outside asset");
            return;
        }

        boolean decoderUsable = session.encodedInput != null && session.pcmQueue != null;
        boolean exhaustedBlocksRestart = session.localExhausted && !packet.looping();
        FiniteDecodeCoordinator.StateDecision decision = session.coordinator.observeState(
            packet.decodeRevision(), decoderUsable, exhaustedBlocksRestart, packet.volume() <= 0.0f);
        if (decision == FiniteDecodeCoordinator.StateDecision.STALE) return;

        session.recovery.stateReceived();
        session.stateRevision = packet.stateRevision();
        session.desiredPaused = packet.state() == HQFiniteMediaStatePacket.PlaybackState.PAUSED;
        if (session.desiredPaused || decision != FiniteDecodeCoordinator.StateDecision.KEEP) {
            session.recovery.clearStarvation();
        }
        session.looping = packet.looping();
        session.duration = packet.duration();
        session.statePosition = packet.position();
        session.stateSnapshotNanos = now;
        session.anchorOffset = packet.anchorOffset();
        session.anchorTime = packet.anchorTime();
        session.targetPosition = projectedServerPosition(session, now);
        session.anchorReady = true;
        setVolume(session, packet.volume());

        if (decision == FiniteDecodeCoordinator.StateDecision.HIBERNATE) {
            session.localExhausted = false;
            if (hasLocalEpoch(session)) session.cancelDecodeEpoch();
            session.window.cancel();
            return;
        }

        if (decision == FiniteDecodeCoordinator.StateDecision.RESTART) {
            session.localExhausted = false;
            restartDecodeEpoch(session, packet.anchorOffset(), packet.anchorTime(), session.targetPosition);
        }

        pump(session, now);
        tryStartRenderer(session, now);
        observeRendererLifecycle(session, now);
        applyRendererState(session);
    }

    private static void rangeData0(HQFiniteMediaRangeDataPacket packet) {
        Session session = SESSIONS.get(packet.source());
        if (!matches(session, packet.assetId(), packet.generation()) || session.terminal
                || !session.anchorReady || session.volume <= 0.0f || session.localExhausted) return;
        if (session.window.accept(packet.offset(), packet.data())) {
            FiniteEncodedInputStream input = session.encodedInput;
            if (input != null) input.signalDataAvailable();
            pump(session, System.nanoTime());
        }
    }

    private static void control0(HQFiniteMediaControlPacket packet) {
        Session session = SESSIONS.get(packet.source());
        if (session == null || session.begin.generation() != packet.generation() || session.terminal) return;
        // Protocol v7 uses STATE as the sole authority for pause/resume/seek/volume/loop.
        // STOP remains explicit because the server removes the session instead of retaining a STOPPED state.
        if (packet.action() != HQFiniteMediaControlPacket.Action.STOP) return;

        session.terminal = true;
        session.cancelAll();
        SESSIONS.remove(packet.source(), session);
        releaseTimelineIfUnused(session);
    }

    private static void restartDecodeEpoch(Session session, long startOffset,
                                           double decodeAnchorTime, double decodeTargetTime) {
        session.cancelDecodeEpoch();
        try {
            session.window.reset(startOffset);
            FiniteEncodedInputStream input = new FiniteEncodedInputStream(session.window, startOffset);
            FinitePcmQueue pcm = new FinitePcmQueue(pcmQueueCapacity(session.begin.descriptor().sampleRate()));
            session.encodedInput = input;
            session.pcmQueue = pcm;
            session.pcmDiscardedBytes = 0L;
            session.localExhausted = false;
            long epoch = session.coordinator.currentLocalEpoch();

            if (session.begin.descriptor().kind() == FiniteDecodeDescriptor.Kind.WAV) {
                session.pcmTimelineStart = decodeAnchorTime;
                session.decoderTask = DECODERS.submit(() -> runWavDecoder(session, epoch, input, pcm, startOffset));
            } else {
                session.pcmTimelineStart = decodeTargetTime;
                session.decoderTask = DECODERS.submit(() -> runMp3Decoder(
                    session, epoch, input, pcm, decodeAnchorTime, decodeTargetTime));
            }
        } catch (RuntimeException e) {
            session.encodedInput = null;
            session.pcmQueue = null;
            fail(session, "cannot create finite decoder epoch: " + safeMessage(e));
        }
    }

    private static void runWavDecoder(Session session, long epoch, FiniteEncodedInputStream input,
                                      FinitePcmQueue pcm, long startOffset) {
        try {
            ProgressiveWavDecoder.decode(input, pcm, session.begin.descriptor().wavLayout(), startOffset);
        } catch (IOException | RuntimeException e) {
            Minecraft.getInstance().execute(() -> decoderFailed(session, epoch, e));
        }
    }

    private static void runMp3Decoder(Session session, long epoch, FiniteEncodedInputStream input,
                                      FinitePcmQueue pcm, double anchorTime, double targetTime) {
        try {
            ProgressiveMp3Decoder.decode(input, pcm,
                session.begin.descriptor().sampleRate(), session.begin.descriptor().channels(),
                anchorTime, targetTime);
        } catch (IOException | RuntimeException e) {
            Minecraft.getInstance().execute(() -> decoderFailed(session, epoch, e));
        }
    }

    private static void decoderFailed(Session session, long epoch, Exception failure) {
        if (session.terminal || !session.coordinator.isCurrentLocalEpoch(epoch)
                || SESSIONS.get(session.begin.source()) != session) return;

        FinitePcmAudioStream stream = session.rendererStream;
        if (stream != null && stream.closed() && !stream.reachedEof()) {
            HQSpeakerMod.warn("M1H finite decoder was cancelled by renderer close; rejoining current server time source="
                + session.begin.source() + " generation=" + session.begin.generation());
            requestAuthoritativeRejoin(session, System.nanoTime());
            return;
        }

        fail(session, "finite decoder failed: " + safeMessage(failure));
    }

    private static void tryStartRenderer(Session session, long nowNanos) {
        if (session.terminal || session.rendererStarted || session.pcmQueue == null
                || session.volume <= 0.0f || session.localExhausted) return;
        if (!catchUpToServerTime(session, nowNanos)) return;

        FinitePcmQueue pcm = session.pcmQueue;
        int queued = pcm.queuedBytes();
        int threshold = prebufferBytes(session.begin.descriptor().sampleRate(), pcm.capacityBytes());
        if (queued <= 0 || (queued < threshold && !pcm.eofMarked())) return;

        FinitePcmAudioStream stream = new FinitePcmAudioStream(
            pcm, session.begin.descriptor().sampleRate(), session.recovery);
        FiniteSpeakerSound sound = new FiniteSpeakerSound(stream, session.volume,
            session.begin.x(), session.begin.y(), session.begin.z());
        session.rendererStream = stream;
        session.sound = sound;
        session.appliedPause = null;
        session.fixedAttenuationApplied = false;
        try {
            Minecraft.getInstance().getSoundManager().play(sound);
            session.rendererStarted = true;
            session.rendererStartNanos = nowNanos;
            session.rendererActiveSeen = false;
        } catch (RuntimeException failure) {
            HQSpeakerMod.warn("M1G finite renderer start failed source=" + session.begin.source()
                + " generation=" + session.begin.generation() + ": " + safeMessage(failure));
            requestAuthoritativeRejoin(session, nowNanos);
        }
    }

    private static void observeRendererLifecycle(Session session, long nowNanos) {
        if (!session.rendererStarted || session.sound == null || session.rendererStream == null) return;

        boolean active = Minecraft.getInstance().getSoundManager().isActive(session.sound);
        if (active) {
            session.rendererActiveSeen = true;
            return;
        }

        if (session.rendererStream.reachedEof()) {
            handleLocalEof(session, nowNanos);
            return;
        }

        if (!session.rendererActiveSeen
                && nowNanos - session.rendererStartNanos < RENDERER_START_GRACE_NANOS) return;

        HQSpeakerMod.warn("M1G finite renderer did not remain active; requesting authoritative rejoin source="
            + session.begin.source() + " generation=" + session.begin.generation());
        requestAuthoritativeRejoin(session, nowNanos);
    }

    private static void handleLocalEof(Session session, long nowNanos) {
        if (session.terminal) return;
        if (session.looping && session.volume > 0.0f) {
            double target = projectedServerPosition(session, nowNanos);
            restartDecodeEpoch(session, loopStartOffset(session), 0.0, target);
            pump(session, nowNanos);
            return;
        }

        session.cancelDecodeEpoch();
        session.window.cancel();
        session.localExhausted = true;
    }

    private static void requestAuthoritativeRejoin(Session session, long nowNanos) {
        if (session.terminal) return;
        session.cancelDecodeEpoch();
        session.window.cancel();
        session.localExhausted = false;
        session.recovery.beginRejoin();
        sendReadyIfDue(session, nowNanos);
    }

    private static void sendReadyIfDue(Session session, long nowNanos) {
        if (!session.recovery.readyDue(nowNanos, REJOIN_READY_RETRY_NANOS)) return;
        session.recovery.markReadyAttempt(nowNanos);
        report(session, HQFiniteMediaStatusPacket.Transition.READY, "");
    }

    private static long loopStartOffset(Session session) {
        if (session.begin.descriptor().kind() == FiniteDecodeDescriptor.Kind.WAV) {
            return session.begin.descriptor().wavLayout().dataOffset();
        }
        return 0L;
    }

    private static boolean hasLocalEpoch(Session session) {
        return session.decoderTask != null || session.encodedInput != null || session.pcmQueue != null
            || session.rendererStream != null || session.sound != null || session.rendererStarted;
    }

    /**
     * Keep dropping already-decoded PCM until its front represents current canonical server time. This means network,
     * decoder, prebuffer, and simple local loop-restart latency do not become permanent audible lag.
     */
    private static boolean catchUpToServerTime(Session session, long nowNanos) {
        FinitePcmQueue pcm = session.pcmQueue;
        if (pcm == null || !session.anchorReady) return false;
        double desired = projectedServerPosition(session, nowNanos);
        double seconds = Math.max(0.0, desired - session.pcmTimelineStart);
        long samples = (long) Math.floor(seconds * session.begin.descriptor().sampleRate() + 1.0e-9);
        long requiredBytes;
        try {
            requiredBytes = Math.multiplyExact(samples, 2L);
        } catch (ArithmeticException e) {
            return false;
        }
        long remaining = Math.max(0L, requiredBytes - session.pcmDiscardedBytes);
        if (remaining == 0L) return true;

        int request = (int) Math.min((long) Integer.MAX_VALUE - 1L, remaining);
        if ((request & 1) != 0) request--;
        int discarded = pcm.discard(request);
        session.pcmDiscardedBytes += discarded;
        return session.pcmDiscardedBytes >= requiredBytes;
    }

    private static double projectedServerPosition(Session session, long nowNanos) {
        if (session.stateRevision > 0L && session.stateRevision == session.timeline.revision()) {
            return session.timeline.projected(nowNanos);
        }

        double position = session.statePosition;
        if (!session.desiredPaused && session.stateSnapshotNanos > 0L) {
            position += Math.max(0L, nowNanos - session.stateSnapshotNanos) / 1_000_000_000.0;
        }
        if (session.duration > 0.0) {
            if (session.looping) {
                position %= session.duration;
                if (position < 0.0) position += session.duration;
            } else {
                position = Math.min(position, session.duration);
            }
        }
        return Math.max(0.0, position);
    }

    private static void updateMovingPosition(Session session, net.minecraft.world.level.Level level) {
        FiniteSpeakerSound sound = session.sound;
        if (sound == null || level == null) return;
        Vector3d world = MovingSourcePosition.resolve(level, session.blockPos, session.movingPosition);
        sound.updatePosition((float) world.x, (float) world.y, (float) world.z);
    }

    private static void applyRendererState(Session session) {
        FiniteSpeakerSound sound = session.sound;
        if (sound == null || !Minecraft.getInstance().getSoundManager().isActive(sound)) return;

        boolean desired = session.desiredPaused;
        boolean needsPause = session.appliedPause == null || session.appliedPause != desired;
        boolean needsAttenuation = !session.fixedAttenuationApplied;
        if (!needsPause && !needsAttenuation) return;

        boolean found = HQSoundChannelControl.execute(sound, channel -> {
            channel.linearAttenuation(FIXED_ATTENUATION_DISTANCE);
            if (needsPause) {
                if (desired) channel.pause();
                else channel.unpause();
            }
            Minecraft.getInstance().execute(() -> {
                if (session.sound == sound) {
                    session.fixedAttenuationApplied = true;
                    if (needsPause && session.desiredPaused == desired) session.appliedPause = desired;
                }
            });
        });
        if (!found) {
            session.fixedAttenuationApplied = false;
            if (needsPause) session.appliedPause = null;
        }
    }

    private static void setVolume(Session session, double value) {
        if (!Double.isFinite(value)) return;
        session.volume = (float) Math.max(0.0, Math.min(3.0, value));
        FiniteSpeakerSound sound = session.sound;
        if (sound != null) {
            sound.updateVolume(session.volume);
            HQSoundChannelControl.refreshBlocksVolume();
        }
    }

    private static int prebufferBytes(int sampleRate, int capacityBytes) {
        long target = Math.max(2_048L, ((long) sampleRate * 2L) / 10L);
        target = Math.min(target, Math.max(2L, capacityBytes / 2L));
        int bytes = (int) target;
        return (bytes & 1) == 0 ? bytes : bytes - 1;
    }

    private static int pcmQueueCapacity(int sampleRate) {
        long target = Math.max(MIN_PCM_QUEUE_BYTES, Math.min((long) MAX_PCM_QUEUE_BYTES, (long) sampleRate));
        int bytes = (int) target;
        return (bytes & 1) == 0 ? bytes : bytes + 1;
    }

    private static void pump(Session session, long nowNanos) {
        if (session.terminal || !session.anchorReady || session.volume <= 0.0f
                || session.localExhausted || !session.window.anchored()) return;
        while (session.window.pendingRequests() < MAX_IN_FLIGHT_REQUESTS) {
            var next = session.window.nextRequest(FiniteRangeLimits.MAX_RANGE_BYTES, nowNanos);
            if (next.isEmpty()) return;
            FiniteRangeWindow.Range range = next.get();
            try {
                HQSpeakerNetwork.sendToServer(new HQFiniteMediaRangeRequestPacket(
                    session.begin.source(), session.begin.mediaId(), session.begin.generation(),
                    range.offset(), range.length()));
            } catch (RuntimeException e) {
                session.window.requestFailed(range.offset());
                return;
            }
        }
    }

    private static boolean matches(Session session, UUID assetId, long generation) {
        return session != null && session.begin.mediaId().equals(assetId) && session.begin.generation() == generation;
    }

    private static void fail(Session session, String error) {
        if (session.terminal) return;
        session.terminal = true;
        HQSpeakerMod.warn("M1G finite client failed source=" + session.begin.source()
            + " generation=" + session.begin.generation() + ": " + error);
        report(session, HQFiniteMediaStatusPacket.Transition.ERROR, error);
        session.cancelAll();
        SESSIONS.remove(session.begin.source(), session);
        releaseTimelineIfUnused(session);
    }

    private static void stopTerminalPlayback(UUID playbackId) {
        for (Session candidate : SESSIONS.values()) {
            if (!candidate.begin.playbackId().equals(playbackId)) continue;
            candidate.terminal = true;
            candidate.cancelAll();
            SESSIONS.remove(candidate.begin.source(), candidate);
        }
        TIMELINES.remove(playbackId);
    }

    private static void releaseTimelineIfUnused(Session removed) {
        UUID playbackId = removed.begin.playbackId();
        for (Session candidate : SESSIONS.values()) {
            if (candidate.begin.playbackId().equals(playbackId)) return;
        }
        TIMELINES.remove(playbackId, removed.timeline);
    }

    private static void report(Session session, HQFiniteMediaStatusPacket.Transition transition, String error) {
        try {
            HQSpeakerNetwork.sendToServer(new HQFiniteMediaStatusPacket(
                session.begin.source(), session.begin.generation(), transition, 0.0, 0.0, error));
        } catch (RuntimeException ignored) {
        }
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
