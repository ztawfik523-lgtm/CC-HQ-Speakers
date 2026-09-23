package com.tom.hqspeaker.client;

import com.mojang.blaze3d.audio.Channel;
import com.tom.hqspeaker.diagnostics.HQDiagnostics;
import com.tom.hqspeaker.mixin.client.ChannelAccessor;
import com.tom.hqspeaker.mixin.client.SoundEngineAccessor;
import dan200.computercraft.client.sound.SpeakerSound;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.fml.ModList;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.SOFTSourceLatency;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Samples the actual Minecraft/OpenAL channels owned by HQ Speakers.
 *
 * <p>All OpenAL queries are dispatched onto Minecraft's sound executor. The common diagnostics registry contains
 * only plain Java data so the integrated server/ComputerCraft side can read it without loading client classes.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class HQAudioDiagnosticsClient {
    private static final ConcurrentHashMap<UUID, Binding> BINDINGS = new ConcurrentHashMap<>();
    private static final AtomicBoolean SAMPLE_SCHEDULED = new AtomicBoolean();

    private HQAudioDiagnosticsClient() {}

    public static void attach(SoundEngine engine, SoundInstance sound, Channel channel, HQDiagnosticSource diagnostic) {
        if (diagnostic == null) return;
        attachIdentity(engine, sound, channel, diagnostic.hqspeaker$diagnosticIdentity());
    }

    /** Observe CC:T's own speaker channels, including static playNote/playSound and DFPWM playAudio. */
    public static void attachNativeComputerCraft(SoundEngine engine, SoundInstance sound, Channel channel) {
        if (!(sound instanceof SpeakerSound ccSound)) return;
        boolean streaming = ccSound.getStream() != null;
        long key = Integer.toUnsignedLong(System.identityHashCode(sound));
        UUID source = new UUID(0x4343544e41544956L, key); // "CCTNATIV" + per-instance identity.
        HQDiagnostics.SourceIdentity identity = new HQDiagnostics.SourceIdentity(
            source,
            streaming ? "native" : "native-static",
            "",
            (int) Math.floor(sound.getX()),
            (int) Math.floor(sound.getY()),
            (int) Math.floor(sound.getZ()),
            streaming ? 48_000 : 0
        );
        attachIdentity(engine, sound, channel, identity);
    }

    private static void attachIdentity(
        SoundEngine engine, SoundInstance sound, Channel channel, HQDiagnostics.SourceIdentity identity
    ) {
        if (!HQDiagnostics.enabled() || engine == null || sound == null || channel == null || identity == null) return;

        int sourceId = ((ChannelAccessor) (Object) channel).hqspeaker$getSource();
        SoundEngineExecutor executor = ((SoundEngineAccessor) (Object) engine).hqspeaker$getExecutor();
        if (sourceId <= 0 || executor == null) return;

        boolean sourceLatency = false;
        String vendor = "";
        String renderer = "";
        int directFilter = 0;
        float directGain = 1.0f;
        float directGainHF = 1.0f;

        try {
            sourceLatency = AL10.alIsExtensionPresent("AL_SOFT_source_latency");
            String one = AL10.alGetString(AL10.AL_VENDOR);
            String two = AL10.alGetString(AL10.AL_RENDERER);
            vendor = one == null ? "" : one;
            renderer = two == null ? "" : two;

            if (ModList.get().isLoaded("sound_physics_remastered")) {
                directFilter = AL10.alGetSourcei(sourceId, EXTEfx.AL_DIRECT_FILTER);
                if (directFilter != 0 && EXTEfx.alIsFilter(directFilter)) {
                    directGain = EXTEfx.alGetFilterf(directFilter, EXTEfx.AL_LOWPASS_GAIN);
                    directGainHF = EXTEfx.alGetFilterf(directFilter, EXTEfx.AL_LOWPASS_GAINHF);
                }
            }
        } catch (RuntimeException ignored) {
            // Diagnostics must never interfere with playback. Missing optional AL/EFX queries simply remain unknown.
        }

        Minecraft minecraft = Minecraft.getInstance();
        HQDiagnostics.updateClientCapabilities(
            minecraft.getSingleplayerServer() != null,
            sourceLatency,
            ModList.get().isLoaded("sound_physics_remastered"),
            vendor,
            renderer
        );
        HQDiagnostics.channelStarted(identity, directFilter, directGain, directGainHF);
        BINDINGS.put(identity.source(),
            new Binding(identity, sound, sourceId, executor, HQDiagnostics.epoch()));
    }

    public static void detach(UUID source) {
        if (source == null) return;
        if (BINDINGS.remove(source) != null) HQDiagnostics.channelDetached(source);
    }

    public static void soundEngineReloaded() {
        BINDINGS.clear();
        SAMPLE_SCHEDULED.set(false);
        HQDiagnostics.soundEngineReloaded();
    }

    public static void tick() {
        if (!HQDiagnostics.enabled() || BINDINGS.isEmpty()) return;
        if (!SAMPLE_SCHEDULED.compareAndSet(false, true)) return;

        ArrayList<Request> requests = new ArrayList<>();
        SoundEngineExecutor executor = null;
        long epoch = HQDiagnostics.epoch();
        for (Map.Entry<UUID, Binding> entry : BINDINGS.entrySet()) {
            Binding binding = entry.getValue();
            if (binding == null) continue;
            if (binding.epoch() != epoch) {
                BINDINGS.remove(entry.getKey(), binding);
                continue;
            }
            if (executor == null) executor = binding.executor();
            if (binding.executor() != executor) continue;
            SoundInstance sound = binding.sound();
            requests.add(new Request(
                binding,
                (float) sound.getX(),
                (float) sound.getY(),
                (float) sound.getZ()
            ));
        }

        if (executor == null || requests.isEmpty()) {
            SAMPLE_SCHEDULED.set(false);
            return;
        }

        executor.execute(() -> {
            try {
                boolean latencyAvailable = AL10.alIsExtensionPresent("AL_SOFT_source_latency");
                ArrayList<HQDiagnostics.ChannelSample> samples = new ArrayList<>(requests.size());
                for (Request request : requests) {
                    Binding binding = request.binding();
                    int source = binding.sourceId();
                    if (source <= 0 || !AL10.alIsSource(source)) {
                        BINDINGS.remove(binding.identity().source(), binding);
                        continue;
                    }

                    int queued = AL10.alGetSourcei(source, AL10.AL_BUFFERS_QUEUED);
                    int processed = AL10.alGetSourcei(source, AL10.AL_BUFFERS_PROCESSED);

                    long fixedSampleOffset;
                    double secondsOffset;
                    double latencySeconds;
                    if (latencyAvailable) {
                        long[] sampleLatency = new long[2];
                        SOFTSourceLatency.alGetSourcei64vSOFT(
                            source, SOFTSourceLatency.AL_SAMPLE_OFFSET_LATENCY_SOFT, sampleLatency);
                        fixedSampleOffset = sampleLatency[0];

                        double[] secondsLatency = new double[2];
                        SOFTSourceLatency.alGetSourcedvSOFT(
                            source, SOFTSourceLatency.AL_SEC_OFFSET_LATENCY_SOFT, secondsLatency);
                        secondsOffset = secondsLatency[0];
                        latencySeconds = Math.max(0.0, secondsLatency[1]);
                    } else {
                        int sampleOffset = AL10.alGetSourcei(source, AL11.AL_SAMPLE_OFFSET);
                        fixedSampleOffset = ((long) sampleOffset) << 32;
                        secondsOffset = AL10.alGetSourcef(source, AL11.AL_SEC_OFFSET);
                        latencySeconds = 0.0;
                    }

                    float[] actual = new float[3];
                    AL10.alGetSourcefv(source, AL10.AL_POSITION, actual);
                    float sourceGain = AL10.alGetSourcef(source, AL10.AL_GAIN);

                    int directFilter = 0;
                    float directGain = 1.0f;
                    float directGainHF = 1.0f;
                    if (ModList.get().isLoaded("sound_physics_remastered")) {
                        try {
                            directFilter = AL10.alGetSourcei(source, EXTEfx.AL_DIRECT_FILTER);
                            if (directFilter != 0 && EXTEfx.alIsFilter(directFilter)) {
                                directGain = EXTEfx.alGetFilterf(directFilter, EXTEfx.AL_LOWPASS_GAIN);
                                directGainHF = EXTEfx.alGetFilterf(directFilter, EXTEfx.AL_LOWPASS_GAINHF);
                            }
                        } catch (RuntimeException ignored) {
                            directFilter = 0;
                            directGain = 1.0f;
                            directGainHF = 1.0f;
                        }
                    }

                    // Query state last. A streaming source can underrun between the offset query and state query;
                    // checking last makes that race visible instead of reporting an older PLAYING state.
                    int state = AL10.alGetSourcei(source, AL10.AL_SOURCE_STATE);
                    samples.add(new HQDiagnostics.ChannelSample(
                        binding.identity(),
                        stateName(state),
                        queued,
                        processed,
                        fixedSampleOffset,
                        secondsOffset,
                        latencySeconds,
                        request.x(),
                        request.y(),
                        request.z(),
                        actual[0],
                        actual[1],
                        actual[2],
                        sourceGain,
                        directFilter,
                        directGain,
                        directGainHF
                    ));
                }
                HQDiagnostics.recordBatch(epoch, samples);
            } catch (RuntimeException ignored) {
                // Measurement failure must never break the actual audio path.
            } finally {
                SAMPLE_SCHEDULED.set(false);
            }
        });
    }

    private static String stateName(int state) {
        if (state == AL10.AL_PLAYING) return "playing";
        if (state == AL10.AL_PAUSED) return "paused";
        if (state == AL10.AL_STOPPED) return "stopped";
        if (state == AL10.AL_INITIAL) return "initial";
        return "unknown";
    }

    private record Binding(
        HQDiagnostics.SourceIdentity identity,
        SoundInstance sound,
        int sourceId,
        SoundEngineExecutor executor,
        long epoch
    ) {}

    private record Request(Binding binding, float x, float y, float z) {}
}
