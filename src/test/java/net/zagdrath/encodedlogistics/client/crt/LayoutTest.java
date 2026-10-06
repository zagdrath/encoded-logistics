/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import net.zagdrath.encodedlogistics.menu.TerminalDeskMenu;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The screens against their layouts (screens handoff E): each composed into the grid - in the real lang file's words,
// with the layouts' sample data - and its fixed rows (headings, legends, labels, keys) compared with
// src/test/resources/layouts/*.txt. Row 1 (the clock) and the message line's illustrative text are left out, as are the
// rows the handoff keeps as they were (noted per screen). No '?' anywhere: every glyph is on the font sheet.
class LayoutTest {
    static final class Host implements CrtTerminal.Host {
        final List<String> sent = new ArrayList<>();

        @Override
        public void send(int kind, String text) {
            sent.add(kind + " " + text);
        }

        @Override
        public void close() {}

        @Override
        public void phosphorChanged() {}

        @Override
        public String clock() {
            return "Day 2  07:14:22";
        }

        @Override
        public @Nullable TerminalDeskMenu menu() {
            return null;
        }
    }

    @BeforeAll
    static void language() throws IOException {
        Map<String, String> words = new HashMap<>();
        try (InputStream in = LayoutTest.class.getResourceAsStream("/assets/encodedlogistics/lang/en_us.json");
                Reader reader = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            json.entrySet().forEach(entry -> words.put(entry.getKey(), entry.getValue().getAsString()));
        }
        Language.inject(new Language() {
            @Override
            public String getOrDefault(String key, String fallback) {
                return words.getOrDefault(key, fallback);
            }

            @Override
            public boolean has(String key) {
                return words.containsKey(key);
            }

            @Override
            public boolean isDefaultRightToLeft() {
                return false;
            }

            @Override
            public FormattedCharSequence getVisualOrder(FormattedText text) {
                return FormattedCharSequence.EMPTY;
            }
        });
    }

    static CrtTerminal terminal() {
        CrtTerminal terminal = new CrtTerminal(new Host());
        terminal.network = "ELNET01";
        terminal.user = "ZAGDRATH";
        terminal.start();
        // No server: no answer to wait for.
        terminal.connecting = false;
        return terminal;
    }

