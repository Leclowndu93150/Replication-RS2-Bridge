package com.leclowndu93150.replication_rs2_bridge.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record MatterComponent(String matterTypeName, ResourceLocation texture) {
    public static final Codec<MatterComponent> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Codec.STRING.fieldOf("matter_type").forGetter(MatterComponent::matterTypeName),
                    ResourceLocation.CODEC.fieldOf("texture").forGetter(MatterComponent::texture)
            ).apply(instance, MatterComponent::new)
    );

    public static final StreamCodec<RegistryFriendlyByteBuf, MatterComponent> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            MatterComponent::matterTypeName,
            ResourceLocation.STREAM_CODEC,
            MatterComponent::texture,
            MatterComponent::new
    );
}
