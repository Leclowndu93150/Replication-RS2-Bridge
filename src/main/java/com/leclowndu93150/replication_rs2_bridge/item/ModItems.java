package com.leclowndu93150.replication_rs2_bridge.item;

import com.leclowndu93150.replication_rs2_bridge.ReplicationRSBridge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, ReplicationRSBridge.MODID);

    public static final DeferredHolder<Item, Item> UNIVERSAL_MATTER = ITEMS.register("universal_matter",
        () -> new UniversalMatterItem(new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
