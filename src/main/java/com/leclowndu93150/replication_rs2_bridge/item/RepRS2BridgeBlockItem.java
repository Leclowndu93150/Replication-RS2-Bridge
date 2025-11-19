package com.leclowndu93150.replication_rs2_bridge.item;

import com.refinedmods.refinedstorage.common.api.support.HelpTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;

import java.util.Optional;

public class RepRS2BridgeBlockItem extends BlockItem {
    
    private static final Component HELP_TEXT = Component.translatable("block.replication_rs2_bridge.rep_rs2_bridge.tooltip");
    
    public RepRS2BridgeBlockItem(Block block, Properties properties) {
        super(block, properties);
    }
    
    @Override
    public Optional<TooltipComponent> getTooltipImage(ItemStack stack) {
        return Optional.of(new HelpTooltipComponent(HELP_TEXT));
    }
}
