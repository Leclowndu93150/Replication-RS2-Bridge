package com.leclowndu93150.replication_rs2_bridge.item;

import com.leclowndu93150.replication_rs2_bridge.ReplicationRSBridge;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(BuiltInRegistries.ITEM, ReplicationRSBridge.MODID);

    public static final DeferredHolder<Item, Item> EARTH_MATTER = ITEMS.register("earth",
        () -> new MatterItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> NETHER_MATTER = ITEMS.register("nether",
        () -> new MatterItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> ORGANIC_MATTER = ITEMS.register("organic",
        () -> new MatterItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> ENDER_MATTER = ITEMS.register("ender",
        () -> new MatterItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> METALLIC_MATTER = ITEMS.register("metallic",
        () -> new MatterItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> PRECIOUS_MATTER = ITEMS.register("precious",
        () -> new MatterItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> LIVING_MATTER = ITEMS.register("living",
        () -> new MatterItem(new Item.Properties()));

    public static final DeferredHolder<Item, Item> QUANTUM_MATTER = ITEMS.register("quantum",
        () -> new QuantumMatterItem(new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
