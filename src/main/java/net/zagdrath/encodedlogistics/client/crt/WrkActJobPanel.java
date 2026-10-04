/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// WRKACTJOB (screen 8): the system's script jobs, interactive and batch - Job, User, Type (INT, BCH), Host (the desk or
// job host), Status (*ACTIVE bright, *WAIT, *JOBQ, *HELD, *MSGW), Budget (% of its own per-tick budget). Row 2: the
// budget used by them all, the time since the screen opened (F5 starts it again), active jobs, job hosts busy. Options:
// 2=Change (a window: priority and LOG, CHGJOB), 3=Hold, 4=End (confirmed, *CNTRLD; F11 in the confirmation *IMMED),
// 5=Work with (WRKJOB), 6=Release, 8=Spooled files. F11 sorts by job, user, status (or as listed). No auto-refresh (F5).
final class WrkActJobPanel extends OsListPanel {
    private enum Sort {
        LISTED, JOB, USER, STATUS
    }

    private Sort sort = Sort.LISTED;
    private int opened;
    private @Nullable TerminalLine summary;

    WrkActJobPanel(CrtTerminal screen) {
        super(screen);
        opened = screen.ticks();
    }

    @Override
    String id() {
        return "WRKACTJOB";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.wrkactjob.title");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.wrkactjob");
    }

    @Override
    String query() {
        return "jobs";
    }

    @Override
    String legend() {
        return tr("crt.encodedlogistics.wrkactjob.opts");
    }

    @Override
    String heading() {
        return tr("crt.encodedlogistics.wrkactjob.cols");
    }

    @Override
    String defaultOption() {
        return "5";
    }

    @Override
    void refresh() {
        opened = screen.ticks();
        super.refresh();
    }

    // The first line is the summary (budget %, active, hosts busy, hosts), the rest the jobs.
    @Override
    void receive(CrtResponsePayload response) {
        if (answers(response, topic())) {
            List<TerminalLine> lines = new ArrayList<>(response.lines());
            summary = lines.isEmpty() ? null : lines.removeFirst();
            lines.sort(switch (sort) {
                case LISTED -> (a, b) -> 0;
                case JOB -> Comparator.comparing((TerminalLine line) -> cell(line, 1)).thenComparing(line -> cell(line, 0));
                case USER -> Comparator.comparing((TerminalLine line) -> cell(line, 2)).thenComparing(line -> cell(line, 1));
                case STATUS -> Comparator.comparing((TerminalLine line) -> cell(line, 5)).thenComparing(line -> cell(line, 1));
            });
            setRows(lines);
            response.message().ifPresent(screen::message);
            return;
        }
        super.receive(response);
    }

    @Override
    void sort() {
        sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
        screen.message(tr("crt.encodedlogistics.wrkactjob.sorted", tr("crt.encodedlogistics.wrkactjob.sort." + sort.name().toLowerCase(Locale.ROOT))));
        screen.query(query());
    }

    @Override
    void drawTop(CrtGrid grid) {
        int seconds = Math.max(0, screen.ticks() - opened) / 20;
        String elapsed = String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
        String budget = summary != null ? cell(summary, 0) : "0", active = summary != null ? cell(summary, 1) : "0";
        String hosts = summary != null ? cell(summary, 2) + "/" + cell(summary, 3) : "0/0";
        grid.put(2, 1, String.format(Locale.ROOT, "%s %4s%%    %s  %s    %s %3s    %s  %s", tr("crt.encodedlogistics.wrkactjob.budget"), budget,
                tr("crt.encodedlogistics.wrkactjob.elapsed"), elapsed, tr("crt.encodedlogistics.wrkactjob.active"), active,
                tr("crt.encodedlogistics.wrkactjob.hosts"), hosts));
    }

    // number, name, user, type, host, status, budget, priority, log
    @Override
    void drawRow(CrtGrid grid, int screenRow, TerminalLine row) {
        String status = cell(row, 5);
        grid.put(screenRow, 5, CrtGrid.pad(cell(row, 1), 10));
        grid.put(screenRow, 17, CrtGrid.pad(cell(row, 2), 10));
        grid.put(screenRow, 29, CrtGrid.pad(cell(row, 3), 3));
        grid.put(screenRow, 36, CrtGrid.pad(cell(row, 4), 12));
        grid.put(screenRow, 50, CrtGrid.pad(status, 9), status.equals("*ACTIVE") ? CrtGrid.BRIGHT : CrtGrid.NORMAL);
        if (!status.equals("*JOBQ") && !status.equals("*HELD")) {
            grid.put(screenRow, 61, CrtGrid.padLeft(cell(row, 6) + "%", 5));
        }
    }

    private static String qualified(TerminalLine row) {
        return cell(row, 0) + "/" + cell(row, 2) + "/" + cell(row, 1);
    }

    @Override
    boolean option(String code, TerminalLine row) {
        String job = cell(row, 0);
        switch (code) {
            case "2" -> then(() -> window(new FormWindow(screen, tr("crt.encodedlogistics.wrkactjob.change", qualified(row)),
                    tr("crt.encodedlogistics.wrkactjob.change_text"),
                    values -> screen.runCommand("CHGJOB JOB(" + job + ") JOBPTY(" + values.get(0) + ") LOG(" + values.get(1) + ")"))
                    .field(tr("crt.encodedlogistics.wrkactjob.priority"), 4, cell(row, 7), "1-9")
                    .field(tr("crt.encodedlogistics.wrkactjob.log"), 4, cell(row, 8).equals("1") ? "*YES" : "*NO", "*NO, *YES")));
            case "3" -> then(() -> screen.runCommand("HLDJOB JOB(" + job + ")"));
            case "6" -> then(() -> screen.runCommand("RLSJOB JOB(" + job + ")"));
            case "5" -> then(() -> screen.push(new WrkJobPanel(screen, job)));
            case "8" -> then(() -> screen.push(new WrkSplfPanel(screen, job)));
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    String deleteLabel() {
        return tr("crt.encodedlogistics.confirm.end");
    }

    @Override
    String rowLabel(TerminalLine row) {
        return qualified(row);
    }

    @Override
    String deleteCommand(TerminalLine row) {
        return "ENDJOB JOB(" + cell(row, 0) + ") OPTION(*CNTRLD)";
    }

    @Override
    @Nullable Runnable immediate(List<TerminalLine> rows) {
        return () -> rows.forEach(row -> screen.runCommand("ENDJOB JOB(" + cell(row, 0) + ") OPTION(*IMMED)"));
    }
}
