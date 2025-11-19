package com.leclowndu93150.replication_rs2_bridge.util;

import com.buuz135.replication.api.IMatterType;
import com.leclowndu93150.replication_rs2_bridge.component.MatterComponent;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record MatterTypeInfo(String name,
                             ResourceLocation texture,
                             float[] color,
                             ResourceLocation registryId,
                             IMatterType matterType,
                             MatterComponent canonicalComponent) {

    public static final StreamCodec<ByteBuf, MatterTypeInfo> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8,
        MatterTypeInfo::name,
        ResourceLocation.STREAM_CODEC,
        MatterTypeInfo::texture,
        ResourceLocation.STREAM_CODEC,
        MatterTypeInfo::registryId,
        (name, texture, registryId) -> {
            MatterComponent component = new MatterComponent(name, texture);
            return new MatterTypeInfo(name, texture, null, registryId, null, component);
        }
    );
}
