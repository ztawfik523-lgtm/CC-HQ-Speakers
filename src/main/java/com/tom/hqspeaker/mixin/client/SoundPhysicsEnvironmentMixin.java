package com.tom.hqspeaker.mixin.client;

import com.tom.hqspeaker.client.HQAudioDiagnosticsClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Optional diagnostic bridge for Sound Physics Remastered.
 *
 * <p>SPR already computes the direct low-pass gain/cutoff we need for acceptance.
 * Capturing those values here is both more accurate and safer than attempting to
 * query OpenAL's write-only AL_DIRECT_FILTER source property.</p>
 */
@Pseudo
@Mixin(targets = "com.sonicether.soundphysics.SoundPhysics", remap = false)
public abstract class SoundPhysicsEnvironmentMixin {
    @Inject(method = "setEnvironment", at = @At("TAIL"), remap = false, require = 0)
    private static void hqspeaker$captureEnvironment(
        int sourceID,
        float sendGain0, float sendGain1, float sendGain2, float sendGain3,
        float sendCutoff0, float sendCutoff1, float sendCutoff2, float sendCutoff3,
        float directCutoff, float directGain,
        CallbackInfo ci
    ) {
        HQAudioDiagnosticsClient.soundPhysicsApplied(sourceID, directGain, directCutoff);
    }
}
