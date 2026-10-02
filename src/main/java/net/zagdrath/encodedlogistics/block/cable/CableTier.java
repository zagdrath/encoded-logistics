/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block.cable;

import net.zagdrath.encodedlogistics.Config;

// Network Cable (8 channels) and Dense Network Cable (32); both from the config.
public enum CableTier {
    NORMAL("network_cable"),
    DENSE("dense_network_cable");

    private final String name;

    CableTier(String name) {
        this.name = name;
    }

    // The block id without a colour: network_cable, dense_network_cable.
    public String baseName() {
        return name;
    }

    public int channels() {
        return this == DENSE ? Config.CHANNELS_PER_DENSE_CABLE.getAsInt() : Config.CHANNELS_PER_CABLE.getAsInt();
    }

    public double passiveDrain() {
        return this == DENSE ? Config.DENSE_CABLE_DRAIN.getAsDouble() : Config.CABLE_DRAIN.getAsDouble();
    }
}
