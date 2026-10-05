/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

// The Main Menu's options (OS.md 8), the one definition both the menu (MainMenuPanel) and its help panel (F1) are
// drawn from: each option's number, the screen command it runs (the same one typed on a command line), whether that
// works yet, and its label and description (lang keys crt.encodedlogistics.menu.<n> and .<n>.help). An option whose
// screen is waiting on logic that doesn't exist yet is marked NOT_AVAILABLE: choosing it says so on the message line.
final class MainMenu {
    enum Availability {
        AVAILABLE, NOT_AVAILABLE
    }

    // row and col: where it's drawn, its number ending at col (the left column's at 8, the right's at RIGHT).
    record Option(int number, String command, Availability availability, int row, int col) {
        Option(int number, String command, Availability availability, int row) {
            this(number, command, availability, row, LEFT);
        }

        String label() {
            return CrtPanel.tr("crt.encodedlogistics.menu." + number);
        }

        String description() {
            return CrtPanel.tr("crt.encodedlogistics.menu." + number + ".help");
        }

        boolean available() {
            return availability == Availability.AVAILABLE;
        }
    }

    static final int LEFT = 8, RIGHT = 47;

    // Rows on the screen: 1-8 from row 6 on the left, 9-13 from row 6 on the right, Sign Off apart at row 15.
    static final List<Option> OPTIONS = List.of(
            new Option(1, "WRKINV", Availability.AVAILABLE, 6),
            new Option(2, "WRKCRFJOB", Availability.AVAILABLE, 7),
            new Option(3, "WRKDEV", Availability.AVAILABLE, 8),
            new Option(4, "DSPNETSTS", Availability.AVAILABLE, 9),
            new Option(5, "WRKLIB", Availability.AVAILABLE, 10),
            new Option(6, "WRKACTJOB", Availability.AVAILABLE, 11),
            new Option(7, "DSPMSG", Availability.AVAILABLE, 12),
            new Option(8, "WRKSPLF", Availability.AVAILABLE, 13),
            new Option(9, "WRKMCH", Availability.AVAILABLE, 6, RIGHT),
            new Option(10, "WRKF", Availability.AVAILABLE, 7, RIGHT),
            new Option(11, "WRKJOBSCDE", Availability.AVAILABLE, 8, RIGHT),
            new Option(12, "WRKTRGEVT", Availability.AVAILABLE, 9, RIGHT),
            new Option(13, "WRKSYSVAL", Availability.AVAILABLE, 10, RIGHT),
            new Option(90, "SIGNOFF", Availability.AVAILABLE, 15));

    private MainMenu() {}

    static @Nullable Option option(String number) {
        for (Option option : OPTIONS) {
            if (Integer.toString(option.number()).equals(number)) {
                return option;
            }
        }
        return null;
    }

    // The option drawn at a cell: its row, and the column's half of the screen.
    static @Nullable Option at(int row, int col) {
        boolean right = col >= RIGHT - 4;
        for (Option option : OPTIONS) {
            if (option.row() == row && (option.col() == RIGHT) == right) {
                return option;
            }
        }
        return null;
    }

    // The help panel: every option (Sign Off last) in two columns first (so they all show on its first page), how to use
    // the menu, then a line on each option.
    static String help() {
        StringBuilder text = new StringBuilder();
        List<Option> numbered = OPTIONS;
        int half = (numbered.size() + 1) / 2;
        for (int i = 0; i < half; i++) {
            text.append(cell(numbered.get(i)));
            if (i + half < numbered.size()) {
                text.append(' ').append(cell(numbered.get(i + half)).stripTrailing());
            }
            text.append('\n');
        }
        text.append('\n').append(CrtPanel.tr("crt.encodedlogistics.help.main")).append('\n');
        for (Option option : OPTIONS) {
            text.append('\n').append(String.format(Locale.ROOT, "%2d  ", option.number())).append(option.label()).append(": ")
                    .append(option.description()).append(" (").append(option.command()).append(')');
            if (!option.available()) {
                text.append(' ').append(CrtPanel.tr("crt.encodedlogistics.menu.not_available_yet"));
            }
        }
        return text.toString();
    }

    // "5 Work with Libraries", padded to half the help window.
    private static String cell(Option option) {
        return String.format(Locale.ROOT, "%2d %-22s", option.number(), option.label());
    }
}
