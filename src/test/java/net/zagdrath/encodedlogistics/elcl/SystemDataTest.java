/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.zagdrath.encodedlogistics.elcl.screen.MessageService;
import net.zagdrath.encodedlogistics.elcl.screen.SpoolService;
import net.zagdrath.encodedlogistics.elcl.store.SystemData;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
        assertEquals(5, loaded.libraries.get("ELSYS").members.size());
        assertTrue(loaded.libraries.get("ELSYS").system());
        assertNotNull(loaded.libraries.get("ELGPL"));
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
