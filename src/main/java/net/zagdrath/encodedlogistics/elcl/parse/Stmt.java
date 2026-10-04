/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.parse;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition;

// One statement: its label, command name, parameters (keyword form; positional values are given their keywords from
// the schema) and the source lines it covers (record indices, 0-based, continuation lines included). definition is
// null for an unknown command; broken when a syntax error cut it short (what was read before it is kept). aliases: more
// labels on it, from label-only lines before it (A: / B: / CMD: A and B both name CMD).
public record Stmt(@Nullable String label, String name, List<Param> params, int firstLine, int lastLine, @Nullable CommandDefinition definition,
        boolean broken, List<String> aliases) {
    public Stmt(@Nullable String label, String name, List<Param> params, int firstLine, int lastLine, @Nullable CommandDefinition definition,
            boolean broken) {
        this(label, name, params, firstLine, lastLine, definition, broken, List.of());
    }

    // A parameter: its keyword (null only when a positional value had nowhere to go) and its values.
    public record Param(@Nullable String keyword, List<Expr> values, int line) {
        @Override
        public String toString() {
            String values = this.values.stream().map(Expr::toString).collect(Collectors.joining(" "));
            return keyword == null ? values : keyword + "(" + values + ")";
        }
    }

    public @Nullable Param param(String keyword) {
        for (Param param : params) {
            if (keyword.equalsIgnoreCase(param.keyword())) {
                return param;
            }
        }
        return null;
    }

    // The first value of a parameter, or null.
    public @Nullable Expr value(String keyword) {
        Param param = param(keyword);
        return param != null && !param.values().isEmpty() ? param.values().getFirst() : null;
    }

    // The nested command of THEN / ELSE CMD / EXEC / OTHERWISE CMD / SBMJOB CMD, or null.
    public @Nullable Stmt nested(String keyword) {
        return value(keyword) instanceof Expr.Nested nested ? nested.command() : null;
    }

    // Every label it has: its own, then its aliases.
    public List<String> labels() {
        List<String> labels = new ArrayList<>();
        if (label != null) {
            labels.add(label);
        }
        labels.addAll(aliases);
        return labels;
    }

    public boolean is(String command) {
        return name.equals(command);
    }

    // The statement as source on one line, keywords in schema order: "STRCRAFT ITEM(LOGIC_DIE) QTY(64)".
    public String toSource() {
        StringBuilder out = new StringBuilder();
        if (label != null) {
            out.append(label).append(": ");
        }
        out.append(name);
        for (Param param : params) {
            out.append(' ').append(param);
        }
        return out.toString();
    }
}
