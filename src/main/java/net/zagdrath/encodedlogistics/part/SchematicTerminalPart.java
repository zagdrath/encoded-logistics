/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.part;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.menu.SchematicTerminalMenu;

// The Schematic Terminal: no state of its own; its screen shows the network's schematic holders and crafting jobs
// (SchematicTerminalMenu). Lit (the screen on) while online.
public class SchematicTerminalPart extends TerminalPart {
    public SchematicTerminalPart(PartType type, CableBlockEntity host, Direction side) {
        super(type, host, side);
    }

    @Override
    public boolean openMenu(ServerPlayer player) {
        SchematicTerminalMenu.open(player, host.getBlockPos(), side);
        return true;
    }
}
