/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.zagdrath.encodedlogistics.elcl.db.DbFile;
import net.zagdrath.encodedlogistics.elcl.db.DbRecord;
import net.zagdrath.encodedlogistics.elcl.db.Dds;
import net.zagdrath.encodedlogistics.elcl.db.RecordFormat;
import net.zagdrath.encodedlogistics.elcl.screen.MessageService;
import net.zagdrath.encodedlogistics.elcl.screen.SpoolService;
import net.zagdrath.encodedlogistics.elcl.store.SystemData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

// A system's saved data round-trips through NBT: libraries, members (sequence numbers, change dates), programs (their
// source member and compile date, and still runnable), message queues, spooled files, system values. ELSYS isn't saved
// but is there again after a load; members cost a byte per 64 characters, ELSYS nothing.
class SystemDataTest {
    @Test
    void roundTrips() {
        SystemData data = new SystemData();
        SystemData.Library library = new SystemData.Library("ZAGLIB", "*TEST", "Scripts", "ZAGDRATH", "*CHANGE", "Day 3  07:00");
        List<SourceLine> lines = List.of(new SourceLine(100, "PGM", 3), new SourceLine(150, "  DCL VAR(&N) TYPE(*INT)", 4), new SourceLine(200, "ENDPGM", 3));
        library.members.put("COUNT", new SystemData.Member("COUNT", "Counter", lines, 2, "Day 4  08:00"));
        library.programs.put("COUNT", new SystemData.Program("COUNT", "ZAGLIB", "COUNT", 1, "Day 3  09:00", lines));
        data.libraries.put("ZAGLIB", library);
        data.queues.put("ZAGDRATH", new java.util.ArrayList<>(List.of(new MessageService.Message(7, "USR0001", 0, "QSYS", "Day 4  08:00", "Hello", true))));
        data.nextMessage = 8;
        data.spooled.add(new SpoolService.SpooledFile(3, "QPRINT", "000101", "QINTER", "ZAGDRATH", 1, "*RDY", "Day 4  08:01", List.of("one", "two")));
        data.nextSpooled = 4;
        data.sysvals.put("SECLVL", "10");

        CompoundTag tag = data.save();
        assertFalse(tag.toString().contains("ELSYS"), "ELSYS saved");
        SystemData loaded = SystemData.load(tag);

        SystemData.Library lib = loaded.libraries.get("ZAGLIB");
        assertNotNull(lib);
        assertEquals("*TEST", lib.type);
        assertEquals("*CHANGE", lib.authority);
        assertEquals("ZAGDRATH", lib.owner);
        SystemData.Member member = lib.members.get("COUNT");
        assertEquals(lines, member.lines);
        assertEquals(2, member.version);
        assertEquals("Day 4  08:00", member.updated);
        SystemData.Program program = lib.programs.get("COUNT");
        assertEquals("Day 3  09:00", program.compiled);
        assertEquals(1, program.sourceVersion);
        assertNotNull(program.compiled(), "Program doesn't compile again");
        assertEquals("Hello", loaded.queues.get("ZAGDRATH").getFirst().text());
        assertTrue(loaded.queues.get("ZAGDRATH").getFirst().unread());
        assertEquals(8, loaded.nextMessage);
        assertEquals(List.of("one", "two"), loaded.spooled.getFirst().lines());
        assertEquals(4, loaded.nextSpooled);
        assertEquals("10", loaded.sysvals.get("SECLVL"));
        assertEquals(8, loaded.libraries.get("ELSYS").members.size());
        assertTrue(loaded.libraries.get("ELSYS").system());
        assertNotNull(loaded.libraries.get("ELGPL"));
    }

