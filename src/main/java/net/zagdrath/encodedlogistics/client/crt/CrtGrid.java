/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.Arrays;

// The green screen's 80 x 24 character cells: a character each, its attribute (NORMAL, BRIGHT, DIM) and whether it's
// underlined (an input field) or reversed (a selected row). Panels write into it every frame.
final class CrtGrid {
    static final int COLS = 80, ROWS = 24;
    static final byte NORMAL = 0, BRIGHT = 1, DIM = 2;
    // The glyphs past ASCII on the font sheet (row 6, cells 96-107): the not sign and the single-line box pieces.
    static final String EXTRA = "¬─│┌┐└┘├┤┬┴┼";
    static final char H = '─', V = '│', TOP_LEFT = '┌', TOP_RIGHT = '┐', BOTTOM_LEFT = '└', BOTTOM_RIGHT = '┘',
            LEFT_TEE = '├', RIGHT_TEE = '┤';

    final char[][] chars = new char[ROWS][COLS];
    final byte[][] attrs = new byte[ROWS][COLS];
    final boolean[][] underline = new boolean[ROWS][COLS], reverse = new boolean[ROWS][COLS];

    void clear() {
        for (int row = 0; row < ROWS; row++) {
            Arrays.fill(chars[row], ' ');
            Arrays.fill(attrs[row], NORMAL);
            Arrays.fill(underline[row], false);
            Arrays.fill(reverse[row], false);
        }
    }

    // Whether the font has a glyph for it: printable ASCII and the extra glyphs.
    static boolean drawable(char c) {
        return c >= 32 && c < 127 || EXTRA.indexOf(c) >= 0;
    }

    // A character's cell on the font sheet: ASCII c at c - 32, the extra glyphs from 96.
    static int glyph(char c) {
        int extra = EXTRA.indexOf(c);
        return extra >= 0 ? 96 + extra : c - 32;
    }

    // Text from (row, col), cut at the screen's edge; characters the font hasn't got show as '?'.
    void put(int row, int col, String text, byte attr) {
        if (row < 0 || row >= ROWS) {
            return;
        }
        for (int i = 0; i < text.length(); i++) {
            int c = col + i;
            if (c < 0) {
                continue;
            }
            if (c >= COLS) {
                break;
            }
            char ch = text.charAt(i);
            chars[row][c] = drawable(ch) ? ch : '?';
            attrs[row][c] = attr;
        }
    }

    void put(int row, int col, String text) {
        put(row, col, text, NORMAL);
    }

    void center(int row, String text, byte attr) {
        put(row, Math.max(0, (COLS - text.length()) / 2), text, attr);
    }

    // Ending at column 78 (one column of margin).
    void right(int row, String text, byte attr) {
        put(row, Math.max(0, COLS - 1 - text.length()), text, attr);
    }

    void underline(int row, int col, int length) {
        for (int c = col; c < Math.min(COLS, col + length); c++) {
            underline[row][c] = true;
        }
    }

    void reverse(int row, int col, int length) {
        for (int c = col; c < Math.min(COLS, col + length); c++) {
            reverse[row][c] = true;
        }
    }

    // A single-line box: its border from (row, col), height x width, the inside cleared to blanks; a divider across
    // it at dividerRow (-1 for none).
    void box(int row, int col, int height, int width, int dividerRow) {
        for (int r = row; r < row + height; r++) {
            for (int c = col; c < col + width; c++) {
                if (r < 0 || r >= ROWS || c < 0 || c >= COLS) {
                    continue;
                }
                boolean top = r == row, bottom = r == row + height - 1, left = c == col, right = c == col + width - 1, divider = r == dividerRow;
                char ch = ' ';
                if (top || bottom || divider) {
                    ch = left ? (top ? TOP_LEFT : bottom ? BOTTOM_LEFT : LEFT_TEE) : right ? (top ? TOP_RIGHT : bottom ? BOTTOM_RIGHT : RIGHT_TEE) : H;
                } else if (left || right) {
                    ch = V;
                }
                chars[r][c] = ch;
                attrs[r][c] = NORMAL;
                underline[r][c] = false;
                reverse[r][c] = false;
            }
        }
    }

    // A column ruler, width long from (row, col): "*...+....1....+....2...", first being the column number of its
    // first character (1, or the window's first column).
    void ruler(int row, int col, int width, byte attr, int first) {
        put(row, col, rulerText(width, first), attr);
    }

    void ruler(int row, int col, int width, byte attr) {
        ruler(row, col, width, attr, 1);
    }

    static String rulerText(int width, int first) {
        StringBuilder out = new StringBuilder(width);
        for (int i = 0; i < width; i++) {
            int column = first + i;
            out.append(column == 1 ? '*' : column % 10 == 0 ? (char) ('0' + column / 10 % 10) : column % 5 == 0 ? '+' : '.');
        }
        return out.toString();
    }

    // The source editor's ruler (and the compile listing's): "*...+... 1 ...+... 2", the column number of each ten
    // under its last column.
    static String columnRuler(int width, int first) {
        StringBuilder out = new StringBuilder(width);
        for (int i = 0; i < width; i++) {
            int column = first + i, place = column % 10;
            out.append(place == 0 ? (char) ('0' + column / 10 % 10) : place == 9 ? ' ' : place == 1 ? (column == 1 ? '*' : ' ') : place == 5 ? '+' : '.');
        }
        return out.toString();
    }

    // Text cut or padded to a width.
    static String pad(String text, int width) {
        return text.length() >= width ? text.substring(0, width) : text + " ".repeat(width - text.length());
    }

    static String padLeft(String text, int width) {
        return text.length() >= width ? text.substring(text.length() - width) : " ".repeat(width - text.length()) + text;
    }
}
