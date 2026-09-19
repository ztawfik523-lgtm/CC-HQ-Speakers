package com.tom.hqspeaker.peripheral;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.media.FiniteDecodeAnchorSelector;
import com.tom.hqspeaker.media.FiniteDecodeDescriptor;
import com.tom.hqspeaker.media.FinitePlaybackStateMachine;
import com.tom.hqspeaker.media.FiniteListenerMembership;
import com.tom.hqspeaker.media.FiniteRangeReadService;
import com.tom.hqspeaker.media.FiniteRangeValidation;
import com.tom.hqspeaker.media.MediaAsset;
import com.tom.hqspeaker.media.MediaAssetReleaseQueue;
import com.tom.hqspeaker.media.MediaAssetStore;
import com.tom.hqspeaker.media.MediaMetadata;
import com.tom.hqspeaker.media.ServerMediaAssets;
import com.tom.hqspeaker.network.BestEffortProjection;
import com.tom.hqspeaker.network.HQFiniteMediaBeginPacket;
import com.tom.hqspeaker.network.HQFiniteMediaControlPacket;
import com.tom.hqspeaker.network.HQFiniteMediaRangeDataPacket;
import com.tom.hqspeaker.network.HQFiniteMediaRangeRequestPacket;
import com.tom.hqspeaker.network.HQFiniteMediaStatePacket;
import com.tom.hqspeaker.network.HQFiniteMediaStatusPacket;
import com.tom.hqspeaker.network.HQSpeakerNetwork;
import com.tom.hqspeaker.vs2.VS2TransformHelper;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Matrix4dc;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Server-authoritative finite playback state plus M1F transport and M1G decoder descriptors/anchors. */
public final class HQFiniteMediaServer {
    private static final double SPEAKER_RADIUS = 32.0;

    private static final Set<HQFiniteMediaServer> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, HQFiniteMediaServer> BY_SOURCE = new ConcurrentHashMap<>();

    private static final class Session {
        final UUID mediaId;
        final long generation;
        final FiniteDecodeDescriptor descriptor;
        final MediaMetadata metadata;
        final long totalBytes;
        final FinitePlaybackStateMachine playback;
        final FiniteRangeReadService rangeReads;
        final MediaAssetReleaseQueue releases;
        final UUID retainedAssetId;
        long decodeRevision = 1L;
        boolean assetReferenceHeld = true;
        final FiniteListenerMembership listeners = new FiniteListenerMembership();

        Session(UUID mediaId, long generation, MediaMetadata metadata, long totalBytes,
                FiniteRangeReadService rangeReads, MediaAssetReleaseQueue releases,
                double volume, long nowNanos) {
            this.mediaId = mediaId;
            this.generation = generation;
            this.metadata = metadata;
            this.descriptor = FiniteDecodeDescriptor.fromMetadata(metadata);
            this.totalBytes = totalBytes;
            this.rangeReads = rangeReads;
            this.releases = releases;
            this.retainedAssetId = mediaId;
            this.playback = new FinitePlaybackStateMachine(metadata.durationSeconds(), volume, nowNanos);
            FiniteDecodeAnchorSelector.select(metadata, totalBytes, 0.0);
        }
    }

    private final ServerLevel level;
    private final BlockPos pos;
    private final UUID source = UUID.randomUUID();
    private final HQMediaStaging staging;
    private final Set<IComputerAccess> attachedComputers = ConcurrentHashMap.newKeySet();
    private long generationCounter;
    private Session session;
    private Map<String, Object> terminalStatus;

    public HQFiniteMediaServer(net.minecraft.world.level.Level level, BlockPos pos, HQMediaStaging staging) {
        if (!(level instanceof ServerLevel serverLevel)) {
            throw new IllegalArgumentException("finite media server requires a server level");
        }
        this.level = serverLevel;
        this.pos = pos.immutable();
        this.staging = staging;
        ACTIVE.add(this);
        BY_SOURCE.put(source, this);
    }

    public UUID source() { return source; }

