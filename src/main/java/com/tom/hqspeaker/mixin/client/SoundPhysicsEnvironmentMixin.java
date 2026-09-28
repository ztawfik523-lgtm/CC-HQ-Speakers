package com.tom.hqspeaker.mixin.client;

import com.tom.hqspeaker.client.HQAudioDiagnosticsClient;
import com.tom.hqspeaker.client.HQSoundPhysicsAcousticsClient;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    @Shadow(remap = false)
    public static void setEnvironment(
        int sourceId,
        float r0, float r1, float r2, float r3,
        float h0, float h1, float h2, float h3,
        float directCutoff, float directGain
    ) {
        throw new AssertionError("mixin shadow");
    }

    /**
     * Replace only the final normal SPR environment application for a bound HQ source.
     *
     * <p>SPR still performs its complete room/reverb/reflection calculation. HQ reuses SPR's own
     * runOcclusion primitive for the approved progressive direct model, then applies the resulting
     * environment with private per-source filters. Early/default SPR environments and any failure path
     * call the untouched native setEnvironment method.</p>
     */
    @Redirect(
        method = "evaluateEnvironment(IDDDLnet/minecraft/sounds/SoundSource;Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/world/phys/Vec3;",
        at = @At(
            value = "INVOKE",
            target = "Lcom/sonicether/soundphysics/SoundPhysics;setEnvironment(IFFFFFFFFFF)V"
        ),
        remap = false,
        require = 0
    )
    private static void hqspeaker$applyEnvironment(
        int sourceId,
        float r0, float r1, float r2, float r3,
        float h0, float h1, float h2, float h3,
        float directCutoff, float directGain
    ) {
        boolean handled = HQSoundPhysicsAcousticsClient.applyEnvironment(
            sourceId,
            r0, r1, r2, r3,
            h0, h1, h2, h3,
            directCutoff, directGain,
            (sx, sy, sz, lx, ly, lz) -> runOcclusion(
                new Vec3(sx, sy, sz), new Vec3(lx, ly, lz)));
        if (handled) return;

        setEnvironment(
            sourceId,
            r0, r1, r2, r3,
            h0, h1, h2, h3,
            directCutoff, directGain);
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
