/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.elcl.compile.FileResolver;
import net.zagdrath.encodedlogistics.elcl.db.DbFile;
import net.zagdrath.encodedlogistics.elcl.db.DbRecord;
import net.zagdrath.encodedlogistics.elcl.db.Dds;
import net.zagdrath.encodedlogistics.elcl.db.FieldDef;
import net.zagdrath.encodedlogistics.elcl.db.FileAccess;
import net.zagdrath.encodedlogistics.elcl.db.RecordFormat;
import net.zagdrath.encodedlogistics.elcl.db.Timestamps;
import net.zagdrath.encodedlogistics.elcl.vm.Vm;

// Files in programs (DCLF and the file operations) on the VM, against files in memory: RCVF reads a file to its end and
// ELC2201 is monitorable as CL programs do; POSDBF and CLOF read it again; CHNRCD reads by key (ELC2202); WRTRCD writes
// (ELC2203 for a duplicate unique key; a blank timestamp comes back filled); UPDRCD and DLTRCD change the record last
// read (ELC2204 without one); a file changed under the program is a level check (ELC2207); and a job saved part-way
// through a file reads on from where it was.
class DbVmTest {
    // Files by LIB/NAME, as a job's FileAccess sees them; *LIBL looks in ZAGLIB.
    static final class Files implements FileAccess {
        final Map<String, DbFile> files = new HashMap<>();
        long clock = 24_000 * 4 + 1_000;

        DbFile add(String library, String name, RecordFormat format) {
            DbFile file = new DbFile(name, "", format, library, name, 0, "", false);
            files.put(library + "/" + name, file);
            return file;
        }

        DbFile file(Opened opened) throws ElclException {
            DbFile file = files.get(opened.qualified());
            if (file == null) {
                throw new ElclException("ELC2205", opened.file(), opened.library());
            }
            return file;
        }

        @Override
        public Opened open(String library, String file) throws ElclException {
            String lib = library.equals("*LIBL") ? "ZAGLIB" : library;
            DbFile found = files.get(lib + "/" + file);
            if (found == null) {
                throw new ElclException("ELC2205", file, library);
            }
            return new Opened(lib, file, found.format);
        }

        @Override
        public @Nullable DbRecord next(Opened file, DbRecord.@Nullable Position after) throws ElclException {
            return file(file).next(after);
        }

        @Override
        public @Nullable DbRecord chain(Opened file, Object[] key) throws ElclException {
            return file(file).chain(key);
        }

        private Object[] stamped(DbFile file, Object[] values) {
            Object[] stamped = values.clone();
            for (int i = 0; i < stamped.length; i++) {
                if (file.format.fields().get(i).type() == FieldDef.Type.TIME && String.valueOf(stamped[i]).isBlank()) {
                    stamped[i] = Timestamps.of(clock);
                }
            }
            return stamped;
        }

        @Override
        public DbRecord write(Opened file, Object[] values) throws ElclException {
            DbFile target = file(file);
            return target.add(stamped(target, values));
        }

        @Override
        public DbRecord update(Opened file, long rrn, Object[] values) throws ElclException {
            DbFile target = file(file);
            return target.update(rrn, stamped(target, values));
        }

        @Override
        public void delete(Opened file, long rrn) throws ElclException {
            if (!file(file).delete(rrn)) {
                throw new ElclException("ELC2204", file.qualified());
            }
        }
    }

    static RecordFormat format(String... lines) {
        Dds.Result result = Dds.compile(List.of(lines));
        assertTrue(result.ok(), result.diagnostics().toString());
        return result.format();
    }

    static final RecordFormat STOCK = format("A UNIQUE", "A R STOCKREC", "A ITEM 20A", "A QTY 9S 0", "A COST 7P 2", "A HOT 1L", "A SEEN T", "A K ITEM");

    private Files files;
    private VmTest.Host host;

    private DbFile stock() throws ElclException {
        files = new Files();
        DbFile file = files.add("ZAGLIB", "STOCK", STOCK);
        file.add(new Object[] { "IRON_INGOT", 40L, new java.math.BigDecimal("1.50"), true, "00002 07:00:00" });
        file.add(new Object[] { "COAL", 900L, new java.math.BigDecimal("0.25"), false, "00002 07:00:00" });
        file.add(new Object[] { "GOLD_INGOT", 3L, new java.math.BigDecimal("9.99"), true, "00003 07:00:00" });
        host = new VmTest.Host();
        host.files = files;
        host.formats.put("ZAGLIB/STOCK", STOCK.save());
        host.formats.put("*LIBL/STOCK", STOCK.save());
        return file;
    }