    public static void tickAll() {
        for (HQFiniteMediaServer server : ACTIVE) {
            try { server.tick(); }
            catch (Exception e) { HQSpeakerMod.warn("finite media tick failed at " + server.pos + ": " + safeMessage(e)); }
        }
    }

    public static void acceptStatus(ServerPlayer player, HQFiniteMediaStatusPacket packet) {
        HQFiniteMediaServer server = packet == null ? null : BY_SOURCE.get(packet.source());
        if (server != null) server.acceptStatus0(player, packet);
    }

    public static void acceptRangeRequest(ServerPlayer player, HQFiniteMediaRangeRequestPacket packet) {
        HQFiniteMediaServer server = packet == null ? null : BY_SOURCE.get(packet.source());
        if (server != null) server.acceptRangeRequest0(player, packet);
    }

    public void attach(IComputerAccess computer) { attachedComputers.add(computer); }
    public void detach(IComputerAccess computer) { attachedComputers.remove(computer); }

    static final class PreparedStart implements AutoCloseable {
        private final HQFiniteMediaServer server;
        private final Session next;
        private final Map<String, Object> initialStatus;
        private boolean finished;

        private PreparedStart(HQFiniteMediaServer server, Session next, Map<String, Object> initialStatus) {
            this.server = server;
            this.next = next;
            this.initialStatus = initialStatus;
        }

        @Override
        public void close() {
            if (finished) return;
            finished = true;
            MediaAssetReleaseQueue.Result release = next.releases.release(next.retainedAssetId);
            if (release.deferred()) {
                HQSpeakerMod.warn("uncommitted prepared start release queued for retry " + next.retainedAssetId
                    + ": " + release.error());
            }
        }
    }

    synchronized PreparedStart preparePreparedStart(String assetId, double volume) throws LuaException {
        if (!Double.isFinite(volume)) throw new LuaException("volume must be finite");

        UUID id = HQMediaStaging.parseAssetId(assetId);
        MediaAssetStore store = staging.assetStore();
        MediaAsset asset = store.get(id).orElseThrow(() -> new LuaException("unknown or released media asset"));
        MediaMetadata metadata = asset.metadata();
        if (metadata == null) throw new LuaException("media asset has not been analyzed");

        ServerMediaAssets mediaAssets;
        try {
            mediaAssets = ServerMediaAssets.get(level.getServer());
        } catch (IOException e) {
            throw new LuaException("HQ media services are unavailable: " + safeMessage(e));
        }

        final boolean retained;
        try {
            retained = store.retain(id);
        } catch (IllegalStateException e) {
            throw new LuaException("HQ media asset store is unavailable: " + safeMessage(e));
        }
        if (!retained) throw new LuaException("unknown or released media asset");

        try {
            long generation = generationCounter + 1L;
            if (generation <= 0L) throw new IllegalStateException("finite generation exhausted");
            long now = System.nanoTime();
            Session next = new Session(id, generation, metadata, asset.sizeBytes(),
                mediaAssets.rangeReads(), mediaAssets.releases(), volume, now);
            return new PreparedStart(this, next, statusOf(next, now));
        } catch (RuntimeException e) {
            MediaAssetReleaseQueue.Result release = mediaAssets.releases().release(id);
            if (release.deferred()) {
                HQSpeakerMod.warn("prepared start construction failed; playback asset release queued for retry " + id
                    + ": " + release.error());
            }
            throw e;
        }
    }

    synchronized boolean commitPreparedStart(PreparedStart prepared) {
        if (prepared == null || prepared.server != this || prepared.finished) {
            throw new IllegalArgumentException("invalid or consumed prepared start");
        }
        if (isActive()) return false;
        if (prepared.next.generation != generationCounter + 1L) {
            throw new IllegalStateException("prepared finite start became stale");
        }

        generationCounter = prepared.next.generation;
        session = prepared.next;
        terminalStatus = null;
        prepared.finished = true;
        refreshListeners(prepared.next);
        queueStateEvent(prepared.initialStatus);
        return true;
    }

