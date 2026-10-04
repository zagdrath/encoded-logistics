/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// WRKSYSVAL (screen 14): the system values (OS.md 7) - System value, Value (bright), Description. Options: 2=Change (a
// window: the value and what it takes, CHGSYSVAL; *SECOFR-class only, ELC0401 otherwise), 5=Display (a window: value,
// default, description, allowed values). A changed PHOSPHOR takes effect here at once.
final class WrkSysvalPanel extends OsListPanel {
    WrkSysvalPanel(CrtTerminal screen) {
        super(screen);
    }

    @Override
    String id() {
        return "WRKSYSVAL";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.wrksysval.title");
    }

    @Override
    String query() {
        return "sysvals";
    }

    @Override
    String legend() {
        return tr("crt.encodedlogistics.wrksysval.opts");
    }

    @Override
    String heading() {
        return tr("crt.encodedlogistics.wrksysval.cols");
    }

    @Override
    String defaultOption() {
        return "5";
    }

    @Override
    String deleteOption() {
        return "";
    }

    // A change: the list again, and the session's details (PHOSPHOR).
    @Override
    void receive(CrtResponsePayload response) {
        if (response.kind() == TerminalService.COMMAND) {
            screen.send(TerminalService.QUERY, "info");
        }
        super.receive(response);
    }

    // name, value, default, description, allowed
    @Override
    void drawRow(CrtGrid grid, int screenRow, TerminalLine row) {
        grid.put(screenRow, 5, CrtGrid.pad(cell(row, 0), 12));
        grid.put(screenRow, 19, CrtGrid.pad(cell(row, 1), 24), CrtGrid.BRIGHT);
        grid.put(screenRow, 45, CrtGrid.pad(cell(row, 3), 34));
    }

    @Override
    boolean option(String code, TerminalLine row) {
        String name = cell(row, 0);
        switch (code) {
            case "2" -> then(() -> window(new FormWindow(screen, tr("crt.encodedlogistics.wrksysval.change", name), cell(row, 3),
                    values -> screen.runCommand("CHGSYSVAL SYSVAL(" + name + ") VALUE(" + values.get(0) + ")"))
                    .field(tr("crt.encodedlogistics.wrksysval.value"), 10, cell(row, 1), cell(row, 4))));
            case "5" -> then(() -> window(new CrtWindow(screen, 6, 8, 12, 64, tr("crt.encodedlogistics.wrksysval.display", name))
                    .text(tr("crt.encodedlogistics.wrksysval.detail", cell(row, 1), cell(row, 2), cell(row, 3), cell(row, 4)))));
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
}
