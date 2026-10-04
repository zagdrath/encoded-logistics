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

// WRKSPLF (screen 13): the user's spooled files, newest first - File, Job (nnnnnn/JOBNAME), User, Pages, Status (*RDY,
// *PRT, *HLD), Created. Options: 4=Delete (confirmed), 5=Display (DSPSPLF), 6=Print (to the Line Printer: ELC1301 when
// there's none, ELC1306 out of paper). F11 sorts by created, file, status. A job given (WRKSPLF JOB(), WRKACTJOB 8)
// shows only its files.
final class WrkSplfPanel extends OsListPanel {
    private enum Sort {
        CREATED, FILE, STATUS
    }

    private final @Nullable String job;
    private Sort sort = Sort.CREATED;

    WrkSplfPanel(CrtTerminal screen, @Nullable String job) {
        super(screen);
        this.job = job;
    }

    @Override
    String id() {
        return "WRKSPLF";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.wrksplf.title");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.wrksplf");
    }

    @Override
    String query() {
        return "spooled" + (job != null ? " " + job : "");
    }

    @Override
    String legend() {
        return tr("crt.encodedlogistics.wrksplf.opts");
    }

    @Override
    String heading() {
        return tr("crt.encodedlogistics.wrksplf.cols");
    }

    @Override
    String defaultOption() {
        return "5";
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (answers(response, topic()) && sort != Sort.CREATED) {
            List<TerminalLine> lines = new ArrayList<>(response.lines());
            lines.sort(sort == Sort.FILE ? Comparator.comparing((TerminalLine line) -> cell(line, 1)) : Comparator.comparing((TerminalLine line) -> cell(line, 5)));
            setRows(lines);
            return;
        }
        super.receive(response);
    }

    @Override
    void sort() {
        sort = Sort.values()[(sort.ordinal() + 1) % Sort.values().length];
        screen.message(tr("crt.encodedlogistics.wrksplf.sorted", tr("crt.encodedlogistics.wrksplf.sort." + sort.name().toLowerCase(Locale.ROOT))));
        screen.query(query());
    }

    // id, name, job, user, pages, status, created
    @Override
    void drawRow(CrtGrid grid, int screenRow, TerminalLine row) {
        grid.put(screenRow, 5, CrtGrid.pad(cell(row, 1), 10));
        grid.put(screenRow, 17, CrtGrid.pad(cell(row, 2), 20));
        grid.put(screenRow, 39, CrtGrid.pad(cell(row, 3), 10));
        grid.put(screenRow, 51, CrtGrid.padLeft(cell(row, 4), 5));
        grid.put(screenRow, 58, CrtGrid.pad(cell(row, 5), 6));
        grid.put(screenRow, 66, CrtGrid.pad(cell(row, 6), 13));
    }

    @Override
    boolean option(String code, TerminalLine row) {
        switch (code) {
            case "5" -> then(() -> screen.push(new DspSplfPanel(screen, Integer.parseInt(cell(row, 0)), cell(row, 1))));
            case "6" -> then(() -> {
                screen.query("printsplf " + cell(row, 0));
                next();
            });
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    String deleteCommand(TerminalLine row) {
        return "";
    }

    @Override
    String rowLabel(TerminalLine row) {
        return cell(row, 1) + "  " + cell(row, 2);
    }

    @Override
    void delete(TerminalLine row) {
        screen.query("deletesplf " + cell(row, 0));
        screen.query(query());
    }
}
