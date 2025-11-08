package com.leclowndu93150.replication_rs2_bridge.item;

import net.minecraft.world.item.ItemStack;

public class QuantumMatterItem extends MatterItem {
    public QuantumMatterItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
