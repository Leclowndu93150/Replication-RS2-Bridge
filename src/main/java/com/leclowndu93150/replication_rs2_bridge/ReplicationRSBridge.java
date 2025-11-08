package com.leclowndu93150.replication_rs2_bridge;

import com.buuz135.replication.block.MatterPipeBlock;
import com.buuz135.replication.network.DefaultMatterNetworkElement;
import com.hrznstudio.titanium.block_network.element.NetworkElementRegistry;
import com.leclowndu93150.replication_rs2_bridge.block.ModBlocks;
import com.leclowndu93150.replication_rs2_bridge.block.entity.ModBlockEntities;
import com.leclowndu93150.replication_rs2_bridge.block.entity.RepRS2BridgeBlockEntity;
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
        
        modEventBus.addListener(this::addCreative);
        
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        LOGGER.info("RepRS2Bridge: Config registered");

        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);

        modEventBus.addListener(this::registerCapabilities);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            boolean replicationLoaded = ModList.get().isLoaded("replication");
            boolean rs2Loaded = ModList.get().isLoaded("refinedstorage");
            
            if (replicationLoaded && rs2Loaded) {
                LOGGER.info("Replication and RS2 mods loaded");
            } else {
                try {
                    LOGGER.info("Registering DefaultMatterNetworkElement factory");
                    NetworkElementRegistry.INSTANCE.addFactory(DefaultMatterNetworkElement.ID, new DefaultMatterNetworkElement.Factory());
                    LOGGER.info("Replication network integration complete");
                } catch (Exception e) {
                    if (e.getMessage() != null && e.getMessage().contains("duplicate")) {
                        LOGGER.info("DefaultMatterNetworkElement factory already registered");
                    } else {
                        LOGGER.error("Failed to register with Replication network system", e);
                    }
                }
            }
        });

        event.enqueueWork(this::registerWithReplicationMod);
        
        LOGGER.info("RepRS2Bridge: Bridge energy consumption set to {} FE/t", Config.bridgeEnergyConsumption);
    }

    private void addCreative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(ModBlocks.REP_RS2_BRIDGE.get());
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        LOGGER.info("RepRS2Bridge: Server starting");
        RepRS2BridgeBlockEntity.setWorldUnloading(false);
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        LOGGER.info("RepRS2Bridge: Server stopping");
        RepRS2BridgeBlockEntity.setWorldUnloading(true);
    }

    @EventBusSubscriber(modid = MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
        }
    }

    private void registerWithReplicationMod() {
        try {
            MatterPipeBlock.ALLOWED_CONNECTION_BLOCKS.add(block ->
                block.getClass().getName().contains(MODID)
            );
            LOGGER.debug("Successfully registered mod namespace with Replication");
        } catch (Exception e) {
            LOGGER.debug("Replication mod not fully initialized yet");
        }
    }

    private void registerCapabilities(final RegisterCapabilitiesEvent event) {
        RepRS2BridgeCapabilities.register(event);
    }
}
