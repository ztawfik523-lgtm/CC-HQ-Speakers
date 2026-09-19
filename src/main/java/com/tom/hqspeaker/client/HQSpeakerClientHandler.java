package com.tom.hqspeaker.client;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.network.HQSpeakerAudioPacket;
import com.tom.hqspeaker.network.HQSpeakerControlPacket;
import com.tom.hqspeaker.network.HQSpeakerNetwork;
import com.tom.hqspeaker.network.HQSpeakerStatusPacket;
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

import java.util.ArrayDeque;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class HQSpeakerClientHandler {
    private static final ResourceLocation HQ_SOUND_LOC =
        ResourceLocation.fromNamespaceAndPath("hqspeaker", "hq_speaker");
    private static final ConcurrentHashMap<UUID, SpeakerState> states = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<UUID, SyncGroupState> syncGroups = new ConcurrentHashMap<>();
    private static final int MAX_ACTIVE_SPEAKERS = 256;
    private static final int MAX_SYNC_GROUPS = 128;
    private static final int MAX_FINITE_TRACKS_PER_SPEAKER = 16;

    private HQSpeakerClientHandler() {}

    public static void receive(HQSpeakerAudioPacket packet) {
        if (!isPacketSafe(packet)) return;
        if (!states.containsKey(packet.source) && states.size() >= MAX_ACTIVE_SPEAKERS) {
            HQSpeakerMod.warn("HQSpeakerClientHandler: dropping audio; too many active speakers");
            return;
        }

        states.computeIfAbsent(packet.source, ignored -> new SpeakerState(packet.source)).push(packet);
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

    public static void control(HQSpeakerControlPacket packet) {
        if (packet == null || packet.source == null || packet.action == null
                || packet.generation <= 0L || !Double.isFinite(packet.value)) return;
        SpeakerState state = states.get(packet.source);
        if (state != null) state.control(packet);
    }

    private static boolean isPacketSafe(HQSpeakerAudioPacket packet) {
        if (packet == null || packet.source == null || packet.format == null) return false;
        if (!Float.isFinite(packet.volume) || !Float.isFinite(packet.x)
                || !Float.isFinite(packet.y) || !Float.isFinite(packet.z)) return false;
        if (packet.isStreamingFormat()) {
            return packet.streamUrl != null && !packet.streamUrl.isBlank()
                && packet.streamUrl.length() <= HQSpeakerAudioPacket.MAX_URL_CHARS;
        }
        if (packet.data == null || packet.data.length == 0
                || packet.data.length > HQSpeakerAudioPacket.MAX_BYTES) return false;
        return packet.format == HQSpeakerAudioPacket.AudioFormat.PCM_S16LE
            || packet.generation > 0L;
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

    private static boolean isFinite(HQSpeakerAudioPacket.AudioFormat format) {
        return format == HQSpeakerAudioPacket.AudioFormat.OGG_VORBIS
            || format == HQSpeakerAudioPacket.AudioFormat.MP3
            || format == HQSpeakerAudioPacket.AudioFormat.AUDIO_FILE;
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
                HQSpeakerMod.log("HQSpeakerClientHandler: started sync group " + groupId
                    + " with " + ready.size() + "/" + expectedCount + " speakers at tick " + now);
            }
        }
    }

    private enum Mode { NONE, RAW, FINITE, STREAM }

    private static final class FinitePlayback {
        final HQSpeakerAudioPacket packet;
        final HQAudioStream decoder;
        HQAudioStream renderer;
        HQSpeakerSound sound;
        double duration;
        double basePosition;
        long anchorNanos;
        float volume;
        boolean looping;
        boolean desiredPaused;
        boolean ready;
        boolean failed;
        boolean startIssued;
        boolean startActionQueued;
        boolean startedObserved;
        boolean pendingSeekReport;
        int startWaitTicks;

        FinitePlayback(HQSpeakerAudioPacket packet, HQAudioStream decoder) {
            this.packet = new HQSpeakerAudioPacket(packet.source, packet.format, packet.volume,
                packet.x, packet.y, packet.z, packet.blockX, packet.blockY, packet.blockZ,
                new byte[0], packet.startTick, packet.syncGroupId, packet.syncGroupSize,
                packet.generation, packet.finiteLooping, packet.finitePaused);
            this.decoder = decoder;
            this.volume = packet.volume;
            this.looping = packet.finiteLooping;
            this.desiredPaused = packet.finitePaused;
        }

        double position(long now) {
            double value = basePosition;
            if (startedObserved && !desiredPaused) {
                value += Math.max(0L, now - anchorNanos) / 1_000_000_000.0;
            }
            if (duration > 0.0) {
                if (looping) value %= duration;
                else value = Math.min(value, duration);
            }
            return Math.max(0.0, value);
        }
    }

    private static final class SpeakerState {
        private final UUID source;
        private final ArrayDeque<FinitePlayback> finiteQueue = new ArrayDeque<>();
        private Mode mode = Mode.NONE;
        private HQAudioStream legacyStream;
        private HQSpeakerSound legacySound;
        private HQSpeakerAudioPacket legacyPacket;
        private long highestGeneration;
        private boolean terminal;

        SpeakerState(UUID source) { this.source = source; }

        void push(HQSpeakerAudioPacket packet) {
            if (isFinite(packet.format)) pushFinite(packet);
            else pushLegacy(packet);
        }

        private void pushFinite(HQSpeakerAudioPacket packet) {
            if (packet.generation <= highestGeneration) return;
            if (mode != Mode.FINITE) {
                resetPlayback();
                mode = Mode.FINITE;
            }
            if (finiteQueue.size() >= MAX_FINITE_TRACKS_PER_SPEAKER) {
                HQSpeakerMod.warn("HQSpeakerClientHandler: dropping finite track; queue full");
                return;
            }
            highestGeneration = packet.generation;
            terminal = false;
            HQAudioStream decoder = new HQAudioStream();
            FinitePlayback playback = new FinitePlayback(packet, decoder);
            decoder.setFiniteListener(new HQAudioStream.FiniteListener() {
                @Override public void onReady(long generation, double duration) {
                    Minecraft.getInstance().execute(() -> finiteReady(playback, generation, duration));
                }

                @Override public void onError(long generation, String message) {
                    Minecraft.getInstance().execute(() -> finiteError(playback, generation, message));
                }
            });
            finiteQueue.addLast(playback);
            decoder.push(packet);
        }

        private void pushLegacy(HQSpeakerAudioPacket packet) {
            Mode nextMode = packet.isStreamingFormat() ? Mode.STREAM : Mode.RAW;
            if (mode != nextMode) {
                resetPlayback();
                mode = nextMode;
            }
            if (legacyStream != null && legacyStream.isDrained()) {
                legacyStream = null;
                legacySound = null;
            }
            if (legacyStream == null) legacyStream = new HQAudioStream();
            legacyStream.push(packet);
            legacyPacket = packet;
            Minecraft minecraft = Minecraft.getInstance();
            if (legacySound != null && minecraft.getSoundManager().isActive(legacySound)) {
                legacySound.update(packet.volume, packet.x, packet.y, packet.z);
            }
        }

        private void finiteReady(FinitePlayback playback, long generation, double duration) {
            if (mode != Mode.FINITE || playback.packet.generation != generation
                    || !finiteQueue.contains(playback)) return;
            playback.duration = duration;
            playback.ready = true;
            report(playback, HQSpeakerStatusPacket.Transition.READY, 0.0, "");
        }

        private void finiteError(FinitePlayback playback, long generation, String message) {
            if (mode != Mode.FINITE || playback.packet.generation != generation
                    || !finiteQueue.contains(playback)) return;
            playback.failed = true;
            report(playback, HQSpeakerStatusPacket.Transition.ERROR, 0.0, message);
        }

        void tick(Level level) {
            if (mode == Mode.FINITE) tickFinite(level);
            else {
                tryStartLegacy(level);
                tickPosition(level, legacySound, legacyPacket);
            }
        }

        private void tickFinite(Level level) {
            FinitePlayback current = finiteQueue.peekFirst();
            if (current == null) {
                terminal = true;
                return;
            }
            if (current.failed) {
                finishCurrent(false);
                return;
            }

            Minecraft minecraft = Minecraft.getInstance();
            if (current.sound != null && minecraft.getSoundManager().isActive(current.sound)
                    && current.startIssued && !current.startedObserved
                    && !current.startActionQueued) {
                queueStartConfirmation(current);
            }
            if (current.sound != null && !minecraft.getSoundManager().isActive(current.sound)) {
                if (current.renderer != null && current.renderer.hasDeliveredFiniteEof()) {
                    current.basePosition = current.duration;
                    report(current, HQSpeakerStatusPacket.Transition.ENDED,
                        current.duration, "");
                    finishCurrent(true);
                    current = finiteQueue.peekFirst();
                    if (current == null) return;
                } else if (current.startedObserved) {
                    double resumeAt = current.position(System.nanoTime());
                    current.basePosition = resumeAt;
                    current.anchorNanos = System.nanoTime();
                    current.sound = null;
                    current.renderer = null;
                    current.startIssued = false;
                    current.startActionQueued = false;
                    current.startedObserved = false;
                } else if (current.startIssued) {
                    if (++current.startWaitTicks > 40) {
                        finiteError(current, current.packet.generation, "renderer failed to start");
                        return;
                    }
                }
            }

            tryStartFinite(level, current, false);
            tickPosition(level, current.sound, current.packet);
        }

        private void finishCurrent(boolean naturalEnd) {
            FinitePlayback current = finiteQueue.pollFirst();
            if (current == null) return;
            stopFiniteRenderer(current);
            current.decoder.closeAndStop();
            terminal = finiteQueue.isEmpty();
            if (!naturalEnd && !finiteQueue.isEmpty()) terminal = false;
        }

        private void tryStartFinite(Level level, FinitePlayback current, boolean forced) {
            if (current == null || !current.ready || current.failed || current.startIssued) return;
            if (!forced && current.packet.syncGroupId != null && current.packet.syncGroupSize > 1) return;
            if (!forced && current.packet.startTick > 0L
                    && level.getGameTime() < current.packet.startTick) return;

            current.renderer = current.decoder.forkFiniteRenderer(
                current.basePosition, current.looping);
            current.sound = new HQSpeakerSound(current.renderer, current.packet, current.volume,
                current.packet.x, current.packet.y, current.packet.z);
            current.anchorNanos = System.nanoTime();
            current.startIssued = true;
            current.startActionQueued = false;
            current.startWaitTicks = 0;
            Minecraft.getInstance().getSoundManager().play(current.sound);
        }

        private void queueStartConfirmation(FinitePlayback current) {
            HQSpeakerSound expectedSound = current.sound;
            boolean found = HQSoundChannelControl.execute(expectedSound, channel -> {
                if (current.desiredPaused) channel.pause();
                Minecraft.getInstance().execute(() -> rendererStarted(current, expectedSound));
            });
            if (found) current.startActionQueued = true;
        }

        private void rendererStarted(FinitePlayback current, HQSpeakerSound expectedSound) {
            if (mode != Mode.FINITE || finiteQueue.peekFirst() != current
                    || current.sound != expectedSound) return;
            current.startedObserved = true;
            current.startActionQueued = false;
            current.anchorNanos = System.nanoTime();
            report(current, HQSpeakerStatusPacket.Transition.STARTED,
                current.basePosition, "");
            if (current.pendingSeekReport) {
                current.pendingSeekReport = false;
                report(current, HQSpeakerStatusPacket.Transition.SEEKED,
                    current.basePosition, "");
            }
            if (current.desiredPaused) {
                report(current, HQSpeakerStatusPacket.Transition.PAUSED,
                    current.basePosition, "");
            }
        }

        private void tryStartLegacy(Level level) {
            if (legacyStream == null || legacyPacket == null || !legacyStream.isStreamReady()) return;
            if (legacyPacket.syncGroupId != null && legacyPacket.syncGroupSize > 1) return;
            if (legacyPacket.startTick > 0L && level.getGameTime() < legacyPacket.startTick) return;
            Minecraft minecraft = Minecraft.getInstance();
            if (legacySound != null && minecraft.getSoundManager().isActive(legacySound)) return;
            legacySound = new HQSpeakerSound(legacyStream, legacyPacket, legacyPacket.volume,
                legacyPacket.x, legacyPacket.y, legacyPacket.z);
            minecraft.getSoundManager().play(legacySound);
        }

        boolean isReadyToStart() {
            if (mode == Mode.FINITE) {
                FinitePlayback current = finiteQueue.peekFirst();
                return current != null && current.ready && !current.startIssued;
            }
            return legacyStream != null && legacyPacket != null && legacyStream.isStreamReady();
        }

        void forceStart(Level level) {
            if (mode == Mode.FINITE) tryStartFinite(level, finiteQueue.peekFirst(), true);
            else {
                HQSpeakerAudioPacket packet = legacyPacket;
                if (legacyStream == null || packet == null || !legacyStream.isStreamReady()) return;
                Minecraft minecraft = Minecraft.getInstance();
                if (legacySound != null && minecraft.getSoundManager().isActive(legacySound)) return;
                legacySound = new HQSpeakerSound(legacyStream, packet, packet.volume,
                    packet.x, packet.y, packet.z);
                minecraft.getSoundManager().play(legacySound);
            }
        }

        void control(HQSpeakerControlPacket packet) {
            if (mode != Mode.FINITE) return;
            FinitePlayback current = finiteQueue.peekFirst();
            if (current == null || current.packet.generation != packet.generation) return;
            switch (packet.action) {
                case PAUSE -> pause(current);
                case RESUME -> resume(current);
                case SEEK -> seek(current, packet.value);
                case SET_VOLUME -> setVolume(current, packet.value);
                case SET_LOOP -> setLooping(current, packet.value >= 0.5);
            }
        }

        private void pause(FinitePlayback current) {
            if (current.desiredPaused) return;
            current.basePosition = current.position(System.nanoTime());
            current.desiredPaused = true;
            HQSpeakerSound expectedSound = current.sound;
            if (expectedSound != null && HQSoundChannelControl.execute(expectedSound, channel -> {
                channel.pause();
                Minecraft.getInstance().execute(() -> {
                    if (isCurrentRenderer(current, expectedSound) && current.desiredPaused) {
                        report(current, HQSpeakerStatusPacket.Transition.PAUSED,
                            current.basePosition, "");
                    }
                });
            })) return;
        }

        private void resume(FinitePlayback current) {
            if (!current.desiredPaused) return;
            current.desiredPaused = false;
            current.anchorNanos = System.nanoTime();
            HQSpeakerSound expectedSound = current.sound;
            if (expectedSound != null) {
                HQSoundChannelControl.execute(expectedSound, channel -> {
                    channel.unpause();
                    Minecraft.getInstance().execute(() -> {
                        if (isCurrentRenderer(current, expectedSound) && !current.desiredPaused) {
                            report(current, HQSpeakerStatusPacket.Transition.RESUMED,
                                current.basePosition, "");
                        }
                    });
                });
            }
        }

        private boolean isCurrentRenderer(FinitePlayback playback, HQSpeakerSound sound) {
            return mode == Mode.FINITE && finiteQueue.peekFirst() == playback
                && playback.sound == sound;
        }

        private void seek(FinitePlayback current, double seconds) {
            if (!current.ready || !Double.isFinite(seconds)) return;
            double applied = Math.max(0.0, Math.min(current.duration, seconds));
            stopFiniteRenderer(current);
            current.basePosition = applied;
            current.anchorNanos = System.nanoTime();
            current.startIssued = false;
            current.startActionQueued = false;
            current.startedObserved = false;
            current.pendingSeekReport = true;
        }

        private void setVolume(FinitePlayback current, double volume) {
            if (!Double.isFinite(volume)) return;
            current.volume = (float) Math.max(0.0, Math.min(3.0, volume));
            if (current.sound != null) {
                current.sound.updateVolume(current.volume);
                HQSoundChannelControl.refreshBlocksVolume();
            }
        }

        private void setLooping(FinitePlayback current, boolean looping) {
            current.looping = looping;
            current.decoder.setFiniteLooping(looping);
            if (current.renderer != null) current.renderer.setFiniteLooping(looping);
        }

        private void stopFiniteRenderer(FinitePlayback playback) {
            if (playback.sound != null) {
                Minecraft.getInstance().getSoundManager().stop(playback.sound);
                playback.sound = null;
            }
            if (playback.renderer != null) {
                playback.renderer.closeAndStop();
                playback.renderer = null;
            }
        }

        private void tickPosition(Level level, HQSpeakerSound sound, HQSpeakerAudioPacket packet) {
            if (sound == null || packet == null || level == null) return;
            if (!Minecraft.getInstance().getSoundManager().isActive(sound)) return;
            if (!VS2TransformHelper.isVS2Loaded()) return;
            BlockPos blockPos = new BlockPos(packet.blockX, packet.blockY, packet.blockZ);
            Object ship = VS2TransformHelper.getShipManagingBlock(level, blockPos);
            if (ship == null) return;
            try {
                Matrix4dc matrix = getShipToWorldMatrix(ship);
                if (matrix == null) return;
                Vector3d local = new Vector3d(packet.blockX + 0.5,
                    packet.blockY + 0.5, packet.blockZ + 0.5);
                Vector3d world = new Vector3d();
                matrix.transformPosition(local, world);
                sound.updatePosition((float) world.x, (float) world.y, (float) world.z);
            } catch (Exception exception) {
                HQSpeakerMod.warn("HQSpeaker: tickPosition failed: " + exception.getMessage());
            }
        }

        UUID currentSyncGroupId() {
            HQSpeakerAudioPacket packet = currentPacket();
            return packet == null ? null : packet.syncGroupId;
        }

        long currentStartTick() {
            HQSpeakerAudioPacket packet = currentPacket();
            return packet == null ? 0L : packet.startTick;
        }

        private HQSpeakerAudioPacket currentPacket() {
            if (mode == Mode.FINITE) {
                FinitePlayback current = finiteQueue.peekFirst();
                return current == null ? null : current.packet;
            }
            return legacyPacket;
        }

        private void report(FinitePlayback playback, HQSpeakerStatusPacket.Transition transition,
                            double position, String error) {
            if (!finiteQueue.contains(playback) && transition != HQSpeakerStatusPacket.Transition.ENDED) return;
            try {
                HQSpeakerNetwork.sendToServer(new HQSpeakerStatusPacket(source,
                    playback.packet.generation, transition, position, playback.duration, error));
            } catch (Exception exception) {
                HQSpeakerMod.warn("HQSpeakerClientHandler: status send failed: " + exception.getMessage());
            }
        }

        void stop() { resetPlayback(); }

        private void resetPlayback() {
            Minecraft minecraft = Minecraft.getInstance();
            if (legacySound != null) minecraft.getSoundManager().stop(legacySound);
            if (legacyStream != null) legacyStream.closeAndStop();
            legacySound = null;
            legacyStream = null;
            legacyPacket = null;
            for (FinitePlayback playback : finiteQueue) {
                stopFiniteRenderer(playback);
                playback.decoder.closeAndStop();
            }
            finiteQueue.clear();
            mode = Mode.NONE;
            terminal = true;
        }

        boolean isDone() {
            if (mode == Mode.FINITE) return terminal && finiteQueue.isEmpty();
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

        void updateVolume(float volume) { this.volume = volume; }

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
