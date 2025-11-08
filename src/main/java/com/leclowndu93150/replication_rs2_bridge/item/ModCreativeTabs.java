package com.leclowndu93150.replication_rs2_bridge.item;

import com.leclowndu93150.replication_rs2_bridge.ReplicationRSBridge;
import com.leclowndu93150.replication_rs2_bridge.block.ModBlocks;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ReplicationRSBridge.MODID);

    public static final Supplier<CreativeModeTab> REPLICATION_RS2_BRIDGE_TAB = CREATIVE_MODE_TABS.register("replication_rs2_bridge_tab",
            () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModBlocks.REP_RS2_BRIDGE.get()))
                    .title(Component.translatable("itemGroup.replication_rs2_bridge"))
                    .displayItems((parameters, output) -> {
                        output.accept(ModBlocks.REP_RS2_BRIDGE.get());
                    })
                    .build());

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
