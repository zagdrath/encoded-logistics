/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics;

import net.neoforged.neoforge.common.ModConfigSpec;

public class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    static {
        BUILDER.push("controller");
    }

    public static final ModConfigSpec.IntValue CONTROLLER_MAX_SIZE = BUILDER
            .comment("Largest Network Controller structure, in blocks along each axis.")
            .defineInRange("maxSize", 7, 1, 32);

    public static final ModConfigSpec.IntValue CONTROLLER_ENERGY_PER_BLOCK = BUILDER
            .comment("FE each controller block adds to the structure's buffer.")
            .defineInRange("energyPerBlock", 25_000, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue CONTROLLER_MAX_RECEIVE = BUILDER
            .comment("FE per tick each controller block accepts.")
            .defineInRange("maxReceivePerBlock", 4_096, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue CONTROLLER_DRAIN = BUILDER
            .comment("FE per tick each controller block drains while the network runs.")
            .defineInRange("drainPerBlock", 2.0, 0.0, 1_000_000.0);

    static {
        BUILDER.pop();
        BUILDER.push("channels");
    }

    public static final ModConfigSpec.IntValue CHANNELS_PER_CONTROLLER_FACE = BUILDER
            .comment("Channels each controller face with a network connection provides.")
            .defineInRange("perControllerFace", 32, 1, 1024);

    public static final ModConfigSpec.IntValue CHANNELS_PER_CABLE = BUILDER
            .comment("Channels a Network Cable carries.")
            .defineInRange("perCable", 8, 1, 1024);

    public static final ModConfigSpec.IntValue CHANNELS_PER_DENSE_CABLE = BUILDER
            .comment("Channels a Dense Network Cable carries.")
            .defineInRange("perDenseCable", 32, 1, 1024);

    public static final ModConfigSpec.DoubleValue CABLE_DRAIN = BUILDER
            .comment("FE per tick each Network Cable block drains while its network runs.")
            .defineInRange("cableDrain", 0.05, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue DENSE_CABLE_DRAIN = BUILDER
            .comment("FE per tick each Dense Network Cable block drains while its network runs.")
            .defineInRange("denseCableDrain", 0.2, 0.0, 1_000.0);

    public static final ModConfigSpec.IntValue ADHOC_MAX_DEVICES = BUILDER
            .comment("Channel-using devices a network without a controller can run.")
            .defineInRange("adHocMaxDevices", 8, 0, 1024);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
