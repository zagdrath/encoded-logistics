/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import net.minecraft.server.level.ServerPlayer;

// A menu that takes a typed-in number from its screen (MenuValuePayload): a tap's priority, a sensor's threshold.
public interface ValueMenu {
    void setValue(ServerPlayer player, int key, int value);
}
