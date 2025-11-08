package com.leclowndu93150.replication_rs2_bridge.block.entity;

import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.pattern.MatterPattern;
import com.buuz135.replication.api.task.IReplicationTask;
import com.buuz135.replication.api.task.ReplicationTask;
import com.buuz135.replication.block.tile.ChipStorageBlockEntity;
import com.buuz135.replication.block.tile.ReplicationMachine;
import com.buuz135.replication.block.MatterPipeBlock;
import com.buuz135.replication.calculation.MatterValue;
import com.buuz135.replication.calculation.ReplicationCalculation;
import com.buuz135.replication.network.DefaultMatterNetworkElement;
import com.buuz135.replication.network.MatterNetwork;
import com.buuz135.replication.ReplicationRegistry;
import com.hrznstudio.titanium.annotation.Save;
import com.hrznstudio.titanium.block.BasicTileBlock;
import com.hrznstudio.titanium.block_network.NetworkManager;
import com.hrznstudio.titanium.block_network.element.NetworkElement;
import com.hrznstudio.titanium.component.inventory.InventoryComponent;
import com.leclowndu93150.replication_rs2_bridge.Config;
import com.leclowndu93150.replication_rs2_bridge.block.ModBlocks;
import com.leclowndu93150.replication_rs2_bridge.block.custom.RepRS2BridgeBlock;
import com.leclowndu93150.replication_rs2_bridge.item.ModItems;
import com.mojang.logging.LogUtils;
import com.refinedmods.refinedstorage.api.autocrafting.Ingredient;
import com.refinedmods.refinedstorage.api.autocrafting.Pattern;
import com.refinedmods.refinedstorage.api.autocrafting.PatternLayout;
import com.refinedmods.refinedstorage.api.autocrafting.PatternType;
import com.refinedmods.refinedstorage.api.autocrafting.task.ExternalPatternSink;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskId;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskState;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskSnapshot;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.energy.EnergyNetworkComponent;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.resource.ResourceKey;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.api.storage.Storage;
import com.refinedmods.refinedstorage.api.storage.composite.CompositeAwareChild;
import com.refinedmods.refinedstorage.api.storage.composite.ParentComposite;
import com.refinedmods.refinedstorage.api.resource.list.MutableResourceList;
import com.refinedmods.refinedstorage.api.resource.list.MutableResourceListImpl;
import com.refinedmods.refinedstorage.api.resource.list.ResourceList;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;
import com.refinedmods.refinedstorage.common.api.storage.PlayerActor;
import com.refinedmods.refinedstorage.common.support.network.ColoredConnectionStrategy;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import com.refinedmods.refinedstorage.common.support.resource.ResourceCodecs;
import com.refinedmods.refinedstorage.common.api.support.resource.PlatformResourceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.*;

/**
 * BlockEntity for the RepRS2Bridge that connects the RS2 network with the Replication matter network
 * Equivalent to RepAE2BridgeBlockEntity but for Refined Storage 2
 */
public class RepRS2BridgeBlockEntity extends ReplicationMachine<RepRS2BridgeBlockEntity> {

    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static final int REQUEST_ACCUMULATION_TICKS = 100;
    private static final int INITIALIZATION_DELAY = 60;
    private static final int PATTERN_UPDATE_INTERVAL = 100;
    private static final String TAG_LOCAL_REQUEST_COUNTERS = "LocalRequestCounters";
    private static final String TAG_LOCAL_PATTERN_REQUESTS = "LocalPatternRequests";
    private static final String TAG_LOCAL_PATTERN_REQUESTS_BY_SOURCE = "LocalPatternRequestsBySource";
    private static final String TAG_LOCAL_ACTIVE_TASKS = "LocalActiveTasks";
    private static final String TAG_RS_TASK_SNAPSHOTS = "RsTaskSnapshots";
    
    private byte initialized = 0;
    private int initializationTicks = 0;
    private int patternUpdateTicks = 0;
    private int debugTickCounter = 0;
    
    @Save
    private InventoryComponent<RepRS2BridgeBlockEntity> output;
    
    @Save
    private UUID blockId;
    
    private final RepRS2BridgeNetworkNode networkNode;
    private final InWorldNetworkNodeContainer nodeContainer;
    private final NetworkNodeContainerProvider containerProvider;
    private boolean nodeCreated = false;
    private boolean shouldReconnect = false;
    
    private final Map<UUID, Map<ItemStack, Integer>> patternRequests = new HashMap<>();
    private final Map<UUID, Map<String, TaskSourceInfo>> activeTasks = new HashMap<>();
    private final Map<UUID, Map<ItemStack, Integer>> patternRequestsBySource = new HashMap<>();
    private final Map<UUID, Map<ItemWithSourceId, Integer>> requestCounters = new HashMap<>();
    private int requestCounterTicks = 0;
    
    final MatterItemsStorage matterItemsStorage = new MatterItemsStorage();
    private boolean needsTaskRescan = false;
    private static final int TASK_SNAPSHOT_SAVE_INTERVAL = 20;
    private int taskSnapshotSaveTicks = 0;
    private boolean hadActiveRsTasks = false;
    
    private static boolean worldUnloading = false;
    private static final Set<RepRS2BridgeBlockEntity> activeBridges = new HashSet<>();

    public RepRS2BridgeBlockEntity(BlockPos pos, BlockState state) {
        super((BasicTileBlock<RepRS2BridgeBlockEntity>) ModBlocks.REP_RS2_BRIDGE.get(),
                ModBlockEntities.REP_RS2_BRIDGE.get(),
                pos,
                state);
        
        this.blockId = UUID.randomUUID();
        
        this.output = new InventoryComponent<RepRS2BridgeBlockEntity>("output", 11, 131, 18)
                .setRange(9, 2)
                .setComponentHarness(this)
                .setInputFilter((stack, slot) -> true);
        this.addInventory(this.output);
        
        this.networkNode = new RepRS2BridgeNetworkNode(this, Config.bridgeEnergyConsumption);
        this.containerProvider = RefinedStorageApi.INSTANCE.createNetworkNodeContainerProvider();
        this.nodeContainer = RefinedStorageApi.INSTANCE.createNetworkNodeContainer(this, networkNode)
                .connectionStrategy(new ColoredConnectionStrategy(this::getBlockState, worldPosition))
                .build();
        this.containerProvider.addContainer(this.nodeContainer);
        
        activeBridges.add(this);
        if (Config.enableDebugLogging) {
            LOGGER.debug("Bridge created at {} (total bridges: {})", pos, activeBridges.size());
        }
    }

    @NotNull
    @Override
    public RepRS2BridgeBlockEntity getSelf() {
        return this;
    }

