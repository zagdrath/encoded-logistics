/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import static net.zagdrath.encodedlogistics.client.crt.LayoutTest.answer;
import static net.zagdrath.encodedlogistics.client.crt.LayoutTest.cells;
import static net.zagdrath.encodedlogistics.client.crt.LayoutTest.hostOf;
import static net.zagdrath.encodedlogistics.client.crt.LayoutTest.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// Work with Machines: its rows under the headings, 5=Display (DSPMCH asking for the machine), 7=Enable/Disable (the
// other way to how it is), and 2=Change's command, with only the fields that changed.
class WrkMchTest {
    private static final TerminalLine CRUSHER = cells("ARCCRU01", "Arc Crusher", "*RUNNING", "45%", "12.0k/20000 FE", "2.5/min", "*IGNORE", "*NO", "*NO",
            "*NONE", "*IGNORE *HIGH *LOW", "*YES", "top=input bottom=output left=none right=none back=energy front=none", "input output energy none");
    private static final TerminalLine FURNACE = cells("INDFUR01", "Induction Furnace", "*NOPOWER", "", "0/40000 FE", "0.0/min", "*NONE", "*NONE", "*NO",
            "*NONE", "", "*NO", "", "");

    @BeforeAll
    static void language() throws java.io.IOException {
        LayoutTest.language();
    }

    private static CrtTerminal opened() {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.runCommand("WRKMCH");
        answer(terminal, "machines", CRUSHER, FURNACE);
        return terminal;
    }

    private static List<String> sent(CrtTerminal terminal) {
        return ((LayoutTest.Host) hostOf(terminal)).sent;
    }

    @Test
    void rows() {
        CrtGrid grid = opened().compose();
        assertEquals("Opt  Machine    Type             Status     Prog  Energy            Rate", row(grid, 6).stripTrailing());
        assertEquals("     ARCCRU01   Arc Crusher      *RUNNING   45%   12.0k/20000 FE    2.5/min", row(grid, 7).stripTrailing());
        assertEquals("     INDFUR01   Induction Furnac *NOPOWER         0/40000 FE        0.0/min", row(grid, 8).stripTrailing());
        assertEquals(CrtGrid.BRIGHT, grid.attrs[8][33], "No power isn't bright");
        assertEquals(CrtGrid.NORMAL, grid.attrs[7][33], "Running is bright");
    }

    @Test
    void displayAndEnable() {
        CrtTerminal terminal = opened();
        LayoutTest.option(terminal, 7, "5");
        terminal.submit();
        assertInstanceOf(TextPanel.class, terminal.current());
        assertTrue(sent(terminal).getLast().endsWith("machine ARCCRU01"), "Asked " + sent(terminal).getLast());
        terminal.back();
        LayoutTest.option(terminal, 7, "7");
        terminal.submit();
        assertTrue(sent(terminal).contains(TerminalService.COMMAND + " CHGMCHSTS MCH(ARCCRU01) STATUS(*DISABLE)"), "Sent " + sent(terminal));
        // A switched-off one is switched on.
        CrtTerminal other = opened();
        LayoutTest.option(other, 8, "7");
        other.submit();
        assertTrue(sent(other).contains(TerminalService.COMMAND + " CHGMCHSTS MCH(INDFUR01) STATUS(*ENABLE)"), "Sent " + sent(other));
    }

    // 8=Sides: a window with each side's mode (F4 steps through the modes), a CHGMCHCFG SIDE() SIDEMODE() for each one
    // changed; a machine without sides says so. 2=Change's F4 steps the redstone mode.
    @Test
    void sidesAndChoices() {
        CrtTerminal terminal = opened();
        LayoutTest.option(terminal, 7, "8");
        terminal.submit();
        CrtWindow window = terminal.window();
        assertTrue(window != null && window.title.contains("ARCCRU01"), "No sides window");
        assertEquals(6, window.fields.size());
        terminal.focus(window.fields.get(0));
        terminal.functionKey(4);
        assertEquals("output", window.fields.get(0).trimmed(), "F4 didn't step the mode");
        window.fields.get(4).set("none");
        terminal.submit();
        assertTrue(sent(terminal).contains(TerminalService.COMMAND + " CHGMCHCFG MCH(ARCCRU01) SIDE(*TOP) SIDEMODE(output)"), "Sent " + sent(terminal));
        assertTrue(sent(terminal).contains(TerminalService.COMMAND + " CHGMCHCFG MCH(ARCCRU01) SIDE(*BACK) SIDEMODE(none)"), "Sent " + sent(terminal));
        assertEquals(2, sent(terminal).stream().filter(line -> line.contains("SIDE(")).count(), "Unchanged sides sent");
        // No sides.
        CrtTerminal other = opened();
        LayoutTest.option(other, 8, "8");
        other.submit();
        assertEquals(null, other.window());
        assertTrue(row(other.compose(), 22).contains("INDFUR01"), "No message: " + row(other.compose(), 22));
        // 2=Change: F4 on the redstone mode.
        CrtTerminal change = opened();
        LayoutTest.option(change, 7, "2");
        change.submit();
        change.focus(change.window().fields.get(0));
        change.functionKey(4);
        assertEquals("*HIGH", change.window().fields.get(0).trimmed());
    }

    @Test
    void changeSendsWhatChanged() {
        assertEquals("CHGMCHCFG MCH(ARCCRU01) RSMODE(*HIGH) GATEWAY(GATEWAY01)",
                WrkMchPanel.change("ARCCRU01", CRUSHER, List.of("*high", "*NO", "*NO", "gateway01")));
        assertEquals("CHGMCHCFG MCH(INDFUR01) PWRNET(*YES)", WrkMchPanel.change("INDFUR01", FURNACE, List.of("*NONE", "*NONE", "*YES", "*NONE")));
    }
}
