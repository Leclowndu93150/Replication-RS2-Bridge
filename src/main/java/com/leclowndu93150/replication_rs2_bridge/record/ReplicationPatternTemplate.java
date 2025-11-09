package com.leclowndu93150.replication_rs2_bridge.record;

import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.pattern.MatterPattern;

import java.util.Map;

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

