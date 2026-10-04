/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// DSPJOBLOG JOB() (screen 9b): a job's log, newest at the bottom (it opens there) - the commands it logged as
// ">> command" (bright), each message indented with its ID and text. F10 shows every message's details (ID, severity,
// from, time, text) in a window. PageUp / PageDown roll it.
final class DspJobLogPanel extends CrtPanel {
    private static final int FIRST = 4, ROWS = 16;

    private final String job;
    private @Nullable TerminalLine info;
    private final List<TerminalLine> entries = new ArrayList<>();
    private int top;

    DspJobLogPanel(CrtTerminal screen, String job) {
        super(screen);
        this.job = job;
    }

    @Override
    String id() {
        return "DSPJOBLOG";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.dspjoblog.title");
    }

    @Override
    String prompt() {
        return "";
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.dspjoblog");
    }

    @Override
    void shown() {
        screen.query("joblog " + job);
    }

    // The job (as the jobs query gives it), then command (1 / 0), id, severity, text, from, time per entry.
    @Override
    void receive(CrtResponsePayload response) {
        if (answers(response, "joblog")) {
            entries.clear();
            if (!response.lines().isEmpty()) {
                info = response.lines().getFirst();
                entries.addAll(response.lines().subList(1, response.lines().size()));
            }
            top = Math.max(0, entries.size() - ROWS);
            response.message().ifPresent(screen::message);
        }
    }

    @Override
    void draw(CrtGrid grid) {
        if (info != null) {
            grid.put(2, 1, tr("crt.encodedlogistics.dspjoblog.job"));
            grid.put(2, 13, cell(info, 1), CrtGrid.BRIGHT);
            grid.put(2, 26, tr("crt.encodedlogistics.dspjoblog.user"));
            grid.put(2, 40, cell(info, 2), CrtGrid.BRIGHT);
            grid.put(2, 54, tr("crt.encodedlogistics.dspjoblog.number"));
            grid.put(2, 70, cell(info, 0), CrtGrid.BRIGHT);
        }
        for (int i = 0; i < ROWS && top + i < entries.size(); i++) {
            TerminalLine entry = entries.get(top + i);
            if (cell(entry, 0).equals("1")) {
                grid.put(FIRST + i, 1, ">> " + cell(entry, 3), CrtGrid.BRIGHT);
            } else {
                grid.put(FIRST + i, 4, cell(entry, 1) + "  " + cell(entry, 3));
            }
        }
        if (!entries.isEmpty()) {
            grid.right(20, tr(top + ROWS < entries.size() ? "crt.encodedlogistics.more" : "crt.encodedlogistics.bottom"), CrtGrid.NORMAL);
        }
    }

    @Override
    void page(int direction) {
        top = Math.max(0, Math.min(Math.max(0, entries.size() - ROWS), top + direction * ROWS));
    }

    // F10: every message's details.
    @Override
    boolean functionKey(int f) {
        if (f != 10) {
            return false;
        }
        CrtWindow window = new CrtWindow(screen, 3, 4, 17, 72, tr("crt.encodedlogistics.dspjoblog.details"));
        for (TerminalLine entry : entries) {
            if (!cell(entry, 0).equals("1")) {
                window.text(tr("crt.encodedlogistics.dspjoblog.detail", cell(entry, 1), cell(entry, 2), cell(entry, 4), cell(entry, 5)), CrtGrid.BRIGHT);
                window.text("  " + cell(entry, 3));
            }
        }
        screen.openWindow(window);
        return true;
    }

    @Override
    boolean enter() {
        screen.back();
        return true;
    }
}
