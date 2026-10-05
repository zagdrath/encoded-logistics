/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.zagdrath.encodedlogistics.elcl.Diagnostic;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.SourceLine;

// A PF member's definition (Dds): every field type compiles, in the editor's columns or free-form, with its keywords;
// each definition error is its message on the line it's on (0-based), and the listing shows the source with sequence
// numbers, the record format and the messages.
class DdsTest {
    static final List<String> EVERY_TYPE = List.of(
            "     A* ITEMS - every field type",
            "     A                                      UNIQUE",
            "     A          R ITEMREC                   TEXT('Item record')",
            "     A            ITEM          64A         COLHDG('Item' 'ID')",
            "     A            QTY           11S 0",
            "     A            PRICE          9P 2       TEXT('Unit price')",
            "     A            ACTIVE         1L",
            "     A            UPDATED        8T",
            "     A                                      TEXT('Last changed')",
            "     A          K ITEM");

    private static Diagnostic first(String... lines) {
        Dds.Result result = Dds.compile(List.of(lines));
        assertFalse(result.ok(), "Compiled: " + List.of(lines));
        return result.diagnostics().getFirst();
    }

    private static void expect(String id, int line, String... lines) {
        Diagnostic diagnostic = first(lines);
        assertEquals(id, diagnostic.message().id(), diagnostic.message().toString());
        assertEquals(line, diagnostic.line(), diagnostic.message().toString());
    }

    @Test
    void everyFieldType() {
        Dds.Result result = Dds.compile(EVERY_TYPE);
        assertTrue(result.ok(), result.diagnostics().toString());
        RecordFormat format = result.format();
        assertEquals("ITEMREC", format.name());
        assertEquals("Item record", format.text());
        assertEquals(List.of("ITEM"), format.key());
        assertTrue(format.unique());
        assertEquals(5, format.fields().size());
        FieldDef item = format.field("ITEM"), qty = format.field("QTY"), price = format.field("PRICE"), active = format.field("ACTIVE"),
                updated = format.field("UPDATED");
        assertEquals(FieldDef.Type.CHAR, item.type());
        assertEquals(64, item.length());
        assertEquals(List.of("Item", "ID"), item.headings());
        assertEquals(FieldDef.Type.INT, qty.type());
        assertEquals(11, qty.length());
        assertEquals(FieldDef.Type.DEC, price.type());
        assertEquals(9, price.length());
        assertEquals(2, price.decimals());
        assertEquals("Unit price", price.text());
        assertEquals(FieldDef.Type.LGL, active.type());
        assertEquals(1, active.length());
        assertEquals(FieldDef.Type.TIME, updated.type());
        assertEquals("Last changed", updated.text());
        // 64 + 11 + 9 + 1 + 14.
        assertEquals(99, format.recordLength());
    }

    @Test
    void freeFormAndCombinedDecimals() {
        Dds.Result result = Dds.compile(List.of("A R REC", "A NAME 10A", "A AMT 7P2 TEXT('Amount')", "A N 5S0", "A K NAME", "A K AMT"));
        assertTrue(result.ok(), result.diagnostics().toString());
        assertEquals(2, result.format().field("AMT").decimals());
        assertEquals(List.of("NAME", "AMT"), result.format().key());
        assertFalse(result.format().unique());
    }

    @Test
    void theFormatRoundTripsAsText() {
        RecordFormat format = Dds.compile(EVERY_TYPE).format();
        RecordFormat loaded = RecordFormat.load(format.save());
        assertEquals(format, loaded);
        assertNull(RecordFormat.load("not a format"));
    }

