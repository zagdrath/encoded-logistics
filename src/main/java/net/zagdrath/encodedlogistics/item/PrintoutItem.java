/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.zagdrath.encodedlogistics.client.ClientRenderHooks;
import net.zagdrath.encodedlogistics.midrange.Printout;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// A Printout (HANDOFF 9): what the Line Printer printed (its encodedlogistics:printout component). Held, its page fills
// the view as a map does; in an item frame, its first page shows; used, it opens in the reader. Its icon is one sheet,
// or a fan-fold stack from two pages (items/printout.json, on the printout_pages property).
public class PrintoutItem extends Item {
    public PrintoutItem(Item.Properties properties) {
        super(properties);
    }

    public static @Nullable Printout printout(ItemStack stack) {
        return stack.get(ModDataComponents.PRINTOUT.get());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (printout(stack) == null) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide()) {
            ClientRenderHooks.openPrintout(stack);
        }
        return InteractionResult.SUCCESS;
    }
}
