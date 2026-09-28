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
final class FiniteSpeakerSound extends AbstractSoundInstance
        implements TickableSoundInstance, HQDiagnosticSource, HQAcousticPositionSource {
    private static final ResourceLocation AUDIO_SOURCE =
        ResourceLocation.fromNamespaceAndPath("hqspeaker", "hq_audio_source");

    private final AudioStream stream;
    private final HQDiagnostics.SourceIdentity diagnosticIdentity;
    private float physicalX;
    private float physicalY;
    private float physicalZ;
    private volatile boolean stopped;

    FiniteSpeakerSound(AudioStream stream, HQDiagnostics.SourceIdentity diagnosticIdentity,
                       float volume, float x, float y, float z) {
        super(AUDIO_SOURCE, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
        if (stream == null) throw new NullPointerException("stream");
        if (diagnosticIdentity == null) throw new NullPointerException("diagnosticIdentity");
        this.stream = stream;
        this.diagnosticIdentity = diagnosticIdentity;
        this.volume = volume;
        this.physicalX = x;
        this.physicalY = y;
        this.physicalZ = z;
        this.x = x;
        this.y = y;
        this.z = z;
        this.attenuation = Attenuation.LINEAR;
        this.looping = false;
    }

    void updateVolume(float volume) {
        this.volume = volume;
    }

    synchronized void updatePosition(float x, float y, float z) {
        float dx = x - physicalX;
        float dy = y - physicalY;
        float dz = z - physicalZ;
        physicalX = x;
        physicalY = y;
        physicalZ = z;
        this.x += dx;
        this.y += dy;
        this.z += dz;
    }

    @Override
    public synchronized double hqspeaker$physicalX() { return physicalX; }

    @Override
    public synchronized double hqspeaker$physicalY() { return physicalY; }

    @Override
    public synchronized double hqspeaker$physicalZ() { return physicalZ; }

    @Override
    public synchronized void hqspeaker$setAcousticPosition(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public synchronized void hqspeaker$clearAcousticPosition() {
        this.x = physicalX;
        this.y = physicalY;
        this.z = physicalZ;
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
