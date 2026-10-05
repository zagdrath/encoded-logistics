/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// A pop-up asking for values (2=Change's text and authority, 3=Copy's library and member, 7=Rename's new name): a label
// and a field per row, with a hint after it (cut at the window's edge); Enter hands the values over and closes it. A
// field with choices takes F4 to step through them.
class FormWindow extends CrtWindow {
    private record Row(String label, CrtField field, String hint, List<String> choices) {}

    private final List<Row> rows = new ArrayList<>();
    private final Consumer<List<String>> done;

    FormWindow(CrtTerminal screen, String title, String text, Consumer<List<String>> done) {
        this(screen, 6, 12, title, text, done);
    }

    // At a row, height rows tall: room for height - 8 fields.
    FormWindow(CrtTerminal screen, int row, int height, String title, String text, Consumer<List<String>> done) {
        super(screen, row, 6, height, 68, title);
        this.done = done;
        text(text);
        keys(CrtPanel.tr("crt.encodedlogistics.form.keys"));
    }

    // A labelled field on the window's next row (under its text).
    FormWindow field(String label, int length, String value, String hint) {
        return field(label, length, length, value, hint, List.of());
    }

    // A field showing `length` of up to `capacity` characters (it scrolls).
    FormWindow field(String label, int length, int capacity, String value, String hint) {
        return field(label, length, capacity, value, hint, List.of());
    }

    // A field whose values F4 steps through.
    FormWindow field(String label, int length, String value, String hint, List<String> choices) {
        return field(label, length, length, value, hint, choices);
    }

    private FormWindow field(String label, int length, int capacity, String value, String hint, List<String> choices) {
        int fieldRow = row + 5 + rows.size();
        CrtField field = new CrtField(fieldRow, col + 32, length, capacity, value);
        rows.add(new Row(label, field, hint, choices));
        fields.add(field);
        if (!choices.isEmpty() && !hasChoiceKeys) {
            hasChoiceKeys = true;
            keys(CrtPanel.tr("crt.encodedlogistics.form.keys_choices"));
        }
        return this;
    }

    private boolean hasChoiceKeys;

    // F4 on a field with choices: its next one.
    @Override
    boolean functionKey(int f) {
        if (f != 4) {
            return false;
        }
        for (Row r : rows) {
            if (r.field() == screen.focused() && !r.choices().isEmpty()) {
                int at = -1;
                for (int i = 0; i < r.choices().size(); i++) {
                    if (r.choices().get(i).equalsIgnoreCase(r.field().trimmed())) {
                        at = i;
                    }
                }
                r.field().set(r.choices().get((at + 1) % r.choices().size()));
                return true;
            }
        }
        return false;
    }

    @Override
    int textRows() {
        return 1;
    }

    @Override
    void drawBody(CrtGrid grid) {
        for (Row r : rows) {
            grid.put(r.field().row, col + 2, CrtGrid.pad(r.label(), 30));
            int hintCol = r.field().col + r.field().length + 1, room = col + width - 2 - hintCol;
            String hint = r.hint().length() > room ? r.hint().substring(0, Math.max(0, room - 3)) + "..." : r.hint();
            grid.put(r.field().row, hintCol, hint, CrtGrid.DIM);
        }
    }

    @Override
    boolean enter() {
        List<String> values = new ArrayList<>();
        for (Row r : rows) {
            values.add(r.field().trimmed());
        }
        screen.closeWindow();
        done.accept(values);
        return true;
    }
}
