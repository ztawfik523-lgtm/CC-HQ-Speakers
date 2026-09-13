package com.tom.hqspeaker.peripheral;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.media.FiniteMediaFormat;
import com.tom.hqspeaker.media.FinitePlaybackClock;
import com.tom.hqspeaker.media.FiniteRangeReadService;
import com.tom.hqspeaker.media.MediaAsset;
import com.tom.hqspeaker.media.MediaAssetStore;
import com.tom.hqspeaker.media.MediaMetadata;
import com.tom.hqspeaker.media.MediaSeekPoint;
import com.tom.hqspeaker.media.ServerMediaAssets;
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

/** Server-authoritative finite playback state plus M1F demand-driven encoded range transport. */
public final class HQFiniteMediaServer {
    private static final double SPEAKER_RADIUS = 32.0;

    private static final Set<HQFiniteMediaServer> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, HQFiniteMediaServer> BY_SOURCE = new ConcurrentHashMap<>();

    private enum State { PLAYING, PAUSED, ENDED, ERROR }
    private record Anchor(long offset, double seconds) {}

    private static final class Session {
        final UUID mediaId;
        final long generation;
        final HQFiniteMediaBeginPacket.MediaFormat format;
        final MediaMetadata metadata;
        final long totalBytes;
        final FinitePlaybackClock clock;
        final MediaAssetStore assetStore;
        final FiniteRangeReadService rangeReads;
        final UUID retainedAssetId;
        float volume;
        boolean assetReferenceHeld;
        State state;
        String error = "";

