package com.leclowndu93150.replication_rs2_bridge.menu;

import com.leclowndu93150.replication_rs2_bridge.ReplicationRSBridge;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModMenus {
    
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(
        Registries.MENU,
        ReplicationRSBridge.MODID
    );
    
    public static final DeferredHolder<MenuType<?>, MenuType<RepRS2BridgeMenu>> REP_RS2_BRIDGE = MENUS.register(
        "rep_rs2_bridge",
        () -> IMenuTypeExtension.create(RepRS2BridgeMenu::new)
    );
    
    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
