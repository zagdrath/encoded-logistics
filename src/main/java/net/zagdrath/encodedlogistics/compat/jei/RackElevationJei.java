/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.compat.jei;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.api.gui.handlers.IScreenHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.client.screen.RackElevationScreen;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;

// JEI and the Rack Elevation's editor (a plain screen, not a container): where it is, so JEI's list shows beside it
// (Bounds), and a rack device dragged from that list onto a free unit places it there (Ghosts).
final class RackElevationJei {
    private RackElevationJei() {}

    static final class Bounds implements IScreenHandler<RackElevationScreen> {
        @Override
        public IGuiProperties apply(RackElevationScreen screen) {
            return new IGuiProperties() {
                @Override
                public Class<? extends net.minecraft.client.gui.screens.Screen> screenClass() {
                    return RackElevationScreen.class;
                }

                @Override
                public int guiLeft() {
                    return screen.guiLeft();
                }

                @Override
                public int guiTop() {
                    return screen.guiTop();
                }

                @Override
                public int guiXSize() {
                    return screen.guiWidth();
                }

                @Override
                public int guiYSize() {
                    return screen.guiHeight();
                }

                @Override
                public int screenWidth() {
                    return screen.width;
                }

                @Override
                public int screenHeight() {
                    return screen.height;
                }
            };
        }
    }

    static final class Ghosts implements IGhostIngredientHandler<RackElevationScreen> {
        @Override
        public <I> List<Target<I>> getTargetsTyped(RackElevationScreen screen, ITypedIngredient<I> ingredient, boolean doStart) {
            Optional<ItemStack> stack = ingredient.getItemStack();
            List<Target<I>> targets = new ArrayList<>();
            if (stack.isEmpty() || RackDeviceType.of(stack.get()) == null) {
                return targets;
            }
            ItemStack item = stack.get();
            for (RackElevationScreen.GhostTarget target : screen.ghostTargets()) {
                Rect2i area = new Rect2i(target.x(), target.y(), target.width(), target.height());
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
}
