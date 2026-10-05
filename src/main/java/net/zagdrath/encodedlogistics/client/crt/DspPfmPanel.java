/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// DSPPFM (Display Physical File Member), and RUNQRY's display (Display Report): a file's records in columns, a field a
// column under its heading (COLHDG's lines, bright). Row 2 the file and how many records (a query: how many it
// selected, of how many); row 3 Position to - a key on a keyed file (its fields' values, blank-separated: the first
// record at or after it comes to the top), else a record number - and the columns shown; row 4 the column ruler (dim);
// the headings from row 5 and the records under them to row 19. PageUp / PageDown roll it, F19 / F20 shift 40 columns
// for records wider than the screen. The server sends the records a window at a time (FileQueries' filedata / runqry);
// rolling past the window asks for the next.
final class DspPfmPanel extends CrtPanel {
    private static final int FIRST = 5, LAST = 19, WIDTH = 78, SHIFT = 40;

    // The file as typed (LIB/NAME or NAME); for a query its selection and sort (null: DSPPFM).
    private final String spec;
    private final @Nullable String qryslt, sort;
    private final CrtField position;
    private String qualified;
    private final List<String> headings = new ArrayList<>(), lines = new ArrayList<>();
    // The records in hand are from..from+lines.size() of total; top: the first shown.
    private int total, of, from, top, offset;
    private boolean keyed, loaded;

    DspPfmPanel(CrtTerminal screen, String spec) {
        this(screen, spec, null, null);
    }

    DspPfmPanel(CrtTerminal screen, String spec, @Nullable String qryslt, @Nullable String sort) {
        super(screen);
        this.spec = spec;
        this.qryslt = qryslt;
        this.sort = sort;
        this.qualified = spec.toUpperCase(Locale.ROOT);
        position = new CrtField(3, 23, 30, 64, "");
        fields.add(position);
    }

    private boolean query() {
        return qryslt != null;
    }

    @Override
    String id() {
        return query() ? "RUNQRY" : "DSPPFM";
    }

    @Override
    String title() {
        return tr(query() ? "crt.encodedlogistics.runqry.title" : "crt.encodedlogistics.dsppfm.title");
    }

    @Override
    String prompt() {
        return "";
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.dsppfm");
    }

    @Override
    void shown() {
        if (!loaded) {
            fetch(Math.max(0, top));
        }
    }

    @Override
    void refresh() {
        loaded = false;
        fetch(top);
    }

    // The window of records from an index (or "*KEY value").
    private void fetch(Object at) {
        if (query()) {
            screen.query("runqry " + spec + " " + at + "\t" + qryslt + "\t" + (sort != null ? sort : ""));
        } else {
            screen.query("filedata " + spec + " " + at);
        }
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (!answers(response, query() ? "runqry" : "filedata")) {
            return;
        }
        response.message().ifPresent(screen::message);
        if (response.lines().isEmpty()) {
            if (!loaded) {
                screen.back();
            }
            return;
        }
        TerminalLine head = response.lines().getFirst();
        qualified = cell(head, 0);
        total = Integer.parseInt(cell(head, 1));
        from = Integer.parseInt(cell(head, 2));
        keyed = cell(head, 3).equals("1");
        int count = Integer.parseInt(cell(head, 4));
        of = Integer.parseInt(cell(head, 5));
        headings.clear();
        lines.clear();
        for (int i = 1; i < response.lines().size(); i++) {
            (i <= count ? headings : lines).add(cell(response.lines().get(i), 0));
        }
        top = from;
        loaded = true;
    }

    private int rows() {
        return LAST - FIRST + 1 - headings.size();
    }

    @Override
    void draw(CrtGrid grid) {
        grid.put(2, 1, tr(query() ? "crt.encodedlogistics.runqry.query" : "crt.encodedlogistics.dsppfm.file"));
        grid.put(2, 21, qualified, CrtGrid.BRIGHT);
        if (query()) {
            grid.put(2, 48, tr("crt.encodedlogistics.runqry.selected"));
            grid.put(2, 64, String.format(Locale.ROOT, "%,d of %,d", total, of), CrtGrid.BRIGHT);
        } else {
            grid.put(2, 48, tr("crt.encodedlogistics.dsppfm.records"));
            grid.put(2, 64, String.format(Locale.ROOT, "%,d", total), CrtGrid.BRIGHT);
        }
        grid.put(3, 1, tr(keyed && !query() ? "crt.encodedlogistics.dsppfm.position_key" : "crt.encodedlogistics.dsppfm.position_record"));
        grid.put(3, 56, tr("crt.encodedlogistics.dsppfm.columns"));
        grid.put(3, 71, String.format(Locale.ROOT, "%d - %d", offset + 1, offset + WIDTH));
        grid.put(4, 0, CrtGrid.rulerText(WIDTH, offset + 1), CrtGrid.DIM);
        if (!loaded) {
            return;
        }
        for (int i = 0; i < headings.size(); i++) {
            grid.put(FIRST + i, 0, shift(headings.get(i)), CrtGrid.BRIGHT);
        }
        int first = FIRST + headings.size();
        for (int i = 0; i < rows() && top - from + i < lines.size(); i++) {
            grid.put(first + i, 0, shift(lines.get(top - from + i)));
        }
        if (total == 0) {
            grid.put(first, 0, tr("crt.encodedlogistics.dsppfm.empty"), CrtGrid.DIM);
        } else {
            grid.right(20, tr(top + rows() < total ? "crt.encodedlogistics.more" : "crt.encodedlogistics.bottom"), CrtGrid.NORMAL);
        }
    }

    private String shift(String line) {
        return line.length() > offset ? line.substring(offset, Math.min(line.length(), offset + WIDTH)) : "";
    }

    @Override
    void page(int direction) {
        int next = Math.max(0, Math.min(Math.max(0, total - rows()), top + direction * rows()));
        if (next == top) {
            screen.message(tr(direction > 0 ? "crt.encodedlogistics.msg.at_bottom" : "crt.encodedlogistics.msg.at_top"));
            return;
        }
        moveTo(next);
    }

    // The top to a record: from the records in hand, else the window around it asked for.
    private void moveTo(int index) {
        if (index >= from && index + rows() <= from + lines.size() || index >= from && from + lines.size() >= total) {
            top = index;
        } else {
            fetch(index);
        }
    }

    @Override
    boolean functionKey(int f) {
        if (f == 19 || f == 20) {
            int widest = 0;
            for (String line : headings) {
                widest = Math.max(widest, line.length());
            }
            for (String line : lines) {
                widest = Math.max(widest, line.length());
            }
            offset = Math.max(0, Math.min(Math.max(0, widest - WIDTH), offset + (f == 19 ? -SHIFT : SHIFT)));
            return true;
        }
        return false;
    }

    // Enter: Position to - a key (a keyed file), else a record number.
    @Override
    boolean enter() {
        String typed = position.trimmed();
        position.set("");
        if (typed.isEmpty()) {
            return true;
        }
        if (keyed && !query()) {
            fetch("*KEY " + typed);
            return true;
        }
        try {
            moveTo(Math.max(0, Math.min(Math.max(0, total - 1), Integer.parseInt(typed) - 1)));
        } catch (NumberFormatException e) {
            screen.message(tr("crt.encodedlogistics.msg.invalid_value", typed));
        }
        return true;
    }

    @Override
    @Nullable String helpField(@Nullable CrtField field) {
        return field == position ? "position" : null;
    }
}
