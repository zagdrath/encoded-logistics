/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block.cable;

import net.zagdrath.encodedlogistics.Config;

// Network Cable (8 lanes), Dense Network Cable (32) and Fiber Cable (32 in the slim profile); all from the config.
public enum CableTier {
    NORMAL("network_cable"),
    DENSE("dense_network_cable"),
    FIBER("fiber_cable");

    private final String name;

    CableTier(String name) {
        this.name = name;
    }

    // The block id without a colour: network_cable, dense_network_cable, fiber_cable.
    public String baseName() {
        return name;
    }

    // Dense cables are the thick ones; Network and Fiber Cable share the slim geometry.
    public boolean dense() {
        return this == DENSE;
    }

    public int lanes() {
        return switch (this) {
            case NORMAL -> Config.LANES_PER_CABLE.getAsInt();
            case DENSE -> Config.LANES_PER_DENSE_CABLE.getAsInt();
            case FIBER -> Config.LANES_PER_FIBER_CABLE.getAsInt();
        };
    }

    public double passiveDrain() {
        return switch (this) {
            case NORMAL -> Config.CABLE_DRAIN.getAsDouble();
            case DENSE -> Config.DENSE_CABLE_DRAIN.getAsDouble();
            case FIBER -> Config.FIBER_CABLE_DRAIN.getAsDouble();
        };
    }
}
