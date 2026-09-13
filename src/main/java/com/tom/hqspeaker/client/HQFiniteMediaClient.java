package com.tom.hqspeaker.client;

import com.tom.hqspeaker.HQSpeakerMod;
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

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * M1F finite client transport only.
 *
 * <p>The old complete-file .part/.media bridge and FileFiniteAudioStream are intentionally gone from the modern
 * prepared path. M1G will consume this bounded encoded window progressively and create the audible renderer.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class HQFiniteMediaClient {
    private static final ConcurrentHashMap<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final int MAX_SESSIONS = 128;
    private static final int MAX_IN_FLIGHT_REQUESTS = 2;
    private static final long REQUEST_TIMEOUT_NANOS = 2_000_000_000L;

    private HQFiniteMediaClient() {}

    private static final class Session {
        final HQFiniteMediaBeginPacket begin;
        final FiniteRangeWindow window;
        long anchorOffset;
        double anchorTime;
        boolean terminal;

        Session(HQFiniteMediaBeginPacket begin) {
            this.begin = begin;
            this.window = new FiniteRangeWindow(begin.totalBytes(), FiniteRangeLimits.CLIENT_WINDOW_BYTES);
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
            session.window.expireRequests(now, REQUEST_TIMEOUT_NANOS);
            pump(session, now);
        });
    }

    public static void stopAll() {
        SESSIONS.forEach((source, session) -> session.window.cancel());
        SESSIONS.clear();
    }

    private static void begin0(HQFiniteMediaBeginPacket packet) {
        if (packet == null || !packet.sensible()) return;
        if (!SESSIONS.containsKey(packet.source()) && SESSIONS.size() >= MAX_SESSIONS) return;

        Session old = SESSIONS.remove(packet.source());
        if (old != null) old.window.cancel();

        Session session = new Session(packet);
        SESSIONS.put(packet.source(), session);
        // Wait for the fresh authoritative STATE before requesting bytes so the first demand starts at the
        // server-selected current/seek anchor instead of assuming byte zero from BEGIN alone.
        report(session, HQFiniteMediaStatusPacket.Transition.READY, "");
    }

    private static void state0(HQFiniteMediaStatePacket packet) {
        Session session = SESSIONS.get(packet.source());
        if (!matches(session, packet.mediaId(), packet.generation()) || session.terminal) return;

        if (packet.state() == HQFiniteMediaStatePacket.PlaybackState.ENDED
                || packet.state() == HQFiniteMediaStatePacket.PlaybackState.ERROR) {
            session.terminal = true;
            session.window.cancel();
            SESSIONS.remove(packet.source(), session);
            return;
        }

        if (packet.anchorOffset() < 0L || packet.anchorOffset() > session.begin.totalBytes()) {
            fail(session, "server supplied encoded anchor outside asset");
            return;
        }

        if (packet.anchorOffset() != session.anchorOffset) {
            session.anchorOffset = packet.anchorOffset();
            session.anchorTime = packet.anchorTime();
            session.window.reset(packet.anchorOffset());
        } else {
            session.anchorTime = packet.anchorTime();
        }
        pump(session, System.nanoTime());
    }

    private static void rangeData0(HQFiniteMediaRangeDataPacket packet) {
        Session session = SESSIONS.get(packet.source());
        if (!matches(session, packet.assetId(), packet.generation()) || session.terminal) return;
        if (session.window.accept(packet.offset(), packet.data())) pump(session, System.nanoTime());
        // A stale response after seek/replacement is intentionally discarded without becoming a playback error.
    }

    private static void control0(HQFiniteMediaControlPacket packet) {
        Session session = SESSIONS.get(packet.source());
        if (session == null || session.begin.generation() != packet.generation() || session.terminal) return;
        if (packet.action() == HQFiniteMediaControlPacket.Action.STOP) {
            session.terminal = true;
            session.window.cancel();
            SESSIONS.remove(packet.source(), session);
        }
        // Pause/resume/seek/loop/volume are semantic server state. Range demand re-anchors from STATE.
    }

    private static void pump(Session session, long nowNanos) {
        if (session.terminal) return;
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
        HQSpeakerMod.warn("M1F finite client transport failed source=" + session.begin.source()
            + " generation=" + session.begin.generation() + ": " + error);
        report(session, HQFiniteMediaStatusPacket.Transition.ERROR, error);
        session.window.cancel();
        SESSIONS.remove(session.begin.source(), session);
    }

    private static void report(Session session, HQFiniteMediaStatusPacket.Transition transition, String error) {
        try {
            HQSpeakerNetwork.sendToServer(new HQFiniteMediaStatusPacket(
                session.begin.source(), session.begin.generation(), transition, 0.0, 0.0, error));
        } catch (RuntimeException ignored) {
        }
    }
}
