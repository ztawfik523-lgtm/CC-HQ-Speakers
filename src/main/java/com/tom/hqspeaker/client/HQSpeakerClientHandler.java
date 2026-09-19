package com.tom.hqspeaker.client;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.network.HQSpeakerAudioPacket;
import com.tom.hqspeaker.network.HQSpeakerControlPacket;
import com.tom.hqspeaker.vs2.VS2TransformHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4dc;
import org.joml.Vector3d;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/** Legacy client path for RAW PCM and optional live streams only. */
@OnlyIn(Dist.CLIENT)
public final class HQSpeakerClientHandler {
    private static final ResourceLocation HQ_SOUND_LOC =
        ResourceLocation.fromNamespaceAndPath("hqspeaker", "hq_speaker");
    private static final ConcurrentHashMap<UUID, SpeakerState> states = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, SyncGroupState> syncGroups = new ConcurrentHashMap<>();
    private static final int MAX_ACTIVE_SPEAKERS = 256;
    private static final int MAX_SYNC_GROUPS = 128;

    private HQSpeakerClientHandler() {}

    public static void receive(HQSpeakerAudioPacket packet) {
        if (!isPacketSafe(packet)) return;
        if (!states.containsKey(packet.source) && states.size() >= MAX_ACTIVE_SPEAKERS) {
            HQSpeakerMod.warn("HQSpeakerClientHandler: dropping audio; too many active speakers");
            return;
        }

        states.computeIfAbsent(packet.source, ignored -> new SpeakerState()).push(packet);
        if (packet.syncGroupId != null && packet.syncGroupSize > 0) {
            if (!syncGroups.containsKey(packet.syncGroupId) && syncGroups.size() >= MAX_SYNC_GROUPS) {
                HQSpeakerMod.warn("HQSpeakerClientHandler: dropping sync group; too many active groups");
                return;
            }
            syncGroups.computeIfAbsent(packet.syncGroupId,
                id -> new SyncGroupState(id, packet.syncGroupSize))
                .add(packet.source, packet.syncGroupSize);
        }
    }

    /** Retained until the dead legacy finite control payload is removed in the next teardown step. */
    public static void control(HQSpeakerControlPacket packet) {
        // Intentionally ignored: legacy finite playback is no longer admitted.
    }

    private static boolean isPacketSafe(HQSpeakerAudioPacket packet) {
        if (packet == null || packet.source == null || packet.format == null) return false;
        if (!Float.isFinite(packet.volume) || !Float.isFinite(packet.x)
                || !Float.isFinite(packet.y) || !Float.isFinite(packet.z)) return false;
        if (packet.isStreamingFormat()) {
            return packet.streamUrl != null && !packet.streamUrl.isBlank()
                && packet.streamUrl.length() <= HQSpeakerAudioPacket.MAX_URL_CHARS;
        }
        return packet.format == HQSpeakerAudioPacket.AudioFormat.PCM_S16LE
            && packet.data != null && packet.data.length > 0
            && packet.data.length <= HQSpeakerAudioPacket.MAX_BYTES;
    }

