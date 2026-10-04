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

// WRKCRFJOB (Main Menu option 2; WRKJOB until the Terminal OS took that name for its Work with Job): the network's
// crafting jobs, active and queued - Opt, Job, Item, Qty, Status (Active, Waiting, Recall), Progress (bar
// and %), Scheduler - refreshed every two seconds. Options: 4=Cancel (Enter again, with no other option typed, to
// confirm; F5 or any other option forgets it), 5=Display; several are done in turn, an unknown one put back.
final class JobsPanel extends ListPanel<TerminalLine> {
    private static final int REFRESH = 40;
    private int timer;
    private final List<String> cancelling = new ArrayList<>();

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
        reload();
        next();
    }

    private void reload() {
        screen.send(TerminalService.QUERY, "jobs");
    }

    @Override
    void tick() {
        if (++timer >= REFRESH) {
            timer = 0;
            reload();
        }
    }

    // F5: the list again, any cancel waiting for Enter forgotten.
    @Override
    void refresh() {
        cancelling.clear();
        reload();
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
        // Over the rows' columns (drawn from 5): Progress the bar and %, Scheduler its number and kind.
        grid.put(6, 0, "Opt  Job   Item                        Qty  Status    Progress      Scheduler", CrtGrid.BRIGHT);
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
        if (!cancelling.isEmpty() && !anyOptions()) {
            for (String job : cancelling) {
                screen.runCommand("cancel job " + job);
            }
            cancelling.clear();
            return true;
        }
        cancelling.clear();
        return super.enter();
    }

    @Override
    boolean process(List<Option<TerminalLine>> chosen) {
        pending.clear();
        List<String> cancel = new ArrayList<>();
        for (Option<TerminalLine> option : chosen) {
            String job = number(option.row());
            switch (option.option().trim()) {
                case "4" -> cancel.add(job);
                case "5" -> then(() -> screen.push(new TextPanel(screen, "DSPJOB", tr("crt.encodedlogistics.job.title"), "job " + job)));
                default -> {
                    pending.clear();
                    return invalid(option);
                }
            }
        }
        // The cancels last: Enter again confirms them.
        if (!cancel.isEmpty()) {
            then(() -> {
                cancelling.addAll(cancel);
                screen.message(tr("crt.encodedlogistics.jobs.confirm", String.join(", ", cancel)));
            });
        }
        next();
        return true;
    }
}
