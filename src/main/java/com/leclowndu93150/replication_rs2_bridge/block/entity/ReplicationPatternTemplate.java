package com.leclowndu93150.replication_rs2_bridge.block.entity;

import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.pattern.MatterPattern;
import com.refinedmods.refinedstorage.api.autocrafting.Pattern;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Value objects used to mirror Replication patterns into Refined Storage's autocrafting system.
 */
public record ReplicationPatternTemplate(
    PatternSignature signature,
    ItemStack outputStack,
    MatterPattern replicationPattern,
    Map<IMatterType, Long> matterCost
) {
}

