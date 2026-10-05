/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// A Midrange System or Integrated Midrange System: its block entity on the master (MidrangeSystemBlockEntity), ticking
// there; a click opens its control panel; a click with a diskette (tier 1) or a magazine (tier 2) puts it in a free slot.
public abstract class MidrangeHostBlock extends FootprintBlock {
    protected MidrangeHostBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MASTER ? new MidrangeSystemBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(PART) != Part.MASTER) {
            return null;
        }
        return level.isClientSide() ? createTickerHelper(type, ModBlockEntityTypes.MIDRANGE_SYSTEM.get(), MidrangeSystemBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntityTypes.MIDRANGE_SYSTEM.get(), MidrangeSystemBlockEntity::serverTick);
    }

    // Its control panel; sneaking with an empty hand, the last diskette in (or the magazine) comes out.
    @Override
    protected InteractionResult use(Level level, BlockPos master, BlockState state, Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(master) instanceof MidrangeSystemBlockEntity system)) {
            return InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            if (level.isClientSide()) {
                return InteractionResult.SUCCESS;
            }
            ItemStack out = system.ejectLast();
            if (out.isEmpty()) {
                return InteractionResult.PASS;
            }
            player.getInventory().placeItemBackInInventory(out, Prediction.SERVER_ONLY);
            return InteractionResult.SUCCESS;
        }
        if (!level.isClientSide()) {
            player.openMenu(system, system::writeOpening);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItem(ItemStack stack, Level level, BlockPos master, BlockState state, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (level.getBlockEntity(master) instanceof MidrangeSystemBlockEntity system && system.canTake(stack)) {
            if (!level.isClientSide()) {
                system.insert(stack);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }
}