    private List<String> run(String... lines) throws ElclException {
        host.program("MAIN", lines);
        return VmTest.run(host);
    }

    @Test
    void readToTheEndAndMonitorIt() throws ElclException {
        stock();
        List<String> said = run("PGM", "DCLF FILE(ZAGLIB/STOCK)", "DCL VAR(&N) TYPE(*INT)",
                "LOOP: RCVF", "MONMSG MSGID(ELC2201) EXEC(GOTO CMDLBL(DONE))",
                "CHGVAR VAR(&N) VALUE(&N + 1)", "SNDPGMMSG MSG(%TRIM(&ITEM) *BCAT %CHAR(&QTY) *BCAT %CHAR(&COST) *BCAT &SEEN)", "GOTO CMDLBL(LOOP)",
                "DONE: SNDPGMMSG MSG('Read' *BCAT %CHAR(&N))",
                // Still at the end: ELC2201 again, caught by the range.
                "RCVF", "MONMSG MSGID(ELC2200) EXEC(SNDPGMMSG MSG('Still at the end'))",
                // POSDBF *START reads it again from the top.
                "POSDBF POSITION(*START)", "RCVF", "SNDPGMMSG MSG('Again' *BCAT &ITEM)",
                // CLOF too.
                "RCVF", "CLOF", "RCVF", "SNDPGMMSG MSG('Reopened' *BCAT &ITEM)",
                "POSDBF POSITION(*END)", "RCVF", "MONMSG MSGID(ELC2201) EXEC(SNDPGMMSG MSG('Positioned at the end'))",
                "ENDPGM");
        assertEquals(List.of("COAL 900 0.25 00002 07:00:00", "GOLD_INGOT 3 9.99 00003 07:00:00", "IRON_INGOT 40 1.50 00002 07:00:00", "Read 3",
                "Still at the end", "Again COAL", "Reopened COAL", "Positioned at the end"), said);
    }

    @Test
    void anUnmonitoredEndOfFileEndsTheProgram() throws ElclException {
        stock();
        run("PGM", "DCLF FILE(STOCK)", "RCVF", "RCVF", "RCVF", "RCVF", "ENDPGM");
        assertNotNull(host.failed);
        assertEquals("ELC2201", host.failed.id());
        assertEquals("End of file ZAGLIB/STOCK reached.", host.failed.text());
    }

    @Test
    void openIdsPrefixTheVariables() throws ElclException {
        stock();
        files.add("ZAGLIB", "COPY", STOCK);
        host.formats.put("ZAGLIB/COPY", STOCK.save());
        List<String> said = run("PGM", "DCLF FILE(ZAGLIB/STOCK) OPNID(IN)", "DCLF FILE(ZAGLIB/COPY) OPNID(OUT)",
                "DOWHILE COND('1')", "RCVF OPNID(IN)", "MONMSG MSGID(ELC2201) EXEC(LEAVE)",
                "CHGVAR VAR(&OUT_ITEM) VALUE(&IN_ITEM)", "CHGVAR VAR(&OUT_QTY) VALUE(&IN_QTY * 2)", "CHGVAR VAR(&OUT_HOT) VALUE(*NOT &IN_HOT)",
                "WRTRCD OPNID(OUT)", "ENDDO",
                "CHNRCD OPNID(OUT) KEY('COAL')", "SNDPGMMSG MSG(%CHAR(&OUT_QTY) *BCAT &OUT_SEEN)", "IF COND(&OUT_HOT) THEN(SNDPGMMSG MSG('hot'))",
                "ENDPGM");
        // A timestamp written blank took the clock's time (day 5).
        assertEquals(List.of("1800 00005 07:00:00", "hot"), said);
        assertEquals(3, files.files.get("ZAGLIB/COPY").size());
    }