    /** Play without replacement. Composite replacement uses transactional prepare + commit. */
    public synchronized boolean playPrepared(String assetId, double volume) throws LuaException {
        if (isActive()) return false;
        try (PreparedStart prepared = preparePreparedStart(assetId, volume)) {
            return commitPreparedStart(prepared);
        }
    }

    public synchronized boolean isActive() {
        return session != null && session.playback.active();
    }

    public synchronized boolean pause() {
        Session s = session;
        if (s == null) return false;
        long now = System.nanoTime();
        if (finalizeNaturalEnd(s, now)) {
            notifyState(s, now);
            return false;
        }
        if (!s.playback.pause(now)) return false;
        notifyState(s, now);
        return true;
    }

    public synchronized boolean resume() {
        Session s = session;
        if (s == null) return false;
        long now = System.nanoTime();
        if (!s.playback.resume(now)) return false;
        notifyState(s, now);
        return true;
    }

    public synchronized boolean seek(double seconds) throws LuaException {
        if (!Double.isFinite(seconds)) throw new LuaException("seconds must be finite");
        Session s = session;
        if (s == null || s.playback.terminal() || s.playback.duration() <= 0.0) return false;
        long now = System.nanoTime();
        if (finalizeNaturalEnd(s, now)) {
            notifyState(s, now);
            return false;
        }

        FinitePlaybackStateMachine.SeekResult result = s.playback.seek(seconds, now);
        if (!result.accepted()) return false;
        if (result.ended()) {
            releaseAssetReference(s);
            terminalStatus = statusOf(s, now);
            notifyState(s, now);
            return true;
        }

        if (s.decodeRevision == Long.MAX_VALUE) {
            failServerSession(s, "finite decoder revision exhausted");
            return false;
        }
        s.decodeRevision++;
        notifyState(s, now);
        return true;
    }

    public synchronized boolean setVolume(double volume) throws LuaException {
        if (!Double.isFinite(volume)) throw new LuaException("volume must be finite");
        Session s = session;
        if (s == null || s.playback.terminal()) return false;
        long now = System.nanoTime();
        if (finalizeNaturalEnd(s, now)) {
            notifyState(s, now);
            return false;
        }
        if (!s.playback.setVolume(volume)) return false;
        notifyState(s, now);
        return true;
    }

    public synchronized boolean setLooping(boolean looping) {
        Session s = session;
        if (s == null || s.playback.terminal()) return false;
        long now = System.nanoTime();
        if (finalizeNaturalEnd(s, now)) {
            notifyState(s, now);
            return false;
        }
        if (!s.playback.setLooping(looping, now)) return false;
        notifyState(s, now);
        return true;
    }

    public synchronized void stop() {
        Session s = session;
        if (s == null) return;

        sendStopToAdmitted(s);
        s.listeners.clear();
        releaseAssetReference(s);
        terminalStatus = null;
        session = null;
        queueStateEvent(idleStatus());
    }

    public synchronized Map<String, Object> status() {
        Session s = session;
        if (s == null) {
            if (terminalStatus != null) return new HashMap<>(terminalStatus);
            return idleStatus();
        }
        long now = System.nanoTime();
        if (finalizeNaturalEnd(s, now)) notifyState(s, now);
        return statusOf(s, now);
    }

    public synchronized boolean hasStatus() { return session != null || terminalStatus != null; }

    public void cleanup() {
        stop();
        attachedComputers.clear();
        ACTIVE.remove(this);
        BY_SOURCE.remove(source, this);
    }

    private synchronized void tick() {
        Session s = session;
        if (s == null) return;
        long now = System.nanoTime();
        if (s.playback.terminal()) {
            if (!s.listeners.isEmpty()) {
                sendStateToAdmitted(s, now, false);
                s.listeners.clear();
            }
            return;
        }
        if (finalizeNaturalEnd(s, now)) notifyState(s, now);
        else refreshListeners(s);
    }

