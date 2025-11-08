package com.leclowndu93150.replication_rs2_bridge;

import com.leclowndu93150.replication_rs2_bridge.block.ModBlocks;
import com.leclowndu93150.replication_rs2_bridge.block.entity.ModBlockEntities;
import com.leclowndu93150.replication_rs2_bridge.block.entity.RepRS2BridgeBlockEntity;

import com.refinedmods.refinedstorage.neoforge.api.RefinedStorageNeoForgeApi;
import net.minecraft.core.Direction;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

/**
 * Capability registration for exposing the bridge inventory to vanilla/Forge automation.
 */
public final class RepRS2BridgeCapabilities {
    private RepRS2BridgeCapabilities() {
    }

    public static void register(final RegisterCapabilitiesEvent event) {
        event.registerBlock(
            Capabilities.ItemHandler.BLOCK,
            (level, pos, state, blockEntity, direction) -> {
                if (blockEntity instanceof RepRS2BridgeBlockEntity bridge
                    && (direction == Direction.UP || direction == null)) {
                    return bridge.getOutput();
                }
                return null;
            },
            ModBlocks.REP_RS2_BRIDGE.get()
        );

        event.registerBlockEntity(
            RefinedStorageNeoForgeApi.INSTANCE.getNetworkNodeContainerProviderCapability(),
            ModBlockEntities.REP_RS2_BRIDGE.get(),
            (blockEntity, direction) -> blockEntity.getContainerProvider()
        );
    }
}
