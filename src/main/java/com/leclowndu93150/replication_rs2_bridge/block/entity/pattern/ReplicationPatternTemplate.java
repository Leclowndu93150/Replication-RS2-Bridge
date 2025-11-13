package com.leclowndu93150.replication_rs2_bridge.block.entity.pattern;

import java.util.Map;

import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.pattern.MatterPattern;

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
