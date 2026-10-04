/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.terminal;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;

// What a command (or a screen's query) gives back: lines for the screen, and a message for its message line.
public final class TerminalOutput {
    private final List<TerminalLine> lines = new ArrayList<>();
    private @Nullable Component message;

    public static TerminalOutput message(Component message) {
        return new TerminalOutput().setMessage(message);
    }

    public TerminalOutput line(TerminalLine line) {
        lines.add(line);
        return this;
    }

    public TerminalOutput line(String text) {
        return line(TerminalLine.of(text));
    }

    public TerminalOutput line(Component text, int attr) {
        return line(TerminalLine.of(text, attr));
    }

    public TerminalOutput setMessage(@Nullable Component message) {
        this.message = message;
        return this;
    }

    public List<TerminalLine> lines() {
        return lines;
    }

    public @Nullable Component message() {
        return message;
    }
}
