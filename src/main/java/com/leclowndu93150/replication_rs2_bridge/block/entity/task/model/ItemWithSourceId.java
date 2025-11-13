package com.leclowndu93150.replication_rs2_bridge.block.entity.task.model;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.world.item.ItemStack;

/**
 * Value object that ties an {@link ItemStack} to the bridge block that requested it.
 */
public final class ItemWithSourceId {
    private final ItemStack itemStack;
    private final UUID sourceId;

    public ItemWithSourceId(final ItemStack itemStack, final UUID sourceId) {
        this.itemStack = itemStack.copy();
        this.sourceId = sourceId;
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ItemWithSourceId that = (ItemWithSourceId) o;
        return ItemStack.matches(itemStack, that.itemStack) && Objects.equals(sourceId, that.sourceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemStack.getItem(), sourceId);
    }
}
