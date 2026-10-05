/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.lex;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.zagdrath.encodedlogistics.elcl.lex.Token.Kind;

// ELCL source to tokens (ELCL_SPEC.md 2). Case-insensitive: names, &variables, *special values and %built-ins come out
// in upper case; strings keep theirs ('' is a quote inside one). Comments (/* ... */, maybe over several lines) are
// dropped. A statement ends at the end of its line unless the line's last token is + or - (comments after it don't
// count): then it carries on with the next line. Inside a string, + at the end of the line carries the string on
// with the next line as it is; - carries it on with the next line's leading blanks dropped. A - straight before a
// digit, after a blank or "(", is a negative number ("PARM(&A -5)" is two values; "&A - 5" and "&A-5" subtract).
public final class Lexer {
    private final List<String> lines;
    private final List<Token> tokens = new ArrayList<>();
    private int line, col;
    private boolean spaced = true;

    private Lexer(List<String> lines) {
        this.lines = lines;
    }

    public static List<Token> tokens(List<String> lines) {
        Lexer lexer = new Lexer(lines);
        lexer.run();
        return lexer.tokens;
    }

    public static List<Token> tokens(String line) {
        return tokens(List.of(line));
    }

    public static boolean isNameStart(char c) {
        return c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z';
    }

    public static boolean isNameChar(char c) {
        return isNameStart(c) || c >= '0' && c <= '9' || c == '_' || c == '@' || c == '#' || c == '$';
    }

    private char at(int c) {
        String text = lines.get(line);
        return c < text.length() ? text.charAt(c) : '\n';
    }

    private void emit(Kind kind, String text, int startCol) {
        tokens.add(new Token(kind, text, line, startCol, spaced));
        spaced = false;
    }

    private void run() {
        boolean comment = false;
        while (line < lines.size()) {
            String text = lines.get(line);
            if (col >= text.length()) {
                // The line's end: the statement's, unless inside a comment.
                if (!comment && !tokens.isEmpty() && tokens.getLast().kind() != Kind.EOL) {
                    emit(Kind.EOL, "", col);
                }
                line++;
                col = 0;
                spaced = true;
                continue;
            }
            if (comment) {
                int end = text.indexOf("*/", col);
                if (end < 0) {
                    col = text.length();
                } else {
                    comment = false;
                    col = end + 2;
                    spaced = true;
                }
                continue;
            }
            char c = text.charAt(col);
            if (c == ' ' || c == '\t') {
                col++;
                spaced = true;
            } else if (c == '/' && at(col + 1) == '*') {
                comment = true;
                col += 2;
            } else if ((c == '+' || c == '-') && continues(col + 1)) {
                // Continuation: the statement goes on with the next line (inside a comment, if one opened after it).
                comment = commentOpen(text, col + 1);
                line++;
                col = 0;
                spaced = true;
            } else if (c == '\'') {
                string();
            } else if (isNameStart(c)) {
                int start = col;
                while (isNameChar(at(col))) {
                    col++;
                }
                emit(Kind.NAME, text.substring(start, col).toUpperCase(Locale.ROOT), start);
            } else if ((c == '&' || c == '*' || c == '%') && isNameStart(at(col + 1)) || c == '*' && digitsThenLetter(col + 1)) {
                int start = col++;
                while (isNameChar(at(col))) {
                    col++;
                }
                Kind kind = c == '&' ? Kind.VARIABLE : c == '*' ? Kind.SPECIAL : Kind.BUILTIN;
                emit(kind, text.substring(start, col).toUpperCase(Locale.ROOT), start);
            } else if (c >= '0' && c <= '9' || c == '-' && digit(at(col + 1)) && negativeAllowed()) {
                int start = col++;
                while (digit(at(col))) {
                    col++;
                }
                if (at(col) == '.' && digit(at(col + 1))) {
                    col++;
                    while (digit(at(col))) {
                        col++;
                    }
                }
                emit(Kind.NUMBER, text.substring(start, col), start);
            } else {
                symbol(c);
            }
        }
        if (!tokens.isEmpty() && tokens.getLast().kind() != Kind.EOL) {
            tokens.add(new Token(Kind.EOL, "", Math.max(0, lines.size() - 1), 0, true));
        }
        tokens.add(new Token(Kind.EOF, "", Math.max(0, lines.size() - 1), 0, true));
    }

    private static boolean digit(char c) {
        return c >= '0' && c <= '9';
    }

