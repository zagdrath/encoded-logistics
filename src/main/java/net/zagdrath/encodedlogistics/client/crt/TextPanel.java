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

// A display screen: lines from a query (an item's, job's or device's details; the network's status), from row 3,
// rolled with PageUp / PageDown. Enter goes back.
class TextPanel extends CrtPanel {
    private static final int FIRST = 3, ROWS = 17;
    private final String id, title, query;
    private List<TerminalLine> lines = new ArrayList<>();
    private int top;

    TextPanel(CrtScreen screen, String id, String title, String query) {
        super(screen);
        this.id = id;
        this.title = title;
        this.query = query;
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
        return "F3=Exit   F5=Refresh   F12=Cancel";
    }

    @Override
    void shown() {
        screen.send(TerminalService.QUERY, query);
    }

    @Override
    void receive(CrtResponsePayload response) {
        String topic = query.split(" ", 2)[0];
        if (response.kind() == TerminalService.QUERY && response.topic().equals(topic)) {
            lines = response.lines();
            top = Math.min(top, Math.max(0, lines.size() - ROWS));
        }
    }

    @Override
    void draw(CrtGrid grid) {
        for (int i = 0; i < ROWS && top + i < lines.size(); i++) {
            TerminalLine line = lines.get(top + i);
            grid.put(FIRST + i, 2, line.text(), (byte) line.attr());
        }
        if (lines.size() > ROWS) {
            grid.right(20, tr(top + ROWS < lines.size() ? "crt.encodedlogistics.more" : "crt.encodedlogistics.bottom"), CrtGrid.NORMAL);
        }
        grid.put(20, 0, tr("crt.encodedlogistics.enter_continue"));
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
