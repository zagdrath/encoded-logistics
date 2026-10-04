/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

// An input field on the green screen: where it is, how long, what's typed (bright, underlined). The cursor sits at the
// end of the text; typing past the length is ignored.
final class CrtField {
    final int row, col, length;
    String value;
    int cursor;

    CrtField(int row, int col, int length, String value) {
        this.row = row;
        this.col = col;
        this.length = length;
        this.value = value.length() > length ? value.substring(0, length) : value;
        this.cursor = this.value.length();
    }

    void set(String text) {
        value = text.length() > length ? text.substring(0, length) : text;
        cursor = value.length();
    }

    void type(char c) {
        if (value.length() < length) {
            value = value.substring(0, cursor) + c + value.substring(cursor);
            cursor++;
        }
    }

    void backspace() {
        if (cursor > 0) {
            value = value.substring(0, cursor - 1) + value.substring(cursor);
            cursor--;
        }
    }

    void delete() {
        if (cursor < value.length()) {
            value = value.substring(0, cursor) + value.substring(cursor + 1);
        }
    }

    void left() {
        cursor = Math.max(0, cursor - 1);
    }

    void right() {
        cursor = Math.min(value.length(), cursor + 1);
    }

    String trimmed() {
        return value.trim();
    }

    void draw(CrtGrid grid) {
        grid.put(row, col, value, CrtGrid.BRIGHT);
        grid.underline(row, col, length);
    }

    boolean contains(int row, int col) {
        return row == this.row && col >= this.col && col < this.col + length;
    }
}
