/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.db;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.zagdrath.encodedlogistics.elcl.ElclException;

// RUNQRY's selection and sort (Query) - QRYSLT over field names with ELCL's operators and built-ins, "..." literals,
// SORT with *DESCEND - and the report lines; CSV out and back in (Csv): quoting, a heading row by name or none, and a
// value that doesn't fit its field refused with its row.
class QueryCsvTest {
    private static final RecordFormat ITEMS = DbFileTest.ITEMS;

    private static List<DbRecord> sample() throws ElclException {
        DbFile file = DbFileTest.file(ITEMS);
        file.add(DbFileTest.row(ITEMS, "IRON_INGOT", 40L, "1.5", "1", "00002 07:00:00"));
        file.add(DbFileTest.row(ITEMS, "COAL", 900L, "0.25", "0", "00002 07:00:00"));
        file.add(DbFileTest.row(ITEMS, "GOLD_INGOT", 3L, "9.99", "1", "00003 07:00:00"));
        file.add(DbFileTest.row(ITEMS, "IRON_NUGGET", 40L, "0.1", "0", "00003 07:00:00"));
        return file.records();
    }

    private static List<String> items(String qryslt, String... sort) throws ElclException {
        List<String> items = new ArrayList<>();
        for (DbRecord record : Query.run(sample(), Query.selection(qryslt, ITEMS, "ZAGLIB/ITEMS"), Query.sort(List.of(sort), ITEMS, "ZAGLIB/ITEMS"))) {
            items.add((String) record.value(0));
        }
        return items;
    }

    @Test
    void selectionAndSort() throws ElclException {
        assertEquals(List.of("COAL", "GOLD_INGOT", "IRON_INGOT", "IRON_NUGGET"), items("*ALL"));
        assertEquals(List.of("GOLD_INGOT", "IRON_INGOT"), items("QTY *LT 100 *AND ACTIVE"));
        assertEquals(List.of("IRON_INGOT", "IRON_NUGGET"), items("%SST(ITEM 1 4) *EQ \"IRON\""));
        assertEquals(List.of("IRON_INGOT", "IRON_NUGGET"), items("%SST(ITEM 1 4) = 'IRON'"));
        assertEquals(List.of("COAL"), items("&PRICE < 0.5 & QTY > 100"));
        assertEquals(List.of("GOLD_INGOT", "IRON_INGOT"), items("UPDATED *GE \"00002 07:00:00\" *AND *NOT (PRICE *LT 1)", "PRICE", "*DESCEND"));
        assertEquals(List.of("COAL", "IRON_INGOT", "IRON_NUGGET", "GOLD_INGOT"), items("*ALL", "QTY", "*DESCEND", "PRICE", "*DESCEND"));
        assertEquals(List.of("GOLD_INGOT", "IRON_NUGGET", "IRON_INGOT", "COAL"), items("*ALL", "QTY", "ITEM", "*DESCEND"));
        assertEquals("ELC2242", assertThrows(ElclException.class, () -> items("WEIGHT *GT 1")).elclMessage().id());
        assertEquals("ELC2242", assertThrows(ElclException.class, () -> items("ITEM *EQ IRON_INGOT")).elclMessage().id());
        assertEquals("ELC2242", assertThrows(ElclException.class, () -> items("*ALL", "WEIGHT")).elclMessage().id());
        assertEquals("ELC0001", assertThrows(ElclException.class, () -> items("QTY *LT")).elclMessage().id());
        assertEquals("ELC2224", assertThrows(ElclException.class, () -> items("*ALL", "ITEM", "QTY", "PRICE", "ACTIVE", "UPDATED")).elclMessage().id());
    }

    @Test
    void doubleQuotedLiterals() {
        assertEquals("ITEM *EQ 'IRON' *AND NAME *EQ 'it''s \"x\"'", Query.singleQuoted("ITEM *EQ \"IRON\" *AND NAME *EQ \"it's \"\"x\"\"\""));
        assertEquals("ITEM *EQ 'say \"hi\"'", Query.singleQuoted("ITEM *EQ 'say \"hi\"'"));
    }

