package com.leclowndu93150.replication_rs2_bridge.menu;

import com.leclowndu93150.replication_rs2_bridge.block.entity.RepRS2BridgeBlockEntity;
import com.refinedmods.refinedstorage.common.support.AbstractBaseContainerMenu;
import com.refinedmods.refinedstorage.common.support.containermenu.ClientProperty;
import com.refinedmods.refinedstorage.common.support.containermenu.ServerProperty;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import javax.annotation.Nullable;

public class RepRS2BridgeMenu extends AbstractBaseContainerMenu {
    
    @Nullable
    private final RepRS2BridgeBlockEntity blockEntity;
    
    public RepRS2BridgeMenu(int syncId, Inventory playerInventory, RegistryFriendlyByteBuf buf) {
        super(ModMenus.REP_RS2_BRIDGE.get(), syncId);
        this.blockEntity = null;
        
        registerProperty(new ClientProperty<>(RepRS2BridgePropertyTypes.PRIORITY, 0));
        
        addPlayerInventory(playerInventory, 8, 84);
    }
    
    public RepRS2BridgeMenu(int syncId, Inventory playerInventory, RepRS2BridgeBlockEntity blockEntity) {
        super(ModMenus.REP_RS2_BRIDGE.get(), syncId);
        this.blockEntity = blockEntity;
        
        registerProperty(new ServerProperty<>(
            RepRS2BridgePropertyTypes.PRIORITY,
            blockEntity::getPriority,
            blockEntity::setPriority
        ));
        
        addPlayerInventory(playerInventory, 8, 84);
    }

    @Override
    public boolean stillValid(Player player) {
        if (blockEntity == null) {
            return true;
        }
        return Container.stillValidBlockEntity(blockEntity, player);
    }
}
