/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;

import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// DSPROUTE and DSPNBR (docs/routing/layouts): a Router's route table, or its neighbours. The server's first line (the
// Router and its summary) goes on row 2, the rest (the column headings, the rows, a note) from row 4, rolled with
// PageUp / PageDown; refreshed every two seconds. F11 shows the neighbours, F10 the route table; Enter goes back.
final class RoutingPanel extends CrtPanel {
    private static final int HEAD = 2, FIRST = 4, ROWS = 16;
    private final String router;
    private boolean neighbours;
    private List<TerminalLine> lines = new ArrayList<>();
    private int top, timer;

    RoutingPanel(CrtTerminal screen, String router, boolean neighbours) {
        super(screen);
        this.router = router;
        this.neighbours = neighbours;
    }

    @Override
    String id() {
        return neighbours ? "DSPNBR" : "DSPROUTE";
    }

    @Override
    String title() {
        return tr(neighbours ? "crt.encodedlogistics.dspnbr.title" : "crt.encodedlogistics.dsproute.title");
    }

    @Override
    String prompt() {
        return "";
    }

    @Override
    String keys() {
        return tr(neighbours ? "crt.encodedlogistics.fkeys.dspnbr" : "crt.encodedlogistics.fkeys.dsproute");
    }

    private String topic() {
        return neighbours ? "neighbours" : "routes";
    }

    @Override
    void shown() {
        screen.send(TerminalService.QUERY, topic() + " " + router);
    }

    @Override
    void tick() {
        if (++timer >= 40) {
            timer = 0;
            shown();
        }
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (response.kind() == TerminalService.QUERY && response.topic().equals(topic())) {
            lines = response.lines();
            top = Math.min(top, Math.max(0, lines.size() - 1 - ROWS));
        }
    }

    @Override
    void draw(CrtGrid grid) {
        if (lines.isEmpty()) {
            return;
        }
        grid.put(HEAD, 1, lines.getFirst().text(), (byte) lines.getFirst().attr());
        // The column headings stay; the rows under them roll.
        if (lines.size() > 1) {
            grid.put(FIRST, 1, lines.get(1).text(), (byte) lines.get(1).attr());
        }
        for (int i = 0; i < ROWS - 1 && 2 + top + i < lines.size(); i++) {
            TerminalLine line = lines.get(2 + top + i);
            grid.put(FIRST + 1 + i, 1, line.text(), (byte) line.attr());
        }
        if (lines.size() - 2 > ROWS - 1) {
            grid.right(20, tr(top + ROWS - 1 < lines.size() - 2 ? "crt.encodedlogistics.more" : "crt.encodedlogistics.bottom"), CrtGrid.NORMAL);
        }
    }

    @Override
    void page(int direction) {
        int rows = ROWS - 1, max = Math.max(0, lines.size() - 2 - rows);
        top = Math.max(0, Math.min(max, top + direction * rows));
    }

    @Override
    boolean functionKey(int f) {
        if (f == 11 && !neighbours || f == 10 && neighbours) {
            neighbours = !neighbours;
            lines = new ArrayList<>();
            top = 0;
            shown();
            return true;
        }
        return false;
    }

    @Override
    boolean enter() {
        screen.back();
        return true;
    }
}