    @Test
    void errorsOnTheirLines() {
        expect("ELC2225", 0, "A NAME 10A");
        expect("ELC2221", 2, "A R REC", "A NAME 10A", "A NAME 5A");
        expect("ELC2222", 1, "A R REC", "A NAME 0A");
        expect("ELC2222", 1, "A R REC", "A BIG 19S 0");
        expect("ELC2222", 1, "A R REC", "A QTY 5S 2");
        expect("ELC2222", 1, "A R REC", "A AMT 5P 6");
        expect("ELC2222", 1, "A R REC", "A FLAG 2L");
        expect("ELC2222", 1, "A R REC", "A TEXT 1025A");
        expect("ELC2223", 2, "A R REC", "A NAME 10A", "A K NOPE");
        expect("ELC2226", 2, "A R REC", "A NAME 10A", "A K NAME DESCEND");
        expect("ELC2226", 1, "A R REC", "A NAME 10A VALUES('A' 'B')");
        expect("ELC2226", 1, "A R REC", "A NAME 10A UNIQUE");
        expect("ELC2227", 1, "A R REC", "A NAME 10X");
        expect("ELC2227", 1, "A R REC", "A NAME");
        expect("ELC2228", 0, "A R 1REC", "A NAME 10A");
        expect("ELC2228", 1, "A R REC", "A NAME_TOO_LONG 10A");
        expect("ELC2220", 1, "A R REC", "A NAME 10A TEXT('open");
        expect("ELC2224", 2, "A R REC", "A NAME 10A", "A R REC2");
        // UNIQUE with no key.
        expect("ELC2226", 0, "A UNIQUE", "A R REC", "A NAME 10A");
    }

    @Test
    void limitsOfFieldsAndKeys() {
        List<String> lines = new ArrayList<>(List.of("A R REC"));
        for (int i = 1; i <= 51; i++) {
            lines.add("A F" + i + " 1A");
        }
        Diagnostic tooMany = first(lines.toArray(String[]::new));
        assertEquals("ELC2224", tooMany.message().id());
        assertEquals(51, tooMany.line());
        List<String> keys = new ArrayList<>(List.of("A R REC"));
        for (int i = 1; i <= 5; i++) {
            keys.add("A F" + i + " 1A");
        }
        for (int i = 1; i <= 5; i++) {
            keys.add("A K F" + i);
        }
        Diagnostic tooManyKeys = first(keys.toArray(String[]::new));
        assertEquals("ELC2224", tooManyKeys.message().id());
        assertEquals(10, tooManyKeys.line());
        lines.removeLast();
        assertTrue(Dds.compile(lines).ok(), "50 fields");
    }

    @Test
    void theListing() {
        List<SourceLine> source = SourceLine.number(List.of("A R REC", "A NAME 10A", "A K NAME", "A QTY 5X"), 3);
        Dds.Result bad = Dds.compileLines(source);
        List<String> listing = Dds.listing("ZAGLIB", "ITEMS", "Day 3 07:00", "ELNET01", source, bad, ElclMessage.of("ELC2239", "ITEMS"));
        assertTrue(listing.getFirst().startsWith("DDS Compile Listing   ZAGLIB/ITEMS"), listing.getFirst());
        assertTrue(listing.stream().anyMatch(line -> line.startsWith(" 0004.00 A QTY 5X")), String.join("\n", listing));
        assertTrue(listing.stream().anyMatch(line -> line.contains("0004.00  ELC2227   30  Type X not valid for field QTY.")), String.join("\n", listing));
        assertTrue(listing.getLast().contains("ELC2239"), listing.getLast());
        Dds.Result good = Dds.compile(EVERY_TYPE);
        List<String> made = Dds.listing("ZAGLIB", "ITEMS", "Day 3 07:00", "ELNET01", SourceLine.number(EVERY_TYPE, 0), good,
                ElclMessage.of("ELC2230", "ITEMS", "ZAGLIB"));
        assertNotNull(made.stream().filter(line -> line.contains("Key    ITEM   (UNIQUE)")).findFirst().orElse(null), String.join("\n", made));
        assertTrue(made.stream().anyMatch(line -> line.startsWith("  PRICE       P          9    2  Unit price")), String.join("\n", made));
    }
}
