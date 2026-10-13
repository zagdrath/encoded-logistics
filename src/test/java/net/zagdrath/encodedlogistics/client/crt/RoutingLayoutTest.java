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
}
