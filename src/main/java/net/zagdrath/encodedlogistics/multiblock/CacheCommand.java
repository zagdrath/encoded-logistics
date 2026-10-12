/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.multiblock;

import com.mojang.brigadier.arguments.BoolArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// /encodedlogistics caches: how often each network cache was rebuilt over the last minute (CacheStats), to check that
// nothing invalidates them every tick. /encodedlogistics caches log true|false logs each minute's rebuilds as well.
// Operators only.
public final class CacheCommand {
    private CacheCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("encodedlogistics")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("caches")
                        .executes(context -> show(context.getSource()))
                        .then(Commands.literal("log")
                                .then(Commands.argument("on", BoolArgumentType.bool())
                                        .executes(context -> log(context.getSource(), BoolArgumentType.getBool(context, "on")))))));
    }

    private static int show(CommandSourceStack source) {
        for (String line : ControllerStructures.cacheReport(source.getServer())) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int log(CommandSourceStack source, boolean on) {
        CacheStats.setLogging(on);
        source.sendSuccess(() -> Component.literal("Cache rebuild logging " + (on ? "on: each minute's rebuilds go to the server log" : "off")), true);
        return 1;
    }
}
