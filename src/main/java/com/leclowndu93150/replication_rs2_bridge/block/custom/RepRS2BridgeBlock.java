package com.leclowndu93150.replication_rs2_bridge.block.custom;

import com.buuz135.replication.block.MatterPipeBlock;
import com.hrznstudio.titanium.block.BasicTileBlock;
import com.hrznstudio.titanium.block_network.INetworkDirectionalConnection;
import com.leclowndu93150.replication_rs2_bridge.block.entity.RepRS2BridgeBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RepRS2BridgeBlock extends BasicTileBlock<RepRS2BridgeBlockEntity> implements INetworkDirectionalConnection {
    public static final VoxelShape SHAPE = box(0, 0, 0, 16, 16, 16);
    public static final MapCodec<RepRS2BridgeBlock> CODEC = simpleCodec(RepRS2BridgeBlock::new);
    public static final BooleanProperty CONNECTED = BooleanProperty.create("connected");

    public RepRS2BridgeBlock(Properties properties) {
        super(properties, RepRS2BridgeBlockEntity.class);
        registerDefaultState(this.getStateDefinition().any().setValue(CONNECTED, false));
    }
    
    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(CONNECTED);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected MapCodec<? extends BasicTileBlock<RepRS2BridgeBlockEntity>> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public BlockEntityType.BlockEntitySupplier<?> getTileEntityFactory() {
        return (pos, state) -> new RepRS2BridgeBlockEntity(pos, state);
    }
    
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @org.jetbrains.annotations.Nullable net.minecraft.world.entity.LivingEntity entity, ItemStack stack) {
        super.setPlacedBy(level, pos, state, entity, stack);
        
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RepRS2BridgeBlockEntity blockEntity) {
            blockEntity.handleNeighborChanged(pos);
            blockEntity.updateConnectedState();
        }
    }
    
    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block blockIn, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, blockIn, fromPos, isMoving);
        
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RepRS2BridgeBlockEntity blockEntity) {
            blockEntity.handleNeighborChanged(fromPos);
        }
    }
    
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof RepRS2BridgeBlockEntity blockEntity) {
                dropInventory(level, pos, blockEntity);
                blockEntity.disconnectFromNetworks();
            }
            level.removeBlockEntity(pos);
        }
        
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public boolean canConnect(Level level, BlockPos pos, BlockState state, Direction direction) {
        return true;
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        super.tick(state, level, pos, random);
        if (level.getBlockEntity(pos) instanceof RepRS2BridgeBlockEntity blockEntity) {
            try {
                blockEntity.onLoad();
            } catch (RuntimeException e) {
                if (e.getMessage() != null && e.getMessage().contains("network is null")) {
                    level.scheduleTick(pos, this, 20);
                }
            }
        }
    }

    private void dropInventory(Level level, BlockPos pos, RepRS2BridgeBlockEntity blockEntity) {
        var inventory = blockEntity.getOutput();
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack.copy());
                stack.setCount(0);
            }
        }
    }
}
