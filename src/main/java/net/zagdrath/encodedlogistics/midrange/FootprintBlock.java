/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;

// A Midrange-line block that takes more than one block of room (HANDOFF 1): a master where it was placed (the block
// clicked, its front toward the player) and dummies at the footprint's other offsets, model-local - +x across the width
// (the facing turned clockwise), +y up. The master shows the model (they're all static models), the dummies nothing.
// Placing needs the whole footprint free; breaking any block breaks the rest (the one broken drops the item). Shapes come
// from MidrangeShapes per footprint block; a click on any block is the master's.
public abstract class FootprintBlock extends BaseEntityBlock {
    public enum Part implements StringRepresentable {
        MASTER, DUMMY, MIDDLE, TOP;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    // Master or dummy; the Tape Drive's (TAPE_PART) master, middle or top.
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class, Part.MASTER, Part.DUMMY);
    public static final EnumProperty<Part> TAPE_PART = EnumProperty.create("part", Part.class, Part.MASTER, Part.MIDDLE, Part.TOP);

    protected FootprintBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    // The footprint's blocks, model-local, (0, 0, 0) the master's.
    protected abstract List<Vec3i> footprint();

    // Its key in MidrangeShapes ("line_printer", "midrange_system[expansion=pos]").
    protected abstract String shapeKey(BlockState state);

    // Its part property (PART, or the Tape Drive's TAPE_PART).
    public EnumProperty<Part> part() {
        return PART;
    }

    public boolean isMaster(BlockState state) {
        return state.getValue(part()) == Part.MASTER;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, part());
    }

    // Where a footprint offset is, from the master.
    public static BlockPos at(BlockPos master, Direction facing, Vec3i offset) {
        return master.relative(facing.getClockWise(), offset.getX()).above(offset.getY());
    }

    // The master of the footprint a block is in (itself for a master), or null when it's lost (a broken footprint).
    public @Nullable BlockPos master(BlockGetter level, BlockPos pos, BlockState state) {
        if (isMaster(state)) {
            return pos;
        }
        Direction facing = state.getValue(FACING);
        for (Vec3i offset : footprint()) {
            if (offset.equals(Vec3i.ZERO)) {
                continue;
            }
            BlockPos master = pos.relative(facing.getClockWise(), -offset.getX()).below(offset.getY());
            BlockState there = level.getBlockState(master);
            if (there.is(this) && isMaster(there) && there.getValue(FACING) == facing) {
                return master;
            }
        }
        return null;
    }

    // A block's offset in its footprint (ZERO for the master or a lost dummy).
    public Vec3i offset(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos master = master(level, pos, state);
        if (master == null || master.equals(pos)) {
            return Vec3i.ZERO;
        }
        Direction facing = state.getValue(FACING);
        for (Vec3i offset : footprint()) {
            if (at(master, facing, offset).equals(pos)) {
                return offset;
            }
        }
        return Vec3i.ZERO;
    }

    // --- Placing and breaking ---

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos master = context.getClickedPos();
        for (Vec3i offset : footprint()) {
            if (offset.equals(Vec3i.ZERO)) {
                continue;
            }
            BlockPos pos = at(master, facing, offset);
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.getBlockState(pos).canBeReplaced(context)) {
                if (context.getPlayer() instanceof ServerPlayer player) {
                    player.sendOverlayMessage(Component.translatable("message.encodedlogistics.midrange.no_room"));
                }
                return null;
            }
        }
        return placementState(defaultBlockState().setValue(FACING, facing).setValue(part(), Part.MASTER), context);
    }

    // The master's state as placed (a subclass sets its own properties).
    protected BlockState placementState(BlockState state, BlockPlaceContext context) {
        return state;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level.isClientSide()) {
            return;
        }
        for (Vec3i offset : footprint()) {
            if (!offset.equals(Vec3i.ZERO)) {
                level.setBlock(at(pos, state.getValue(FACING), offset), dummyState(state, offset), Block.UPDATE_ALL);
            }
        }
    }

    // A dummy's state, from the master's and its place in the footprint.
    protected BlockState dummyState(BlockState master, Vec3i offset) {
        return master.setValue(part(), Part.DUMMY);
    }

    // Breaking one block breaks the rest, without drops: the one broken drops the item.
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Direction facing = state.getValue(FACING);
        BlockPos master = isMaster(state) ? pos : null;
        if (master == null) {
            // The master is found from the blocks still standing.
            for (Vec3i offset : footprint()) {
                BlockPos candidate = pos.relative(facing.getClockWise(), -offset.getX()).below(offset.getY());
                BlockState there = level.getBlockState(candidate);
                if (there.is(this) && isMaster(there) && there.getValue(FACING) == facing) {
                    master = candidate;
                }
            }
        }
        if (master != null) {
            for (Vec3i offset : footprint()) {
                BlockPos other = at(master, facing, offset);
                BlockState there = level.getBlockState(other);
                if (!other.equals(pos) && there.is(this) && there.getValue(FACING) == facing) {
                    level.destroyBlock(other, false);
                }
            }
        }
        ControllerStructures.get(level).markTopologyChanged();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
            ControllerStructures.get(serverLevel).markTopologyChanged();
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

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return MidrangeShapes.shape(shapeKey(masterState(level, pos, state)), offset(level, pos, state), state.getValue(FACING));
    }

    // The master's state (a dummy's shape depends on it: the Midrange System's expansion).
    protected BlockState masterState(BlockGetter level, BlockPos pos, BlockState state) {
        BlockPos master = master(level, pos, state);
        BlockState there = master != null ? level.getBlockState(master) : state;
        // Its own state when the master isn't there (the shape cache asks with an empty level).
        return there.is(this) ? there : state;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // --- Using it ---

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos master = master(level, pos, state);
        return master == null ? InteractionResult.PASS : use(level, master, level.getBlockState(master), player, hit);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        BlockPos master = master(level, pos, state);
        return master == null ? InteractionResult.TRY_WITH_EMPTY_HAND : useItem(stack, level, master, level.getBlockState(master), player, hand, hit);
    }

    // A click on any of its blocks, as the master's.
    protected InteractionResult use(Level level, BlockPos master, BlockState state, Player player, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    protected InteractionResult useItem(ItemStack stack, Level level, BlockPos master, BlockState state, Player player, InteractionHand hand,
            BlockHitResult hit) {
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
}
