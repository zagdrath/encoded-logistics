/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

// The green screens' function keys (CrtScreen's, CrtMachineScreen's). Fullscreen and screenshot (F11, F2) are taken
// before any screen sees the key: while a green screen is open, the ones bound to function keys are unbound, so the
// screen gets them. Given back when it closes.
final class FunctionKeys {
    private final List<KeyMapping> borrowed = new ArrayList<>();
    private final List<InputConstants.Key> borrowedKeys = new ArrayList<>();

    void borrow(Minecraft minecraft) {
        if (!borrowed.isEmpty()) {
            return;
        }
        for (KeyMapping mapping : List.of(minecraft.options.keyFullscreen, minecraft.options.keyScreenshot)) {
            InputConstants.Key key = mapping.getKey();
            int code = key.getValue();
            if (key.getType() == InputConstants.Type.KEYBOARD
                    && (code >= InputConstants.KEY_F1 && code <= InputConstants.KEY_F12 || code >= InputConstants.KEY_F13 && code <= InputConstants.KEY_F24)) {
                borrowed.add(mapping);
                borrowedKeys.add(key);
                mapping.setKey(InputConstants.UNKNOWN);
            }
        }
    }

    void giveBack() {
        for (int i = 0; i < borrowed.size(); i++) {
            borrowed.get(i).setKey(borrowedKeys.get(i));
        }
        borrowed.clear();
        borrowedKeys.clear();
    }

    // F1-F12 (shifted: F13-F24), and F13-F24 themselves; 0 for any other key.
    static int of(KeyEvent event) {
        int key = event.key();
        if (key >= InputConstants.KEY_F1 && key <= InputConstants.KEY_F12) {
            return key - InputConstants.KEY_F1 + 1 + (event.hasShiftDown() ? 12 : 0);
        }
        return key >= InputConstants.KEY_F13 && key <= InputConstants.KEY_F24 ? key - InputConstants.KEY_F13 + 13 : 0;
    }

    // The game's day and time of day (the screens' clock): "Day 3  14:32:07".
    static String clock(Minecraft minecraft) {
        if (minecraft.level == null) {
            return "";
        }
        long time = minecraft.level.getOverworldClockTime();
        long day = time / 24_000 + 1, tick = time % 24_000;
        long seconds = (tick * 86_400 / 24_000 + 6 * 3_600) % 86_400;
        return String.format(Locale.ROOT, "Day %d  %02d:%02d:%02d", day, seconds / 3_600, seconds / 60 % 60, seconds % 60);
    }
}
