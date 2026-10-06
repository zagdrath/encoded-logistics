/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackPermission;

// A signal device (lights, sirens, speakers handoff 1): mounted on any face of a block, FACING the way it points away
// from it (models built floor-mounted and rotated). It works from the redstone at its block on its own; cabled to a
// network (or placed against a networked block) it's also a device there - one lane, signalDeviceDrain FE/t, named by
// its type (LGT01, SRN01, SPK01). Right-click: its settings screen (SignalMenu), which on a network needs the
// Firewall's build permission. Its box is the model's main boxes, floor-mounted (shapes/collision_shapes.json), turned
// with FACING.
public abstract class SignalBlock extends BaseEntityBlock implements NetworkNodeBlock {
    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;

    private final Map<Direction, VoxelShape> shapes = new EnumMap<>(Direction.class);

    // floorBoxes: { x0, y0, z0, x1, y1, z1 } in px, standing on the floor.
    protected SignalBlock(BlockBehaviour.Properties properties, double[]... floorBoxes) {
        super(properties);
        for (Direction facing : Direction.values()) {
            VoxelShape shape = Shapes.empty();
            for (double[] box : floorBoxes) {
                shape = Shapes.or(shape, turned(box, facing));
            }
            shapes.put(facing, shape.optimize());
        }
    }

    // A floor box pointing the way facing does: its height along facing's axis (from the face it's mounted on), its
    // width and depth across it (every model is square across, so how they're turned doesn't matter).
    static VoxelShape turned(double[] box, Direction facing) {
        double[] min = new double[3], max = new double[3];
        int axis = facing.getAxis().ordinal();
        boolean positive = facing.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        // The two axes across take the box's x and z extents.
        int across = 0;
        for (int i = 0; i < 3; i++) {
            if (i != axis) {
                min[i] = across == 0 ? box[0] : box[2];
                max[i] = across == 0 ? box[3] : box[5];
                across++;
            }
        }
        min[axis] = positive ? box[1] : 16 - box[4];
        max[axis] = positive ? box[4] : 16 - box[1];
        return Block.box(min[0], min[1], min[2], max[0], max[1], max[2]);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    // On the face clicked, pointing away from it.
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapes.get(state.getValue(FACING));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // --- Redstone ---

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SignalBlockEntity device) {
            device.redstoneChanged();
        }
    }

    // --- Settings ---

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (level.getBlockEntity(pos) instanceof SignalBlockEntity && NetworkAccess.check(serverLevel, pos, player, RackPermission.BUILD)) {
            SignalMenu.open(serverPlayer, pos, getName());
        }
        return InteractionResult.SUCCESS;
    }

    // --- Network ---

    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        return new DeviceNode(pos.immutable(), EnumSet.allOf(Direction.class), 1, Config.SIGNAL_DEVICE_DRAIN.getAsDouble(), List.of(), false);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
            ControllerStructures.get(serverLevel).markTopologyChanged();
            if (level.getBlockEntity(pos) instanceof SignalBlockEntity device) {
                device.redstoneChanged();
            }
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        ControllerStructures.get(level).markTopologyChanged();
    }

    // --- Block entity ---

    // Its block entity type (the ticker runs on both sides: the client's plays the sounds).
    protected abstract BlockEntityType<? extends SignalBlockEntity> blockEntityType();

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return type == blockEntityType() ? (lvl, pos, st, be) -> ((SignalBlockEntity) be).tick() : null;
    }
}
