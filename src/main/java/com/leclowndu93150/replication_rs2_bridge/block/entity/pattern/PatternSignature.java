package com.leclowndu93150.replication_rs2_bridge.block.entity.pattern;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.buuz135.replication.api.IMatterType;
import com.leclowndu93150.replication_rs2_bridge.component.MatterComponent;
import com.leclowndu93150.replication_rs2_bridge.component.ModDataComponents;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

public record PatternSignature(ResourceLocation outputItemId,
                               int outputCount,
                               MatterComponent matterComponent,
                               List<MatterCost> costs) {
    private static final String TAG_ITEM = "Item";
    private static final String TAG_COUNT = "Count";
    private static final String TAG_MATTER = "Matter";
    private static final String TAG_COSTS = "Costs";

    public static PatternSignature from(final ItemStack stack, final Map<IMatterType, Long> matterCost) {
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        final MatterComponent matterComponent = stack.get(ModDataComponents.MATTER.get());
        final List<MatterCost> entries = matterCost.entrySet()
            .stream()
            .filter(entry -> entry.getKey() != null && entry.getValue() > 0)
            .map(entry -> new MatterCost(entry.getKey().getName(), entry.getValue()))
            .sorted(Comparator.comparing(MatterCost::matterName))
            .toList();
        return new PatternSignature(id, stack.getCount(), matterComponent, entries);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString(TAG_ITEM, outputItemId.toString());
        tag.putInt(TAG_COUNT, outputCount);
        if (matterComponent != null) {
            MatterComponent.CODEC.encodeStart(NbtOps.INSTANCE, matterComponent)
                .result()
                .ifPresent(value -> tag.put(TAG_MATTER, value));
        }
        ListTag costList = new ListTag();
        costs.forEach(cost -> costList.add(cost.save()));
        tag.put(TAG_COSTS, costList);
        return tag;
    }

    public static PatternSignature load(final CompoundTag tag) {
        final ResourceLocation itemId = ResourceLocation.parse(tag.getString(TAG_ITEM));
        final int count = tag.getInt(TAG_COUNT);
        MatterComponent matterComponent = null;
        if (tag.contains(TAG_MATTER)) {
            matterComponent = MatterComponent.CODEC.parse(NbtOps.INSTANCE, tag.get(TAG_MATTER))
                .result()
                .orElse(null);
        }
        final List<MatterCost> costs = new ArrayList<>();
        ListTag costList = tag.getList(TAG_COSTS, Tag.TAG_COMPOUND);
        for (Tag element : costList) {
            costs.add(MatterCost.load((CompoundTag) element));
        }
        return new PatternSignature(itemId, count, matterComponent, costs);
    }
}
