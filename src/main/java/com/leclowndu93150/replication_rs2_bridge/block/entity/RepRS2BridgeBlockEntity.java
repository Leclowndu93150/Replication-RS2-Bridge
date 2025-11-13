package com.leclowndu93150.replication_rs2_bridge.block.entity;

import com.buuz135.replication.api.IMatterType;
import com.buuz135.replication.api.pattern.MatterPattern;
import com.buuz135.replication.block.tile.ChipStorageBlockEntity;
import com.buuz135.replication.block.tile.ReplicationMachine;
import com.buuz135.replication.block.MatterPipeBlock;
import com.buuz135.replication.calculation.MatterValue;
import com.buuz135.replication.calculation.ReplicationCalculation;
import com.buuz135.replication.network.DefaultMatterNetworkElement;
import com.buuz135.replication.network.MatterNetwork;
import com.hrznstudio.titanium.annotation.Save;
import com.hrznstudio.titanium.block.BasicTileBlock;
import com.hrznstudio.titanium.block_network.NetworkManager;
import com.hrznstudio.titanium.block_network.element.NetworkElement;
import com.hrznstudio.titanium.component.inventory.InventoryComponent;
import com.leclowndu93150.replication_rs2_bridge.block.ModBlocks;
import com.leclowndu93150.replication_rs2_bridge.block.custom.RepRS2BridgeBlock;
import com.leclowndu93150.replication_rs2_bridge.block.entity.lifecycle.Rs2NodeLifecycle;
import com.leclowndu93150.replication_rs2_bridge.block.entity.storage.MatterItemsStorage;
import com.leclowndu93150.replication_rs2_bridge.block.entity.pattern.PatternSignature;
import com.leclowndu93150.replication_rs2_bridge.block.entity.pattern.ReplicationPatternTemplate;
import com.leclowndu93150.replication_rs2_bridge.block.entity.task.ReplicationTaskHandler;
import com.leclowndu93150.replication_rs2_bridge.block.entity.task.TaskSnapshotNbt;
import com.leclowndu93150.replication_rs2_bridge.item.ModItems;
import com.mojang.logging.LogUtils;
import com.refinedmods.refinedstorage.api.autocrafting.task.ExternalPatternSink;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskId;
import com.refinedmods.refinedstorage.api.autocrafting.task.TaskSnapshot;
import com.refinedmods.refinedstorage.api.core.Action;
import com.refinedmods.refinedstorage.api.network.Network;
import com.refinedmods.refinedstorage.api.network.energy.EnergyNetworkComponent;
import com.refinedmods.refinedstorage.api.network.node.NetworkNode;
import com.refinedmods.refinedstorage.api.network.storage.StorageNetworkComponent;
import com.refinedmods.refinedstorage.api.resource.ResourceAmount;
import com.refinedmods.refinedstorage.api.storage.Actor;
import com.refinedmods.refinedstorage.common.api.RefinedStorageApi;
import com.refinedmods.refinedstorage.common.api.support.network.InWorldNetworkNodeContainer;
import com.refinedmods.refinedstorage.common.api.support.network.NetworkNodeContainerProvider;
import com.refinedmods.refinedstorage.common.support.network.ColoredConnectionStrategy;
import com.refinedmods.refinedstorage.common.support.resource.ItemResource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
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
import java.util.stream.Collectors;

/**
 * BlockEntity for the RepRS2Bridge that connects the RS2 network with the Replication matter network
 * Equivalent to RepAE2BridgeBlockEntity but for Refined Storage 2
 */
public class RepRS2BridgeBlockEntity extends ReplicationMachine<RepRS2BridgeBlockEntity> {

    private static final Logger LOGGER = LogUtils.getLogger();
    
    private static final int INITIALIZATION_DELAY = 60;
    private static final int PATTERN_UPDATE_INTERVAL = 100;
    private static final String TAG_RS_TASK_SNAPSHOTS = "RsTaskSnapshots";
    private static final String TAG_PATTERN_ID_MAPPINGS = "PatternIdMappings";
    private static final String TAG_PATTERN_ID = "PatternId";
    
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
    private final Rs2NodeLifecycle nodeLifecycle;
    
    private final MatterItemsStorage matterItemsStorage;
    private static final int TASK_SNAPSHOT_SAVE_INTERVAL = 20;
    private int taskSnapshotSaveTicks = 0;
    private boolean hadActiveRsTasks = false;
    
    private static boolean worldUnloading = false;
    private static final Set<RepRS2BridgeBlockEntity> activeBridges = new HashSet<>();
    private final Map<PatternSignature, UUID> patternIds = new HashMap<>();
    private final ReplicationTaskHandler taskHandler;

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
        
        this.networkNode = new RepRS2BridgeNetworkNode(this, 10);
        this.containerProvider = RefinedStorageApi.INSTANCE.createNetworkNodeContainerProvider();
        this.nodeContainer = RefinedStorageApi.INSTANCE.createNetworkNodeContainer(this, networkNode)
                .connectionStrategy(new ColoredConnectionStrategy(this::getBlockState, worldPosition))
                .build();
        this.containerProvider.addContainer(this.nodeContainer);
        this.nodeLifecycle = new Rs2NodeLifecycle(this, containerProvider, LOGGER);
        this.taskHandler = new ReplicationTaskHandler(this);
        this.matterItemsStorage = new MatterItemsStorage(this, taskHandler);
        
        this.initialized = 0;
        this.initializationTicks = 0;
        
