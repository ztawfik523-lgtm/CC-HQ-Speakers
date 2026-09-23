package com.tom.hqspeaker;

import com.mojang.logging.LogUtils;
import com.tom.hqspeaker.config.HQSpeakerServerConfig;
import com.tom.hqspeaker.media.ServerMediaAssets;
import com.tom.hqspeaker.network.HQSpeakerNetwork;
import com.tom.hqspeaker.peripheral.HQSpeakerPeripheralProvider;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

import java.io.IOException;

@Mod("hqspeaker")
public class HQSpeakerMod {

    public static final String MOD_ID = "hqspeaker";
    private static final Logger LOGGER = LogUtils.getLogger();

    public HQSpeakerMod(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::setup);
        modEventBus.addListener(HQSpeakerNetwork::register);
        modContainer.registerConfig(ModConfig.Type.SERVER, HQSpeakerServerConfig.SPEC);

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
        ServerMediaAssets.tickPendingReleases();
        com.tom.hqspeaker.peripheral.HQSpeakerPeripheral.tickAllActive();
        com.tom.hqspeaker.peripheral.HQFiniteMediaServer.tickAll();
        com.tom.hqspeaker.peripheral.HQSpeakerCompositePeripheral.tickAll();
    }

    /** Explicitly evict Level-keyed speaker composites before their server Level can become stale. */
    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide) {
            HQSpeakerPeripheralProvider.forgetLevel(level);
        }
    }

    /** Start range-worker shutdown before final server cleanup so ServerStopped normally only has to drain it. */
    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        ServerMediaAssets.beginCloseServer(event.getServer());
    }

    /** Final cache/media safety net for integrated-server restart and dedicated-server shutdown. */
    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        // Speaker cleanup releases prepared/playback references before the shared store removes any remaining files.
        HQSpeakerPeripheralProvider.clearAll();
        try {
            ServerMediaAssets.closeServer(event.getServer());
        } catch (IOException e) {
            warn("could not close server media asset store: " + e.getMessage());
        }
    }

    @SubscribeEvent
    @OnlyIn(Dist.CLIENT)
    public void onClientTick(ClientTickEvent.Post event) {
        com.tom.hqspeaker.client.HQSpeakerClientHandler.tick();
        com.tom.hqspeaker.client.HQFiniteMediaClient.tick();
        com.tom.hqspeaker.client.HQAudioDiagnosticsClient.tick();
    }

    public static void log(String msg)   { LOGGER.info("[HQSpeaker] {}", msg); }
    public static void warn(String msg)  { LOGGER.warn("[HQSpeaker] {}", msg); }
    public static void error(String msg) { LOGGER.error("[HQSpeaker] {}", msg); }
}
