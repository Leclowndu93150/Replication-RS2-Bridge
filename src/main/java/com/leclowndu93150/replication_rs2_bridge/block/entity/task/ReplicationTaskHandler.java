package com.leclowndu93150.replication_rs2_bridge.block.entity.task;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.pattern.MatterPattern;
import com.buuz135.replication.api.task.IReplicationTask;
import com.buuz135.replication.api.task.ReplicationTask;
import com.buuz135.replication.block.tile.ChipStorageBlockEntity;
import com.buuz135.replication.calculation.MatterValue;
import com.buuz135.replication.calculation.ReplicationCalculation;
import com.buuz135.replication.network.MatterNetwork;
import com.hrznstudio.titanium.block_network.element.NetworkElement;
import com.leclowndu93150.replication_rs2_bridge.block.entity.RepRS2BridgeBlockEntity;
import com.leclowndu93150.replication_rs2_bridge.block.entity.RepRS2BridgeNetworkNode;
import com.leclowndu93150.replication_rs2_bridge.block.entity.pattern.ReplicationPatternTemplate;
import com.leclowndu93150.replication_rs2_bridge.block.entity.task.model.ItemWithSourceId;
import com.leclowndu93150.replication_rs2_bridge.block.entity.task.model.TaskSourceInfo;
import com.leclowndu93150.replication_rs2_bridge.storage.BridgePatternStorage;
import com.leclowndu93150.replication_rs2_bridge.storage.BridgeTaskHandlerRepository;
import com.mojang.logging.LogUtils;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskId;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;

/**
 * Encapsulates the replication-task workflow so the block entity can stay lean.
 */
public final class ReplicationTaskHandler {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String TAG_LOCAL_REQUEST_COUNTERS = "LocalRequestCounters";
    private static final String TAG_LOCAL_PATTERN_REQUESTS = "LocalPatternRequests";
    private static final String TAG_LOCAL_PATTERN_REQUESTS_BY_SOURCE = "LocalPatternRequestsBySource";
    private static final String TAG_LOCAL_ACTIVE_TASKS = "LocalActiveTasks";
    private static final int REQUEST_ACCUMULATION_TICKS = 100;

    private final RepRS2BridgeBlockEntity owner;
    private final Map<UUID, Map<ItemWithSourceId, Integer>> requestCounters = new HashMap<>();
    private final Map<UUID, Map<ItemStack, Integer>> patternRequests = new HashMap<>();
    private final Map<UUID, Map<ItemStack, Integer>> patternRequestsBySource = new HashMap<>();
    private final Map<UUID, Map<String, TaskSourceInfo>> activeTasks = new HashMap<>();
    private final Map<String, Map<IMatterType, Long>> allocatedMatterByTask = new HashMap<>();

    private boolean needsTaskRescan;
    private int requestCounterTicks;

    public ReplicationTaskHandler(final RepRS2BridgeBlockEntity owner) {
        this.owner = owner;
    }

    public void queuePatternRequest(final ReplicationPatternTemplate template) {
        final UUID sourceId = owner.getBlockId();
        if (sourceId == null) {
            return;
        }
        final ItemStack output = template.outputStack().copy();

        final Map<ItemWithSourceId, Integer> counters = requestCounters.computeIfAbsent(sourceId, id -> new HashMap<>());
        final ItemWithSourceId key = new ItemWithSourceId(output, sourceId);
        counters.merge(key, output.getCount(), Integer::sum);

        final Map<ItemStack, Integer> sourceRequests = patternRequestsBySource.computeIfAbsent(sourceId, id -> new HashMap<>());
        sourceRequests.merge(output, 1, Integer::sum);

        final Map<ItemStack, Integer> globalRequests = patternRequests.computeIfAbsent(sourceId, id -> new HashMap<>());
        globalRequests.merge(output, 1, Integer::sum);

        requestCounterTicks = REQUEST_ACCUMULATION_TICKS;
    }

