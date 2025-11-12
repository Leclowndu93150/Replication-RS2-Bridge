package com.leclowndu93150.replication_rs2_bridge.item;

import com.buuz135.replication.api.IMatterType;
import com.leclowndu93150.replication_rs2_bridge.component.MatterComponent;
import com.leclowndu93150.replication_rs2_bridge.component.ModDataComponents;
import com.leclowndu93150.replication_rs2_bridge.util.MatterTypeUtil;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

public class UniversalMatterItem extends Item {
    public UniversalMatterItem(Properties properties) {
        super(properties);
    }
    
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (entity instanceof Player && !level.isClientSide()) {
            stack.setCount(0);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        MatterComponent component = stack.get(ModDataComponents.MATTER.get());
        if (component != null) {
            String matterName = component.matterTypeName();
            String capitalizedName = matterName.substring(0, 1).toUpperCase() + matterName.substring(1);
            return Component.literal(capitalizedName + " Matter");
        }
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        MatterComponent component = stack.get(ModDataComponents.MATTER.get());
        if (component != null) {
            var info = MatterTypeUtil.getMatterInfo(component.matterTypeName());
            if (info != null && !info.registryId().getNamespace().equals("replication")) {
                tooltipComponents.add(Component.literal("From: " + info.registryId().getNamespace())
                        .withStyle(ChatFormatting.GRAY));
            }
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }

    public static ItemStack createMatterStack(IMatterType matterType, int count) {
        var info = MatterTypeUtil.getMatterInfo(matterType);
        if (info == null) {
            return ItemStack.EMPTY;
        }
        
        ItemStack stack = new ItemStack(ModItems.UNIVERSAL_MATTER.get(), count);
        MatterComponent component = new MatterComponent(
                info.name(),
                info.texture(),
                info.color()
        );
        stack.set(ModDataComponents.MATTER.get(), component);
        return stack;
    }
}
