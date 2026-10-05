/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// DSPFD (Display File Description): a file's attributes - library, attribute, text, record format, source member (and
// whether it changed since the file was made), when it was made, its records of the limit, record length, storage,
// key - then its fields: name, type, length, decimals, key position, text (or column heading). From row 2, rolled
// with PageUp / PageDown; Enter or F12 goes back. Its data is FileQueries' filedesc.
final class DspFdPanel extends CrtPanel {
    private static final int FIRST = 2, ROWS = 18;

    private record Line(String label, String value, byte attr) {}

    private final String spec;
    private final List<Line> lines = new ArrayList<>();
    private int top;

    DspFdPanel(CrtTerminal screen, String spec) {
        super(screen);
        this.spec = spec;
    }

    @Override
    String id() {
        return "DSPFD";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.dspfd.title");
    }

    @Override
    String prompt() {
        return "";
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.text");
    }

    @Override
    void shown() {
        screen.query("filedesc " + spec);
    }

    private void label(String key, String value) {
        lines.add(new Line(tr("crt.encodedlogistics.dspfd." + key), value, CrtGrid.BRIGHT));
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (!answers(response, "filedesc")) {
            return;
        }
        response.message().ifPresent(screen::message);
        if (response.lines().isEmpty()) {
            screen.back();
            return;
        }
        lines.clear();
        TerminalLine head = response.lines().getFirst();
        boolean system = cell(head, 15).equals("1");
        label("file", cell(head, 0) + "/" + cell(head, 1));
        label("attribute", cell(head, 2) + (system ? "   " + tr("crt.encodedlogistics.dspfd.system") : ""));
        label("text", cell(head, 3));
        label("format", cell(head, 4) + (cell(head, 5).isEmpty() ? "" : "   " + cell(head, 5)));
        label("source", cell(head, 6) + (cell(head, 14).equals("1") ? "   " + tr("crt.encodedlogistics.dspfd.changed") : ""));
        label("created", cell(head, 7));
        label("records", String.format(Locale.ROOT, "%,d", Long.parseLong(cell(head, 8)))
                + (system ? "" : "   " + tr("crt.encodedlogistics.dspfd.limit", String.format(Locale.ROOT, "%,d", Long.parseLong(cell(head, 9))))));
        label("length", cell(head, 10));
        label("size", cell(head, 11));
        label("key", cell(head, 12).isEmpty() ? tr("crt.encodedlogistics.dspfd.arrival")
                : cell(head, 12) + (cell(head, 13).equals("1") ? "   " + tr("crt.encodedlogistics.dspfd.unique") : ""));
        lines.add(new Line("", "", CrtGrid.NORMAL));
        lines.add(new Line(tr("crt.encodedlogistics.dspfd.cols"), null, CrtGrid.BRIGHT));
        for (TerminalLine field : response.lines().subList(1, response.lines().size())) {
            String type = cell(field, 1), decimals = type.equals("S") || type.equals("P") ? cell(field, 3) : "";
            List<String> heading = new ArrayList<>();
            for (int i = 6; i < field.cells().size(); i++) {
                heading.add(cell(field, i));
            }
            String text = !cell(field, 5).isEmpty() ? cell(field, 5) : String.join(" ", heading);
            lines.add(new Line(String.format(Locale.ROOT, "%-10s  %-4s %7s  %3s  %3s  %s", cell(field, 0), type, cell(field, 2), decimals, cell(field, 4),
                    text), null, CrtGrid.NORMAL));
        }
        top = Math.min(top, Math.max(0, lines.size() - ROWS));
    }

    @Override
    void draw(CrtGrid grid) {
        for (int i = 0; i < ROWS && top + i < lines.size(); i++) {
            Line line = lines.get(top + i);
            if (line.value() == null) {
                grid.put(FIRST + i, 1, line.label(), line.attr());
            } else if (!line.label().isEmpty()) {
                grid.put(FIRST + i, 1, PrompterPanel.leaders(line.label(), 1, 27) + " :");
                grid.put(FIRST + i, 32, line.value(), line.attr());
            }
        }
        if (lines.size() > ROWS) {
            grid.right(20, tr(top + ROWS < lines.size() ? "crt.encodedlogistics.more" : "crt.encodedlogistics.bottom"), CrtGrid.NORMAL);
        }
        grid.put(20, 1, tr("crt.encodedlogistics.enter_continue"));
    }

    @Override
    void page(int direction) {
        top = Math.max(0, Math.min(Math.max(0, lines.size() - ROWS), top + direction * ROWS));
    }

    @Override
    boolean enter() {
        screen.back();
        return true;
    }
}