    public void tick(@Nullable final MatterNetwork replicationNetwork) {
        if (needsTaskRescan && replicationNetwork != null) {
            relinkActiveTasksFromNetwork(replicationNetwork);
            needsTaskRescan = false;
        }
        if (requestCounterTicks >= REQUEST_ACCUMULATION_TICKS) {
            try {
                processAccumulatedRequests(replicationNetwork);
                requestCounters.clear();
                requestCounterTicks = 0;
            } catch (Exception e) {
                LOGGER.error("Bridge: Exception in request processing: {}", e.getMessage());
            }
        } else {
            requestCounterTicks++;
        }
    }

    public void cancelReplicationTaskForRs2Task(final TaskId rs2TaskId) {
        final UUID blockId = owner.getBlockId();
        final ServerLevel serverLevel = owner.getLevel() instanceof ServerLevel server ? server : null;
        if (blockId == null || serverLevel == null) {
            return;
        }

        final Map<String, TaskSourceInfo> sourceTasks = activeTasks.get(blockId);
        if (sourceTasks == null || sourceTasks.isEmpty()) {
            return;
        }

        final List<String> replicationTaskIds = new ArrayList<>();
        for (Map.Entry<String, TaskSourceInfo> entry : sourceTasks.entrySet()) {
            final TaskSourceInfo info = entry.getValue();
            if (info.getRs2TaskId() != null && info.getRs2TaskId().equals(rs2TaskId)) {
                replicationTaskIds.add(entry.getKey());
            }
        }

        if (replicationTaskIds.isEmpty()) {
            return;
        }

        final MatterNetwork replicationNetwork = owner.getNetwork();
        if (replicationNetwork == null) {
            return;
        }

        for (String replicationTaskId : replicationTaskIds) {
            replicationNetwork.cancelTask(replicationTaskId, serverLevel);
            final TaskSourceInfo info = sourceTasks.remove(replicationTaskId);
            if (info != null && info.getItemStack() != null) {
                final Map<ItemStack, Integer> sourceRequests = patternRequestsBySource.get(blockId);
                if (sourceRequests != null) {
                    sourceRequests.remove(info.getItemStack());
                    if (sourceRequests.isEmpty()) {
                        patternRequestsBySource.remove(blockId);
                    }
                }
            }
            allocatedMatterByTask.remove(replicationTaskId);
        }

        if (sourceTasks.isEmpty()) {
            activeTasks.remove(blockId);
        }

        owner.getMatterStorage().refreshCache();
        final RepRS2BridgeNetworkNode networkNode = owner.getBridgeNetworkNode();
        if (networkNode != null) {
            networkNode.refreshStorageInNetwork();
        }
        owner.setChanged();
    }

    /**
     * Saves all task handler data to the SavedData repository.
     * This should be called periodically and on chunk unload/world save.
     */
    public void saveToRepository() {
        final var level = owner.getLevel();
        final UUID blockId = owner.getBlockId();
        if (level == null || level.isClientSide() || blockId == null) {
            return;
        }

        final BridgeTaskHandlerRepository repo = BridgePatternStorage.getTaskHandler(level);
        final BridgeTaskHandlerRepository.BridgeTaskData data = new BridgeTaskHandlerRepository.BridgeTaskData();

        final Map<ItemWithSourceId, Integer> localCounters = requestCounters.get(blockId);
        if (localCounters != null && !localCounters.isEmpty()) {
            data.requestCounters = new HashMap<>(localCounters);
        }

        final Map<ItemStack, Integer> localPatternRequests = patternRequests.get(blockId);
        if (localPatternRequests != null && !localPatternRequests.isEmpty()) {
            data.patternRequests = new HashMap<>(localPatternRequests);
        }

        final Map<ItemStack, Integer> localPatternRequestsBySource = patternRequestsBySource.get(blockId);
        if (localPatternRequestsBySource != null && !localPatternRequestsBySource.isEmpty()) {
            data.patternRequestsBySource = new HashMap<>(localPatternRequestsBySource);
        }

        final Map<String, TaskSourceInfo> tasks = activeTasks.get(blockId);
        if (tasks != null && !tasks.isEmpty()) {
            data.activeTasks = new HashMap<>(tasks);
        }

        repo.setDataForBridge(blockId, data);
    }

