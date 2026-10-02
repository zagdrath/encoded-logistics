/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block.cable;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.DyeColor;

// A cable's colour. Neutral connects to every colour; a dyed cable only to the same dye and to neutral. Black, brown,
// gray and light gray are not offered.
public enum CableColor {
    NEUTRAL(null),
    WHITE(DyeColor.WHITE),
    ORANGE(DyeColor.ORANGE),
    MAGENTA(DyeColor.MAGENTA),
    LIGHT_BLUE(DyeColor.LIGHT_BLUE),
    YELLOW(DyeColor.YELLOW),
    LIME(DyeColor.LIME),
    PINK(DyeColor.PINK),
    CYAN(DyeColor.CYAN),
    PURPLE(DyeColor.PURPLE),
    BLUE(DyeColor.BLUE),
    GREEN(DyeColor.GREEN),
    RED(DyeColor.RED);

    private final @Nullable DyeColor dye;

    CableColor(@Nullable DyeColor dye) {
        this.dye = dye;
    }

    public @Nullable DyeColor dye() {
        return dye;
    }

    // The block id prefix: "" for neutral, "light_blue_" and so on.
    public String prefix() {
        return dye == null ? "" : dye.getSerializedName() + "_";
    }

    public boolean connectsTo(CableColor other) {
        return this == NEUTRAL || other == NEUTRAL || this == other;
    }

    // The cable colour a dye gives, or null for a dye cables don't come in.
    public static @Nullable CableColor of(@Nullable DyeColor dye) {
        if (dye == null) {
            return null;
        }
        for (CableColor color : values()) {
            if (color.dye == dye) {
                return color;
            }
        }
        return null;
    }
}
