/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.wireless;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

// What a wireless block shows (its blockstate's "state"): off (no power or no lanes), linking (not linked, or no
// controller to serve it: yellow blink), online (light blue), active (a Wireless Bridge with lanes crossing: its
// window pulses) or fault (no access points, over capacity: red blink).
public enum WirelessState implements StringRepresentable {
    OFF("off"),
    LINKING("linking"),
    ONLINE("online"),
    ACTIVE("active"),
    FAULT("fault");

    // The Access Point's and the Wireless Ports' (no active), and the Wireless Bridge's.
    public static final EnumProperty<WirelessState> STATE = EnumProperty.create("state", WirelessState.class, state -> state != ACTIVE);
    public static final EnumProperty<WirelessState> BRIDGE_STATE = EnumProperty.create("state", WirelessState.class);

    private final String name;

    WirelessState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
