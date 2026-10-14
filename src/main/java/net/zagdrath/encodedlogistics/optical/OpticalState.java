/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.optical;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.EnumProperty;

// The optical blocks' look (their blockstates' "state"): off, on (lit) or fault (line down, input loss of signal).
public enum OpticalState implements StringRepresentable {
    OFF("off"), ON("on"), FAULT("fault");

    public static final EnumProperty<OpticalState> PROPERTY = EnumProperty.create("state", OpticalState.class);

    private final String name;

    OpticalState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
