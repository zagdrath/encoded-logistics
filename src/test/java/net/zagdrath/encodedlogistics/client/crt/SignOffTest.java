/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Main Menu option 90 (SIGNOFF): where sign-on is needed the session signs off and Sign On shows again, with the last
// user's history gone; where it isn't, there's no Sign On to go back to.
class SignOffTest {
    @BeforeAll
    static void language() throws IOException {
        LayoutTest.language();
    }

    private static List<String> sent(CrtTerminal terminal) {
        try {
            var field = CrtTerminal.class.getDeclaredField("host");
            field.setAccessible(true);
            return ((LayoutTest.Host) field.get(terminal)).sent;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void option90(CrtTerminal terminal) {
        terminal.command.set("90");
        terminal.submit();
    }

    @Test
    void backToSignOn() {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.firewall = true;
        terminal.signedOn = true;
        terminal.securityLevel = "30";
        terminal.addHistory("> DSPMSG", CrtGrid.NORMAL);
        option90(terminal);
        assertInstanceOf(SignOnPanel.class, terminal.current(), "Not back at Sign On");
        assertFalse(terminal.signedOn, "Still signed on");
        assertTrue(terminal.history.isEmpty(), "The last user's history is still there");
        assertTrue(sent(terminal).stream().anyMatch(text -> text.endsWith("signoff")), "The server wasn't told: " + sent(terminal));
    }

    @Test
    void noSignOnNeeded() {
        CrtTerminal terminal = LayoutTest.terminal();
        terminal.firewall = false;
        option90(terminal);
        assertFalse(terminal.current() instanceof SignOnPanel, "Sign On shown where none is needed");
        assertFalse(sent(terminal).stream().anyMatch(text -> text.endsWith("signoff")), "Signed off where none is needed");
    }
}
