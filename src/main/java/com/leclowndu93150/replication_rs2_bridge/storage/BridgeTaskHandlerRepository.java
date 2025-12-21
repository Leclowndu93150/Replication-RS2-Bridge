package com.leclowndu93150.replication_rs2_bridge.storage;

import com.leclowndu93150.replication_rs2_bridge.block.entity.task.model.ItemWithSourceId;
import com.leclowndu93150.replication_rs2_bridge.block.entity.task.model.TaskSourceInfo;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskId;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SavedData repository for storing task handler data (active tasks, pattern requests, etc.)
 * This data was previously stored in block entity NBT but is now centralized here for better management.
 */
public class BridgeTaskHandlerRepository extends SavedData {
    public static final String NAME = "replication_rs2_bridge_task_handler";

    private static final String TAG_BRIDGES = "Bridges";
    private static final String TAG_BRIDGE_ID = "BridgeId";
    private static final String TAG_REQUEST_COUNTERS = "RequestCounters";
    private static final String TAG_PATTERN_REQUESTS = "PatternRequests";
    private static final String TAG_PATTERN_REQUESTS_BY_SOURCE = "PatternRequestsBySource";
    private static final String TAG_ACTIVE_TASKS = "ActiveTasks";

    private final Map<UUID, BridgeTaskData> bridgeData = new HashMap<>();
    private HolderLookup.Provider cachedProvider;

    public BridgeTaskHandlerRepository() {
    }

    public BridgeTaskHandlerRepository(CompoundTag tag, HolderLookup.Provider provider) {
        this.cachedProvider = provider;
        if (tag.contains(TAG_BRIDGES, Tag.TAG_LIST)) {
            ListTag bridgesList = tag.getList(TAG_BRIDGES, Tag.TAG_COMPOUND);
            for (Tag bridgeTag : bridgesList) {
                CompoundTag bridgeCompound = (CompoundTag) bridgeTag;
                UUID bridgeId = bridgeCompound.getUUID(TAG_BRIDGE_ID);
                BridgeTaskData data = new BridgeTaskData();

                if (bridgeCompound.contains(TAG_REQUEST_COUNTERS, Tag.TAG_LIST)) {
                    data.requestCounters = readItemWithSourceList(
                        bridgeCompound.getList(TAG_REQUEST_COUNTERS, Tag.TAG_COMPOUND), provider, bridgeId);
                }
                if (bridgeCompound.contains(TAG_PATTERN_REQUESTS, Tag.TAG_LIST)) {
                    data.patternRequests = readItemCountList(
                        bridgeCompound.getList(TAG_PATTERN_REQUESTS, Tag.TAG_COMPOUND), provider);
                }
                if (bridgeCompound.contains(TAG_PATTERN_REQUESTS_BY_SOURCE, Tag.TAG_LIST)) {
                    data.patternRequestsBySource = readItemCountList(
                        bridgeCompound.getList(TAG_PATTERN_REQUESTS_BY_SOURCE, Tag.TAG_COMPOUND), provider);
                }
                if (bridgeCompound.contains(TAG_ACTIVE_TASKS, Tag.TAG_LIST)) {
                    data.activeTasks = readActiveTaskList(
                        bridgeCompound.getList(TAG_ACTIVE_TASKS, Tag.TAG_COMPOUND), provider, bridgeId);
                }

                if (!data.isEmpty()) {
                    bridgeData.put(bridgeId, data);
                }
            }
        }
    }

    public void setCachedProvider(HolderLookup.Provider provider) {
        this.cachedProvider = provider;
    }

    public BridgeTaskData getDataForBridge(UUID bridgeId) {
        return bridgeData.computeIfAbsent(bridgeId, id -> new BridgeTaskData());
    }

    public void setDataForBridge(UUID bridgeId, BridgeTaskData data) {
        if (data == null || data.isEmpty()) {
            bridgeData.remove(bridgeId);
        } else {
            bridgeData.put(bridgeId, data);
        }
        setDirty();
    }

    public void removeBridge(UUID bridgeId) {
        if (bridgeData.remove(bridgeId) != null) {
            setDirty();
        }
    }

