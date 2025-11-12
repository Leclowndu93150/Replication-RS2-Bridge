package com.leclowndu93150.replication_rs2_bridge.util;

import com.buuz135.replication.ReplicationRegistry;
import com.buuz135.replication.api.IMatterType;
import com.leclowndu93150.replication_rs2_bridge.component.MatterComponent;
import com.leclowndu93150.replication_rs2_bridge.record.MatterTypeInfo;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

public class MatterTypeUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, MatterTypeInfo> MATTER_CACHE = new HashMap<>();
    private static final Map<IMatterType, MatterTypeInfo> MATTER_BY_TYPE = new HashMap<>();

    public static void loadAllMatters() {
        MATTER_CACHE.clear();
        MATTER_BY_TYPE.clear();

        try {
            Registry<IMatterType> registry = ReplicationRegistry.MATTER_TYPES_REGISTRY;
            if (registry == null) {
                LOGGER.warn("RepRS2Bridge: Matter type registry not ready, skipping texture registration for now.");
                return;
            }
            
            for (var entry : registry.entrySet()) {
                ResourceLocation id = entry.getKey().location();
                IMatterType matterType = entry.getValue();
                
                String name = matterType.getName();
                float[] color = matterType.getColor().get();
                ResourceLocation texture = resolveTexture(id, name);
                
                MatterTypeInfo info = new MatterTypeInfo(name, texture, color, id, matterType);
                MATTER_CACHE.put(name, info);
                MATTER_BY_TYPE.put(matterType, info);
                
                if (!id.getNamespace().equals("replication")) {
                    LOGGER.info("RepRS2Bridge: Loaded custom matter '{}' from mod '{}' with texture '{}'", 
                            name, id.getNamespace(), texture);
                }
            }
            
            LOGGER.info("RepRS2Bridge: Loaded {} matter types.", MATTER_CACHE.size());
        } catch (Exception e) {
            LOGGER.error("RepRS2Bridge: Failed to load matter types", e);
        }
    }

    private static ResourceLocation resolveTexture(ResourceLocation id, String name) {
        return ResourceLocation.fromNamespaceAndPath(
                id.getNamespace(),
                "gui/mattertypes/" + name.toLowerCase()
        );
    }

    public static Map<String, MatterTypeInfo> getAllMatters() {
        if (MATTER_CACHE.isEmpty()) {
            loadAllMatters();
        }
        return new HashMap<>(MATTER_CACHE);
    }

    public static MatterTypeInfo getMatterInfo(String matterTypeName) {
        if (MATTER_CACHE.isEmpty()) {
            loadAllMatters();
        }
        return MATTER_CACHE.get(matterTypeName);
    }

    public static MatterTypeInfo getMatterInfo(IMatterType matterType) {
        if (MATTER_CACHE.isEmpty()) {
            loadAllMatters();
        }
        return MATTER_BY_TYPE.get(matterType);
    }

    public static IMatterType getMatterTypeFromComponent(MatterComponent component) {
        if (component == null) return null;
        
        if (MATTER_CACHE.isEmpty()) {
            loadAllMatters();
        }
        
        MatterTypeInfo info = MATTER_CACHE.get(component.matterTypeName());
        return info != null ? info.matterType() : null;
    }
}
