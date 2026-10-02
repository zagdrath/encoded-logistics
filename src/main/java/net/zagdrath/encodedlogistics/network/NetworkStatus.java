/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import net.minecraft.network.chat.Component;

// Why a network is (or isn't) running, as shown on the Network screen's status line.
public enum NetworkStatus {
    ONLINE("online"),
    // Valid, but the controller's buffer is empty: offline, every device loses its lane.
    NO_POWER("no_power"),
    // The controllers aren't an edge-only box.
    INVALID_SHAPE("invalid_shape"),
    // The controllers' bounding box is over the size limit.
    TOO_LARGE("too_large"),
    // Two or more separate controller structures on one network: all of it offline until one goes.
    CONFLICT("conflict"),
    // No controller and more ad-hoc devices than allowed.
    ADHOC_OVERLOAD("adhoc_overload");

    private static final NetworkStatus[] VALUES = values();

    private final String name;

    NetworkStatus(String name) {
        this.name = name;
    }

    public String getSerializedName() {
        return name;
    }

    // Errors show red: the LED, the status text and the error overlay on the controller blocks.
    public boolean isError() {
        return this != ONLINE && this != NO_POWER;
    }

    public Component description() {
        return Component.translatable("gui.encodedlogistics.status." + name);
    }

    public static NetworkStatus byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NO_POWER;
    }
}
