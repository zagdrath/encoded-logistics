/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.io.IOException;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

// Work with Members' F6: CRTMBR prompted with the library shown already in the Member field, so the name typed after it
// creates the member there (not in the current library).
class WrkMbrTest {
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
}