    public static void tick() {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            if (!states.isEmpty()) stopAll();
            return;
        }
        tickSyncGroups(level);
        states.forEach((id, state) -> {
            state.tick(level);
            if (state.isDone() && states.remove(id, state)) state.stop();
        });
    }

    public static void stop(UUID source) {
        SpeakerState state = states.remove(source);
        if (state != null) state.stop();
        syncGroups.forEach((id, group) -> group.remove(source));
    }

    public static void stopAll() {
        states.forEach((id, state) -> state.stop());
        states.clear();
        syncGroups.clear();
    }

    public static boolean isPlaying(UUID source) {
        SpeakerState state = states.get(source);
        return state != null && !state.isDone();
    }

    private static void tickSyncGroups(Level level) {
        long now = level.getGameTime();
        syncGroups.forEach((groupId, group) -> {
            group.tick(level, now);
            if (group.canRemove()) syncGroups.remove(groupId, group);
        });
    }

    private static final class SyncGroupState {
        private final UUID groupId;
        private volatile int expectedCount;
        private final java.util.Set<UUID> members = ConcurrentHashMap.newKeySet();
        private volatile long armedStartTick = -1L;
        private volatile boolean started;

        private SyncGroupState(UUID groupId, int expectedCount) {
            this.groupId = groupId;
            this.expectedCount = Math.max(1, expectedCount);
        }

        void add(UUID source, int count) {
            expectedCount = Math.max(expectedCount, count);
            members.add(source);
        }

        void remove(UUID source) { members.remove(source); }

        boolean canRemove() {
            if (!started) return members.isEmpty();
            for (UUID member : members) {
                SpeakerState state = states.get(member);
                if (state != null && groupId.equals(state.currentSyncGroupId())) return false;
            }
            return true;
        }

        void tick(Level level, long now) {
            java.util.ArrayList<SpeakerState> ready = new java.util.ArrayList<>();
            long maxStartTick = 0L;
            int present = 0;
            for (UUID member : members) {
                SpeakerState state = states.get(member);
                if (state == null || !groupId.equals(state.currentSyncGroupId())) continue;
                present++;
                maxStartTick = Math.max(maxStartTick, state.currentStartTick());
                if (state.isReadyToStart()) ready.add(state);
            }
            if (present == 0) return;
            if (!started && armedStartTick < 0L && present >= expectedCount
                    && ready.size() >= expectedCount) {
                armedStartTick = Math.max(maxStartTick, now + 1L);
            }
            if (!started && armedStartTick >= 0L && now >= armedStartTick) {
                for (SpeakerState state : ready) state.forceStart(level);
                started = true;
                HQSpeakerMod.log("HQSpeakerClientHandler: started legacy sync group " + groupId
                    + " with " + ready.size() + "/" + expectedCount + " speakers at tick " + now);
            }
        }
    }

    private enum Mode { NONE, RAW, STREAM }

    private static final class SpeakerState {
        private Mode mode = Mode.NONE;
        private HQAudioStream stream;
        private HQSpeakerSound sound;
        private HQSpeakerAudioPacket packet;

        void push(HQSpeakerAudioPacket next) {
            Mode nextMode = next.isStreamingFormat() ? Mode.STREAM : Mode.RAW;
            if (mode != nextMode) {
                resetPlayback();
                mode = nextMode;
            }

            if (stream != null && stream.isDrained()) {
                if (sound != null) Minecraft.getInstance().getSoundManager().stop(sound);
                stream.closeAndStop();
                stream = null;
                sound = null;
            }
            if (stream == null) stream = new HQAudioStream();
            stream.push(next);
            packet = next;

            Minecraft minecraft = Minecraft.getInstance();
            if (sound != null && minecraft.getSoundManager().isActive(sound)) {
                sound.update(next.volume, next.x, next.y, next.z);
            }
        }

        void tick(Level level) {
            tryStart(level);
            tickPosition(level, sound, packet);
        }

        private void tryStart(Level level) {
            if (stream == null || packet == null || !stream.isStreamReady()) return;
            if (packet.syncGroupId != null && packet.syncGroupSize > 1) return;
            if (packet.startTick > 0L && level.getGameTime() < packet.startTick) return;
            Minecraft minecraft = Minecraft.getInstance();
            if (sound != null && minecraft.getSoundManager().isActive(sound)) return;
            sound = new HQSpeakerSound(stream, packet, packet.volume,
                packet.x, packet.y, packet.z);
            minecraft.getSoundManager().play(sound);
        }

        boolean isReadyToStart() {
            return stream != null && packet != null && stream.isStreamReady();
        }

        void forceStart(Level level) {
            if (stream == null || packet == null || !stream.isStreamReady()) return;
            Minecraft minecraft = Minecraft.getInstance();
            if (sound != null && minecraft.getSoundManager().isActive(sound)) return;
            sound = new HQSpeakerSound(stream, packet, packet.volume,
                packet.x, packet.y, packet.z);
            minecraft.getSoundManager().play(sound);
        }

        private void tickPosition(Level level, HQSpeakerSound currentSound, HQSpeakerAudioPacket currentPacket) {
            if (currentSound == null || currentPacket == null || level == null) return;
            if (!Minecraft.getInstance().getSoundManager().isActive(currentSound)) return;
            if (!VS2TransformHelper.isVS2Loaded()) return;

            BlockPos blockPos = new BlockPos(currentPacket.blockX, currentPacket.blockY, currentPacket.blockZ);
            Object ship = VS2TransformHelper.getShipManagingBlock(level, blockPos);
            if (ship == null) return;
            try {
                Matrix4dc matrix = getShipToWorldMatrix(ship);
                if (matrix == null) return;
                Vector3d local = new Vector3d(
                    currentPacket.blockX + 0.5, currentPacket.blockY + 0.5, currentPacket.blockZ + 0.5);
                Vector3d world = new Vector3d();
                matrix.transformPosition(local, world);
                currentSound.updatePosition((float) world.x, (float) world.y, (float) world.z);
            } catch (Exception exception) {
                HQSpeakerMod.warn("HQSpeaker: tickPosition failed: " + exception.getMessage());
            }
        }

        UUID currentSyncGroupId() { return packet == null ? null : packet.syncGroupId; }
        long currentStartTick() { return packet == null ? 0L : packet.startTick; }

        void stop() { resetPlayback(); }

        private void resetPlayback() {
            Minecraft minecraft = Minecraft.getInstance();
            if (sound != null) minecraft.getSoundManager().stop(sound);
            if (stream != null) stream.closeAndStop();
            sound = null;
            stream = null;
            packet = null;
            mode = Mode.NONE;
        }

        boolean isDone() {
            if (mode == Mode.STREAM) return false;
            return mode == Mode.NONE;
        }
    }

    static Matrix4dc getShipToWorldMatrix(Object ship) {
        return VS2TransformHelper.getShipToWorldMatrix(ship);
    }

    @OnlyIn(Dist.CLIENT)
    public static final class HQSpeakerSound extends AbstractSoundInstance
            implements TickableSoundInstance {
        private final HQAudioStream stream;
        private final boolean streaming;

        HQSpeakerSound(HQAudioStream stream, HQSpeakerAudioPacket packet,
                       float volume, float x, float y, float z) {
            super(HQ_SOUND_LOC, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.stream = stream;
            this.volume = volume;
            this.x = x;
            this.y = y;
            this.z = z;
            this.attenuation = Attenuation.LINEAR;
            this.looping = false;
            this.streaming = packet.isStreamingFormat();
        }

        void update(float volume, float x, float y, float z) {
            this.volume = volume;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        void updatePosition(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override public boolean isStopped() { return streaming && stream.isDrained(); }
        @Override public void tick() {}

        @Override
        public CompletableFuture<AudioStream> getStream(
                SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
            return CompletableFuture.completedFuture(stream);
        }
    }
}
