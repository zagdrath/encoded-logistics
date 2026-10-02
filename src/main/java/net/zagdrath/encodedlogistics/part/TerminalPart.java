/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.part;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.menu.AccessTerminalMenu;

// The Access Terminal: no state of its own; its screen browses the network's storage. Lit (the screen on) while online.
public class TerminalPart extends CablePart {
    public TerminalPart(PartType type, CableBlockEntity host, Direction side) {
        super(type, host, side);
    }

    @Override
    public boolean openMenu(ServerPlayer player) {
        AccessTerminalMenu.open(player, host.getBlockPos(), side);
        return true;
    }
}
