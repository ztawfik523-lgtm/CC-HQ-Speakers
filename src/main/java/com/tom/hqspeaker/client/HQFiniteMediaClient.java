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

/** M1F bounded transport plus the local M1G decoder-epoch boundary. */
@OnlyIn(Dist.CLIENT)
public final class HQFiniteMediaClient {
    private static final ConcurrentHashMap<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final int MAX_SESSIONS = 128;
    private static final int MAX_IN_FLIGHT_REQUESTS = 2;
    private static final long REQUEST_TIMEOUT_NANOS = 2_000_000_000L;
    private static final int MIN_PCM_QUEUE_BYTES = 32 * 1024;
    private static final int MAX_PCM_QUEUE_BYTES = 256 * 1024;
    // Decoder workers are allowed to block on M1F starvation/PCM backpressure. Virtual threads keep those waits from
    // monopolizing Minecraft/client threads; active sessions remain hard-capped by MAX_SESSIONS.
    private static final ExecutorService DECODERS = Executors.newThreadPerTaskExecutor(
        Thread.ofVirtual().name("hqspeaker-finite-decoder-", 0L).factory());

    private HQFiniteMediaClient() {}

    private static final class Session {
        final HQFiniteMediaBeginPacket begin;
        final FiniteRangeWindow window;
        long anchorOffset;
        double anchorTime;
        double targetPosition;
        long decodeEpoch;
        boolean anchorReady;
        boolean restartRequested;
        boolean terminal;
        FiniteEncodedInputStream encodedInput;
        FinitePcmQueue pcmQueue;
        Future<?> decoderTask;

        Session(HQFiniteMediaBeginPacket begin) {
            this.begin = begin;
            this.window = new FiniteRangeWindow(begin.totalBytes(), FiniteRangeLimits.CLIENT_WINDOW_BYTES);
        }

        void cancelDecodeEpoch() {
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
        // BEGIN describes identity/layout, but byte demand and a decoder epoch wait for authoritative STATE so the
        // server chooses the current codec-safe encoded anchor.
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
        pump(session, System.nanoTime());
    }

    private static void rangeData0(HQFiniteMediaRangeDataPacket packet) {
        Session session = SESSIONS.get(packet.source());
        if (!matches(session, packet.assetId(), packet.generation()) || session.terminal || !session.anchorReady) return;
        if (session.window.accept(packet.offset(), packet.data())) {
            FiniteEncodedInputStream input = session.encodedInput;
            if (input != null) input.signalDataAvailable();
            pump(session, System.nanoTime());
        }
        // A stale response after seek/replacement is intentionally discarded without becoming a playback error.
    }

    private static void control0(HQFiniteMediaControlPacket packet) {
        Session session = SESSIONS.get(packet.source());
        if (session == null || session.begin.generation() != packet.generation() || session.terminal) return;
        if (packet.action() == HQFiniteMediaControlPacket.Action.STOP) {
            session.terminal = true;
            session.cancelAll();
            SESSIONS.remove(packet.source(), session);
            return;
        }
        if (packet.action() == HQFiniteMediaControlPacket.Action.SEEK) {
            // Codec state and queued PCM are semantic-seek state. Invalidate them even if STATE later selects the same
            // coarse encoded anchor. Immutable encoded bytes may remain reusable until STATE decides whether to reset.
            session.cancelDecodeEpoch();
            session.restartRequested = true;
        }
        // Pause/resume/loop/volume remain server semantics; renderer projection is added later in M1G.
    }

    private static void restartDecodeEpoch(Session session, long startOffset) {
        session.cancelDecodeEpoch();
        try {
            FiniteEncodedInputStream input = new FiniteEncodedInputStream(session.window, startOffset);
            FinitePcmQueue pcm = new FinitePcmQueue(pcmQueueCapacity(session.begin.descriptor().sampleRate()));
            session.encodedInput = input;
            session.pcmQueue = pcm;
            long epoch = ++session.decodeEpoch;

            if (session.begin.descriptor().kind() == FiniteDecodeDescriptor.Kind.WAV) {
                session.decoderTask = DECODERS.submit(() -> runWavDecoder(session, epoch, input, pcm, startOffset));
            }
            // MP3 gets the same epoch/input/PCM boundary; the JLayer worker is the next M1G slice.
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

    private static void decoderFailed(Session session, long epoch, Exception failure) {
        if (session.terminal || session.decodeEpoch != epoch || SESSIONS.get(session.begin.source()) != session) return;
        fail(session, "finite decoder failed: " + safeMessage(failure));
    }

    private static int pcmQueueCapacity(int sampleRate) {
        // Roughly 500 ms of mono S16 at ordinary rates, with absolute bounds independent of hostile metadata.
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
