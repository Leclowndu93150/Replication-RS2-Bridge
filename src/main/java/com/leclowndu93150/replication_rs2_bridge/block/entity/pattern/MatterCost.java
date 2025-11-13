package com.leclowndu93150.replication_rs2_bridge.block.entity.pattern;

import net.minecraft.nbt.CompoundTag;

public record MatterCost(String matterName, long amount) {
    private static final String TAG_NAME = "Matter";
    private static final String TAG_AMOUNT = "Amount";

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_NAME, matterName);
        tag.putLong(TAG_AMOUNT, amount);
        return tag;
    }

    public static MatterCost load(final CompoundTag tag) {
        final String name = tag.getString(TAG_NAME);
        final long amount = tag.getLong(TAG_AMOUNT);
        return new MatterCost(name, amount);
    }
}
