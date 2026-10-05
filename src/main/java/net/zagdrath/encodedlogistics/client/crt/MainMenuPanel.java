/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

// MAIN: the options in MainMenu (1 Work with Inventory, 2 Work with Jobs (crafting), 3 Work with Devices, 4 Display
// Network Status, 5 Work with Libraries, 6 Work with Active Jobs, 7 Display Messages, 8 Work with Output; on its second
// page (PageDown) 9 Work with Machines, 10 Work with Files, 11 Work with Job Schedule Entries, 12 Work with Trigger Events, 13 Work
// with System Values; 90 Sign Off),
// each running its screen's command just as typing it would (ScreenCommands). Type the number on the command line (or
// click an option, double-click to go); an option not available yet says so; a number not on the menu says that;
// anything else runs as a command. Its help panel comes from the same definition.
final class MainMenuPanel extends CrtPanel {
    private int page;

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
    String help(@Nullable String field) {
        return MainMenu.help();
    }

    @Override
    void draw(CrtGrid grid) {
        grid.put(3, 0, tr("crt.encodedlogistics.menu.select"));
        for (MainMenu.Option option : MainMenu.OPTIONS) {
            if (option.page() != page) {
                continue;
            }
            String number = option.number() + ".";
            grid.put(option.row(), MainMenu.NUMBER_END - number.length(), number + " " + option.label(), option.available() ? CrtGrid.NORMAL : CrtGrid.DIM);
        }
        grid.right(18, tr(page + 1 < MainMenu.PAGES ? "crt.encodedlogistics.more" : "crt.encodedlogistics.bottom"), CrtGrid.NORMAL);
    }

    @Override
    void page(int direction) {
        page = Math.clamp(page + direction, 0, MainMenu.PAGES - 1);
    }

    @Override
    boolean option(String text) {
        MainMenu.Option option = MainMenu.option(text);
        if (option == null) {
            // A number that isn't an option; anything else is a command.
            if (!text.isEmpty() && text.chars().allMatch(Character::isDigit)) {
                screen.message(tr("crt.encodedlogistics.menu.no_option", text));
                return true;
            }
            return false;
        }
        choose(option);
        return true;
    }

    // Goes to an option's screen, or says it isn't available (marked so, or its command can't open a screen here).
    void choose(MainMenu.Option option) {
        if (!option.available() || !ScreenCommands.open(screen, option.command().toUpperCase(Locale.ROOT))) {
            screen.message(tr("crt.encodedlogistics.menu.not_available", option.number()));
        }
    }

    @Override
    void click(int row, int col, boolean doubleClick) {
        MainMenu.Option option = MainMenu.at(page, row);
        if (option == null) {
            return;
        }
        screen.command.set(Integer.toString(option.number()));
        screen.focus(screen.command);
        if (doubleClick) {
            screen.command.set("");
            option(Integer.toString(option.number()));
        }
    }
}
