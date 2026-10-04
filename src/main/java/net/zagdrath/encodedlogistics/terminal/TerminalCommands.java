/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.exec.ElclCommandLine;

// The Terminal Desk's command line: every line is an ELCL command (COMMANDS.md), run in the user's interactive job. Words
// are split on spaces, with "quoted strings" kept whole (the screens' requests to the server use the same split). Tab
// completes the first word from the ELCL commands and later words from the network's items.
public final class TerminalCommands {
    private TerminalCommands() {}

    // Words of a command line: spaces split them, double quotes keep them together.
    public static List<String> words(String line) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean quoted = false, any = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
                any = true;
            } else if (c == ' ' && !quoted) {
                if (any) {
                    words.add(word.toString());
                    word.setLength(0);
                    any = false;
                }
            } else {
                word.append(c);
                any = true;
            }
        }
        if (any) {
            words.add(word.toString());
        }
        return words;
    }

    // Runs a command line as ELCL.
    public static TerminalOutput execute(TerminalContext context, String line) {
        if (words(line).isEmpty()) {
            return new TerminalOutput();
        }
        return ElclCommandLine.run(context, line);
    }

    // Completions for the last word of a line (a trailing space starts a new one): an ELCL command's name, then items.
    public static List<String> complete(TerminalContext context, String line) {
        List<String> words = new ArrayList<>(words(line));
        if (line.endsWith(" ") || words.isEmpty()) {
            words.add("");
        }
        String last = words.getLast();
        if (words.size() == 1) {
            String start = last.toUpperCase(Locale.ROOT);
            return CommandRegistry.all().stream().map(CommandDefinition::name).filter(name -> name.startsWith(start)).sorted().toList();
        }
        return last.isEmpty() ? List.of() : TerminalItems.complete(context, last);
    }
}
