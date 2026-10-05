/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.Locale;

import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;

// The Midrange line's blockstate properties.
public final class MidrangeStates {
    // What a Midrange System's (or the Integrated system's) operator panel shows: off, IPL (booting), run, busy (a job
    // running), attention (an error code).
    public enum Run implements StringRepresentable {
        OFF, IPL, RUN, BUSY, ATTN;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    // A side across the width: none, the +x side, the -x side (model-local). A Midrange System's Expansion Cabinet is on
    // one; an Expansion Cabinet's Midrange System is on one.
    public enum Side implements StringRepresentable {
        NONE, POS, NEG;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    // The Integrated system's console: off, booting, on (a session signed on at it).
    public enum Console implements StringRepresentable {
        OFF, BOOT, ON;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Run> STATE = EnumProperty.create("state", Run.class);
    public static final EnumProperty<Side> EXPANSION = EnumProperty.create("expansion", Side.class);
    public static final EnumProperty<Side> ATTACHED = EnumProperty.create("attached", Side.class);
    public static final EnumProperty<Console> CONSOLE = EnumProperty.create("console", Console.class);
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    public static final BooleanProperty LIT = BooleanProperty.create("lit");

    private MidrangeStates() {}
}
