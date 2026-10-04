/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// A Terminal OS "Work with" screen (screens handoff C.3, screens 3, 4, 8, 10-14): its rows from a screen query (a
// line of cells each, the first cell its key), " Type options, press Enter." on row 3, the option legend on row 4 at
// column 3, the column headings bright on row 6, the list from row 7. The options chosen are done one after another:
// one that opens a window or a screen waits for it to finish (next()), and every step waits for the answers to the
// commands sent before it (however many a step ran); 4=Delete-style ones are confirmed together.
// Commands it runs come back as their message, and the list refreshes.
abstract class OsListPanel extends ListPanel<TerminalLine> {
    OsListPanel(CrtTerminal screen) {
        super(screen);
    }

    // The screen query that lists the rows ("libraries", "members ZAGLIB"), and its topic.
    abstract String query();

    String topic() {
        return query().split(" ", 2)[0];
    }

    // The legend (after its leading blanks: "2=Change   4=Delete ...") and the heading row.
    abstract String legend();

    abstract String heading();

    @Override
    int firstRow() {
        return 7;
    }

    @Override
    int pageSize() {
        return 13;
    }

    @Override
    Object key(TerminalLine row) {
        return cell(row, 0);
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.list");
    }

    @Override
    void shown() {
        screen.query(query());
        next();
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (answers(response, topic())) {
            setRows(response.lines());
            response.message().ifPresent(screen::message);
        } else if (response.kind() == TerminalService.COMMAND) {
            screen.query(query());
            next();
        }
    }

    // Rows 2-6: anything of its own on row 2 (drawTop), the options line, the legend, the headings.
    @Override
    void drawHead(CrtGrid grid) {
        drawTop(grid);
        grid.put(3, 1, tr("crt.encodedlogistics.type_options"));
        grid.put(4, 3, legend());
        grid.put(6, 0, heading(), CrtGrid.BRIGHT);
    }

    void drawTop(CrtGrid grid) {}

    // --- Doing the options ---

    // The confirmation for every row given the deleting option: Enter deletes each (F11, where the screen has it,
    // its other way: ENDJOB *IMMED).
    void confirmAll(List<TerminalLine> rows) {
        if (rows.isEmpty()) {
            return;
        }
        StringBuilder text = new StringBuilder(tr("crt.encodedlogistics.confirm.option", deleteLabel())).append("\n");
        for (TerminalLine row : rows) {
            text.append("\n   ").append(rowLabel(row));
        }
        Runnable f11 = immediate(rows);
        then(() -> screen.confirm(text.toString(), () -> {
            for (TerminalLine row : rows) {
                delete(row);
            }
            next();
        }, f11 == null ? null : () -> {
            f11.run();
            next();
        }));
    }

    // The deleting option as the confirmation names it ("4=Delete").
    String deleteLabel() {
        return tr("crt.encodedlogistics.confirm.delete");
    }

    // A row as the confirmation lists it.
    String rowLabel(TerminalLine row) {
        return cell(row, 0);
    }

    // F11 in the confirmation, for screens with a second way to delete; null for none.
    @Nullable Runnable immediate(List<TerminalLine> rows) {
        return null;
    }

    // Runs the rows' options in order: each option code to a step (false: not an option this screen has).
    abstract boolean option(String code, TerminalLine row);

    // Options that delete (confirmed together, after the others).
    String deleteOption() {
        return "4";
    }

    abstract String deleteCommand(TerminalLine row);

    // Deletes a row's object (confirmed): its delete command, by default.
    void delete(TerminalLine row) {
        screen.runCommand(deleteCommand(row));
    }

    @Override
    boolean process(List<Option<TerminalLine>> chosen) {
        pending.clear();
        List<TerminalLine> deleting = new ArrayList<>();
        for (Option<TerminalLine> option : chosen) {
            String code = option.option().trim().toUpperCase(Locale.ROOT);
            if (code.equals(deleteOption())) {
                deleting.add(option.row());
            } else if (!option(code, option.row())) {
                pending.clear();
                return invalid(option);
            }
        }
        confirmAll(deleting);
        next();
        return true;
    }
}
