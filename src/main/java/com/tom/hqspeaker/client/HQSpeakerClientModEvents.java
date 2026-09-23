package com.tom.hqspeaker.client;

import com.tom.hqspeaker.HQSpeakerMod;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.sound.SoundEngineLoadEvent;

/** Client-only mod-bus hooks used by the built-in diagnostic subsystem. */
@EventBusSubscriber(modid = HQSpeakerMod.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class HQSpeakerClientModEvents {
    private HQSpeakerClientModEvents() {}

    @SubscribeEvent
    public static void soundEngineLoad(SoundEngineLoadEvent event) {
        HQAudioDiagnosticsClient.soundEngineReloaded();
    }
}
