/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// A pop-up asking for values (2=Change's text and authority, 3=Copy's library and member, 7=Rename's new name): a label
// and a field per row, with a hint after it; Enter hands the values over and closes it.
class FormWindow extends CrtWindow {
    private record Row(String label, CrtField field, String hint) {}

    private final List<Row> rows = new ArrayList<>();
    private final Consumer<List<String>> done;

    FormWindow(CrtTerminal screen, String title, String text, Consumer<List<String>> done) {
        super(screen, 6, 6, 12, 68, title);
        this.done = done;
        text(text);
        keys(CrtPanel.tr("crt.encodedlogistics.form.keys"));
    }

    // A labelled field on the window's next row (under its text).
    FormWindow field(String label, int length, String value, String hint) {
        int fieldRow = row + 5 + rows.size();
        CrtField field = new CrtField(fieldRow, col + 32, length, value);
        rows.add(new Row(label, field, hint));
        fields.add(field);
        return this;
    }

    @Override
    int textRows() {
        return 1;
    }

    @Override
    void drawBody(CrtGrid grid) {
        for (Row r : rows) {
            grid.put(r.field().row, col + 2, CrtGrid.pad(r.label(), 30));
            grid.put(r.field().row, r.field().col + r.field().length + 1, r.hint(), CrtGrid.DIM);
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
