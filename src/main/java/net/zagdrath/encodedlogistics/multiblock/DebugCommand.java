/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.multiblock;

import java.util.List;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

// /encodedlogistics debug: how the mod's networks are performing, told to whoever asks (in chat or the console), never
// written to the server log. Operators only.
//   debug solves                 each network's solves (rediscovered and lanes worked out again) over the last minute:
//                                how many, what they cost and what caused them, busiest first
//   debug ticks [count]          each level's network tick time, then the costliest networks (10, or count)
//   debug networks               every network: status, nodes, devices, racks, lanes, energy, when last solved
//   debug watch solves [ms]      tells you in chat of every solve taking at least ms (default 0: every one)
//   debug watch off              stops it
public final class DebugCommand {
    private DebugCommand() {}

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("encodedlogistics")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("debug")
                        .then(Commands.literal("solves")
                                .executes(context -> send(context.getSource(), ControllerStructures.solveReport(context.getSource().getServer()))))
                        .then(Commands.literal("ticks")
                                .executes(context -> send(context.getSource(), ControllerStructures.tickReport(context.getSource().getServer(), 10)))
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, 1000))
                                        .executes(context -> send(context.getSource(), ControllerStructures.tickReport(context.getSource().getServer(),
                                                IntegerArgumentType.getInteger(context, "count"))))))
                        .then(Commands.literal("networks")
                                .executes(context -> send(context.getSource(), ControllerStructures.networkReport(context.getSource().getServer()))))
                        .then(Commands.literal("watch")
                                .then(Commands.literal("solves")
                                        .executes(context -> watch(context.getSource(), 0))
                                        .then(Commands.argument("ms", IntegerArgumentType.integer(0, 60_000))
                                                .executes(context -> watch(context.getSource(), IntegerArgumentType.getInteger(context, "ms")))))
                                .then(Commands.literal("off")
                                        .executes(context -> watch(context.getSource(), null))))));
    }

    private static int send(CommandSourceStack source, List<String> lines) {
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return lines.size();
    }

    private static int watch(CommandSourceStack source, Integer millis) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Only a player can watch solves (the console has debug solves)."));
            return 0;
        }
        ControllerStructures.watchSolves(player.getUUID(), millis);
        source.sendSuccess(() -> Component.literal(millis == null ? "Stopped watching network solves."
                : "Watching network solves" + (millis > 0 ? " of " + millis + " ms or more" : "") + ": each is shown here in chat."), false);
        return 1;
    }
}