    @Test
    void reportLines() throws ElclException {
        List<DbRecord> records = sample();
        List<String> headings = Report.headings(ITEMS);
        assertEquals(1, headings.size());
        // ITEM 20 wide, QTY 12 (11 digits and a sign), PRICE 11, ACTIVE its heading's 6, UPDATED 14; two blanks between.
        assertEquals("ITEM" + " ".repeat(16 + 2 + 9) + "QTY" + " ".repeat(2 + 6) + "PRICE  ACTIVE  UPDATED", headings.getFirst());
        assertEquals("COAL" + " ".repeat(16 + 2 + 9) + "900" + " ".repeat(2 + 7) + "0.25  0       00002 07:00:00", Report.line(ITEMS, records.getFirst().values()));
        List<String> printed = Report.print("ZAGLIB/ITEMS", "QTY *LT 100", "Day 3 07:00", "ELNET01", ITEMS, records.subList(0, 1), 4);
        assertEquals("Selection  QTY *LT 100", printed.get(1));
        assertEquals("1 of 4 records selected", printed.get(printed.size() - 2));
        assertEquals(Report.END, printed.getLast());
        // Stacked COLHDGs: on the bottom lines.
        RecordFormat stacked = DbFileTest.format("A R REC", "A ITEM 4A COLHDG('Item' 'ID')", "A N 3S 0");
        assertEquals(List.of("Item", "ID       N"), Report.headings(stacked));
    }

    @Test
    void csvRoundTrips() throws ElclException {
        List<DbRecord> records = new ArrayList<>(sample());
        DbFile file = DbFileTest.file(ITEMS);
        records.forEach(record -> {
            try {
                file.add(record.values());
            } catch (ElclException e) {
                throw new AssertionError(e);
            }
        });
        file.add(DbFileTest.row(ITEMS, "a, \"quoted\" item", 1L, "1", "0", "00004 06:00:00"));
        file.add(DbFileTest.row(ITEMS, " padded", 2L, "2", "1", "00004 06:00:00"));
        String csv = Csv.write(ITEMS, file.records());
        assertTrue(csv.startsWith("ITEM,QTY,PRICE,ACTIVE,UPDATED\n"), csv);
        assertTrue(csv.contains("\"a, \"\"quoted\"\" item\",1,1.00,0,00004 06:00:00\n"), csv);
        assertTrue(csv.contains("\" padded\",2,2.00,1,00004 06:00:00\n"), csv);
        List<Object[]> back = Csv.records(ITEMS, Csv.read(csv));
        assertEquals(file.size(), back.size());
        for (int i = 0; i < back.size(); i++) {
            assertEquals(List.of(file.records().get(i).values()), List.of(back.get(i)), "Row " + i);
        }
    }

    @Test
    void csvHeadingsByNameOrPosition() throws ElclException {
        List<Object[]> named = Csv.records(ITEMS, Csv.read("qty,item\r\n5,STONE\r\n\r\n7,\"DIRT\"\r\n"));
        assertEquals(2, named.size());
        assertEquals("STONE", named.get(0)[0]);
        assertEquals(5L, named.get(0)[1]);
        assertEquals(new BigDecimal("0.00"), named.get(0)[2]);
        assertEquals("", named.get(0)[4]);
        List<Object[]> positional = Csv.records(ITEMS, Csv.read("SAND,3,1.25,1,00001 06:00:00\n"));
        assertEquals("SAND", positional.getFirst()[0]);
        assertEquals(new BigDecimal("1.25"), positional.getFirst()[2]);
        ElclException bad = assertThrows(ElclException.class, () -> Csv.records(ITEMS, Csv.read("ITEM,QTY\nA,1\nB,many\n")));
        assertEquals("ELC2243", bad.elclMessage().id());
        assertEquals("Row 3: value 'many' not valid for field QTY.", bad.elclMessage().text());
    }
}
