package com.tom.hqspeaker.client;

import com.tom.hqspeaker.HQSpeakerMod;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

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
    private static final int MAX_SESSIONS = 128;
    private static final int MAX_IN_FLIGHT_REQUESTS = 2;
    private static final long REQUEST_TIMEOUT_NANOS = 2_000_000_000L;
    private static final int MIN_PCM_QUEUE_BYTES = 32 * 1024;
    private static final int MAX_PCM_QUEUE_BYTES = 256 * 1024;
    private static final ExecutorService DECODERS = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("hqspeaker-finite-decoder-", 0L).factory());

    private HQFiniteMediaClient() {}

    private static final class Session {
        final HQFiniteMediaBeginPacket begin;
        final FiniteRangeWindow window;
        long anchorOffset;
        double anchorTime;
        double targetPosition;
        double duration;
        double statePosition;
        long stateSnapshotNanos;
        double pcmTimelineStart;
        long pcmDiscardedBytes;
        long decodeEpoch;
        boolean anchorReady;
        boolean restartRequested;
        boolean terminal;
        boolean desiredPaused;
        boolean looping;
        float volume;
        FiniteEncodedInputStream encodedInput;
        FinitePcmQueue pcmQueue;
        Future<?> decoderTask;
        FinitePcmAudioStream rendererStream;
        FiniteSpeakerSound sound;
        boolean rendererStarted;
        Boolean appliedPause;

        Session(HQFiniteMediaBeginPacket begin) {
            this.begin = begin;
            this.window = new FiniteRangeWindow(begin.totalBytes(), FiniteRangeLimits.CLIENT_WINDOW_BYTES);
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
            appliedPause = null;
        }

        void cancelDecodeEpoch() {
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
            if (!session.anchorReady) return;
            session.window.expireRequests(now, REQUEST_TIMEOUT_NANOS);
            pump(session, now);
            tryStartRenderer(session, now);
            applyRendererState(session);
        });
    }

    public static void stopAll() {
        SESSIONS.forEach((source, session) -> session.cancelAll());
        SESSIONS.clear();
    }

    private static void begin0(HQFiniteMediaBeginPacket packet) {
        if (packet == null || !packet.sensible()) return;
        if (!SESSIONS.containsKey(packet.source()) && SESSIONS.size() >= MAX_SESSIONS) return;

        Session old = SESSIONS.remove(packet.source());
        if (old != null) old.cancelAll();

        Session session = new Session(packet);
        SESSIONS.put(packet.source(), session);
        report(session, HQFiniteMediaStatusPacket.Transition.READY, "");
    }

    private static void state0(HQFiniteMediaStatePacket packet) {
        Session session = SESSIONS.get(packet.source());
        if (!matches(session, packet.mediaId(), packet.generation()) || session.terminal) return;

        if (packet.state() == HQFiniteMediaStatePacket.PlaybackState.ENDED
                || packet.state() == HQFiniteMediaStatePacket.PlaybackState.ERROR) {
            session.terminal = true;
            session.cancelAll();
            SESSIONS.remove(packet.source(), session);
            return;
        }

        if (packet.anchorOffset() < 0L || packet.anchorOffset() > session.begin.totalBytes()) {
            fail(session, "server supplied encoded anchor outside asset");
            return;
        }

        long now = System.nanoTime();
        session.desiredPaused = packet.state() == HQFiniteMediaStatePacket.PlaybackState.PAUSED;
        session.looping = packet.looping();
        session.duration = packet.duration();
        session.statePosition = packet.position();
        session.stateSnapshotNanos = now;
        setVolume(session, packet.volume());

        boolean anchorChanged = !session.anchorReady || packet.anchorOffset() != session.anchorOffset;
        boolean anchorOutsideWindow = session.window.anchored()
            && (packet.anchorOffset() < session.window.windowStart() || packet.anchorOffset() >= session.window.windowEnd());
        boolean restart = anchorChanged || session.restartRequested || session.encodedInput == null || session.pcmQueue == null;

        if (!session.window.anchored() || anchorChanged || anchorOutsideWindow) {
            session.window.reset(packet.anchorOffset());
        }
        session.anchorOffset = packet.anchorOffset();
        session.anchorTime = packet.anchorTime();
        session.targetPosition = packet.position();
        session.anchorReady = true;

        if (restart) restartDecodeEpoch(session, packet.anchorOffset());
        session.restartRequested = false;
        pump(session, now);
        tryStartRenderer(session, now);
        applyRendererState(session);
    }

    private static void rangeData0(HQFiniteMediaRangeDataPacket packet) {
        Session session = SESSIONS.get(packet.source());
        if (!matches(session, packet.assetId(), packet.generation()) || session.terminal || !session.anchorReady) return;
        if (session.window.accept(packet.offset(), packet.data())) {
            FiniteEncodedInputStream input = session.encodedInput;
            if (input != null) input.signalDataAvailable();
            pump(session, System.nanoTime());
        }
    }

    private static void control0(HQFiniteMediaControlPacket packet) {
        Session session = SESSIONS.get(packet.source());
        if (session == null || session.begin.generation() != packet.generation() || session.terminal) return;
        long now = System.nanoTime();
        switch (packet.action()) {
            case STOP -> {
                session.terminal = true;
                session.cancelAll();
                SESSIONS.remove(packet.source(), session);
            }
            case SEEK -> {
                session.cancelDecodeEpoch();
                session.restartRequested = true;
            }
            case PAUSE -> {
                if (!session.desiredPaused && session.anchorReady) {
                    session.statePosition = projectedServerPosition(session, now);
                    session.stateSnapshotNanos = now;
                }
                session.desiredPaused = true;
                applyRendererState(session);
            }
            case RESUME -> {
                if (session.desiredPaused) session.stateSnapshotNanos = now;
                session.desiredPaused = false;
                applyRendererState(session);
            }
            case SET_VOLUME -> setVolume(session, packet.value());
            case SET_LOOP -> session.looping = packet.value() >= 0.5;
        }
    }

    private static void restartDecodeEpoch(Session session, long startOffset) {
        session.cancelDecodeEpoch();
        try {
            FiniteEncodedInputStream input = new FiniteEncodedInputStream(session.window, startOffset);
            FinitePcmQueue pcm = new FinitePcmQueue(pcmQueueCapacity(session.begin.descriptor().sampleRate()));
            session.encodedInput = input;
            session.pcmQueue = pcm;
            session.pcmDiscardedBytes = 0L;
            long epoch = ++session.decodeEpoch;

            if (session.begin.descriptor().kind() == FiniteDecodeDescriptor.Kind.WAV) {
                session.pcmTimelineStart = session.anchorTime;
                session.decoderTask = DECODERS.submit(() -> runWavDecoder(session, epoch, input, pcm, startOffset));
            } else {
                double anchorTime = session.anchorTime;
                double targetTime = session.targetPosition;
                session.pcmTimelineStart = targetTime;
                session.decoderTask = DECODERS.submit(() -> runMp3Decoder(
                    session, epoch, input, pcm, anchorTime, targetTime));
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
        if (session.terminal || session.decodeEpoch != epoch || SESSIONS.get(session.begin.source()) != session) return;
        fail(session, "finite decoder failed: " + safeMessage(failure));
    }

    private static void tryStartRenderer(Session session, long nowNanos) {
        if (session.terminal || session.rendererStarted || session.pcmQueue == null) return;
        if (!catchUpToServerTime(session, nowNanos)) return;

        FinitePcmQueue pcm = session.pcmQueue;
        int queued = pcm.queuedBytes();
        int threshold = prebufferBytes(session.begin.descriptor().sampleRate(), pcm.capacityBytes());
        if (queued <= 0 || (queued < threshold && !pcm.eofMarked())) return;

        FinitePcmAudioStream stream = new FinitePcmAudioStream(pcm, session.begin.descriptor().sampleRate());
        FiniteSpeakerSound sound = new FiniteSpeakerSound(stream, session.volume,
            session.begin.x(), session.begin.y(), session.begin.z());
        session.rendererStream = stream;
        session.sound = sound;
        session.rendererStarted = true;
        session.appliedPause = null;
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    /**
     * Keep dropping already-decoded PCM until its front represents current canonical server time. This means network,
     * decoder and prebuffer latency do not become permanent audible lag. Loop-wrap restart itself remains a separate
     * server-authority decision and is intentionally not implemented here.
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
        double position = session.statePosition;
        if (!session.desiredPaused && session.stateSnapshotNanos > 0L) {
            position += Math.max(0L, nowNanos - session.stateSnapshotNanos) / 1_000_000_000.0;
        }
        // Loop wrap needs an explicit authority decision. Until that is chosen, never locally modulo the server clock.
        if (session.duration > 0.0) position = Math.min(position, session.duration);
        return Math.max(0.0, position);
    }

    private static void applyRendererState(Session session) {
        FiniteSpeakerSound sound = session.sound;
        if (sound == null || !Minecraft.getInstance().getSoundManager().isActive(sound)) return;
        boolean desired = session.desiredPaused;
        if (session.appliedPause != null && session.appliedPause == desired) return;
        boolean found = HQSoundChannelControl.execute(sound, channel -> {
            if (desired) channel.pause();
            else channel.unpause();
            Minecraft.getInstance().execute(() -> {
                if (session.sound == sound && session.desiredPaused == desired) {
                    session.appliedPause = desired;
                }
            });
        });
        if (!found) session.appliedPause = null;
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
        if (session.terminal || !session.anchorReady) return;
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
