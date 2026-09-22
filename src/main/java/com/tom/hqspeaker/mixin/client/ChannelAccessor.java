package com.tom.hqspeaker.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Channel.class)
public interface ChannelAccessor {
    @Invoker("pumpBuffers")
    void hqspeaker$pumpBuffers(int count);
}
