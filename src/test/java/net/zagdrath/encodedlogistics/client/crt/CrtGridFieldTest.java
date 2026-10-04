/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The screen infrastructure (screens handoff C.1, C.2, C.4): the extra glyphs, box and ruler; fields' cursor,
// overwrite / insert, Field Exit, Field Advance and flags; a window's text wrapping.
class CrtGridFieldTest {
    private static String row(CrtGrid grid, int row) {
        return new String(grid.chars[row]);
    }

    @Test
    void extraGlyphs() {
        assertEquals(96, CrtGrid.glyph('¬'));
        assertEquals(107, CrtGrid.glyph('┼'));
        assertEquals('A' - 32, CrtGrid.glyph('A'));
        CrtGrid grid = new CrtGrid();
        grid.clear();
        grid.put(0, 0, "┌─é");
        assertEquals("┌─?", row(grid, 0).substring(0, 3));
    }

    @Test
    void box() {
        CrtGrid grid = new CrtGrid();
        grid.clear();
        grid.put(6, 0, "x".repeat(80));
        grid.box(5, 12, 13, 56, 7);
        assertEquals("┌" + "─".repeat(54) + "┐", row(grid, 5).substring(12, 68));
        assertEquals("│" + " ".repeat(54) + "│", row(grid, 6).substring(12, 68));
        assertEquals("├" + "─".repeat(54) + "┤", row(grid, 7).substring(12, 68));
        assertEquals("└" + "─".repeat(54) + "┘", row(grid, 17).substring(12, 68));
        assertEquals("x", row(grid, 6).substring(11, 12));
    }

    @Test
    void ruler() {
        assertEquals("*...+....1....+....2", CrtGrid.rulerText(20, 1));
        assertEquals("....+....5....+....6", CrtGrid.rulerText(20, 41));
    }

    @Test
    void overwriteInsertAndAdvance() {
        CrtField field = new CrtField(0, 0, 5, "ABCD");
        field.cursor = 1;
        assertFalse(field.type('x', false));
        assertEquals("AxCD", field.value);
        field.type('y', true);
        assertEquals("AxyCD", field.value);
        CrtField full = new CrtField(0, 0, 3, "");
        assertFalse(full.type('a', false));
        assertFalse(full.type('b', false));
        assertTrue(full.type('c', false), "Field Advance at the last position");
        assertFalse(full.type('d', true), "Full field took more");
        assertEquals("abc", full.value);
    }

    @Test
    void fieldExitAndFlags() {
        CrtField field = new CrtField(0, 0, 10, "HELLO WORLD".substring(0, 10));
        field.cursor = 5;
        field.fieldExit();
        assertEquals("HELLO", field.value);
        CrtField number = new CrtField(0, 0, 5, "").numeric();
        number.type('a', true);
        number.type('7', true);
        assertEquals("7", number.value);
        CrtField name = new CrtField(0, 0, 10, "").uppercase();
        name.type('z', true);
        assertEquals("Z", name.value);
        CrtGrid grid = new CrtGrid();
        grid.clear();
        new CrtField(1, 0, 6, "label").protect().draw(grid);
        assertFalse(grid.underline[1][0], "Protected text underlined");
    }

    @Test
    void wrap() {
        assertEquals(List.of("Shows the libraries on this system. Type an option", "in the Opt column."),
                CrtWindow.wrap("Shows the libraries on this system. Type an option in the Opt column.", 52));
        assertEquals(List.of("  2=Change   Change the", "  text."), CrtWindow.wrap("  2=Change   Change the text.", 23));
    }
}
