package com.leclowndu93150.replication_rs2_bridge.util;

import java.util.ArrayList;
import java.util.List;

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
        ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()).map(
            list -> {
                float[] arr = new float[list.size()];
                for (int i = 0; i < list.size(); i++) {
                    arr[i] = list.get(i);
                }
                return arr;
            },
            arr -> {
                List<Float> list = new ArrayList<>();
                for (float v : arr) {
                    list.add(v);
                }
                return list;
            }
        ),
        MatterTypeInfo::color,
        ResourceLocation.STREAM_CODEC,
        MatterTypeInfo::registryId,
        (name, texture, color, registryId) -> {
            MatterComponent component = new MatterComponent(name, texture, color);
            return new MatterTypeInfo(name, texture, color, registryId, null, component);
        }
    );
}
