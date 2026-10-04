/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.lex;

// One token of ELCL source: its kind, its text (names, variables, special values and built-ins folded to upper case;
// a string's contents as written, quotes undone), the source line (record index, 0-based) and column it starts at,
// and whether blanks (or a line break) come before it - the parser tells "LIB/NAME" from "&A / 2", a keyword's "(" and
// a negative number by that.
public record Token(Kind kind, String text, int line, int col, boolean spaced) {
    public enum Kind {
        NAME,       // RTVITMCNT, IRON_INGOT, ELC1201, a label
        VARIABLE,   // &COUNT
        SPECIAL,    // *YES, *EQ, *CAT
        BUILTIN,    // %SST
        STRING,     // 'text'
        NUMBER,     // 42, 3.25, -7
        LPAREN, RPAREN,
        SLASH,      // / (qualified names, division)
        OPERATOR,   // + - * // = <> > < >= <= || |> |< & | ¬
        COLON,      // after a label
        EOL,        // end of a statement (after continuation)
        EOF,
        ERROR       // a character ELCL doesn't use, or an unclosed string
    }

    public boolean is(Kind kind, String text) {
        return this.kind == kind && this.text.equals(text);
    }

    @Override
    public String toString() {
        return kind == Kind.STRING ? "'" + text.replace("'", "''") + "'" : text;
    }
}