        activeBridges.add(this);
    }

    @Override
    public void clearRemoved() {
        super.clearRemoved();
        if (level != null && !level.isClientSide()) {
            nodeLifecycle.requestInitialization("clear_removed");
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
        
        if (level != null && !level.isClientSide()) {
            nodeLifecycle.requestInitialization("on_load");
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
            
            nodeLifecycle.requestInitialization("neighbor_change");
            updateConnectedState();
        }
    }

    public void onRsNodeInitializedFromLifecycle() {
        updateConnectedState();
        forceNeighborUpdates();
        matterItemsStorage.refreshCache();
        patternUpdateTicks = PATTERN_UPDATE_INTERVAL;
        try {
            updateRS2Patterns(getNetwork());
        } catch (Exception patternEx) {
            LOGGER.error("Bridge: Pattern update failed during initialization", patternEx);
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
        
        nodeLifecycle.tick();
        
        MatterNetwork replicationNetwork = getNetwork();
        taskHandler.tick(replicationNetwork);
        
        if (initialized == 0) {
            initializationTicks++;
            if (initializationTicks >= INITIALIZATION_DELAY) {
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
            boolean updated = false;
            if (isActive()) {
                if (replicationNetwork != null) {
                    try {
                        updateRS2Patterns(replicationNetwork);
                        updated = true;
                    } catch (Exception e) {
                        LOGGER.error("Bridge: Exception during pattern update: {}", e.getMessage());
                    }
                }
            }
            patternUpdateTicks = updated ? 0 : PATTERN_UPDATE_INTERVAL;
        } else {
            patternUpdateTicks++;
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


    private void updateRS2Patterns(@Nullable final MatterNetwork replicationNetwork) {
        if (!isActive() || networkNode == null || replicationNetwork == null) {
            return;
        }
        final List<ReplicationPatternTemplate> templates = collectReplicationTemplates(replicationNetwork);
        if (templates.isEmpty()) {
            patternIds.clear();
        } else {
            patternIds.keySet().retainAll(templates.stream().map(ReplicationPatternTemplate::signature).collect(Collectors.toSet()));
        }
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

    private List<ReplicationPatternTemplate> collectReplicationTemplates(final MatterNetwork network) {
        if (level == null || level.isClientSide()) {
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
        taskHandler.queuePatternRequest(template);
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

    public boolean isBridgeInitialized() {
        return initialized == 1;
    }

    public void onNetworkActivityChanged(final boolean active) {
        updateConnectedState();
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (blockId != null) {
            tag.putUUID("BlockId", blockId);
        }
        taskHandler.saveLocalRequestState(tag, registries);
        taskHandler.saveLocalActiveTasks(tag, registries);
        saveTaskSnapshots(tag, registries);
        savePatternIdMappings(tag);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("BlockId")) {
            blockId = tag.getUUID("BlockId");
        } else {
            blockId = UUID.randomUUID();
        }
        nodeLifecycle.resetAfterDataLoad();
        taskHandler.loadLocalRequestState(tag, registries);
        taskHandler.loadLocalActiveTasks(tag, registries);
        loadTaskSnapshots(tag, registries);
        loadPatternIdMappings(tag);
        taskHandler.resetAfterDataLoad();
    }

    @Override
    public void setRemoved() {
        activeBridges.remove(this);
        if (!nodeLifecycle.isRemoved()) {
            nodeLifecycle.shutdown("set_removed", false);
        }
        super.setRemoved();
    }

    @Override
    public void onChunkUnloaded() {
        if (!nodeLifecycle.isRemoved()) {
            nodeLifecycle.shutdown("chunk_unloaded", true);
        }
        super.onChunkUnloaded();
    }

    public void disconnectFromNetworks() {
        if (!nodeLifecycle.isRemoved()) {
            nodeLifecycle.shutdown("manual_disconnect", false);
        }
        initialized = 0;
        initializationTicks = 0;
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
            bridge.taskHandler.clearPendingOperations();
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
        return ModItems.UNIVERSAL_MATTER.get();
    }

    private boolean isVirtualMatterItem(Item item) {
        return item == ModItems.UNIVERSAL_MATTER.get();
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

    public RepRS2BridgeNetworkNode getBridgeNetworkNode() {
        return networkNode;
    }

    public void handleExternalIteration() {
        this.debugTickCounter = 0;
    }

    public void cancelReplicationTaskForRS2Task(final TaskId rs2TaskId) {
        taskHandler.cancelReplicationTaskForRs2Task(rs2TaskId);
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

    UUID getOrCreatePatternId(final PatternSignature signature) {
        return patternIds.computeIfAbsent(signature, key -> UUID.randomUUID());
    }
    
    private void savePatternIdMappings(final CompoundTag tag) {
        if (patternIds.isEmpty()) {
            return;
        }
        ListTag list = new ListTag();
        patternIds.forEach((signature, id) -> {
            CompoundTag entry = signature.save();
            entry.putUUID(TAG_PATTERN_ID, id);
            list.add(entry);
        });
        tag.put(TAG_PATTERN_ID_MAPPINGS, list);
    }

    private void loadPatternIdMappings(final CompoundTag tag) {
        patternIds.clear();
        if (!tag.contains(TAG_PATTERN_ID_MAPPINGS, Tag.TAG_LIST)) {
            return;
        }
        ListTag list = tag.getList(TAG_PATTERN_ID_MAPPINGS, Tag.TAG_COMPOUND);
        for (Tag element : list) {
            CompoundTag entry = (CompoundTag) element;
            UUID patternId = entry.getUUID(TAG_PATTERN_ID);
            patternIds.put(PatternSignature.load(entry), patternId);
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


}
