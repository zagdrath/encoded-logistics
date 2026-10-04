/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;

// A pop-up window over the current screen (help, confirmations, a value's change): a single-line box at its rectangle,
// its title bright under the top edge with a divider under that, text wrapped to the window's width (less 4), its own
// fields, "More..." / "Bottom" at its bottom right inside the border and its keys bright on its last row. PageUp /
// PageDown scroll the text. While it's open only its fields take focus; Esc or F12 closes it (F3 too), and Enter is
// its own.
class CrtWindow {
    protected final CrtTerminal screen;
    final int row, col, height, width;
    final String title;
    final List<CrtField> fields = new ArrayList<>();
    // Text lines (each with its attribute) and how far it's scrolled.
    private final List<CrtTerminal.HistoryLine> lines = new ArrayList<>();
    private int top;
    private String keys = CrtPanel.tr("crt.encodedlogistics.window.keys");

    CrtWindow(CrtTerminal screen, int row, int col, int height, int width, String title) {
        this.screen = screen;
        this.row = row;
        this.col = col;
        this.height = height;
        this.width = width;
        this.title = title;
    }

    // The text rows: under the divider, down to the More / Bottom row.
    int firstTextRow() {
        return row + 3;
    }

    int textRows() {
        return height - 6;
    }

    int textCol() {
        return col + 2;
    }

    int textWidth() {
        return width - 4;
    }

    CrtWindow keys(String keys) {
        this.keys = keys;
        return this;
    }

    // Adds text, wrapped at blanks to the window's width; "\n" starts a new line.
    CrtWindow text(String text, byte attr) {
        for (String paragraph : text.split("\n", -1)) {
            for (String line : wrap(paragraph, textWidth())) {
                lines.add(new CrtTerminal.HistoryLine(line, attr));
            }
        }
        return this;
    }

    CrtWindow text(String text) {
        return text(text, CrtGrid.NORMAL);
    }

    static List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        // Keep a line's indent on the lines it wraps onto.
        int indent = 0;
        while (indent < text.length() && text.charAt(indent) == ' ') {
            indent++;
        }
        String rest = text;
        while (rest.length() > width) {
            int cut = rest.lastIndexOf(' ', width);
            if (cut <= indent) {
                cut = width;
            }
            out.add(rest.substring(0, cut).stripTrailing());
            rest = " ".repeat(Math.min(indent, width / 2)) + rest.substring(cut).stripLeading();
        }
        out.add(rest);
        return out;
    }

    void draw(CrtGrid grid) {
        grid.box(row, col, height, width, row + 2);
        grid.put(row + 1, col + 2, CrtGrid.pad(title, width - 4), CrtGrid.BRIGHT);
        int rows = textRows();
        for (int i = 0; i < rows && top + i < lines.size(); i++) {
            CrtTerminal.HistoryLine line = lines.get(top + i);
            grid.put(firstTextRow() + i, textCol(), CrtGrid.pad(line.text(), textWidth()), line.attr());
        }
        drawBody(grid);
        if (!lines.isEmpty()) {
            String more = CrtPanel.tr(top + rows < lines.size() ? "crt.encodedlogistics.more" : "crt.encodedlogistics.bottom");
            grid.put(row + height - 3, col + width - 4 - more.length(), more, CrtGrid.NORMAL);
        }
        grid.put(row + height - 2, col + 2, CrtGrid.pad(keys, width - 4), CrtGrid.BRIGHT);
        for (CrtField field : fields) {
            field.draw(grid);
        }
    }

    // Labels and the like, over the text area.
    void drawBody(CrtGrid grid) {}

    void page(int direction) {
        top = Math.max(0, Math.min(Math.max(0, lines.size() - textRows()), top + direction * textRows()));
    }

    // Enter; true when it handled it (it may close itself with screen.closeWindow()).
    boolean enter() {
        screen.closeWindow();
        return true;
    }

    // A function key while it's open, besides F3 / F12; true when it took it.
    boolean functionKey(int f) {
        return false;
    }

    void click(int row, int col, boolean doubleClick) {}

    // Closed by Esc / F3 / F12.
    void cancelled() {}
}
