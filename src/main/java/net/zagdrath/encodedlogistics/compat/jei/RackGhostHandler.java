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
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.client.screen.RackScreen;

// Dragging an item from JEI onto a filter box in the Server Rack's screen (a Router or L3 Switch route, an L3 Switch QoS
// rule) sets that filter to the item: the open panel's RackScreen#ghostTargets.
final class RackGhostHandler implements IGhostIngredientHandler<RackScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(RackScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
        Optional<ItemStack> stack = ingredient.getItemStack();
        List<Target<I>> targets = new ArrayList<>();
        if (stack.isEmpty() || stack.get().isEmpty()) {
            return targets;
        }
        for (RackScreen.GhostTarget target : screen.ghostTargets()) {
            Rect2i area = new Rect2i(target.x(), target.y(), target.width(), target.height());
            ItemStack item = stack.get();
            targets.add(new Target<>() {
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(I ignored) {
                    target.accept().accept(item);
                }
            });
        }
        return targets;
    }

    @Override
    public void onComplete() {}
}