    @Test
    void chainWriteUpdateDelete() throws ElclException {
        DbFile file = stock();
        List<String> said = run("PGM", "DCLF FILE(ZAGLIB/STOCK)", "DCL VAR(&KEY) TYPE(*CHAR) LEN(20) VALUE('GOLD_INGOT')",
                "DCL VAR(&MSG) TYPE(*CHAR) LEN(64)",
                "CHNRCD KEY(&KEY)", "SNDPGMMSG MSG(%CHAR(&QTY))",
                "CHGVAR VAR(&QTY) VALUE(&QTY + 10)", "UPDRCD", "DLTRCD",
                "CHNRCD KEY('NETHERITE')", "MONMSG MSGID(ELC2202) EXEC(RCVMSG RTNMSG(&MSG))", "SNDPGMMSG MSG(&MSG)",
                "UPDRCD", "MONMSG MSGID(ELC2204) EXEC(SNDPGMMSG MSG('Nothing read'))",
                "CHGVAR VAR(&ITEM) VALUE('COAL')", "WRTRCD", "MONMSG MSGID(ELC2203) EXEC(SNDPGMMSG MSG('Duplicate'))",
                "CHGVAR VAR(&ITEM) VALUE('STONE')", "CHGVAR VAR(&SEEN) VALUE('Day 9 12:00')", "WRTRCD",
                // Reads after CHNRCD carry on from it.
                "CHNRCD KEY('IRON_INGOT')", "RCVF", "SNDPGMMSG MSG('After iron:' *BCAT &ITEM *BCAT &SEEN)",
                "ENDPGM");
        assertEquals(List.of("3", "Record with key NETHERITE not found in file ZAGLIB/STOCK.", "Nothing read", "Duplicate",
                "After iron: STONE 00009 12:00:00"), said);
        assertEquals(List.of("COAL", "IRON_INGOT", "STONE"), file.records().stream().map(record -> record.value(0)).toList());
    }

    @Test
    void aFileChangedUnderTheProgramIsALevelCheck() throws ElclException {
        stock();
        host.program("MAIN", "PGM", "DCLF FILE(ZAGLIB/STOCK)", "RCVF", "ENDPGM");
        Vm vm = VmTest.start(host, "MAIN");
        // The file is made again with QTY as a decimal.
        RecordFormat changed = format("A UNIQUE", "A R STOCKREC", "A ITEM 20A", "A QTY 9P 0", "A COST 7P 2", "A HOT 1L", "A SEEN T", "A K ITEM");
        files.add("ZAGLIB", "STOCK", changed);
        vm.run(100);
        assertNotNull(host.failed);
        assertEquals("ELC2207", host.failed.id());
        // A field added doesn't matter: the program still runs.
        stock();
        RecordFormat wider = format("A UNIQUE", "A R STOCKREC", "A ITEM 20A", "A QTY 9S 0", "A COST 7P 2", "A HOT 1L", "A SEEN T", "A NOTE 10A",
                "A K ITEM");
        files.add("ZAGLIB", "STOCK", wider).add(new Object[] { "SAND", 1L, new java.math.BigDecimal("0"), false, "00001 06:00:00", "x" });
        List<String> said = run("PGM", "DCLF FILE(ZAGLIB/STOCK)", "RCVF", "SNDPGMMSG MSG(&ITEM)", "CHGVAR VAR(&ITEM) VALUE('CLAY')", "WRTRCD", "ENDPGM");
        assertEquals(List.of("SAND"), said);
        assertEquals("", files.files.get("ZAGLIB/STOCK").chain(new Object[] { "CLAY" }).value(5));
    }

    @Test
    void aSavedJobReadsOnWhereItWas() throws ElclException {
        stock();
        host.program("MAIN", "PGM", "DCLF FILE(ZAGLIB/STOCK)", "RCVF", "DLYJOB DLY(1)", "RCVF", "SNDPGMMSG MSG(&ITEM)", "ENDPGM");
        Vm vm = VmTest.start(host, "MAIN");
        vm.run(2);
        assertEquals(Vm.State.WAITING, vm.state());
        Vm loaded = Vm.load(host, vm.save());
        host.time += 100;
        for (int i = 0; i < 10 && loaded.state() != Vm.State.ENDED; i++) {
            loaded.run(100);
        }
        assertEquals(List.of("GOLD_INGOT"), host.said);
    }

