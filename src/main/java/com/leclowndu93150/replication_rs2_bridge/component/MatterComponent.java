package com.leclowndu93150.replication_rs2_bridge.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public record MatterComponent(String matterTypeName, ResourceLocation texture, float[] color) {
    public static final Codec<MatterComponent> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("matter_type").forGetter(MatterComponent::matterTypeName),
                    ResourceLocation.CODEC.fieldOf("texture").forGetter(MatterComponent::texture),
                    Codec.FLOAT.listOf().xmap(
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
                    ).fieldOf("color").forGetter(MatterComponent::color)
            ).apply(instance, MatterComponent::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MatterComponent> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            MatterComponent::matterTypeName,
            ResourceLocation.STREAM_CODEC,
            MatterComponent::texture,
            ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list()).map(
                    list -> {
                        float[] arr = new float[list.size()];
                        for (int i = 0; i < list.size(); i++) {
                            arr[i] = list.get(i);
                        }
                        return arr;
                    },
                    arr -> {
                        java.util.List<Float> list = new java.util.ArrayList<>();
                        for (float v : arr) {
                            list.add(v);
                        }
                        return list;
                    }
            ),
            MatterComponent::color,
            MatterComponent::new
    );

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MatterComponent other)) {
            return false;
        }
        return Objects.equals(matterTypeName, other.matterTypeName)
                && Objects.equals(texture, other.texture)
                && Arrays.equals(color, other.color);
    }

    @Override
    public int hashCode() {
        int result = Objects.hash(matterTypeName, texture);
        result = 31 * result + Arrays.hashCode(color);
        return result;
    }
}
