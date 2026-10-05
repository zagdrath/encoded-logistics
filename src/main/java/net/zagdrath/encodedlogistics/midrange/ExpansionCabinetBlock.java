/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.encodedlogistics.midrange.FootprintBlock.Part;
import net.zagdrath.encodedlogistics.registry.ModBlocks;

// The Expansion Cabinet (HANDOFF 3): an upgrade for a Midrange System - one more thread, diskette slot and batch job, max
// job 128. It attaches when it stands directly beside a system along its width, facing the same way, one per system;
// ATTACHED is the side the system is on (its model sits flush against the system's overhang, so the pair reads as one
// 28 px machine) and the system shows EXPANSION. Anywhere else it does nothing. LIT: its lamp, while attached to a
// system that's on.
public class ExpansionCabinetBlock extends Block {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<MidrangeStates.Side> ATTACHED = MidrangeStates.ATTACHED;

    public ExpansionCabinetBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ATTACHED, MidrangeStates.Side.NONE)
                .setValue(MidrangeStates.LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ATTACHED, MidrangeStates.LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return MidrangeShapes.shape("expansion_cabinet[attached=" + state.getValue(ATTACHED).getSerializedName() + "]", Vec3i.ZERO, state.getValue(FACING));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
            refresh(serverLevel, pos);
        }
    }

    // Broken: its system has no cabinet now (unless one stands on its other side).
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Direction facing = state.getValue(FACING);
        BlockPos system = system(level, pos, state);
        if (system != null && isSystem(level, system, facing)) {
            level.setBlock(system, level.getBlockState(system).setValue(MidrangeStates.EXPANSION, false), Block.UPDATE_ALL);
            refreshAround(level, system, facing);
        }
    }

    // --- Attaching ---

    private static boolean isSystem(Level level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.is(ModBlocks.MIDRANGE_SYSTEM.get()) && state.getValue(FootprintBlock.PART) == Part.MASTER
                && state.getValue(FootprintBlock.FACING) == facing;
    }

    // The cabinets that could attach to a system (either side of it) look again.
    public static void refreshAround(ServerLevel level, BlockPos system, Direction facing) {
        Direction right = facing.getClockWise();
        for (BlockPos pos : new BlockPos[] { system.relative(right, -1), system.relative(right) }) {
            if (level.getBlockState(pos).getBlock() instanceof ExpansionCabinetBlock) {
                refresh(level, pos);
            }
        }
    }

    // A cabinet attaches to the system beside it (if that system has no cabinet yet: the system to its +x first), or
    // comes loose.
    public static void refresh(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ExpansionCabinetBlock)) {
            return;
        }
        Direction facing = state.getValue(FACING);
        Direction right = facing.getClockWise();
        MidrangeStates.Side attached = MidrangeStates.Side.NONE;
        BlockPos system = null;
        if (isSystem(level, pos.relative(right), facing) && free(level, pos.relative(right), pos, facing)) {
            attached = MidrangeStates.Side.POS;
            system = pos.relative(right);
        } else if (isSystem(level, pos.relative(right, -1), facing) && free(level, pos.relative(right, -1), pos, facing)) {
            attached = MidrangeStates.Side.NEG;
            system = pos.relative(right, -1);
        }
        if (state.getValue(ATTACHED) != attached) {
            level.setBlock(pos, state.setValue(ATTACHED, attached), Block.UPDATE_ALL);
        }
        if (system != null) {
            BlockState systemState = level.getBlockState(system);
            if (!systemState.getValue(MidrangeStates.EXPANSION)) {
                level.setBlock(system, systemState.setValue(MidrangeStates.EXPANSION, true), Block.UPDATE_ALL);
            }
        }
    }

    // Whether a system takes this cabinet: it has none, or this one.
    private static boolean free(Level level, BlockPos system, BlockPos cabinet, Direction facing) {
        BlockPos other = cabinetOf(level, system, facing);
        return other == null || other.equals(cabinet);
    }

    // The cabinet attached to a system (beside it, attached toward it), or null.
    public static @Nullable BlockPos cabinetOf(BlockGetter level, BlockPos system, Direction facing) {
        Direction right = facing.getClockWise();
        for (BlockPos pos : new BlockPos[] { system.relative(right, -1), system.relative(right) }) {
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof ExpansionCabinetBlock && state.getValue(FACING) == facing && system.equals(system(level, pos, state))) {
                return pos;
            }
        }
        return null;
    }

    // The system this cabinet is attached to, or null.
    public static @Nullable BlockPos system(BlockGetter level, BlockPos pos, BlockState state) {
        Direction right = state.getValue(FACING).getClockWise();
        return switch (state.getValue(ATTACHED)) {
            case POS -> pos.relative(right);
            case NEG -> pos.relative(right, -1);
            case NONE -> null;
        };
    }
}