    /**
     * Loads all task handler data from the SavedData repository.
     * This should be called on block entity load.
     */
    public void loadFromRepository() {
        final var level = owner.getLevel();
        final UUID blockId = owner.getBlockId();
        if (level == null || level.isClientSide() || blockId == null) {
            return;
        }

        final BridgeTaskHandlerRepository repo = BridgePatternStorage.getTaskHandler(level);
        final BridgeTaskHandlerRepository.BridgeTaskData data = repo.getDataForBridge(blockId);

        requestCounters.remove(blockId);
        patternRequests.remove(blockId);
        patternRequestsBySource.remove(blockId);
        activeTasks.remove(blockId);

        if (!data.requestCounters.isEmpty()) {
            requestCounters.put(blockId, new HashMap<>(data.requestCounters));
        }
        if (!data.patternRequests.isEmpty()) {
            patternRequests.put(blockId, new HashMap<>(data.patternRequests));
        }
        if (!data.patternRequestsBySource.isEmpty()) {
            patternRequestsBySource.put(blockId, new HashMap<>(data.patternRequestsBySource));
        }
        if (!data.activeTasks.isEmpty()) {
            activeTasks.put(blockId, new HashMap<>(data.activeTasks));
        }
    }

    /**
     * Legacy method - no longer saves to NBT, data is now in SavedData.
     * Kept for API compatibility but does nothing.
     * @deprecated Use {@link #saveToRepository()} instead
     */
    @Deprecated
    public void saveLocalRequestState(final CompoundTag tag, final HolderLookup.Provider registries) {
        // No longer saving to NBT - data is now stored in SavedData repository
    }

    /**
     * Legacy method for migrating data from old NBT format to SavedData.
     * Only loads if data exists in NBT (for migration), then saves to repository.
     */
    public void loadLocalRequestState(final CompoundTag tag, final HolderLookup.Provider registries) {
        final UUID blockId = owner.getBlockId();
        if (blockId == null) {
            return;
        }

        boolean hasMigrationData = tag.contains(TAG_LOCAL_REQUEST_COUNTERS, Tag.TAG_LIST)
            || tag.contains(TAG_LOCAL_PATTERN_REQUESTS, Tag.TAG_LIST)
            || tag.contains(TAG_LOCAL_PATTERN_REQUESTS_BY_SOURCE, Tag.TAG_LIST);

        if (!hasMigrationData) {
            return;
        }

        LOGGER.info("Migrating task handler request state from NBT to SavedData for bridge {}", blockId);

        if (tag.contains(TAG_LOCAL_REQUEST_COUNTERS, Tag.TAG_LIST)) {
            final Map<ItemWithSourceId, Integer> counters =
                readItemWithSourceList(tag.getList(TAG_LOCAL_REQUEST_COUNTERS, Tag.TAG_COMPOUND), registries);
            if (!counters.isEmpty()) {
                requestCounters.put(blockId, counters);
            }
        }
        if (tag.contains(TAG_LOCAL_PATTERN_REQUESTS, Tag.TAG_LIST)) {
            final Map<ItemStack, Integer> requests =
                readItemCountList(tag.getList(TAG_LOCAL_PATTERN_REQUESTS, Tag.TAG_COMPOUND), registries);
            if (!requests.isEmpty()) {
                patternRequests.put(blockId, requests);
            }
        }
        if (tag.contains(TAG_LOCAL_PATTERN_REQUESTS_BY_SOURCE, Tag.TAG_LIST)) {
            final Map<ItemStack, Integer> requests =
                readItemCountList(tag.getList(TAG_LOCAL_PATTERN_REQUESTS_BY_SOURCE, Tag.TAG_COMPOUND), registries);
            if (!requests.isEmpty()) {
                patternRequestsBySource.put(blockId, requests);
            }
        }

        // Save migrated data to repository
        saveToRepository();
        LOGGER.info("Migration complete for bridge {} request state", blockId);
    }

