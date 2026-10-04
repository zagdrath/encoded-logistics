/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.screen;

import java.util.Locale;

import net.minecraft.server.MinecraftServer;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// A system (OS.md 1): a network on a server. Libraries, jobs, message queues, spooled files and system values belong
// to it. Also the game clock as the screens show it.
public record ElclSystem(MinecraftServer server, NetworkRef network) {
    // The network's default system name, ELNET01 (SYSNAME's default).
    public String defaultName() {
        return TerminalService.networkName(network);
    }

    // SYSNAME: the system value, or the default.
    public String name() {
        return ElclServices.sysvals().get(this, "SYSNAME");
    }

    // The game day and time: "Day 2  07:14:22".
    public String now() {
        return clock(server.overworld().getOverworldClockTime(), true);
    }

    // "Day 2  07:13", for lists.
    public String nowShort() {
        return clock(server.overworld().getOverworldClockTime(), false);
    }

    public long ticks() {
        return server.overworld().getOverworldClockTime();
    }

    public static String clock(long time, boolean seconds) {
        long day = time / 24_000 + 1, tick = time % 24_000;
        long secs = (tick * 86_400 / 24_000 + 6 * 3_600) % 86_400;
        return seconds ? String.format(Locale.ROOT, "Day %d  %02d:%02d:%02d", day, secs / 3_600, secs / 60 % 60, secs % 60)
                : String.format(Locale.ROOT, "Day %d  %02d:%02d", day, secs / 3_600, secs / 60 % 60);
    }

    // The game day number (a source line's change date).
    public int day() {
        return (int) (ticks() / 24_000 + 1);
    }
}
