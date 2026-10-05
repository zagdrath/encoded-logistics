/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.db.DbRecord;
import net.zagdrath.encodedlogistics.elcl.db.FieldDef;
import net.zagdrath.encodedlogistics.elcl.exec.OsCommands;
import net.zagdrath.encodedlogistics.elcl.job.JobManager;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.FileService;
import net.zagdrath.encodedlogistics.elcl.screen.LibraryService;
import net.zagdrath.encodedlogistics.elcl.screen.SpoolService;
import net.zagdrath.encodedlogistics.elcl.store.ElclConfig;
import net.zagdrath.encodedlogistics.elcl.store.ElclStore;
import net.zagdrath.encodedlogistics.elcl.store.StoredLibraryService;
import net.zagdrath.encodedlogistics.elcl.sync.FolderSync;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;

// Database files in the game: PF members compiled by CRTPF (every field type; errors in the listing with their
// sequence numbers); programs reading and writing files (DCLF, RCVF to the end with ELC2201 monitored, CHNRCD, WRTRCD
// with a unique key, UPDRCD, DLTRCD) and a level check after CHGPF, which keeps records field by field; RUNQRY's
// selection and sort to a spooled file and to an output file; ELSYS's system files showing the network as it is now;
// CSV out and back in; CPYF; files, records and storage surviving a save and reload; the record limit and storage
// full; authority to read and write; and the shipped ITEMSETUP / LOGITEMS example.
final class DatabaseGameTests {
    // A file of every field type, keyed and unique.
    static final List<String> STOCK = List.of(
            "     A* STOCK - stock levels",
            "     A                                      UNIQUE",
            "     A          R STOCKREC                  TEXT('Stock record')",
            "     A            ITEM          20A         COLHDG('Item')",
            "     A            QTY            9S 0       COLHDG('Quantity')",
            "     A            COST           7P 2",
            "     A            HOT            1L",
            "     A            SEEN           8T",
            "     A          K ITEM");

    private DatabaseGameTests() {}

    private record Rig(TerminalContext context, ElclSystem system) {
        String user() {
            return context.user();
        }

        FileService.Who who() {
            return new FileService.Who(context.user(), true);
        }
    }

    @FunctionalInterface
    private interface Body {
        void accept(GameTestHelper helper, GameTestSequence sequence, Rig[] rig);
    }

