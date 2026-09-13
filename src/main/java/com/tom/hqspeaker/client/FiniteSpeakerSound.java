package com.tom.hqspeaker.client;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;

import java.util.concurrent.CompletableFuture;

/** One Minecraft positional source for one physical speaker's modern finite renderer epoch. */
final class FiniteSpeakerSound extends AbstractSoundInstance implements TickableSoundInstance {
    private static final ResourceLocation SOUND =
        ResourceLocation.fromNamespaceAndPath("hqspeaker", "hq_speaker");

    private final AudioStream stream;
    private volatile boolean stopped;

    FiniteSpeakerSound(AudioStream stream, float volume, float x, float y, float z) {
        super(SOUND, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        if (stream == null) throw new NullPointerException("stream");
        this.stream = stream;
        this.volume = volume;
        this.x = x;
        this.y = y;
        this.z = z;
        this.attenuation = Attenuation.LINEAR;
        this.looping = false;
    }

    void updateVolume(float volume) {
        this.volume = volume;
    }

    void updatePosition(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    void stopLocally() {
        stopped = true;
    }

    @Override public boolean isStopped() { return stopped; }
    @Override public void tick() {}

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return CompletableFuture.completedFuture(stream);
    }
}