    /**
     * Legacy method - no longer saves to NBT, data is now in SavedData.
     * Kept for API compatibility but does nothing.
     * @deprecated Use {@link #saveToRepository()} instead
     */
    @Deprecated
    public void saveLocalActiveTasks(final CompoundTag tag, final HolderLookup.Provider registries) {
        // No longer saving to NBT - data is now stored in SavedData repository
    }

    /**
     * Legacy method for migrating active tasks from old NBT format to SavedData.
     * Only loads if data exists in NBT (for migration), then saves to repository.
     */
    public void loadLocalActiveTasks(final CompoundTag tag, final HolderLookup.Provider registries) {
        final UUID blockId = owner.getBlockId();
        if (blockId == null) {
            return;
        }

        if (!tag.contains(TAG_LOCAL_ACTIVE_TASKS, Tag.TAG_LIST)) {
            return;
        }

        LOGGER.info("Migrating active tasks from NBT to SavedData for bridge {}", blockId);

        final Map<String, TaskSourceInfo> tasks =
            readActiveTaskList(tag.getList(TAG_LOCAL_ACTIVE_TASKS, Tag.TAG_COMPOUND), registries);
        if (!tasks.isEmpty()) {
            activeTasks.put(blockId, tasks);
        }

        // Save migrated data to repository
        saveToRepository();
        LOGGER.info("Migration complete for bridge {} active tasks ({} tasks)", blockId, tasks.size());
    }

    public Map<String, Map<IMatterType, Long>> getAllocatedMatterByTask() {
        return allocatedMatterByTask;
    }

    public Map<IMatterType, Long> summarizeAllocatedMatter() {
        final Map<IMatterType, Long> totals = new HashMap<>();
        for (Map<IMatterType, Long> allocation : allocatedMatterByTask.values()) {
            for (Map.Entry<IMatterType, Long> entry : allocation.entrySet()) {
                totals.merge(entry.getKey(), entry.getValue(), Long::sum);
            }
        }
        return totals;
    }

    public void resetAfterDataLoad() {
        forceImmediateProcessing();
        needsTaskRescan = true;
    }

    public void forceImmediateProcessing() {
        requestCounterTicks = REQUEST_ACCUMULATION_TICKS;
    }

    public void markNeedsTaskRescan() {
        needsTaskRescan = true;
    }

    public void clearPendingOperations() {
        requestCounters.clear();
        patternRequests.clear();
        patternRequestsBySource.clear();
    }

    private void processAccumulatedRequests(@Nullable final MatterNetwork network) {
        if (RepRS2BridgeBlockEntity.isWorldUnloading() || network == null || requestCounters.isEmpty()) {
            return;
        }
        for (UUID sourceId : requestCounters.keySet()) {
            if (RepRS2BridgeBlockEntity.isWorldUnloading()) {
                return;
            }
            final Map<ItemWithSourceId, Integer> sourceCounters = requestCounters.get(sourceId);
            if (sourceCounters == null) {
                continue;
            }
            for (Map.Entry<ItemWithSourceId, Integer> entry : sourceCounters.entrySet()) {
                if (RepRS2BridgeBlockEntity.isWorldUnloading()) {
                    return;
                }
                final ItemWithSourceId key = entry.getKey();
                final ItemStack itemStack = key.getItemStack();
                final int count = entry.getValue();
                if (count > 0) {
                    createReplicationTasks(network, itemStack, count, sourceId);
                }
            }
        }
    }

