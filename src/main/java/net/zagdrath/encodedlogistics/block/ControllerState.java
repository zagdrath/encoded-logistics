/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block;

import net.minecraft.util.StringRepresentable;

// What a Network Controller block shows: plain (offline), the colour cycle (online) or the red overlay (error).
public enum ControllerState implements StringRepresentable {
    // A valid structure without power.
    OFFLINE("offline"),
    // A valid structure with power.
    ONLINE("online"),
    // An invalid shape, too large, or in conflict with another controller.
    ERROR("error");

    private final String name;

    ControllerState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
