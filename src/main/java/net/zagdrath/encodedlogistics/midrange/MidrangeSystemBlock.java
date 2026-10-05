/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.EnumSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;

// The Midrange System (tier 1, HANDOFF 2): a waist-high cabinet 2 blocks wide (master and a dummy to its +x), one lane on
// the network from either block. STATE: its operator panel (off, IPL, run, busy, attention). EXPANSION: the side its
// Expansion Cabinet is on (none, pos, neg) - the model moves flush toward it, so the pair reads as one machine.
public class MidrangeSystemBlock extends FootprintBlock implements NetworkNodeBlock {
    private static final List<Vec3i> FOOTPRINT = List.of(Vec3i.ZERO, new Vec3i(1, 0, 0));

    public MidrangeSystemBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.MASTER)
                .setValue(MidrangeStates.STATE, MidrangeStates.Run.OFF).setValue(MidrangeStates.EXPANSION, MidrangeStates.Side.NONE));
    }

    public static int lightLevel(BlockState state) {
        return state.getValue(PART) == Part.MASTER && state.getValue(MidrangeStates.STATE) != MidrangeStates.Run.OFF ? 3 : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MidrangeStates.STATE, MidrangeStates.EXPANSION);
    }

    @Override
    protected List<Vec3i> footprint() {
        return FOOTPRINT;
    }

    @Override
    protected String shapeKey(BlockState state) {
        return "midrange_system[expansion=" + state.getValue(MidrangeStates.EXPANSION).getSerializedName() + "]";
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // Placed: an Expansion Cabinet already beside it attaches.
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && state.getValue(PART) == Part.MASTER && level instanceof ServerLevel serverLevel) {
            ExpansionCabinetBlock.refreshAround(serverLevel, pos, state.getValue(FACING));
        }
    }

    // Broken: its cabinet comes loose.
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (state.getValue(PART) == Part.MASTER) {
            ExpansionCabinetBlock.refreshAround(level, pos, state.getValue(FACING));
        }
    }

    // --- Network ---

    // The master is the device (a lane, its drain); the dummy passes lanes to it.
    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        boolean master = state.getValue(PART) == Part.MASTER;
        double drain = Config.MIDRANGE_DRAIN.getAsDouble() + (state.getValue(MidrangeStates.EXPANSION) != MidrangeStates.Side.NONE
                ? Config.EXPANSION_CABINET_DRAIN.getAsDouble() : 0);
        return new DeviceNode(pos.immutable(), EnumSet.allOf(Direction.class), master ? 1 : 0, master ? drain : 0, List.of(), true);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MASTER ? new MidrangeSystemBlockEntity(pos, state) : null;
    }
}
