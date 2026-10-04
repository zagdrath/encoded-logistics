/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.List;

import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// SIGN ON (HANDOFF 7.4, screens handoff screen 1): shown first on a network with a Firewall (unless SECLVL is 10) - the
// player types their own name (any case), no password; the server signs the session on (ELC0402 for another name; until
// then it answers nothing else) and makes their profile the first time. The desk then works with that player's
// permissions (the server checks them per command and option). Program/procedure (blank: none; else it's CALLed after sign-on), Menu (MAIN) and
// Current library (*USRPRF: the profile's, ELGPL) round it out, over the system's banner. Enter signs on; Esc, F3 or
// F12 exits.
final class SignOnPanel extends CrtPanel {
    private final CrtField user, program, menu, library;

    SignOnPanel(CrtTerminal screen) {
        super(screen);
        user = new CrtField(8, 34, 16, "");
        program = new CrtField(10, 34, 10, "").uppercase();
        menu = new CrtField(11, 34, 10, "MAIN").uppercase();
        library = new CrtField(12, 34, 10, "*USRPRF").uppercase();
        fields.add(user);
        fields.add(program);
        fields.add(menu);
        fields.add(library);
    }

    @Override
    String id() {
        return "SIGNON";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.signon.title");
    }

    @Override
    String prompt() {
        return "";
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.signon");
    }

    @Override
    void draw(CrtGrid grid) {
        String system = screen.network.isEmpty() ? "*OFFLINE" : screen.network;
        grid.put(3, 30, tr("crt.encodedlogistics.signon.system"));
        grid.put(3, 52, system, CrtGrid.BRIGHT);
        grid.put(5, 0, tr("crt.encodedlogistics.signon.firewall"), CrtGrid.DIM);
        grid.put(8, 0, CrtGrid.pad(tr("crt.encodedlogistics.signon.user"), 34));
        grid.put(9, 0, CrtGrid.pad(tr("crt.encodedlogistics.signon.password"), 34));
        grid.put(9, 34, tr("crt.encodedlogistics.signon.no_password"), CrtGrid.DIM);
        grid.put(10, 0, CrtGrid.pad(tr("crt.encodedlogistics.signon.program"), 34));
        grid.put(11, 0, CrtGrid.pad(tr("crt.encodedlogistics.signon.menu"), 34));
        grid.put(12, 0, CrtGrid.pad(tr("crt.encodedlogistics.signon.library"), 34));
        grid.put(20, 0, tr("crt.encodedlogistics.enter_continue"));
        // The system's name in small, dim, at the bottom right.
        String banner = tr("crt.encodedlogistics.signon.banner");
        grid.put(20, CrtGrid.COLS - banner.length(), banner, CrtGrid.DIM);
    }

    @Override
    String helpField(CrtField field) {
        return field == user ? "user" : field == program ? "program" : field == menu ? "menu" : field == library ? "library" : null;
    }

    @Override
    boolean enter() {
        String typed = user.trimmed();
        if (typed.isEmpty()) {
            screen.message(tr("crt.encodedlogistics.signon.type_user"));
            screen.focus(user);
            return true;
        }
        if (!menu.trimmed().isEmpty() && !menu.trimmed().equals("MAIN")) {
            screen.message(tr("crt.encodedlogistics.signon.bad_menu", menu.trimmed()));
            screen.focus(menu);
            return true;
        }
        // The server checks the name (ELC0402) and the library (ELC0201), and signs the session on.
        String lib = library.trimmed();
        screen.query("signon " + typed + " " + (lib.isEmpty() ? "*USRPRF" : lib));
        return true;
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (response.kind() != TerminalService.SCREEN || !response.topic().equals("signon")) {
            return;
        }
        if (response.message().isPresent() || response.lines().isEmpty()) {
            response.message().ifPresent(screen::message);
            user.set("");
            screen.focus(user);
            return;
        }
        // Back: the profile's class, current library and library list.
        List<TerminalLine.Cell> cells = response.lines().getFirst().cells();
        screen.currentLibrary = cells.size() > 1 ? cells.get(1).text().getString() : "ELGPL";
        screen.signedOn = true;
        screen.leave();
        screen.message(tr("crt.encodedlogistics.signon.done", screen.user));
        // The initial program, run as the session's first command.
        if (!program.trimmed().isEmpty() && !program.trimmed().equals("*NONE")) {
            screen.runCommand("CALL PGM(" + program.trimmed() + ")");
        }
    }
}
