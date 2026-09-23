package com.tom.hqspeaker.mixin.client;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Channel.class)
public interface ChannelAccessor {
    @Accessor("source")
    int hqspeaker$getSource();

    @Invoker("pumpBuffers")
    void hqspeaker$pumpBuffers(int count);
}
