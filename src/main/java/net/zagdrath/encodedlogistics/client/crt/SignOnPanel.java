/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

// SIGN ON (HANDOFF 7.4, screens handoff screen 1): shown first on a network with a Firewall (unless SECLVL is 10) - the
// player types their own name (any case), no password; the desk then works with that player's permissions (the server
// checks them per command and option). Program/procedure (blank: none; else it's CALLed after sign-on), Menu (MAIN) and
// Current library (*USRPRF: the profile's, ELGPL) round it out, over the system's banner. Enter signs on; Esc, F3 or
// F12 exits.
final class SignOnPanel extends CrtPanel {
    private static final int BOX_ROW = 14, BOX_COL = 18, BOX_HEIGHT = 5, BOX_WIDTH = 44;
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
        grid.box(BOX_ROW, BOX_COL, BOX_HEIGHT, BOX_WIDTH, -1);
        banner(grid, 1, tr("crt.encodedlogistics.signon.banner"), CrtGrid.BRIGHT);
        banner(grid, 2, tr("crt.encodedlogistics.signon.os", system), CrtGrid.NORMAL);
        banner(grid, 3, tr("crt.encodedlogistics.signon.credit"), CrtGrid.DIM);
        grid.put(20, 0, tr("crt.encodedlogistics.enter_continue"));
    }

    private static void banner(CrtGrid grid, int line, String text, byte attr) {
        grid.put(BOX_ROW + line, BOX_COL + (BOX_WIDTH - text.length()) / 2, text, attr);
    }

    @Override
    String helpField(CrtField field) {
        return field == user ? "user" : field == program ? "program" : field == menu ? "menu" : field == library ? "library" : null;
    }

    @Override
    boolean enter() {
        String typed = user.trimmed();
        if (typed.isEmpty() || !typed.equalsIgnoreCase(screen.user)) {
            screen.message(typed.isEmpty() ? tr("crt.encodedlogistics.signon.type_user") : tr("crt.encodedlogistics.signon.bad_user", typed));
            user.set("");
            screen.focus(user);
            return true;
        }
        if (!menu.trimmed().isEmpty() && !menu.trimmed().equals("MAIN")) {
            screen.message(tr("crt.encodedlogistics.signon.bad_menu", menu.trimmed()));
            screen.focus(menu);
            return true;
        }
        String lib = library.trimmed();
        screen.currentLibrary = lib.isEmpty() || lib.equals("*USRPRF") ? "ELGPL" : lib;
        screen.signedOn = true;
        screen.leave();
        screen.message(tr("crt.encodedlogistics.signon.done", screen.user));
        // The initial program, run as the session's first command.
        if (!program.trimmed().isEmpty() && !program.trimmed().equals("*NONE")) {
            screen.runCommand("CALL PGM(" + program.trimmed() + ")");
        }
        return true;
    }
}
