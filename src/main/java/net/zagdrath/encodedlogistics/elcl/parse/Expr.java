/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.parse;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

// A parameter value (ELCL_SPEC.md 3): an expression, a special value, a name, a qualified name or a nested command.
// Operators are kept in their *WORD form (*EQ, *CAT, *AND...; + - * / // as they are). toString gives it back as
// source, the way the prompter writes it.
public sealed interface Expr {
    int line();

    record Str(String value, int line) implements Expr {
        @Override
        public String toString() {
            return "'" + value.replace("'", "''") + "'";
        }
    }

    record Num(BigDecimal value, String text, int line) implements Expr {
        @Override
        public String toString() {
            return text;
        }
    }

    record Var(String name, int line) implements Expr {
        @Override
        public String toString() {
            return name;
        }
    }

    record Special(String name, int line) implements Expr {
        @Override
        public String toString() {
            return name;
        }
    }

    // A bare name: IRON_INGOT, ELC1400, a label, MINECRAFT:IRON_INGOT.
    record Name(String name, int line) implements Expr {
        @Override
        public String toString() {
            return name;
        }
    }

    // Names joined by "/" with no blanks: LIB/NAME, *LIBL/NAME, 000123/ZAGDRATH/RESTOCK.
    record Path(List<String> parts, int line) implements Expr {
        @Override
        public String toString() {
            return String.join("/", parts);
        }
    }

    // A built-in function: %SST(&S 1 3).
    record Builtin(String function, List<Expr> args, int line) implements Expr {
        @Override
        public String toString() {
            return function + "(" + args.stream().map(Expr::toString).collect(Collectors.joining(" ")) + ")";
        }
    }

    // - (negation) or *NOT.
    record Unary(String op, Expr operand, int line) implements Expr {
        @Override
        public String toString() {
            return op.equals("-") ? "-" + operand : op + " " + operand;
        }
    }

    record Binary(String op, Expr left, Expr right, int line) implements Expr {
        @Override
        public String toString() {
            return left + " " + op + " " + right;
        }
    }

    // An expression in parentheses (kept so the prompter writes it back as typed).
    record Group(Expr inner, int line) implements Expr {
        @Override
        public String toString() {
            return "(" + inner + ")";
        }
    }

    // A command inside THEN(), EXEC(), CMD().
    record Nested(Stmt command, int line) implements Expr {
        @Override
        public String toString() {
            return command.toSource();
        }
    }
}
