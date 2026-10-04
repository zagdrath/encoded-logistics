/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// WRKTRGEVT (screen 11): the system's trigger events - Trigger, Event, Item/Device, Value, Program, Status (*ACTIVE,
// *HELD). Options: 2=Change (ADDTRGEVT's prompter with its values; the trigger is replaced), 3=Hold (and again to
// release), 4=Remove (confirmed, RMVTRGEVT). F6 prompts ADDTRGEVT.
final class WrkTrgEvtPanel extends OsListPanel {
    WrkTrgEvtPanel(CrtTerminal screen) {
        super(screen);
    }

    @Override
    String id() {
        return "WRKTRGEVT";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.wrktrgevt.title");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.add");
    }

    @Override
    String query() {
        return "triggers";
    }

    @Override
    String legend() {
        return tr("crt.encodedlogistics.wrktrgevt.opts");
    }

    @Override
    String heading() {
        return tr("crt.encodedlogistics.wrktrgevt.cols");
    }

    @Override
    String defaultOption() {
        return "2";
    }

    // The item, else the device ("*ANY" for neither).
    private static String target(TerminalLine row) {
        String item = cell(row, 2), device = cell(row, 3);
        return !item.isEmpty() && !item.equals("*ANY") ? item : device.isEmpty() ? "*ANY" : device;
    }

    // name, event, item, device, value, program, status, user
    @Override
    void drawRow(CrtGrid grid, int screenRow, TerminalLine row) {
        grid.put(screenRow, 5, CrtGrid.pad(cell(row, 0), 10));
        grid.put(screenRow, 17, CrtGrid.pad(cell(row, 1), 11));
        grid.put(screenRow, 29, CrtGrid.pad(target(row), 16));
        grid.put(screenRow, 46, CrtGrid.pad(cell(row, 4), 6));
        grid.put(screenRow, 53, CrtGrid.pad(cell(row, 5), 17));
        grid.put(screenRow, 72, CrtGrid.pad(cell(row, 6), 7));
    }

    @Override
    boolean functionKey(int f) {
        if (f == 6) {
            screen.prompter("ADDTRGEVT", false, screen::runCommand);
            return true;
        }
        return false;
    }

    static String addCommand(TerminalLine row) {
        String command = "ADDTRGEVT TRG(" + cell(row, 0) + ") EVENT(" + cell(row, 1) + ") PGM(" + cell(row, 5) + ")";
        if (!cell(row, 2).isEmpty() && !cell(row, 2).equals("*ANY")) {
            command += " ITEM(" + cell(row, 2) + ")";
        }
        if (!cell(row, 3).isEmpty() && !cell(row, 3).equals("*ANY")) {
            command += " DEV(" + cell(row, 3) + ")";
        }
        if (!cell(row, 4).isEmpty()) {
            command += " VALUE(" + cell(row, 4) + ")";
        }
        return command;
    }

    @Override
    boolean option(String code, TerminalLine row) {
        String name = cell(row, 0);
        switch (code) {
            case "2" -> then(() -> screen.prompter(addCommand(row), false, command -> {
                screen.runCommand("RMVTRGEVT TRG(" + name + ")");
                screen.runCommand(command);
            }));
            case "3" -> then(() -> screen.runCommand((cell(row, 6).equals("*HELD") ? "RLSTRGEVT" : "HLDTRGEVT") + " TRG(" + name + ")"));
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    String deleteLabel() {
        return tr("crt.encodedlogistics.confirm.remove");
    }

    @Override
    String deleteCommand(TerminalLine row) {
        return "RMVTRGEVT TRG(" + cell(row, 0) + ")";
    }
}
