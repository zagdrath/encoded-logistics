/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.client.screen.TerminalSettings;

// F24=More keys: every key the screens take, and the phosphor (Green, Amber, White) - type it (or 1-3), press Enter;
// it's kept in the client config.
final class MoreKeysPanel extends CrtPanel {
    private static final String[] PHOSPHORS = { "green", "amber", "white" };
    private final CrtField phosphor;

    MoreKeysPanel(CrtScreen screen) {
        super(screen);
        phosphor = new CrtField(17, 34, 10, capital(TerminalSettings.phosphor()));
        fields.add(phosphor);
    }

    private static String capital(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1).toLowerCase(Locale.ROOT);
    }

    @Override
    String id() {
        return "MOREKEYS";
    }

    @Override
    String title() {
        return tr("crt.encodedlogistics.keys.title");
    }

    @Override
    String prompt() {
        return "";
    }

    @Override
    String keys() {
        return "F3=Exit   F4=Prompt   F12=Cancel";
    }

    @Override
    void draw(CrtGrid grid) {
        String[] keys = { "F3     Exit", "F4     Prompt (the values a field takes)", "F5     Refresh", "F9     Command Entry (there: retrieve)",
                "F11    Sort", "F12    Cancel (back one screen; Esc too)", "F13    Clear (Shift+F1, Command Entry)", "F24    More keys (Shift+F12)",
                "Tab    Next field (Shift+Tab: previous)", "PgUp   Roll the list back (PgDn: on)" };
        for (int i = 0; i < keys.length; i++) {
            grid.put(3 + i, 2, keys[i]);
        }
        grid.put(15, 0, tr("crt.encodedlogistics.type_choices"));
        grid.put(17, 0, CrtGrid.pad(tr("crt.encodedlogistics.keys.phosphor"), 34));
        grid.put(17, 46, "Green, Amber, White", CrtGrid.DIM);
        grid.put(20, 0, tr("crt.encodedlogistics.enter_continue"));
    }

    @Override
    Component prompt(CrtField field) {
        return Component.literal("1=Green  2=Amber  3=White");
    }

    @Override
    boolean enter() {
        String typed = phosphor.trimmed().toLowerCase(Locale.ROOT);
        String chosen = null;
        for (int i = 0; i < PHOSPHORS.length; i++) {
            if (typed.equals(PHOSPHORS[i]) || typed.equals(Integer.toString(i + 1)) || !typed.isEmpty() && PHOSPHORS[i].startsWith(typed)) {
                chosen = PHOSPHORS[i];
            }
        }
        if (chosen == null) {
            screen.message(tr("crt.encodedlogistics.msg.invalid_value", phosphor.trimmed()));
            return true;
        }
        screen.setPhosphor(chosen);
        screen.back();
        screen.message(tr("crt.encodedlogistics.keys.set", capital(chosen)));
        return true;
    }
}
