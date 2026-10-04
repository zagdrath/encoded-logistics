/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.compile;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef.VarType;
import net.zagdrath.encodedlogistics.elcl.parse.Expr;

// A declared variable (DCL): its name (&COUNT), type, length (and decimals, for *DEC), initial value and the line
// that declares it.
public record VarDecl(String name, VarType type, int length, int decimals, @Nullable Expr value, int line) {
    // The listing's Length column: "64", "5 1", blank for the types without one.
    public String lengthText() {
        return switch (type) {
            case CHAR -> Integer.toString(length);
            case DEC -> length + " " + decimals;
            default -> "";
        };
    }
}
