package com.leclowndu93150.replication_rs2_bridge.client;

import com.leclowndu93150.replication_rs2_bridge.ReplicationRSBridge;
import com.leclowndu93150.replication_rs2_bridge.client.model.UniversalMatterItemModel;
import net.minecraft.client.renderer.texture.atlas.SpriteResourceLoader;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ModelEvent;

@EventBusSubscriber(modid = ReplicationRSBridge.MODID, value = Dist.CLIENT)
public class ModClientSetup {
    
    @SubscribeEvent
    public static void registerGeometryLoaders(ModelEvent.RegisterGeometryLoaders event) {
        event.register(ResourceLocation.fromNamespaceAndPath(ReplicationRSBridge.MODID, "universal_matter"), new UniversalMatterItemModel.Loader());
    }
}
