package com.leclowndu93150.replication_rs2_bridge;

import com.buuz135.replication.block.MatterPipeBlock;
import com.buuz135.replication.network.DefaultMatterNetworkElement;
import com.hrznstudio.titanium.block_network.element.NetworkElementRegistry;
import com.leclowndu93150.replication_rs2_bridge.block.ModBlocks;
import com.leclowndu93150.replication_rs2_bridge.block.entity.ModBlockEntities;
import com.leclowndu93150.replication_rs2_bridge.block.entity.RepRS2BridgeBlockEntity;
import com.leclowndu93150.replication_rs2_bridge.component.ModDataComponents;
import com.leclowndu93150.replication_rs2_bridge.util.MatterTypeUtil;
import com.leclowndu93150.replication_rs2_bridge.item.ModItems;
import com.mojang.logging.LogUtils;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;

@Mod(ReplicationRSBridge.MODID)
public class ReplicationRSBridge {
    public static final String MODID = "replication_rs2_bridge";
    private static final Logger LOGGER = LogUtils.getLogger();

    public ReplicationRSBridge(IEventBus modEventBus, ModContainer modContainer) {
        modEventBus.addListener(this::commonSetup);
        
        NeoForge.EVENT_BUS.register(this);
        
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        ModDataComponents.register(modEventBus);
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        com.leclowndu93150.replication_rs2_bridge.item.ModCreativeTabs.register(modEventBus);

        modEventBus.addListener(this::registerCapabilities);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            if (!ModList.get().isLoaded("replication") || !ModList.get().isLoaded("refinedstorage")) {
                NetworkElementRegistry.INSTANCE.addFactory(DefaultMatterNetworkElement.ID, new DefaultMatterNetworkElement.Factory());
            }
        });

        event.enqueueWork(this::registerWithReplicationMod);
    }


    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        RepRS2BridgeBlockEntity.setWorldUnloading(false);
        MatterTypeUtil.loadAllMatters();
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        RepRS2BridgeBlockEntity.setWorldUnloading(true);
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
        }
    }

    private void registerWithReplicationMod() {
        MatterPipeBlock.ALLOWED_CONNECTION_BLOCKS.add(block ->
            block.getClass().getName().contains(MODID)
        );
    }

    private void registerCapabilities(final RegisterCapabilitiesEvent event) {
        RepRS2BridgeCapabilities.register(event);
    }
}
