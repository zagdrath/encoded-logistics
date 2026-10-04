/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

// MAIN: 1 Work with Inventory, 2 Work with Jobs (crafting), 3 Work with Devices, 4 Display Network Status, 5 Work with
// Libraries, 6 Work with Active Jobs, 7 Display Messages, 8 Work with Output, 90 Sign Off. Type the number on the
// command line (or click an option, double-click to go); anything else runs as a command.
final class MainMenuPanel extends CrtPanel {
    private static final int[] OPTIONS = { 1, 2, 3, 4, 5, 6, 7, 8, 90 };
    private static final int[] ROWS = { 6, 7, 8, 9, 10, 11, 12, 13, 15 };

    MainMenuPanel(CrtTerminal screen) {
        super(screen);
    }

    @Override
    String id() {
        return "MAIN";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.menu.title");
    }

    @Override
    String prompt() {
        return tr("crt.encodedlogistics.selection");
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.main");
    }

    @Override
    void draw(CrtGrid grid) {
        grid.put(3, 0, tr("crt.encodedlogistics.menu.select"));
        for (int i = 0; i < OPTIONS.length; i++) {
            String number = OPTIONS[i] + ".";
            grid.put(ROWS[i], 8 - number.length(), number + " " + tr("crt.encodedlogistics.menu." + OPTIONS[i]));
        }
    }

    @Override
    boolean option(String text) {
        switch (text) {
            case "1" -> screen.push(new InventoryPanel(screen));
            case "2" -> screen.push(new JobsPanel(screen));
            case "3" -> screen.push(new DevicesPanel(screen));
            case "4" -> screen.push(new StatusPanel(screen));
            case "5" -> screen.push(new WrkLibPanel(screen));
            case "6" -> ScreenCommands.open(screen, "WRKACTJOB");
            case "7" -> ScreenCommands.open(screen, "DSPMSG");
            case "8" -> ScreenCommands.open(screen, "WRKSPLF");
            case "90" -> screen.onClose();
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
