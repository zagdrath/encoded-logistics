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

    // page and row: where it's drawn, its number ending at column NUMBER_END.
    record Option(int number, String command, Availability availability, int page, int row) {
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

    static final int NUMBER_END = 8, PAGES = 2;

    // Two pages, as a long menu pages (PageDown / PageUp, More... and Bottom): 1-8 from row 6 on the first; 9-13 from row 6
    // on the second, Sign Off apart at row 12. Any option's number works from either page.
    static final List<Option> OPTIONS = List.of(
            new Option(1, "WRKINV", Availability.AVAILABLE, 0, 6),
            new Option(2, "WRKCRFJOB", Availability.AVAILABLE, 0, 7),
            new Option(3, "WRKDEV", Availability.AVAILABLE, 0, 8),
            new Option(4, "DSPNETSTS", Availability.AVAILABLE, 0, 9),
            new Option(5, "WRKLIB", Availability.AVAILABLE, 0, 10),
            new Option(6, "WRKACTJOB", Availability.AVAILABLE, 0, 11),
            new Option(7, "DSPMSG", Availability.AVAILABLE, 0, 12),
            new Option(8, "WRKSPLF", Availability.AVAILABLE, 0, 13),
            new Option(9, "WRKMCH", Availability.AVAILABLE, 1, 6),
            new Option(10, "WRKF", Availability.AVAILABLE, 1, 7),
            new Option(11, "WRKJOBSCDE", Availability.AVAILABLE, 1, 8),
            new Option(12, "WRKTRGEVT", Availability.AVAILABLE, 1, 9),
            new Option(13, "WRKSYSVAL", Availability.AVAILABLE, 1, 10),
            new Option(90, "SIGNOFF", Availability.AVAILABLE, 1, 12));

    private MainMenu() {}

    static @Nullable Option option(String number) {
        for (Option option : OPTIONS) {
            if (Integer.toString(option.number()).equals(number)) {
                return option;
            }
        }
        return null;
    }

    // The option drawn on a page's row.
    static @Nullable Option at(int page, int row) {
        for (Option option : OPTIONS) {
            if (option.page() == page && option.row() == row) {
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
