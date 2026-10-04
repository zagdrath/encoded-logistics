/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// WRKJOBSCDE (screen 10): the system's job schedule entries - Job, Status (*SCD, *HLD), Frequency (*ONCE, *DAILY,
// *INTERVAL), Next run (game day and time), Command (cut). Options: 2=Change (ADDJOBSCDE's prompter with its values; the
// entry is replaced), 3=Hold (and again to release), 4=Remove (confirmed, RMVJOBSCDE), 10=Submit now (SBMJOB with its
// command; the entry stays). F6 prompts ADDJOBSCDE.
final class WrkJobScdePanel extends OsListPanel {
    WrkJobScdePanel(CrtTerminal screen) {
        super(screen);
    }

    @Override
    String id() {
        return "WRKJOBSCDE";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.wrkjobscde.title");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.add");
    }

    @Override
    String query() {
        return "schedules";
    }

    @Override
    String legend() {
        return tr("crt.encodedlogistics.wrkjobscde.opts");
    }

    @Override
    String heading() {
        return tr("crt.encodedlogistics.wrkjobscde.cols");
    }

    @Override
    String defaultOption() {
        return "2";
    }

    // job, status, frequency, next, command, user, time, interval
    @Override
    void drawRow(CrtGrid grid, int screenRow, TerminalLine row) {
        grid.put(screenRow, 5, CrtGrid.pad(cell(row, 0), 10));
        grid.put(screenRow, 17, CrtGrid.pad(cell(row, 1), 8));
        grid.put(screenRow, 27, CrtGrid.pad(cell(row, 2), 10));
        grid.put(screenRow, 39, CrtGrid.pad(cell(row, 3), 15));
        grid.put(screenRow, 56, CrtGrid.pad(cell(row, 4), 23));
    }

    @Override
    boolean functionKey(int f) {
        if (f == 6) {
            screen.prompter("ADDJOBSCDE", false, screen::runCommand);
            return true;
        }
        return false;
    }

    // The entry as ADDJOBSCDE makes it.
    static String addCommand(TerminalLine row) {
        String command = "ADDJOBSCDE JOB(" + cell(row, 0) + ") CMD(" + cell(row, 4) + ") FRQ(" + cell(row, 2) + ")";
        if (!cell(row, 6).isEmpty() && !cell(row, 6).equals("*CURRENT")) {
            command += " TIME(" + cell(row, 6) + ")";
        }
        if (!cell(row, 7).isEmpty() && !cell(row, 7).equals("0")) {
            command += " INTERVAL(" + cell(row, 7) + ")";
        }
        return command;
    }

    @Override
    boolean option(String code, TerminalLine row) {
        String job = cell(row, 0);
        switch (code) {
            case "2" -> then(() -> screen.prompter(addCommand(row), false, command -> {
                screen.runCommand("RMVJOBSCDE JOB(" + job + ")");
                screen.runCommand(command);
            }));
            case "3" -> then(() -> screen.runCommand((cell(row, 1).equals("*HLD") ? "RLSJOBSCDE" : "HLDJOBSCDE") + " JOB(" + job + ")"));
            case "10" -> then(() -> screen.runCommand("SBMJOB CMD(" + cell(row, 4) + ") JOB(" + job + ")"));
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
        return "RMVJOBSCDE JOB(" + cell(row, 0) + ")";
    }
}
