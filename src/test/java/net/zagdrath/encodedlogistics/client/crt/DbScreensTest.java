/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import static net.zagdrath.encodedlogistics.client.crt.LayoutTest.answer;
import static net.zagdrath.encodedlogistics.client.crt.LayoutTest.cells;
import static net.zagdrath.encodedlogistics.client.crt.LayoutTest.compare;
import static net.zagdrath.encodedlogistics.client.crt.LayoutTest.hostOf;
import static net.zagdrath.encodedlogistics.client.crt.LayoutTest.option;
import static net.zagdrath.encodedlogistics.client.crt.LayoutTest.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// The database screens against their layouts (src/test/resources/layouts/17-21) with sample data - Work with Files,
// Display Physical File Member and RUNQRY's Display Report, Display File Description, Update Data, and Work with
// Members' file rows - and what their keys and options send.
class DbScreensTest {
    @BeforeAll
    static void language() throws IOException {
        LayoutTest.language();
    }

    private static List<String> sent(CrtTerminal terminal) {
        return ((LayoutTest.Host) hostOf(terminal)).sent;
    }

    private static String last(CrtTerminal terminal) {
        List<String> sent = sent(terminal);
        return sent.getLast();
    }

    static CrtTerminal wrkf() {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.runCommand("WRKF LIB(ZAGLIB)");
        answer(terminal, "files", cells("ITEMHIST", "PF", "1240", "Item count history", "117", "Day 2  07:13", "ZAGLIB/ITEMHIST", "", "0"),
                cells("STOCK", "PF", "3", "Stock levels", "3", "Day 2  07:20", "ZAGLIB/STOCK", "*", "0"),
                cells("LOWSTOCK", "PF", "2", "Query of ZAGLIB/STOCK", "2", "Day 2  07:31", "*NONE", "", "0"));
        option(terminal, 8, "5");
        return terminal;
    }

    // STOCK's headings and records as the server lays them out.
    private static final String HEADING = "Item                   Quantity        COST  HOT  SEEN";
    private static final TerminalLine[] STOCK = {
            cells("ZAGLIB/STOCK", "3", "0", "1", "1", "3"), cells(HEADING),
            cells("COAL                        900        0.25  0    00002 07:00:00"),
            cells("GOLD_INGOT                    3        9.99  1    00003 07:00:00"),
            cells("IRON_INGOT                   40        1.50  1    00002 07:00:00") };

    static CrtTerminal dsppfm() {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.runCommand("DSPPFM FILE(ZAGLIB/STOCK)");
        answer(terminal, "filedata", STOCK);
        return terminal;
    }

    static CrtTerminal runqry() {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.runCommand("RUNQRY FILE(ZAGLIB/STOCK) QRYSLT('QTY *LT 100') SORT(QTY *DESCEND)");
        answer(terminal, "runqry", cells("ZAGLIB/STOCK", "2", "0", "1", "1", "3"), cells(HEADING),
                cells("IRON_INGOT                   40        1.50  1    00002 07:00:00"),
                cells("GOLD_INGOT                    3        9.99  1    00003 07:00:00"));
        return terminal;
    }

    private static final TerminalLine[] DESCRIPTION = {
            cells("ZAGLIB", "STOCK", "PF", "Stock levels", "STOCKREC", "Stock record", "ZAGLIB/STOCK", "Day 2  07:20", "3", "10000", "51", "3", "ITEM", "1",
                    "1", "0"),
            cells("ITEM", "A", "20", "0", "1", "", "Item"), cells("QTY", "S", "9", "0", "", "", "Quantity"), cells("COST", "P", "7", "2", "", "Unit cost"),
            cells("HOT", "L", "1", "0", "", ""), cells("SEEN", "T", "14", "0", "", "Last counted") };

    static CrtTerminal dspfd() {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.runCommand("DSPFD FILE(ZAGLIB/STOCK)");
        answer(terminal, "filedesc", DESCRIPTION);
        return terminal;
    }

    static CrtTerminal upddta() {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.runCommand("UPDDTA FILE(ZAGLIB/STOCK)");
        answer(terminal, "filedesc", DESCRIPTION);
        answer(terminal, "record", cells("2", "2", "3"), cells("GOLD_INGOT", "3", "9.99", "1", "00003 07:00:00"));
        return terminal;
    }

