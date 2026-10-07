/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.zagdrath.encodedlogistics.blockentity.PowerInletBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.BlockNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// The Power Inlet: feeds FE into its network's energy (controllers first, then Capacitor Banks). FE goes in through the
// port on its FRONT only; cables connect on the other five faces. Placed with the port toward the player, like a
// dispenser. POWERED while it has received FE in the last second. Uses no lanes and drains nothing.
public class PowerInletBlock extends BaseEntityBlock implements NetworkNodeBlock {
    private static final MapCodec<PowerInletBlock> CODEC = simpleCodec(PowerInletBlock::new);

    @Override
    protected MapCodec<PowerInletBlock> codec() {
        return CODEC;
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public PowerInletBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getNearestLookingDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // --- Network ---

    @Override
    public boolean connectsOn(BlockState state, Direction side) {
        return side != state.getValue(FACING);
    }

    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        Set<Direction> sides = EnumSet.allOf(Direction.class);
        sides.remove(state.getValue(FACING));
        return new BlockNode(pos.immutable(), sides, 0, true);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
            ControllerStructures.get(serverLevel).markTopologyChanged();
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        ControllerStructures.get(level).markTopologyChanged();
    }

    // --- Block entity ---

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerInletBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntityTypes.POWER_INLET.get(), PowerInletBlockEntity::serverTick);
    }
}