    // The desk rig (a rack, its drive and a Terminal Desk) with 64 cobblestone and 10 iron ingots stored and the user's
    // interactive job; the body's steps run once it's online.
    @SuppressWarnings("removal")
    private static void rig(GameTestHelper helper, Body body) {
        ElclGameTests.desk(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Rig[] rig = new Rig[1];
        GameTestSequence sequence = helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    TerminalDeskBlockEntity desk = helper.getBlockEntity(ElclGameTests.DESK, TerminalDeskBlockEntity.class);
                    TerminalContext context = new TerminalContext(helper.getLevel().getServer(), desk.network(), desk, player);
                    helper.assertTrue(context.network() != null, "Desk offline");
                    rig[0] = new Rig(context, new ElclSystem(context.server(), context.network()));
                    ElclServices.jobs().interactive(rig[0].system(), context.user(), "db", "ELDESK01");
                    NetworkStorage storage = RackGameTests.storage(helper, ElclGameTests.BAY);
                    storage.insert(StorageKey.of(new ItemStack(Items.COBBLESTONE)), 64, false);
                    storage.insert(StorageKey.of(new ItemStack(Items.IRON_INGOT)), 10, false);
                });
        body.accept(helper, sequence, rig);
        sequence.thenSucceed();
    }

    // A member of a type (ELCLP or PF) with these lines, its library made if it isn't there.
    static void member(Rig rig, String library, String name, String type, List<String> lines) {
        try {
            try {
                ElclServices.libraries().createLibrary(rig.system(), rig.user(), library, "*PROD", "");
            } catch (ElclException ignored) {}
            ElclServices.libraries().createMember(rig.system(), rig.user(), library, name, "", type);
            ElclServices.libraries().save(rig.system(), rig.user(), library, name, SourceLine.number(lines, 1));
        } catch (ElclException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    // An ELCLP member compiled to a program.
    static void program(Rig rig, String library, String name, String... lines) {
        member(rig, library, name, "ELCLP", List.of(lines));
        try {
            LibraryService.CompileOutcome outcome = ElclServices.libraries().compile(rig.system(), rig.user(), library, name, library, name);
            if (!outcome.created()) {
                throw new IllegalStateException(name + " didn't compile: " + outcome.diagnostics());
            }
        } catch (ElclException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    // DBLIB/STOCK made, with IRON_INGOT 40, COAL 900 and GOLD_INGOT 3.
    static void stock(GameTestHelper helper, Rig rig) {
        member(rig, "DBLIB", "STOCK", "PF", STOCK);
        ElclGameTests.expect(helper, rig.context(), "CRTPF FILE(DBLIB/STOCK)", "ELC2230");
        try {
            for (Object[] row : List.of(new Object[] { "IRON_INGOT", 40L, new BigDecimal("1.50"), true, "" },
                    new Object[] { "COAL", 900L, new BigDecimal("0.25"), false, "" }, new Object[] { "GOLD_INGOT", 3L, new BigDecimal("9.99"), true, "" })) {
                ElclServices.files().write(rig.system(), rig.who(), "DBLIB", "STOCK", row);
            }
        } catch (ElclException e) {
            helper.fail("Writing STOCK: " + e.getMessage());
        }
    }

    static List<DbRecord> records(GameTestHelper helper, Rig rig, String library, String file) {
        try {
            return ElclServices.files().records(rig.system(), rig.who(), library, file);
        } catch (ElclException e) {
            helper.fail("Reading " + library + "/" + file + ": " + e.getMessage());
            return List.of();
        }
    }

    static List<Object> column(List<DbRecord> records, int field) {
        List<Object> values = new ArrayList<>();
        records.forEach(record -> values.add(record.value(field)));
        return values;
    }

    private static boolean idle(Rig rig) {
        String job = OsCommands.interactiveJob(rig.system(), rig.user()).number();
        return JobManager.of(rig.system().server()).run(rig.system(), job) == null;
    }

    static SpoolService.SpooledFile spooled(GameTestHelper helper, Rig rig, String name) {
        for (SpoolService.SpooledFile file : ElclServices.spool().files(rig.system(), rig.user(), null)) {
            if (file.name().equals(name)) {
                return file;
            }
        }
        helper.fail("No spooled file " + name);
        return null;
    }

    // --- CRTPF and the listing ---

    static void files(GameTestHelper helper) {
        rig(helper, (h, sequence, rig) -> sequence.thenExecute(() -> {
            Rig r = rig[0];
            TerminalContext c = r.context();
            ElclGameTests.expect(h, c, "CRTLIB LIB(DBLIB)", "ELC0210");
            ElclGameTests.expect(h, c, "CRTMBR MBR(DBLIB/STOCK) SRCTYPE(PF)", "ELC0214");
            ElclGameTests.expect(h, c, "CRTMBR MBR(DBLIB/ODD) SRCTYPE(RPGLE)", "ELC0103");
            try {
                ElclServices.libraries().save(r.system(), r.user(), "DBLIB", "STOCK", SourceLine.number(STOCK, 1));
            } catch (ElclException e) {
                h.fail(e.getMessage());
            }
            ElclGameTests.expect(h, c, "CRTPF FILE(DBLIB/STOCK)", "ELC2230");
            ElclGameTests.expect(h, c, "CRTPF FILE(DBLIB/STOCK)", "ELC0204");
            try {
                FileService.File file = ElclServices.files().file(r.system(), "DBLIB", "STOCK");
                h.assertTrue(file.text().equals("Stock record") && file.format().fields().size() == 5 && file.source().equals("DBLIB/STOCK"),
                        "STOCK " + file);
                h.assertTrue(file.format().field("SEEN").type() == FieldDef.Type.TIME && file.format().unique(), "STOCK's format " + file.format());
                LibraryService.Member member = ElclServices.libraries().member(r.system(), "DBLIB", "STOCK");
                h.assertTrue(member.type().equals("PF") && member.program() && !member.changed(), "Member " + member);
            } catch (ElclException e) {
                h.fail(e.getMessage());
            }
            // A definition's errors: in the listing with their sequence numbers.
            member(r, "DBLIB", "BAD", "PF", List.of("A R REC", "A NAME 10A", "A QTY 5X", "A K NOPE"));
            ElclGameTests.expect(h, c, "CRTPF FILE(DBLIB/BAD)", "ELC2239");
            List<String> listing = spooled(h, r, "BAD").lines();
            h.assertTrue(listing.stream().anyMatch(line -> line.contains("0003.00  ELC2227")), "Listing " + listing);
            h.assertTrue(listing.stream().anyMatch(line -> line.contains("0004.00  ELC2223")), "Listing " + listing);
            // Programs from ELCLP members, files from PF members.
            ElclGameTests.expect(h, c, "CRTELPGM PGM(DBLIB/STOCK)", "ELC2247");
            program(r, "DBLIB", "HELLO", "PGM", "ENDPGM");
            ElclGameTests.expect(h, c, "CRTPF FILE(DBLIB/HELLO) SRCMBR(DBLIB/HELLO)", "ELC2247");
            ElclGameTests.expect(h, c, "DLTF FILE(DBLIB/STOCK)", "ELC2233");
            ElclGameTests.expect(h, c, "DLTF FILE(DBLIB/STOCK)", "ELC2205");
            ElclGameTests.expect(h, c, "DLTF FILE(ELSYS/INVITEMS)", "ELC0205");
            ElclGameTests.expect(h, c, "CRTPF FILE(ELSYS/MINE) SRCMBR(ELSYS/ITEMHIST)", "ELC0205");
            ElclGameTests.expect(h, c, "RCVF", "ELC0106");
        }));
    }

    // --- Programs on files; CHGPF and the level check ---

    static void programs(GameTestHelper helper) {
        rig(helper, (h, sequence, rig) -> sequence
                .thenExecute(() -> {
                    Rig r = rig[0];
                    member(r, "DBLIB", "STOCK", "PF", STOCK);
                    ElclGameTests.expect(h, r.context(), "CRTPF FILE(DBLIB/STOCK)", "ELC2230");
                    program(r, "DBLIB", "LOAD", "PGM",
                            "DCLF FILE(DBLIB/STOCK)",
                            "DCL VAR(&TOTAL) TYPE(*INT)",
                            "CHGVAR VAR(&ITEM) VALUE('IRON_INGOT')", "CHGVAR VAR(&QTY) VALUE(5)", "WRTRCD",
                            "CHGVAR VAR(&ITEM) VALUE('COAL')", "CHGVAR VAR(&QTY) VALUE(7)", "CHGVAR VAR(&COST) VALUE(0.5)", "WRTRCD",
                            "WRTRCD", "MONMSG MSGID(ELC2203) EXEC(SNDMSG MSG('Duplicate COAL') TOUSR(*REQUESTER))",
                            "CHGVAR VAR(&ITEM) VALUE('SAND')", "CHGVAR VAR(&QTY) VALUE(1)", "WRTRCD",
                            "CHNRCD KEY('SAND')", "DLTRCD",
                            "CHNRCD KEY('COAL')", "CHGVAR VAR(&QTY) VALUE(&QTY * 10)", "UPDRCD",
                            "POSDBF POSITION(*START)",
                            "READ: RCVF", "MONMSG MSGID(ELC2201) EXEC(GOTO CMDLBL(DONE))",
                            "CHGVAR VAR(&TOTAL) VALUE(&TOTAL + &QTY)", "GOTO CMDLBL(READ)",
                            "DONE: SNDMSG MSG('Total' *BCAT %CHAR(&TOTAL)) TOUSR(*REQUESTER)",
                            "ENDPGM");
                    ElclGameTests.expect(h, r.context(), "CALL PGM(DBLIB/LOAD)", "ELC0108");
                })
                .thenWaitUntil(() -> h.assertTrue(ElclVmGameTests.said(rig[0].system(), rig[0].user(), "Total 75"), "LOAD not done"))
                .thenWaitUntil(() -> h.assertTrue(idle(rig[0]), "LOAD still running"))
                .thenExecute(() -> {
                    Rig r = rig[0];
                    h.assertTrue(ElclVmGameTests.said(r.system(), r.user(), "Duplicate COAL"), "ELC2203 not monitored");
                    List<DbRecord> records = records(h, r, "DBLIB", "STOCK");
                    h.assertTrue(column(records, 0).equals(List.of("COAL", "IRON_INGOT")), "Items " + column(records, 0));
                    h.assertTrue(column(records, 1).equals(List.of(70L, 5L)), "Quantities " + column(records, 1));
                    h.assertTrue(records.stream().allMatch(record -> ((String) record.value(4)).matches("\\d{5} \\d\\d:\\d\\d:\\d\\d")),
                            "Timestamps " + column(records, 4));
                    // CHGPF: QTY becomes a decimal (its values go), SEEN goes, NOTE comes; ITEM and COST stay.
                    try {
                        ElclServices.libraries().save(r.system(), r.user(), "DBLIB", "STOCK", SourceLine.number(List.of("A UNIQUE", "A R STOCKREC",
                                "A ITEM 20A", "A QTY 9P 2", "A COST 7P 2", "A HOT 1L", "A NOTE 10A", "A K ITEM"), 2));
                        LibraryService.Member member = ElclServices.libraries().member(r.system(), "DBLIB", "STOCK");
                        h.assertTrue(member.changed(), "Not changed since its file was made: " + member);
                    } catch (ElclException e) {
                        h.fail(e.getMessage());
                    }
                    ElclGameTests.expect(h, r.context(), "CHGPF FILE(DBLIB/STOCK)", "ELC2231  File STOCK changed in library DBLIB: 2 records kept.");
                    List<DbRecord> changed = records(h, r, "DBLIB", "STOCK");
                    h.assertTrue(column(changed, 0).equals(List.of("COAL", "IRON_INGOT")), "Items after CHGPF " + column(changed, 0));
                    h.assertTrue(column(changed, 1).equals(List.of(new BigDecimal("0.00"), new BigDecimal("0.00"))), "QTY kept " + column(changed, 1));
                    h.assertTrue(column(changed, 2).equals(List.of(new BigDecimal("0.50"), BigDecimal.ZERO.setScale(2))), "COST " + column(changed, 2));
                    h.assertTrue(column(changed, 4).equals(List.of("", "")), "NOTE " + column(changed, 4));
                    List<String> listing = spooled(h, r, "STOCK").lines();
                    h.assertTrue(listing.stream().anyMatch(line -> line.contains("ELC2232") && line.contains("QTY")), "Listing " + listing);
                    // LOAD was compiled when QTY was a whole number: a level check now.
                    ElclGameTests.expect(h, r.context(), "CALL PGM(DBLIB/LOAD)", "ELC0108");
                })
                .thenWaitUntil(() -> h.assertTrue(ElclVmGameTests.said(rig[0].system(), rig[0].user(), "Level check on file DBLIB/STOCK"),
                        "No level check"))
                .thenWaitUntil(() -> h.assertTrue(idle(rig[0]), "LOAD still running")));
    }

    // --- RUNQRY ---

    static void query(GameTestHelper helper) {
        rig(helper, (h, sequence, rig) -> sequence.thenExecute(() -> {
            Rig r = rig[0];
            stock(h, r);
            ElclGameTests.expect(h, r.context(), "RUNQRY FILE(DBLIB/STOCK) QRYSLT('QTY *LT 100 *AND HOT') SORT(QTY *DESCEND) OUTPUT(*PRINT)",
                    "ELC2238  Query selected 2 of 3 records.");
            List<String> report = spooled(h, r, "QPQUPRFIL").lines();
            int iron = -1, gold = -1;
            for (int i = 0; i < report.size(); i++) {
                iron = report.get(i).startsWith("IRON_INGOT") ? i : iron;
                gold = report.get(i).startsWith("GOLD_INGOT") ? i : gold;
            }
            h.assertTrue(iron > 0 && gold > iron && report.stream().noneMatch(line -> line.startsWith("COAL")), "Report " + report);
            h.assertTrue(report.stream().anyMatch(line -> line.startsWith("Item") && line.contains("Quantity")), "Headings " + report);
            ElclGameTests.expect(h, r.context(), "RUNQRY FILE(DBLIB/STOCK) QRYSLT('COST *GT 0.3') SORT(COST) OUTPUT(*OUTFILE) OUTFILE(DBLIB/DEAR)",
                    "ELC2238  Query selected 2 of 3 records.");
            h.assertTrue(column(records(h, r, "DBLIB", "DEAR"), 0).equals(List.of("IRON_INGOT", "GOLD_INGOT")), "DEAR " + records(h, r, "DBLIB", "DEAR"));
            // Replaced by another run.
            ElclGameTests.expect(h, r.context(), "RUNQRY DBLIB/STOCK QRYSLT('ITEM *EQ \"COAL\"') OUTPUT(*OUTFILE) OUTFILE(DBLIB/DEAR)", "ELC2238");
            h.assertTrue(column(records(h, r, "DBLIB", "DEAR"), 0).equals(List.of("COAL")), "DEAR replaced");
            ElclGameTests.expect(h, r.context(), "RUNQRY FILE(DBLIB/STOCK) QRYSLT('WEIGHT *GT 1')", "ELC2242");
            ElclGameTests.expect(h, r.context(), "RUNQRY FILE(DBLIB/STOCK) OUTPUT(*OUTFILE)", "ELC0102");
            ElclGameTests.expect(h, r.context(), "RUNQRY FILE(DBLIB/NOPE)", "ELC2205");
        }));
    }

    // --- ELSYS's system files ---

    static void systemFiles(GameTestHelper helper) {
        rig(helper, (h, sequence, rig) -> sequence
                .thenExecute(() -> {
                    Rig r = rig[0];
                    List<DbRecord> items = records(h, r, "ELSYS", "INVITEMS");
                    h.assertTrue(items.stream().anyMatch(record -> record.value(0).equals("COBBLESTONE") && record.value(2).equals(64L)
                            && record.value(3).equals(0L) && record.value(4).equals("minecraft") && !((String) record.value(1)).isBlank()),
                            "INVITEMS " + column(items, 0) + " " + column(items, 2));
                    List<DbRecord> devices = records(h, r, "ELSYS", "DEVICES");
                    h.assertTrue(devices.stream().anyMatch(record -> record.value(0).equals("ELDESK01") && record.value(1).equals("DESK")
                            && record.value(4).equals("*ONLINE")), "DEVICES " + column(devices, 0));
                    String job = OsCommands.interactiveJob(r.system(), r.user()).number();
                    h.assertTrue(column(records(h, r, "ELSYS", "JOBS"), 0).contains(job), "JOBS");
                    h.assertTrue(records(h, r, "ELSYS", "CRFHIST").isEmpty(), "CRFHIST");
                    // Without the Firewall's view permission: refused.
                    try {
                        ElclServices.files().records(r.system(), new FileService.Who(r.user(), false), "ELSYS", "INVITEMS");
                        h.fail("Read ELSYS/INVITEMS without view permission");
                    } catch (ElclException e) {
                        h.assertTrue(e.elclMessage().id().equals("ELC0401"), e.getMessage());
                    }
                    // A program reads it to its end.
                    program(r, "DBLIB", "COUNTS", "PGM", "DCLF FILE(ELSYS/INVITEMS)", "DCL VAR(&N) TYPE(*INT)",
                            "DOWHILE COND('1')", "RCVF", "MONMSG MSGID(ELC2201) EXEC(LEAVE)", "CHGVAR VAR(&N) VALUE(&N + &HOT + &COLD)", "ENDDO",
                            "SNDMSG MSG('Items' *BCAT %CHAR(&N)) TOUSR(*REQUESTER)", "ENDPGM");
                    RackGameTests.storage(h, ElclGameTests.BAY).insert(StorageKey.of(new ItemStack(Items.COBBLESTONE)), 6, false);
                    ElclGameTests.expect(h, r.context(), "CALL PGM(DBLIB/COUNTS)", "ELC0108");
                })
                // Live: the six more are there now.
                .thenWaitUntil(() -> h.assertTrue(ElclVmGameTests.said(rig[0].system(), rig[0].user(), "Items 80"), "COUNTS not done: "
                        + ElclServices.messages().messages(rig[0].system(), rig[0].user()).stream().map(m -> m.msgId() + " " + m.text()).toList()))
                .thenExecute(() -> {
                    List<DbRecord> items = records(h, rig[0], "ELSYS", "INVITEMS");
                    h.assertTrue(items.stream().anyMatch(record -> record.value(0).equals("COBBLESTONE") && record.value(2).equals(70L)), "Not live");
                }));
    }

    // --- CSV and CPYF ---

    static void copies(GameTestHelper helper) {
        rig(helper, (h, sequence, rig) -> sequence.thenExecute(() -> {
            Rig r = rig[0];
            TerminalContext c = r.context();
            stock(h, r);
            List<DbRecord> before = records(h, r, "DBLIB", "STOCK");
            // Folder sync is switched for this step only (other tests use it too).
            Boolean sync = FolderSync.override();
            try {
                FolderSync.setEnabled(false);
                ElclGameTests.expect(h, c, "CPYTOIMPF FILE(DBLIB/STOCK) TOSTMF('stock.csv')", "ELC2241");
                FolderSync.setEnabled(true);
                ElclGameTests.expect(h, c, "CPYTOIMPF FILE(DBLIB/STOCK) TOSTMF('stock.csv')", "ELC2236  3 records copied to stock.csv.");
                ElclGameTests.expect(h, c, "CPYTOIMPF FILE(DBLIB/STOCK) TOSTMF('../stock.csv')", "ELC2245");
                ElclGameTests.expect(h, c, "CLRPFM FILE(DBLIB/STOCK)", "ELC2234  File STOCK cleared: 3 records removed.");
                ElclGameTests.expect(h, c, "CPYFRMIMPF FROMSTMF('stock.csv') FILE(DBLIB/STOCK)", "ELC2237  3 records copied from stock.csv.");
                ElclGameTests.expect(h, c, "CPYFRMIMPF FROMSTMF('stock.csv') FILE(DBLIB/STOCK)", "ELC2203");
                ElclGameTests.expect(h, c, "CPYFRMIMPF FROMSTMF('stock.csv') FILE(DBLIB/STOCK) MBROPT(*REPLACE)", "ELC2237");
                ElclGameTests.expect(h, c, "CPYFRMIMPF FROMSTMF('nothing.csv') FILE(DBLIB/STOCK)", "ELC2240");
            } finally {
                FolderSync.setEnabled(sync);
            }
            List<DbRecord> after = records(h, r, "DBLIB", "STOCK");
            h.assertTrue(after.size() == before.size(), "Round trip lost records");
            for (int i = 0; i < after.size(); i++) {
                h.assertTrue(List.of(after.get(i).values()).equals(List.of(before.get(i).values())), "Record " + i + " changed in the round trip");
            }
            // CPYF: a new file like it; added twice is a duplicate key (and adds nothing); replaced.
            ElclGameTests.expect(h, c, "CPYF FROMFILE(DBLIB/STOCK) TOFILE(DBLIB/COPY) CRTFILE(*YES)", "ELC2235  3 records copied to file DBLIB/COPY.");
            ElclGameTests.expect(h, c, "CPYF FROMFILE(DBLIB/STOCK) TOFILE(DBLIB/COPY)", "ELC2203");
            h.assertTrue(records(h, r, "DBLIB", "COPY").size() == 3, "A failed CPYF added records");
            ElclGameTests.expect(h, c, "CPYF FROMFILE(DBLIB/STOCK) TOFILE(DBLIB/COPY) MBROPT(*REPLACE)", "ELC2235  3");
            // Into another format: fields by name.
            member(r, "DBLIB", "NAMES", "PF", List.of("A R NAMEREC", "A ITEM 10A", "A NOTE 5A"));
            ElclGameTests.expect(h, c, "CRTPF FILE(DBLIB/NAMES)", "ELC2230");
            ElclGameTests.expect(h, c, "CPYF FROMFILE(DBLIB/STOCK) TOFILE(DBLIB/NAMES)", "ELC2235  3");
            h.assertTrue(column(records(h, r, "DBLIB", "NAMES"), 0).equals(List.of("COAL", "GOLD_INGOT", "IRON_INGOT")), "Mapped by name");
            member(r, "DBLIB", "OTHER", "PF", List.of("A R OTHREC", "A ZZZ 5A"));
            ElclGameTests.expect(h, c, "CRTPF FILE(DBLIB/OTHER)", "ELC2230");
            ElclGameTests.expect(h, c, "CPYF FROMFILE(DBLIB/STOCK) TOFILE(DBLIB/OTHER)", "ELC2244");
        }));
    }

    // --- Save and reload; storage; the record limit; authority ---

    static void persistence(GameTestHelper helper) {
        rig(helper, (h, sequence, rig) -> sequence.thenExecute(() -> {
            Rig r = rig[0];
            long empty = StoredLibraryService.storageBytes(r.system());
            stock(h, r);
            program(r, "DBLIB", "FIRST", "PGM", "DCLF FILE(DBLIB/STOCK)", "RCVF", "SNDMSG MSG(&ITEM) TOUSR(*REQUESTER)", "ENDPGM");
            long withFile = StoredLibraryService.storageBytes(r.system());
            h.assertTrue(withFile > empty, "Records cost no storage");
            ElclStore.get(r.system().server()).reload(r.system().network());
            List<DbRecord> records = records(h, r, "DBLIB", "STOCK");
            h.assertTrue(column(records, 0).equals(List.of("COAL", "GOLD_INGOT", "IRON_INGOT")), "Records after reload " + column(records, 0));
            h.assertTrue(StoredLibraryService.storageBytes(r.system()) == withFile, "Storage after reload");
            try {
                h.assertTrue(ElclServices.libraries().program(r.system(), "DBLIB", "FIRST") != null, "FIRST doesn't compile after reload");
                h.assertTrue(!ElclServices.libraries().programFiles(r.system(), "DBLIB", "FIRST").isEmpty(), "FIRST's formats lost");
                // Unique still enforced after the reload.
                ElclServices.files().write(r.system(), r.who(), "DBLIB", "STOCK", new Object[] { "COAL", 1L, BigDecimal.ONE.setScale(2), false, "" });
                h.fail("Duplicate key written after reload");
            } catch (ElclException e) {
                h.assertTrue(e.elclMessage().id().equals("ELC2203"), e.getMessage());
            }
        }));
    }

    static void limits(GameTestHelper helper) {
        rig(helper, (h, sequence, rig) -> sequence.thenExecute(() -> {
            Rig r = rig[0];
            member(r, "DBLIB", "TINY", "PF", List.of("A R TINYREC", "A C 1A"));
            ElclGameTests.expect(h, r.context(), "CRTPF FILE(DBLIB/TINY)", "ELC2230");
            int limit = ElclConfig.maxRecordsPerFile();
            List<Object[]> rows = new ArrayList<>();
            for (int i = 0; i <= limit; i++) {
                rows.add(new Object[] { "x" });
            }
            FileService files = ElclServices.files();
            try {
                files.add(r.system(), r.who(), "DBLIB", "TINY", rows, false);
                h.fail("Added past the limit");
            } catch (ElclException e) {
                h.assertTrue(e.elclMessage().id().equals("ELC2208"), e.getMessage());
            }
            try {
                h.assertTrue(files.file(r.system(), "DBLIB", "TINY").records() == 0, "Some added past the limit");
                files.add(r.system(), r.who(), "DBLIB", "TINY", rows.subList(0, limit), false);
                files.write(r.system(), r.who(), "DBLIB", "TINY", new Object[] { "y" });
                h.fail("Written past the limit");
            } catch (ElclException e) {
                h.assertTrue(e.elclMessage().id().equals("ELC2208"), e.getMessage());
            }
            // Storage full: no more records.
            try {
                files.clear(r.system(), r.user(), "DBLIB", "TINY");
                RackGameTests.storage(h, ElclGameTests.BAY).insert(StorageKey.of(new ItemStack(Items.COBBLESTONE)), Long.MAX_VALUE / 4, false);
                files.add(r.system(), r.who(), "DBLIB", "TINY", rows.subList(0, 640), false);
                h.fail("Records written with storage full");
            } catch (ElclException e) {
                h.assertTrue(e.elclMessage().id().equals("ELC0207"), e.getMessage());
            }
        }));
    }

    static void authority(GameTestHelper helper) {
        rig(helper, (h, sequence, rig) -> sequence.thenExecute(() -> {
            Rig r = rig[0];
            ElclSystem system = r.system();
            FileService files = ElclServices.files();
            try {
                ElclServices.libraries().createLibrary(system, "OWNER", "OWNED", "*PROD", "");
                ElclServices.libraries().createMember(system, "OWNER", "OWNED", "STOCK", "", "PF");
                ElclServices.libraries().save(system, "OWNER", "OWNED", "STOCK", SourceLine.number(STOCK, 1));
                h.assertTrue(files.create(system, "OWNER", "OWNED", "STOCK", "OWNED", "STOCK", null).done(), "Owner's CRTPF");
                files.write(system, new FileService.Who("OWNER", true), "OWNED", "STOCK", new Object[] { "COAL", 1L, BigDecimal.ONE.setScale(2), false, "" });
            } catch (ElclException e) {
                h.fail("Owner: " + e.getMessage());
            }
            FileService.Who other = new FileService.Who("OTHER", true);
            try {
                h.assertTrue(files.records(system, other, "OWNED", "STOCK").size() == 1, "Another user can't read a *USE library's file");
            } catch (ElclException e) {
                h.fail("Read: " + e.getMessage());
            }
            for (String what : List.of("write", "clear", "delete", "create")) {
                try {
                    switch (what) {
                        case "write" -> files.write(system, other, "OWNED", "STOCK", new Object[] { "SAND", 1L, BigDecimal.ONE.setScale(2), false, "" });
                        case "clear" -> files.clear(system, "OTHER", "OWNED", "STOCK");
                        case "delete" -> files.delete(system, "OTHER", "OWNED", "STOCK");
                        default -> files.create(system, "OTHER", "OWNED", "MORE", "OWNED", "STOCK", null);
                    }
                    h.fail("Another user could " + what + " in a *USE library");
                } catch (ElclException e) {
                    h.assertTrue(e.elclMessage().id().equals("ELC0401"), what + ": " + e.getMessage());
                }
            }
            try {
                ElclServices.libraries().changeLibrary(system, "OWNER", "OWNED", null, "*CHANGE");
                files.write(system, other, "OWNED", "STOCK", new Object[] { "SAND", 1L, BigDecimal.ONE.setScale(2), false, "" });
            } catch (ElclException e) {
                h.fail("*CHANGE: " + e.getMessage());
            }
        }));
    }

    // --- SAVLIB / RSTLIB: files, their records and PF members go with the library ---

    static void saveRestore(GameTestHelper helper) {
        InterfaceGameTests.FakeDrive drive = new InterfaceGameTests.FakeDrive();
        InterfaceGameTests.FakeDiskette diskette = new InterfaceGameTests.FakeDiskette();
        drive.mounted.add(diskette);
        ElclSystem[] system = new ElclSystem[1];
        net.zagdrath.encodedlogistics.elcl.device.DeviceSources.Source<net.zagdrath.encodedlogistics.elcl.device.DisketteDevice> source = s -> s
                .equals(system[0]) ? List.of(drive) : List.of();
        rig(helper, (h, sequence, rig) -> sequence.thenExecute(() -> {
            Rig r = rig[0];
            system[0] = r.system();
            stock(h, r);
            program(r, "DBLIB", "READER", "PGM", "DCLF FILE(DBLIB/STOCK)", "RCVF", "ENDPGM");
            List<DbRecord> before = records(h, r, "DBLIB", "STOCK");
            net.zagdrath.encodedlogistics.elcl.device.Diskettes.register(source);
            try {
                ElclGameTests.expect(h, r.context(), "SAVLIB LIB(DBLIB) DEV(MIDRANGE01)", "ELC0220");
                var image = diskette.library("DBLIB");
                h.assertTrue(image != null && image.files().size() == 1 && image.members().stream().anyMatch(m -> m.type().equals("PF")),
                        "Image " + image);
                h.assertTrue(net.zagdrath.encodedlogistics.elcl.device.LibraryImage.load(image.save()).equals(image), "Image doesn't survive NBT");
                ElclGameTests.expect(h, r.context(), "DLTLIB LIB(DBLIB)", "ELC0211");
                ElclGameTests.expect(h, r.context(), "RSTLIB LIB(DBLIB) DEV(MIDRANGE01)", "ELC0221");
                List<DbRecord> after = records(h, r, "DBLIB", "STOCK");
                h.assertTrue(after.size() == before.size(), "Records not restored");
                for (int i = 0; i < after.size(); i++) {
                    h.assertTrue(List.of(after.get(i).values()).equals(List.of(before.get(i).values())), "Record " + i + " changed");
                }
                LibraryService.Member member = ElclServices.libraries().member(r.system(), "DBLIB", "STOCK");
                h.assertTrue(member.type().equals("PF") && member.program() && !member.changed(), "PF member " + member);
                h.assertTrue(!ElclServices.libraries().programFiles(r.system(), "DBLIB", "READER").isEmpty(), "READER's formats");
                // A too-small diskette: the records count against it.
                diskette.capacity = image.bytes() - 10;
                diskette.saved.clear();
                ElclGameTests.expect(h, r.context(), "SAVLIB LIB(DBLIB) DEV(MIDRANGE01)", "ELC1311");
            } catch (ElclException e) {
                h.fail(e.getMessage());
            } finally {
                net.zagdrath.encodedlogistics.elcl.device.Diskettes.unregister(source);
            }
        }));
    }

    // --- A Display Panel's table ---

    static void displayTable(GameTestHelper helper) {
        net.minecraft.core.BlockPos master = new net.minecraft.core.BlockPos(3, 1, 3);
        for (int x = 3; x <= 5; x++) {
            for (int y = 1; y <= 2; y++) {
                DisplayGameTests.panel(helper, new net.minecraft.core.BlockPos(x, y, 3), net.minecraft.core.Direction.SOUTH);
            }
        }
        rig(helper, (h, sequence, rig) -> sequence.thenIdle(1).thenExecute(() -> {
            Rig r = rig[0];
            TerminalContext c = r.context();
            stock(h, r);
            var display = DisplayGameTests.at(h, master);
            ElclGameTests.expect(h, c, "SNDDSPWDG DEV(DSP01) RGN(A) WDG(*TABLE)", "ELC1316");
            ElclGameTests.expect(h, c, "SNDDSPWDG DEV(DSP01) RGN(A) WDG(*CLOCK) FILE(DBLIB/STOCK)", "ELC1316");
            ElclGameTests.expect(h, c, "SNDDSPWDG DEV(DSP01) RGN(A) WDG(*TABLE) FILE(DBLIB/STOCK) QRYSLT('WEIGHT *GT 1')", "ELC2242");
            ElclGameTests.expect(h, c, "SNDDSPWDG DEV(DSP01) RGN(A) WDG(*TABLE) FILE(DBLIB/NOPE)", "ELC2205");
            String message = run(c, "SNDDSPWDG DEV(DSP01) RGN(A) WDG(*TABLE) FILE(DBLIB/STOCK) QRYSLT('QTY *LT 100') SORT(QTY *DESCEND)");
            h.assertTrue(message.isEmpty(), "SNDDSPWDG *TABLE: " + message);
            var widget = display.displayContent().region("A", display.canvasWidth(), display.canvasHeight()).widget();
            h.assertTrue(widget.kind().equals("*TABLE") && widget.file().equals("DBLIB/STOCK") && widget.select().equals("QTY *LT 100")
                    && widget.sort().equals("QTY *DESCEND"), "Widget " + widget);
            var frame = display.liveFrames().getFirst();
            h.assertTrue(frame.label().equals("DBLIB/STOCK") && frame.value().equals("2") && frame.rows().size() == 3, "Frame " + frame);
            h.assertTrue(frame.rows().get(0).startsWith("Item\tQuantity") && frame.rows().get(1).startsWith("IRON_INGOT\t40\t1.50"), "Rows " + frame.rows());
            h.assertTrue(frame.numbers().equals(List.of(0L, 1L, 1L, 0L, 0L)), "Numeric columns " + frame.numbers());
            // The file gone: the table says so.
            ElclGameTests.expect(h, c, "DLTF FILE(DBLIB/STOCK)", "ELC2233");
            var gone = display.liveFrames().getFirst();
            h.assertTrue(gone.value().equals("!") && gone.rows().getFirst().contains("STOCK"), "After DLTF " + gone);
        }));
    }

    // A command line's message ("" for none).
    private static String run(TerminalContext context, String line) {
        var out = net.zagdrath.encodedlogistics.terminal.TerminalCommands.execute(context, line);
        return out.message() != null ? out.message().getString() : "";
    }

    // --- The screens' queries (FileQueries) ---

    private static List<String> cells(net.zagdrath.encodedlogistics.terminal.TerminalOutput out, int line) {
        List<String> cells = new ArrayList<>();
        out.lines().get(line).cells().forEach(cell -> cells.add(cell.text().getString()));
        return cells;
    }

    private static net.zagdrath.encodedlogistics.terminal.TerminalOutput screen(Rig rig, String text) {
        return net.zagdrath.encodedlogistics.elcl.screen.ScreenQueries.handle(rig.context(), text);
    }

    private static String message(net.zagdrath.encodedlogistics.terminal.TerminalOutput out) {
        return out.message() != null ? out.message().getString() : "";
    }

    static void screens(GameTestHelper helper) {
        rig(helper, (h, sequence, rig) -> sequence.thenExecute(() -> {
            Rig r = rig[0];
            stock(h, r);
            var files = screen(r, "files DBLIB");
            h.assertTrue(cells(files, 0).subList(0, 4).equals(List.of("STOCK", "PF", "3", "Stock record")), "files " + cells(files, 0));
            var desc = screen(r, "filedesc DBLIB/STOCK");
            h.assertTrue(desc.lines().size() == 6 && cells(desc, 0).get(12).equals("ITEM") && cells(desc, 1).subList(0, 5).equals(List.of("ITEM", "A", "20", "0",
                    "1")), "filedesc " + cells(desc, 0));
            // Display Physical File Member: headings, then a line a record in key order; by key.
            var data = screen(r, "filedata DBLIB/STOCK 0");
            h.assertTrue(cells(data, 0).equals(List.of("DBLIB/STOCK", "3", "0", "1", "1", "3")), "filedata " + cells(data, 0));
            h.assertTrue(cells(data, 1).getFirst().startsWith("Item") && cells(data, 2).getFirst().startsWith("COAL") && data.lines().size() == 5,
                    "filedata lines");
            h.assertTrue(cells(screen(r, "filedata DBLIB/STOCK *KEY H"), 0).get(2).equals("2"), "Position to key");
            var query = screen(r, "runqry DBLIB/STOCK 0\tQTY *LT 100\tQTY *DESCEND");
            h.assertTrue(cells(query, 0).get(1).equals("2") && cells(query, 0).get(5).equals("3") && cells(query, 2).getFirst().startsWith("IRON_INGOT"),
                    "runqry " + cells(query, 0));
            // Update Data: records in turn, changed, added, deleted; a bad value refused with its field.
            var first = screen(r, "record DBLIB/STOCK *FIRST");
            h.assertTrue(cells(first, 0).subList(1, 3).equals(List.of("1", "3")) && cells(first, 1).getFirst().equals("COAL"), "*FIRST");
            String coal = cells(first, 0).getFirst();
            var next = screen(r, "record DBLIB/STOCK *NEXT " + coal);
            h.assertTrue(cells(next, 1).getFirst().equals("GOLD_INGOT"), "*NEXT");
            String gold = cells(next, 0).getFirst();
            h.assertTrue(cells(screen(r, "record DBLIB/STOCK *KEY IRON"), 1).getFirst().equals("IRON_INGOT"), "*KEY");
            h.assertTrue(message(screen(r, "record DBLIB/STOCK *KEY ZZZ")).startsWith("ELC2202"), "*KEY not found");
            var bad = screen(r, "putrecord DBLIB/STOCK " + gold + "\tGOLD_INGOT\tmany\t9.99\t1\t");
            h.assertTrue(message(bad).equals("ELC2209: Value 'many' not valid for field QTY."), "putrecord bad " + message(bad));
            var changed = screen(r, "putrecord DBLIB/STOCK " + gold + "\tGOLD_INGOT\t4\t9.99\t1\t");
            h.assertTrue(cells(changed, 1).get(1).equals("4") && !cells(changed, 1).get(4).isBlank(), "putrecord " + cells(changed, 1));
            var added = screen(r, "addrecord DBLIB/STOCK\tSAND\t1\t0\t0\t");
            h.assertTrue(cells(added, 0).get(2).equals("4") && cells(added, 1).getFirst().equals("SAND"), "addrecord");
            h.assertTrue(message(screen(r, "addrecord DBLIB/STOCK\tSAND\t1\t0\t0\t")).startsWith("ELC2203"), "Duplicate added");
            var deleted = screen(r, "delrecord DBLIB/STOCK " + gold);
            h.assertTrue(cells(deleted, 1).getFirst().equals("IRON_INGOT") && cells(deleted, 0).get(2).equals("3"), "delrecord");
            h.assertTrue(message(screen(r, "putrecord ELSYS/INVITEMS 0\tX\tX\t0\t0\tX")).startsWith("ELC0205"), "Changed ELSYS");
            // The editor's DCLF check, and the prompter's list.
            var format = screen(r, "fileformat DBLIB STOCK");
            h.assertTrue(cells(format, 0).getFirst().equals("DBLIB/STOCK")
                    && net.zagdrath.encodedlogistics.elcl.db.RecordFormat.load(cells(format, 0).get(1)) != null, "fileformat");
            h.assertTrue(message(screen(r, "fileformat DBLIB NOPE")).startsWith("ELC2205"), "fileformat missing");
            var values = screen(r, "values files DBLIB");
            h.assertTrue(cells(values, 0).getFirst().equals("DBLIB/STOCK"), "values files");
            var elsys = screen(r, "values files ELSYS");
            h.assertTrue(elsys.lines().size() == 4, "ELSYS files listed " + elsys.lines().size());
            h.assertTrue(!cells(screen(r, "files ELSYS"), 1).get(2).equals("0"), "INVITEMS' live count");
        }));
    }

    // --- The shipped example: ITEMSETUP, then LOGITEMS ---

    static void example(GameTestHelper helper) {
        rig(helper, (h, sequence, rig) -> sequence
                .thenExecute(() -> ElclGameTests.expect(h, rig[0].context(), "CALL ELSYS/ITEMSETUP", "ELC0108"))
                .thenWaitUntil(() -> h.assertTrue(idle(rig[0]), "ITEMSETUP still running"))
                .thenExecute(() -> {
                    Rig r = rig[0];
                    try {
                        h.assertTrue(ElclServices.files().file(r.system(), "ELGPL", "ITEMHIST").records() == 0, "ITEMHIST");
                        h.assertTrue(ElclServices.libraries().program(r.system(), "ELGPL", "LOGITEMS") != null, "LOGITEMS");
                    } catch (ElclException e) {
                        h.fail("ITEMSETUP: " + e.getMessage());
                    }
                    h.assertTrue(ElclServices.jobs().scheduleEntries(r.system()).stream().anyMatch(entry -> entry.job().equals("LOGITEMS")), "No schedule");
                    ElclGameTests.expect(h, r.context(), "CALL ELGPL/LOGITEMS", "ELC0108");
                })
                .thenWaitUntil(() -> h.assertTrue(idle(rig[0]), "LOGITEMS still running"))
                .thenExecute(() -> {
                    Rig r = rig[0];
                    List<DbRecord> logged = records(h, r, "ELGPL", "ITEMHIST");
                    h.assertTrue(logged.size() == 2, "Logged " + logged.size());
                    h.assertTrue(column(logged, 1).equals(List.of("COBBLESTONE", "IRON_INGOT")) && column(logged, 2).equals(List.of(64L, 10L)),
                            "ITEMHIST " + column(logged, 1) + " " + column(logged, 2));
                    h.assertTrue(logged.getFirst().value(0).equals(logged.getLast().value(0)), "Not one LOGGED time a run");
                    List<String> report = spooled(h, r, "QPQUPRFIL").lines();
                    h.assertTrue(report.stream().anyMatch(line -> line.contains("IRON_INGOT")) && report.stream().noneMatch(line -> line.contains("COBBLESTONE")),
                            "Report " + report);
                    ElclGameTests.expect(h, r.context(), "RMVJOBSCDE JOB(LOGITEMS)", "ELC0313");
                    // Run again: a second time's records alongside the first's.
                    ElclGameTests.expect(h, r.context(), "CALL ELSYS/ITEMSETUP", "ELC0108");
                })
                .thenWaitUntil(() -> h.assertTrue(idle(rig[0]), "ITEMSETUP again still running"))
                .thenExecute(() -> ElclGameTests.expect(h, rig[0].context(), "RMVJOBSCDE JOB(LOGITEMS)", "ELC0313")));
    }
}
