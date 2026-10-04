/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.List;

import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// WRKLIB (screen 3): the system's libraries by name - Library, Type (*PROD, *TEST, *SYS), Text. Options: 2=Change (a
// window: its text and public authority, CHGLIB), 4=Delete (confirmed, DLTLIB), 5=Display (owner, members, size,
// created), 12=Work with members. F6 prompts CRTLIB.
final class WrkLibPanel extends OsListPanel {
    WrkLibPanel(CrtTerminal screen) {
        super(screen);
    }

    @Override
    String id() {
        return "WRKLIB";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.wrklib.title");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.wrklib");
    }

    @Override
    String query() {
        return "libraries";
    }

    @Override
    String legend() {
        return tr("crt.encodedlogistics.wrklib.opts");
    }

    @Override
    String heading() {
        return tr("crt.encodedlogistics.wrklib.cols");
    }

    @Override
    String defaultOption() {
        return "12";
    }

    // name, type, text, owner, authority, members, size, created
    @Override
    void drawRow(CrtGrid grid, int screenRow, TerminalLine row) {
        grid.put(screenRow, 5, CrtGrid.pad(cell(row, 0), 10));
        grid.put(screenRow, 17, CrtGrid.pad(cell(row, 1), 8));
        grid.put(screenRow, 27, CrtGrid.pad(cell(row, 2), 50));
    }

    @Override
    boolean functionKey(int f) {
        if (f == 6) {
            screen.prompter("CRTLIB", false, screen::runCommand);
            return true;
        }
        return false;
    }

    @Override
    boolean option(String code, TerminalLine row) {
        String lib = cell(row, 0);
        switch (code) {
            case "2" -> then(() -> window(new FormWindow(screen, tr("crt.encodedlogistics.wrklib.change", lib), tr("crt.encodedlogistics.wrklib.change_text"),
                    values -> screen.runCommand("CHGLIB LIB(" + lib + ") TEXT('" + values.get(0).replace("'", "''") + "') AUT(" + values.get(1) + ")"))
                    .field(tr("crt.encodedlogistics.wrklib.text"), 50 - 18, 50, cell(row, 2), "")
                    .field(tr("crt.encodedlogistics.wrklib.authority"), 8, cell(row, 4), "*USE, *CHANGE")));
            case "5" -> then(() -> screen.push(new InfoPanel(screen, "DSPLIB", tr("crt.encodedlogistics.dsplib.title"), List.of(
                    new InfoPanel.Line(tr("crt.encodedlogistics.dsplib.library"), lib),
                    new InfoPanel.Line(tr("crt.encodedlogistics.dsplib.type"), cell(row, 1)),
                    new InfoPanel.Line(tr("crt.encodedlogistics.dsplib.text"), cell(row, 2)),
                    new InfoPanel.Line(tr("crt.encodedlogistics.dsplib.owner"), cell(row, 3)),
                    new InfoPanel.Line(tr("crt.encodedlogistics.dsplib.authority"), cell(row, 4)),
                    new InfoPanel.Line(tr("crt.encodedlogistics.dsplib.members"), cell(row, 5)),
                    new InfoPanel.Line(tr("crt.encodedlogistics.dsplib.size"), cell(row, 6)),
                    new InfoPanel.Line(tr("crt.encodedlogistics.dsplib.created"), cell(row, 7))))));
            case "12" -> then(() -> screen.push(new WrkMbrPanel(screen, lib)));
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    String deleteCommand(TerminalLine row) {
        return "DLTLIB LIB(" + cell(row, 0) + ")";
    }
}
