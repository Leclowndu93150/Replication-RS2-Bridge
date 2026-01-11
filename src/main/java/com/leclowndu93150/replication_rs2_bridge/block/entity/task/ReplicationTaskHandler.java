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
import com.buuz135.replication.ReplicationRegistry;
import com.hrznstudio.titanium.block_network.element.NetworkElement;
import com.leclowndu93150.replication_rs2_bridge.block.entity.RepRS2BridgeBlockEntity;
import com.leclowndu93150.replication_rs2_bridge.block.entity.RepRS2BridgeNetworkNode;
import com.leclowndu93150.replication_rs2_bridge.block.entity.pattern.ReplicationPatternTemplate;
import com.leclowndu93150.replication_rs2_bridge.block.entity.task.model.ItemWithSourceId;
import com.leclowndu93150.replication_rs2_bridge.block.entity.task.model.TaskSourceInfo;
import com.mojang.logging.LogUtils;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskId;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.resources.ResourceLocation;
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
    private static final String TAG_ALLOCATED_MATTER = "AllocatedMatter";
    private static final String TAG_TASK_ID = "TaskId";
    private static final String TAG_MATTER_LIST = "MatterList";
    private static final String TAG_MATTER_ID = "MatterId"; // int registry id (preferred)
    private static final String TAG_MATTER_NAME = "MatterName"; // fallback string key
    private static final String TAG_AMOUNT = "Amount";
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
        cleanupCompletedTasks(replicationNetwork);
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
                final Map<ItemStack, Integer> globalRequests = patternRequests.get(blockId);
                if (globalRequests != null) {
                    globalRequests.remove(info.getItemStack());
                    if (globalRequests.isEmpty()) {
                        patternRequests.remove(blockId);
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

    public void saveToNbt(final CompoundTag tag, final HolderLookup.Provider registries) {
        final UUID blockId = owner.getBlockId();
        if (blockId == null || tag == null) {
            return;
        }

        final CompoundTag handler = new CompoundTag();

        final Map<ItemWithSourceId, Integer> localCounters = requestCounters.get(blockId);
        if (localCounters != null && !localCounters.isEmpty()) {
            handler.put(TAG_LOCAL_REQUEST_COUNTERS, writeItemWithSourceList(localCounters, registries));
        }

        final Map<ItemStack, Integer> localPatternRequests = patternRequests.get(blockId);
        if (localPatternRequests != null && !localPatternRequests.isEmpty()) {
            handler.put(TAG_LOCAL_PATTERN_REQUESTS, writeItemCountList(localPatternRequests, registries));
        }

        final Map<ItemStack, Integer> localPatternRequestsBySource = patternRequestsBySource.get(blockId);
        if (localPatternRequestsBySource != null && !localPatternRequestsBySource.isEmpty()) {
            handler.put(TAG_LOCAL_PATTERN_REQUESTS_BY_SOURCE, writeItemCountList(localPatternRequestsBySource, registries));
        }

        final Map<String, TaskSourceInfo> tasks = activeTasks.get(blockId);
        if (tasks != null && !tasks.isEmpty()) {
            handler.put(TAG_LOCAL_ACTIVE_TASKS, writeActiveTaskList(tasks, registries));
        }

        final Map<String, Map<String, Long>> allocated = serializeAllocatedMatter();
        if (!allocated.isEmpty()) {
            handler.put(TAG_ALLOCATED_MATTER, writeAllocatedMatterList(allocated));
        }

        tag.put("TaskHandler", handler);
    }

    public void loadFromNbt(final CompoundTag tag, final HolderLookup.Provider registries) {
        final UUID blockId = owner.getBlockId();
        if (blockId == null || tag == null || !tag.contains("TaskHandler", Tag.TAG_COMPOUND)) {
            return;
        }
        final CompoundTag handler = tag.getCompound("TaskHandler");

        requestCounters.remove(blockId);
        patternRequests.remove(blockId);
        patternRequestsBySource.remove(blockId);
        activeTasks.remove(blockId);
        allocatedMatterByTask.clear();

        if (handler.contains(TAG_LOCAL_REQUEST_COUNTERS, Tag.TAG_LIST)) {
            requestCounters.put(blockId, readItemWithSourceList(handler.getList(TAG_LOCAL_REQUEST_COUNTERS, Tag.TAG_COMPOUND), registries));
        }
        if (handler.contains(TAG_LOCAL_PATTERN_REQUESTS, Tag.TAG_LIST)) {
            patternRequests.put(blockId, readItemCountList(handler.getList(TAG_LOCAL_PATTERN_REQUESTS, Tag.TAG_COMPOUND), registries));
        }
        if (handler.contains(TAG_LOCAL_PATTERN_REQUESTS_BY_SOURCE, Tag.TAG_LIST)) {
            patternRequestsBySource.put(blockId, readItemCountList(handler.getList(TAG_LOCAL_PATTERN_REQUESTS_BY_SOURCE, Tag.TAG_COMPOUND), registries));
        }
        if (handler.contains(TAG_LOCAL_ACTIVE_TASKS, Tag.TAG_LIST)) {
            activeTasks.put(blockId, readActiveTaskList(handler.getList(TAG_LOCAL_ACTIVE_TASKS, Tag.TAG_COMPOUND), registries));
        }
        if (handler.contains(TAG_ALLOCATED_MATTER, Tag.TAG_LIST)) {
            deserializeAllocatedMatter(readAllocatedMatterList(handler.getList(TAG_ALLOCATED_MATTER, Tag.TAG_COMPOUND)));
        }
        pruneOrphanAllocations(blockId);
    }

    // Legacy methods kept for API compatibility; no-ops now that persistence is in block NBT.
    @Deprecated
    public void saveLocalRequestState(final CompoundTag tag, final HolderLookup.Provider registries) {
    }

    public void loadLocalRequestState(final CompoundTag tag, final HolderLookup.Provider registries) {
    }

    @Deprecated
    public void saveLocalActiveTasks(final CompoundTag tag, final HolderLookup.Provider registries) {
    }

    public void loadLocalActiveTasks(final CompoundTag tag, final HolderLookup.Provider registries) {
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

    public void prepareForSave(@Nullable final MatterNetwork replicationNetwork) {
        if (replicationNetwork != null) {
            cleanupCompletedTasks(replicationNetwork);
            cancelOrphanReplicationTasks(replicationNetwork);
        }
        pruneOrphanAllocations(owner.getBlockId());
    }

    public void cancelAllReplicationTasks(@Nullable final MatterNetwork replicationNetwork) {
        if (replicationNetwork == null || owner.getLevel() == null) {
            return;
        }
        final ServerLevel serverLevel = owner.getLevel() instanceof ServerLevel server ? server : null;
        final UUID blockId = owner.getBlockId();
        final var pendingTasks = replicationNetwork.getTaskManager().getPendingTasks();
        for (String taskId : new ArrayList<>(pendingTasks.keySet())) {
            final var task = pendingTasks.get(taskId);
            if (task == null || task.getSource() == null || !task.getSource().equals(owner.getBlockPos())) {
                continue;
            }
            replicationNetwork.cancelTask(taskId, serverLevel);
            pendingTasks.remove(taskId);
        }
        if (blockId != null) {
            activeTasks.remove(blockId);
            patternRequests.remove(blockId);
            patternRequestsBySource.remove(blockId);
        }
        allocatedMatterByTask.clear();
        owner.setChanged();
    }

    public void rebuildAllocationState() {
        owner.getMatterStorage().refreshCache();
        final RepRS2BridgeNetworkNode networkNode = owner.getBridgeNetworkNode();
        if (networkNode != null) {
            networkNode.refreshStorageInNetwork();
        }
        owner.setChanged();
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

    public void compactIfIdle(final boolean hasRs2Tasks) {
        if (hasRs2Tasks) {
            return;
        }
        if (!activeTasks.isEmpty()) {
            return;
        }
        final UUID blockId = owner.getBlockId();
        boolean changed = false;
        if (!allocatedMatterByTask.isEmpty()) {
            allocatedMatterByTask.clear();
            changed = true;
        }
        if (blockId != null) {
            if (patternRequests.remove(blockId) != null) {
                changed = true;
            }
            if (patternRequestsBySource.remove(blockId) != null) {
                changed = true;
            }
            if (requestCounters.remove(blockId) != null) {
                changed = true;
            }
        }
        if (changed) {
            owner.setChanged();
        }
    }

    private void pruneOrphanAllocations(final UUID blockId) {
        if (blockId == null) {
            return;
        }
        final Map<String, TaskSourceInfo> tasks = activeTasks.get(blockId);
        if (tasks == null || tasks.isEmpty()) {
            if (!allocatedMatterByTask.isEmpty()) {
                allocatedMatterByTask.clear();
                owner.setChanged();
            }
            return;
        }
        final var validTaskIds = new ArrayList<>(tasks.keySet());
        boolean changed = allocatedMatterByTask.keySet().removeIf(taskId -> !validTaskIds.contains(taskId));
        if (changed) {
            owner.setChanged();
        }
    }

    private void cleanupCompletedTasks(@Nullable final MatterNetwork replicationNetwork) {
        if (replicationNetwork == null) {
            return;
        }
        final var pendingTasks = replicationNetwork.getTaskManager().getPendingTasks();
        boolean removedAny = false;

        for (UUID sourceId : new ArrayList<>(activeTasks.keySet())) {
            final Map<String, TaskSourceInfo> sourceTasks = activeTasks.get(sourceId);
            if (sourceTasks == null) {
                continue;
            }
            for (String taskId : new ArrayList<>(sourceTasks.keySet())) {
                if (pendingTasks.containsKey(taskId)) {
                    continue;
                }
                final TaskSourceInfo info = sourceTasks.remove(taskId);
                allocatedMatterByTask.remove(taskId);
                if (info != null && info.getItemStack() != null) {
                    final Map<ItemStack, Integer> sourceRequests = patternRequestsBySource.get(sourceId);
                    if (sourceRequests != null) {
                        sourceRequests.remove(info.getItemStack());
                        if (sourceRequests.isEmpty()) {
                            patternRequestsBySource.remove(sourceId);
                        }
                    }
                    final Map<ItemStack, Integer> globalRequests = patternRequests.get(sourceId);
                    if (globalRequests != null) {
                        globalRequests.remove(info.getItemStack());
                        if (globalRequests.isEmpty()) {
                            patternRequests.remove(sourceId);
                        }
                    }
                }
                removedAny = true;
            }
            if (sourceTasks.isEmpty()) {
                activeTasks.remove(sourceId);
            }
        }

        if (removedAny) {
            owner.getMatterStorage().refreshCache();
            final RepRS2BridgeNetworkNode networkNode = owner.getBridgeNetworkNode();
            if (networkNode != null) {
                networkNode.refreshStorageInNetwork();
            }
            owner.setChanged();
        }
    }

    private void cancelOrphanReplicationTasks(final MatterNetwork replicationNetwork) {
        final UUID blockId = owner.getBlockId();
        if (blockId == null) {
            return;
        }
        final var pendingTasks = replicationNetwork.getTaskManager().getPendingTasks();
        for (String taskId : new ArrayList<>(pendingTasks.keySet())) {
            final var task = pendingTasks.get(taskId);
            if (task == null || task.getSource() == null) {
                continue;
            }
            if (!task.getSource().equals(owner.getBlockPos())) {
                continue;
            }
            final Map<String, TaskSourceInfo> tasks = activeTasks.get(blockId);
            if (tasks != null && tasks.containsKey(taskId)) {
                continue;
            }
            replicationNetwork.cancelTask(taskId, owner.getLevel() instanceof ServerLevel server ? server : null);
            pendingTasks.remove(taskId);
        }
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
        owner.setChanged();
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

    private Map<String, Map<String, Long>> serializeAllocatedMatter() {
        final Map<String, Map<String, Long>> serialized = new HashMap<>();
        allocatedMatterByTask.forEach((taskId, allocations) -> {
            if (allocations.isEmpty()) {
                return;
            }
            final Map<String, Long> matterMap = new HashMap<>();
            allocations.forEach((matterType, amount) -> {
                if (amount <= 0) {
                    return;
                }
                final Integer numericId = ReplicationRegistry.MATTER_TYPES_REGISTRY.getId(matterType);
                if (numericId != null && numericId >= 0) {
                    matterMap.put(Integer.toString(numericId), amount);
                } else {
                    final ResourceLocation key = ReplicationRegistry.MATTER_TYPES_REGISTRY.getKey(matterType);
                    if (key != null) {
                        matterMap.put(key.toString(), amount);
                    }
                }
            });
            if (!matterMap.isEmpty()) {
                serialized.put(taskId, matterMap);
            }
        });
        return serialized;
    }

    private ListTag writeAllocatedMatterList(final Map<String, Map<String, Long>> allocated) {
        final ListTag list = new ListTag();
        allocated.forEach((taskId, matterMap) -> {
            if (matterMap.isEmpty()) {
                return;
            }
            final CompoundTag entry = new CompoundTag();
            entry.putString(TAG_TASK_ID, taskId);
            final ListTag matterList = new ListTag();
            matterMap.forEach((matterId, amount) -> {
                final CompoundTag matterEntry = new CompoundTag();
                Integer numericId = null;
                try {
                    numericId = Integer.parseInt(matterId);
                } catch (NumberFormatException ignored) {
                }
                if (numericId == null || numericId < 0) {
                    final ResourceLocation key = ResourceLocation.tryParse(matterId);
                    final IMatterType type = key != null ? ReplicationRegistry.MATTER_TYPES_REGISTRY.get(key) : null;
                    numericId = type != null ? ReplicationRegistry.MATTER_TYPES_REGISTRY.getId(type) : null;
                }
                if (numericId != null && numericId >= 0) {
                    matterEntry.putInt(TAG_MATTER_ID, numericId);
                } else {
                    matterEntry.putString(TAG_MATTER_NAME, matterId);
                }
                matterEntry.putLong(TAG_AMOUNT, amount);
                matterList.add(matterEntry);
            });
            entry.put(TAG_MATTER_LIST, matterList);
            list.add(entry);
        });
        return list;
    }

    private Map<String, Map<String, Long>> readAllocatedMatterList(final ListTag list) {
        final Map<String, Map<String, Long>> allocated = new HashMap<>();
        for (Tag element : list) {
            final CompoundTag entry = (CompoundTag) element;
            final String taskId = entry.getString(TAG_TASK_ID);
            if (taskId == null || taskId.isEmpty()) {
                continue;
            }
            final Map<String, Long> matters = new HashMap<>();
            for (Tag matterTag : entry.getList(TAG_MATTER_LIST, Tag.TAG_COMPOUND)) {
                final CompoundTag matterEntry = (CompoundTag) matterTag;
                String matterId = matterEntry.getString(TAG_MATTER_NAME);
                if (matterEntry.contains(TAG_MATTER_ID, Tag.TAG_INT)) {
                    final IMatterType type =
                        ReplicationRegistry.MATTER_TYPES_REGISTRY.byId(matterEntry.getInt(TAG_MATTER_ID));
                    if (type != null && ReplicationRegistry.MATTER_TYPES_REGISTRY.getKey(type) != null) {
                        matterId = ReplicationRegistry.MATTER_TYPES_REGISTRY.getKey(type).toString();
                    }
                } else if (!matterId.isEmpty()) {
                    try {
                        final int numericId = Integer.parseInt(matterId);
                        final IMatterType type = ReplicationRegistry.MATTER_TYPES_REGISTRY.byId(numericId);
                        if (type != null && ReplicationRegistry.MATTER_TYPES_REGISTRY.getKey(type) != null) {
                            matterId = ReplicationRegistry.MATTER_TYPES_REGISTRY.getKey(type).toString();
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                final long amount = matterEntry.getLong(TAG_AMOUNT);
                if (!matterId.isEmpty() && amount > 0) {
                    matters.put(matterId, amount);
                }
            }
            if (!matters.isEmpty()) {
                allocated.put(taskId, matters);
            }
        }
        return allocated;
    }

    private void deserializeAllocatedMatter(final Map<String, Map<String, Long>> serialized) {
        allocatedMatterByTask.clear();
        serialized.forEach((taskId, matterMap) -> {
            final Map<IMatterType, Long> allocations = new HashMap<>();
            matterMap.forEach((matterId, amount) -> {
                if (amount <= 0) {
                    return;
                }
                IMatterType type = null;
                try {
                    final int numericId = Integer.parseInt(matterId);
                    type = ReplicationRegistry.MATTER_TYPES_REGISTRY.byId(numericId);
                } catch (NumberFormatException ignored) {
                }
                if (type == null) {
                    final ResourceLocation key = ResourceLocation.tryParse(matterId);
                    type = key != null ? ReplicationRegistry.MATTER_TYPES_REGISTRY.get(key) : null;
                }
                if (type != null) {
                    allocations.put(type, amount);
                }
            });
            if (!allocations.isEmpty()) {
                allocatedMatterByTask.put(taskId, allocations);
            }
        });
    }
}
