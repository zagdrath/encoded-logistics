/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import static net.zagdrath.encodedlogistics.client.crt.LayoutTest.answer;
import static net.zagdrath.encodedlogistics.client.crt.LayoutTest.cells;
import static net.zagdrath.encodedlogistics.client.crt.LayoutTest.hostOf;
import static net.zagdrath.encodedlogistics.client.crt.LayoutTest.row;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// Work with Members' F6: CRTMBR prompted with the library shown already in the Member field, so the name typed after it
// creates the member there (not in the current library). 9=Submit and 16=Call: their prompters filled in, the command
// each sends, and a member with no program or one changed since it was compiled refused with why, and the offer to
// compile first (Enter compiles it, then the prompter on ELC0218; F12 leaves it).
class WrkMbrTest {
    // name, type, changed, text, lines, updated, program
    private static final TerminalLine RESTOCK = cells("RESTOCK", "ELCLP", "", "Keep logic dies stocked", "40", "2", "1");
    private static final TerminalLine NOCWALL = cells("NOCWALL", "ELCLP", "*", "Refresh the NOC status display", "30", "2", "1");
    private static final TerminalLine NEWPGM = cells("NEWPGM", "ELCLP", "", "Not compiled yet", "3", "2", "0");
    private static final TerminalLine STOCK = cells("STOCK", "PF", "", "Stock levels", "4", "2", "0");

    @BeforeAll
    static void language() throws IOException {
        LayoutTest.language();
    }

    @Test
    void createInTheLibraryShown() {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.push(new WrkMbrPanel(terminal, "ZAGLIB"));
        terminal.functionKey(6);
        PrompterPanel prompter = assertInstanceOf(PrompterPanel.class, terminal.current());
        assertEquals("ZAGLIB/", terminal.focused().value, "The library in the Member field");
        for (char c : "RED_TEST".toCharArray()) {
            terminal.type(c);
        }
        assertEquals("CRTMBR MBR(ZAGLIB/RED_TEST)", prompter.command());
    }

    // The list, sorted by name: NEWPGM (row 7), NOCWALL (8), RESTOCK (9), STOCK (10).
    private static CrtTerminal opened() {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.push(new WrkMbrPanel(terminal, "ZAGLIB"));
        answer(terminal, "members", RESTOCK, NOCWALL, NEWPGM, STOCK);
        return terminal;
    }

    private static CrtTerminal chosen(int row, String option) {
        CrtTerminal terminal = opened();
        LayoutTest.option(terminal, row, option);
        terminal.submit();
        return terminal;
    }

    private static List<String> sent(CrtTerminal terminal) {
        return ((LayoutTest.Host) hostOf(terminal)).sent;
    }

    private static void answered(CrtTerminal terminal, String message) {
        terminal.receive(new CrtResponsePayload(0, TerminalService.COMMAND, "", List.of(), Optional.of(Component.literal(message)), -1));
    }

    private static String messageLine(CrtTerminal terminal) {
        return row(terminal.compose(), 22).strip();
    }

    @Test
    void submitPromptsSbmjobFilledIn() {
        CrtTerminal terminal = chosen(9, "9");
        PrompterPanel prompter = assertInstanceOf(PrompterPanel.class, terminal.current(), "9 didn't open the prompter");
        assertEquals("Submit Job (SBMJOB)", prompter.title());
        assertEquals("SBMJOB CMD(CALL PGM(ZAGLIB/RESTOCK)) JOB(RESTOCK)", prompter.command());
        terminal.submit();
        assertTrue(sent(terminal).contains(TerminalService.COMMAND + " SBMJOB CMD(CALL PGM(ZAGLIB/RESTOCK)) JOB(RESTOCK)"), "Sent " + sent(terminal));
        assertInstanceOf(WrkMbrPanel.class, terminal.current(), "Not back on the list");
    }

    @Test
    void callPromptsCallFilledIn() {
        CrtTerminal terminal = chosen(9, "16");
        PrompterPanel prompter = assertInstanceOf(PrompterPanel.class, terminal.current(), "16 didn't open the prompter");
        assertEquals("Call Program (CALL)", prompter.title());
        assertEquals("CALL PGM(ZAGLIB/RESTOCK)", prompter.command());
        terminal.submit();
        assertTrue(sent(terminal).contains(TerminalService.COMMAND + " CALL PGM(ZAGLIB/RESTOCK)"), "Sent " + sent(terminal));
    }

    @Test
    void notCompiledRefusedThenCompiledFirst() {
        CrtTerminal terminal = chosen(7, "16");
        assertEquals("Program ZAGLIB/NEWPGM is not compiled.", messageLine(terminal));
        assertNotNull(terminal.window(), "No offer to compile first");
        assertInstanceOf(WrkMbrPanel.class, terminal.current(), "Prompted anyway");
        assertFalse(sent(terminal).stream().anyMatch(line -> line.contains("CALL ")), "Called anyway");
        // Enter compiles it; once it's created, CALL's prompter.
        terminal.submit();
        assertNull(terminal.window());
        assertTrue(sent(terminal).contains(TerminalService.COMMAND + " CRTELPGM PGM(ZAGLIB/NEWPGM)"), "Sent " + sent(terminal));
        answered(terminal, "ELC0218  Program NEWPGM created in library ZAGLIB.");
        PrompterPanel prompter = assertInstanceOf(PrompterPanel.class, terminal.current(), "No prompter after the compile");
        assertEquals("CALL PGM(ZAGLIB/NEWPGM)", prompter.command());
    }

    @Test
    void compileFirstThatFailsStops() {
        CrtTerminal terminal = chosen(7, "9");
        terminal.submit();
        answered(terminal, "ELC0206  Program NEWPGM was not created: compile errors.");
        assertInstanceOf(WrkMbrPanel.class, terminal.current(), "Prompted after a failed compile");
    }

    @Test
    void changedSinceCompiledRefused() {
        CrtTerminal terminal = chosen(8, "9");
        assertEquals("Member ZAGLIB/NOCWALL changed since it was compiled.", messageLine(terminal));
        assertNotNull(terminal.window(), "No offer to compile first");
        // F12: nothing run, back on the list.
        terminal.functionKey(12);
        assertNull(terminal.window());
        assertInstanceOf(WrkMbrPanel.class, terminal.current());
        assertFalse(sent(terminal).stream().anyMatch(line -> line.startsWith(TerminalService.COMMAND + " ")), "Sent " + sent(terminal));
    }

    @Test
    void notForAFileDefinition() {
        CrtTerminal terminal = chosen(10, "16");
        assertEquals(CrtPanel.tr("crt.encodedlogistics.msg.invalid_option", "16"), messageLine(terminal));
        assertInstanceOf(WrkMbrPanel.class, terminal.current());
    }
}