    private void createReplicationTasks(final MatterNetwork network,
                                        final ItemStack itemStack,
                                        final int totalCount,
                                        final UUID sourceId) {
        final List<MatterPattern> patterns = findMatchingPatterns(network, itemStack);
        if (patterns.isEmpty()) {
            LOGGER.warn(
                "Bridge: No replication pattern found for {} ({} requests)",
                itemStack.getDisplayName().getString(),
                totalCount
            );
            return;
        }
        final MatterPattern pattern = patterns.get(0);
        spawnReplicationTask(network, pattern, itemStack, totalCount, sourceId);
    }

    private List<MatterPattern> findMatchingPatterns(final MatterNetwork network, final ItemStack requestedStack) {
        final List<MatterPattern> matches = new ArrayList<>();
        final var level = owner.getLevel();
        if (level == null) {
            return matches;
        }
        for (NetworkElement chipSupplier : network.getChipSuppliers()) {
            final var tile = chipSupplier.getLevel().getBlockEntity(chipSupplier.getPos());
            if (tile instanceof ChipStorageBlockEntity chipStorage) {
                for (MatterPattern pattern : chipStorage.getPatterns(level, chipStorage)) {
                    if (ItemStack.isSameItemSameComponents(pattern.getStack(), requestedStack)) {
                        matches.add(pattern);
                    }
                }
            }
        }
        return matches;
    }

    private void spawnReplicationTask(final MatterNetwork network,
                                      final MatterPattern pattern,
                                      final ItemStack requestedStack,
                                      final int amount,
                                      final UUID sourceId) {
        final BlockPos blockPos = owner.getBlockPos();
        final ReplicationTask task = new ReplicationTask(
            pattern.getStack().copy(),
            amount,
            IReplicationTask.Mode.MULTIPLE,
            blockPos,
            false
        );

        final String taskId = task.getUuid().toString();
        network.getTaskManager().getPendingTasks().put(taskId, task);
        if (owner.getLevel() instanceof ServerLevel serverLevel) {
            network.onTaskValueChanged(task, serverLevel);
        }

        final TaskSourceInfo info = getTaskSourceInfo(requestedStack, sourceId);
        final Map<String, TaskSourceInfo> sourceTasks = activeTasks.computeIfAbsent(sourceId, id -> new HashMap<>());
        sourceTasks.put(taskId, info);

        final Map<ItemStack, Integer> sourceRequests = patternRequestsBySource.computeIfAbsent(sourceId, id -> new HashMap<>());
        final int currentPatternRequests = sourceRequests.getOrDefault(requestedStack, 0);
        sourceRequests.put(requestedStack, currentPatternRequests + amount);

        extractMatterForTask(pattern, amount, taskId);
    }

    private @NotNull TaskSourceInfo getTaskSourceInfo(final ItemStack itemStack, final UUID sourceId) {
        TaskId rs2TaskId = null;
        final RepRS2BridgeNetworkNode networkNode = owner.getBridgeNetworkNode();
        if (networkNode != null) {
            rs2TaskId = networkNode.peekActiveTaskId();
        }
        return new TaskSourceInfo(itemStack, sourceId, rs2TaskId);
    }

    private void extractMatterForTask(final MatterPattern pattern, final int count, final String taskId) {
        final var matterCompound = ReplicationCalculation.getMatterCompound(pattern.getStack());
        if (matterCompound == null) {
            return;
        }
        final Map<IMatterType, Long> allocated = new HashMap<>();
        for (MatterValue matterValue : matterCompound.getValues().values()) {
            final IMatterType matterType = matterValue.getMatter();
            if (matterType == null) {
                continue;
            }
            final long matterAmount = (long) Math.ceil(matterValue.getAmount()) * count;
            allocated.merge(matterType, matterAmount, Long::sum);
        }
        if (!allocated.isEmpty()) {
            allocatedMatterByTask.put(taskId, allocated);
        }
    }