    @Override
    protected NetworkElement createElement(Level level, BlockPos pos) {
        try {
            return new DefaultMatterNetworkElement(level, pos) {
                @Override
                public boolean canConnectFrom(Direction direction) {
                    BlockPos neighborPos = pos.relative(direction);
                    if (level.getBlockEntity(neighborPos) instanceof RepRS2BridgeBlockEntity) {
                        return false;
                    }
                    return super.canConnectFrom(direction);
                }
            };
        } catch (Exception e) {
            LOGGER.error("Failed to create Replication network element: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void onLoad() {
        try {
            super.onLoad();
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("network is null")) {
                LOGGER.warn("Bridge: Replication network not ready during onLoad, will retry later");
                if (level != null && !level.isClientSide()) {
                    level.scheduleTick(worldPosition, getBlockState().getBlock(), 60);
                }
                return;
            } else {
                LOGGER.error("Bridge: Unexpected error during onLoad: {}", e.getMessage());
                if (level != null && !level.isClientSide()) {
                    level.scheduleTick(worldPosition, getBlockState().getBlock(), 100);
                }
                return;
            }
        }
        
        if (!nodeCreated && level != null && !level.isClientSide()) {
            initializeRS2Node();
        }
    }

    private void initializeRS2Node() {
        if (level == null || level.isClientSide()) {
            return;
        }
        try {
            containerProvider.initialize(level, () -> {
                nodeCreated = true;
                shouldReconnect = false;
                updateConnectedState();
                forceNeighborUpdates();
                matterItemsStorage.refreshCache();
                patternUpdateTicks = PATTERN_UPDATE_INTERVAL;
                try {
                    updateRS2Patterns();
                } catch (Exception patternEx) {
                    LOGGER.error("Bridge: Pattern update failed during initialization", patternEx);
                }
                if (Config.enableDebugLogging) {
                    LOGGER.info("Bridge: RS2 node initialized successfully at {}", worldPosition);
                }
            });
        } catch (Exception e) {
            LOGGER.error("Failed to initialize RS2 node: {}", e.getMessage());
            shouldReconnect = true;
            level.scheduleTick(worldPosition, getBlockState().getBlock(), 20);
        }
    }

    public void handleNeighborChanged(BlockPos fromPos) {
        if (level != null && !level.isClientSide()) {
            Direction directionToNeighbor = null;
            for (Direction dir : Direction.values()) {
                if (worldPosition.relative(dir).equals(fromPos)) {
                    directionToNeighbor = dir;
                    break;
                }
            }
            
            if (directionToNeighbor != null && level.getBlockEntity(fromPos) instanceof RepRS2BridgeBlockEntity) {
                return;
            }
            
            if (!nodeCreated) {
                initializeRS2Node();
            }
            updateConnectedState();
        }
    }

    public void updateConnectedState() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = level.getBlockState(worldPosition);
        if (state.getBlock() instanceof RepRS2BridgeBlock && state.hasProperty(RepRS2BridgeBlock.CONNECTED)) {
            boolean connected = isActive() && getNetwork() != null;
            if (state.getValue(RepRS2BridgeBlock.CONNECTED) != connected) {
                level.setBlock(worldPosition, state.setValue(RepRS2BridgeBlock.CONNECTED, connected), 3);
            }
        }
    }