    // Digits then a letter from here: a special value such as *1M or *10M (a range), not a multiplication.
    private boolean digitsThenLetter(int from) {
        int at = from;
        while (digit(at(at))) {
            at++;
        }
        return at > from && Character.isLetter(at(at));
    }

    // A - starts a negative number after a blank or "(" (or at a statement's start).
    private boolean negativeAllowed() {
        if (tokens.isEmpty() || tokens.getLast().kind() == Kind.EOL) {
            return true;
        }
        return spaced || tokens.getLast().kind() == Kind.LPAREN;
    }

    // Whether only blanks and comments follow on this line (so a + or - there is a continuation).
    private boolean continues(int from) {
        String text = lines.get(line);
        int c = from;
        while (c < text.length()) {
            char ch = text.charAt(c);
            if (ch == ' ' || ch == '\t') {
                c++;
            } else if (ch == '/' && c + 1 < text.length() && text.charAt(c + 1) == '*') {
                int end = text.indexOf("*/", c + 2);
                if (end < 0) {
                    return true;
                }
                c = end + 2;
            } else {
                return false;
            }
        }
        return line + 1 < lines.size();
    }

    // Whether a comment opened after a continuation (only blanks and comments there) is still open at the line's end.
    private static boolean commentOpen(String text, int from) {
        int open = text.lastIndexOf("/*");
        return open >= from && text.indexOf("*/", open + 2) < 0;
    }

    // A string: '' is a quote; + or - at the end of a line inside it carries it on with the next line.
    private void string() {
        int startLine = line, startCol = col;
        boolean wasSpaced = spaced;
        StringBuilder value = new StringBuilder();
        col++;
        while (true) {
            String text = lines.get(line);
            if (col >= text.length()) {
                // Unclosed on this line.
                tokens.add(new Token(Kind.ERROR, "'" + value, startLine, startCol, wasSpaced));
                spaced = false;
                return;
            }
            char c = text.charAt(col);
            if (c == '\'') {
                if (at(col + 1) == '\'') {
                    value.append('\'');
                    col += 2;
                    continue;
                }
                col++;
                break;
            }
            if ((c == '+' || c == '-') && col == text.length() - 1 && line + 1 < lines.size()) {
                line++;
                col = 0;
                if (c == '-') {
                    String next = lines.get(line);
                    while (col < next.length() && next.charAt(col) == ' ') {
                        col++;
                    }
                }
                continue;
            }
            value.append(c);
            col++;
        }
        tokens.add(new Token(Kind.STRING, value.toString(), startLine, startCol, wasSpaced));
        spaced = false;
    }

    private void symbol(char c) {
        int start = col;
        char next = at(col + 1);
        String two = "" + c + next;
        switch (c) {
            case '(' -> {
                col++;
                emit(Kind.LPAREN, "(", start);
            }
            case ')' -> {
                col++;
                emit(Kind.RPAREN, ")", start);
            }
            case ':' -> {
                col++;
                emit(Kind.COLON, ":", start);
            }
            case '/' -> {
                if (next == '/') {
                    col += 2;
                    emit(Kind.OPERATOR, "//", start);
                } else {
                    col++;
                    emit(Kind.SLASH, "/", start);
                }
            }
            case '|' -> {
                if (next == '|' || next == '>' || next == '<') {
                    col += 2;
                    emit(Kind.OPERATOR, two, start);
                } else {
                    col++;
                    emit(Kind.OPERATOR, "|", start);
                }
            }
            case '<' -> {
                if (next == '>' || next == '=') {
                    col += 2;
                    emit(Kind.OPERATOR, two, start);
                } else {
                    col++;
                    emit(Kind.OPERATOR, "<", start);
                }
            }
            case '>' -> {
                if (next == '=') {
                    col += 2;
                    emit(Kind.OPERATOR, ">=", start);
                } else {
                    col++;
                    emit(Kind.OPERATOR, ">", start);
                }
            }
            case '¬' -> {
                if (next == '=') {
                    col += 2;
                    emit(Kind.OPERATOR, "<>", start);
                } else {
                    col++;
                    emit(Kind.OPERATOR, "¬", start);
                }
            }
            case '+', '-', '*', '=', '&' -> {
                col++;
                emit(Kind.OPERATOR, String.valueOf(c), start);
            }
            default -> {
                col++;
                emit(Kind.ERROR, String.valueOf(c), start);
            }
        }
    }
}
