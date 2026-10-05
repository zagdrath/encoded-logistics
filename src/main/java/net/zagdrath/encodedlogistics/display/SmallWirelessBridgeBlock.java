/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.display;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.encodedlogistics.machine.MachineBridge;
import net.zagdrath.encodedlogistics.machine.MachineBridges;

// The Small Wireless Bridge (display handoff 7): a 10 x 10 x 4 module flush on a machine's face (FACING: toward the
// machine), with its LED (STATE: unlinked, linked, fault). The item places it in front of the face it's used on, and the
// bridge itself is kept with the machine (machine.MachineBridges, by the machine block's position), which sets its LED.
// Mining it (a pickaxe) drops it and takes the bridge off; so does breaking the machine.
public class SmallWirelessBridgeBlock extends Block {
    public enum State implements StringRepresentable {
        UNLINKED, LINKED, FAULT;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final EnumProperty<State> STATE = EnumProperty.create("state", State.class);
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        // 10 x 10 in the middle of the face, 4 deep against it.
        for (Direction facing : Direction.values()) {
            double[] min = { 3, 3, 3 }, max = { 13, 13, 13 };
            int axis = facing.getAxis().ordinal();
            boolean positive = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE;
            min[axis] = positive ? 12 : 0;
            max[axis] = positive ? 16 : 4;
            SHAPES.put(facing, Block.box(min[0], min[1], min[2], max[0], max[1], max[2]));
        }
    }

    public SmallWirelessBridgeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATE, State.UNLINKED));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATE);
    }

    // Against the face clicked.
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    // Gone (mined, or the machine broken): the bridge on the machine behind it comes off (the block's own loot is the item).
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        if (level.getBlockState(pos).is(this)) {
            return;
        }
        MachineBridge bridge = MachineBridges.at(level, pos.relative(state.getValue(FACING)));
        if (bridge != null && bridge.face() == state.getValue(FACING).getOpposite()) {
            MachineBridges.get(level).remove(level, bridge, false);
        }
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
