/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.encodedlogistics.blockentity.SwivelChairBlockEntity;
import net.zagdrath.encodedlogistics.entity.SeatEntity;

// The Swivel Chair: a five-star base (the block model) under a seat and back that turn (drawn by its block entity's
// renderer at the chair's yaw). Placed facing the way its placer looks; right-click to sit (on a SeatEntity), and the
// chair turns with its sitter; sneak to get up. One sitter at a time. Dye it in a crafting grid (dyed_color).
public class SwivelChairBlock extends BaseEntityBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 2, 15), Block.box(7, 2, 7, 9, 7, 9), Block.box(2.5, 6, 2.5, 13.5, 9, 13.5));

    public SwivelChairBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer != null && level.getBlockEntity(pos) instanceof SwivelChairBlockEntity chair) {
            chair.setYaw(placer.getYRot());
        }
    }

    // Sits the player down, unless someone already is.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (player.isPassenger() || !(level.getBlockEntity(pos) instanceof SwivelChairBlockEntity chair)) {
            return InteractionResult.PASS;
        }
        if (!level.getEntitiesOfClass(SeatEntity.class, new AABB(pos)).stream().allMatch(seat -> seat.getPassengers().isEmpty())) {
            return InteractionResult.PASS;
        }
        SeatEntity seat = SeatEntity.at(level, pos);
        level.addFreshEntity(seat);
        chair.setYaw(player.getYRot());
        player.startRiding(seat);
        return InteractionResult.SUCCESS;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SwivelChairBlockEntity(pos, state);
    }
}
