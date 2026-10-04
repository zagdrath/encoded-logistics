/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.crafting;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.zagdrath.encodedlogistics.elcl.device.DeviceSources;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;

// The recipe libraries on each network: every registered source's (the Midrange line registers its own).
public final class RecipeLibraries {
    private static final DeviceSources<RecipeLibrarySource> SOURCES = new DeviceSources<>();

    private RecipeLibraries() {}

    public static void register(DeviceSources.Source<RecipeLibrarySource> source) {
        SOURCES.register(source);
    }

    public static void unregister(DeviceSources.Source<RecipeLibrarySource> source) {
        SOURCES.unregister(source);
    }

    // The online libraries' recipes on a network.
    public static List<Schematic> recipes(MinecraftServer server, @Nullable NetworkRef network) {
        List<Schematic> recipes = new ArrayList<>();
        if (network == null) {
            return recipes;
        }
        for (RecipeLibrarySource library : SOURCES.all(new ElclSystem(server, network))) {
            if (library.online()) {
                recipes.addAll(library.recipes());
            }
        }
        return recipes;
    }
}
