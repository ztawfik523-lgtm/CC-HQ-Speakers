package com.tom.hqspeaker;

import com.tom.hqspeaker.network.HQSpeakerNetwork;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod("hqspeaker")
public class HQSpeakerMod {

    public static final String MOD_ID = "hqspeaker";

    public HQSpeakerMod(IEventBus modEventBus) {
        HQSpeakerRegistry.register(modEventBus);
        modEventBus.addListener(this::setup);
        modEventBus.addListener(HQSpeakerNetwork::register);

        if (FMLEnvironment.dist == Dist.CLIENT) {
            modEventBus.addListener(this::clientSetup);
        }

        NeoForge.EVENT_BUS.register(this);
        log("HQSpeaker mod loaded");
    }

    private void setup(FMLCommonSetupEvent event) {
        log("HQSpeaker common setup complete");
    }

    @OnlyIn(Dist.CLIENT)
    private void clientSetup(FMLClientSetupEvent event) {
        log("HQSpeaker client setup complete");
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        com.tom.hqspeaker.peripheral.HQSpeakerPeripheral.tickAllActive();
        com.tom.hqspeaker.peripheral.HQFiniteMediaServer.tickAll();
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public void onClientTick(ClientTickEvent.Post event) {
        com.tom.hqspeaker.client.HQSpeakerClientHandler.tick();
        com.tom.hqspeaker.client.HQFiniteMediaClient.tick();
    }

    public static void log(String msg)   { System.out.println("[HQSpeaker] " + msg); }
    public static void warn(String msg)  { System.err.println("[HQSpeaker WARN] " + msg); }
    public static void error(String msg) { System.err.println("[HQSpeaker ERROR] " + msg); }
}
