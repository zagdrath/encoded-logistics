/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.cmd;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

// Every ELCL command by name (COMMANDS.md): the built-in ones (BuiltinCommands) and any other device's. A command is
// registered once with its schema; what runs it can be bound later (the game side binds the built-ins' executors at
// setup), so the schemas stay usable without a game - the compiler's tests use them as they are.
public final class CommandRegistry {
    private static final Map<String, CommandDefinition> COMMANDS = new LinkedHashMap<>();

    static {
        BuiltinCommands.register();
    }

    private CommandRegistry() {}

    public static synchronized void register(CommandDefinition command) {
        if (COMMANDS.putIfAbsent(command.name(), command) != null) {
            throw new IllegalArgumentException("ELCL command " + command.name() + " registered twice");
        }
    }

    // Sets (or replaces) what runs a registered command.
    public static synchronized void bind(String name, CommandExecutor executor) {
        CommandDefinition command = COMMANDS.get(name.toUpperCase(Locale.ROOT));
        if (command == null) {
            throw new IllegalArgumentException("ELCL command " + name + " is not registered");
        }
        COMMANDS.put(command.name(), command.withExecutor(executor));
    }

    public static synchronized @Nullable CommandDefinition get(String name) {
        return COMMANDS.get(name.toUpperCase(Locale.ROOT));
    }

    public static synchronized Collection<CommandDefinition> all() {
        return List.copyOf(COMMANDS.values());
    }
}
