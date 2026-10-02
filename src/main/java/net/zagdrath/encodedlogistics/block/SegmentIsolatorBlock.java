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
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.block.cable.CableConnection;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.BlockNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;
import net.zagdrath.encodedlogistics.network.NetworkNodeHost;

// The Segment Isolator: an inline housing that splits a network in two. It connects only on the two ends of its AXIS
// (set from the face clicked, like a log) and the network stops there, so each side is its own network: lanes, storage,
// devices and energy never cross. It drains isolatorDrain FE/t from each side. ACTIVE (the amber light) while both ends
// are attached to something on a network, i.e. it's actually separating two segments.
public class SegmentIsolatorBlock extends Block implements NetworkNodeBlock {
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.AXIS;
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    // A 10x10x16 housing along the axis with a 12x12x2 collar round its middle.
    private static final VoxelShape SHAPE_Z = Shapes.or(Block.box(3, 3, 0, 13, 13, 16), Block.box(2, 2, 7, 14, 14, 9));
    private static final VoxelShape SHAPE_X = Shapes.or(Block.box(0, 3, 3, 16, 13, 13), Block.box(7, 2, 2, 9, 14, 14));
    private static final VoxelShape SHAPE_Y = Shapes.or(Block.box(3, 0, 3, 13, 16, 13), Block.box(2, 7, 2, 14, 9, 14));

    public SegmentIsolatorBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(AXIS, Direction.Axis.Z).setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(AXIS, ACTIVE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(AXIS, context.getClickedFace().getAxis());
        return state.setValue(ACTIVE, active(context.getLevel(), context.getClickedPos(), state));
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return RotatedPillarBlock.rotatePillar(state, rotation);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return side.getAxis() == state.getValue(AXIS) ? state.setValue(ACTIVE, active(level, pos, state)) : state;
    }

    // Both ends have a network block (a cable, controller, device...) connected to them.
    private static boolean active(BlockGetter level, BlockPos pos, BlockState state) {
        Direction.Axis axis = state.getValue(AXIS);
        return attached(level, pos, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE))
                && attached(level, pos, Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE));
    }

    private static boolean attached(BlockGetter level, BlockPos pos, Direction side) {
        BlockPos neighbourPos = pos.relative(side);
        BlockState neighbour = level.getBlockState(neighbourPos);
        if (neighbour.getBlock() instanceof NetworkCableBlock) {
            return NetworkCableBlock.connection(neighbour, side.getOpposite()) != CableConnection.NONE;
        }
        if (neighbour.getBlock() instanceof NetworkNodeBlock block) {
            return block.connectsOn(neighbour, side.getOpposite());
        }
        return neighbour.getBlock() instanceof NetworkControllerBlock || level.getBlockEntity(neighbourPos) instanceof NetworkNodeHost;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(AXIS)) {
            case X -> SHAPE_X;
            case Y -> SHAPE_Y;
            case Z -> SHAPE_Z;
        };
    }

    // --- Network ---

    @Override
    public boolean connectsOn(BlockState state, Direction side) {
        return side.getAxis() == state.getValue(AXIS);
    }

    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        Direction.Axis axis = state.getValue(AXIS);
        Set<Direction> ends = EnumSet.of(Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE),
                Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE));
        return new BlockNode(pos.immutable(), ends, Config.ISOLATOR_DRAIN.getAsDouble(), false);
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
}