    @Test
    void theCompilerChecksFileOperations() {
        Map<String, RecordFormat> formats = new HashMap<>();
        formats.put("ZAGLIB/STOCK", STOCK);
        FileResolver files = FileResolver.of(formats);
        List<String> ok = List.of("PGM", "DCLF FILE(ZAGLIB/STOCK) OPNID(S)", "CHGVAR VAR(&S_QTY) VALUE(&S_QTY + 1)", "CHGVAR VAR(&S_COST) VALUE(1.25)",
                "CHGVAR VAR(&S_HOT) VALUE(*TRUE)", "CHGVAR VAR(&S_SEEN) VALUE('Day 2 06:00')", "CHNRCD OPNID(S) KEY('COAL')", "UPDRCD OPNID(S)", "ENDPGM");
        Compiler.Result result = Compiler.compileTexts(ok, files);
        assertTrue(result.ok(), result.diagnostics().toString());
        assertEquals(STOCK, result.formats().get("ZAGLIB/STOCK"));
        assertEquals("*CHAR", result.variables().get("&S_ITEM").type().special());
        assertEquals(20, result.variables().get("&S_ITEM").length());
        assertEquals("*INT", result.variables().get("&S_QTY").type().special());
        assertEquals("7 2", result.variables().get("&S_COST").lengthText());
        assertEquals("*LGL", result.variables().get("&S_HOT").type().special());
        assertEquals(14, result.variables().get("&S_SEEN").length());
        expect(files, "ELC2205", 1, "PGM", "DCLF FILE(ZAGLIB/NOPE)", "ENDPGM");
        expect(files, "ELC2206", 2, "PGM", "DCLF FILE(ZAGLIB/STOCK) OPNID(S)", "RCVF", "ENDPGM");
        expect(files, "ELC2206", 2, "PGM", "DCLF FILE(ZAGLIB/STOCK)", "RCVF OPNID(OTHER)", "ENDPGM");
        expect(files, "ELC0016", 2, "PGM", "SNDPGMMSG MSG('x')", "DCLF FILE(ZAGLIB/STOCK)", "ENDPGM");
        expect(files, "ELC0103", 2, "PGM", "DCLF FILE(ZAGLIB/STOCK)", "DCLF FILE(ZAGLIB/STOCK)", "ENDPGM");
        expect(files, "ELC0103", 2, "PGM", "DCLF FILE(ZAGLIB/STOCK)", "DCL VAR(&ITEM) TYPE(*CHAR)", "ENDPGM");
        expect(files, "ELC0103", 2, "PGM", "DCLF FILE(ZAGLIB/STOCK)", "CHNRCD KEY('A' 'B')", "ENDPGM");
        expect(files, "ELC0002", 2, "PGM", "DCLF FILE(ZAGLIB/STOCK)", "CHGVAR VAR(&S_QTY) VALUE(1)", "ENDPGM");
        expect(files, "ELC0003", 2, "PGM", "DCLF FILE(ZAGLIB/STOCK)", "CHGVAR VAR(&QTY) VALUE('many')", "ENDPGM");
        expect(files, "ELC0106", 0, "RCVF");
        // The editor's lenient check: a file it doesn't know leaves its variables unchecked.
        FileResolver lenient = new FileResolver() {
            @Override
            public @Nullable RecordFormat format(String library, String file) {
                return null;
            }

            @Override
            public boolean lenient() {
                return true;
            }
        };
        assertTrue(Compiler.compileTexts(List.of("PGM", "DCLF FILE(ZAGLIB/NOPE) OPNID(N)", "CHGVAR VAR(&N_X) VALUE(1)", "RCVF OPNID(N)", "ENDPGM"), lenient)
                .diagnostics().isEmpty());
    }

    private static void expect(FileResolver files, String id, int line, String... lines) {
        if (id.equals("ELC0106")) {
            assertEquals(id, net.zagdrath.encodedlogistics.elcl.exec.CommandRunner.run(null, lines[0]).escape().id());
            return;
        }
        Compiler.Result result = Compiler.compileTexts(List.of(lines), files);
        assertTrue(!result.diagnostics().isEmpty(), "No message for " + List.of(lines));
        Diagnostic first = result.diagnostics().getFirst();
        assertEquals(id, first.message().id(), first.message().toString());
        assertEquals(line, first.line(), first.message().toString());
    }

    // The cross reference: a file's variables set by RCVF, used by WRTRCD.
    @Test
    void theCrossReference() {
        Map<String, RecordFormat> formats = new HashMap<>();
        formats.put("*LIBL/STOCK", STOCK);
        Compiler.Result result = Compiler.compileTexts(List.of("PGM", "DCLF FILE(STOCK)", "RCVF", "WRTRCD", "ENDPGM"), FileResolver.of(formats));
        List<String> refs = new ArrayList<>();
        result.variableRefs().get("&QTY").forEach(ref -> refs.add(ref.line() + (ref.modified() ? "*" : "")));
        assertEquals(List.of("1", "2*", "3"), refs);
    }
}
