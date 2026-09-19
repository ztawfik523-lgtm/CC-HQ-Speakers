package com.tom.hqspeaker.peripheral;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.compat.MovingSourcePosition;
import com.tom.hqspeaker.media.FiniteDecodeAnchorSelector;
import com.tom.hqspeaker.media.FiniteDecodeDescriptor;
import com.tom.hqspeaker.media.FinitePlaybackAuthority;
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
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Vector3d;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
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

    /**
     * State shared by every physical endpoint participating in one finite playback.
     *
     * <p>M1J deliberately keeps listener membership, endpoint gain and renderer transport outside this object.</p>
     */
    private static final class SharedPlayback {
        final UUID mediaId;
        final FiniteDecodeDescriptor descriptor;
        final MediaMetadata metadata;
        final long totalBytes;
        final FinitePlaybackAuthority playback;
        final FiniteRangeReadService rangeReads;
        final MediaAssetReleaseQueue releases;
        final UUID retainedAssetId;
        private boolean assetReferenceHeld = true;

        SharedPlayback(UUID mediaId, MediaMetadata metadata, long totalBytes,
                       FiniteRangeReadService rangeReads, MediaAssetReleaseQueue releases,
                       long nowNanos) {
            this.mediaId = mediaId;
            this.metadata = metadata;
            this.descriptor = FiniteDecodeDescriptor.fromMetadata(metadata);
            this.totalBytes = totalBytes;
            this.rangeReads = rangeReads;
            this.releases = releases;
            this.retainedAssetId = mediaId;
            this.playback = new FinitePlaybackAuthority(metadata.durationSeconds(), nowNanos);
            FiniteDecodeAnchorSelector.select(metadata, totalBytes, 0.0);
        }

        private final Set<HQFiniteMediaServer> endpoints = ConcurrentHashMap.newKeySet();

        void attachEndpoint(HQFiniteMediaServer endpoint) {
            endpoints.add(endpoint);
        }

        void detachEndpoint(HQFiniteMediaServer endpoint) {
            endpoints.remove(endpoint);
            if (endpoints.isEmpty()) logReleaseResult(releaseAssetReference(), retainedAssetId,
                "empty shared finite playback");
        }

        void notifyEndpoints(long nowNanos) {
            for (HQFiniteMediaServer endpoint : new ArrayList<>(endpoints)) {
                endpoint.notifySharedState(this, nowNanos);
            }
        }

        void stopAllEndpoints() {
            for (HQFiniteMediaServer endpoint : new ArrayList<>(endpoints)) {
                endpoint.stopSharedEndpoint(this);
            }
        }

        synchronized MediaAssetReleaseQueue.Result releaseAssetReference() {
            if (!assetReferenceHeld) return null;
            MediaAssetReleaseQueue.Result result = releases.release(retainedAssetId);
            assetReferenceHeld = false;
            return result;
        }
    }

    private static final class Session {
        final SharedPlayback shared;
        final UUID mediaId;
        final long generation;
        final FiniteDecodeDescriptor descriptor;
        final MediaMetadata metadata;
        final long totalBytes;
        final FinitePlaybackAuthority playback;
        float volume;
        final FiniteRangeReadService rangeReads;
        final MediaAssetReleaseQueue releases;
        final UUID retainedAssetId;
        final FiniteListenerMembership listeners = new FiniteListenerMembership();

        Session(SharedPlayback shared, long generation, double volume) {
            this.shared = shared;
            this.mediaId = shared.mediaId;
            this.generation = generation;
            this.metadata = shared.metadata;
            this.descriptor = shared.descriptor;
            this.totalBytes = shared.totalBytes;
            this.rangeReads = shared.rangeReads;
            this.releases = shared.releases;
            this.retainedAssetId = shared.retainedAssetId;
            this.playback = shared.playback;
            this.volume = clampVolume(volume);
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
            MediaAssetReleaseQueue.Result release = next.shared.releaseAssetReference();
            if (release != null && release.deferred()) {
                HQSpeakerMod.warn("uncommitted prepared start release queued for retry " + next.retainedAssetId
                    + ": " + release.error());
            }
        }
    }

    static final class PreparedGroupStart implements AutoCloseable {
        private final SharedPlayback shared;
        private final Map<HQFiniteMediaServer, Session> nextByServer;
        private boolean finished;

        private PreparedGroupStart(SharedPlayback shared, Map<HQFiniteMediaServer, Session> nextByServer) {
            this.shared = shared;
            this.nextByServer = nextByServer;
        }

        @Override
        public void close() {
            if (finished) return;
            finished = true;
            logReleaseResult(shared.releaseAssetReference(), shared.retainedAssetId,
                "uncommitted prepared multispeaker playback");
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
            SharedPlayback shared = new SharedPlayback(id, metadata, asset.sizeBytes(),
                mediaAssets.rangeReads(), mediaAssets.releases(), now);
            Session next = new Session(shared, generation, volume);
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

    PreparedGroupStart preparePreparedGroupStart(List<HQFiniteMediaServer> targets,
                                                       String assetId, double volume) throws LuaException {
        if (!Double.isFinite(volume)) throw new LuaException("volume must be finite");
        if (targets == null || targets.isEmpty()) throw new LuaException("no speakers connected to this computer");

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

        SharedPlayback shared = null;
        try {
            long now = System.nanoTime();
            shared = new SharedPlayback(id, metadata, asset.sizeBytes(),
                mediaAssets.rangeReads(), mediaAssets.releases(), now);

            Map<HQFiniteMediaServer, Session> next = new LinkedHashMap<>();
            for (HQFiniteMediaServer target : targets) {
                if (target == null || target.level.getServer() != level.getServer()) {
                    throw new LuaException("all multispeaker endpoints must belong to the same Minecraft server");
                }
                synchronized (target) {
                    long generation = target.generationCounter + 1L;
                    if (generation <= 0L) throw new IllegalStateException("finite generation exhausted");
                    next.put(target, new Session(shared, generation, volume));
                }
            }
            if (next.isEmpty()) throw new LuaException("no speakers connected to this computer");
            return new PreparedGroupStart(shared, next);
        } catch (LuaException | RuntimeException e) {
            MediaAssetReleaseQueue.Result release = shared == null
                ? mediaAssets.releases().release(id)
                : shared.releaseAssetReference();
            logReleaseResult(release, id, "failed prepared multispeaker playback");
            throw e;
        }
    }

    static boolean commitPreparedGroupStart(PreparedGroupStart prepared) {
        if (prepared == null || prepared.finished) {
            throw new IllegalArgumentException("invalid or consumed prepared multispeaker start");
        }

        // Validate every endpoint before installing any of them.
        for (Map.Entry<HQFiniteMediaServer, Session> entry : prepared.nextByServer.entrySet()) {
            HQFiniteMediaServer endpoint = entry.getKey();
            Session next = entry.getValue();
            synchronized (endpoint) {
                if (endpoint.isActive()) return false;
                if (next.generation != endpoint.generationCounter + 1L) {
                    throw new IllegalStateException("prepared multispeaker start became stale");
                }
            }
        }

        long now = System.nanoTime();
        for (Map.Entry<HQFiniteMediaServer, Session> entry : prepared.nextByServer.entrySet()) {
            HQFiniteMediaServer endpoint = entry.getKey();
            Session next = entry.getValue();
            synchronized (endpoint) {
                endpoint.generationCounter = next.generation;
                endpoint.session = next;
                endpoint.terminalStatus = null;
                prepared.shared.attachEndpoint(endpoint);
            }
        }
        prepared.finished = true;

        // Projection is intentionally after every endpoint is installed: listeners can never observe a half-built group.
        for (Map.Entry<HQFiniteMediaServer, Session> entry : prepared.nextByServer.entrySet()) {
            HQFiniteMediaServer endpoint = entry.getKey();
            Session next = entry.getValue();
            synchronized (endpoint) {
                endpoint.refreshListeners(next);
                endpoint.queueStateEvent(endpoint.statusOf(next, now));
            }
        }
        return true;
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
        prepared.next.shared.attachEndpoint(this);
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

    public boolean sharesPlaybackWith(HQFiniteMediaServer other) {
        if (other == null) return false;
        SharedPlayback mine;
        synchronized (this) {
            mine = session == null ? null : session.shared;
        }
        if (mine == null) return false;
        synchronized (other) {
            return other.session != null && other.session.shared == mine;
        }
    }

    public synchronized boolean pause() {
        Session s = session;
        if (s == null) return false;
        long now = System.nanoTime();
        if (finalizeNaturalEnd(s, now)) {
            s.shared.notifyEndpoints(now);
            return false;
        }
        if (!s.playback.pause(now)) return false;
        s.shared.notifyEndpoints(now);
        return true;
    }

    public synchronized boolean resume() {
        Session s = session;
        if (s == null) return false;
        long now = System.nanoTime();
        if (!s.playback.resume(now)) return false;
        s.shared.notifyEndpoints(now);
        return true;
    }

    public synchronized boolean seek(double seconds) throws LuaException {
        if (!Double.isFinite(seconds)) throw new LuaException("seconds must be finite");
        Session s = session;
        if (s == null || s.playback.terminal() || s.playback.duration() <= 0.0) return false;
        long now = System.nanoTime();
        if (finalizeNaturalEnd(s, now)) {
            s.shared.notifyEndpoints(now);
            return false;
        }

        FinitePlaybackAuthority.SeekResult result = s.playback.seek(seconds, now);
        if (!result.accepted()) {
            if (s.playback.state() == FinitePlaybackAuthority.State.ERROR) {
                releaseAssetReference(s);
                s.shared.notifyEndpoints(now);
            }
            return false;
        }
        if (result.ended()) {
            releaseAssetReference(s);
            s.shared.notifyEndpoints(now);
            return true;
        }

        s.shared.notifyEndpoints(now);
        return true;
    }

    public synchronized boolean setVolume(double volume) throws LuaException {
        if (!Double.isFinite(volume)) throw new LuaException("volume must be finite");
        Session s = session;
        if (s == null || s.playback.terminal()) return false;
        long now = System.nanoTime();
        if (finalizeNaturalEnd(s, now)) {
            s.shared.notifyEndpoints(now);
            return false;
        }
        s.volume = clampVolume(volume);
        notifyState(s, now);
        return true;
    }

    public synchronized boolean setLooping(boolean looping) {
        Session s = session;
        if (s == null || s.playback.terminal()) return false;
        long now = System.nanoTime();
        if (finalizeNaturalEnd(s, now)) {
            s.shared.notifyEndpoints(now);
            return false;
        }
        if (!s.playback.setLooping(looping, now)) return false;
        s.shared.notifyEndpoints(now);
        return true;
    }

    /** Detach only this physical endpoint. Used by physical replacement/removal. */
    public synchronized void stop() {
        Session s = session;
        if (s == null) return;

        sendStopToAdmitted(s);
        s.listeners.clear();
        terminalStatus = null;
        session = null;
        s.shared.detachEndpoint(this);
        queueStateEvent(idleStatus());
    }

    /** Stop the whole shared playback, including every currently attached physical endpoint. */
    public void stopPlayback() {
        SharedPlayback shared;
        synchronized (this) {
            Session s = session;
            if (s == null) return;
            shared = s.shared;
        }
        shared.stopAllEndpoints();
    }

    private synchronized void stopSharedEndpoint(SharedPlayback shared) {
        Session s = session;
        if (s == null || s.shared != shared) return;
        sendStopToAdmitted(s);
        s.listeners.clear();
        terminalStatus = null;
        session = null;
        shared.detachEndpoint(this);
        queueStateEvent(idleStatus());
    }

    private synchronized void notifySharedState(SharedPlayback shared, long nowNanos) {
        Session s = session;
        if (s == null || s.shared != shared) return;
        notifyState(s, nowNanos);
    }

    public synchronized Map<String, Object> status() {
        Session s = session;
        if (s == null) {
            if (terminalStatus != null) return new HashMap<>(terminalStatus);
            return idleStatus();
        }
        long now = System.nanoTime();
        if (finalizeNaturalEnd(s, now)) s.shared.notifyEndpoints(now);
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
        if (finalizeNaturalEnd(s, now)) s.shared.notifyEndpoints(now);
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
        if (s == null || player == null || s.playback.terminal() || s.volume <= 0.0f) return;
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
        if (s == null || s.playback.terminal() || s.volume <= 0.0f) return;
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
            if (finalizeNaturalEnd(s, now)) s.shared.notifyEndpoints(now);
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
            s.shared.notifyEndpoints(now);
            return;
        }
        if (!s.playback.fail(error, now)) return;
        releaseAssetReference(s);
        s.shared.notifyEndpoints(now);
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
            s.volume, world[0], world[1], world[2], pos.getX(), pos.getY(), pos.getZ(),
            s.totalBytes, s.playback.looping(), s.playback.state() == FinitePlaybackAuthority.State.PAUSED);
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
        FinitePlaybackAuthority.Snapshot playback = s.playback.snapshot(now);
        FiniteDecodeAnchorSelector.Anchor anchor =
            FiniteDecodeAnchorSelector.select(s.metadata, s.totalBytes, playback.position());
        return new HQFiniteMediaStatePacket(
            source, s.mediaId, s.generation, playback.decodeRevision(), wireState(playback.state()),
            playback.position(), playback.duration(), s.volume, playback.looping(),
            anchor.offset(), anchor.seconds(), playback.error()
        );
    }

    private void notifyState(Session s, long now) {
        boolean terminal = s.playback.terminal();
        Map<String, Object> status = statusOf(s, now);
        if (terminal) terminalStatus = new HashMap<>(status);
        sendStateToAdmitted(s, now, !terminal);
        if (terminal) s.listeners.clear();
        queueStateEvent(status);
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
        double distanceSquared = player.distanceToSqr(world[0], world[1], world[2]);
        return FiniteRangeValidation.listenerRelevant(
            player.level() == level, player.isRemoved(), distanceSquared, SPEAKER_RADIUS);
    }

    private Map<String, Object> statusOf(Session s, long now) {
        FinitePlaybackAuthority.Snapshot playback = s.playback.snapshot(now);
        boolean terminal = playback.state() == FinitePlaybackAuthority.State.ENDED
            || playback.state() == FinitePlaybackAuthority.State.ERROR;

        Map<String, Object> out = new HashMap<>();
        out.put("generation", s.generation);
        out.put("playbackId", playback.playbackId().toString());
        out.put("stateRevision", playback.stateRevision());
        out.put("state", playback.state().name().toLowerCase(Locale.ROOT));
        out.put("kind", "finite");
        out.put("format", s.metadata.format().id());
        out.put("position", playback.position());
        out.put("duration", playback.duration());
        out.put("sampleRate", s.metadata.sampleRate());
        out.put("channels", s.metadata.channels());
        out.put("bitsPerSample", s.metadata.bitsPerSample());
        out.put("volume", (double) s.volume);
        out.put("looping", playback.looping());
        out.put("totalBytes", s.totalBytes);
        out.put("assetId", s.retainedAssetId.toString());
        out.put("canPause", !terminal);
        out.put("canSeek", !terminal);
        out.put("canLoop", !terminal);
        if (!playback.error().isBlank()) out.put("error", playback.error());
        return out;
    }

    private static float clampVolume(double volume) {
        return (float) Math.max(0.0, Math.min(3.0, volume));
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

    private static HQFiniteMediaStatePacket.PlaybackState wireState(FinitePlaybackAuthority.State state) {
        return switch (state) {
            case PLAYING -> HQFiniteMediaStatePacket.PlaybackState.PLAYING;
            case PAUSED -> HQFiniteMediaStatePacket.PlaybackState.PAUSED;
            case ENDED -> HQFiniteMediaStatePacket.PlaybackState.ENDED;
            case ERROR -> HQFiniteMediaStatePacket.PlaybackState.ERROR;
        };
    }

    private float[] computeWorldPos() {
        Vector3d world = MovingSourcePosition.resolve(level, pos, new Vector3d());
        return new float[]{ (float) world.x, (float) world.y, (float) world.z };
    }

    private void releaseAssetReference(Session s) {
        logReleaseResult(s.shared.releaseAssetReference(), s.retainedAssetId, "playback media asset");
    }

    private static void logReleaseResult(MediaAssetReleaseQueue.Result result, UUID assetId, String context) {
        if (result == null) return;
        if (result.status() == MediaAssetReleaseQueue.Status.MISSING) {
            HQSpeakerMod.warn(context + " reference was already missing " + assetId);
        } else if (result.deferred()) {
            HQSpeakerMod.warn(context + " release queued for retry " + assetId + ": " + result.error());
        }
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
