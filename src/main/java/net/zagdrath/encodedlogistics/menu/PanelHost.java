/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.ValueOutput;

// A block entity (or cable part) whose screen is a panel (BlockPanelMenu): the Exchange Switch, the Directory Server,
// the Trunk Port. Like a rack
// device's panel, it writes its state every few ticks while open and takes actions back. Server side.
public interface PanelHost {
    // Which panel the client draws ("exchange", "directory").
    String panelKind();

    Component panelTitle();

    void writePanel(ValueOutput output, ServerPlayer viewer);

    void handlePanelAction(ServerPlayer player, int action, int value, String text);

    // Whether a player can still use it (a block entity: while it's there and they're near).
    default boolean stillValid(Player player) {
        return true;
    }
}
