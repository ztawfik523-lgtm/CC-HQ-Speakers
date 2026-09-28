package com.tom.hqspeaker.mixin.client;

import com.tom.hqspeaker.client.HQAudioDiagnosticsClient;
import com.tom.hqspeaker.client.HQSoundPhysicsRefreshClient;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional diagnostic hook proving that Sound Physics Remastered actually evaluates an HQ source through processSound.
 *
 * <p>This deliberately does not change SPR behavior. It records only the exact 1.21.1-1.5.1 processing overload and
 * remains inert when SPR is absent.</p>
 */
@Pseudo
@Mixin(targets = "com.sonicether.soundphysics.SoundPhysics", remap = false)
public abstract class SoundPhysicsProcessMixin {
    @Inject(
        method = "processSound(IDDDLnet/minecraft/sounds/SoundSource;Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/world/phys/Vec3;",
        at = @At("HEAD"),
        remap = false,
        require = 0
    )
    private static void hqspeaker$beginProcessSound(
        int sourceId,
        double x, double y, double z,
        SoundSource category,
        ResourceLocation sound,
        boolean auxOnly,
        CallbackInfoReturnable<Vec3> cir
    ) {
        HQAudioDiagnosticsClient.soundPhysicsProcessBegin(
            sourceId, sound == null ? "" : sound.toString());
    }

    @Inject(
        method = "processSound(IDDDLnet/minecraft/sounds/SoundSource;Lnet/minecraft/resources/ResourceLocation;Z)Lnet/minecraft/world/phys/Vec3;",
        at = @At("RETURN"),
        remap = false,
        require = 0
    )
    private static void hqspeaker$captureProcessSound(
        int sourceId,
        double x, double y, double z,
        SoundSource category,
        ResourceLocation sound,
        boolean auxOnly,
        CallbackInfoReturnable<Vec3> cir
    ) {
        Vec3 reflected = cir.getReturnValue();
        String soundId = sound == null ? "" : sound.toString();
        HQSoundPhysicsRefreshClient.soundPhysicsProcessed(sourceId, x, y, z, soundId);
        HQAudioDiagnosticsClient.soundPhysicsProcessed(
            sourceId,
            x, y, z,
            category == null ? "" : category.getName(),
            soundId,
            reflected != null,
            reflected == null ? 0.0 : reflected.x,
            reflected == null ? 0.0 : reflected.y,
            reflected == null ? 0.0 : reflected.z);
        HQAudioDiagnosticsClient.soundPhysicsProcessEnd(sourceId);
    }
}
