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

// DSPOPT (docs/optical/layouts/dspopt.txt): the network's optical gear - multiplexers, amplifiers, transport shelves -
// and their spans: path, length, lanes delivered, loss, quality, protection; under them the last protection switch.
// F10 shows their channels instead (again: back). The server's first line (the network and its counts) goes on row
// 2, the rest from row 4, rolled with PageUp / PageDown; refreshed every two seconds. Enter goes back.
final class DspOptPanel extends CrtPanel {
    private static final int HEAD = 2, FIRST = 4, ROWS = 16;
    private boolean channels;
    private List<TerminalLine> lines = new ArrayList<>();
    private int top, timer;

    DspOptPanel(CrtTerminal screen) {
        super(screen);
    }

    @Override
    String id() {
        return "DSPOPT";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.dspopt.title");
    }

    @Override
    String prompt() {
        return "";
    }

    @Override
    String keys() {
        return tr(channels ? "crt.encodedlogistics.fkeys.dspopt_channels" : "crt.encodedlogistics.fkeys.dspopt");
    }

    private String topic() {
        return channels ? "opticalchannels" : "optical";
    }

    @Override
    void shown() {
        screen.send(TerminalService.QUERY, topic());
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
        if (f == 10) {
            channels = !channels;
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
