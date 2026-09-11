package com.tom.hqspeaker.client;

import com.tom.hqspeaker.HQSpeakerMod;
import com.tom.hqspeaker.media.FinitePlaybackClock;
import com.tom.hqspeaker.network.HQFiniteMediaBeginPacket;
import com.tom.hqspeaker.network.HQFiniteMediaChunkPacket;
import com.tom.hqspeaker.network.HQFiniteMediaControlPacket;
import com.tom.hqspeaker.network.HQFiniteMediaEndPacket;
import com.tom.hqspeaker.network.HQFiniteMediaStatusPacket;
import com.tom.hqspeaker.network.HQSpeakerNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
public final class HQFiniteMediaClient {
    private static final ResourceLocation HQ_SOUND_LOC =
        ResourceLocation.fromNamespaceAndPath("hqspeaker", "hq_speaker");
    private static final ConcurrentHashMap<UUID, Session> SESSIONS = new ConcurrentHashMap<>();
    private static final int MAX_SESSIONS = 128;

    private HQFiniteMediaClient() {}

    private static final class Session {
        final HQFiniteMediaBeginPacket begin;
        final Path partPath;
        final Path finalPath;
        final FinitePlaybackClock clock;
        FileChannel transfer;
        long received;
        FileFiniteAudioStream stream;
        FiniteSound sound;
        float volume;
        boolean desiredPaused;
        boolean ready;
        boolean started;
        boolean startConfirmationQueued;
        boolean terminal;

        Session(HQFiniteMediaBeginPacket begin, Path partPath, Path finalPath, FileChannel transfer) {
            this.begin = begin;
            this.partPath = partPath;
            this.finalPath = finalPath;
            this.transfer = transfer;
            this.clock = new FinitePlaybackClock(begin.looping());
            this.volume = begin.volume();
            this.desiredPaused = begin.paused();
        }
    }

    public static void begin(HQFiniteMediaBeginPacket packet) {
        Minecraft.getInstance().execute(() -> begin0(packet));
    }

    public static void chunk(HQFiniteMediaChunkPacket packet) {
        Minecraft.getInstance().execute(() -> chunk0(packet));
    }

    public static void end(HQFiniteMediaEndPacket packet) {
        Minecraft.getInstance().execute(() -> end0(packet));
    }

