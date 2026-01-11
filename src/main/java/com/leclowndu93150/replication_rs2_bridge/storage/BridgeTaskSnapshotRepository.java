package com.leclowndu93150.replication_rs2_bridge.storage;

import com.leclowndu93150.replication_rs2_bridge.block.entity.task.TaskSnapshotNbt;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskSnapshot;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class BridgeTaskSnapshotRepository extends SavedData {
    public static final String NAME = "replication_rs2_bridge_task_snapshots";
    private static final String TAG_BRIDGES = "Bridges";
    private static final String TAG_BRIDGE_ID = "BridgeId";
    private static final String TAG_SNAPSHOTS = "Snapshots";

    private final Map<UUID, List<TaskSnapshot>> bridgeSnapshots = new HashMap<>();

    public BridgeTaskSnapshotRepository() {
    }

    public BridgeTaskSnapshotRepository(CompoundTag tag, HolderLookup.Provider provider) {
        if (tag.contains(TAG_BRIDGES, Tag.TAG_LIST)) {
            ListTag bridgesList = tag.getList(TAG_BRIDGES, Tag.TAG_COMPOUND);
            for (Tag bridgeTag : bridgesList) {
                CompoundTag bridgeCompound = (CompoundTag) bridgeTag;
                UUID bridgeId = bridgeCompound.getUUID(TAG_BRIDGE_ID);
                List<TaskSnapshot> snapshots = new ArrayList<>();

                if (bridgeCompound.contains(TAG_SNAPSHOTS, Tag.TAG_LIST)) {
                    ListTag snapshotsList = bridgeCompound.getList(TAG_SNAPSHOTS, Tag.TAG_COMPOUND);
                    for (Tag snapshotTag : snapshotsList) {
                        try {
                            TaskSnapshot snapshot = TaskSnapshotNbt.decode((CompoundTag) snapshotTag);
                            snapshots.add(snapshot);
                        } catch (Exception e) {
                            // Skip invalid snapshots
                        }
                    }
                }
                if (!snapshots.isEmpty()) {
                    bridgeSnapshots.put(bridgeId, snapshots);
                }
            }
        }
    }

    public List<TaskSnapshot> getSnapshotsForBridge(UUID bridgeId) {
        return bridgeSnapshots.getOrDefault(bridgeId, List.of());
    }

    public void setSnapshotsForBridge(UUID bridgeId, List<TaskSnapshot> snapshots) {
        //RepRS2BridgeLogger.snapshotSave(bridgeId, snapshots.size());
        if (snapshots == null || snapshots.isEmpty()) {
            bridgeSnapshots.remove(bridgeId);
        } else {
            bridgeSnapshots.put(bridgeId, new ArrayList<>(snapshots));
        }
        setDirty();
    }

    public void removeBridge(UUID bridgeId) {
        if (bridgeSnapshots.remove(bridgeId) != null) {
            setDirty();
        }
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag bridgesList = new ListTag();

        for (Map.Entry<UUID, List<TaskSnapshot>> bridgeEntry : bridgeSnapshots.entrySet()) {
            if (bridgeEntry.getValue().isEmpty()) {
                //RepRS2BridgeLogger.snapshotSkipEmpty(bridgeEntry.getKey());
                continue;
            }

            CompoundTag bridgeCompound = new CompoundTag();
            bridgeCompound.putUUID(TAG_BRIDGE_ID, bridgeEntry.getKey());

            ListTag snapshotsList = new ListTag();
            for (TaskSnapshot snapshot : bridgeEntry.getValue()) {
                try {
                    snapshotsList.add(TaskSnapshotNbt.encode(snapshot));
                } catch (Exception e) {
                    //RepRS2BridgeLogger.snapshotEncodeFailed(bridgeEntry.getKey(), e);
                }
            }
            bridgeCompound.put(TAG_SNAPSHOTS, snapshotsList);
            bridgesList.add(bridgeCompound);
        }

        tag.put(TAG_BRIDGES, bridgesList);
        return tag;
    }
}