    static CrtTerminal wrkmbr() {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.push(new WrkMbrPanel(terminal, "ZAGLIB"));
        answer(terminal, "members", cells("NOCWALL", "ELCLP", "", "Refresh the NOC status display"), cells("STOCK", "PF", "*", "Stock levels"));
        answer(terminal, "files", cells("STOCK", "PF", "3", "Stock levels", "3", "Day 2  07:20", "ZAGLIB/STOCK", "*", "0"));
        option(terminal, 8, "14");
        option(terminal, 9, "5");
        return terminal;
    }

    @Test
    void workWithFiles() throws IOException {
        CrtTerminal terminal = wrkf();
        compare("17_wrkf", terminal.compose(), 0, 2, 3, 4, 6, 7, 8, 9, 11, 20, 21, 23);
        terminal.submit();
        assertInstanceOf(DspPfmPanel.class, terminal.current());
        assertEquals(TerminalService.SCREEN + " filedata ZAGLIB/STOCK 0", last(terminal));
        terminal.functionKey(12);
        option(terminal, 7, "8");
        option(terminal, 9, "2");
        terminal.submit();
        assertInstanceOf(DspFdPanel.class, terminal.current());
        terminal.functionKey(12);
        assertInstanceOf(UpdDtaPanel.class, terminal.current());
        terminal.functionKey(12);
        // F6: CRTPF with the library in its File field.
        terminal.functionKey(6);
        assertInstanceOf(PrompterPanel.class, terminal.current());
        assertEquals("ZAGLIB/", terminal.focused().value);
    }

    @Test
    void displayPhysicalFileMember() throws IOException {
        CrtTerminal terminal = dsppfm();
        compare("18_dsppfm", terminal.compose(), 0, 2, 3, 4, 5, 6, 7, 8, 20, 23);
        // Position to a key: the server finds where.
        terminal.current().fields.getFirst().set("GOLD");
        terminal.submit();
        assertEquals(TerminalService.SCREEN + " filedata ZAGLIB/STOCK *KEY GOLD", last(terminal));
        // F20 shifts 40 columns, no further than the widest line allows: these fit.
        terminal.functionKey(20);
        assertTrue(row(terminal.compose(), 6).startsWith("COAL"), row(terminal.compose(), 6));
    }

    @Test
    void displayReport() throws IOException {
        CrtTerminal terminal = runqry();
        assertTrue(sent(terminal).contains(TerminalService.SCREEN + " runqry ZAGLIB/STOCK 0\tQTY *LT 100\tQTY *DESCEND"), "Sent " + sent(terminal));
        compare("19_runqry", terminal.compose(), 0, 2, 3, 5, 6, 7, 20, 23);
        // Other outputs run on the server.
        terminal.runCommand("RUNQRY FILE(ZAGLIB/STOCK) OUTPUT(*PRINT)");
        assertEquals(TerminalService.COMMAND + " RUNQRY FILE(ZAGLIB/STOCK) OUTPUT(*PRINT)", last(terminal));
    }

    @Test
    void displayFileDescription() throws IOException {
        compare("20_dspfd", dspfd().compose(), 0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 20, 23);
    }

    @Test
    void updateData() throws IOException {
        CrtTerminal terminal = upddta();
        compare("21_upddta", terminal.compose(), 0, 2, 3, 4, 6, 8, 9, 10, 11, 12, 23);
        // A value that isn't its type: refused here, the cursor on it.
        CrtField qty = terminal.current().fields.get(2);
        qty.set("lots");
        terminal.submit();
        assertTrue(last(terminal).endsWith("*FIRST"), "Sent " + last(terminal));
        assertEquals(qty, terminal.focused());
        // Changed: putrecord with every value.
        qty.set("4");
        terminal.submit();
        assertEquals(TerminalService.SCREEN + " putrecord ZAGLIB/STOCK 2\tGOLD_INGOT\t4\t9.99\t1\t00003 07:00:00", last(terminal));
        answer(terminal, "putrecord", cells("2", "2", "3"), cells("GOLD_INGOT", "4", "9.99", "1", "00003 07:00:00"));
        // Unchanged: on to the next record.
        terminal.submit();
        assertEquals(TerminalService.SCREEN + " record ZAGLIB/STOCK *NEXT 2", last(terminal));
        terminal.functionKey(7);
        assertEquals(TerminalService.SCREEN + " record ZAGLIB/STOCK *PREV 2", last(terminal));
        // Entry mode: an added record clears the fields.
        terminal.functionKey(6);
        assertEquals(" Type values for a new record, press Enter.", row(terminal.compose(), 6).stripTrailing());
        terminal.current().fields.get(0).set("SAND");
        terminal.submit();
        assertEquals(TerminalService.SCREEN + " addrecord ZAGLIB/STOCK\tSAND\t\t\t\t", last(terminal));
        answer(terminal, "addrecord", cells("4", "3", "4"), cells("SAND", "0", "0.00", "0", "00005 07:00:00"));
        assertEquals("", terminal.current().fields.get(0).value);
        // F11 in change mode asks, then deletes.
        terminal.functionKey(10);
        answer(terminal, "record", cells("2", "2", "4"), cells("GOLD_INGOT", "4", "9.99", "1", "00003 07:00:00"));
        terminal.functionKey(11);
        terminal.submit();
        assertEquals(TerminalService.SCREEN + " delrecord ZAGLIB/STOCK 2", last(terminal));
        // ELSYS: refused, no screen.
        CrtPanel before = terminal.current();
        terminal.runCommand("UPDDTA FILE(ELSYS/INVITEMS)");
        assertEquals(before, terminal.current());
        assertTrue(row(terminal.compose(), 22).startsWith(" ELC0205"), row(terminal.compose(), 22));
    }

