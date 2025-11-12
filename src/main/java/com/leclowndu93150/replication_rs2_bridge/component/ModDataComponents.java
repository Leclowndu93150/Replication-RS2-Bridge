package com.leclowndu93150.replication_rs2_bridge.component;

import com.leclowndu93150.replication_rs2_bridge.ReplicationRSBridge;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ReplicationRSBridge.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<MatterComponent>> MATTER =
            DATA_COMPONENTS.register("matter", () -> DataComponentType.<MatterComponent>builder()
                    .persistent(MatterComponent.CODEC)
                    .networkSynchronized(MatterComponent.STREAM_CODEC)
                    .build());

    public static void register(IEventBus eventBus) {
        DATA_COMPONENTS.register(eventBus);
    }
}
