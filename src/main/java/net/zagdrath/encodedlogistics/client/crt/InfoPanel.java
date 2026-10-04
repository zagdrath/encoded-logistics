/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.List;

// A display screen built on the client from what a list already holds (WRKLIB 5=Display): a label and a value per row
// from row 3, labels with their dot leaders. Enter or F12 goes back.
final class InfoPanel extends CrtPanel {
    record Line(String label, String value) {}

    private final String id, title;
    private final List<Line> lines;

    InfoPanel(CrtTerminal screen, String id, String title, List<Line> lines) {
        super(screen);
        this.id = id;
        this.title = title;
        this.lines = lines;
    }

    @Override
    String id() {
        return id;
    }

    @Override
    String title() {
        return title;
    }

    @Override
    String prompt() {
        return "";
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.display");
    }

    @Override
    void draw(CrtGrid grid) {
        for (int i = 0; i < lines.size() && i < 16; i++) {
            Line line = lines.get(i);
            grid.put(3 + i, 1, PrompterPanel.leaders(line.label(), 1, 30) + " :");
            grid.put(3 + i, 34, line.value(), CrtGrid.BRIGHT);
        }
        grid.put(20, 1, tr("crt.encodedlogistics.enter_continue"));
    }

    @Override
    boolean enter() {
        screen.back();
        return true;
    }
}