        Session(UUID mediaId, long generation, MediaMetadata metadata, long totalBytes,
                MediaAssetStore assetStore, FiniteRangeReadService rangeReads, float volume) {
            this.mediaId = mediaId;
            this.generation = generation;
            this.metadata = metadata;
            this.format = wireFormat(metadata.format());
            this.totalBytes = totalBytes;
            this.assetStore = assetStore;
            this.rangeReads = rangeReads;
            this.retainedAssetId = mediaId;
            this.assetReferenceHeld = true;
            this.volume = volume;
            this.clock = new FinitePlaybackClock(false);
            long now = System.nanoTime();
            this.clock.setDuration(metadata.durationSeconds(), now);
            this.clock.start(now);
            this.state = State.PLAYING;
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
            catch (Exception e) { HQSpeakerMod.warn("finite media tick failed at " + server.pos + ": " + e.getMessage()); }
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

    /** Play one reusable analyzed media asset, retaining a playback reference until stop/end/error. */
    public synchronized boolean playPrepared(String assetId, double volume) throws LuaException {
        if (isActive()) return false;
        if (!Double.isFinite(volume)) throw new LuaException("volume must be finite");
        float appliedVolume = (float) Math.max(0.0, Math.min(3.0, volume));
        UUID id = HQMediaStaging.parseAssetId(assetId);
        MediaAssetStore store = staging.assetStore();
        MediaAsset asset = store.get(id).orElseThrow(() -> new LuaException("unknown or released media asset"));
        MediaMetadata metadata = asset.metadata();
        if (metadata == null) throw new LuaException("media asset has not been analyzed");

        FiniteRangeReadService rangeReads;
        try {
            rangeReads = ServerMediaAssets.get(level.getServer()).rangeReads();
        } catch (IOException e) {
            throw new LuaException("HQ media range service is unavailable: " + safeMessage(e));
        }

        if (!store.retain(id)) throw new LuaException("unknown or released media asset");
        try {
            long generation = ++generationCounter;
            Session next = new Session(id, generation, metadata, asset.sizeBytes(), store, rangeReads, appliedVolume);
            session = next;
            terminalStatus = null;
            sendBeginToRelevant(next);
            sendStateToRelevant(next);
            queueStateEvent(statusOf(next, System.nanoTime()));
            return true;
        } catch (RuntimeException e) {
            try { store.release(id); }
            catch (IOException releaseFailure) { e.addSuppressed(releaseFailure); }
            throw e;
        }
    }

    public synchronized boolean isActive() {
        return session != null && (session.state == State.PLAYING || session.state == State.PAUSED);
    }

    public synchronized boolean pause() {
        Session s = session;
        if (s == null) return false;
        long now = System.nanoTime();
        if (finalizeNaturalEnd(s, now)) {
            notifyState(s, now);
            return false;
        }
        if (s.state != State.PLAYING) return false;
        s.clock.pause(now);
        s.state = State.PAUSED;
        sendControlToRelevant(s, HQFiniteMediaControlPacket.Action.PAUSE, 0.0);
        notifyState(s, now);
        return true;
    }

    public synchronized boolean resume() {
        Session s = session;
        if (s == null || s.state != State.PAUSED) return false;
        long now = System.nanoTime();
        s.clock.resume(now);
        s.state = State.PLAYING;
        sendControlToRelevant(s, HQFiniteMediaControlPacket.Action.RESUME, 0.0);
        notifyState(s, now);
        return true;
    }

    public synchronized boolean seek(double seconds) throws LuaException {
        if (!Double.isFinite(seconds)) throw new LuaException("seconds must be finite");
        Session s = session;
        if (s == null || s.state == State.ERROR || s.state == State.ENDED || s.clock.duration() <= 0.0) return false;
        long now = System.nanoTime();
        if (finalizeNaturalEnd(s, now)) {
            notifyState(s, now);
            return false;
        }
        double target = s.clock.seek(seconds, now);
        if (!s.clock.looping() && target >= s.clock.duration()) {
            s.clock.finish(now);
            s.state = State.ENDED;
            releaseAssetReference(s);
            terminalStatus = statusOf(s, now);
            notifyState(s, now);
            return true;
        }
        sendControlToRelevant(s, HQFiniteMediaControlPacket.Action.SEEK, target);
        notifyState(s, now);
        return true;
    }

    public synchronized boolean setVolume(double volume) throws LuaException {
        if (!Double.isFinite(volume)) throw new LuaException("volume must be finite");
        Session s = session;
        if (s == null || s.state == State.ERROR || s.state == State.ENDED) return false;
        long now = System.nanoTime();
        if (finalizeNaturalEnd(s, now)) {
            notifyState(s, now);
            return false;
        }
        s.volume = (float) Math.max(0.0, Math.min(3.0, volume));
        sendControlToRelevant(s, HQFiniteMediaControlPacket.Action.SET_VOLUME, s.volume);
        notifyState(s, now);
        return true;
    }

    public synchronized boolean setLooping(boolean looping) {
        Session s = session;
        if (s == null || s.state == State.ERROR || s.state == State.ENDED) return false;
        long now = System.nanoTime();
        if (finalizeNaturalEnd(s, now)) {
            notifyState(s, now);
            return false;
        }
        s.clock.setLooping(looping, now);
        sendControlToRelevant(s, HQFiniteMediaControlPacket.Action.SET_LOOP, looping ? 1.0 : 0.0);
        notifyState(s, now);
        return true;
    }

    public synchronized void stop() {
        Session s = session;
        if (s == null) return;
        sendControlToRelevant(s, HQFiniteMediaControlPacket.Action.STOP, 0.0);
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
        if (s == null || s.state == State.ENDED || s.state == State.ERROR) return;
        long now = System.nanoTime();
        if (finalizeNaturalEnd(s, now)) notifyState(s, now);
    }

    private boolean finalizeNaturalEnd(Session s, long now) {
        if (s.state != State.PLAYING || !s.clock.reachedEnd(now)) return false;
        s.clock.finish(now);
        s.state = State.ENDED;
        releaseAssetReference(s);
        terminalStatus = statusOf(s, now);
        return true;
    }

    private synchronized void acceptRangeRequest0(ServerPlayer player, HQFiniteMediaRangeRequestPacket packet) {
        Session s = session;
        if (s == null || player == null || s.state == State.ENDED || s.state == State.ERROR) return;
        if (packet.generation() != s.generation || !packet.assetId().equals(s.mediaId)) return;
        if (!isRelevant(player)) return;
        if (packet.offset() < 0L || packet.offset() >= s.totalBytes || packet.length() <= 0
                || packet.length() > s.totalBytes - packet.offset()) return;

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
        // OVER_LIMIT/BUSY are temporary admission failures. The bounded client window retries timed-out demand.
    }

    private synchronized void completeRange(UUID playerId, UUID assetId, long generation, int requestedLength,
                                            FiniteRangeReadService.ReadResult result) {
        Session s = session;
        if (s == null || s.state == State.ENDED || s.state == State.ERROR) return;
        if (s.generation != generation || !s.mediaId.equals(assetId)) return;

        ServerPlayer player = level.getServer().getPlayerList().getPlayer(playerId);
        if (player == null || !isRelevant(player)) return;
        if (!result.success()) {
            failServerSession(s, "media range read failed: " + result.error());
            return;
        }
        if (result.data().length != requestedLength || result.offset() < 0L
                || result.offset() + result.data().length > s.totalBytes) {
            failServerSession(s, "media range read returned invalid bounds");
            return;
        }

        HQSpeakerNetwork.sendToPlayer(new HQFiniteMediaRangeDataPacket(
            source, assetId, generation, result.offset(), result.data()), player);
    }

    private synchronized void acceptStatus0(ServerPlayer player, HQFiniteMediaStatusPacket packet) {
        Session s = session;
        if (s == null || player == null || packet.generation() != s.generation || !isRelevant(player)) return;

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
        if (session != s || s.state == State.ENDED || s.state == State.ERROR) return;
        s.state = State.ERROR;
        s.error = error;
        releaseAssetReference(s);
        long now = System.nanoTime();
        terminalStatus = statusOf(s, now);
        notifyState(s, now);
    }

    private void sendBeginToRelevant(Session s) {
        float[] world = computeWorldPos();
        HQFiniteMediaBeginPacket packet = new HQFiniteMediaBeginPacket(source, s.mediaId, s.generation, s.format,
            s.volume, world[0], world[1], world[2], pos.getX(), pos.getY(), pos.getZ(),
            s.totalBytes, s.clock.looping(), s.state == State.PAUSED);
        sendToRelevant(packet);
    }

    private void sendControlToRelevant(Session s, HQFiniteMediaControlPacket.Action action, double value) {
        sendToRelevant(new HQFiniteMediaControlPacket(source, s.generation, action, value));
    }

    private void sendStateToRelevant(Session s) {
        sendToRelevant(statePacket(s, System.nanoTime()));
    }

    private void sendState(Session s, ServerPlayer player) {
        if (player != null && isRelevant(player)) HQSpeakerNetwork.sendToPlayer(statePacket(s, System.nanoTime()), player);
    }

    private HQFiniteMediaStatePacket statePacket(Session s, long now) {
        double position = s.clock.position(now);
        Anchor anchor = selectAnchor(s, position);
        return new HQFiniteMediaStatePacket(
            source, s.mediaId, s.generation, wireState(s.state),
            position, s.clock.duration(), s.volume, s.clock.looping(),
            anchor.offset(), anchor.seconds(), s.error
        );
    }

    private Anchor selectAnchor(Session s, double position) {
        long bestOffset = 0L;
        double bestSeconds = 0.0;
        for (MediaSeekPoint point : s.metadata.seekPoints()) {
            if (point.seconds() <= position + 1.0e-9
                    && point.byteOffset() >= 0L && point.byteOffset() < s.totalBytes
                    && point.seconds() >= bestSeconds) {
                bestOffset = point.byteOffset();
                bestSeconds = point.seconds();
            }
        }
        return new Anchor(bestOffset, bestSeconds);
    }

    private void notifyState(Session s, long now) {
        sendStateToRelevant(s);
        queueStateEvent(statusOf(s, now));
    }

    private void sendToRelevant(net.minecraft.network.protocol.common.custom.CustomPacketPayload packet) {
        for (ServerPlayer player : level.players()) {
            if (isRelevant(player)) HQSpeakerNetwork.sendToPlayer(packet, player);
        }
    }

    private boolean isRelevant(ServerPlayer player) {
        if (player == null || player.level() != level || player.isRemoved()) return false;
        float[] p = computeWorldPos();
        double dx = player.getX() - p[0], dy = player.getY() - p[1], dz = player.getZ() - p[2];
        return dx * dx + dy * dy + dz * dz <= SPEAKER_RADIUS * SPEAKER_RADIUS;
    }

    private Map<String, Object> statusOf(Session s, long now) {
        Map<String, Object> out = new HashMap<>();
        out.put("generation", s.generation);
        out.put("state", s.state.name().toLowerCase(Locale.ROOT));
        out.put("kind", "finite");
        out.put("format", s.metadata.format().id());
        out.put("position", s.clock.position(now));
        out.put("duration", s.metadata.durationSeconds());
        out.put("sampleRate", s.metadata.sampleRate());
        out.put("channels", s.metadata.channels());
        out.put("bitsPerSample", s.metadata.bitsPerSample());
        out.put("volume", (double) s.volume);
        out.put("looping", s.clock.looping());
        out.put("totalBytes", s.totalBytes);
        out.put("assetId", s.retainedAssetId.toString());
        out.put("canPause", s.state != State.ENDED && s.state != State.ERROR);
        out.put("canSeek", s.state != State.ENDED && s.state != State.ERROR);
        out.put("canLoop", s.state != State.ENDED && s.state != State.ERROR);
        if (!s.error.isBlank()) out.put("error", s.error);
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
        for (IComputerAccess computer : attachedComputers) {
            try { computer.queueEvent("hqspeaker_audio_state", new HashMap<>(state)); }
            catch (RuntimeException ignored) {}
        }
    }

    private static HQFiniteMediaBeginPacket.MediaFormat wireFormat(FiniteMediaFormat format) {
        return switch (format) {
            case MP3 -> HQFiniteMediaBeginPacket.MediaFormat.MP3;
            case OGG_VORBIS -> HQFiniteMediaBeginPacket.MediaFormat.OGG;
            case WAV, AIFF, AU -> HQFiniteMediaBeginPacket.MediaFormat.AUDIO_FILE;
        };
    }

    private static HQFiniteMediaStatePacket.PlaybackState wireState(State state) {
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
            HQSpeakerMod.warn("finite media position transform failed: " + e.getMessage());
        }
        return new float[]{ x, y, z };
    }

    private void releaseAssetReference(Session s) {
        if (!s.assetReferenceHeld) return;
        s.assetReferenceHeld = false;
        try {
            s.assetStore.release(s.retainedAssetId);
        } catch (IOException e) {
            HQSpeakerMod.warn("could not release playback media asset " + s.retainedAssetId + ": " + e.getMessage());
        }
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
