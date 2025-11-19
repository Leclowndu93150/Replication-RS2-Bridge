package com.leclowndu93150.replication_rs2_bridge.util;

import com.leclowndu93150.replication_rs2_bridge.component.MatterComponent;
import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class MatterComponentRegistry extends SavedData {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String DATA_NAME = "replication_rs2_bridge_matter_components";
    
    private final Map<String, MatterComponent> canonicalComponents = new HashMap<>();
    
    public MatterComponentRegistry() {
    }
    
    public static MatterComponentRegistry load(CompoundTag tag, HolderLookup.Provider provider) {
        MatterComponentRegistry registry = new MatterComponentRegistry();
        
        ListTag list = tag.getList("components", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag componentTag = list.getCompound(i);
            String matterTypeName = componentTag.getString("matterTypeName");
            ResourceLocation texture = ResourceLocation.parse(componentTag.getString("texture"));
            
            MatterComponent component = new MatterComponent(matterTypeName, texture);
            registry.canonicalComponents.put(matterTypeName, component);
        }
        
        LOGGER.info("RepRS2Bridge: Loaded {} canonical matter components from disk", registry.canonicalComponents.size());
        return registry;
    }
    
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        
        for (Map.Entry<String, MatterComponent> entry : canonicalComponents.entrySet()) {
            CompoundTag componentTag = new CompoundTag();
            MatterComponent component = entry.getValue();
            
            componentTag.putString("matterTypeName", component.matterTypeName());
            componentTag.putString("texture", component.texture().toString());
            
            list.add(componentTag);
        }
        
        tag.put("components", list);
        return tag;
    }
    
    public MatterComponent getOrCreate(String matterTypeName, ResourceLocation texture) {
        return canonicalComponents.computeIfAbsent(matterTypeName, k -> {
            LOGGER.info("RepRS2Bridge: Registering new canonical matter component: {}", matterTypeName);
            setDirty();
            return new MatterComponent(matterTypeName, texture);
        });
    }
    
    public MatterComponent get(String matterTypeName) {
        return canonicalComponents.get(matterTypeName);
    }
    
    public static MatterComponentRegistry get(MinecraftServer server) {
        DimensionDataStorage storage = server.overworld().getDataStorage();
        return storage.computeIfAbsent(
            new Factory<>(MatterComponentRegistry::new, MatterComponentRegistry::load),
            DATA_NAME
        );
    }
}
