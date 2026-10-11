/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

// The vanilla Sneak key, for a screen's sneak-clicks. Key mappings don't update while a screen is open, so this reads the
// key bound to Sneak straight from the keyboard. With Sneak on a Shift key (the default) either Shift works, as before;
// with it on another key, that key does. Bound to a mouse button or nothing, Shift stands in. (In the world, sneak-use
// already follows the Sneak key: the server sees the player crouching.)
public final class SneakKey {
    private SneakKey() {}

    // Whether Sneak is held; shift: whether the click or key event had Shift down.
    public static boolean held(boolean shift) {
        InputConstants.Key key = Minecraft.getInstance().options.keyShift.getKey();
        int value = key.getValue();
        if (key.getType() != InputConstants.Type.KEYBOARD || value < 0) {
            return shift;
        }
        if (value == InputConstants.KEY_LSHIFT || value == InputConstants.KEY_RSHIFT) {
            return shift || InputConstants.isKeyDown(value);
        }
        return InputConstants.isKeyDown(value);
    }

    // The key's name as the controls screen shows it ("Left Shift", "C").
    public static Component name() {
        return Minecraft.getInstance().options.keyShift.getTranslatedKeyMessage();
    }
}
