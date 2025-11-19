package com.leclowndu93150.replication_rs2_bridge.client.screen;

import com.refinedmods.refinedstorage.common.support.amount.PriorityScreen;
import com.refinedmods.refinedstorage.common.support.containermenu.ClientProperty;
import com.refinedmods.refinedstorage.common.support.widget.AbstractSideButtonWidget;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import java.util.List;

import static com.refinedmods.refinedstorage.common.util.IdentifierUtil.createIdentifier;
import static com.refinedmods.refinedstorage.common.util.IdentifierUtil.createTranslation;

public class RepRS2BridgePrioritySideButtonWidget extends AbstractSideButtonWidget {
    
    private static final MutableComponent TITLE = createTranslation("gui", "priority");
    private static final Component HELP = Component.translatable(
        "block.replication_rs2_bridge.rep_rs2_bridge.priority.help"
    );
    private static final ResourceLocation SPRITE = createIdentifier("widget/side_button/priority");
    
    private final ClientProperty<Integer> property;
    
    public RepRS2BridgePrioritySideButtonWidget(ClientProperty<Integer> property,
                                                Inventory playerInventory,
                                                Screen parent) {
        super(createPressAction(property, playerInventory, parent));
        this.property = property;
    }
    
    private static OnPress createPressAction(ClientProperty<Integer> property,
                                            Inventory playerInventory,
                                            Screen parent) {
        return btn -> Minecraft.getInstance().setScreen(
            new PriorityScreen(TITLE, property.get(), property::setValue, parent, playerInventory)
        );
    }
    
    @Override
    protected ResourceLocation getSprite() {
        return SPRITE;
    }
    
    @Override
    protected MutableComponent getTitle() {
        return TITLE;
    }
    
    @Override
    protected List<MutableComponent> getSubText() {
        return List.of(Component.literal(String.valueOf(property.getValue())).withStyle(ChatFormatting.GRAY));
    }
    
    @Override
    protected Component getHelpText() {
        return HELP;
    }
}
