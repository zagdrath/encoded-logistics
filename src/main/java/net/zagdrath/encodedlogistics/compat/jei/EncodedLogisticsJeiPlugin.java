/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.compat.jei;

import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.registry.ModItems;

// JEI support (only loaded when JEI is installed). The crafting recipe shows up on its own; this adds an info page on
// how controllers form structures and what they provide.
@JeiPlugin
public class EncodedLogisticsJeiPlugin implements IModPlugin {
    private static final Identifier UID = EncodedLogistics.id("jei_plugin");

    @Override
    public Identifier getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addItemStackInfo(new ItemStack(ModItems.NETWORK_CONTROLLER.get()),
                Component.translatable("jei.encodedlogistics.network_controller.info"));
    }
}
