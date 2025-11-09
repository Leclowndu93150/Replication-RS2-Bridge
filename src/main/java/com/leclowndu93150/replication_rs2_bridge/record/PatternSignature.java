package com.leclowndu93150.replication_rs2_bridge.record;

import com.buuz135.replication.api.IMatterType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record PatternSignature(ResourceLocation outputItemId,
                               int outputCount,
                               List<MatterCost> costs) {
    public static PatternSignature from(final ItemStack stack, final Map<IMatterType, Long> matterCost) {
        final ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        final List<MatterCost> entries = matterCost.entrySet()
            .stream()
            .filter(entry -> entry.getKey() != null && entry.getValue() > 0)
            .map(entry -> new MatterCost(entry.getKey().getName(), entry.getValue()))
            .sorted(Comparator.comparing(MatterCost::matterName))
            .toList();
        return new PatternSignature(id, stack.getCount(), entries);
    }
}
