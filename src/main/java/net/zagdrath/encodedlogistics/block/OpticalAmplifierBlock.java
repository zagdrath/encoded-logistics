/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block;

import java.util.EnumSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.OpticalAmplifierBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.TopologyCause;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;
import net.zagdrath.encodedlogistics.optical.OpticalState;
import net.zagdrath.encodedlogistics.optical.SpanLoss;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// The Optical Amplifier (the optical handoff 3): an 8 x 8 x 16 housing inline on a Fiber Cable run, along FACING's axis
// (light comes in at the back and leaves at the front). Powered, it ends the span it's on, so span loss counts again
// from it (SpanLoss); unpowered it passes the light through and the run goes on through it. It's powered while it's
// online on a network, or while an online Optical Multiplexer or Transport Shelf whose line runs through it powers the
// line (OpticalAmplifierBlockEntity#linePowered). STATE: off, on (amplifying) or fault (input loss of signal: no fibre at
// its back).
public class OpticalAmplifierBlock extends BaseEntityBlock implements NetworkNodeBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<OpticalState> STATE = OpticalState.PROPERTY;
    private static final VoxelShape ALONG_Z = Block.box(4, 4, 0, 12, 12, 16), ALONG_X = Block.box(0, 4, 4, 16, 12, 12);

    public OpticalAmplifierBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATE, OpticalState.OFF));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATE);
    }

    // Along the way the player looks: the light goes away from them.
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection());
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

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? ALONG_Z : ALONG_X;
    }

    // Cables join it at its two ends only.
    @Override
    public boolean connectsOn(BlockState state, Direction side) {
        return side.getAxis() == state.getValue(FACING).getAxis();
    }

    // On the network: lanes pass through it as through a Fiber Cable; it takes one for itself, and a powered one ends
    // its span.
    public record AmplifierNode(BlockPos pos, Set<Direction> connections, boolean amplifying) implements NetworkNode {
        @Override
        public int laneCost() {
            return 1;
        }

        @Override
        public double passiveDrain() {
            return Config.AMPLIFIER_DRAIN.getAsDouble();
        }

        @Override
        public int laneCapacity() {
            return SpanLoss.fiberLanes();
        }

        @Override
        public Optical optical() {
            return amplifying ? Optical.AMPLIFIER : Optical.FIBER;
        }
    }

    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FACING);
        return new AmplifierNode(pos.immutable(), EnumSet.of(facing, facing.getOpposite()), state.getValue(STATE) == OpticalState.ON);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
            ControllerStructures.topologyChanged(serverLevel, pos, TopologyCause.BLOCK);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        ControllerStructures.topologyChanged(level, pos, TopologyCause.BLOCK);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OpticalAmplifierBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntityTypes.OPTICAL_AMPLIFIER.get(),
                (tickLevel, pos, tickState, amplifier) -> amplifier.serverTick((ServerLevel) tickLevel));
    }
}
