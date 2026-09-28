package com.tom.hqspeaker.mixin.client;

import com.tom.hqspeaker.client.HQAudioDiagnosticsClient;
import com.tom.hqspeaker.client.HQSoundPhysicsAcousticsClient;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

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
     * Replace only the final environment arguments for a bound HQ source.
     *
     * <p>SPR still performs its complete native room/reverb/reflection calculation. The HQ layer
     * reuses SPR's own private runOcclusion primitive for the accepted 17/9-path direct model,
     * then smooths the resulting direct pair and native wet-send targets before SPR applies them.</p>
     */
    @ModifyArgs(
        method = "evaluateEnvironment(IDDDLnet/minecraft/sounds/SoundSource;Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/world/phys/Vec3;",
        at = @At(
            value = "INVOKE",
            target = "Lcom/sonicether/soundphysics/SoundPhysics;setEnvironment(IFFFFFFFFFF)V"
        ),
        remap = false,
        require = 0
    )
    private static void hqspeaker$adjustEnvironment(Args args) {
        int sourceId = (Integer) args.get(0);
        HQSoundPhysicsAcousticsClient.AdjustedEnvironment adjusted =
            HQSoundPhysicsAcousticsClient.adjustEnvironment(
                sourceId,
                (Float) args.get(1), (Float) args.get(2), (Float) args.get(3), (Float) args.get(4),
                (Float) args.get(5), (Float) args.get(6), (Float) args.get(7), (Float) args.get(8),
                (Float) args.get(9), (Float) args.get(10),
                (sx, sy, sz, lx, ly, lz) -> runOcclusion(
                    new Vec3(sx, sy, sz), new Vec3(lx, ly, lz)));

        if (adjusted == null) return;
        args.set(1, adjusted.sendGain0());
        args.set(2, adjusted.sendGain1());
        args.set(3, adjusted.sendGain2());
        args.set(4, adjusted.sendGain3());
        args.set(5, adjusted.sendCutoff0());
        args.set(6, adjusted.sendCutoff1());
        args.set(7, adjusted.sendCutoff2());
        args.set(8, adjusted.sendCutoff3());
        args.set(9, adjusted.directCutoff());
        args.set(10, adjusted.directGain());
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