    public static void control(HQFiniteMediaControlPacket packet) {
        Minecraft.getInstance().execute(() -> control0(packet));
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            stopAll();
            return;
        }
        SESSIONS.forEach((source, session) -> tickSession(source, session));
    }

    public static void stopAll() {
        SESSIONS.forEach((id, session) -> destroy(session, true));
        SESSIONS.clear();
    }

    private static void begin0(HQFiniteMediaBeginPacket packet) {
        if (packet == null || !packet.sensible()) return;
        if (!SESSIONS.containsKey(packet.source()) && SESSIONS.size() >= MAX_SESSIONS) return;
        Session old = SESSIONS.remove(packet.source());
        if (old != null) destroy(old, true);
        try {
            Path dir = Minecraft.getInstance().gameDirectory.toPath().resolve("hqspeaker-cache");
            Files.createDirectories(dir);
            String stem = packet.source() + "-" + packet.mediaId() + "-" + packet.generation();
            Path part = dir.resolve(stem + ".part");
            Path file = dir.resolve(stem + ".media");
            Files.deleteIfExists(part);
            Files.deleteIfExists(file);
            FileChannel channel = FileChannel.open(part,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            SESSIONS.put(packet.source(), new Session(packet, part, file, channel));
        } catch (IOException e) {
            reportError(packet.source(), packet.generation(), "cannot create finite media cache: " + safeMessage(e));
        }
    }

    private static void chunk0(HQFiniteMediaChunkPacket packet) {
        Session s = SESSIONS.get(packet.source());
        if (!matches(s, packet.mediaId(), packet.generation()) || s.transfer == null || s.terminal) return;
        try {
            if (packet.offset() != s.received) throw new IOException("out-of-order finite media chunk");
            if (s.received + packet.data().length > s.begin.totalBytes()) throw new IOException("finite media exceeds declared size");
            ByteBuffer buffer = ByteBuffer.wrap(packet.data());
            while (buffer.hasRemaining()) s.transfer.write(buffer);
            s.received += packet.data().length;
        } catch (IOException e) {
            fail(s, "finite media cache write failed: " + safeMessage(e));
        }
    }

    private static void end0(HQFiniteMediaEndPacket packet) {
        Session s = SESSIONS.get(packet.source());
        if (!matches(s, packet.mediaId(), packet.generation()) || s.terminal) return;
        try {
            closeTransfer(s);
            if (s.received != s.begin.totalBytes()) throw new IOException("finite media transfer incomplete (" + s.received + "/" + s.begin.totalBytes() + ")");
            try {
                Files.move(s.partPath, s.finalPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(s.partPath, s.finalPath, StandardCopyOption.REPLACE_EXISTING);
            }
            s.stream = new FileFiniteAudioStream(s.finalPath, s.begin.format(), s.clock.looping());
            double duration = s.stream.durationSeconds();
            s.clock.setDuration(duration, System.nanoTime());
            s.ready = true;
            report(s, HQFiniteMediaStatusPacket.Transition.READY, 0.0, duration, "");
            if (!s.desiredPaused) startRenderer(s, 0.0);
        } catch (Exception e) {
            fail(s, "finite media decode failed: " + safeMessage(e));
        }
    }

    private static void tickSession(UUID source, Session s) {
        if (s.terminal || !s.ready) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (s.sound != null && minecraft.getSoundManager().isActive(s.sound)
                && !s.started && !s.startConfirmationQueued) {
            FiniteSound expected = s.sound;
            boolean found = HQSoundChannelControl.execute(expected, channel -> {
                if (s.desiredPaused) channel.pause();
                minecraft.execute(() -> {
                    if (SESSIONS.get(source) != s || s.sound != expected || s.terminal) return;
                    s.started = true;
                    s.startConfirmationQueued = false;
                    long now = System.nanoTime();
                    if (s.desiredPaused) s.clock.pause(now); else s.clock.start(now);
                    report(s, HQFiniteMediaStatusPacket.Transition.STARTED,
                        s.clock.position(now), s.clock.duration(), "");
                    if (s.desiredPaused) report(s, HQFiniteMediaStatusPacket.Transition.PAUSED,
                        s.clock.position(now), s.clock.duration(), "");
                });
            });
            if (found) s.startConfirmationQueued = true;
        }

        if (s.sound != null && !minecraft.getSoundManager().isActive(s.sound)) {
            if (s.stream != null && s.stream.ended()) {
                finish(s);
            } else if (s.started && !s.desiredPaused) {
                // Sound engine/resource reload: rebuild from the semantic cursor.
                double resumeAt = s.clock.position(System.nanoTime());
                try { restartRenderer(s, resumeAt); }
                catch (IOException e) { fail(s, "finite renderer restart failed: " + safeMessage(e)); }
            }
        }
    }

    private static void control0(HQFiniteMediaControlPacket packet) {
        Session s = SESSIONS.get(packet.source());
        if (s == null || s.begin.generation() != packet.generation() || s.terminal) return;
        try {
            switch (packet.action()) {
                case PAUSE -> pause(s);
                case RESUME -> resume(s);
                case SEEK -> seek(s, packet.value());
                case SET_VOLUME -> setVolume(s, packet.value());
                case SET_LOOP -> setLooping(s, packet.value() >= 0.5);
                case STOP -> {
                    destroy(s, true);
                    SESSIONS.remove(packet.source(), s);
                }
            }
        } catch (IOException e) {
            fail(s, "finite control failed: " + safeMessage(e));
        }
    }

    private static void pause(Session s) {
        if (s.desiredPaused) return;
        long now = System.nanoTime();
        s.clock.pause(now);
        s.desiredPaused = true;
        if (s.sound != null) HQSoundChannelControl.execute(s.sound, channel -> channel.pause());
        report(s, HQFiniteMediaStatusPacket.Transition.PAUSED, s.clock.position(now), s.clock.duration(), "");
    }

    private static void resume(Session s) throws IOException {
        if (!s.desiredPaused) return;
        s.desiredPaused = false;
        long now = System.nanoTime();
        s.clock.resume(now);
        if (s.sound != null && HQSoundChannelControl.execute(s.sound, channel -> channel.unpause())) {
            report(s, HQFiniteMediaStatusPacket.Transition.RESUMED, s.clock.position(now), s.clock.duration(), "");
        } else if (s.ready) {
            restartRenderer(s, s.clock.position(now));
        }
    }

    private static void seek(Session s, double seconds) throws IOException {
        if (!s.ready || !Double.isFinite(seconds)) return;
        long now = System.nanoTime();
        double target = s.clock.seek(seconds, now);
        if (!s.clock.looping() && s.clock.duration() > 0.0 && target >= s.clock.duration()) {
            stopRenderer(s);
            s.clock.finish(now);
            s.terminal = true;
            report(s, HQFiniteMediaStatusPacket.Transition.ENDED, s.clock.duration(), s.clock.duration(), "");
            return;
        }
        restartStreamOnly(s, target);
        if (!s.desiredPaused) startRenderer(s, target);
        report(s, HQFiniteMediaStatusPacket.Transition.SEEKED, target, s.clock.duration(), "");
    }

    private static void setVolume(Session s, double volume) {
        s.volume = (float) Math.max(0.0, Math.min(3.0, volume));
        if (s.sound != null) {
            s.sound.setVolume(s.volume);
            HQSoundChannelControl.refreshBlocksVolume();
        }
    }

    private static void setLooping(Session s, boolean looping) {
        long now = System.nanoTime();
        s.clock.setLooping(looping, now);
        if (s.stream != null) s.stream.setLooping(looping);
    }

    private static void startRenderer(Session s, double position) throws IOException {
        if (!s.ready || s.terminal) return;
        if (s.stream == null) s.stream = new FileFiniteAudioStream(s.finalPath, s.begin.format(), s.clock.looping());
        s.stream.seek(position);
        s.sound = new FiniteSound(s.stream, s.begin, s.volume);
        s.started = false;
        s.startConfirmationQueued = false;
        Minecraft.getInstance().getSoundManager().play(s.sound);
    }

    private static void restartRenderer(Session s, double position) throws IOException {
        stopRenderer(s);
        restartStreamOnly(s, position);
        startRenderer(s, position);
    }

    private static void restartStreamOnly(Session s, double position) throws IOException {
        if (s.stream != null) s.stream.close();
        s.stream = new FileFiniteAudioStream(s.finalPath, s.begin.format(), s.clock.looping());
        s.stream.seek(position);
        s.started = false;
        s.startConfirmationQueued = false;
    }

    private static void finish(Session s) {
        long now = System.nanoTime();
        s.clock.finish(now);
        s.terminal = true;
        report(s, HQFiniteMediaStatusPacket.Transition.ENDED, s.clock.duration(), s.clock.duration(), "");
        stopRenderer(s);
    }

    private static void fail(Session s, String error) {
        if (s.terminal) return;
        s.terminal = true;
        report(s, HQFiniteMediaStatusPacket.Transition.ERROR, s.clock.position(System.nanoTime()), s.clock.duration(), error);
        destroy(s, false);
    }

    private static void reportError(UUID source, long generation, String error) {
        try { HQSpeakerNetwork.sendToServer(new HQFiniteMediaStatusPacket(source, generation,
            HQFiniteMediaStatusPacket.Transition.ERROR, 0.0, 0.0, error)); }
        catch (Exception ignored) {}
    }

    private static void report(Session s, HQFiniteMediaStatusPacket.Transition transition,
                               double position, double duration, String error) {
        try {
            HQSpeakerNetwork.sendToServer(new HQFiniteMediaStatusPacket(s.begin.source(), s.begin.generation(),
                transition, Math.max(0.0, position), Math.max(0.0, duration), error == null ? "" : error));
        } catch (Exception e) {
            HQSpeakerMod.warn("finite status send failed: " + e.getMessage());
        }
    }

    private static boolean matches(Session s, UUID mediaId, long generation) {
        return s != null && s.begin.generation() == generation && s.begin.mediaId().equals(mediaId);
    }

    private static void stopRenderer(Session s) {
        if (s.sound != null) {
            Minecraft.getInstance().getSoundManager().stop(s.sound);
            s.sound = null;
        }
        if (s.stream != null) {
            s.stream.close();
            s.stream = null;
        }
        s.started = false;
        s.startConfirmationQueued = false;
    }

    private static void destroy(Session s, boolean deleteFiles) {
        closeTransfer(s);
        stopRenderer(s);
        if (deleteFiles) {
            try { Files.deleteIfExists(s.partPath); } catch (IOException ignored) {}
            try { Files.deleteIfExists(s.finalPath); } catch (IOException ignored) {}
        }
    }

    private static void closeTransfer(Session s) {
        if (s.transfer != null) {
            try { s.transfer.close(); } catch (IOException ignored) {}
            s.transfer = null;
        }
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }

    private static final class FiniteSound extends AbstractSoundInstance implements TickableSoundInstance {
        private final FileFiniteAudioStream stream;
        private boolean stopped;

        FiniteSound(FileFiniteAudioStream stream, HQFiniteMediaBeginPacket begin, float volume) {
            super(HQ_SOUND_LOC, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.stream = stream;
            this.volume = volume;
            this.x = begin.x(); this.y = begin.y(); this.z = begin.z();
            this.attenuation = Attenuation.LINEAR;
            this.looping = false;
        }

        void setVolume(float volume) { this.volume = volume; }
        @Override public boolean isStopped() { return stopped || stream.ended(); }
        @Override public void tick() {}
        @Override public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
            return CompletableFuture.completedFuture(stream);
        }
    }
}
