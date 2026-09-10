package com.tom.hqspeaker.client;

import com.mojang.blaze3d.audio.Channel;
import com.tom.hqspeaker.mixin.client.SoundEngineAccessor;
import com.tom.hqspeaker.mixin.client.SoundManagerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.sounds.SoundSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
final class HQSoundChannelControl {
    private HQSoundChannelControl() {}

    static boolean execute(SoundInstance sound, Consumer<Channel> action) {
        SoundManager manager = Minecraft.getInstance().getSoundManager();
        SoundEngine engine = ((SoundManagerAccessor) manager).hqspeaker$getSoundEngine();
        ChannelAccess.ChannelHandle handle =
            ((SoundEngineAccessor) engine).hqspeaker$getInstanceToChannel().get(sound);
        if (handle == null || handle.isStopped()) return false;
        handle.execute(action);
        return true;
    }

    static void refreshBlocksVolume() {
        Minecraft minecraft = Minecraft.getInstance();
        float slider = minecraft.options.getSoundSourceVolume(SoundSource.BLOCKS);
        minecraft.getSoundManager().updateSourceVolume(SoundSource.BLOCKS, slider);
    }
}
