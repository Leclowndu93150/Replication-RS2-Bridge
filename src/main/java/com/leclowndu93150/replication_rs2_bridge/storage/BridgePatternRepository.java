package com.leclowndu93150.replication_rs2_bridge.storage;

import com.leclowndu93150.replication_rs2_bridge.block.entity.pattern.PatternSignature;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class BridgePatternRepository extends SavedData {
    public static final String NAME = "replication_rs2_bridge_patterns";
    private static final String TAG_BRIDGES = "Bridges";
    private static final String TAG_BRIDGE_ID = "BridgeId";
    private static final String TAG_PATTERNS = "Patterns";
    private static final String TAG_PATTERN_ID = "PatternId";

    private final Map<UUID, Map<PatternSignature, UUID>> bridgePatterns = new HashMap<>();

    public BridgePatternRepository() {
    }

    public BridgePatternRepository(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag.contains(TAG_BRIDGES, Tag.TAG_LIST)) {
            ListTag bridgesList = tag.getList(TAG_BRIDGES, Tag.TAG_COMPOUND);
            for (Tag bridgeTag : bridgesList) {
                CompoundTag bridgeCompound = (CompoundTag) bridgeTag;
                UUID bridgeId = bridgeCompound.getUUID(TAG_BRIDGE_ID);
                Map<PatternSignature, UUID> patterns = new HashMap<>();

                if (bridgeCompound.contains(TAG_PATTERNS, Tag.TAG_LIST)) {
                    ListTag patternsList = bridgeCompound.getList(TAG_PATTERNS, Tag.TAG_COMPOUND);
                    for (Tag patternTag : patternsList) {
                        CompoundTag patternCompound = (CompoundTag) patternTag;
                        PatternSignature signature = PatternSignature.load(patternCompound);
                        UUID patternId = patternCompound.getUUID(TAG_PATTERN_ID);
                        patterns.put(signature, patternId);
                    }
                }
                bridgePatterns.put(bridgeId, patterns);
            }
        }
    }

    public Map<PatternSignature, UUID> getPatternsForBridge(UUID blockId) {
        return bridgePatterns.computeIfAbsent(blockId, k -> new HashMap<>());
    }

    public void setPatternsForBridge(UUID blockId, Map<PatternSignature, UUID> patterns) {
        bridgePatterns.put(blockId, new HashMap<>(patterns));
        setDirty();
    }

    public void removeBridge(UUID blockId) {
        if (bridgePatterns.remove(blockId) != null) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag bridgesList = new ListTag();

        for (Map.Entry<UUID, Map<PatternSignature, UUID>> bridgeEntry : bridgePatterns.entrySet()) {
            if (bridgeEntry.getValue().isEmpty()) {
                continue;
            }

            CompoundTag bridgeCompound = getCompoundTag(bridgeEntry);
            bridgesList.add(bridgeCompound);
        }

        tag.put(TAG_BRIDGES, bridgesList);
        return tag;
    }

    private static @NotNull CompoundTag getCompoundTag(Map.Entry<UUID, Map<PatternSignature, UUID>> bridgeEntry) {
        CompoundTag bridgeCompound = new CompoundTag();
        bridgeCompound.putUUID(TAG_BRIDGE_ID, bridgeEntry.getKey());

        ListTag patternsList = new ListTag();
        for (Map.Entry<PatternSignature, UUID> patternEntry : bridgeEntry.getValue().entrySet()) {
            CompoundTag patternCompound = patternEntry.getKey().save();
            patternCompound.putUUID(TAG_PATTERN_ID, patternEntry.getValue());
            patternsList.add(patternCompound);
        }
        bridgeCompound.put(TAG_PATTERNS, patternsList);
        return bridgeCompound;
    }
}
