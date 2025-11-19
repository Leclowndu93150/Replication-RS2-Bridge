package com.leclowndu93150.replication_rs2_bridge.menu;

import com.leclowndu93150.replication_rs2_bridge.ReplicationRSBridge;
import com.refinedmods.refinedstorage.common.support.containermenu.PropertyType;
import com.refinedmods.refinedstorage.common.support.containermenu.PropertyTypes;
import net.minecraft.resources.ResourceLocation;

public final class RepRS2BridgePropertyTypes {
    
    public static final PropertyType<Integer> PRIORITY = PropertyTypes.createIntegerProperty(
        ResourceLocation.fromNamespaceAndPath(ReplicationRSBridge.MODID, "bridge_priority")
    );
    
    private RepRS2BridgePropertyTypes() {
    }
}
