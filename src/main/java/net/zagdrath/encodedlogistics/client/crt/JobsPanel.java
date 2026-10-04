/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// WRKCRFJOB (Main Menu option 2; WRKJOB until the Terminal OS took that name for its Work with Job): the network's
// crafting jobs, active and queued - Opt, Job, Item, Qty, Status (Active, Waiting, Recall), Progress (bar
// and %), Scheduler - refreshed every two seconds. Options: 4=Cancel (Enter again to confirm), 5=Display.
final class JobsPanel extends ListPanel<TerminalLine> {
    private static final int REFRESH = 40;
    private int timer;
    private @Nullable String confirming;

    JobsPanel(CrtTerminal screen) {
        super(screen);
    }

    @Override
    String id() {
        return "WRKCRFJOB";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.jobs.title");
    }

    @Override
    int firstRow() {
        return 7;
    }

    @Override
    int pageSize() {
        return 12;
    }

    // A row's job number.
    @Override
    Object key(TerminalLine row) {
        return number(row);
    }

    private static String number(TerminalLine row) {
        return row.cells().isEmpty() ? "" : row.cells().getFirst().text().getString().trim();
    }

    @Override
    String defaultOption() {
        return "5";
    }

    @Override
    void shown() {
        screen.send(TerminalService.QUERY, "jobs");
    }

    @Override
    void tick() {
        if (++timer >= REFRESH) {
            timer = 0;
            shown();
        }
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (response.kind() == TerminalService.QUERY && response.topic().equals("jobs")) {
            setRows(response.lines());
        } else if (response.kind() == TerminalService.COMMAND) {
            shown();
        }
    }

    @Override
    void drawHead(CrtGrid grid) {
        grid.put(3, 0, tr("crt.encodedlogistics.type_options"));
        grid.put(4, 0, tr("crt.encodedlogistics.jobs.opts"));
        grid.put(6, 0, "Opt  Job   Item                        Qty  Status    Progress  Scheduler", CrtGrid.BRIGHT);
        if (rows.isEmpty()) {
            grid.put(8, 5, tr("crt.encodedlogistics.msg.no_jobs"), CrtGrid.DIM);
        }
    }

    @Override
    void drawRow(CrtGrid grid, int screenRow, TerminalLine row) {
        grid.put(screenRow, 5, row.text(), (byte) row.attr());
    }

    @Override
    boolean enter() {
        if (confirming != null) {
            String job = confirming;
            confirming = null;
            screen.runCommand("cancel job " + job);
            return true;
        }
        return super.enter();
    }

    @Override
    boolean process(List<Option<TerminalLine>> chosen) {
        for (Option<TerminalLine> option : chosen) {
            String job = number(option.row());
            switch (option.option()) {
                case "4" -> {
                    confirming = job;
                    screen.message(tr("crt.encodedlogistics.jobs.confirm", job));
                    return true;
                }
                case "5" -> {
                    screen.push(new TextPanel(screen, "DSPJOB", tr("crt.encodedlogistics.job.title"), "job " + job));
                    return true;
                }
                default -> {
                    screen.message(tr("crt.encodedlogistics.msg.invalid_option", option.option()));
                    return true;
                }
            }
        }
        return false;
    }
}
