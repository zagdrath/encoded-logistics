/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.terminal;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.rack.RackPermission;

// A root command of the Terminal Desk's command line (TerminalCommands): its word ("show"), how it's used, its help,
// the Firewall permission it needs (none: anyone who can open the desk), what it does, and how it completes. Run on
// the server; its output goes to the desk's screen.
public interface TerminalCommand {
    String name();

    String usage();

    Component help();

    @Nullable RackPermission permission();

    TerminalOutput run(TerminalContext context, List<String> args);

    // Completions for the last argument (args: those typed so far, the last maybe partial).
    default List<String> complete(TerminalContext context, List<String> args) {
        return List.of();
    }
}
