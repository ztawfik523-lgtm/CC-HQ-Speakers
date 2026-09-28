package com.tom.hqspeaker.mixin.client;

import com.tom.hqspeaker.client.HQSoundPhysicsAcousticsClient;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Prevents a bound HQ source from being exposed to SPR's raw reflected OpenAL position.
 *
 * <p>SPR still computes and returns the reflected target. The process-return hook feeds that target into
 * the approved HQ reflection stabilizer, which owns the final render position for the HQ source.</p>
 */
@Pseudo
@Mixin(targets = "com.sonicether.soundphysics.SoundPhysics", remap = false)
public abstract class SoundPhysicsPositionMixin {
    @Inject(
        method = "setSoundPos(ILnet/minecraft/world/phys/Vec3;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false,
        require = 0
    )
    private static void hqspeaker$stabilizeReflectedPosition(
        int sourceId,
        Vec3 position,
        CallbackInfo ci
    ) {
        if (HQSoundPhysicsAcousticsClient.shouldSuppressReflectedPosition(sourceId)) {
            ci.cancel();
        }
    }
}
