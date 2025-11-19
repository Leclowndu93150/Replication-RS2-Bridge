package com.leclowndu93150.replication_rs2_bridge.block;

import com.leclowndu93150.replication_rs2_bridge.ReplicationRSBridge;
import com.leclowndu93150.replication_rs2_bridge.block.custom.RepRS2BridgeBlock;
import com.leclowndu93150.replication_rs2_bridge.item.ModItems;
import com.leclowndu93150.replication_rs2_bridge.item.RepRS2BridgeBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModBlocks {
    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(ReplicationRSBridge.MODID);

    public static final DeferredBlock<Block> REP_RS2_BRIDGE = BLOCKS.register("rep_rs2_bridge",
            () -> new RepRS2BridgeBlock(BlockBehaviour.Properties.of()
                .strength(5.0F, 6.0F)
                .sound(SoundType.COPPER)
                .noOcclusion()));

    static {
        ModItems.ITEMS.register("rep_rs2_bridge", 
            () -> new RepRS2BridgeBlockItem(REP_RS2_BRIDGE.get(), new Item.Properties()));
    }

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
