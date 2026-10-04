/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// The prompter's F4 list for a field: its values (special values first, then what the server lists) with an Opt
// field each - 1=Select and Enter puts the value in the field. PageUp / PageDown roll it.
final class ValueListWindow extends CrtWindow {
    private record Entry(String value, String description) {}

    private final List<Entry> entries = new ArrayList<>();
    private final Consumer<String> selected;
    private int top;

    ValueListWindow(CrtTerminal screen, String label, Consumer<String> selected) {
        super(screen, 4, 8, 16, 64, CrtPanel.tr("crt.encodedlogistics.values.title", label));
        this.selected = selected;
        keys(CrtPanel.tr("crt.encodedlogistics.values.keys"));
    }

    void add(String value, String description) {
        entries.add(new Entry(value, description));
        rebuild();
    }

    private int rows() {
        return textRows() - 1;
    }

    private void rebuild() {
        fields.clear();
        for (int i = 0; i < rows() && top + i < entries.size(); i++) {
            fields.add(new CrtField(firstTextRow() + 1 + i, col + 2, 1, ""));
        }
        if (screen.window() == this) {
            screen.focusFirst();
        }
    }

    @Override
    void drawBody(CrtGrid grid) {
        grid.put(firstTextRow(), col + 2, CrtPanel.tr("crt.encodedlogistics.values.heading"), CrtGrid.BRIGHT);
        for (int i = 0; i < rows() && top + i < entries.size(); i++) {
            Entry entry = entries.get(top + i);
            grid.put(firstTextRow() + 1 + i, col + 6, CrtGrid.pad(entry.value(), 25));
            grid.put(firstTextRow() + 1 + i, col + 32, CrtGrid.pad(entry.description(), width - 34), CrtGrid.DIM);
        }
        if (entries.isEmpty()) {
            grid.put(firstTextRow() + 1, col + 6, CrtPanel.tr("crt.encodedlogistics.values.none"), CrtGrid.DIM);
        } else {
            String more = CrtPanel.tr(top + rows() < entries.size() ? "crt.encodedlogistics.more" : "crt.encodedlogistics.bottom");
            grid.put(row + height - 3, col + width - 4 - more.length(), more, CrtGrid.NORMAL);
        }
    }

    @Override
    void page(int direction) {
        int next = top + direction * rows();
        if (next >= 0 && next < entries.size()) {
            top = next;
            rebuild();
        }
    }

    @Override
    boolean enter() {
        for (int i = 0; i < fields.size(); i++) {
            if (fields.get(i).trimmed().equals("1")) {
                screen.closeWindow();
                selected.accept(entries.get(top + i).value());
                return true;
            }
        }
        return true;
    }

    @Override
    void click(int row, int col, boolean doubleClick) {
        int index = row - firstTextRow() - 1;
        if (index >= 0 && index < fields.size()) {
            fields.get(index).set("1");
            screen.focus(fields.get(index));
            if (doubleClick) {
                enter();
            }
        }
    }
}
