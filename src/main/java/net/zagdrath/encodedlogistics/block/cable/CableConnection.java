/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block.cable;

import net.minecraft.util.StringRepresentable;

// What a cable side connects to: nothing, a compatible cable, or a network block (a controller, later devices).
public enum CableConnection implements StringRepresentable {
    NONE("none"),
    CABLE("cable"),
    BLOCK("block");

    private final String name;

    CableConnection(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public boolean connected() {
        return this != NONE;
    }
}
