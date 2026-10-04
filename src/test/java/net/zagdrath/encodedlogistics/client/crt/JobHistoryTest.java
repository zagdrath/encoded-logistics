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

import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Work with Jobs' history view: F10 there and back, the columns, F11's sorts, Position to, and options 4, 5 and 7.
class JobHistoryTest {
    @BeforeAll
    static void language() throws IOException {
        LayoutTest.language();
    }

    // A history row as TerminalService.jobHistory sends it.
    private static TerminalLine row(String number, String id, String name, String qty, String status, String ended, long at, String by) {
        return LayoutTest.cells(number, id, name, qty, status.equals("Done") ? qty : "0", status, ended, Long.toString(at), "1m 05s", by);
    }

    private static void history(CrtTerminal terminal) {
        terminal.receive(new CrtResponsePayload(0, TerminalService.QUERY, "jobhistory", List.of(
                row("0003", "minecraft:gold_ingot", "Gold Ingot", "4", "Failed", "Day 2  07:10", 300, "RESTOCK"),
                row("0002", "minecraft:iron_ingot", "Iron Ingot", "8", "Cancelled", "Day 2  07:05", 200, "ZAGDRATH"),
                row("0001", "minecraft:iron_ingot", "Iron Ingot", "16", "Done", "Day 2  07:00", 100, "ZAGDRATH")), Optional.empty(), -1));
    }

    private static List<String> numbers(JobsPanel panel) {
        return panel.rows.stream().map(line -> CrtPanel.cell(line, 0)).toList();
    }

    private static List<String> sent(CrtTerminal terminal) {
        return ((LayoutTest.Host) hostOf(terminal)).sent;
    }

    private static CrtTerminal.Host hostOf(CrtTerminal terminal) {
        try {
            var field = CrtTerminal.class.getDeclaredField("host");
            field.setAccessible(true);
            return (CrtTerminal.Host) field.get(terminal);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static JobsPanel opened() {
        CrtTerminal terminal = LayoutTest.terminal();
        JobsPanel panel = new JobsPanel(terminal);
        terminal.push(panel);
        terminal.functionKey(10);
        assertTrue(panel.history(), "F10 didn't switch to the history");
        assertTrue(sent(terminal).getLast().endsWith("jobhistory"), "Asked " + sent(terminal).getLast());
        history(terminal);
        return panel;
    }

    @Test
    void toggleAndColumns() {
        JobsPanel panel = opened();
        CrtTerminal terminal = panel.screen;
        CrtGrid grid = terminal.compose();
        assertEquals("Crafting Job History", panel.title());
        assertTrue(new String(grid.chars[0]).contains("Crafting Job History"), new String(grid.chars[0]));
        String heading = new String(grid.chars[8]);
        assertTrue(heading.startsWith("Opt  Job   Item") && heading.contains("Status") && heading.contains("Ended") && heading.contains("Duration")
                && heading.trim().endsWith("Requested by"), heading);
        String first = new String(grid.chars[9]);
        assertTrue(first.contains("0003") && first.contains("Gold Ingot") && first.contains("Failed") && first.contains("Day 2  07:10")
                && first.contains("RESTOCK"), first);
        assertTrue(terminal.current().keys().contains("F10=Active jobs") && terminal.current().keys().contains("F11=Sort"));
        // F10 again: the active jobs.
        terminal.functionKey(10);
        assertFalse(panel.history());
        assertTrue(sent(terminal).getLast().endsWith("jobs"), "Asked " + sent(terminal).getLast());
        assertTrue(panel.keys().contains("F10=History"));
    }

    @Test
    void sortsAndFilters() {
        JobsPanel panel = opened();
        CrtTerminal terminal = panel.screen;
        assertEquals(List.of("0003", "0002", "0001"), numbers(panel), "Newest first");
        terminal.functionKey(11);
        assertEquals(List.of("0003", "0002", "0001"), numbers(panel), "By item (Gold, then Iron newest first)");
        terminal.functionKey(11);
        assertEquals(List.of("0002", "0001", "0003"), numbers(panel), "By status: Cancelled, Done, Failed");
        terminal.functionKey(11);
        assertEquals(List.of("0003", "0002", "0001"), numbers(panel), "Back to ended time");
        CrtField position = panel.fields.stream().filter(field -> field.row == 3 && field.col == 27).findFirst().orElse(null);
        assertNotNull(position, "No Position to field");
        position.set("iron");
        panel.tick();
        assertEquals(List.of("0002", "0001"), numbers(panel));
        position.set("*ALL");
        panel.tick();
        assertEquals(3, panel.rows.size());
    }

    @Test
    void options() {
        JobsPanel panel = opened();
        CrtTerminal terminal = panel.screen;
        // 5=Display: the record.
        LayoutTest.option(terminal, 9, "5");
        terminal.submit();
        assertInstanceOf(TextPanel.class, terminal.current());
        assertTrue(sent(terminal).getLast().endsWith("jobrecord 0003"), "Asked " + sent(terminal).getLast());
        terminal.back();
        // 7=Craft again: the Craft Item screen with the same item and quantity.
        LayoutTest.option(terminal, 10, "7");
        terminal.submit();
        CrtPanel craft = terminal.current();
        assertInstanceOf(CraftPanel.class, craft);
        assertEquals("minecraft:iron_ingot", craft.fields.get(0).value);
        assertEquals("8", craft.fields.get(1).value);
        terminal.back();
        // 4=Remove: confirmed, then asked for.
        LayoutTest.option(terminal, 11, "4");
        terminal.submit();
        assertNotNull(terminal.window(), "No confirmation");
        assertFalse(sent(terminal).stream().anyMatch(text -> text.contains("removejobrecord")), "Removed before it was confirmed");
        terminal.submit();
        assertTrue(sent(terminal).stream().anyMatch(text -> text.endsWith("removejobrecord 0001")), "Not removed: " + sent(terminal));
        // An option the history hasn't got goes back on its row.
        LayoutTest.option(terminal, 9, "2");
        terminal.submit();
        assertTrue(terminal.current() == panel && panel.anyOptions(), "Option 2 wasn't put back");
    }
}
