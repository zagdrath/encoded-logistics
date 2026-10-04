/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.zagdrath.encodedlogistics.elcl.screen.SpoolService;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// DSPSPLF (screen 13b, the compile listing's screen 7): a spooled file's lines. Rows 2-4: the file and where it's at
// (page / line), Control (+n / -n lines, T top, B bottom, or a page number) and the columns shown, Find (Enter looks
// for it from the top line on); row 5 the column ruler (dim); rows 6-19 the page. F19 / F20 shift 40 columns (a
// listing's change dates are past column 80); PageUp / PageDown roll it.
final class DspSplfPanel extends CrtPanel {
    private static final int FIRST = 6, ROWS = 14, WIDTH = 78, SHIFT = 40;

    private final int id;
    private final String name;
    private final CrtField control, find;
    private final List<String> lines = new ArrayList<>();
    private int top, offset;
    private boolean loaded;

    DspSplfPanel(CrtTerminal screen, int id, String name) {
        super(screen);
        this.id = id;
        this.name = name;
        control = new CrtField(3, 21, 4, "");
        find = new CrtField(4, 21, 30, "");
        fields.add(control);
        fields.add(find);
    }

    // For the tests: the lines in hand.
    DspSplfPanel(CrtTerminal screen, String name, List<String> lines) {
        this(screen, 0, name);
        this.lines.addAll(lines);
        loaded = true;
    }

    @Override
    String id() {
        return "DSPSPLF";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.dspsplf.title");
    }

    @Override
    String prompt() {
        return "";
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.dspsplf");
    }

    @Override
    void shown() {
        if (!loaded) {
            screen.query("splf " + id);
        }
    }

    // F5: the file again.
    @Override
    void refresh() {
        loaded = false;
        shown();
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (answers(response, "splf")) {
            lines.clear();
            for (TerminalLine line : response.lines().subList(Math.min(1, response.lines().size()), response.lines().size())) {
                lines.add(cell(line, 0));
            }
            loaded = true;
        }
    }

    @Override
    void draw(CrtGrid grid) {
        int page = top / SpoolService.LINES_PER_PAGE + 1, line = top % SpoolService.LINES_PER_PAGE + 1;
        grid.put(2, 1, tr("crt.encodedlogistics.dspsplf.file"));
        grid.put(2, 21, CrtGrid.pad(name, 10), CrtGrid.BRIGHT);
        grid.put(2, 34, String.format(Locale.ROOT, "%s   %d/%d", tr("crt.encodedlogistics.dspsplf.page_line"), page, line));
        grid.put(3, 1, tr("crt.encodedlogistics.dspsplf.control"));
        grid.put(3, 34, String.format(Locale.ROOT, "%s     %d - %d", tr("crt.encodedlogistics.dspsplf.columns"), offset + 1, offset + WIDTH));
        grid.put(4, 1, tr("crt.encodedlogistics.dspsplf.find"));
        grid.ruler(5, 0, WIDTH, CrtGrid.DIM, offset + 1);
        for (int i = 0; i < ROWS && top + i < lines.size(); i++) {
            String text = lines.get(top + i);
            grid.put(FIRST + i, 0, text.length() > offset ? text.substring(offset, Math.min(text.length(), offset + WIDTH)) : "");
        }
        if (!lines.isEmpty()) {
            grid.right(20, tr(top + ROWS < lines.size() ? "crt.encodedlogistics.more" : "crt.encodedlogistics.bottom"), CrtGrid.NORMAL);
        }
    }

    @Override
    void page(int direction) {
        top = Math.max(0, Math.min(Math.max(0, lines.size() - ROWS), top + direction * ROWS));
    }

    @Override
    boolean functionKey(int f) {
        if (f == 19 || f == 20) {
            int widest = lines.stream().mapToInt(String::length).max().orElse(0);
            offset = Math.max(0, Math.min(Math.max(0, widest - WIDTH), offset + (f == 19 ? -SHIFT : SHIFT)));
            return true;
        }
        return false;
    }

    // Enter: Control's position, then Find from the line after the top one.
    @Override
    boolean enter() {
        String position = control.trimmed().toUpperCase(Locale.ROOT);
        control.set("");
        if (!position.isEmpty()) {
            try {
                if (position.equals("T")) {
                    top = 0;
                } else if (position.equals("B")) {
                    top = Math.max(0, lines.size() - ROWS);
                } else if (position.startsWith("+") || position.startsWith("-")) {
                    top += Integer.parseInt(position);
                } else {
                    top = (Integer.parseInt(position) - 1) * SpoolService.LINES_PER_PAGE;
                }
                top = Math.max(0, Math.min(Math.max(0, lines.size() - 1), top));
            } catch (NumberFormatException e) {
                screen.message(tr("crt.encodedlogistics.msg.invalid_value", position));
                return true;
            }
        }
        String text = find.trimmed().toUpperCase(Locale.ROOT);
        if (!text.isEmpty()) {
            for (int i = 1; i <= lines.size(); i++) {
                int at = (top + i) % lines.size();
                if (lines.get(at).toUpperCase(Locale.ROOT).contains(text)) {
                    top = at;
                    screen.message(tr("crt.encodedlogistics.edit.found", find.trimmed()));
                    return true;
                }
            }
            screen.message(tr("crt.encodedlogistics.edit.not_found", find.trimmed()));
        }
        return true;
    }

    @Override
    String helpField(CrtField field) {
        return field == control ? "control" : field == find ? "find" : null;
    }
}
