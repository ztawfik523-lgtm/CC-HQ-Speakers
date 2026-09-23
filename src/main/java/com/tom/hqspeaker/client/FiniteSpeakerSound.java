package com.tom.hqspeaker.client;

import com.tom.hqspeaker.diagnostics.HQDiagnostics;
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
final class FiniteSpeakerSound extends AbstractSoundInstance implements TickableSoundInstance, HQDiagnosticSource {
    private static final ResourceLocation AUDIO_SOURCE =
        ResourceLocation.fromNamespaceAndPath("hqspeaker", "hq_audio_source");

    private final AudioStream stream;
    private final HQDiagnostics.SourceIdentity diagnosticIdentity;
    private volatile boolean stopped;

    FiniteSpeakerSound(AudioStream stream, HQDiagnostics.SourceIdentity diagnosticIdentity,
                       float volume, float x, float y, float z) {
        super(AUDIO_SOURCE, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        if (stream == null) throw new NullPointerException("stream");
        if (diagnosticIdentity == null) throw new NullPointerException("diagnosticIdentity");
        this.stream = stream;
        this.diagnosticIdentity = diagnosticIdentity;
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
    @Override public boolean canStartSilent() { return true; }
    @Override public void tick() {}

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary soundBuffers, Sound sound, boolean looping) {
        return CompletableFuture.completedFuture(stream);
    }

    @Override
    public HQDiagnostics.SourceIdentity hqspeaker$diagnosticIdentity() {
        return diagnosticIdentity;
    }
}
