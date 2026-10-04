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

    public static final ModConfigSpec.DoubleValue PART_DRAIN = BUILDER
            .comment("FE per tick each other cable part (ports, taps, sensors) drains.")
            .defineInRange("partDrain", 0.5, 0.0, 1_000.0);

    public static final ModConfigSpec.IntValue PART_LANES = BUILDER
            .comment("Lanes each other cable part (ports, taps, sensors) uses.")
            .defineInRange("partLanes", 1, 0, 32);

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> PORT_RATES = BUILDER
            .comment("Items an Ingress or Egress Port moves per operation (one every 20 ticks), with 0, 1, 2 and 3 Throughput Modules.")
            .defineList("portRates", List.of(4, 16, 32, 64), () -> 4, entry -> entry instanceof Integer value && value >= 0);

    public static final ModConfigSpec.DoubleValue PORT_ENERGY_PER_ITEM = BUILDER
            .comment("FE a port spends from its network for each item it moves.")
            .defineInRange("portEnergyPerItem", 0.5, 0.0, 1_000.0);

    static {
        BUILDER.pop();
        BUILDER.push("autocrafting");
    }

    public static final ModConfigSpec.IntValue SCHEDULER_MAX_SIZE = BUILDER
            .comment("Largest Scheduler structure, in blocks along each axis.")
            .defineInRange("schedulerMaxSize", 7, 1, 16);

    public static final ModConfigSpec.DoubleValue SCHEDULER_DRAIN = BUILDER
            .comment("FE per tick a formed Scheduler drains, on top of its blocks.")
            .defineInRange("schedulerDrain", 2.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue SCHEDULER_DRAIN_PER_BLOCK = BUILDER
            .comment("FE per tick each block of a formed Scheduler drains.")
            .defineInRange("schedulerDrainPerBlock", 0.5, 0.0, 1_000.0);

    public static final ModConfigSpec.IntValue SCHEDULER_BASE_MEMORY = BUILDER
            .comment("Job memory a Scheduler Core provides on its own (items across its jobs' ingredient trees).")
            .defineInRange("schedulerBaseMemory", 4_096, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue JOB_BUFFER_MEMORY = BUILDER
            .comment("Job memory each Job Buffer adds.")
            .defineInRange("jobBufferMemory", 16_384, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue SCHEDULER_BASE_THREADS = BUILDER
            .comment("Jobs a Scheduler Core runs at once on its own.")
            .defineInRange("schedulerBaseThreads", 1, 1, 1024);

    public static final ModConfigSpec.IntValue THREAD_UNIT_THREADS = BUILDER
            .comment("Jobs each Thread Unit adds.")
            .defineInRange("threadUnitThreads", 1, 0, 1024);

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> FABRICATOR_CRAFT_TICKS = BUILDER
            .comment("Ticks a Fabricator takes per craft with 0, 1 and 2 Throughput Modules.")
            .defineList("fabricatorCraftTicks", List.of(20, 10, 5), () -> 20, entry -> entry instanceof Integer value && value >= 1);

    public static final ModConfigSpec.IntValue FABRICATOR_ENERGY_PER_CRAFT = BUILDER
            .comment("FE a Fabricator spends from its network per craft.")
            .defineInRange("fabricatorEnergyPerCraft", 50, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue FABRICATOR_DRAIN = BUILDER
            .comment("FE per tick a Fabricator drains while its network runs.")
            .defineInRange("fabricatorDrain", 1.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue GATEWAY_DRAIN = BUILDER
            .comment("FE per tick a Gateway drains while its network runs.")
            .defineInRange("gatewayDrain", 1.0, 0.0, 1_000.0);

    static {
        BUILDER.pop();
        BUILDER.push("reach");
    }

    public static final ModConfigSpec.IntValue RELAY_BASE_RANGE = BUILDER
            .comment("Blocks a Relay Antenna covers on its own (a sphere around it).")
            .defineInRange("relayBaseRange", 32, 0, 4096);

    public static final ModConfigSpec.IntValue RELAY_RANGE_PER_TRANSCEIVER = BUILDER
            .comment("Blocks each Optical Transceiver in a Relay Antenna adds to its range.")
            .defineInRange("relayRangePerTransceiver", 32, 0, 4096);

    public static final ModConfigSpec.DoubleValue RELAY_DRAIN = BUILDER
            .comment("FE per tick a Relay Antenna drains while its network runs.")
            .defineInRange("relayDrain", 2.0, 0.0, 1_000.0);

    public static final ModConfigSpec.IntValue HANDHELD_CAPACITY = BUILDER
            .comment("FE a Handheld Terminal's battery holds.")
            .defineInRange("handheldCapacity", 200_000, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue HANDHELD_DRAIN_PER_SECOND = BUILDER
            .comment("FE a Handheld Terminal uses each second while its screen is open.")
            .defineInRange("handheldDrainPerSecond", 10, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue HANDHELD_ENERGY_PER_ITEM = BUILDER
            .comment("FE a Handheld Terminal uses for each item it moves in or out of the network.")
            .defineInRange("handheldEnergyPerItem", 1, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue HANDHELD_CHARGE_RATE = BUILDER
            .comment("FE per tick a Handheld Terminal takes from a Capacitor Bank while held against it (use and hold), and at most",
                    "from any other charger.")
            .defineInRange("handheldChargeRate", 4_096, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue BRIDGE_LANES = BUILDER
            .comment("Lanes a linked pair of Network Bridges carries between its two networks.")
            .defineInRange("bridgeLanes", 32, 1, 1024);

    public static final ModConfigSpec.DoubleValue BRIDGE_DRAIN = BUILDER
            .comment("FE per tick each Network Bridge drains while its network runs.")
            .defineInRange("bridgeDrain", 8.0, 0.0, 1_000.0);

    public static final ModConfigSpec.BooleanValue BRIDGE_CROSS_DIMENSION = BUILDER
            .comment("Whether a pair of Network Bridges links networks in different dimensions.")
            .define("bridgeCrossDimension", true);

    public static final ModConfigSpec.BooleanValue BRIDGE_CHUNK_LOADING = BUILDER
            .comment("Whether a linked Network Bridge keeps its own chunk loaded (so its partner's network reaches it while nobody is near).")
            .define("bridgeChunkLoading", false);

    public static final ModConfigSpec.IntValue P2P_LANES = BUILDER
            .comment("Lanes a lanes Point-to-Point Link carries from its input to its outputs (split evenly between them).")
            .defineInRange("p2pLanes", 8, 1, 1024);

    public static final ModConfigSpec.DoubleValue P2P_DRAIN = BUILDER
            .comment("FE per tick each Point-to-Point Link endpoint drains while its network runs.")
            .defineInRange("p2pDrain", 0.5, 0.0, 1_000.0);

    public static final ModConfigSpec.IntValue P2P_ITEMS_PER_OPERATION = BUILDER
            .comment("Items an items Point-to-Point Link moves per operation (one every 10 ticks).")
            .defineInRange("p2pItemsPerOperation", 64, 1, 4096);

    public static final ModConfigSpec.IntValue P2P_ENERGY_PER_TICK = BUILDER
            .comment("FE per tick an energy Point-to-Point Link moves from its input to its outputs at most.")
            .defineInRange("p2pEnergyPerTick", 8_192, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue COLLECTOR_TICKS_PER_HARDNESS = BUILDER
            .comment("Ticks a Collector Plane takes to break a block, per point of the block's hardness.")
            .defineInRange("collectorTicksPerHardness", 30, 1, 1_000);

    public static final ModConfigSpec.IntValue DEPLOYER_INTERVAL = BUILDER
            .comment("Ticks between a Deployer Plane's operations.")
            .defineInRange("deployerInterval", 10, 1, 1_000);

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
        BUILDER.push("rack");
    }

    public static final ModConfigSpec.IntValue RACK_CULL_DISTANCE = BUILDER
            .comment("Blocks away beyond which a Server Rack with both doors closed doesn't draw its devices.")
            .defineInRange("rackCullDistance", 24, 0, 256);

    public static final ModConfigSpec.DoubleValue FIREWALL_DRAIN = BUILDER
            .comment("FE per tick a Firewall drains while its network runs.")
            .defineInRange("firewallDrain", 2.0, 0.0, 1_000.0);

    public static final ModConfigSpec.BooleanValue FIREWALL_FAIL_CLOSED = BUILDER
            .comment("Whether a Firewall that's offline (no lane, no power) still enforces its rules.")
            .define("firewallFailClosed", true);

    public static final ModConfigSpec.DoubleValue ROUTER_DRAIN = BUILDER
            .comment("FE per tick a Router drains while its network runs.")
            .defineInRange("routerDrain", 4.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue ROUTER_DRAIN_PER_TRANSCEIVER = BUILDER
            .comment("FE per tick each Optical Transceiver in a Router adds to its drain.")
            .defineInRange("routerDrainPerTransceiver", 1.0, 0.0, 1_000.0);

    public static final ModConfigSpec.IntValue ROUTER_BASE_RATE = BUILDER
            .comment("Items per second a Router moves, shared between its routes.")
            .defineInRange("routerBaseRate", 16, 0, 4096);

    public static final ModConfigSpec.IntValue ROUTER_RATE_PER_TRANSCEIVER = BUILDER
            .comment("Items per second each Optical Transceiver in a Router adds.")
            .defineInRange("routerRatePerTransceiver", 16, 0, 4096);

    public static final ModConfigSpec.IntValue UPS_CAPACITY = BUILDER
            .comment("FE a UPS's battery holds.")
            .defineInRange("upsCapacity", 1_000_000, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue UPS_MAX_OUTPUT = BUILDER
            .comment("FE per tick a UPS supplies at most (more is an overload).")
            .defineInRange("upsMaxOutput", 8_192, 1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue UPS_MAX_INPUT = BUILDER
            .comment("FE per tick a UPS recharges at most, from the network's surplus.")
            .defineInRange("upsMaxInput", 8_192, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue UPS_DRAIN_ONLINE = BUILDER
            .comment("FE per tick a UPS in Online mode drains while its network runs.")
            .defineInRange("upsDrainOnline", 2.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue UPS_DRAIN_STANDBY = BUILDER
            .comment("FE per tick a UPS in Standby mode drains while its network runs.")
            .defineInRange("upsDrainStandby", 1.0, 0.0, 1_000.0);

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
