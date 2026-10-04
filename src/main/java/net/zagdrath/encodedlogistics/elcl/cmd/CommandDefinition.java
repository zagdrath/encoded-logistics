/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.cmd;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

// A command (COMMANDS.md): its name, description ("Start Crafting"), parameter schema in keyword order (the first
// `positional` of them may be given without keywords, in that order), where it may run, the Firewall authority it
// needs and what runs it. The same definition drives the parser, the compiler, the F4 prompter, F1 help and the
// interactive command line. Other devices add theirs through CommandRegistry.register.
public record CommandDefinition(String name, String description, List<ParamDef> params, int positional, Context context, Auth auth,
        @Nullable CommandExecutor executor) {

    // Where a command may run: on the command line and in programs (BOTH), one of those, or only inside a program
    // (the language's own statements: PGM, DCL, IF, DO, ...).
    public enum Context {
        INTERACTIVE, BATCH, BOTH, PROGRAM;

        public boolean interactive() {
            return this == INTERACTIVE || this == BOTH;
        }

        public boolean batch() {
            return this == BATCH || this == BOTH;
        }
    }

    // The Firewall permission a command needs (OS.md 6): none, or one of the Firewall's.
    public enum Auth {
        NONE, VIEW, INSERT, EXTRACT, CRAFT, CONFIGURE
    }

    public @Nullable ParamDef param(String keyword) {
        for (ParamDef param : params) {
            if (param.keyword().equalsIgnoreCase(keyword)) {
                return param;
            }
        }
        return null;
    }

    // The parameter a positional value goes to (0-based), or null past the last positional one.
    public @Nullable ParamDef positional(int index) {
        return index < positional && index < params.size() ? params.get(index) : null;
    }

    public CommandDefinition withExecutor(@Nullable CommandExecutor executor) {
        return new CommandDefinition(name, description, params, positional, context, auth, executor);
    }

    // The prompter's title: "Start Crafting (STRCRAFT)".
    public String title() {
        return description + " (" + name + ")";
    }

    public static Builder of(String name, String description) {
        return new Builder(name, description);
    }

    public static final class Builder {
        private final String name, description;
        private final List<ParamDef> params = new ArrayList<>();
        private int positional;
        private Context context = Context.BOTH;
        private Auth auth = Auth.NONE;

        private Builder(String name, String description) {
            this.name = name.toUpperCase(Locale.ROOT);
            this.description = description;
        }

        public Builder p(ParamDef.Builder param) {
            params.add(param.build());
            return this;
        }

        public Builder positional(int count) {
            positional = count;
            return this;
        }

        public Builder context(Context context) {
            this.context = context;
            return this;
        }

        public Builder auth(Auth auth) {
            this.auth = auth;
            return this;
        }

        public CommandDefinition build() {
            return new CommandDefinition(name, description, List.copyOf(params), positional, context, auth, null);
        }
    }
}
