/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

// An input field on the green screen: where it is, how long, what's typed (bright, underlined). The cursor goes to the
// end of the text when it's focused and anywhere in it with the arrows, Home and End; typing overwrites at the cursor
// (or, in insert mode, pushes the rest along), Delete closes the gap, Field Exit (Ctrl+Enter, keypad Enter) clears from
// the cursor on. Flags: protected (text only, never focused), numeric (digits, a sign, a decimal point), uppercase
// (letters folded, for names), required (the prompter shows its label bright). A field can hold more than it shows
// (its capacity, the prompter's): the text scrolls to keep the cursor in view. One can also take several rows (the
// command line on Command Entry): its text wraps from each row's end to the next row's start, length to a row.
final class CrtField {
    final int col, length, capacity;
    // Its first row, and how many it takes (only the command line changes these, as its screen wants).
    int row, rows = 1;
    String value;
    int cursor;
    boolean isProtected, numeric, uppercase, required;
    // The first character shown (a field holding more than it shows).
    private int scroll;

    CrtField(int row, int col, int length, String value) {
        this(row, col, length, length, value);
    }

    CrtField(int row, int col, int length, int capacity, String value) {
        this.row = row;
        this.col = col;
        this.length = length;
        this.capacity = Math.max(length, capacity);
        this.value = value.length() > this.capacity ? value.substring(0, this.capacity) : value;
        this.cursor = Math.min(this.value.length(), this.capacity - 1);
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

    // The command line's place: its first row and how many rows it takes.
    void shape(int row, int rows) {
        this.row = row;
        this.rows = Math.max(1, rows);
    }

    int lastRow() {
        return row + rows - 1;
    }

    // Whether its text wraps over its rows rather than scrolling in one.
    private boolean wraps() {
        return rows > 1 && capacity <= length * rows;
    }

    void set(String text) {
        value = text.length() > capacity ? text.substring(0, capacity) : text;
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
        } else if (value.length() < capacity) {
            value = value.substring(0, cursor) + c + value.substring(cursor);
        } else {
            return false;
        }
        cursor++;
        return cursor >= capacity;
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
        cursor = Math.min(Math.min(value.length(), capacity - 1), cursor + 1);
    }

    // The screen row the cursor is on.
    int cursorRow() {
        return wraps() ? row + Math.min(cursor / length, rows - 1) : row;
    }

    // The screen column the cursor is on (the text scrolled to keep it in view).
    int cursorColumn() {
        if (wraps()) {
            return col + cursor % length;
        }
        if (cursor < scroll) {
            scroll = cursor;
        } else if (cursor >= scroll + length) {
            scroll = cursor - length + 1;
        }
        return col + Math.min(cursor - scroll, length - 1);
    }

    // A click at a row and column: the cursor to the character there.
    void clickAt(int at, int column) {
        int start = wraps() ? (Math.clamp(at, row, lastRow()) - row) * length : scroll;
        cursor = Math.min(value.length(), Math.min(capacity - 1, start + column - col));
    }

    // Up or Down within its rows: false at its first or last row.
    boolean moveRow(boolean up) {
        if (!wraps() || (up ? cursorRow() == row : cursorRow() == lastRow())) {
            return false;
        }
        cursor = Math.min(value.length(), Math.min(capacity - 1, cursor + (up ? -length : length)));
        return true;
    }

    String trimmed() {
        return value.trim();
    }

    void draw(CrtGrid grid) {
        if (isProtected) {
            grid.put(row, col, value, CrtGrid.NORMAL);
            return;
        }
        if (wraps()) {
            for (int r = 0; r < rows; r++) {
                int from = r * length;
                if (from < value.length()) {
                    grid.put(row + r, col, value.substring(from, Math.min(value.length(), from + length)), CrtGrid.BRIGHT);
                }
                grid.underline(row + r, col, length);
            }
            return;
        }
        // Scrolled as the cursor last left it (only the focused field's cursor moves it: CrtScreen asks for its column).
        String shown = value.length() > scroll ? value.substring(scroll) : "";
        grid.put(row, col, shown.length() > length ? shown.substring(0, length) : shown, CrtGrid.BRIGHT);
        grid.underline(row, col, length);
    }

    boolean contains(int row, int col) {
        return row >= this.row && row <= lastRow() && col >= this.col && col < this.col + length;
    }
}
