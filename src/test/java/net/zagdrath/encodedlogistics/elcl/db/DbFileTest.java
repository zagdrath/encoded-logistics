/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.zagdrath.encodedlogistics.elcl.ElclException;

// A file's records (DbFile): written, changed and deleted by relative record number; read in key order (arrival order
// without a key), from a position, back and forth, by a whole or partial key; a UNIQUE key refusing a second record;
// values converted and refused by type (FieldDef); and saved and loaded with their numbers.
class DbFileTest {
    static RecordFormat format(String... lines) {
        Dds.Result result = Dds.compile(List.of(lines));
        assertTrue(result.ok(), result.diagnostics().toString());
        return result.format();
    }

    static final RecordFormat ITEMS = format("A UNIQUE", "A R ITEMREC", "A ITEM 20A", "A QTY 11S 0", "A PRICE 9P 2", "A ACTIVE 1L", "A UPDATED T",
            "A K ITEM");

    static Object[] row(RecordFormat format, Object... values) throws ElclException {
        Object[] row = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            row[i] = format.fields().get(i).convert(values[i]);
        }
        return row;
    }

    static DbFile file(RecordFormat format) {
        return new DbFile("ITEMS", "Items", format, "ZAGLIB", "ITEMS", 1, "Day 1  06:00", false);
    }

    private static List<String> items(List<DbRecord> records) {
        List<String> items = new ArrayList<>();
        records.forEach(record -> items.add((String) record.value(0)));
        return items;
    }

    @Test
    void keyOrderUniqueAndChanges() throws ElclException {
        DbFile file = file(ITEMS);
        DbRecord iron = file.add(row(ITEMS, "IRON_INGOT", 40L, "1.5", "1", "00002 07:00:00"));
        file.add(row(ITEMS, "COAL", 900L, "0.25", "0", "00002 07:00:00"));
        file.add(row(ITEMS, "GOLD_INGOT", 3L, "9.99", "1", "00002 07:00:00"));
        assertEquals(List.of("COAL", "GOLD_INGOT", "IRON_INGOT"), items(file.records()));
        assertEquals(1, iron.rrn());
        ElclException duplicate = assertThrows(ElclException.class, () -> file.add(row(ITEMS, "COAL", 1L, "1", "0", "00002 07:00:00")));
        assertEquals("ELC2203", duplicate.elclMessage().id());
        assertEquals("Duplicate key COAL in file ITEMS.", duplicate.elclMessage().text());
        // Changing a record's key moves it; onto another's is refused.
        file.update(iron.rrn(), row(ITEMS, "ANDESITE", 40L, "1.5", "1", "00002 07:00:00"));
        assertEquals(List.of("ANDESITE", "COAL", "GOLD_INGOT"), items(file.records()));
        assertEquals("ELC2203", assertThrows(ElclException.class, () -> file.update(iron.rrn(), row(ITEMS, "COAL", 1L, "1", "0", "00002 07:00:00")))
                .elclMessage().id());
        // The same key on itself is fine.
        file.update(iron.rrn(), row(ITEMS, "ANDESITE", 41L, "1.5", "1", "00002 07:00:00"));
        assertTrue(file.delete(iron.rrn()));
        assertFalse(file.delete(iron.rrn()));
        assertEquals("ELC2204", assertThrows(ElclException.class, () -> file.update(iron.rrn(), row(ITEMS, "X", 1L, "1", "0", ""))).elclMessage().id());
        assertEquals(2, file.size());
        // Numbers aren't reused.
        assertEquals(4, file.add(row(ITEMS, "STONE", 1L, "0", "0", "00002 07:00:00")).rrn());
    }

    @Test
    void readingOnFromAPosition() throws ElclException {
        DbFile file = file(ITEMS);
        for (String item : List.of("D", "B", "A", "C")) {
            file.add(row(ITEMS, item, 1L, "0", "0", "00001 06:00:00"));
        }
        List<String> read = new ArrayList<>();
        DbRecord.Position at = null;
        for (DbRecord record = file.next(null); record != null; record = file.next(at)) {
            read.add((String) record.value(0));
            at = record.position(ITEMS);
        }
        assertEquals(List.of("A", "B", "C", "D"), read);
        // A record deleted under the position: reads carry on after where it was.
        DbRecord b = file.chain(new Object[] { "B" });
        assertNotNull(b);
        DbRecord.Position afterB = b.position(ITEMS);
        file.delete(b.rrn());
        assertEquals("C", file.next(afterB).value(0));
        assertEquals("A", file.previous(afterB).value(0));
        assertEquals("D", file.previous(null).value(0));
        assertNull(file.chain(new Object[] { "Z" }));
        assertEquals(1, file.position(new Object[] { "C" }));
        assertEquals(1, file.position(new Object[] { "BB" }));
    }

    @Test
    void arrivalOrderAndPartialKeys() throws ElclException {
        RecordFormat log = format("A R LOGREC", "A DAY 5S 0", "A ITEM 10A", "A N 5S 0", "A K DAY", "A K ITEM");
        DbFile keyed = file(log);
        keyed.add(row(log, 2L, "B", 1L));
        keyed.add(row(log, 1L, "Z", 2L));
        keyed.add(row(log, 2L, "A", 3L));
        // Not unique: the same key twice is fine.
        keyed.add(row(log, 2L, "A", 4L));
        assertEquals(List.of(2L, 3L, 4L, 1L), keyed.records().stream().map(record -> record.value(2)).toList());
        // The first record of day 2 by the partial key.
        assertEquals(3L, keyed.chain(new Object[] { 2L }).value(2));
        RecordFormat plain = format("A R PLAIN", "A NAME 10A");
        DbFile arrival = file(plain);
        for (String name : List.of("C", "A", "B")) {
            arrival.add(row(plain, name));
        }
        assertEquals(List.of("C", "A", "B"), items(arrival.records()));
        assertEquals(-1, arrival.position(new Object[] { "A" }));
        assertNull(arrival.chain(new Object[] { "A" }));
        assertEquals(2, arrival.indexOf(3));
    }

    @Test
    void valuesByType() throws ElclException {
        FieldDef qty = ITEMS.field("QTY"), price = ITEMS.field("PRICE"), active = ITEMS.field("ACTIVE"), item = ITEMS.field("ITEM"),
                updated = ITEMS.field("UPDATED");
        assertEquals(42L, qty.convert(" 42 "));
        assertEquals(0L, qty.convert(""));
        assertEquals("ELC2209", assertThrows(ElclException.class, () -> qty.convert("4.5")).elclMessage().id());
        assertEquals("ELC2209", assertThrows(ElclException.class, () -> qty.convert("123456789012")).elclMessage().id());
        assertEquals("ELC2209", assertThrows(ElclException.class, () -> qty.convert("lots")).elclMessage().id());
        assertEquals(new BigDecimal("1.26"), price.convert("1.255"));
        assertEquals("ELC2209", assertThrows(ElclException.class, () -> price.convert("12345678")).elclMessage().id());
        assertEquals(Boolean.TRUE, active.convert("1"));
        assertEquals(Boolean.FALSE, active.convert(""));
        assertEquals("ELC2209", assertThrows(ElclException.class, () -> active.convert("Y")).elclMessage().id());
        assertEquals("x".repeat(20), item.convert("x".repeat(25)));
        assertEquals("ABC", item.convert("ABC   "));
        assertEquals("00012 06:30:00", updated.convert("Day 12 06:30"));
        assertEquals("00012 06:30:15", updated.convert("12 6:30:15"));
        assertEquals("", updated.convert("*NOW"));
        assertEquals("ELC2209", assertThrows(ElclException.class, () -> updated.convert("noon")).elclMessage().id());
        assertEquals("00001 06:00:00", Timestamps.of(0));
        assertEquals("00002 12:00:00", Timestamps.of(24_000 + 6_000));
        assertEquals("    9.99", price.column(new BigDecimal("9.99")).substring(3));
    }

    @Test
    void savedAndLoaded() throws ElclException {
        DbFile file = file(ITEMS);
        file.add(row(ITEMS, "IRON_INGOT", 40L, "1.5", "1", "00002 07:00:00"));
        DbRecord coal = file.add(row(ITEMS, "COAL", 900L, "0.25", "0", "00003 08:00:00"));
        file.add(row(ITEMS, "A|B, \"C\"", 1L, "0", "0", "00003 08:00:00"));
        file.delete(1);
        DbFile loaded = DbFile.load(file.save());
        assertNotNull(loaded);
        assertEquals(ITEMS, loaded.format);
        assertEquals("ZAGLIB", loaded.sourceLibrary);
        assertEquals(1, loaded.sourceVersion);
        assertEquals(2, loaded.size());
        assertEquals(new BigDecimal("0.25"), loaded.get(coal.rrn()).value(2));
        assertEquals("A|B, \"C\"", loaded.records().getFirst().value(0));
        assertEquals(4, loaded.add(row(ITEMS, "STONE", 1L, "0", "0", "00003 08:00:00")).rrn());
        assertEquals(2L * ITEMS.recordLength(), file.characters());
    }

    @Test
    void aChangeOfFormatThatBreaksTheKeyChangesNothing() throws ElclException {
        RecordFormat loose = format("A R REC", "A ITEM 10A", "A N 5S 0", "A K ITEM");
        DbFile file = file(loose);
        file.add(row(loose, "A", 1L));
        file.add(row(loose, "A", 2L));
        RecordFormat strict = format("A UNIQUE", "A R REC", "A ITEM 10A", "A N 5S 0", "A K ITEM");
        Map<Long, Object[]> rows = file.rows();
        assertEquals("ELC2203", assertThrows(ElclException.class, () -> file.reformat(strict, rows)).elclMessage().id());
        assertEquals(loose, file.format);
        assertEquals(2, file.size());
    }
}
