package com.tom.hqspeaker;

import com.tom.hqspeaker.peripheral.HQSpeakerBlockEntity;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class HQSpeakerRegistry {

    public static final DeferredRegister<Block> BLOCKS =
        DeferredRegister.create(BuiltInRegistries.BLOCK, HQSpeakerMod.MOD_ID);

    public static final DeferredRegister<Item> ITEMS =
        DeferredRegister.create(BuiltInRegistries.ITEM, HQSpeakerMod.MOD_ID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, HQSpeakerMod.MOD_ID);

    public static final DeferredHolder<Block, Block> SPEAKER_BLOCK =
        BLOCKS.register("hq_speaker", () -> new HQSpeakerBlock());

    public static final DeferredHolder<Item, Item> SPEAKER_BLOCK_ITEM =
        ITEMS.register("hq_speaker",
            () -> new BlockItem(SPEAKER_BLOCK.get(), new Item.Properties()));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<HQSpeakerBlockEntity>> SPEAKER_BLOCK_ENTITY =
        BLOCK_ENTITIES.register("hq_speaker",
            () -> BlockEntityType.Builder.of(HQSpeakerBlockEntity::new, SPEAKER_BLOCK.get()).build(null));

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
    }
}
