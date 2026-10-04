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

    // Text from (row, col), cut at the screen's edge; characters outside ASCII show as '?'.
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
            chars[row][c] = ch >= 32 && ch < 127 ? ch : '?';
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

    // Text cut or padded to a width.
    static String pad(String text, int width) {
        return text.length() >= width ? text.substring(0, width) : text + " ".repeat(width - text.length());
    }

    static String padLeft(String text, int width) {
        return text.length() >= width ? text.substring(text.length() - width) : " ".repeat(width - text.length()) + text;
    }
}