    // Physical files round-trip with their records (numbers kept), PF members keep their type, a program the formats it
    // was compiled with (so it compiles again); records cost storage as source does; ELSYS has its system files and
    // the samples that compile there as programs.
    @Test
    void filesRoundTripAndCostStorage() throws ElclException {
        SystemData data = new SystemData();
        SystemData.Library library = new SystemData.Library("ZAGLIB", "*PROD", "", "ZAGDRATH", "*USE", "Day 3  07:00");
        List<String> dds = List.of("A UNIQUE", "A R STOCKREC", "A ITEM 54A", "A QTY 10S 0", "A K ITEM");
        RecordFormat format = Dds.compile(dds).format();
        DbFile file = new DbFile("STOCK", "Stock levels", format, "ZAGLIB", "STOCKSRC", 2, "Day 3  07:05", false);
        file.add(new Object[] { "IRON_INGOT", 40L });
        DbRecord coal = file.add(new Object[] { "COAL", 900L });
        file.delete(1);
        library.files.put("STOCK", file);
        library.members.put("STOCKSRC", new SystemData.Member("STOCKSRC", SystemData.PF, "Stock", SourceLine.number(dds, 3), 2, "Day 3  07:00"));
        List<SourceLine> program = SourceLine.number(List.of("PGM", "DCLF FILE(STOCK)", "RCVF", "ENDPGM"), 3);
        library.programs.put("READ", new SystemData.Program("READ", "ZAGLIB", "READ", 1, "Day 3  07:10", program, Map.of("*LIBL/STOCK", format.save())));
        data.libraries.put("ZAGLIB", library);
        // 64 characters a record, a byte per 64: one record, one byte.
        assertEquals(cost(dds, 64) + 1, data.storageBytes(64));

        SystemData loaded = SystemData.load(data.save());
        SystemData.Library lib = loaded.libraries.get("ZAGLIB");
        DbFile back = lib.files.get("STOCK");
        assertNotNull(back);
        assertEquals(format, back.format);
        assertEquals("Stock levels", back.text);
        assertEquals("STOCKSRC", back.sourceMember);
        assertEquals(1, back.size());
        assertEquals(900L, back.get(coal.rrn()).value(1));
        assertEquals(3, back.nextRrn());
        assertEquals(SystemData.PF, lib.members.get("STOCKSRC").type);
        assertNotNull(lib.programs.get("READ").compiled(), "A program declaring a file doesn't compile again");
        assertEquals(data.storageBytes(64), loaded.storageBytes(64));

        SystemData.Library elsys = loaded.libraries.get("ELSYS");
        assertTrue(elsys.files.keySet().containsAll(List.of("INVITEMS", "DEVICES", "CRFHIST", "JOBS")));
        assertTrue(elsys.files.get("INVITEMS").system);
        assertEquals(SystemData.PF, elsys.members.get("ITEMHIST").type);
        assertNotNull(elsys.programs.get("ITEMSETUP"));
        assertNull(elsys.programs.get("LOGITEMS"), "LOGITEMS needs ELGPL/ITEMHIST: a member to compile, not a program");
        assertEquals("Item counts logged by LOGITEMS", elsys.members.get("ITEMHIST").text);
        assertFalse(loaded.save().toString().contains("INVITEMS"), "ELSYS's files saved");
    }

    private static long cost(List<String> lines, int perByte) {
        return SystemData.cost(lines.stream().mapToInt(String::length).sum(), perByte);
    }

    @Test
    void membersCostStorage() {
        SystemData data = new SystemData();
        assertEquals(0, data.storageBytes(64), "ELSYS costs storage");
        SystemData.Library library = new SystemData.Library("ZAGLIB", "*PROD", "", "ZAGDRATH", "*USE", "");
        library.members.put("A", new SystemData.Member("A", "", List.of(new SourceLine(100, "x".repeat(64), 1)), 0, ""));
        library.members.put("B", new SystemData.Member("B", "", List.of(new SourceLine(100, "x".repeat(65), 1)), 0, ""));
        data.libraries.put("ZAGLIB", library);
        assertEquals(3, data.storageBytes(64));
    }
}
