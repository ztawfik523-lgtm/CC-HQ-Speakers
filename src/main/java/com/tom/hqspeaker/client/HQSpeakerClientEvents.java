package com.tom.hqspeaker.client;

import com.tom.hqspeaker.HQSpeakerMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.PlayStreamingSourceEvent;

/**
 * Client-only bridge between Minecraft's streaming sound channel and HQ producer-fed RAW audio.
 */
@EventBusSubscriber(modid = HQSpeakerMod.MOD_ID, value = Dist.CLIENT)
public final class HQSpeakerClientEvents {
    private HQSpeakerClientEvents() {}

    @SubscribeEvent
    public static void playStreaming(PlayStreamingSourceEvent event) {
        if (!(event.getSound() instanceof HQSpeakerClientHandler.HQSpeakerSound sound)) return;
        sound.hqStream().attachChannel(event.getEngine(), event.getChannel());
    }
}
