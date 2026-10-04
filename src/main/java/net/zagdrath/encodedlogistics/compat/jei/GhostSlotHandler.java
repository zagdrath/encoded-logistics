/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.menu.GhostSlot;
import net.zagdrath.encodedlogistics.net.GhostSlotPayload;

// Dragging an item from JEI onto any of a screen's active ghost slots (port, tap and plane filters, the Gateway's stock
// list, the Threshold Sensor's item, the Schematic Encoder's grid and outputs) sets it as clicking with the item would.
final class GhostSlotHandler<T extends AbstractContainerScreen<?>> implements IGhostIngredientHandler<T> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(T screen, ITypedIngredient<I> ingredient, boolean doStart) {
        Optional<ItemStack> stack = ingredient.getItemStack();
        List<Target<I>> targets = new ArrayList<>();
        if (stack.isEmpty() || stack.get().isEmpty()) {
            return targets;
        }
        ItemStack item = stack.get().copyWithCount(1);
        for (Slot slot : screen.getMenu().slots) {
            if (slot instanceof GhostSlot && slot.isActive()) {
                Rect2i area = new Rect2i(screen.getLeftPos() + slot.x, screen.getTopPos() + slot.y, 16, 16);
                int containerId = screen.getMenu().containerId, index = slot.index;
                targets.add(new Target<>() {
                    @Override
                    public Rect2i getArea() {
                        return area;
                    }

                    @Override
                    public void accept(I ignored) {
                        ClientPacketDistributor.sendToServer(new GhostSlotPayload(containerId, index, item));
                    }
                });
            }
        }
        return targets;
    }

    @Override
    public void onComplete() {}
}