    public void clearBridgeData(UUID bridgeId) {
        BridgeTaskData data = bridgeData.get(bridgeId);
        if (data != null) {
            data.clear();
            setDirty();
        }
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag bridgesList = new ListTag();

        for (Map.Entry<UUID, BridgeTaskData> entry : bridgeData.entrySet()) {
            BridgeTaskData data = entry.getValue();
            if (data.isEmpty()) {
                continue;
            }

            CompoundTag bridgeCompound = new CompoundTag();
            bridgeCompound.putUUID(TAG_BRIDGE_ID, entry.getKey());

            if (!data.requestCounters.isEmpty()) {
                bridgeCompound.put(TAG_REQUEST_COUNTERS, writeItemWithSourceList(data.requestCounters, provider));
            }
            if (!data.patternRequests.isEmpty()) {
                bridgeCompound.put(TAG_PATTERN_REQUESTS, writeItemCountList(data.patternRequests, provider));
            }
            if (!data.patternRequestsBySource.isEmpty()) {
                bridgeCompound.put(TAG_PATTERN_REQUESTS_BY_SOURCE, writeItemCountList(data.patternRequestsBySource, provider));
            }
            if (!data.activeTasks.isEmpty()) {
                bridgeCompound.put(TAG_ACTIVE_TASKS, writeActiveTaskList(data.activeTasks, provider));
            }

            bridgesList.add(bridgeCompound);
        }

        tag.put(TAG_BRIDGES, bridgesList);
        return tag;
    }

    private ListTag writeItemWithSourceList(Map<ItemWithSourceId, Integer> map, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        map.forEach((key, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("Stack", key.getItemStack().saveOptional(provider));
            entry.putInt("Count", amount);
            entry.putUUID("Owner", key.getSourceId());
            list.add(entry);
        });
        return list;
    }

    private Map<ItemWithSourceId, Integer> readItemWithSourceList(ListTag list, HolderLookup.Provider provider, UUID defaultOwner) {
        Map<ItemWithSourceId, Integer> map = new HashMap<>();
        for (Tag element : list) {
            CompoundTag entry = (CompoundTag) element;
            ItemStack stack = ItemStack.parse(provider, entry.getCompound("Stack")).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }
            UUID ownerId = entry.contains("Owner") ? entry.getUUID("Owner") : defaultOwner;
            int amount = entry.getInt("Count");
            map.put(new ItemWithSourceId(stack, ownerId), amount);
        }
        return map;
    }

    private ListTag writeItemCountList(Map<ItemStack, Integer> map, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        map.forEach((stack, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("Stack", stack.saveOptional(provider));
            entry.putInt("Count", amount);
            list.add(entry);
        });
        return list;
    }

    private Map<ItemStack, Integer> readItemCountList(ListTag list, HolderLookup.Provider provider) {
        Map<ItemStack, Integer> map = new HashMap<>();
        for (Tag element : list) {
            CompoundTag entry = (CompoundTag) element;
            ItemStack stack = ItemStack.parse(provider, entry.getCompound("Stack")).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }
            map.put(stack, entry.getInt("Count"));
        }
        return map;
    }

    private ListTag writeActiveTaskList(Map<String, TaskSourceInfo> tasks, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        tasks.forEach((taskId, info) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("TaskId", taskId);
            entry.put("Stack", info.getItemStack().saveOptional(provider));
            if (info.getRs2TaskId() != null) {
                entry.putUUID("Rs2TaskId", info.getRs2TaskId().id());
            }
            list.add(entry);
        });
        return list;
    }

    private Map<String, TaskSourceInfo> readActiveTaskList(ListTag list, HolderLookup.Provider provider, UUID blockId) {
        Map<String, TaskSourceInfo> map = new HashMap<>();
        for (Tag element : list) {
            CompoundTag entry = (CompoundTag) element;
            ItemStack stack = ItemStack.parse(provider, entry.getCompound("Stack")).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }
            String taskId = entry.getString("TaskId");
            TaskId rs2TaskId = null;
            if (entry.contains("Rs2TaskId")) {
                rs2TaskId = new TaskId(entry.getUUID("Rs2TaskId"));
            }
            map.put(taskId, new TaskSourceInfo(stack, blockId, rs2TaskId));
        }
        return map;
    }

    /**
     * Container class for all task-related data for a single bridge
     */
    public static class BridgeTaskData {
        public Map<ItemWithSourceId, Integer> requestCounters = new HashMap<>();
        public Map<ItemStack, Integer> patternRequests = new HashMap<>();
        public Map<ItemStack, Integer> patternRequestsBySource = new HashMap<>();
        public Map<String, TaskSourceInfo> activeTasks = new HashMap<>();

        public boolean isEmpty() {
            return requestCounters.isEmpty()
                && patternRequests.isEmpty()
                && patternRequestsBySource.isEmpty()
                && activeTasks.isEmpty();
        }

        public void clear() {
            requestCounters.clear();
            patternRequests.clear();
            patternRequestsBySource.clear();
            activeTasks.clear();
        }
    }
}
