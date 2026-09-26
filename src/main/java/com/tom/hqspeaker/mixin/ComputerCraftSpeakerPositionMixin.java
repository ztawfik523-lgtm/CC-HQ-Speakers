package com.tom.hqspeaker.mixin;

import com.tom.hqspeaker.compat.MovingSourcePosition;
import dan200.computercraft.shared.peripheral.speaker.SpeakerBlockEntity;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPosition;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keep native CC:T speaker playback on CC:T's own implementation while resolving
 * the speaker position through the same Sable/VS2/static path as HQ audio.
 *
 * <p>Sable sub-level blocks live at plot-space coordinates. CC:T's block speaker
 * normally publishes the raw block centre, which makes native playNote/playSound/
 * playAudio inaudible once that block is inside a moving Sable sub-level. Replacing
 * only the returned SpeakerPosition preserves every native CC:T playback semantic
 * while giving its normal packets the real world-space source position.</p>
 */
@Mixin(targets = "dan200.computercraft.shared.peripheral.speaker.SpeakerBlockEntity$Peripheral")
public abstract class ComputerCraftSpeakerPositionMixin {
    @Shadow(remap = false) @Final
    private SpeakerBlockEntity speaker;

    @Inject(method = "getPosition", at = @At("RETURN"), cancellable = true, remap = false)
    private void hqspeaker$resolveMovingSpeakerPosition(CallbackInfoReturnable<SpeakerPosition> cir) {
        var level = speaker.getLevel();
        if (level == null) return;

        Vector3d resolved = MovingSourcePosition.resolve(level, speaker.getBlockPos(), new Vector3d());
        cir.setReturnValue(SpeakerPosition.of(level, new Vec3(resolved.x, resolved.y, resolved.z)));
    }
}