    // The editor's text field holding a line.
    private static CrtField line(CrtTerminal terminal, String text) {
        for (CrtField field : terminal.current().fields) {
            if (field.value.equals(text)) {
                return field;
            }
        }
        throw new AssertionError("No field holds " + text);
    }

    @Test
    void theEditorChecksADefinition() {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.runCommand("EDTMBR MBR(ZAGLIB/STOCK)");
        answer(terminal, "source", cells("2", "0", "0", "PF"), cells("100", "1", "A R REC"), cells("200", "1", "A NAME 10A"));
        assertTrue(row(terminal.compose(), 1).contains("ZAGLIB/STOCK   PF"), row(terminal.compose(), 1));
        line(terminal, "A NAME 10A").set("A NAME 10X");
        terminal.submit();
        assertTrue(row(terminal.compose(), 22).startsWith(" ELC2227"), row(terminal.compose(), 22));
        // No prompter for a definition line.
        terminal.focus(line(terminal, "A NAME 10X"));
        terminal.functionKey(4);
        assertTrue(row(terminal.compose(), 22).contains("no prompter"), row(terminal.compose(), 22));
    }

    @Test
    void theEditorChecksDclfAgainstTheFilesFormat() {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.runCommand("EDTMBR MBR(ZAGLIB/READER)");
        answer(terminal, "source", cells("4", "0", "0", "ELCLP"), cells("100", "1", "PGM"), cells("200", "1", "DCLF FILE(ZAGLIB/STOCK)"),
                cells("300", "1", "RCVF"), cells("400", "1", "ENDPGM"));
        assertTrue(sent(terminal).contains(TerminalService.SCREEN + " fileformat ZAGLIB STOCK"), "Sent " + sent(terminal));
        // Before the format comes: the file's variables aren't checked.
        line(terminal, "RCVF").set("CHGVAR VAR(&QTY) VALUE('many')");
        terminal.submit();
        assertTrue(row(terminal.compose(), 22).isBlank(), row(terminal.compose(), 22));
        net.zagdrath.encodedlogistics.elcl.db.RecordFormat stock = net.zagdrath.encodedlogistics.elcl.db.Dds.compile(List.of("A R STOCKREC", "A ITEM 20A",
                "A QTY 9S 0", "A K ITEM")).format();
        answer(terminal, "fileformat", cells("ZAGLIB/STOCK", stock.save()));
        // Now they are: &QTY is a whole number.
        CrtField changed = line(terminal, "CHGVAR VAR(&QTY) VALUE('many')");
        changed.set("CHGVAR VAR(&QTY) VALUE('lots')");
        terminal.submit();
        assertTrue(row(terminal.compose(), 22).startsWith(" ELC0003"), row(terminal.compose(), 22));
    }

    @Test
    void workWithMembersListsFiles() throws IOException {
        CrtTerminal terminal = wrkmbr();
        compare("22_wrkmbr_files", terminal.compose(), 0, 6, 7, 8, 9, 20, 23);
        terminal.submit();
        // 14 on a PF member whose file is there: CHGPF; 5 on the file: Display Physical File Member.
        assertTrue(sent(terminal).contains(TerminalService.COMMAND + " CHGPF FILE(ZAGLIB/STOCK) SRCMBR(ZAGLIB/STOCK)"), "Sent " + sent(terminal));
    }
}
