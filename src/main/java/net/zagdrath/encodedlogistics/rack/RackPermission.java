/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.Locale;

import net.minecraft.network.chat.Component;

// What a Firewall controls, per player: seeing the network's items, putting items in, taking them out, asking for
// crafts, building (placing, breaking and configuring the network's blocks and parts), and rack access (opening the
// network's Server Racks - their doors and screen - and installing, removing and setting up their devices; left on
// INHERIT it follows the player's build permission, so it can be granted without build, or build without it).
public enum RackPermission {
    VIEW, INSERT, EXTRACT, CRAFT, BUILD, RACK;

    private static final RackPermission[] VALUES = values();

    public static RackPermission byId(int id) {
        return VALUES[Math.clamp(id, 0, VALUES.length - 1)];
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public Component label() {
        return Component.translatable("gui.encodedlogistics.firewall.perm." + key());
    }
}
