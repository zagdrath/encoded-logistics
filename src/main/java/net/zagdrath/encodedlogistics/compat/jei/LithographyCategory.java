/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.compat.jei;

import java.util.List;
import java.util.Locale;

import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.recipe.types.IRecipeHolderType;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.zagdrath.encodedlogistics.recipe.LithographyRecipe;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.registry.ModRecipeTypes;

// JEI: lithography recipes laid out like the press - photomask above, wafer on the left, additive below, the result on
// the right - with the energy and time underneath. The photomask isn't used up.
public class LithographyCategory implements IRecipeCategory<RecipeHolder<LithographyRecipe>> {
    public static final IRecipeHolderType<LithographyRecipe> TYPE = IRecipeType.create(ModRecipeTypes.LITHOGRAPHY.get());

    private static final int WIDTH = 120, HEIGHT = 66;

    private final IDrawable icon;

    public LithographyCategory(IGuiHelper guiHelper) {
        this.icon = guiHelper.createDrawableItemLike(ModItems.LITHOGRAPHY_PRESS.get());
    }

    @Override
    public IRecipeHolderType<LithographyRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.encodedlogistics.lithography");
    }

    @Override
    public int getWidth() {
        return WIDTH;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<LithographyRecipe> holder, IFocusGroup focuses) {
        LithographyRecipe recipe = holder.value();
        builder.addInputSlot(37, 1).setStandardSlotBackground().add(recipe.photomask())
                .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.encodedlogistics.photomask.reusable")));
        builder.addInputSlot(1, 20).setStandardSlotBackground().add(recipe.wafer());
        builder.addInputSlot(37, 39).setStandardSlotBackground().add(recipe.additive());
        builder.addOutputSlot(95, 20).setOutputSlotBackground().add(recipe.result());
    }

    @Override
    public void createRecipeExtras(IRecipeExtrasBuilder builder, RecipeHolder<LithographyRecipe> holder, IFocusGroup focuses) {
        LithographyRecipe recipe = holder.value();
        builder.addAnimatedRecipeArrowWidget(recipe.time()).setPosition(60, 20);
        builder.addText(List.of(Component.translatable("jei.encodedlogistics.lithography.energy", String.format(Locale.ROOT, "%,d", recipe.energy()),
                String.format(Locale.ROOT, "%.1f", recipe.time() / 20.0))), WIDTH, 10).setPosition(0, 58);
    }
}
