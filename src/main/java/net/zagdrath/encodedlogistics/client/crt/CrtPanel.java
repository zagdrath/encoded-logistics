/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;

// One screen of the green-screen terminal (HANDOFF 3): its id (top left) and title (centred), its body (rows 3-20), its
// input fields, the prompt over the command line, and its function keys. CrtScreen draws the frame - header, date and
// time, command line, message line, keys - and hands it keys, clicks and the server's answers.
abstract class CrtPanel {
    protected final CrtScreen screen;
    final List<CrtField> fields = new ArrayList<>();

    CrtPanel(CrtScreen screen) {
        this.screen = screen;
    }

    abstract String id();

    abstract String title();

    // The prompt over the command line (row 21).
    String prompt() {
        return tr("crt.encodedlogistics.params");
    }

    String keys() {
        return "F3=Exit   F5=Refresh   F9=Command Entry   F12=Cancel";
    }

    // Shown (pushed, or come back to): fetch what it shows.
    void shown() {}

    // F5.
    void refresh() {
        shown();
    }

    abstract void draw(CrtGrid grid);

    // Enter with an empty command line; true when it did something.
    boolean enter() {
        return false;
    }

    void receive(CrtResponsePayload response) {}

    // F4 on a field: what it takes, for the message line; null for nothing to say.
    @Nullable Component prompt(CrtField field) {
        return null;
    }

    // F11.
    void sort() {}

    // PageUp (-1) / PageDown (+1).
    void page(int direction) {}

    void tick() {}

    // A click on a cell; double-clicked: do what Enter would.
    void click(int row, int col, boolean doubleClick) {}

    // The command line's text as this screen takes it (a menu's option number); false to run it as a command.
    boolean option(String text) {
        return false;
    }

    static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