    private boolean finalizeNaturalEnd(Session s, long now) {
        if (!s.playback.finalizeNaturalEnd(now)) return false;
        releaseAssetReference(s);
        terminalStatus = statusOf(s, now);
        return true;
    }

    private synchronized void acceptRangeRequest0(ServerPlayer player, HQFiniteMediaRangeRequestPacket packet) {
        Session s = session;
        if (s == null || player == null || s.playback.terminal() || s.playback.volume() <= 0.0f) return;
        if (!FiniteRangeValidation.requestMatches(
                source, s.mediaId, s.generation, s.totalBytes,
                packet.source(), packet.assetId(), packet.generation(), packet.offset(), packet.length())) return;
        if (!s.listeners.contains(player.getUUID()) || !isRelevant(player)) return;

        UUID playerId = player.getUUID();
        UUID assetId = s.mediaId;
        long generation = s.generation;
        int requestedLength = packet.length();
        FiniteRangeReadService.Submission submission = s.rangeReads.submit(
            playerId, assetId, s.totalBytes, packet.offset(), requestedLength,
            result -> {
                if (!ACTIVE.contains(this)) return;
                level.getServer().execute(() -> completeRange(playerId, assetId, generation, requestedLength, result));
            }
        );

        if (submission == FiniteRangeReadService.Submission.CLOSED
                || submission == FiniteRangeReadService.Submission.UNKNOWN_ASSET) {
            failServerSession(s, "media range service lost the active asset");
        }
    }

    private synchronized void completeRange(UUID playerId, UUID assetId, long generation, int requestedLength,
                                            FiniteRangeReadService.ReadResult result) {
        Session s = session;
        if (s == null || s.playback.terminal() || s.playback.volume() <= 0.0f) return;
        if (!FiniteRangeValidation.completionMatches(s.mediaId, s.generation, assetId, generation)) return;

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (player == null || !s.listeners.contains(playerId) || !isRelevant(player)) return;
        if (!result.success()) {
            failServerSession(s, "media range read failed: " + result.error());
            return;
        }
        if (result.data().length != requestedLength || result.offset() < 0L
                || result.offset() + result.data().length > s.totalBytes) {
            failServerSession(s, "media range read returned invalid bounds");
            return;
        }

        projectToClients(HQFiniteMediaRangeDataPacket.class.getSimpleName(), () ->
            HQSpeakerNetwork.sendToPlayer(new HQFiniteMediaRangeDataPacket(
                source, assetId, generation, result.offset(), result.data()), player));
    }

    private synchronized void acceptStatus0(ServerPlayer player, HQFiniteMediaStatusPacket packet) {
        Session s = session;
        if (s == null || player == null || packet.generation() != s.generation
                || !s.listeners.contains(player.getUUID()) || !isRelevant(player)) return;

        if (packet.transition() == HQFiniteMediaStatusPacket.Transition.READY) {
            long now = System.nanoTime();
            if (finalizeNaturalEnd(s, now)) notifyState(s, now);
            else sendState(s, player);
            return;
        }

        if (packet.transition() == HQFiniteMediaStatusPacket.Transition.ERROR) {
            String detail = packet.error() == null || packet.error().isBlank() ? "client transport error" : packet.error();
            HQSpeakerMod.warn("finite client diagnostic from " + player.getUUID() + " for generation "
                + s.generation + ": " + detail);
        }
    }

    private void failServerSession(Session s, String error) {
        if (session != s || s.playback.terminal()) return;
        long now = System.nanoTime();
        if (finalizeNaturalEnd(s, now)) {
            notifyState(s, now);
            return;
        }
        if (!s.playback.fail(error, now)) return;
        releaseAssetReference(s);
        terminalStatus = statusOf(s, now);
        notifyState(s, now);
    }

