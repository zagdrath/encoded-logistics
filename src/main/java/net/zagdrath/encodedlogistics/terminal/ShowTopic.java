/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.terminal;

import java.util.List;

import net.minecraft.network.chat.Component;

// A "show <topic>" listing (TerminalCommands.registerShowTopic): its one-line help and what it lists (args: anything
// typed after the topic).
public interface ShowTopic {
    Component help();

    TerminalOutput show(TerminalContext context, List<String> args);
}
