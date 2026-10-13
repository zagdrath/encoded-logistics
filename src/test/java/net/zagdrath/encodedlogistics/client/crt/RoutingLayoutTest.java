/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.RoutingScreens;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;
import static org.junit.jupiter.api.Assertions.assertEquals;

// The routing handoff's screens against its layouts (docs/routing/layouts, copied to src/test/resources/layouts), with
// the rows formatted as the server formats them (RoutingScreens).
class RoutingLayoutTest {
    @BeforeAll
    static void language() throws IOException {
        LayoutTest.language();
    }

    private static void query(CrtTerminal terminal, String topic, String... lines) {
        List<TerminalLine> rows = new java.util.ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            rows.add(i == 1 ? TerminalLine.of(Component.literal(lines[i]), TerminalLine.BRIGHT) : TerminalLine.of(lines[i]));
        }
        terminal.receive(new CrtResponsePayload(0, TerminalService.QUERY, topic, rows, Optional.empty(), -1));
    }

    @Test
    void displayRouteTable() throws IOException {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.runCommand("DSPROUTE RTR(RTR01)");
        assertEquals("DSPROUTE", terminal.current().id());
        query(terminal, "routes", RoutingScreens.routeHead(RoutingScreens.routerName("RTR01", "ELNET01"), "*DYNAMIC", 14),
                RoutingScreens.ROUTE_COLUMNS,
                RoutingScreens.routeRow("ELNET02", "direct", 10, 1, "*CONNECTED", "2:14:06"),
                RoutingScreens.routeRow("ELNET07", "ELNET02", 35, 2, "*DYNAMIC", "0:41:10"),
                RoutingScreens.routeRow("ELNET15", "IX:MAIN", 42, 2, "*DYNAMIC", "0:12:55"),
                RoutingScreens.routeRow("ELNET19", "ELNET07", 60, 3, "*DYNAMIC", "0:09:31"),
                RoutingScreens.routeRow("ELNET22", "IX:MAIN", 71, 3, "*DYNAMIC", "0:12:55"),
                RoutingScreens.routeRow("FARMNET", "IX:MAIN", 88, 4, "*DYNAMIC", "0:00:04"),
                RoutingScreens.routeRow("BASE", "ELNET02", 5, 1, "*STATIC", "12:30:00"),
                "",
                "*STATIC routes override *DYNAMIC ones.  FARMNET reconverged 4 s ago.");
        CrtGrid grid = terminal.compose();
        LayoutTest.compare("23_dsproute", grid, 0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 23);
        // F11: the neighbours.
        terminal.functionKey(11);
        assertEquals("DSPNBR", terminal.current().id());
    }

    @Test
    void displayNeighbours() throws IOException {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.runCommand("DSPNBR RTR(RTR01)");
        assertEquals("DSPNBR", terminal.current().id());
        query(terminal, "neighbours", RoutingScreens.neighbourHead(RoutingScreens.routerName("RTR01", "ELNET01"), 3, 1),
                RoutingScreens.NEIGHBOUR_COLUMNS,
                RoutingScreens.neighbourRow("ELNET02", "Network Bridge", "*FULL", "10", "-", "2:14:06"),
                RoutingScreens.neighbourRow("ELNET15", "IX:MAIN", "*FULL", "32", "-", "0:12:55"),
                RoutingScreens.neighbourRow("ELNET22", "IX:MAIN", "*FULL", "41", "30", "0:12:55"),
                RoutingScreens.neighbourRow("ELNET09", "P2P Link", "*DOWN", "-", "-", "-"));
        CrtGrid grid = terminal.compose();
        LayoutTest.compare("24_dspnbr", grid, 0, 2, 3, 4, 5, 6, 7, 8, 23);
        terminal.functionKey(10);
        assertEquals("DSPROUTE", terminal.current().id());
    }

    // Row 12 (the default) is left out: the layout's own sample puts it off the columns above it. So is row 20: the
    // frame's "Bottom" marker, which every list shows (the other layouts have it).
    @Test
    void workWithAcls() throws IOException {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.runCommand("WRKACL RTR(RTR01) ACL(NOFUEL)");
        assertEquals("WRKACL", terminal.current().id());
        LayoutTest.answer(terminal, "acl", LayoutTest.cells("10", "COAL", "*BOTH", "*DENY", "", "1,204", "14:31:02", "COAL deny"),
                LayoutTest.cells("20", "#c:fuels", "*OUT", "*DENY", "", "88", "14:02:10", "#c:fuels deny out"),
                LayoutTest.cells("30", "*ENERGY", "*BOTH", "*PERMIT", "", "12,401", "14:32:00", "*ENERGY permit"),
                LayoutTest.cells("40", "DIAMOND", "*OUT", "*PERMIT", "64/min", "310", "14:28:44", "DIAMOND permit out 64/m"),
                LayoutTest.cells("50", "*FLUID", "*IN", "*PERMIT", "2000/day", "1,950", "13:55:00", "*FLUID permit in 2000/d"),
                LayoutTest.cells("*DFT", "(default)", "", "*PERMIT", "", "4,102", "14:32:05", ""));
        CrtGrid grid = terminal.compose();
        LayoutTest.compare("25_wrkacl", grid, 0, 2, 3, 4, 6, 7, 8, 9, 10, 11, 21, 23);
    }

    @Test
    void workWithExchange() throws IOException {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.runCommand("WRKIX IX(MAIN)");
        assertEquals("WRKIX", terminal.current().id());
        LayoutTest.answer(terminal, "ix", LayoutTest.cells("MAIN", "Cody", "5", "32", "*APPROVAL"),
                LayoutTest.cells("ELNET01", "Cody", "1", "*UP", "*RS", "12", "3,410"),
                LayoutTest.cells("ELNET15", "Alex", "2", "*UP", "*RS", "6", "1,220"),
                LayoutTest.cells("ELNET22", "Sam", "5", "*UP", "*BILAT", "4", "610"),
                LayoutTest.cells("FARMNET", "Rin", "7", "*DOWN", "*RS", "0", "0"),
                LayoutTest.cells("MINES", "Jo", "9", "*UP", "*BILAT", "3", "95"),
                LayoutTest.cells("QUARRY", "Kim", "-", "*REQUEST", "-", "-", "-"));
        CrtGrid grid = terminal.compose();
        LayoutTest.compare("26_wrkix", grid, 0, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 21, 23);
    }
}
