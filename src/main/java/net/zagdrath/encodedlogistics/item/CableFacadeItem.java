/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// A Cable Facade: a panel that covers one face of a cable. It carries the block whose look it copies
// (encodedlogistics:facade_target); without one it's blank. Mounting it is the cable's job (NetworkCableBlock).
public class CableFacadeItem extends Item {
    public CableFacadeItem(Item.Properties properties) {
        super(properties);
    }

    public static @Nullable BlockState target(ItemStack stack) {
        return stack.get(ModDataComponents.FACADE_TARGET.get());
    }

    public static ItemStack of(Item facade, BlockState target) {
        ItemStack stack = new ItemStack(facade);
        stack.set(ModDataComponents.FACADE_TARGET.get(), target);
        return stack;
    }

    // "Stone Cable Facade" once it copies a block.
    @Override
    public Component getName(ItemStack stack) {
        BlockState target = target(stack);
        return target != null ? Component.translatable("item.encodedlogistics.cable_facade.with", target.getBlock().getName())
                : super.getName(stack);
    }

    // Whether a facade can copy a block: a full, non-translucent cube drawn from its model, without a block entity, and not
    // on the config's blocklist.
    public static boolean canCopy(BlockState state) {
        if (state.hasBlockEntity() || state.getRenderShape() != RenderShape.MODEL || !state.canOcclude()
                || !Block.isShapeFullBlock(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO))) {
            return false;
        }
        String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
        return !Config.SPEC.isLoaded() || !Config.FACADE_BLOCKLIST.get().contains(id);
    }

    // The block an item would make a facade of, or null if it can't.
    public static @Nullable BlockState targetFor(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            BlockState state = blockItem.getBlock().defaultBlockState();
            return canCopy(state) ? state : null;
        }
        return null;
    }
}
