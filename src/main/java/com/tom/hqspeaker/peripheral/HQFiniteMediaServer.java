package com.tom.hqspeaker.peripheral;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.media.FinitePlaybackClock;
import com.tom.hqspeaker.media.MediaAsset;
import com.tom.hqspeaker.media.MediaAssetStore;
import com.tom.hqspeaker.network.HQFiniteMediaBeginPacket;
import com.tom.hqspeaker.network.HQFiniteMediaChunkPacket;
import com.tom.hqspeaker.network.HQFiniteMediaControlPacket;
import com.tom.hqspeaker.network.HQFiniteMediaEndPacket;
import com.tom.hqspeaker.network.HQFiniteMediaStatusPacket;
import com.tom.hqspeaker.network.HQSpeakerNetwork;
import com.tom.hqspeaker.vs2.VS2TransformHelper;
import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.joml.Matrix4dc;
import org.joml.Vector3d;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transitional finite playback sender.
 *
 * <p>M1C removes staging ownership from this class. It may still play the old direct-staged path for compatibility,
 * but prepared playback reads a reusable server-wide media asset. Fixed recipients/client renderer authority remain
 * prototype behavior scheduled for replacement by M1E/M1F.</p>
 */
public final class HQFiniteMediaServer {
    private static final double SPEAKER_RADIUS = 32.0;
    private static final int CHUNKS_PER_TICK = 2;
    private static final long UNOBSERVED_TIMEOUT_NANOS = 15_000_000_000L;

    private static final Set<HQFiniteMediaServer> ACTIVE = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, HQFiniteMediaServer> BY_SOURCE = new ConcurrentHashMap<>();

    private enum State { LOADING, PLAYING, PAUSED, ENDED, ERROR }

    private static final class Session {
        final UUID mediaId;
        final long generation;
        final HQFiniteMediaBeginPacket.MediaFormat format;
        final long totalBytes;
        final SeekableByteChannel channel;
        final Set<UUID> recipients;
        final Set<UUID> successfulRenderers = new HashSet<>();
        final FinitePlaybackClock clock;
        final long createdNanos = System.nanoTime();
        final boolean consume;
        final String stagedPath;
        final MediaAssetStore assetStore;
        final UUID retainedAssetId;
        float volume;
        long sentBytes;
        boolean transferComplete;
        boolean observed;
        boolean assetReferenceHeld;
        State state = State.LOADING;
        String error = "";

        Session(UUID mediaId, long generation, HQFiniteMediaBeginPacket.MediaFormat format, long totalBytes,
                SeekableByteChannel channel, Set<UUID> recipients, float volume, boolean looping,
                boolean consume, String stagedPath, MediaAssetStore assetStore, UUID retainedAssetId) {
            this.mediaId = mediaId;
            this.generation = generation;
            this.format = format;
            this.totalBytes = totalBytes;
            this.channel = channel;
            this.recipients = recipients;
            this.volume = volume;
            this.clock = new FinitePlaybackClock(looping);
            this.consume = consume;
            this.stagedPath = stagedPath;
            this.assetStore = assetStore;
            this.retainedAssetId = retainedAssetId;
            this.assetReferenceHeld = assetStore != null && retainedAssetId != null;
        }
    }

    private final Level level;
    private final BlockPos pos;
    private final UUID source = UUID.randomUUID();
    private final HQMediaStaging staging;
    private final Set<IComputerAccess> attachedComputers = ConcurrentHashMap.newKeySet();
    private long generationCounter;
    private Session session;
    private Map<String, Object> terminalStatus;

