/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// One screen of the green-screen terminal (HANDOFF 3): its id (top left) and title (centred), its body (rows 3-20), its
// input fields, the prompt over the command line, and its function keys. CrtTerminal draws the frame - header, date and
// time, command line, message line, keys - and hands it keys, clicks and the server's answers.
abstract class CrtPanel {
    protected final CrtTerminal screen;
    final List<CrtField> fields = new ArrayList<>();

    CrtPanel(CrtTerminal screen) {
        this.screen = screen;
    }

    abstract String id();

    abstract String title();

    // The prompt over the command line (row 21).
    String prompt() {
        return tr("crt.encodedlogistics.params");
    }

    // How many rows its command line takes (ending on row 21).
    int commandRows() {
        return 1;
    }

    String keys() {
        return tr("crt.encodedlogistics.fkeys.default");
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

    // Tab on this screen: true when it took it (Command Entry completes the command line's last word).
    boolean tab() {
        return false;
    }

    // A function key this screen has its own meaning for (F6 Create, F10, F19 / F20 window left / right, the editor's
    // keys...): true when it took it, before the frame's own meaning.
    boolean functionKey(int f) {
        return false;
    }

    // F1: the help for the field (or the option code) under the cursor, else the screen's -
    // crt.encodedlogistics.help.<screen>[.<field>] (null for the screen's own).
    String help(@Nullable String field) {
        String base = "crt.encodedlogistics.help." + id().toLowerCase(Locale.ROOT);
        if (field != null && Language.getInstance().has(base + "." + field)) {
            return tr(base + "." + field);
        }
        return Language.getInstance().has(base) ? tr(base) : tr("crt.encodedlogistics.help.none");
    }

    // The help key of the field under the cursor ("opt" on a list's Opt column), or null.
    @Nullable String helpField(@Nullable CrtField field) {
        return null;
    }

    // The command line's text as this screen takes it (a menu's option number); false to run it as a command.
    boolean option(String text) {
        return false;
    }

    // A field of a row from the server (the screens' queries send one cell per field), "" past the last.
    static String cell(TerminalLine line, int index) {
        return index < line.cells().size() ? line.cells().get(index).text().getString() : "";
    }

    // Whether a response is the answer to this screen's query (TerminalService.SCREEN) about topic.
    static boolean answers(CrtResponsePayload response, String topic) {
        return response.kind() == TerminalService.SCREEN && response.topic().equals(topic);
    }

    static String tr(String key, Object... args) {
        return Component.translatable(key, args).getString();
    }
}
