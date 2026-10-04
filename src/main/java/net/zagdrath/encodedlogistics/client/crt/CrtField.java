/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

// An input field on the green screen: where it is, how long, what's typed (bright, underlined). The cursor goes to the
// end of the text when it's focused and anywhere in it with the arrows, Home and End; typing overwrites at the cursor
// (or, in insert mode, pushes the rest along), Delete closes the gap, Field Exit (Ctrl+Enter, keypad Enter) clears from
// the cursor on. Flags: protected (text only, never focused), numeric (digits, a sign, a decimal point), uppercase
// (letters folded, for names), required (the prompter shows its label bright).
final class CrtField {
    final int row, col, length;
    String value;
    int cursor;
    boolean isProtected, numeric, uppercase, required;

    CrtField(int row, int col, int length, String value) {
        this.row = row;
        this.col = col;
        this.length = length;
        this.value = value.length() > length ? value.substring(0, length) : value;
        this.cursor = this.value.length();
    }

    CrtField protect() {
        isProtected = true;
        return this;
    }

    CrtField numeric() {
        numeric = true;
        return this;
    }

    CrtField uppercase() {
        uppercase = true;
        return this;
    }

    CrtField required() {
        required = true;
        return this;
    }

    void set(String text) {
        value = text.length() > length ? text.substring(0, length) : text;
        cursor = value.length();
    }

    // Types a character at the cursor: true when it reached the field's last position (Field Advance).
    boolean type(char c, boolean insert) {
        if (numeric && !(c >= '0' && c <= '9' || c == '-' || c == '+' || c == '.' || c == ',')) {
            return false;
        }
        if (uppercase) {
            c = Character.toUpperCase(c);
        }
        if (cursor < value.length() && !insert) {
            value = value.substring(0, cursor) + c + value.substring(cursor + 1);
        } else if (value.length() < length) {
            value = value.substring(0, cursor) + c + value.substring(cursor);
        } else {
            return false;
        }
        cursor++;
        return cursor >= length;
    }

    void type(char c) {
        type(c, true);
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

    // Field Exit: everything from the cursor on goes.
    void fieldExit() {
        value = value.substring(0, Math.min(cursor, value.length()));
    }

    void left() {
        cursor = Math.max(0, cursor - 1);
    }

    // Past the text only as far as the field's last position (overwriting blanks there pads it).
    void right() {
        cursor = Math.min(Math.min(value.length(), length - 1), cursor + 1);
    }

    String trimmed() {
        return value.trim();
    }

    void draw(CrtGrid grid) {
        if (isProtected) {
            grid.put(row, col, value, CrtGrid.NORMAL);
            return;
        }
        grid.put(row, col, value, CrtGrid.BRIGHT);
        grid.underline(row, col, length);
    }

    boolean contains(int row, int col) {
        return row == this.row && col >= this.col && col < this.col + length;
    }
}
