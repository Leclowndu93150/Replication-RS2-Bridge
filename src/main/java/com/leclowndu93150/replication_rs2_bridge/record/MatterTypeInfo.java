package com.leclowndu93150.replication_rs2_bridge.record;

import com.buuz135.replication.api.IMatterType;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public record MatterTypeInfo(String name, ResourceLocation texture, float[] color, ResourceLocation registryId, IMatterType matterType) {
    
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
            (name, texture, color, registryId) -> new MatterTypeInfo(name, texture, color, registryId, null)
    );
}