    private void refreshListeners(Session s) {
        if (session != s || s.playback.terminal()) return;

        float[] world = computeWorldPos();
        Map<UUID, ServerPlayer> relevantPlayers = new HashMap<>();
        for (ServerPlayer player : level.players()) {
            if (isRelevant(player, world)) relevantPlayers.put(player.getUUID(), player);
        }

        FiniteListenerMembership.Delta delta = s.listeners.plan(relevantPlayers.keySet());

        for (UUID playerId : delta.left()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                s.listeners.remove(playerId);
                continue;
            }
            if (isRelevant(player, world)) continue;
            if (sendStop(s, player)) s.listeners.remove(playerId);
        }

        HQFiniteMediaBeginPacket begin = delta.joined().isEmpty() ? null : beginPacket(s, world);
        for (UUID playerId : delta.joined()) {
            ServerPlayer player = relevantPlayers.get(playerId);
            if (player == null || !isRelevant(player, world)) continue;
            if (!sendPacketToPlayer("BEGIN", begin, player)) continue;
            s.listeners.admit(playerId);
            sendState(s, player);
        }
    }

    private HQFiniteMediaBeginPacket beginPacket(Session s, float[] world) {
        return new HQFiniteMediaBeginPacket(source, s.mediaId, s.generation, s.descriptor,
            s.playback.volume(), world[0], world[1], world[2], pos.getX(), pos.getY(), pos.getZ(),
            s.totalBytes, s.playback.looping(), s.playback.state() == FinitePlaybackStateMachine.State.PAUSED);
    }

    private boolean sendStop(Session s, ServerPlayer player) {
        return sendPacketToPlayer("STOP",
            new HQFiniteMediaControlPacket(source, s.generation, HQFiniteMediaControlPacket.Action.STOP, 0.0), player);
    }

    private void sendStopToAdmitted(Session s) {
        for (UUID playerId : s.listeners.snapshot()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player != null) sendStop(s, player);
        }
    }

    private void sendStateToAdmitted(Session s, long now, boolean relevantOnly) {
        HQFiniteMediaStatePacket packet = statePacket(s, now);
        for (UUID playerId : s.listeners.snapshot()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
            if (player == null) {
                s.listeners.remove(playerId);
                continue;
            }
            if (relevantOnly && !isRelevant(player)) continue;
            sendPacketToPlayer("STATE", packet, player);
        }
    }

    private void sendState(Session s, ServerPlayer player) {
        if (player == null || !s.listeners.contains(player.getUUID()) || !isRelevant(player)) return;
        sendPacketToPlayer("STATE", statePacket(s, System.nanoTime()), player);
    }

    private HQFiniteMediaStatePacket statePacket(Session s, long now) {
        double position = s.playback.position(now);
        FiniteDecodeAnchorSelector.Anchor anchor = FiniteDecodeAnchorSelector.select(s.metadata, s.totalBytes, position);
        return new HQFiniteMediaStatePacket(
            source, s.mediaId, s.generation, s.decodeRevision, wireState(s.playback.state()),
            position, s.playback.duration(), s.playback.volume(), s.playback.looping(),
            anchor.offset(), anchor.seconds(), s.playback.error()
        );
    }

    private void notifyState(Session s, long now) {
        boolean terminal = s.playback.terminal();
        sendStateToAdmitted(s, now, !terminal);
        if (terminal) s.listeners.clear();
        queueStateEvent(statusOf(s, now));
    }

    private boolean sendPacketToPlayer(String what, CustomPacketPayload packet, ServerPlayer player) {
        if (packet == null || player == null) return false;
        return projectToClients(what + " player=" + player.getUUID(), () ->
            HQSpeakerNetwork.sendToPlayer(packet, player));
    }

    private boolean projectToClients(String what, Runnable projection) {
        return BestEffortProjection.run(projection, failure ->
            HQSpeakerMod.warn("finite media client projection failed source=" + source + " " + what + ": "
                + safeMessage(failure)));
    }

    private boolean isRelevant(ServerPlayer player) {
        return isRelevant(player, computeWorldPos());
    }

    private boolean isRelevant(ServerPlayer player, float[] world) {
        if (player == null || world == null || world.length < 3) return false;
        double dx = player.getX() - world[0], dy = player.getY() - world[1], dz = player.getZ() - world[2];
        double distanceSquared = dx * dx + dy * dy + dz * dz;
        return FiniteRangeValidation.listenerRelevant(
            player.level() == level, player.isRemoved(), distanceSquared, SPEAKER_RADIUS);
    }

    private Map<String, Object> statusOf(Session s, long now) {
        Map<String, Object> out = new HashMap<>();
        out.put("generation", s.generation);
        out.put("state", s.playback.state().name().toLowerCase(Locale.ROOT));
        out.put("kind", "finite");
        out.put("format", s.metadata.format().id());
        out.put("position", s.playback.position(now));
        out.put("duration", s.metadata.durationSeconds());
        out.put("sampleRate", s.metadata.sampleRate());
        out.put("channels", s.metadata.channels());
        out.put("bitsPerSample", s.metadata.bitsPerSample());
        out.put("volume", (double) s.playback.volume());
        out.put("looping", s.playback.looping());
        out.put("totalBytes", s.totalBytes);
        out.put("assetId", s.retainedAssetId.toString());
        out.put("canPause", !s.playback.terminal());
        out.put("canSeek", !s.playback.terminal());
        out.put("canLoop", !s.playback.terminal());
        if (!s.playback.error().isBlank()) out.put("error", s.playback.error());
        return out;
    }

    private static Map<String, Object> idleStatus() {
        Map<String, Object> idle = new HashMap<>();
        idle.put("state", "idle");
        idle.put("kind", "finite");
        idle.put("position", 0.0);
        idle.put("canPause", false);
        idle.put("canSeek", false);
        idle.put("canLoop", false);
        return idle;
    }

    private void queueStateEvent(Map<String, Object> state) {
        BestEffortProjection.run(() -> {
            for (IComputerAccess computer : attachedComputers) {
                try { computer.queueEvent("hqspeaker_audio_state", new HashMap<>(state)); }
                catch (RuntimeException ignored) {}
            }
        }, failure -> HQSpeakerMod.warn("finite media Lua state projection failed source=" + source + ": "
            + safeMessage(failure)));
    }

    private static HQFiniteMediaStatePacket.PlaybackState wireState(FinitePlaybackStateMachine.State state) {
        return switch (state) {
            case PLAYING -> HQFiniteMediaStatePacket.PlaybackState.PLAYING;
            case PAUSED -> HQFiniteMediaStatePacket.PlaybackState.PAUSED;
            case ENDED -> HQFiniteMediaStatePacket.PlaybackState.ENDED;
            case ERROR -> HQFiniteMediaStatePacket.PlaybackState.ERROR;
        };
    }

    private float[] computeWorldPos() {
        float x = pos.getX() + 0.5f, y = pos.getY() + 0.5f, z = pos.getZ() + 0.5f;
        try {
            if (VS2TransformHelper.isVS2Loaded()) {
                Object ship = VS2TransformHelper.getShipManagingBlock(level, pos);
                if (ship != null) {
                    Matrix4dc matrix = VS2TransformHelper.getShipToWorldMatrix(ship);
                    if (matrix != null) {
                        Vector3d point = new Vector3d(x, y, z);
                        matrix.transformPosition(point);
                        x = (float) point.x; y = (float) point.y; z = (float) point.z;
                    }
                }
            }
        } catch (Exception e) {
            HQSpeakerMod.warn("finite media position transform failed: " + safeMessage(e));
        }
        return new float[]{ x, y, z };
    }

    private void releaseAssetReference(Session s) {
        if (!s.assetReferenceHeld) return;
        MediaAssetReleaseQueue.Result result = s.releases.release(s.retainedAssetId);
        s.assetReferenceHeld = false;
        if (result.status() == MediaAssetReleaseQueue.Status.MISSING) {
            HQSpeakerMod.warn("playback media asset reference was already missing " + s.retainedAssetId);
        } else if (result.deferred()) {
            HQSpeakerMod.warn("playback media asset release queued for retry " + s.retainedAssetId
                + ": " + result.error());
        }
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
