/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.HardwareScreens;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;
import static org.junit.jupiter.api.Assertions.assertEquals;

// The optical handoff's screens against its layouts (docs/optical/layouts, copied to src/test/resources/layouts), with
// the rows formatted as the server formats them (HardwareScreens).
class HardwareLayoutTest {
    @BeforeAll
    static void language() throws IOException {
        LayoutTest.language();
    }

    private static void query(CrtTerminal terminal, String topic, TerminalLine... lines) {
        terminal.receive(new CrtResponsePayload(0, TerminalService.QUERY, topic, List.of(lines), Optional.empty(), -1));
    }

    private static TerminalLine row(String device, String slot, String part, String status, String detail) {
        return HardwareScreens.hardwareRow(device, slot, Component.literal(part), status, Component.literal(detail));
    }

    @Test
    void workWithHardware() throws IOException {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.runCommand("WRKHW");
        assertEquals("WRKHW", terminal.current().id());
        query(terminal, "hardware", TerminalLine.of(HardwareScreens.hardwareHead("ELNET01", 3, 31, 2)),
                row("CSW01", "A", "Supervisor", "*FAILED", "failover to B 14:28"),
                row("CSW01", "B", "Supervisor", "*ACTIVE", "standby lost"),
                row("CSW01", "L1", "Uplink Card", "*ONLINE", "4/4 cages"),
                row("CSW01", "P4", "Power Supply", "*EMPTY", "3/4 PSUs"),
                row("BLD01", "F3", "Compute Blade", "*ONLINE", "8 threads"),
                row("BLD01", "R1", "Interconnect", "*ONLINE", "64 lanes"),
                row("OTS01", "C1", "Controller", "*ACTIVE", ""),
                row("OTS01", "L5", "Protection", "*PROTECT", "working path down"));
        CrtGrid grid = terminal.compose();
        LayoutTest.compare("28_wrkhw", grid, 0, 2, 3, 4, 6, 7, 8, 9, 10, 11, 12, 13, 14, 21, 23);
    }

    @Test
    void displayOpticalStatus() throws IOException {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.runCommand("DSPOPT");
        assertEquals("DSPOPT", terminal.current().id());
        List<TerminalLine> lines = new ArrayList<>();
        lines.add(TerminalLine.of(HardwareScreens.opticalHead("ELNET01", 1, 2, 3)));
        lines.add(TerminalLine.of(Component.literal(HardwareScreens.OPTICAL_COLUMNS), TerminalLine.BRIGHT));
        lines.add(TerminalLine.of(HardwareScreens.opticalRow("OTS01-OTS02", "working", "612", "-", "-", 0, "*FAILED")));
        lines.add(TerminalLine.of(HardwareScreens.opticalRow("OTS01-OTS02", "protect", "488", "32", "2", 92, "*ACTIVE")));
        lines.add(TerminalLine.of(HardwareScreens.opticalRow("MUX01-MUX02", "line", "301", "32", "0", 100, "-")));
        lines.add(TerminalLine.of(HardwareScreens.opticalRow("MUX02-AMP01", "line", "290", "31", "1", 96, "-")));
        lines.add(TerminalLine.blank());
        lines.add(TerminalLine.of(HardwareScreens.protectionNote("14:31:05", "working fibre cut", true)));
        query(terminal, "optical", lines.toArray(TerminalLine[]::new));
        CrtGrid grid = terminal.compose();
        LayoutTest.compare("29_dspopt", grid, 0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 23);
        // F10: the channels.
        terminal.functionKey(10);
        assertEquals("DSPOPT", terminal.current().id());
    }
}
