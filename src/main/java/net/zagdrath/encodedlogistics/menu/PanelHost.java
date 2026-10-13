/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueOutput;

// A block entity whose screen is a panel (BlockPanelMenu): the Exchange Switch, the Directory Server. Like a rack
// device's panel, it writes its state every few ticks while open and takes actions back. Server side.
public interface PanelHost {
    // Which panel the client draws ("exchange", "directory").
    String panelKind();

    Component panelTitle();

    void writePanel(ValueOutput output, ServerPlayer viewer);

    void handlePanelAction(ServerPlayer player, int action, int value, String text);
}