    public HQFiniteMediaServer(Level level, BlockPos pos, HQMediaStaging staging) {
        this.level = level;
        this.pos = pos.immutable();
        this.staging = staging;
        if (!(level instanceof ServerLevel)) throw new IllegalArgumentException("finite media server requires a server level");
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

    public void attach(IComputerAccess computer) {
        attachedComputers.add(computer);
    }

    public void detach(IComputerAccess computer) {
        attachedComputers.remove(computer);
    }

    /** Historical direct-staged entrypoint retained until the old prototype surface can be removed. */
    public synchronized boolean playStaged(IComputerAccess computer, String path, double volume, boolean consume) throws LuaException {
        if (isActive()) return false;
        if (!Double.isFinite(volume)) throw new LuaException("volume must be finite");
        float appliedVolume = (float) Math.max(0.0, Math.min(3.0, volume));

        HQMediaStaging.StagedFile staged = staging.openStaged(computer, path);
        SeekableByteChannel channel = staged.channel();
        try {
            long generation = ++generationCounter;
            Session next = new Session(
                UUID.randomUUID(), generation, detectFormat(staged.path()), staged.sizeBytes(), channel,
                collectRecipients(), appliedVolume, false, consume, staged.path(), null, null
            );
            session = next;
            terminalStatus = null;
            sendBegin(next);
            queueStateEvent();
            return true;
        } catch (RuntimeException e) {
            closeChannel(channel);
            throw e;
        }
    }

    /** Play one reusable media asset, retaining a playback reference until stop/end/error. */
    public synchronized boolean playPrepared(String assetId, double volume) throws LuaException {
        if (isActive()) return false;
        if (!Double.isFinite(volume)) throw new LuaException("volume must be finite");
        float appliedVolume = (float) Math.max(0.0, Math.min(3.0, volume));
        UUID id = HQMediaStaging.parseAssetId(assetId);
        MediaAssetStore store = staging.assetStore();
        MediaAsset asset = store.get(id).orElseThrow(() -> new LuaException("unknown or released media asset"));

        if (!store.retain(id)) throw new LuaException("unknown or released media asset");
        SeekableByteChannel channel = null;
        try {
            channel = store.openRead(id);
            long generation = ++generationCounter;
            Session next = new Session(
                id, generation, detectFormat(asset.sourceName()), asset.sizeBytes(), channel,
                collectRecipients(), appliedVolume, false, false, null, store, id
            );
            session = next;
            terminalStatus = null;
            sendBegin(next);
            queueStateEvent();
            return true;
        } catch (IOException e) {
            closeChannel(channel);
            try {
                store.release(id);
            } catch (IOException releaseFailure) {
                e.addSuppressed(releaseFailure);
            }
            throw new LuaException("cannot open prepared media: " + safeMessage(e));
        } catch (RuntimeException e) {
            closeChannel(channel);
            try {
                store.release(id);
            } catch (IOException releaseFailure) {
                e.addSuppressed(releaseFailure);
            }
            throw e;
        }
    }

    public synchronized boolean isActive() {
        return session != null && (session.state == State.LOADING || session.state == State.PLAYING || session.state == State.PAUSED);
    }

    public synchronized boolean pause() {
        Session s = session;
        if (s == null || (s.state != State.PLAYING && s.state != State.LOADING)) return false;
        long now = System.nanoTime();
        s.clock.pause(now);
        s.state = State.PAUSED;
        sendControl(s, HQFiniteMediaControlPacket.Action.PAUSE, 0.0);
        queueStateEvent();
        return true;
    }

    public synchronized boolean resume() {
        Session s = session;
        if (s == null || s.state != State.PAUSED) return false;
        s.clock.resume(System.nanoTime());
        s.state = s.observed ? State.PLAYING : State.LOADING;
        sendControl(s, HQFiniteMediaControlPacket.Action.RESUME, 0.0);
        queueStateEvent();
        return true;
    }

    public synchronized boolean seek(double seconds) throws LuaException {
        if (!Double.isFinite(seconds)) throw new LuaException("seconds must be finite");
        Session s = session;
        if (s == null || s.clock.duration() <= 0.0 || s.state == State.ERROR || s.state == State.ENDED) return false;
        long now = System.nanoTime();
        double target = s.clock.seek(seconds, now);
        if (!s.clock.looping() && target >= s.clock.duration()) {
            s.clock.finish(now);
            s.state = State.ENDED;
            releaseAssetReference(s);
        }
        sendControl(s, HQFiniteMediaControlPacket.Action.SEEK, target);
        queueStateEvent();
        return true;
    }

    public synchronized boolean setVolume(double volume) throws LuaException {
        if (!Double.isFinite(volume)) throw new LuaException("volume must be finite");
        Session s = session;
        if (s == null) return false;
        s.volume = (float) Math.max(0.0, Math.min(3.0, volume));
        sendControl(s, HQFiniteMediaControlPacket.Action.SET_VOLUME, s.volume);
        queueStateEvent();
        return true;
    }

    public synchronized boolean setLooping(boolean looping) {
        Session s = session;
        if (s == null || s.state == State.ERROR || s.state == State.ENDED) return false;
        s.clock.setLooping(looping, System.nanoTime());
        sendControl(s, HQFiniteMediaControlPacket.Action.SET_LOOP, looping ? 1.0 : 0.0);
        queueStateEvent();
        return true;
    }

    public synchronized void stop() {
        Session s = session;
        if (s == null) return;
        sendControl(s, HQFiniteMediaControlPacket.Action.STOP, 0.0);
        closeSessionTransfer(s);
        releaseAssetReference(s);
        terminalStatus = null;
        session = null;
        queueStateEvent();
    }

    public synchronized Map<String, Object> status() {
        Session s = session;
        if (s == null) {
            if (terminalStatus != null) return new HashMap<>(terminalStatus);
            Map<String, Object> idle = new HashMap<>();
            idle.put("state", "idle");
            idle.put("kind", "finite");
            idle.put("position", 0.0);
            idle.put("observed", false);
            idle.put("canPause", false);
            idle.put("canSeek", false);
            idle.put("canLoop", false);
            return idle;
        }
        return statusOf(s);
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

        if (!s.transferComplete) transferSome(s);

        if (s.transferComplete && !s.observed && s.state != State.ERROR && s.state != State.ENDED
                && System.nanoTime() - s.createdNanos > UNOBSERVED_TIMEOUT_NANOS) {
            s.state = State.ERROR;
            s.error = s.recipients.isEmpty() ? "no client renderer was in range" : "client renderer was not observed";
            releaseAssetReference(s);
            terminalStatus = statusOf(s);
            queueStateEvent();
        }
    }

    private void transferSome(Session s) {
        try {
            for (int i = 0; i < CHUNKS_PER_TICK && !s.transferComplete; i++) {
                if (s.sentBytes >= s.totalBytes) {
                    finishTransfer(s);
                    break;
                }
                int wanted = (int) Math.min(HQFiniteMediaChunkPacket.MAX_CHUNK_BYTES, s.totalBytes - s.sentBytes);
                ByteBuffer buffer = ByteBuffer.allocate(wanted);
                int read = s.channel.read(buffer);
                if (read < 0) {
                    if (s.sentBytes != s.totalBytes) failTransfer(s, "staged media ended early");
                    else finishTransfer(s);
                    break;
                }
                if (read == 0) break;
                byte[] bytes = new byte[read];
                buffer.flip();
                buffer.get(bytes);
                HQFiniteMediaChunkPacket packet = new HQFiniteMediaChunkPacket(source, s.mediaId, s.generation, s.sentBytes, bytes);
                sendToRecipients(s, packet);
                s.sentBytes += read;
                if (s.sentBytes >= s.totalBytes) finishTransfer(s);
            }
        } catch (IOException e) {
            failTransfer(s, "media transfer failed: " + safeMessage(e));
        }
    }

    private void finishTransfer(Session s) {
        if (s.transferComplete) return;
        s.transferComplete = true;
        sendToRecipients(s, new HQFiniteMediaEndPacket(source, s.mediaId, s.generation));
        closeChannel(s.channel);
        if (s.consume && s.stagedPath != null) {
            try { staging.deleteStaged(s.stagedPath); }
            catch (IOException e) { HQSpeakerMod.warn("could not delete consumed staged media: " + e.getMessage()); }
        }
    }

    private void failTransfer(Session s, String error) {
        s.state = State.ERROR;
        s.error = error;
        s.transferComplete = true;
        closeChannel(s.channel);
        sendControl(s, HQFiniteMediaControlPacket.Action.STOP, 0.0);
        releaseAssetReference(s);
        terminalStatus = statusOf(s);
        queueStateEvent();
    }

    private void sendBegin(Session s) {
        float[] world = computeWorldPos();
        HQFiniteMediaBeginPacket packet = new HQFiniteMediaBeginPacket(source, s.mediaId, s.generation, s.format,
            s.volume, world[0], world[1], world[2], pos.getX(), pos.getY(), pos.getZ(),
            s.totalBytes, s.clock.looping(), s.state == State.PAUSED);
        sendToRecipients(s, packet);
    }

    private void sendControl(Session s, HQFiniteMediaControlPacket.Action action, double value) {
        sendToRecipients(s, new HQFiniteMediaControlPacket(source, s.generation, action, value));
    }

    private void sendToRecipients(Session s, net.minecraft.network.protocol.common.custom.CustomPacketPayload packet) {
        if (!(level instanceof ServerLevel sl)) return;
        for (UUID id : s.recipients) {
            ServerPlayer player = sl.getServer().getPlayerList().getPlayer(id);
            if (player != null) HQSpeakerNetwork.sendToPlayer(packet, player);
        }
    }

    private Set<UUID> collectRecipients() {
        Set<UUID> out = new HashSet<>();
        if (!(level instanceof ServerLevel sl)) return out;
        float[] p = computeWorldPos();
        for (ServerPlayer player : sl.players()) {
            double dx = player.getX() - p[0], dy = player.getY() - p[1], dz = player.getZ() - p[2];
            if (dx * dx + dy * dy + dz * dz <= SPEAKER_RADIUS * SPEAKER_RADIUS) out.add(player.getUUID());
        }
        return out;
    }

    private synchronized void acceptStatus0(ServerPlayer player, HQFiniteMediaStatusPacket packet) {
        Session s = session;
        if (s == null || player == null || packet.generation() != s.generation || !s.recipients.contains(player.getUUID())) return;
        long now = System.nanoTime();
        switch (packet.transition()) {
            case READY -> {
                if (packet.duration() > 0.0) s.clock.setDuration(packet.duration(), now);
            }
            case STARTED -> {
                if (packet.duration() > 0.0) s.clock.setDuration(packet.duration(), now);
                s.clock.seek(packet.position(), now);
                s.successfulRenderers.add(player.getUUID());
                s.observed = true;
                if (s.state == State.PAUSED) s.clock.pause(now);
                else { s.clock.start(now); s.state = State.PLAYING; }
            }
            case PAUSED -> {
                if (!s.successfulRenderers.contains(player.getUUID())) return;
                s.clock.seek(packet.position(), now);
                s.clock.pause(now);
                s.state = State.PAUSED;
            }
            case RESUMED, SEEKED -> {
                if (!s.successfulRenderers.contains(player.getUUID())) return;
                s.clock.seek(packet.position(), now);
                if (s.state == State.PAUSED) s.clock.pause(now);
                else { s.clock.resume(now); s.state = State.PLAYING; }
            }
            case ENDED -> {
                if (!s.successfulRenderers.contains(player.getUUID())) return;
                s.clock.finish(now);
                s.state = State.ENDED;
                releaseAssetReference(s);
                terminalStatus = statusOf(s);
            }
            case ERROR -> {
                boolean anotherSucceeded = s.successfulRenderers.stream().anyMatch(id -> !id.equals(player.getUUID()));
                if (anotherSucceeded) return;
                s.state = State.ERROR;
                s.error = packet.error() == null || packet.error().isBlank() ? "client playback error" : packet.error();
                releaseAssetReference(s);
                terminalStatus = statusOf(s);
            }
        }
        queueStateEvent();
    }

    private Map<String, Object> statusOf(Session s) {
        Map<String, Object> out = new HashMap<>();
        out.put("generation", s.generation);
        out.put("state", s.state.name().toLowerCase(Locale.ROOT));
        out.put("kind", "finite");
        out.put("format", switch (s.format) { case MP3 -> "mp3"; case OGG -> "ogg"; case AUDIO_FILE -> "audio"; });
        out.put("position", s.clock.position(System.nanoTime()));
        if (s.clock.duration() > 0.0) out.put("duration", s.clock.duration());
        out.put("volume", (double) s.volume);
        out.put("looping", s.clock.looping());
        out.put("observed", s.observed);
        out.put("transferredBytes", s.sentBytes);
        out.put("totalBytes", s.totalBytes);
        if (s.retainedAssetId != null) out.put("assetId", s.retainedAssetId.toString());
        out.put("canPause", s.state != State.ENDED && s.state != State.ERROR);
        out.put("canSeek", s.clock.duration() > 0.0 && s.state != State.ENDED && s.state != State.ERROR);
        out.put("canLoop", s.state != State.ENDED && s.state != State.ERROR);
        if (!s.error.isBlank()) out.put("error", s.error);
        return out;
    }

    private void queueStateEvent() {
        Map<String, Object> state = status();
        for (IComputerAccess computer : attachedComputers) {
            try { computer.queueEvent("hqspeaker_audio_state", state); }
            catch (RuntimeException ignored) {}
        }
    }

    private HQFiniteMediaBeginPacket.MediaFormat detectFormat(String path) {
        String lower = path == null ? "" : path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".mp3") || lower.endsWith(".mp2")) return HQFiniteMediaBeginPacket.MediaFormat.MP3;
        if (lower.endsWith(".ogg")) return HQFiniteMediaBeginPacket.MediaFormat.OGG;
        return HQFiniteMediaBeginPacket.MediaFormat.AUDIO_FILE;
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

    private void closeSessionTransfer(Session s) {
        closeChannel(s.channel);
        if (s.consume && !s.transferComplete && s.stagedPath != null) {
            try { staging.deleteStaged(s.stagedPath); } catch (IOException ignored) {}
        }
    }

    private void releaseAssetReference(Session s) {
        if (!s.assetReferenceHeld || s.assetStore == null || s.retainedAssetId == null) return;
        s.assetReferenceHeld = false;
        try {
            s.assetStore.release(s.retainedAssetId);
        } catch (IOException e) {
            HQSpeakerMod.warn("could not release playback media asset " + s.retainedAssetId + ": " + e.getMessage());
        }
    }

    private static void closeChannel(SeekableByteChannel channel) {
        if (channel == null) return;
        try { channel.close(); } catch (IOException ignored) {}
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
