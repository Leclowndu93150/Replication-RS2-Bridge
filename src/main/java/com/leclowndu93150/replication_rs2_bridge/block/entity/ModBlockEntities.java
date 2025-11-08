package com.leclowndu93150.replication_rs2_bridge.block.entity;

import com.leclowndu93150.replication_rs2_bridge.ReplicationRSBridge;
import com.leclowndu93150.replication_rs2_bridge.block.ModBlocks;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(BuiltInRegistries.BLOCK_ENTITY_TYPE, ReplicationRSBridge.MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<RepRS2BridgeBlockEntity>> REP_RS2_BRIDGE =
            BLOCK_ENTITIES.register("rep_rs2_bridge", () ->
                    BlockEntityType.Builder.of(RepRS2BridgeBlockEntity::new,
                            ModBlocks.REP_RS2_BRIDGE.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
