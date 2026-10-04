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

// WRKJOB JOB() (screen 9): a job's menu - 1 Display job status attributes, 2 Display job definition attributes, 4 Work
// with spooled files, 10 Display job log, 11 Display call stack, 30 All of the above (shown one after another). Rows
// 2-3: Job / User / Number, Host / Status / Budget. Type the number on the command line, or click an option.
final class WrkJobPanel extends CrtPanel {
    private static final int[] OPTIONS = { 1, 2, 4, 10, 11, 30 };
    private static final int[] ROWS = { 7, 8, 9, 10, 11, 12 };

    private final String job;
    private @Nullable TerminalLine info;
    private final List<String> stack = new ArrayList<>();

    WrkJobPanel(CrtTerminal screen, String job) {
        super(screen);
        this.job = job;
    }

    @Override
    String id() {
        return "WRKJOB";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.wrkjob.title");
    }

    @Override
    String prompt() {
        return tr("crt.encodedlogistics.selection");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.wrkjob");
    }

    @Override
    void shown() {
        screen.query("job " + job);
        screen.query("callstack " + job);
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (answers(response, "job")) {
            info = response.lines().isEmpty() ? null : response.lines().getFirst();
            response.message().ifPresent(screen::message);
        } else if (answers(response, "callstack")) {
            stack.clear();
            response.lines().forEach(line -> stack.add(cell(line, 0)));
        }
    }

    // number, name, user, type, host, status, budget, priority, log
    @Override
    void draw(CrtGrid grid) {
        if (info != null) {
            grid.put(2, 1, tr("crt.encodedlogistics.wrkjob.job"));
            grid.put(2, 8, cell(info, 1), CrtGrid.BRIGHT);
            grid.put(2, 21, tr("crt.encodedlogistics.wrkjob.user"));
            grid.put(2, 29, cell(info, 2), CrtGrid.BRIGHT);
            grid.put(2, 43, tr("crt.encodedlogistics.wrkjob.number"));
            grid.put(2, 53, cell(info, 0), CrtGrid.BRIGHT);
            grid.put(3, 1, tr("crt.encodedlogistics.wrkjob.host"));
            grid.put(3, 8, cell(info, 4), CrtGrid.BRIGHT);
            grid.put(3, 21, tr("crt.encodedlogistics.wrkjob.status"));
            grid.put(3, 29, cell(info, 5), CrtGrid.BRIGHT);
            grid.put(3, 43, tr("crt.encodedlogistics.wrkjob.budget"));
            grid.put(3, 53, cell(info, 6) + "%", CrtGrid.BRIGHT);
        }
        grid.put(5, 1, tr("crt.encodedlogistics.menu.select"));
        for (int i = 0; i < OPTIONS.length; i++) {
            String number = OPTIONS[i] + ".";
            grid.put(ROWS[i], 8 - number.length(), number + " " + tr("crt.encodedlogistics.wrkjob." + OPTIONS[i]));
        }
    }

    private InfoPanel status() {
        return new InfoPanel(screen, "DSPJOB", tr("crt.encodedlogistics.wrkjob.1"), List.of(
                new InfoPanel.Line(tr("crt.encodedlogistics.dspjob.job"), cell(info, 1)),
                new InfoPanel.Line(tr("crt.encodedlogistics.dspjob.user"), cell(info, 2)),
                new InfoPanel.Line(tr("crt.encodedlogistics.dspjob.number"), cell(info, 0)),
                new InfoPanel.Line(tr("crt.encodedlogistics.dspjob.status"), cell(info, 5)),
                new InfoPanel.Line(tr("crt.encodedlogistics.dspjob.host"), cell(info, 4)),
                new InfoPanel.Line(tr("crt.encodedlogistics.dspjob.budget"), cell(info, 6) + "%")));
    }

    private InfoPanel definition() {
        return new InfoPanel(screen, "DSPJOB", tr("crt.encodedlogistics.wrkjob.2"), List.of(
                new InfoPanel.Line(tr("crt.encodedlogistics.dspjob.type"), cell(info, 3).equals("INT") ? "*INTER" : "*BATCH"),
                new InfoPanel.Line(tr("crt.encodedlogistics.dspjob.priority"), cell(info, 7)),
                new InfoPanel.Line(tr("crt.encodedlogistics.dspjob.log"), cell(info, 8).equals("1") ? "*YES" : "*NO"),
                new InfoPanel.Line(tr("crt.encodedlogistics.dspjob.user"), cell(info, 2))));
    }

    private InfoPanel callStack() {
        List<InfoPanel.Line> lines = new ArrayList<>();
        for (String entry : stack) {
            lines.add(new InfoPanel.Line(entry.length() > 10 ? entry.substring(0, 10).trim() : entry, entry.length() > 11 ? entry.substring(11).trim() : ""));
        }
        if (lines.isEmpty()) {
            lines.add(new InfoPanel.Line(tr("crt.encodedlogistics.dspjob.no_stack"), ""));
        }
        return new InfoPanel(screen, "DSPJOB", tr("crt.encodedlogistics.wrkjob.11"), lines);
    }

    @Override
    boolean option(String text) {
        if (info == null) {
            return false;
        }
        String number = cell(info, 0);
        switch (text) {
            case "1" -> screen.push(status());
            case "2" -> screen.push(definition());
            case "4" -> screen.push(new WrkSplfPanel(screen, number));
            case "10" -> screen.push(new DspJobLogPanel(screen, number));
            case "11" -> screen.push(callStack());
            case "30" -> {
                // F12 / Enter walks back through them: status, definition, job log, call stack, here.
                screen.push(callStack());
                screen.push(new DspJobLogPanel(screen, number));
                screen.push(definition());
                screen.push(status());
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    void click(int row, int col, boolean doubleClick) {
        for (int i = 0; i < ROWS.length; i++) {
            if (row == ROWS[i]) {
                screen.command.set(Integer.toString(OPTIONS[i]));
                screen.focus(screen.command);
                if (doubleClick) {
                    screen.command.set("");
                    option(Integer.toString(OPTIONS[i]));
                }
            }
        }
    }
}
