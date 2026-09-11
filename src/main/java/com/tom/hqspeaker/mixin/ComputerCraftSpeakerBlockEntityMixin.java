package com.tom.hqspeaker.mixin;

import com.tom.hqspeaker.peripheral.HQSpeakerPeripheralProvider;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.shared.peripheral.speaker.SpeakerBlockEntity;
import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpeakerBlockEntity.class)
public abstract class ComputerCraftSpeakerBlockEntityMixin {
    @Shadow(remap = false) @Final
    private SpeakerPeripheral peripheral;

    @Inject(method = "peripheral", at = @At("HEAD"), cancellable = true, remap = false)
    private void hqspeaker$replaceSpeakerPeripheral(CallbackInfoReturnable<IPeripheral> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        BlockPos pos = self.getBlockPos();
        if (level == null || level.isClientSide || pos == null) return;
        cir.setReturnValue(HQSpeakerPeripheralProvider.getOrCreate(level, pos, peripheral));
    }

    @Inject(method = "setRemoved", at = @At("TAIL"), remap = false)
    private void hqspeaker$forgetSpeakerPeripheral(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level != null && !level.isClientSide) {
            HQSpeakerPeripheralProvider.forget(level, self.getBlockPos());
        }
    }
}
