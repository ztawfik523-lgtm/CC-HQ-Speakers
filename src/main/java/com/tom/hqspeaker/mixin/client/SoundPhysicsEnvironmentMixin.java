package com.tom.hqspeaker.mixin.client;

import com.tom.hqspeaker.client.HQAudioDiagnosticsClient;
import com.tom.hqspeaker.client.HQSoundPhysicsAcousticsClient;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
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
    @Shadow(remap = false)
    private static double runOcclusion(Vec3 soundPos, Vec3 playerPos) {
        throw new AssertionError("mixin shadow");
    }

    /**
     * Isolate every environment write for an already-bound HQ source.
     *
     * <p>This intentionally covers both the normal final environment and SPR's early/default environment
     * paths (for example its sound-rate limiter). Once an HQ source owns private filters, no later native
     * default write may reconnect it to SPR's shared mutable low-pass filters.</p>
     */
    @Inject(
        method = "setEnvironment(IFFFFFFFFFF)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void hqspeaker$applyPrivateEnvironment(
        int sourceId,
        float r0, float r1, float r2, float r3,
        float h0, float h1, float h2, float h3,
        float directCutoff, float directGain,
        CallbackInfo ci
    ) {
        boolean handled = HQSoundPhysicsAcousticsClient.applyEnvironment(
            sourceId,
            r0, r1, r2, r3,
            h0, h1, h2, h3,
            directCutoff, directGain,
            (sx, sy, sz, lx, ly, lz) -> runOcclusion(
                new Vec3(sx, sy, sz), new Vec3(lx, ly, lz)));
        if (handled) ci.cancel();
    }

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
