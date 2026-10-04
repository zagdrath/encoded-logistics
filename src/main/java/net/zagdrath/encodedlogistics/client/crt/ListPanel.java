/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

// A "Work with" screen: a list a page at a time, an Opt field (3 wide, column 0) on each row, "More..." or "Bottom" under
// it. Options typed stay with their rows across pages; Enter takes every row's option, top to bottom. Clicking a row
// types its default option; double-clicking does it.
abstract class ListPanel<T> extends CrtPanel {
    protected List<T> rows = new ArrayList<>();
    protected int top;
    private final Map<Object, String> options = new HashMap<>();
    private final List<CrtField> optionFields = new ArrayList<>();

    ListPanel(CrtTerminal screen) {
        super(screen);
    }

    // The first list row on the screen, and how many fit.
    abstract int firstRow();

    abstract int pageSize();

    // What identifies a row (its option stays with it).
    abstract Object key(T row);

    abstract void drawRow(CrtGrid grid, int screenRow, T row);

    // The option a click types.
    abstract String defaultOption();

    // The rows' options, top to bottom; true when something was done.
    abstract boolean process(List<Option<T>> chosen);

    record Option<T>(T row, String option) {}

    // Draws above the list (headings, the options line).
    abstract void drawHead(CrtGrid grid);

    void setRows(List<T> rows) {
        keepOptions();
        this.rows = rows;
        top = Math.max(0, Math.min(top, Math.max(0, rows.size() - 1) / pageSize() * pageSize()));
        rebuild();
    }

    // The visible rows' Opt fields, after the screen's other fields.
    protected void rebuild() {
        CrtField focused = screen.focused();
        int focusedRow = focused != null && optionFields.contains(focused) ? focused.row : -1;
        fields.removeAll(optionFields);
        optionFields.clear();
        for (int i = 0; i < pageSize() && top + i < rows.size(); i++) {
            CrtField field = new CrtField(firstRow() + i, 0, 3, options.getOrDefault(key(rows.get(top + i)), ""));
            optionFields.add(field);
        }
        fields.addAll(optionFields);
        for (CrtField field : optionFields) {
            if (field.row == focusedRow) {
                screen.focus(field);
                return;
            }
        }
        if (focused != null && !fields.contains(focused) && focused != screen.command) {
            screen.focusFirst();
        }
    }

    private void keepOptions() {
        for (int i = 0; i < optionFields.size() && top + i < rows.size(); i++) {
            String value = optionFields.get(i).trimmed();
            Object key = key(rows.get(top + i));
            if (value.isEmpty()) {
                options.remove(key);
            } else {
                options.put(key, value);
            }
        }
    }

    @Override
    void page(int direction) {
        keepOptions();
        int next = top + direction * pageSize();
        if (next >= 0 && next < rows.size()) {
            top = next;
            rebuild();
        } else {
            screen.message(direction > 0 ? tr("crt.encodedlogistics.msg.at_bottom") : tr("crt.encodedlogistics.msg.at_top"));
        }
    }

    @Override
    void draw(CrtGrid grid) {
        drawHead(grid);
        for (int i = 0; i < pageSize() && top + i < rows.size(); i++) {
            drawRow(grid, firstRow() + i, rows.get(top + i));
        }
        if (!rows.isEmpty()) {
            grid.right(20, tr(top + pageSize() < rows.size() ? "crt.encodedlogistics.more" : "crt.encodedlogistics.bottom"), CrtGrid.NORMAL);
        }
    }

    @Override
    boolean enter() {
        keepOptions();
        List<Option<T>> chosen = new ArrayList<>();
        for (T row : rows) {
            String option = options.get(key(row));
            if (option != null) {
                chosen.add(new Option<>(row, option));
            }
        }
        options.clear();
        rebuild();
        return !chosen.isEmpty() && process(chosen);
    }

    // An option code this screen hasn't got: says so, puts it back on its row and the cursor there.
    protected boolean invalid(Option<T> option) {
        screen.message(tr("crt.encodedlogistics.msg.invalid_option", option.option()));
        options.put(key(option.row()), option.option());
        int index = rows.indexOf(option.row());
        if (index >= 0 && (index < top || index >= top + pageSize())) {
            top = index / pageSize() * pageSize();
        }
        rebuild();
        for (CrtField field : optionFields) {
            if (index >= 0 && field.row == firstRow() + index - top) {
                screen.focus(field);
            }
        }
        return true;
    }

    // The Opt column's help key for F1 there.
    @Override
    @Nullable String helpField(@Nullable CrtField field) {
        return field != null && optionFields.contains(field) ? "opt" : null;
    }

    @Override
    void click(int row, int col, boolean doubleClick) {
        int index = row - firstRow();
        if (index < 0 || index >= optionFields.size()) {
            return;
        }
        CrtField field = optionFields.get(index);
        field.set(defaultOption());
        screen.focus(field);
        if (doubleClick) {
            enter();
        }
    }
}
