package com.tom.hqspeaker.peripheral;

import dan200.computercraft.shared.peripheral.speaker.SpeakerPeripheral;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

public class HQSpeakerPeripheralProvider {
    private static final ResourceLocation CC_SPEAKER_ID = ResourceLocation.fromNamespaceAndPath("computercraft", "speaker");

    // Level identity is part of the cache boundary. Weak keys prevent an integrated-server world from
    // pinning old Level instances across unload/reload.
    private static final Map<Level, ConcurrentHashMap<BlockPos, HQSpeakerCompositePeripheral>> CACHE =
        Collections.synchronizedMap(new WeakHashMap<>());

    public static HQSpeakerCompositePeripheral getOrCreate(Level world, BlockPos pos, SpeakerPeripheral vanilla) {
        if (world == null || world.isClientSide) throw new IllegalArgumentException("speaker peripheral is server-only");
        ConcurrentHashMap<BlockPos, HQSpeakerCompositePeripheral> levelCache;
        synchronized (CACHE) {
            levelCache = CACHE.computeIfAbsent(world, ignored -> new ConcurrentHashMap<>());
        }
        BlockPos key = pos.immutable();
        return levelCache.computeIfAbsent(key, ignored -> {
            HQSpeakerPeripheral legacy = new HQSpeakerPeripheral(key, world);
            HQFiniteMediaServer finite = new HQFiniteMediaServer(world, key);
            return new HQSpeakerCompositePeripheral(legacy, vanilla, finite);
        });
    }

    public static boolean isComputerCraftSpeaker(Level world, BlockPos pos) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof dan200.computercraft.shared.peripheral.speaker.SpeakerBlockEntity) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(world.getBlockState(pos).getBlock());
        return CC_SPEAKER_ID.equals(id);
    }

    public static void forget(Level world, BlockPos pos) {
        ConcurrentHashMap<BlockPos, HQSpeakerCompositePeripheral> levelCache;
        synchronized (CACHE) { levelCache = CACHE.get(world); }
        if (levelCache == null) return;
        HQSpeakerCompositePeripheral peripheral = levelCache.remove(pos);
        if (peripheral != null) peripheral.cleanup();
        if (levelCache.isEmpty()) {
            synchronized (CACHE) { if (levelCache.isEmpty()) CACHE.remove(world); }
        }
    }
}
