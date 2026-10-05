/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The Main Menu and its help panel come from one definition (MainMenu): every option is drawn, opens the screen its
// command names, and is in the help (all of them on its first page); an option that isn't available says so.
class MainMenuTest {
    @BeforeAll
    static void language() throws IOException {
        LayoutTest.language();
    }

    private static CrtTerminal choose(String number) {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.command.set(number);
        terminal.submit();
        return terminal;
    }

    private static String messageLine(CrtTerminal terminal) {
        return LayoutTest.row(terminal.compose(), 22).strip();
    }

    @Test
    void everyOptionOpensItsScreen() {
        for (MainMenu.Option option : MainMenu.OPTIONS) {
            if (option.number() == 90) {
                continue;
            }
            CrtTerminal terminal = choose(Integer.toString(option.number()));
            assertEquals(option.command(), terminal.current().id(), "Option " + option.number());
        }
    }

    @Test
    void everyOptionIsDrawnAtItsRow() {
        CrtTerminal terminal = LayoutTest.terminal();
        for (int page = 0; page < MainMenu.PAGES; page++) {
            CrtGrid grid = terminal.compose();
            assertTrue(LayoutTest.row(grid, 18).strip().equals(page + 1 < MainMenu.PAGES ? "More..." : "Bottom"), "Page " + page + " marker");
            for (MainMenu.Option option : MainMenu.OPTIONS) {
                String drawn = option.number() + ". " + option.label();
                assertEquals(option.page() == page, LayoutTest.row(grid, option.row()).contains(drawn), "Option " + option.number() + " on page " + page);
            }
            terminal.page(1);
        }
    }

    @Test
    void helpListsEveryOptionOnItsFirstPage() {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.functionKey(1);
        CrtGrid grid = terminal.compose();
        List<String> rows = new ArrayList<>();
        for (int r = 0; r < 24; r++) {
            rows.add(LayoutTest.row(grid, r));
        }
        String shown = String.join("\n", rows);
        String help = MainMenu.help();
        for (MainMenu.Option option : MainMenu.OPTIONS) {
            assertTrue(shown.contains(String.format("%2d %s", option.number(), option.label())), "Option " + option.number() + " not on the help's first page");
            assertTrue(help.contains(option.label() + ": " + option.description()), "Option " + option.number() + " not described");
        }
    }

    @Test
    void anOptionNotAvailableSaysSo() {
        CrtTerminal terminal = LayoutTest.terminal();
        MainMenuPanel menu = (MainMenuPanel) terminal.current();
        menu.choose(new MainMenu.Option(6, "WRKACTJOB", MainMenu.Availability.NOT_AVAILABLE, 0, 11));
        assertEquals("MAIN", terminal.current().id());
        assertEquals("Option 6 not available.", messageLine(terminal));
    }

    @Test
    void aNumberNotOnTheMenuSaysSo() {
        CrtTerminal terminal = choose("14");
        assertEquals("MAIN", terminal.current().id());
        assertEquals("Option 14 is not on this menu.", messageLine(terminal));
    }
}
