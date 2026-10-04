/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

// SIGN ON (HANDOFF 7.4): shown first on a network with a Firewall - the player types their own name (any case), no
// password; the desk then works with that player's permissions (the server checks them per command and option).
// Enter signs on; Esc, F3 or F12 exits.
final class SignOnPanel extends CrtPanel {
    private final CrtField user;

    SignOnPanel(CrtScreen screen) {
        super(screen);
        user = new CrtField(8, 34, 16, "");
        fields.add(user);
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
        return "F3=Exit";
    }

    @Override
    void draw(CrtGrid grid) {
        grid.put(5, 0, tr("crt.encodedlogistics.signon.firewall"), CrtGrid.DIM);
        grid.put(8, 0, CrtGrid.pad(tr("crt.encodedlogistics.signon.user"), 34));
        grid.put(9, 0, CrtGrid.pad(tr("crt.encodedlogistics.signon.password"), 34));
        grid.put(9, 34, tr("crt.encodedlogistics.signon.no_password"), CrtGrid.DIM);
        grid.put(20, 0, tr("crt.encodedlogistics.enter_continue"));
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
        screen.signedOn = true;
        screen.leave();
        screen.message(tr("crt.encodedlogistics.signon.done", screen.user));
        return true;
    }
}
