package com.leclowndu93150.replication_rs2_bridge.client;

import com.leclowndu93150.replication_rs2_bridge.ReplicationRSBridge;
import com.leclowndu93150.replication_rs2_bridge.client.model.UniversalMatterItemModel;
import com.leclowndu93150.replication_rs2_bridge.client.screen.RepRS2BridgeScreen;
import com.leclowndu93150.replication_rs2_bridge.menu.ModMenus;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

@EventBusSubscriber(modid = ReplicationRSBridge.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public class ModClientSetup {
    
    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ResourceLocation.fromNamespaceAndPath(ReplicationRSBridge.MODID, "universal_matter"), new UniversalMatterItemModel.Loader());
    }
    
    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(ModMenus.REP_RS2_BRIDGE.get(), RepRS2BridgeScreen::new);
    }
}