    // A layout's rows: the text after "NN | ", padded to 80.
    static List<String> layout(String name) throws IOException {
        List<String> rows = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of("src/test/resources/layouts/" + name + ".txt"), StandardCharsets.UTF_8)) {
            if (line.length() >= 4 && line.charAt(3) == '|') {
                String text = line.length() > 5 ? line.substring(5) : "";
                rows.add(CrtGrid.pad(text, 80));
            }
        }
        return rows;
    }

    static String row(CrtGrid grid, int row) {
        return new String(grid.chars[row]);
    }

    static void compare(String name, CrtGrid grid, int... rows) throws IOException {
        List<String> expected = layout(name);
        for (int row : rows) {
            assertEquals(expected.get(row).stripTrailing(), row(grid, row).stripTrailing(), name + " row " + row);
        }
        for (int row = 0; row < CrtGrid.ROWS; row++) {
            assertFalse(row(grid, row).contains("?") && !expected.get(row).contains("?"), name + " row " + row + " has '?': " + row(grid, row));
        }
    }

    static TerminalLine cells(String... cells) {
        TerminalLine.Builder builder = TerminalLine.builder();
        for (String cell : cells) {
            builder.left(cell, 0);
        }
        return builder.build();
    }

    static void answer(CrtTerminal terminal, String topic, TerminalLine... lines) {
        terminal.receive(new CrtResponsePayload(0, TerminalService.SCREEN, topic, List.of(lines), Optional.empty(), -1));
    }

    static void option(CrtTerminal terminal, int row, String option) {
        for (CrtField field : terminal.current().fields) {
            if (field.row == row && field.col == 0) {
                field.set(option);
            }
        }
    }

    @Test
    void mainMenu() throws IOException {
        CrtTerminal terminal = terminal();
        terminal.unread = 1;
        CrtGrid grid = terminal.compose();
        compare("02_main_menu", grid, 0, 3, 6, 7, 8, 9, 10, 11, 12, 13, 15, 18, 20, 21, 22, 23);
    }

    @Test
    void signOn() throws IOException {
        CrtTerminal terminal = terminal();
        terminal.push(new SignOnPanel(terminal));
        CrtGrid grid = terminal.compose();
        // Rows 5, 8 and 9 are the existing Sign On's (kept); its new fields line up with them (column 0). The banner is one
        // dim line at the bottom right of row 20.
        compare("01_signon", grid, 0, 3, 14, 15, 16, 17, 18, 20, 23);
        assertEquals("MAIN", terminal.current().fields.get(2).value);
        assertEquals("*USRPRF", terminal.current().fields.get(3).value);
    }

    @Test
    void workWithLibraries() throws IOException {
        CrtTerminal terminal = terminal();
        terminal.push(new WrkLibPanel(terminal));
        answer(terminal, "libraries", cells("ELGPL", "*PROD", "General purpose library"), cells("ELSYS", "*SYS", "System library - samples (read-only)"),
                cells("ZAGLIB", "*PROD", "Zagdrath automation scripts"), cells("TESTLIB", "*TEST", "Scratch"));
        option(terminal, 9, "12");
        CrtGrid grid = terminal.compose();
        compare("03_wrklib", grid, 0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 19, 20, 21, 23);
    }

    // Every screen's function-key line fits on row 23 from column 1 (79 columns): one longer is cut off ("F12=Cance").
    @Test
    void keyLinesFit() throws IOException {
        try (InputStream in = LayoutTest.class.getResourceAsStream("/assets/encodedlogistics/lang/en_us.json");
                Reader reader = new java.io.InputStreamReader(in, StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            json.entrySet().stream().filter(entry -> entry.getKey().startsWith("crt.encodedlogistics.fkeys."))
                    .forEach(entry -> assertTrue(entry.getValue().getAsString().length() <= CrtGrid.COLS - 1, entry.getKey() + " is too long"));
        }
    }

    @Test
    void workWithMembers() throws IOException {
        CrtTerminal terminal = terminal();
        terminal.push(new WrkMbrPanel(terminal, "ZAGLIB"));
        answer(terminal, "members", cells("ARCHIVE", "ELCLP", "", "Move old items to tape"), cells("NOCWALL", "ELCLP", "*", "Refresh the NOC status display"),
                cells("RESTOCK", "ELCLP", "", "Keep logic dies stocked"), cells("SORTDEMO", "ELCLP", "", "List and sort demo"),
                cells("UPSALERT", "ELCLP", "*", "Alert operators on UPS power"));
        option(terminal, 8, "14");
        option(terminal, 9, "2");
        CrtGrid grid = terminal.compose();
        compare("04_wrkmbr", grid, 0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 20, 21, 23);
    }

    @Test
    void prompter() throws IOException {
        CrtTerminal terminal = terminal();
        assert terminal.prompter("STRCRAFT QTY(64)", false, command -> {});
        terminal.functionKey(10);
        // The item typed into its field.
        terminal.current().fields.getFirst().set("logic_die");
        CrtGrid grid = terminal.compose();
        compare("06_prompter", grid, 0, 2, 3, 5, 6, 7, 8, 9, 10, 11, 12, 20, 23);
        // The item as typed (the layout's lower case).
        assertEquals(" Item  . . . . . . . .  ITEM       logic_die                 Name, F4 for list", row(grid, 4).stripTrailing());
        PrompterPanel prompter = (PrompterPanel) terminal.current();
        assertEquals("STRCRAFT ITEM(LOGIC_DIE) QTY(64)", prompter.command());
    }

    @Test
    void helpWindow() throws IOException {
        CrtTerminal terminal = terminal();
        terminal.push(new WrkLibPanel(terminal));
        answer(terminal, "libraries", cells("ELGPL", "*PROD", "General purpose library"), cells("ELSYS", "*SYS", "System library - samples (read-only)"),
                cells("ZAGLIB", "*PROD", "Zagdrath automation scripts"), cells("TESTLIB", "*TEST", "Scratch"));
        option(terminal, 9, "12");
        terminal.focus(terminal.command);
        terminal.functionKey(1);
        CrtGrid grid = terminal.compose();
        compare("15_help_popup", grid, 0, 5, 6, 7, 8, 9, 17, 23);
        assertEquals(" F3=Exit help   F12=Cancel", row(grid, 16).substring(13, 67).stripTrailing().replace("│", "|").replace("|", ""));
        // F3 closes the help only.
        terminal.functionKey(3);
        assertEquals("WRKLIB", terminal.current().id());
        assertEquals(null, terminal.window());
    }

    @Test
    void commandEntry() throws IOException {
        CrtTerminal terminal = terminal();
        terminal.functionKey(9);
        terminal.unread = 1;
        CrtGrid grid = terminal.compose();
        // The history rows are the existing Command Entry's (kept).
        compare("16_cmdent", grid, 0, 20, 21, 22, 23);
    }

    @Test
    void workWithOutput() throws IOException {
        CrtTerminal terminal = terminal();
        terminal.runCommand("WRKSPLF");
        answer(terminal, "spooled", cells("3", "NOCWALL", "000118/NOCWALL", "ZAGDRATH", "1", "*RDY", "Day 2  07:13"),
                cells("2", "QPJOBLOG", "000123/RESTOCK", "ZAGDRATH", "2", "*RDY", "Day 2  07:10"),
                cells("1", "INVLIST", "000099/QPRTRPT", "OPERATOR", "6", "*PRT", "Day 1  18:44"));
        CrtGrid grid = terminal.compose();
        compare("13_wrksplf", grid, 0, 2, 3, 4, 5, 6, 7, 8, 9, 10, 20, 21, 23);
    }

    @Test
    void displaySpooledFile() throws IOException {
        CrtTerminal terminal = terminal();
        terminal.push(new DspSplfPanel(terminal, "NOCWALL", List.of("ELCL Compile Listing   ZAGLIB/NOCWALL   Day 2 07:13   ELNET01")));
        CrtGrid grid = terminal.compose();
        // Rows 3 and 4 hold fields (the layout draws them as underscores); the listing below is the real one's.
        compare("07_compile_listing", grid, 0, 2, 5, 6, 23);
    }

    @Test
    void workWithActiveJobs() throws IOException {
        CrtTerminal terminal = terminal();
        terminal.runCommand("WRKACTJOB");
        answer(terminal, "jobs", cells("37", "5", "2", "2"), cells("000101", "QINTER", "ZAGDRATH", "INT", "ELDESK01", "*ACTIVE", "12", "5", "1"),
                cells("000123", "RESTOCK", "ZAGDRATH", "BCH", "MIDRANGE01", "*ACTIVE", "31", "5", "1"),
                cells("000118", "NOCWALL", "ZAGDRATH", "BCH", "CMPSRV01", "*WAIT", "0", "5", "0"),
                cells("000124", "ARCHIVE", "OPERATOR", "BCH", "CMPSRV01", "*JOBQ", "0", "5", "0"),
                cells("000125", "UPSALERT", "ZAGDRATH", "BCH", "CMPSRV01", "*HELD", "0", "5", "0"));
        option(terminal, 8, "5");
        CrtGrid grid = terminal.compose();
        compare("08_wrkactjob", grid, 0, 3, 4, 6, 7, 8, 9, 10, 11, 20, 21, 23);
        // Elapsed counts from when the screen opened.
        assertEquals(" Budget used:   37%    Elapsed:  00:00:00    Active jobs:   5    Hosts:  2/2", row(grid, 2).stripTrailing());
    }

    @Test
    void workWithJob() throws IOException {
        CrtTerminal terminal = terminal();
        terminal.runCommand("WRKJOB JOB(RESTOCK)");
        answer(terminal, "job", cells("000123", "RESTOCK", "ZAGDRATH", "BCH", "MIDRANGE01", "*ACTIVE", "31", "5", "1"));
        CrtGrid grid = terminal.compose();
        compare("09_wrkjob", grid, 0, 2, 3, 5, 7, 8, 9, 10, 11, 12, 20, 21, 23);
        terminal.command.set("10");
        terminal.submit();
        assertEquals("DSPJOBLOG", terminal.current().id());
    }

    @Test
    void displayJobLog() throws IOException {
        CrtTerminal terminal = terminal();
        terminal.runCommand("DSPJOBLOG JOB(RESTOCK)");
        answer(terminal, "joblog", cells("000123", "RESTOCK", "ZAGDRATH", "BCH", "MIDRANGE01", "*ACTIVE", "31", "5", "1"),
                cells("1", "", "0", "CALL PGM(ZAGLIB/RESTOCK) PARM(LOGIC_DIE 64)", "", ""),
                cells("0", "ELC1203", "0", "Item LOGIC_DIE is being recalled from tape.", "QCMD", ""),
                cells("1", "", "0", "STRCRAFT ITEM(&ITEM) QTY(64) RTNCRFJOB(&JOB)", "", ""),
                cells("0", "ELC1403", "30", "Missing ingredients for LOGIC_DIE.", "RESTOCK", ""));
        CrtGrid grid = terminal.compose();
        compare("09b_dspjoblog", grid, 0, 2, 3, 4, 5, 6, 7, 20, 23);
    }

    @Test
    void workWithScheduleEntries() throws IOException {
        CrtTerminal terminal = terminal();
        terminal.runCommand("WRKJOBSCDE");
        answer(terminal, "schedules", cells("NOCWALL", "*SCD", "*INTERVAL", "Day 2  07:15:00", "CALL PGM(ZAGLIB/NOCWALL)", "ZAGDRATH", "*CURRENT", "30"),
                cells("ARCHIVE", "*SCD", "*DAILY", "Day 3  02:00:00", "CALL PGM(ZAGLIB/ARCHIVE)", "ZAGDRATH", "0200", "0"),
                cells("RESTOCK", "*HLD", "*ONCE", "Day 2  12:00:00", "CALL PGM(ZAGLIB/RESTOCK)", "ZAGDRATH", "1200", "0"));
        CrtGrid grid = terminal.compose();
        compare("10_wrkjobscde", grid, 0, 3, 4, 6, 7, 8, 9, 20, 21, 23);
    }

    @Test
    void workWithTriggers() throws IOException {
        CrtTerminal terminal = terminal();
        terminal.runCommand("WRKTRGEVT");
        answer(terminal, "triggers", cells("LOWDIES", "*ITMBELOW", "LOGIC_DIE", "*ANY", "64", "ZAGLIB/RESTOCK", "*ACTIVE", "ZAGDRATH"),
                cells("UPSPWR", "*PWRUPS", "*ANY", "UPS01", "", "ZAGLIB/UPSALERT", "*ACTIVE", "ZAGDRATH"),
                cells("LEVER", "*RSCHANGE", "*ANY", "CTLIF01", "*UP", "ZAGLIB/DOORS", "*HELD", "ZAGDRATH"));
        CrtGrid grid = terminal.compose();
        compare("11_wrktrgevt", grid, 0, 3, 4, 6, 7, 8, 9, 20, 21, 23);
    }

    @Test
    void displayMessages() throws IOException {
        CrtTerminal terminal = terminal();
        terminal.runCommand("DSPMSG");
        answer(terminal, "messages", cells("3", "ELC0013", "30", "RESTOCK", "Day 2  07:10:44", "Called program ZAGLIB/RESTOCK ended abnormally.", "1"),
                cells("2", "", "00", "UPSALERT", "Day 2  06:58:02", "Utility power restored. UPS charge 82%.", "0"),
                cells("1", "", "10", "QSYSOPR", "Day 2  06:40:15", "Storage 91% used (hot tier).", "0"));
        CrtGrid grid = terminal.compose();
        compare("12_dspmsg", grid, 0, 2, 3, 4, 6, 7, 8, 9, 11, 20, 21, 23);
        assertEquals(CrtGrid.BRIGHT, grid.attrs[7][39], "Unread not bright");
        assertTrue(((Host) hostOf(terminal)).sent.contains(TerminalService.SCREEN + " readmessages"), "Not marked read");
    }

    @Test
    void workWithSystemValues() throws IOException {
        CrtTerminal terminal = terminal();
        terminal.runCommand("WRKSYSVAL");
        answer(terminal, "sysvals", cells("SYSNAME", "ELNET01", "ELNET01", "System name", ""), cells("DATFMT", "*DAY", "*DAY", "Date display format", ""),
                cells("SECLVL", "30", "30", "Security level (10 / 30)", ""), cells("QMAXJOB", "16", "16", "Maximum batch jobs", ""),
                cells("LOGRTN", "50", "50", "Job logs retained", ""), cells("PHOSPHOR", "*GREEN", "*GREEN", "Default screen colour", ""));
        CrtGrid grid = terminal.compose();
        compare("14_wrksysval", grid, 0, 3, 4, 6, 7, 8, 9, 10, 11, 12, 20, 21, 23);
    }

    static CrtTerminal.Host hostOf(CrtTerminal terminal) {
        try {
            var field = CrtTerminal.class.getDeclaredField("host");
            field.setAccessible(true);
            return (CrtTerminal.Host) field.get(terminal);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void screenCommandsOpenTheirScreens() {
        CrtTerminal terminal = terminal();
        terminal.runCommand("WRKLIB");
        assertEquals("WRKLIB", terminal.current().id());
        terminal.runCommand("WRKMBR LIB(ZAGLIB)");
        assertEquals("WRKMBR", terminal.current().id());
        terminal.runCommand("WRKCRFJOB");
        assertEquals("WRKCRFJOB", terminal.current().id());
        terminal.runCommand("GO MAIN");
        assertEquals("MAIN", terminal.current().id());
        terminal.command.set("5");
        terminal.submit();
        assertEquals("WRKLIB", terminal.current().id());
        terminal.functionKey(12);
        terminal.command.set("2");
        terminal.submit();
        assertEquals("WRKCRFJOB", terminal.current().id());
    }

    @Test
    void phosphorDefaultsToTheSystemValue() {
        CrtTerminal terminal = terminal();
        terminal.systemPhosphor = "*AMBER";
        assertEquals("amber", terminal.phosphor());
    }
}
