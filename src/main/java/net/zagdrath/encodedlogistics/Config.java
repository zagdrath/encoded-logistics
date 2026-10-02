/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics;

import java.util.List;

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
        BUILDER.push("lanes");
    }

    public static final ModConfigSpec.IntValue LANES_PER_CONTROLLER_FACE = BUILDER
            .comment("Lanes each controller face with a network connection provides.")
            .defineInRange("perControllerFace", 32, 1, 1024);

    public static final ModConfigSpec.IntValue LANES_PER_CABLE = BUILDER
            .comment("Lanes a Network Cable carries.")
            .defineInRange("perCable", 8, 1, 1024);

    public static final ModConfigSpec.IntValue LANES_PER_DENSE_CABLE = BUILDER
            .comment("Lanes a Dense Network Cable carries.")
            .defineInRange("perDenseCable", 32, 1, 1024);

    public static final ModConfigSpec.IntValue LANES_PER_FIBER_CABLE = BUILDER
            .comment("Lanes a Fiber Cable carries.")
            .defineInRange("perFiberCable", 32, 1, 1024);

    public static final ModConfigSpec.DoubleValue CABLE_DRAIN = BUILDER
            .comment("FE per tick each Network Cable block drains while its network runs.")
            .defineInRange("cableDrain", 0.05, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue DENSE_CABLE_DRAIN = BUILDER
            .comment("FE per tick each Dense Network Cable block drains while its network runs.")
            .defineInRange("denseCableDrain", 0.2, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue FIBER_CABLE_DRAIN = BUILDER
            .comment("FE per tick each Fiber Cable block drains while its network runs.")
            .defineInRange("fiberCableDrain", 0.1, 0.0, 1_000.0);

    public static final ModConfigSpec.IntValue ADHOC_MAX_DEVICES = BUILDER
            .comment("Lane-using devices a network without a controller can run.")
            .defineInRange("adHocMaxDevices", 8, 0, 1024);

    static {
        BUILDER.pop();
        BUILDER.push("power");
    }

    public static final ModConfigSpec.IntValue INLET_MAX_INPUT = BUILDER
            .comment("FE per tick a Power Inlet accepts through its port.")
            .defineInRange("inletMaxInput", 16_384, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue INLET_BUFFER = BUILDER
            .comment("FE a Power Inlet holds when the network's energy is full, passed on as soon as there's room (0: none).")
            .defineInRange("inletBuffer", 0, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue CAPACITOR_CAPACITY = BUILDER
            .comment("FE a Capacitor Bank stores.")
            .defineInRange("capacitorCapacity", 2_000_000, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue CAPACITOR_MAX_TRANSFER = BUILDER
            .comment("FE per tick a Capacitor Bank takes in, and gives out, at most.")
            .defineInRange("capacitorMaxTransfer", 16_384, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.BooleanValue CAPACITOR_DIRECT_IO = BUILDER
            .comment("Whether Capacitor Banks also take and give FE directly on every face, not just through the network.")
            .define("capacitorDirectIO", true);

    public static final ModConfigSpec.DoubleValue ISOLATOR_DRAIN = BUILDER
            .comment("FE per tick a Segment Isolator drains from each network it separates.")
            .defineInRange("isolatorDrain", 0.5, 0.0, 1_000.0);

    static {
        BUILDER.pop();
        BUILDER.push("storage");
    }

    public static final ModConfigSpec.DoubleValue DRIVE_BAY_DRAIN = BUILDER
            .comment("FE per tick a Drive Bay drains while its network runs, on top of its drives.")
            .defineInRange("driveBayDrain", 2.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue DRIVE_BAY_DRAIN_PER_DRIVE = BUILDER
            .comment("FE per tick each Storage Drive in a Drive Bay drains.")
            .defineInRange("driveBayDrainPerDrive", 0.5, 0.0, 1_000.0);

    public static final ModConfigSpec.IntValue DRIVE_TYPE_LIMIT = BUILDER
            .comment("Different item types one Storage Drive holds, whatever its size.")
            .defineInRange("driveTypeLimit", 63, 1, 4096);

    public static final ModConfigSpec.DoubleValue TERMINAL_DRAIN = BUILDER
            .comment("FE per tick an Access Terminal drains.")
            .defineInRange("terminalDrain", 0.5, 0.0, 1_000.0);

    public static final ModConfigSpec.IntValue TERMINAL_LANES = BUILDER
            .comment("Lanes an Access Terminal uses (0: terminals are free).")
            .defineInRange("terminalLanes", 1, 0, 32);

    static {
        BUILDER.pop();
        BUILDER.push("lithography");
    }

    public static final ModConfigSpec.IntValue PRESS_CAPACITY = BUILDER
            .comment("FE a Lithography Press buffers.")
            .defineInRange("pressCapacity", 32_000, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue PRESS_MAX_INPUT = BUILDER
            .comment("FE per tick a Lithography Press takes in.")
            .defineInRange("pressMaxInput", 512, 0, Integer.MAX_VALUE);

    static {
        BUILDER.pop();
        BUILDER.push("facades");
    }

    public static final ModConfigSpec.ConfigValue<List<? extends String>> FACADE_BLOCKLIST = BUILDER
            .comment("Blocks a Cable Facade can't copy, by id (e.g. \"minecraft:bedrock\"), on top of the built-in rules: the block must be",
                    "a full, non-translucent cube without a block entity.")
            .defineListAllowEmpty("blocklist", List.of(), () -> "minecraft:stone", entry -> entry instanceof String);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
