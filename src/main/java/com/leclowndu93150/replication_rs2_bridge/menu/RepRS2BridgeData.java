package com.leclowndu93150.replication_rs2_bridge.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record RepRS2BridgeData() {
    public static final StreamCodec<RegistryFriendlyByteBuf, RepRS2BridgeData> STREAM_CODEC = 
        StreamCodec.unit(new RepRS2BridgeData());
}