    private void forceNeighborUpdates() {
        if (level == null || level.isClientSide()) {
            return;
        }
        BlockState state = level.getBlockState(worldPosition);
        level.sendBlockUpdated(worldPosition, state, state, 3);
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = worldPosition.relative(direction);
            BlockState neighborState = level.getBlockState(neighborPos);
            level.neighborChanged(neighborPos, state.getBlock(), worldPosition);
            if (neighborState.getBlock() instanceof MatterPipeBlock) {
                level.sendBlockUpdated(neighborPos, neighborState, neighborState, 3);
            }
        }
    }

    @Override
    public void serverTick(Level level, BlockPos pos, BlockState state, RepRS2BridgeBlockEntity blockEntity) {
        if (worldUnloading) {
            return;
        }
        
        try {
            super.serverTick(level, pos, state, blockEntity);
        } catch (Exception e) {
            LOGGER.error("Bridge: Exception in super.serverTick(): {}", e.getMessage());
            return;
        }
        
        if (worldUnloading) {
            return;
        }
        
        MatterNetwork replicationNetwork = getNetwork();
        if (needsTaskRescan && replicationNetwork != null) {
            relinkActiveTasksFromNetwork(replicationNetwork);
            needsTaskRescan = false;
        }
        if (shouldReconnect && !nodeCreated) {
            initializeRS2Node();
        }
        
        if (initialized == 0) {
            initializationTicks++;
            if (initializationTicks >= INITIALIZATION_DELAY) {
                if (Config.enableDebugLogging) {
                    LOGGER.info("Bridge: Initialization completed at {}", worldPosition);
                }
                initialized = 1;
            }
        }
        
        if (level.getGameTime() % 20 == 0) {
            if (initialized == 1) {
                try {
                    transferItemsToRS2();
                } catch (Exception e) {
                    LOGGER.error("Bridge: Exception in transferItemsToRS2(): {}", e.getMessage());
                }
            }
            matterItemsStorage.refreshCache();
        }
        
        if (patternUpdateTicks >= PATTERN_UPDATE_INTERVAL) {
            if (isActive() && replicationNetwork != null) {
                try {
                    updateRS2Patterns();
                } catch (Exception e) {
                    LOGGER.error("Bridge: Exception during pattern update: {}", e.getMessage());
                }
            }
            patternUpdateTicks = 0;
        } else {
            patternUpdateTicks++;
        }
        
        if (requestCounterTicks >= REQUEST_ACCUMULATION_TICKS) {
            try {
                processAccumulatedRequests();
                requestCounters.clear();
                requestCounterTicks = 0;
            } catch (Exception e) {
                LOGGER.error("Bridge: Exception in request processing: {}", e.getMessage());
            }
        } else {
            requestCounterTicks++;
        }
        
        if (replicationNetwork == null) {
            NetworkManager networkManager = NetworkManager.get(level);
            if (networkManager != null && networkManager.getElement(pos) == null) {
                networkManager.addElement(createElement(level, pos));
            }
        }
        updateConnectedState();
        tickNetworkNode();
        tickTaskSnapshotPersistence();
    }

    private void processAccumulatedRequests() {
        if (worldUnloading) return;
        
        MatterNetwork network = getNetwork();
        if (network != null && !requestCounters.isEmpty()) {
            for (UUID sourceId : requestCounters.keySet()) {
                if (worldUnloading) return;
                
                Map<ItemWithSourceId, Integer> sourceCounters = requestCounters.get(sourceId);
                
                for (Map.Entry<ItemWithSourceId, Integer> entry : sourceCounters.entrySet()) {
                    if (worldUnloading) return;
                    
                    ItemWithSourceId key = entry.getKey();
                    ItemStack itemStack = key.getItemStack();
                    int count = entry.getValue();
                    
                    if (count > 0) {
                        createReplicationTask(network, itemStack, count, sourceId);
                    }
                }
            }
        }
    }

    private void createReplicationTask(MatterNetwork network, ItemStack itemStack, int count, UUID sourceId) {
        for (NetworkElement chipSupplier : network.getChipSuppliers()) {
            var tile = chipSupplier.getLevel().getBlockEntity(chipSupplier.getPos());
            if (tile instanceof ChipStorageBlockEntity chipStorage) {
                for (MatterPattern pattern : chipStorage.getPatterns(level, chipStorage)) {
                    if (pattern.getStack().getItem().equals(itemStack.getItem())) {
                        ReplicationTask task = new ReplicationTask(
                                pattern.getStack(),
                                count,
                                IReplicationTask.Mode.MULTIPLE,
                                this.worldPosition
                        );
                        
                        String taskId = task.getUuid().toString();
                        network.getTaskManager().getPendingTasks().put(taskId, task);
                        if (level instanceof ServerLevel serverLevel) {
                            network.onTaskValueChanged(task, serverLevel);
                        }

                        TaskSourceInfo info = getTaskSourceInfo(itemStack, sourceId);
                        Map<String, TaskSourceInfo> sourceTasks = activeTasks.getOrDefault(sourceId, new HashMap<>());
                        sourceTasks.put(taskId, info);
                        activeTasks.put(sourceId, sourceTasks);
                        
                        Map<ItemStack, Integer> sourceRequests = patternRequestsBySource.getOrDefault(sourceId, new HashMap<>());
                        int currentPatternRequests = sourceRequests.getOrDefault(itemStack, 0);
                        sourceRequests.put(itemStack, currentPatternRequests + count);
                        patternRequestsBySource.put(sourceId, sourceRequests);
                        
                        extractMatterForTask(pattern, count);
                        break;
                    }
                }
            }
        }
    }

    private @NotNull TaskSourceInfo getTaskSourceInfo(ItemStack itemStack, UUID sourceId) {
        TaskId rs2TaskId = null;
        if (networkNode != null && networkNode.getNetwork() != null) {
            var rs2Tasks = networkNode.getTaskSnapshots();
            if (!rs2Tasks.isEmpty()) {
                rs2TaskId = rs2Tasks.get(rs2Tasks.size() - 1).id();
            }
        }

        TaskSourceInfo info = new TaskSourceInfo(itemStack, sourceId, rs2TaskId);
        return info;
    }

    private void extractMatterForTask(MatterPattern pattern, int count) {
        var matterCompound = ReplicationCalculation.getMatterCompound(pattern.getStack());
        if (matterCompound != null) {
            for (MatterValue matterValue : matterCompound.getValues().values()) {
                var matterType = matterValue.getMatter();
                var matterAmount = (long)Math.ceil(matterValue.getAmount()) * count;
                
                Item matterItem = getItemForMatterType(matterType);
                if (matterItem != null) {
                    matterItemsStorage.extractVirtual(matterItem, matterAmount);
                }
            }
        }
    }

    private void updateRS2Patterns() {
        if (!isActive() || networkNode == null) {
            return;
        }
        final List<ReplicationPatternTemplate> templates = collectReplicationTemplates();
        networkNode.updatePatterns(templates);
    }

    private void transferItemsToRS2() {
        if (networkNode == null || networkNode.getNetwork() == null) {
            return;
        }
        
        Network network = networkNode.getNetwork();
        StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
        if (storage == null) {
            return;
        }
        
        boolean itemsMoved = false;
        
        for (int i = 0; i < output.getSlots(); i++) {
            ItemStack stack = output.getStackInSlot(i);
            if (stack.isEmpty()) {
                continue;
            }
            
            ItemResource resource = ItemResource.ofItemStack(stack);
            long inserted = storage.insert(resource, stack.getCount(), Action.EXECUTE, Actor.EMPTY);
            
            if (inserted > 0) {
                stack.shrink((int)inserted);
                itemsMoved = true;
            }
        }
        
        if (itemsMoved) {
            this.setChanged();
        }
    }

    public boolean receiveItemFromReplicator(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        
        boolean insertedIntoRS2 = false;
        long remainingCount = stack.getCount();
        
        if (networkNode != null && networkNode.getNetwork() != null) {
            Network network = networkNode.getNetwork();
            StorageNetworkComponent storage = network.getComponent(StorageNetworkComponent.class);
            if (storage != null) {
                ItemResource resource = ItemResource.ofItemStack(stack);
                long inserted = storage.insert(resource, stack.getCount(), Action.EXECUTE, Actor.EMPTY);
                
                if (inserted > 0) {
                    for (int i = 0; i < output.getSlots(); i++) {
                        ItemStack slotStack = output.getStackInSlot(i);
                        if (ItemStack.isSameItem(slotStack, stack)) {
                            int toRemove = (int) Math.min(inserted, slotStack.getCount());
                            slotStack.shrink(toRemove);
                            inserted -= toRemove;
                            if (inserted <= 0) break;
                        }
                    }
                    
                    remainingCount -= inserted;
                    insertedIntoRS2 = true;
                    
                    if (remainingCount <= 0) {
                        return true;
                    }
                }
            }
        }
        
        if (remainingCount > 0) {
            ItemStack remainingStack = stack.copy();
            remainingStack.setCount((int)remainingCount);
            
            ItemStack notInserted = ItemHandlerHelper.insertItem(this.output, remainingStack, false);
            
            if (notInserted.isEmpty()) {
                this.setChanged();
                return true;
            }
            
            if (notInserted.getCount() < remainingCount) {
                this.setChanged();
                return insertedIntoRS2 || (notInserted.getCount() < stack.getCount());
            }
            
            return insertedIntoRS2;
        }
        
        return true;
    }

    private boolean hasRequiredMatter(final ReplicationPatternTemplate template) {
        final MatterNetwork network = getNetwork();
        if (network == null) {
            return false;
        }
        for (Map.Entry<IMatterType, Long> entry : template.matterCost().entrySet()) {
            final long available = network.calculateMatterAmount(entry.getKey());
            if (available < entry.getValue()) {
                return false;
            }
        }
        return true;
    }

    private void queuePatternRequest(final ReplicationPatternTemplate template) {
        final UUID sourceId = blockId;
        final ItemStack output = template.outputStack().copy();

        final Map<ItemWithSourceId, Integer> counters = requestCounters.computeIfAbsent(sourceId, id -> new HashMap<>());
        final ItemWithSourceId key = new ItemWithSourceId(output, sourceId);
        counters.merge(key, output.getCount(), Integer::sum);

        final Map<ItemStack, Integer> sourceRequests = patternRequestsBySource.computeIfAbsent(sourceId, id -> new HashMap<>());
        sourceRequests.merge(output, 1, Integer::sum);

        final Map<ItemStack, Integer> globalRequests = patternRequests.computeIfAbsent(sourceId, id -> new HashMap<>());
        globalRequests.merge(output, 1, Integer::sum);

        // Fast-track processing so tasks are created on the next tick.
        requestCounterTicks = REQUEST_ACCUMULATION_TICKS;
    }

    private List<ReplicationPatternTemplate> collectReplicationTemplates() {
        final MatterNetwork network = getNetwork();
        if (network == null || level == null || level.isClientSide()) {
            return List.of();
        }
        final List<ReplicationPatternTemplate> templates = new ArrayList<>();
        for (NetworkElement element : network.getChipSuppliers()) {
            final var tile = element.getLevel().getBlockEntity(element.getPos());
            if (tile instanceof ChipStorageBlockEntity chipStorage) {
                for (MatterPattern pattern : chipStorage.getPatterns(level, chipStorage)) {
                    if (pattern.getStack().isEmpty() || pattern.getCompletion() != 1) {
                        continue;
                    }
                    final Map<IMatterType, Long> cost = calculateMatterCost(pattern.getStack());
                    if (cost.isEmpty()) {
                        continue;
                    }
                    final ItemStack output = pattern.getStack().copy();
                    final PatternSignature signature = PatternSignature.from(output, cost);
                    templates.add(new ReplicationPatternTemplate(signature, output, pattern, cost));
                }
            }
        }
        return templates;
    }

    private Map<IMatterType, Long> calculateMatterCost(final ItemStack stack) {
        final Map<IMatterType, Long> cost = new HashMap<>();
        final var compound = ReplicationCalculation.getMatterCompound(stack);
        if (compound == null) {
            return cost;
        }
        for (MatterValue value : compound.getValues().values()) {
            final IMatterType matterType = value.getMatter();
            if (matterType == null) {
                continue;
            }
            final long amount = (long) Math.ceil(value.getAmount());
            if (amount > 0) {
                cost.merge(matterType, amount, Long::sum);
            }
        }
        return cost;
    }

    public ExternalPatternSink.Result handlePatternRequest(final ReplicationPatternTemplate template,
                                                           final Collection<ResourceAmount> resources,
                                                           final Action action) {
        if (template == null || worldUnloading || initialized != 1) {
            return ExternalPatternSink.Result.REJECTED;
        }
        if (!hasRequiredMatter(template)) {
            return ExternalPatternSink.Result.REJECTED;
        }
        if (action == Action.SIMULATE) {
            return ExternalPatternSink.Result.ACCEPTED;
        }
        queuePatternRequest(template);
        return ExternalPatternSink.Result.ACCEPTED;
    }

    @Override
    public MatterNetwork getNetwork() {
        if (level == null || level.isClientSide()) {
            return null;
        }
        
        if (worldUnloading) {
            return null;
        }
        
        try {
            NetworkManager networkManager = NetworkManager.get(level);
            if (networkManager == null) {
                return null;
            }
            NetworkElement element = networkManager.getElement(worldPosition);
            if (element == null) {
                if (worldUnloading) {
                    return null;
                }
                
                element = createElement(level, worldPosition);
                if (element != null) {
                    networkManager.addElement(element);
                }
            }
            if (element != null && element.getNetwork() instanceof MatterNetwork matterNetwork) {
                return matterNetwork;
            }
        } catch (Exception e) {
            LOGGER.error("Bridge: Exception accessing Replication network: {}", e.getMessage());
        }
        return null;
    }

    public boolean isActive() {
        if (worldUnloading) {
            return false;
        }
        
        try {
            return networkNode != null && networkNode.isActive() && networkNode.getNetwork() != null;
        } catch (Exception e) {
            return false;
        }
    }

    public void onNetworkActivityChanged(final boolean active) {
        if (Config.enableDebugLogging) {
            LOGGER.debug("Bridge {} activity changed: {}", worldPosition, active);
        }
        updateConnectedState();
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (blockId != null) {
            tag.putUUID("BlockId", blockId);
        }
        saveLocalRequestState(tag, registries);
        saveLocalActiveTasks(tag, registries);
        saveTaskSnapshots(tag, registries);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("BlockId")) {
            blockId = tag.getUUID("BlockId");
        } else {
            blockId = UUID.randomUUID();
        }
        nodeCreated = false;
        shouldReconnect = true;
        loadLocalRequestState(tag, registries);
        loadLocalActiveTasks(tag, registries);
        loadTaskSnapshots(tag, registries);
        requestCounterTicks = REQUEST_ACCUMULATION_TICKS;
        needsTaskRescan = true;
    }

    @Override
    public void setRemoved() {
        activeBridges.remove(this);
        
        try {
            if (nodeContainer != null) {
                RefinedStorageApi.INSTANCE.removeNetworkNodeContainer(nodeContainer, level);
            }
        } catch (Exception e) {
            LOGGER.error("Bridge: Exception destroying RS2 node: {}", e.getMessage());
        }
        
        super.setRemoved();
        nodeCreated = false;
        shouldReconnect = false;
    }

    @Override
    public void onChunkUnloaded() {
        try {
            if (nodeContainer != null) {
                RefinedStorageApi.INSTANCE.removeNetworkNodeContainer(nodeContainer, level);
            }
        } catch (Exception e) {
            LOGGER.error("Bridge: Exception in onChunkUnloaded: {}", e.getMessage());
        }
        
        super.onChunkUnloaded();
        nodeCreated = false;
        shouldReconnect = false;
    }

    public void disconnectFromNetworks() {
        if (Config.enableDebugLogging) {
            LOGGER.info("Bridge: Disconnecting from RS2 and Replication networks at {}", worldPosition);
        }
        
        if (level != null && !level.isClientSide() && nodeContainer != null) {
            try {
                RefinedStorageApi.INSTANCE.removeNetworkNodeContainer(nodeContainer, level);
                nodeCreated = false;
                shouldReconnect = false;
            } catch (Exception e) {
                LOGGER.warn("Bridge: Error disconnecting from RS2 network: {}", e.getMessage());
            }
        }
        
        try {
            if (level != null && !level.isClientSide()) {
                NetworkManager networkManager = NetworkManager.get(level);
                if (networkManager != null) {
                    NetworkElement element = networkManager.getElement(worldPosition);
                    if (element != null) {
                        networkManager.removeElement(worldPosition);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Bridge: Error disconnecting from Replication network: {}", e.getMessage());
        }
        updateConnectedState();
    }

    public static void setWorldUnloading(boolean unloading) {
        if (unloading && !worldUnloading) {
            LOGGER.info("RepRS2Bridge: World unloading detected - All bridges will now shutdown");
            cancelAllPendingOperations();
            disconnectAllBridgesFromNetworks();
        }
        worldUnloading = unloading;
    }

    public static boolean isWorldUnloading() {
        return worldUnloading;
    }

    private static void cancelAllPendingOperations() {
        for (RepRS2BridgeBlockEntity bridge : new ArrayList<>(activeBridges)) {
            bridge.requestCounters.clear();
            bridge.patternRequests.clear();
            bridge.patternRequestsBySource.clear();
        }
    }

    private static void disconnectAllBridgesFromNetworks() {
        List<RepRS2BridgeBlockEntity> bridgesToDisconnect = new ArrayList<>(activeBridges);
        for (RepRS2BridgeBlockEntity bridge : bridgesToDisconnect) {
            try {
                bridge.disconnectFromNetworks();
            } catch (Exception e) {
                LOGGER.warn("RepRS2Bridge: Failed to disconnect bridge: {}", e.getMessage());
            }
        }
        activeBridges.clear();
    }

    Item getItemForMatterType(IMatterType type) {
        String name = type.getName();
        if (name.equalsIgnoreCase("earth")) return ModItems.EARTH_MATTER.get();
        if (name.equalsIgnoreCase("nether")) return ModItems.NETHER_MATTER.get();
        if (name.equalsIgnoreCase("organic")) return ModItems.ORGANIC_MATTER.get();
        if (name.equalsIgnoreCase("ender")) return ModItems.ENDER_MATTER.get();
        if (name.equalsIgnoreCase("metallic")) return ModItems.METALLIC_MATTER.get();
        if (name.equalsIgnoreCase("precious")) return ModItems.PRECIOUS_MATTER.get();
        if (name.equalsIgnoreCase("living")) return ModItems.LIVING_MATTER.get();
        if (name.equalsIgnoreCase("quantum")) return ModItems.QUANTUM_MATTER.get();
        return null;
    }

    private boolean isVirtualMatterItem(Item item) {
        return item == ModItems.EARTH_MATTER.get()
                || item == ModItems.NETHER_MATTER.get()
                || item == ModItems.ORGANIC_MATTER.get()
                || item == ModItems.ENDER_MATTER.get()
                || item == ModItems.METALLIC_MATTER.get()
                || item == ModItems.PRECIOUS_MATTER.get()
                || item == ModItems.LIVING_MATTER.get()
                || item == ModItems.QUANTUM_MATTER.get();
    }

    private IMatterType getMatterTypeForItem(Item item) {
        if (item == ModItems.EARTH_MATTER.get()) return ReplicationRegistry.Matter.EARTH.get();
        if (item == ModItems.NETHER_MATTER.get()) return ReplicationRegistry.Matter.NETHER.get();
        if (item == ModItems.ORGANIC_MATTER.get()) return ReplicationRegistry.Matter.ORGANIC.get();
        if (item == ModItems.ENDER_MATTER.get()) return ReplicationRegistry.Matter.ENDER.get();
        if (item == ModItems.METALLIC_MATTER.get()) return ReplicationRegistry.Matter.METALLIC.get();
        if (item == ModItems.PRECIOUS_MATTER.get()) return ReplicationRegistry.Matter.PRECIOUS.get();
        if (item == ModItems.LIVING_MATTER.get()) return ReplicationRegistry.Matter.LIVING.get();
        if (item == ModItems.QUANTUM_MATTER.get()) return ReplicationRegistry.Matter.QUANTUM.get();
        return null;
    }

    public InventoryComponent<RepRS2BridgeBlockEntity> getOutput() {
        return output;
    }

    public MatterItemsStorage getMatterStorage() {
        return matterItemsStorage;
    }

    @Override
    public ItemInteractionResult onActivated(Player player, InteractionHand hand, Direction facing, double hitX, double hitY, double hitZ) {
        return ItemInteractionResult.SUCCESS;
    }

    public UUID getBlockId() {
        return blockId;
    }

    public NetworkNode getNetworkNode() {
        return networkNode;
    }

    public void handleExternalIteration() {
        this.debugTickCounter = 0;
    }

    public void cancelReplicationTaskForRS2Task(final TaskId rs2TaskId) {
        if (blockId == null || level == null || level.isClientSide()) {
            return;
        }
        
        Map<String, TaskSourceInfo> sourceTasks = activeTasks.get(blockId);
        if (sourceTasks == null || sourceTasks.isEmpty()) {
            return;
        }
        
        List<String> replicationTaskIds = new ArrayList<>();
        for (Map.Entry<String, TaskSourceInfo> entry : sourceTasks.entrySet()) {
            TaskSourceInfo info = entry.getValue();
            if (info.getRs2TaskId() != null && info.getRs2TaskId().equals(rs2TaskId)) {
                replicationTaskIds.add(entry.getKey());
            }
        }
        
        if (!replicationTaskIds.isEmpty()) {
            MatterNetwork replicationNetwork = getNetwork();
            if (replicationNetwork != null && level instanceof ServerLevel serverLevel) {
                for (String replicationTaskId : replicationTaskIds) {
                    replicationNetwork.cancelTask(replicationTaskId, serverLevel);
                    sourceTasks.remove(replicationTaskId);
                }
                
                if (sourceTasks.isEmpty()) {
                    activeTasks.remove(blockId);
                }
                
                Map<ItemStack, Integer> sourceRequests = patternRequestsBySource.get(blockId);
                if (sourceRequests != null) {
                    sourceRequests.clear();
                }
                
                setChanged();
            }
        }
    }

    private void tickNetworkNode() {
        Network network = networkNode.getNetwork();
        if (network == null || worldUnloading) {
            if (networkNode.isActive()) {
                networkNode.setActive(false);
            }
            return;
        }
        final EnergyNetworkComponent energy = network.getComponent(EnergyNetworkComponent.class);
        final boolean hasEnergy = energy == null || energy.getStored() >= networkNode.getEnergyUsage();
        final boolean shouldBeActive = hasEnergy;
        if (networkNode.isActive() != shouldBeActive) {
            networkNode.setActive(shouldBeActive);
        }
        if (shouldBeActive) {
            networkNode.doWork();
        }
    }

    private void tickTaskSnapshotPersistence() {
        if (level == null || level.isClientSide()) {
            return;
        }
        final boolean hasTasks = networkNode.hasActiveTasks();
        if (hasTasks) {
            if (!hadActiveRsTasks) {
                markTaskSnapshotsDirty();
            } else if (++taskSnapshotSaveTicks >= TASK_SNAPSHOT_SAVE_INTERVAL) {
                markTaskSnapshotsDirty();
            }
        } else if (hadActiveRsTasks) {
            markTaskSnapshotsDirty();
        } else {
            taskSnapshotSaveTicks = 0;
        }
        hadActiveRsTasks = hasTasks;
    }

    private void markTaskSnapshotsDirty() {
        taskSnapshotSaveTicks = 0;
        setChanged();
    }

    public NetworkNodeContainerProvider getContainerProvider() {
        return containerProvider;
    }
    
    public class MatterItemsStorage implements Storage, CompositeAwareChild {
        private ParentComposite parentComposite;
        private final Map<ResourceKey, Long> cachedAmounts = new HashMap<>();

        @Override
        public long insert(ResourceKey resource, long amount, Action action, Actor actor) {
            return 0;
        }

        @Override
        public long extract(ResourceKey resource, long amount, Action action, Actor actor) {
            if (initialized != 1) {
                return 0;
            }

            if (actor instanceof PlayerActor) {
                // Prevent players from pulling virtual matter directly from the grid.
                return 0;
            }
            
            if (resource instanceof ItemResource itemResource) {
                Item item = itemResource.item();
                if (isVirtualMatterItem(item)) {
                    MatterNetwork network = getNetwork();
                    if (network != null) {
                        IMatterType matterType = getMatterTypeForItem(item);
                        if (matterType != null) {
                            long available = network.calculateMatterAmount(matterType);
                            long toExtract = Math.min(amount, available);
                            return toExtract;
                        }
                    }
                }
            }
            return 0;
        }

        public long extractVirtual(Item item, long amount) {
            if (isVirtualMatterItem(item)) {
                return amount;
            }
            return 0;
        }

        @Override
        public Collection<ResourceAmount> getAll() {
            if (initialized != 1) {
                return List.of();
            }
            
            MatterNetwork network = getNetwork();
            if (network != null) {
                List<ResourceAmount> amounts = new ArrayList<>();
                
                List<IMatterType> matterTypes = List.of(
                        ReplicationRegistry.Matter.METALLIC.get(),
                        ReplicationRegistry.Matter.EARTH.get(),
                        ReplicationRegistry.Matter.NETHER.get(),
                        ReplicationRegistry.Matter.ORGANIC.get(),
                        ReplicationRegistry.Matter.ENDER.get(),
                        ReplicationRegistry.Matter.PRECIOUS.get(),
                        ReplicationRegistry.Matter.QUANTUM.get(),
                        ReplicationRegistry.Matter.LIVING.get()
                );
                
                for (IMatterType matterType : matterTypes) {
                    long amount = network.calculateMatterAmount(matterType);
                    if (amount > 0) {
                        Item item = getItemForMatterType(matterType);
                        if (item != null) {
                            ItemResource resource = new ItemResource(item);
                            amounts.add(new ResourceAmount(resource, amount));
                        }
                    }
                }
                
                return amounts;
            }
            return List.of();
        }

        @Override
        public long getStored() {
            return getAll().stream().mapToLong(ResourceAmount::amount).sum();
        }

        @Override
        public void onAddedIntoComposite(ParentComposite parentComposite) {
            this.parentComposite = parentComposite;
            cachedAmounts.clear();
            for (ResourceAmount amount : getAll()) {
                cachedAmounts.put(amount.resource(), amount.amount());
            }
        }

        @Override
        public void onRemovedFromComposite(ParentComposite parentComposite) {
            this.parentComposite = null;
            cachedAmounts.clear();
        }

        @Override
        public Amount compositeInsert(ResourceKey resource, long amount, Action action, Actor actor) {
            return Amount.ZERO;
        }

        @Override
        public Amount compositeExtract(ResourceKey resource, long amount, Action action, Actor actor) {
            long extracted = extract(resource, amount, action, actor);
            if (extracted == 0) {
                return Amount.ZERO;
            }
            return new Amount(extracted, extracted);
        }

        public void refreshCache() {
            if (parentComposite == null || level == null || level.isClientSide()) {
                return;
            }
            Map<ResourceKey, Long> latest = new HashMap<>();
            for (ResourceAmount amount : getAll()) {
                latest.put(amount.resource(), amount.amount());
            }
            for (Map.Entry<ResourceKey, Long> entry : latest.entrySet()) {
                long previous = cachedAmounts.getOrDefault(entry.getKey(), 0L);
                long delta = entry.getValue() - previous;
                if (delta > 0) {
                    parentComposite.addToCache(entry.getKey(), delta);
                } else if (delta < 0) {
                    parentComposite.removeFromCache(entry.getKey(), -delta);
                }
            }
            for (ResourceKey previousKey : new HashSet<>(cachedAmounts.keySet())) {
                if (!latest.containsKey(previousKey)) {
                    long previous = cachedAmounts.get(previousKey);
                    if (previous > 0) {
                        parentComposite.removeFromCache(previousKey, previous);
                    }
                }
            }
            cachedAmounts.clear();
            cachedAmounts.putAll(latest);
        }
    }

    public static class ItemWithSourceId {
        private final ItemStack itemStack;
        private final UUID sourceId;

        public ItemWithSourceId(ItemStack itemStack, UUID sourceId) {
            this.itemStack = itemStack.copy();
            this.sourceId = sourceId;
        }

        public ItemStack getItemStack() {
            return itemStack;
        }

        public UUID getSourceId() {
            return sourceId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemWithSourceId that = (ItemWithSourceId) o;
            return ItemStack.matches(itemStack, that.itemStack) &&
                    Objects.equals(sourceId, that.sourceId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(itemStack.getItem(), sourceId);
        }
    }

    public static class TaskSourceInfo {
        private final ItemStack itemStack;
        private final UUID sourceId;
        private final TaskId rs2TaskId;

        public TaskSourceInfo(ItemStack itemStack, UUID sourceId) {
            this(itemStack, sourceId, null);
        }

        public TaskSourceInfo(ItemStack itemStack, UUID sourceId, TaskId rs2TaskId) {
            this.itemStack = itemStack.copy();
            this.sourceId = sourceId;
            this.rs2TaskId = rs2TaskId;
        }

        public ItemStack getItemStack() {
            return itemStack;
        }

        public UUID getSourceId() {
            return sourceId;
        }

        public TaskId getRs2TaskId() {
            return rs2TaskId;
        }
    }

    private void saveLocalRequestState(CompoundTag tag, HolderLookup.Provider registries) {
        if (blockId == null) {
            return;
        }
        Map<ItemWithSourceId, Integer> localCounters = requestCounters.get(blockId);
        if (localCounters != null && !localCounters.isEmpty()) {
            tag.put(TAG_LOCAL_REQUEST_COUNTERS, writeItemWithSourceList(localCounters, registries));
        }
        Map<ItemStack, Integer> localPatternRequests = patternRequests.get(blockId);
        if (localPatternRequests != null && !localPatternRequests.isEmpty()) {
            tag.put(TAG_LOCAL_PATTERN_REQUESTS, writeItemCountList(localPatternRequests, registries));
        }
        Map<ItemStack, Integer> localPatternRequestsBySource = patternRequestsBySource.get(blockId);
        if (localPatternRequestsBySource != null && !localPatternRequestsBySource.isEmpty()) {
            tag.put(TAG_LOCAL_PATTERN_REQUESTS_BY_SOURCE, writeItemCountList(localPatternRequestsBySource, registries));
        }
    }

    private void loadLocalRequestState(CompoundTag tag, HolderLookup.Provider registries) {
        if (blockId == null) {
            return;
        }
        requestCounters.remove(blockId);
        patternRequests.remove(blockId);
        patternRequestsBySource.remove(blockId);
        if (tag.contains(TAG_LOCAL_REQUEST_COUNTERS, Tag.TAG_LIST)) {
            Map<ItemWithSourceId, Integer> counters =
                readItemWithSourceList(tag.getList(TAG_LOCAL_REQUEST_COUNTERS, Tag.TAG_COMPOUND), registries);
            if (!counters.isEmpty()) {
                requestCounters.put(blockId, counters);
            }
        }
        if (tag.contains(TAG_LOCAL_PATTERN_REQUESTS, Tag.TAG_LIST)) {
            Map<ItemStack, Integer> requests =
                readItemCountList(tag.getList(TAG_LOCAL_PATTERN_REQUESTS, Tag.TAG_COMPOUND), registries);
            if (!requests.isEmpty()) {
                patternRequests.put(blockId, requests);
            }
        }
        if (tag.contains(TAG_LOCAL_PATTERN_REQUESTS_BY_SOURCE, Tag.TAG_LIST)) {
            Map<ItemStack, Integer> requests =
                readItemCountList(tag.getList(TAG_LOCAL_PATTERN_REQUESTS_BY_SOURCE, Tag.TAG_COMPOUND), registries);
            if (!requests.isEmpty()) {
                patternRequestsBySource.put(blockId, requests);
            }
        }
    }

    private void saveLocalActiveTasks(CompoundTag tag, HolderLookup.Provider registries) {
        if (blockId == null) {
            return;
        }
        Map<String, TaskSourceInfo> tasks = activeTasks.get(blockId);
        if (tasks != null && !tasks.isEmpty()) {
            tag.put(TAG_LOCAL_ACTIVE_TASKS, writeActiveTaskList(tasks, registries));
        }
    }

    private void loadLocalActiveTasks(CompoundTag tag, HolderLookup.Provider registries) {
        if (blockId == null) {
            return;
        }
        activeTasks.remove(blockId);
        if (tag.contains(TAG_LOCAL_ACTIVE_TASKS, Tag.TAG_LIST)) {
            Map<String, TaskSourceInfo> tasks =
                readActiveTaskList(tag.getList(TAG_LOCAL_ACTIVE_TASKS, Tag.TAG_COMPOUND), registries);
            if (!tasks.isEmpty()) {
                activeTasks.put(blockId, tasks);
            }
        }
    }

    private void saveTaskSnapshots(CompoundTag tag, HolderLookup.Provider registries) {
        List<TaskSnapshot> snapshots = networkNode.getTaskSnapshots();
        if (snapshots.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        for (TaskSnapshot snapshot : snapshots) {
            list.add(TaskSnapshotNbt.encode(snapshot));
        }
        tag.put(TAG_RS_TASK_SNAPSHOTS, list);
    }

    private void loadTaskSnapshots(CompoundTag tag, HolderLookup.Provider registries) {
        if (!tag.contains(TAG_RS_TASK_SNAPSHOTS, Tag.TAG_LIST)) {
            return;
        }
        List<TaskSnapshot> snapshots = new ArrayList<>();
        for (Tag element : tag.getList(TAG_RS_TASK_SNAPSHOTS, Tag.TAG_COMPOUND)) {
            snapshots.add(TaskSnapshotNbt.decode((CompoundTag) element));
        }
        if (!snapshots.isEmpty()) {
            networkNode.restoreTasks(snapshots);
        }
    }

    private ListTag writeItemWithSourceList(Map<ItemWithSourceId, Integer> map, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        map.forEach((key, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("Stack", key.getItemStack().saveOptional(registries));
            entry.putInt("Count", amount);
            entry.putUUID("Owner", key.getSourceId());
            list.add(entry);
        });
        return list;
    }

    private Map<ItemWithSourceId, Integer> readItemWithSourceList(ListTag list, HolderLookup.Provider registries) {
        Map<ItemWithSourceId, Integer> map = new HashMap<>();
        for (Tag element : list) {
            CompoundTag entry = (CompoundTag) element;
            ItemStack stack = ItemStack.parse(registries, entry.getCompound("Stack")).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }
            UUID owner = entry.contains("Owner") ? entry.getUUID("Owner") : blockId;
            int amount = entry.getInt("Count");
            map.put(new ItemWithSourceId(stack, owner), amount);
        }
        return map;
    }

    private ListTag writeItemCountList(Map<ItemStack, Integer> map, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        map.forEach((stack, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("Stack", stack.saveOptional(registries));
            entry.putInt("Count", amount);
            list.add(entry);
        });
        return list;
    }

    private Map<ItemStack, Integer> readItemCountList(ListTag list, HolderLookup.Provider registries) {
        Map<ItemStack, Integer> map = new HashMap<>();
        for (Tag element : list) {
            CompoundTag entry = (CompoundTag) element;
            ItemStack stack = ItemStack.parse(registries, entry.getCompound("Stack")).orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }
            map.put(stack, entry.getInt("Count"));
        }
        return map;
    }

    private ListTag writeActiveTaskList(Map<String, TaskSourceInfo> tasks, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        tasks.forEach((taskId, info) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("TaskId", taskId);
            entry.put("Stack", info.getItemStack().saveOptional(registries));
            if (info.getRs2TaskId() != null) {
                entry.putUUID("Rs2TaskId", info.getRs2TaskId().id());
            }
            list.add(entry);
        });
        return list;
    }

    private Map<String, TaskSourceInfo> readActiveTaskList(ListTag list, HolderLookup.Provider registries) {
        Map<String, TaskSourceInfo> map = new HashMap<>();
        for (Tag element : list) {
            CompoundTag entry = (CompoundTag) element;
            ItemStack stack = ItemStack.parse(registries, entry.getCompound("Stack")).orElse(ItemStack.EMPTY);
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

    private void relinkActiveTasksFromNetwork(MatterNetwork network) {
        if (blockId == null) {
            return;
        }
        Map<String, TaskSourceInfo> tasks = activeTasks.computeIfAbsent(blockId, id -> new HashMap<>());
        tasks.clear();
        network.getTaskManager().getPendingTasks().forEach((taskId, task) -> {
            if (task != null && task.getSource() != null && task.getSource().equals(worldPosition)) {
                tasks.put(taskId, new TaskSourceInfo(task.getReplicatingStack(), blockId));
            }
        });
    }

    private static final class TaskSnapshotNbt {
        private static final String TAG_ID = "Id";
        private static final String TAG_RESOURCE = "Resource";
        private static final String TAG_AMOUNT = "Amount";
        private static final String TAG_ACTOR = "Actor";
        private static final String TAG_NOTIFY_ACTOR = "NotifyActor";
        private static final String TAG_START_TIME = "StartTime";
        private static final String TAG_INITIAL_REQUIREMENTS = "InitialRequirements";
        private static final String TAG_INTERNAL_STORAGE = "InternalStorage";
        private static final String TAG_CANCELLED = "Cancelled";
        private static final String TAG_STATE = "State";
        private static final String TAG_COMPLETED_PATTERNS = "CompletedPatterns";
        private static final String TAG_PATTERNS = "Patterns";
        private static final String TAG_INGREDIENTS = "Ingredients";
        private static final String TAG_OUTPUTS = "Outputs";
        private static final String TAG_BYPRODUCTS = "Byproducts";
        private static final String TAG_PATTERN = "Pattern";
        private static final String TAG_PATTERN_TYPE = "PatternType";
        private static final String TAG_PATTERN_DATA = "PatternData";
        private static final String TAG_INTERNAL = "Internal";
        private static final String TAG_INTERNAL_PATTERN = "InternalPattern";
        private static final String TAG_EXTERNAL_PATTERN = "ExternalPattern";
        private static final String TAG_ROOT = "Root";
        private static final String TAG_INPUTS = "Inputs";
        private static final String TAG_ORIGINAL_ITERATIONS_REMAINING = "OriginalIterationsRemaining";
        private static final String TAG_ITERATIONS_REMAINING = "IterationsRemaining";
        private static final String TAG_EXPECTED_OUTPUTS = "ExpectedOutputs";
        private static final String TAG_SIMULATED_INPUTS = "SimulatedIterationInputs";
        private static final String TAG_ORIGINAL_ITERATIONS_TO_SEND = "OriginalIterationsToSend";
        private static final String TAG_ITERATIONS_TO_SEND = "IterationsToSend";
        private static final String TAG_ITERATIONS_RECEIVED = "IterationsReceived";
        private static final String TAG_INTERCEPTED = "InterceptedSinceLastStep";
        private static final String TAG_LAST_SINK_RESULT = "LastSinkResult";

        private TaskSnapshotNbt() {
        }

        private static CompoundTag encode(TaskSnapshot snapshot) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(TAG_ID, snapshot.id().id());
            tag.put(TAG_RESOURCE, encodeResourceKey(snapshot.resource()));
            tag.putLong(TAG_AMOUNT, snapshot.amount());
            if (snapshot.actor() instanceof PlayerActor playerActor) {
                tag.putString(TAG_ACTOR, playerActor.name());
            }
            tag.putBoolean(TAG_NOTIFY_ACTOR, snapshot.notifyActor());
            tag.putLong(TAG_START_TIME, snapshot.startTime());
            tag.put(TAG_INITIAL_REQUIREMENTS, encodeResourceList(snapshot.initialRequirements()));
            tag.put(TAG_INTERNAL_STORAGE, encodeResourceList(snapshot.internalStorage()));
            tag.putBoolean(TAG_CANCELLED, snapshot.cancelled());
            tag.putString(TAG_STATE, snapshot.state().name());
            ListTag completed = new ListTag();
            snapshot.completedPatterns().forEach(patternSnapshot -> completed.add(encodePatternSnapshot(patternSnapshot)));
            tag.put(TAG_COMPLETED_PATTERNS, completed);
            ListTag patterns = new ListTag();
            snapshot.patterns().forEach((pattern, patternSnapshot) -> {
                CompoundTag entry = new CompoundTag();
                entry.put(TAG_PATTERN, encodePattern(pattern));
                entry.put(TAG_PATTERN_DATA, encodePatternSnapshot(patternSnapshot));
                patterns.add(entry);
            });
            tag.put(TAG_PATTERNS, patterns);
            return tag;
        }

        private static TaskSnapshot decode(CompoundTag tag) {
            TaskId id = new TaskId(tag.getUUID(TAG_ID));
            ResourceKey resource = ResourceCodecs.CODEC.parse(NbtOps.INSTANCE, tag.getCompound(TAG_RESOURCE)).result().orElseThrow();
            long amount = tag.getLong(TAG_AMOUNT);
            Actor actor = tag.contains(TAG_ACTOR, Tag.TAG_STRING) ? new PlayerActor(tag.getString(TAG_ACTOR)) : Actor.EMPTY;
            boolean notifyActor = tag.getBoolean(TAG_NOTIFY_ACTOR);
            long startTime = tag.getLong(TAG_START_TIME);
            ResourceList initialRequirements = decodeResourceList(tag.getList(TAG_INITIAL_REQUIREMENTS, Tag.TAG_COMPOUND));
            ResourceList internalStorage = decodeResourceList(tag.getList(TAG_INTERNAL_STORAGE, Tag.TAG_COMPOUND));
            boolean cancelled = tag.getBoolean(TAG_CANCELLED);
            TaskState state = TaskState.valueOf(tag.getString(TAG_STATE));
            List<TaskSnapshot.PatternSnapshot> completed = new ArrayList<>();
            for (Tag completedTag : tag.getList(TAG_COMPLETED_PATTERNS, Tag.TAG_COMPOUND)) {
                completed.add(decodePatternSnapshot((CompoundTag) completedTag));
            }
            Map<Pattern, TaskSnapshot.PatternSnapshot> patterns = new HashMap<>();
            for (Tag patternTag : tag.getList(TAG_PATTERNS, Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) patternTag;
                Pattern pattern = decodePattern(entry.getCompound(TAG_PATTERN));
                TaskSnapshot.PatternSnapshot snapshot = decodePatternSnapshot(entry.getCompound(TAG_PATTERN_DATA));
                patterns.put(pattern, snapshot);
            }
            return new TaskSnapshot(
                id,
                resource,
                amount,
                actor,
                notifyActor,
                startTime,
                patterns,
                completed,
                initialRequirements,
                internalStorage,
                state,
                cancelled
            );
        }

        private static ListTag encodeResourceList(ResourceList list) {
            ListTag listTag = new ListTag();
            list.getAll().forEach(resource -> {
                CompoundTag entry = encodeResourceKey(resource);
                entry.putLong(TAG_AMOUNT, list.get(resource));
                listTag.add(entry);
            });
            return listTag;
        }

        private static ResourceList decodeResourceList(ListTag listTag) {
            MutableResourceList resourceList = MutableResourceListImpl.create();
            for (Tag element : listTag) {
                CompoundTag entry = (CompoundTag) element;
                ResourceKey resource = ResourceCodecs.CODEC.parse(NbtOps.INSTANCE, entry).result().orElseThrow();
                long amount = entry.getLong(TAG_AMOUNT);
                resourceList.add(resource, amount);
            }
            return resourceList;
        }

        private static CompoundTag encodeResourceKey(ResourceKey resource) {
            if (resource instanceof PlatformResourceKey platformResourceKey) {
                Tag encoded = ResourceCodecs.CODEC.encodeStart(NbtOps.INSTANCE, platformResourceKey)
                    .result()
                    .orElse(new CompoundTag());
                if (encoded instanceof CompoundTag compoundTag) {
                    return compoundTag;
                }
                throw new IllegalStateException("Expected CompoundTag while encoding resource key, got: "
                    + encoded.getClass().getSimpleName());
            }
            throw new IllegalStateException("Cannot encode non-platform resource key: " + resource);
        }

        private static CompoundTag encodePatternSnapshot(TaskSnapshot.PatternSnapshot snapshot) {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean(TAG_ROOT, snapshot.root());
            tag.put(TAG_PATTERN, encodePattern(snapshot.pattern()));
            ListTag ingredients = new ListTag();
            snapshot.ingredients().forEach((slot, inner) -> {
                CompoundTag ingredientTag = new CompoundTag();
                ingredientTag.putInt("Slot", slot);
                ListTag innerList = new ListTag();
                inner.forEach((resource, amount) -> {
                    CompoundTag entry = encodeResourceKey(resource);
                    entry.putLong(TAG_AMOUNT, amount);
                    innerList.add(entry);
                });
                ingredientTag.put(TAG_INPUTS, innerList);
                ingredients.add(ingredientTag);
            });
            tag.put(TAG_INGREDIENTS, ingredients);
            tag.putBoolean(TAG_INTERNAL, snapshot.internalPattern() != null);
            if (snapshot.internalPattern() != null) {
                tag.put(TAG_INTERNAL_PATTERN, encodeInternalPattern(snapshot.internalPattern()));
            } else if (snapshot.externalPattern() != null) {
                tag.put(TAG_EXTERNAL_PATTERN, encodeExternalPattern(snapshot.externalPattern()));
            }
            return tag;
        }

        private static TaskSnapshot.PatternSnapshot decodePatternSnapshot(CompoundTag tag) {
            boolean root = tag.getBoolean(TAG_ROOT);
            Pattern pattern = decodePattern(tag.getCompound(TAG_PATTERN));
            Map<Integer, Map<ResourceKey, Long>> ingredients = new HashMap<>();
            for (Tag ingredientTag : tag.getList(TAG_INGREDIENTS, Tag.TAG_COMPOUND)) {
                CompoundTag ingredient = (CompoundTag) ingredientTag;
                int slot = ingredient.getInt("Slot");
                Map<ResourceKey, Long> inner = new HashMap<>();
                for (Tag innerTag : ingredient.getList(TAG_INPUTS, Tag.TAG_COMPOUND)) {
                    CompoundTag entry = (CompoundTag) innerTag;
                    ResourceKey resource = ResourceCodecs.CODEC.parse(NbtOps.INSTANCE, entry).result().orElseThrow();
                    inner.put(resource, entry.getLong(TAG_AMOUNT));
                }
                ingredients.put(slot, inner);
            }
            if (tag.getBoolean(TAG_INTERNAL)) {
                TaskSnapshot.InternalPatternSnapshot internal = decodeInternalPattern(tag.getCompound(TAG_INTERNAL_PATTERN));
                return new TaskSnapshot.PatternSnapshot(root, pattern, ingredients, internal, null);
            }
            TaskSnapshot.ExternalPatternSnapshot external = decodeExternalPattern(tag.getCompound(TAG_EXTERNAL_PATTERN));
            return new TaskSnapshot.PatternSnapshot(root, pattern, ingredients, null, external);
        }

        private static CompoundTag encodePattern(Pattern pattern) {
            CompoundTag tag = new CompoundTag();
            tag.putUUID(TAG_ID, pattern.id());
            ListTag ingredients = new ListTag();
            for (Ingredient ingredient : pattern.layout().ingredients()) {
                ingredients.add(encodeIngredient(ingredient));
            }
            tag.put(TAG_INGREDIENTS, ingredients);
            ListTag outputs = new ListTag();
            for (ResourceAmount output : pattern.layout().outputs()) {
                outputs.add(ResourceCodecs.AMOUNT_CODEC.encodeStart(NbtOps.INSTANCE, output).result().orElse(new CompoundTag()));
            }
            tag.put(TAG_OUTPUTS, outputs);
            ListTag byproducts = new ListTag();
            for (ResourceAmount byproduct : pattern.layout().byproducts()) {
                byproducts.add(ResourceCodecs.AMOUNT_CODEC.encodeStart(NbtOps.INSTANCE, byproduct).result().orElse(new CompoundTag()));
            }
            tag.put(TAG_BYPRODUCTS, byproducts);
            tag.putString(TAG_PATTERN_TYPE, pattern.layout().type().name());
            return tag;
        }

        private static Pattern decodePattern(CompoundTag tag) {
            UUID id = tag.getUUID(TAG_ID);
            List<Ingredient> ingredients = new ArrayList<>();
            for (Tag ingredientTag : tag.getList(TAG_INGREDIENTS, Tag.TAG_COMPOUND)) {
                ingredients.add(decodeIngredient((CompoundTag) ingredientTag));
            }
            List<ResourceAmount> outputs = new ArrayList<>();
            for (Tag outputTag : tag.getList(TAG_OUTPUTS, Tag.TAG_COMPOUND)) {
                outputs.add(ResourceCodecs.AMOUNT_CODEC.parse(NbtOps.INSTANCE, outputTag).result().orElseThrow());
            }
            List<ResourceAmount> byproducts = new ArrayList<>();
            for (Tag byproductTag : tag.getList(TAG_BYPRODUCTS, Tag.TAG_COMPOUND)) {
                byproducts.add(ResourceCodecs.AMOUNT_CODEC.parse(NbtOps.INSTANCE, byproductTag).result().orElseThrow());
            }
            PatternType type = PatternType.valueOf(tag.getString(TAG_PATTERN_TYPE));
            PatternLayout layout = type == PatternType.INTERNAL
                ? PatternLayout.internal(ingredients, outputs, byproducts)
                : PatternLayout.external(ingredients, outputs);
            return new Pattern(id, layout);
        }

        private static CompoundTag encodeIngredient(Ingredient ingredient) {
            CompoundTag tag = new CompoundTag();
            tag.putLong(TAG_AMOUNT, ingredient.amount());
            ListTag inputs = new ListTag();
            ingredient.inputs().forEach(input -> inputs.add(encodeResourceKey(input)));
            tag.put(TAG_INPUTS, inputs);
            return tag;
        }

        private static Ingredient decodeIngredient(CompoundTag tag) {
            long amount = tag.getLong(TAG_AMOUNT);
            List<ResourceKey> inputs = new ArrayList<>();
            for (Tag inputTag : tag.getList(TAG_INPUTS, Tag.TAG_COMPOUND)) {
                inputs.add(ResourceCodecs.CODEC.parse(NbtOps.INSTANCE, inputTag).result().orElseThrow());
            }
            return new Ingredient(amount, inputs);
        }

        private static CompoundTag encodeInternalPattern(TaskSnapshot.InternalPatternSnapshot snapshot) {
            CompoundTag tag = new CompoundTag();
            tag.putLong(TAG_ORIGINAL_ITERATIONS_REMAINING, snapshot.originalIterationsRemaining());
            tag.putLong(TAG_ITERATIONS_REMAINING, snapshot.iterationsRemaining());
            return tag;
        }

        private static TaskSnapshot.InternalPatternSnapshot decodeInternalPattern(CompoundTag tag) {
            long original = tag.getLong(TAG_ORIGINAL_ITERATIONS_REMAINING);
            long remaining = tag.getLong(TAG_ITERATIONS_REMAINING);
            return new TaskSnapshot.InternalPatternSnapshot(original, remaining);
        }

        private static CompoundTag encodeExternalPattern(TaskSnapshot.ExternalPatternSnapshot snapshot) {
            CompoundTag tag = new CompoundTag();
            tag.put(TAG_EXPECTED_OUTPUTS, encodeResourceList(snapshot.expectedOutputs()));
            tag.put(TAG_SIMULATED_INPUTS, encodeResourceList(snapshot.simulatedIterationInputs()));
            tag.putLong(TAG_ORIGINAL_ITERATIONS_TO_SEND, snapshot.originalIterationsToSendToSink());
            tag.putLong(TAG_ITERATIONS_TO_SEND, snapshot.iterationsToSendToSink());
            tag.putLong(TAG_ITERATIONS_RECEIVED, snapshot.iterationsReceived());
            tag.putBoolean(TAG_INTERCEPTED, snapshot.interceptedAnythingSinceLastStep());
            if (snapshot.lastSinkResult() != null) {
                tag.putString(TAG_LAST_SINK_RESULT, snapshot.lastSinkResult().name());
            }
            return tag;
        }

        private static TaskSnapshot.ExternalPatternSnapshot decodeExternalPattern(CompoundTag tag) {
            ResourceList expectedOutputs = decodeResourceList(tag.getList(TAG_EXPECTED_OUTPUTS, Tag.TAG_COMPOUND));
            ResourceList simulatedInputs = decodeResourceList(tag.getList(TAG_SIMULATED_INPUTS, Tag.TAG_COMPOUND));
            long originalIterationsToSend = tag.getLong(TAG_ORIGINAL_ITERATIONS_TO_SEND);
            long iterationsToSend = tag.getLong(TAG_ITERATIONS_TO_SEND);
            long iterationsReceived = tag.getLong(TAG_ITERATIONS_RECEIVED);
            boolean intercepted = tag.getBoolean(TAG_INTERCEPTED);
            ExternalPatternSink.Result sinkResult = tag.contains(TAG_LAST_SINK_RESULT)
                ? ExternalPatternSink.Result.valueOf(tag.getString(TAG_LAST_SINK_RESULT))
                : null;
            return new TaskSnapshot.ExternalPatternSnapshot(
                expectedOutputs,
                simulatedInputs,
                originalIterationsToSend,
                iterationsToSend,
                iterationsReceived,
                intercepted,
                sinkResult,
                null
            );
        }
    }
}
