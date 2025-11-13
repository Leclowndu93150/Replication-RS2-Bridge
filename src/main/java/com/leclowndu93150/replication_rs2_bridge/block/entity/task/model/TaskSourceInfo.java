package com.leclowndu93150.replication_rs2_bridge.block.entity.task.model;

import java.util.UUID;

import com.refinedmods.refinedstorage.api.autocrafting.task.TaskId;

import net.minecraft.world.item.ItemStack;

/**
 * Captures the metadata needed to map RS2 tasks back to Replication tasks.
 */
public final class TaskSourceInfo {
    private final ItemStack itemStack;
    private final UUID sourceId;
    private final TaskId rs2TaskId;

    public TaskSourceInfo(final ItemStack itemStack, final UUID sourceId) {
        this(itemStack, sourceId, null);
    }

    public TaskSourceInfo(final ItemStack itemStack, final UUID sourceId, final TaskId rs2TaskId) {
        this.itemStack = itemStack.copy();
        this.sourceId = sourceId;
        this.rs2TaskId = rs2TaskId;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public TaskId getRs2TaskId() {
        return rs2TaskId;
    }
}