    private void relinkActiveTasksFromNetwork(final MatterNetwork network) {
        final UUID blockId = owner.getBlockId();
        if (blockId == null) {
            return;
        }
        final Map<String, TaskSourceInfo> tasks = activeTasks.computeIfAbsent(blockId, id -> new HashMap<>());
        final Map<String, TaskSourceInfo> previous = new HashMap<>(tasks);
        tasks.clear();
        network.getTaskManager().getPendingTasks().forEach((taskId, task) -> {
            if (task == null || task.getSource() == null) {
                return;
            }
            if (!task.getSource().equals(owner.getBlockPos())) {
                return;
            }
            final TaskSourceInfo existing = previous.get(taskId);
            if (existing != null) {
                tasks.put(taskId, new TaskSourceInfo(task.getReplicatingStack(), existing.getSourceId(), existing.getRs2TaskId()));
            } else {
                tasks.put(taskId, new TaskSourceInfo(task.getReplicatingStack(), blockId));
            }
        });
    }

    private ListTag writeItemWithSourceList(final Map<ItemWithSourceId, Integer> map,
                                            final HolderLookup.Provider registries) {
        final ListTag list = new ListTag();
        map.forEach((key, amount) -> {
            final CompoundTag entry = new CompoundTag();
            entry.put("Stack", key.getItemStack().saveOptional(registries));
            entry.putInt("Count", amount);
            entry.putUUID("Owner", key.getSourceId());
            list.add(entry);
        });
        return list;
    }

    private Map<ItemWithSourceId, Integer> readItemWithSourceList(final ListTag list,
                                                                  final HolderLookup.Provider registries) {
        final Map<ItemWithSourceId, Integer> map = new HashMap<>();
        for (Tag element : list) {
            final CompoundTag entry = (CompoundTag) element;
            final ItemStack stack = ItemStack.parse(registries, entry.getCompound("Stack")).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }
            final UUID ownerId = entry.contains("Owner") ? entry.getUUID("Owner") : owner.getBlockId();
            final int amount = entry.getInt("Count");
            map.put(new ItemWithSourceId(stack, ownerId), amount);
        }
        return map;
    }

    private ListTag writeItemCountList(final Map<ItemStack, Integer> map, final HolderLookup.Provider registries) {
        final ListTag list = new ListTag();
        map.forEach((stack, amount) -> {
            final CompoundTag entry = new CompoundTag();
            entry.put("Stack", stack.saveOptional(registries));
            entry.putInt("Count", amount);
            list.add(entry);
        });
        return list;
    }

    private Map<ItemStack, Integer> readItemCountList(final ListTag list, final HolderLookup.Provider registries) {
        final Map<ItemStack, Integer> map = new HashMap<>();
        for (Tag element : list) {
            final CompoundTag entry = (CompoundTag) element;
            final ItemStack stack = ItemStack.parse(registries, entry.getCompound("Stack")).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }
            map.put(stack, entry.getInt("Count"));
        }
        return map;
    }

    private ListTag writeActiveTaskList(final Map<String, TaskSourceInfo> tasks,
                                        final HolderLookup.Provider registries) {
        final ListTag list = new ListTag();
        tasks.forEach((taskId, info) -> {
            final CompoundTag entry = new CompoundTag();
            entry.putString("TaskId", taskId);
            entry.put("Stack", info.getItemStack().saveOptional(registries));
            if (info.getRs2TaskId() != null) {
                entry.putUUID("Rs2TaskId", info.getRs2TaskId().id());
            }
            list.add(entry);
        });
        return list;
    }

    private Map<String, TaskSourceInfo> readActiveTaskList(final ListTag list,
                                                           final HolderLookup.Provider registries) {
        final Map<String, TaskSourceInfo> map = new HashMap<>();
        for (Tag element : list) {
            final CompoundTag entry = (CompoundTag) element;
            final ItemStack stack = ItemStack.parse(registries, entry.getCompound("Stack")).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }
            final String taskId = entry.getString("TaskId");
            TaskId rs2TaskId = null;
            if (entry.contains("Rs2TaskId")) {
                rs2TaskId = new TaskId(entry.getUUID("Rs2TaskId"));
            }
            map.put(taskId, new TaskSourceInfo(stack, owner.getBlockId(), rs2TaskId));
        }
        return map;
    }
}
