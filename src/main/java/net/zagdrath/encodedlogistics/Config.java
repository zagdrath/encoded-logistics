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

    public static final ModConfigSpec.IntValue ENERGY_DRIVE_MAX_TRANSFER = BUILDER
            .comment("FE per tick an Energy Storage Drive takes in or gives out as a battery item, out of a drive holder (in one it is part of the network's energy pool, with no limit of its own).")
            .defineInRange("energyDriveMaxTransfer", 65_536, 0, Integer.MAX_VALUE);

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

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> PORT_FLUID_RATES = BUILDER
            .comment("mB of fluid or gas an Ingress or Egress Port moves per operation (one every 20 ticks), with 0, 1, 2 and 3 Throughput Modules.")
            .defineList("portFluidRates", List.of(1_000, 4_000, 8_000, 16_000), () -> 1_000, entry -> entry instanceof Integer value && value >= 0);

    public static final ModConfigSpec.DoubleValue PORT_ENERGY_PER_BUCKET = BUILDER
            .comment("FE a port spends from its network for each 1,000 mB of fluid or gas it moves.")
            .defineInRange("portEnergyPerBucket", 2.0, 0.0, 1_000.0);

    public static final ModConfigSpec.ConfigValue<List<? extends Integer>> PORT_ENERGY_RATES = BUILDER
            .comment("FE per tick an Ingress or Egress Port in Energy mode moves, with 0, 1, 2 and 3 Throughput Modules.")
            .defineList("portEnergyRates", List.of(1_024, 4_096, 16_384, 65_536), () -> 1_024, entry -> entry instanceof Integer value && value >= 0);

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

    public static final ModConfigSpec.DoubleValue CONTROL_INTERFACE_DRAIN = BUILDER
            .comment("FE per tick a Control Interface drains while its network runs.")
            .defineInRange("controlInterfaceDrain", 1.0, 0.0, 1_000.0);

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

    public static final ModConfigSpec.IntValue P2P_FLUID_PER_OPERATION = BUILDER
            .comment("mB a fluids or pressurized Point-to-Point Link moves per operation (one every 10 ticks).")
            .defineInRange("p2pFluidPerOperation", 8_000, 1, 1_000_000);

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

    public static final ModConfigSpec.BooleanValue RACK_BREAK_PROTECTION = BUILDER
            .comment("Whether breaking a Server Rack (and so pulling its devices out) needs the Firewall's rack access permission on its network.")
            .define("rackBreakProtection", true);

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

    public static final ModConfigSpec.BooleanValue ALLOW_JOB_TOASTS = BUILDER
            .comment("Whether players get a toast when a crafting job finishes, fails or is cancelled (each player chooses which in their client config).")
            .define("allowJobToasts", true);

    public static final ModConfigSpec.IntValue UPS_LOW_BATTERY_PERCENT = BUILDER
            .comment("Charge (%) below which a UPS on battery shows Low battery and sounds its rapid alarm.")
            .defineInRange("upsLowBatteryPercent", 20, 0, 100);

    public static final ModConfigSpec.BooleanValue UPS_ALARMS = BUILDER
            .comment("Whether UPSes sound their alarms on battery.")
            .define("upsAlarms", true);

    public static final ModConfigSpec.IntValue RACK_CONTROLLER_2U_ENERGY_BLOCKS = BUILDER
            .comment("Controller blocks' worth of energy buffer, receive rate and drain a 2U Network Controller has (a 4U has twice).")
            .defineInRange("rackController2uEnergyBlocks", 20, 1, 343);

    public static final ModConfigSpec.IntValue RACK_CONTROLLER_2U_LANE_FACES = BUILDER
            .comment("Connected controller faces' worth of lanes a 2U Network Controller hands out (a 4U has twice).")
            .defineInRange("rackController2uLaneFaces", 6, 1, 1024);

    public static final ModConfigSpec.DoubleValue RACK_CONTROLLER_STANDBY_FACTOR = BUILDER
            .comment("The share of its drain a standby rack Network Controller draws while idle.")
            .defineInRange("rackControllerStandbyFactor", 0.1, 0.0, 1.0);

    public static final ModConfigSpec.IntValue RACK_POWER_MAX_INPUT = BUILDER
            .comment("FE per tick a Server Rack takes in through its connection points (across all of them); -1: the same as inletMaxInput.")
            .defineInRange("rackPowerMaxInput", -1, -1, Integer.MAX_VALUE);

    public static final ModConfigSpec.IntValue L2_SWITCH_24_LANES = BUILDER
            .comment("Rack-local lanes a 24-Port L2 Switch gives the other devices in its rack.")
            .defineInRange("l2Switch24Lanes", 16, 0, 1024);

    public static final ModConfigSpec.IntValue L2_SWITCH_48_LANES = BUILDER
            .comment("Rack-local lanes a 48-Port L2 Switch gives the other devices in its rack.")
            .defineInRange("l2Switch48Lanes", 32, 0, 1024);

    public static final ModConfigSpec.IntValue L3_SWITCH_LANES = BUILDER
            .comment("Rack-local lanes an L3 Switch gives the other devices in its rack.")
            .defineInRange("l3SwitchLanes", 32, 0, 1024);

    public static final ModConfigSpec.DoubleValue SWITCH_DRAIN = BUILDER
            .comment("FE per tick an L2 Switch drains while its network runs.")
            .defineInRange("switchDrain", 2.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue L3_SWITCH_DRAIN = BUILDER
            .comment("FE per tick an L3 Switch drains while its network runs.")
            .defineInRange("l3SwitchDrain", 3.0, 0.0, 1_000.0);

    public static final ModConfigSpec.IntValue L3_SWITCH_RATE = BUILDER
            .comment("Items per second an L3 Switch routes between segments, shared between its routes.")
            .defineInRange("l3SwitchRate", 32, 0, 4096);

    public static final ModConfigSpec.IntValue COMPUTE_SERVER_THREADS = BUILDER
            .comment("Crafting threads each Compute Server gives its rack's scheduler.")
            .defineInRange("computeServerThreads", 2, 0, 1024);

    public static final ModConfigSpec.IntValue MEMORY_SERVER_MEMORY = BUILDER
            .comment("Job memory each Memory Server gives its rack's scheduler.")
            .defineInRange("memoryServerMemory", 32_768, 0, Integer.MAX_VALUE);

    public static final ModConfigSpec.DoubleValue COMPUTE_SERVER_DRAIN = BUILDER
            .comment("FE per tick a Compute Server drains while its network runs.")
            .defineInRange("computeServerDrain", 6.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue MEMORY_SERVER_DRAIN = BUILDER
            .comment("FE per tick a Memory Server drains while its network runs.")
            .defineInRange("memoryServerDrain", 3.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue FABRICATION_SERVER_DRAIN = BUILDER
            .comment("FE per tick a Fabrication Server drains while its network runs (each craft also costs fabricatorEnergyPerCraft).")
            .defineInRange("fabricationServerDrain", 4.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue MONITORING_SERVER_DRAIN = BUILDER
            .comment("FE per tick a Monitoring Server drains while its network runs.")
            .defineInRange("monitoringServerDrain", 2.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue NAS_DRAIN = BUILDER
            .comment("FE per tick a NAS drains while its network runs, on top of its drives.")
            .defineInRange("nasDrain", 2.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue SAN_DRAIN = BUILDER
            .comment("FE per tick a SAN drains while its network runs, on top of its drives.")
            .defineInRange("sanDrain", 4.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue RACK_DRIVE_DRAIN = BUILDER
            .comment("FE per tick each Storage Drive in a NAS or SAN drains.")
            .defineInRange("rackDriveDrain", 0.5, 0.0, 1_000.0);

    // Batch 3: the Rack Console, the Wireless Controller, Tape Libraries.
    public static final ModConfigSpec.DoubleValue RACK_CONSOLE_DRAIN = BUILDER
            .comment("FE per tick a Rack Console drains while its network runs.")
            .defineInRange("rackConsoleDrain", 1.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue RACK_CONSOLE_OPEN_DRAIN = BUILDER
            .comment("FE per tick a Rack Console drains on top of that while its drawer is out.")
            .defineInRange("rackConsoleOpenDrain", 2.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue WIRELESS_CONTROLLER_DRAIN = BUILDER
            .comment("FE per tick a Wireless Controller drains while its network runs.")
            .defineInRange("wirelessControllerDrain", 4.0, 0.0, 1_000.0);

    public static final ModConfigSpec.BooleanValue WIRELESS_CROSS_DIMENSION = BUILDER
            .comment("Whether Handheld Terminals linked to a Wireless Controller work in other dimensions too.")
            .define("wirelessCrossDimension", true);

    public static final ModConfigSpec.BooleanValue WIRELESS_ANY_CONTROLLER = BUILDER
            .comment("Whether any online Wireless Controller on the network serves a linked Handheld Terminal (else only the one it was linked to).")
            .define("wirelessAnyController", true);

    public static final ModConfigSpec.IntValue WIRELESS_AP_CLIENTS = BUILDER
            .comment("Wireless Bridges and Wireless Ports each online Access Point lets a network's Wireless Controller serve.")
            .defineInRange("wirelessApClients", 8, 1, 256);

    public static final ModConfigSpec.IntValue WIRELESS_BRIDGE_LANES = BUILDER
            .comment("Lanes a Wireless Bridge carries between its Wireless Controller and what's cabled to it.")
            .defineInRange("wirelessBridgeLanes", 32, 1, 1_024);

    public static final ModConfigSpec.DoubleValue WIRELESS_ENERGY_MULTIPLIER = BUILDER
            .comment("Wireless Bridges and Wireless Ports drain this many times their cabled equivalent's (Network Bridge, Ingress / Egress Port).")
            .defineInRange("wirelessEnergyMultiplier", 2.0, 0.0, 100.0);

    public static final ModConfigSpec.DoubleValue SMALL_WIRELESS_BRIDGE_DRAIN = BUILDER
            .comment("FE per tick a linked Small Wireless Bridge (on an Arcforge machine) drains from its network over the air.")
            .defineInRange("smallWirelessBridgeDrain", 1.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue MACHINE_POWER_EFFICIENCY = BUILDER
            .comment("With power from network on, the share of the FE taken from the network that reaches the machine.")
            .defineInRange("machinePowerEfficiency", 0.9, 0.01, 1.0);

    public static final ModConfigSpec.IntValue MACHINE_POWER_RATE = BUILDER
            .comment("With power from network on, the most FE per tick a machine is given.")
            .defineInRange("machinePowerRate", 1_000, 0, 1_000_000);

    public static final ModConfigSpec.DoubleValue MACHINE_POWER_RESERVE = BUILDER
            .comment("Power from network never takes a network's stored energy below this share of its capacity, so the network keeps running.")
            .defineInRange("machinePowerReserve", 0.25, 0.0, 1.0);

    public static final ModConfigSpec.DoubleValue MIDRANGE_DRAIN = BUILDER
            .comment("FE per tick a Midrange System drains while its network runs.")
            .defineInRange("midrangeDrain", 4.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue EXPANSION_CABINET_DRAIN = BUILDER
            .comment("FE per tick an attached Expansion Cabinet adds to its Midrange System's drain.")
            .defineInRange("expansionCabinetDrain", 1.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue INTEGRATED_MIDRANGE_DRAIN = BUILDER
            .comment("FE per tick an Integrated Midrange System drains while its network runs.")
            .defineInRange("integratedMidrangeDrain", 8.0, 0.0, 1_000.0);

    public static final ModConfigSpec.IntValue MIDRANGE_ENERGY_BLOCKS = BUILDER
            .comment("Controller blocks' worth of energy buffer and receive rate a Midrange System has as its network's controller.")
            .defineInRange("midrangeEnergyBlocks", 4, 1, 343);

    public static final ModConfigSpec.IntValue MIDRANGE_LANE_FACES = BUILDER
            .comment("Connected controller faces' worth of lanes a Midrange System hands out as its network's controller.")
            .defineInRange("midrangeLaneFaces", 4, 1, 1024);

    public static final ModConfigSpec.IntValue INTEGRATED_MIDRANGE_ENERGY_BLOCKS = BUILDER
            .comment("Controller blocks' worth of energy buffer and receive rate an Integrated Midrange System has (a 2U rack controller's by default).")
            .defineInRange("integratedMidrangeEnergyBlocks", 20, 1, 343);

    public static final ModConfigSpec.IntValue INTEGRATED_MIDRANGE_LANE_FACES = BUILDER
            .comment("Connected controller faces' worth of lanes an Integrated Midrange System hands out (a 2U rack controller's by default).")
            .defineInRange("integratedMidrangeLaneFaces", 6, 1, 1024);

    public static final ModConfigSpec.IntValue MIDRANGE_CRAFT_ENERGY = BUILDER
            .comment("FE a Midrange System (or Integrated Midrange System) spends per craft it runs itself.")
            .defineInRange("midrangeCraftEnergy", 20, 0, 1_000_000);

    public static final ModConfigSpec.IntValue MIDRANGE_IPL_TICKS = BUILDER
            .comment("Ticks a Midrange System takes to IPL (boot) before it runs.")
            .defineInRange("midrangeIplTicks", 100, 0, 6_000);

    public static final ModConfigSpec.IntValue MIDRANGE_STEP_TICKS = BUILDER
            .comment("Ticks a Midrange System takes for each craft it runs itself.")
            .defineInRange("midrangeStepTicks", 40, 1, 6_000);

    public static final ModConfigSpec.IntValue INTEGRATED_MIDRANGE_STEP_TICKS = BUILDER
            .comment("Ticks an Integrated Midrange System takes for each craft it runs itself.")
            .defineInRange("integratedMidrangeStepTicks", 25, 1, 6_000);

    public static final ModConfigSpec.DoubleValue DISK_DRIVE_DRAIN = BUILDER
            .comment("FE per tick a Disk Drive drains while its network runs.")
            .defineInRange("diskDriveDrain", 1.5, 0.0, 1_000.0);

    public static final ModConfigSpec.IntValue DISK_SPIN_UP_TICKS = BUILDER
            .comment("Ticks a Disk Drive spins up after a Storage Drive goes in, before the network can read it.")
            .defineInRange("diskSpinUpTicks", 40, 0, 6_000);

    public static final ModConfigSpec.IntValue DISK_SPIN_DOWN_TICKS = BUILDER
            .comment("Ticks a Disk Drive spins down before its Storage Drive comes out.")
            .defineInRange("diskSpinDownTicks", 20, 0, 6_000);

    public static final ModConfigSpec.DoubleValue TAPE_DRIVE_DRAIN = BUILDER
            .comment("FE per tick a Tape Drive drains while its network runs (twice that while reading or writing).")
            .defineInRange("tapeDriveDrain", 2.0, 0.0, 1_000.0);

    public static final ModConfigSpec.IntValue TAPE_REEL_ITEMS = BUILDER
            .comment("Items a Tape Reel holds.")
            .defineInRange("tapeReelItems", 65_536, 8, 1 << 30);

    public static final ModConfigSpec.IntValue TAPE_REEL_TYPES = BUILDER
            .comment("Item types a Tape Reel holds.")
            .defineInRange("tapeReelTypes", 1_024, 1, 65_536);

    public static final ModConfigSpec.IntValue TAPE_LOAD_TICKS = BUILDER
            .comment("Ticks a Tape Drive takes to thread a reel mounted on it.")
            .defineInRange("tapeLoadTicks", 100, 0, 6_000);

    public static final ModConfigSpec.IntValue TAPE_REWIND_TICKS = BUILDER
            .comment("Ticks a Tape Drive takes to rewind its reel (before unloading it, or on 7=Rewind).")
            .defineInRange("tapeRewindTicks", 40, 0, 6_000);

    public static final ModConfigSpec.IntValue TAPE_DRIVE_BASE_TICKS = BUILDER
            .comment("Ticks a Tape Drive takes for each read or write, plus tapeDriveTicksPer4k for each 4,096 items.")
            .defineInRange("tapeDriveBaseTicks", 60, 1, 72_000);

    public static final ModConfigSpec.IntValue TAPE_DRIVE_TICKS_PER_4K = BUILDER
            .comment("Ticks a Tape Drive adds to a read or write for each 4,096 items.")
            .defineInRange("tapeDriveTicksPer4k", 20, 0, 72_000);

    public static final ModConfigSpec.IntValue DISPLAY_MAX_WIDTH = BUILDER
            .comment("The widest a Display Panel screen merges, in panels.")
            .defineInRange("displayMaxWidth", 8, 1, 16);

    public static final ModConfigSpec.IntValue DISPLAY_MAX_HEIGHT = BUILDER
            .comment("The tallest a Display Panel screen merges, in panels.")
            .defineInRange("displayMaxHeight", 6, 1, 16);

    public static final ModConfigSpec.DoubleValue DISPLAY_PANEL_DRAIN = BUILDER
            .comment("FE/t each Display Panel of a screen draws from its network.")
            .defineInRange("displayPanelDrain", 0.5, 0, 1_000);

    public static final ModConfigSpec.IntValue DISPLAY_HISTORY_SECONDS = BUILDER
            .comment("Seconds of graph history a Display Panel keeps itself (one sample a second, while loaded) for graphs no Monitoring Server serves.")
            .defineInRange("displayHistorySeconds", 600, 10, 3_600);

    public enum ImagesAllowed { AUTO, TRUE, FALSE }

    public static final ModConfigSpec.EnumValue<ImagesAllowed> ALLOW_IMAGES = BUILDER
            .comment("Whether Display Panels show images from <world>/encodedlogistics/images/<SYSNAME>/: AUTO is on in single-player and off on dedicated servers.")
            .defineEnum("allowImages", ImagesAllowed.AUTO);

    public static final ModConfigSpec.IntValue DISPLAY_MAX_IMAGE_SIZE = BUILDER
            .comment("The largest source image a Display Panel takes, in pixels each way.")
            .defineInRange("displayMaxImageSize", 4_096, 16, 16_384);

    public static final ModConfigSpec.IntValue DISPLAY_MAX_IMAGE_BYTES = BUILDER
            .comment("The largest source image file a Display Panel takes, in MB.")
            .defineInRange("displayMaxImageBytes", 4, 1, 64);

    public static final ModConfigSpec.ConfigValue<String> DISPLAY_IMAGE_COLORS = BUILDER
            .comment("The most colours a player may pick for an image on a Display Panel: 16, 64, 256 or FULL.")
            .define("displayImageColors", "256", value -> value instanceof String text && java.util.List.of("16", "64", "256", "FULL").contains(text));

    public static final ModConfigSpec.DoubleValue ACCESS_POINT_DRAIN = BUILDER
            .comment("FE per tick an Access Point drains while its network runs.")
            .defineInRange("accessPointDrain", 2.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue TAPE_LIBRARY_4U_DRAIN = BUILDER
            .comment("FE per tick a 4U Tape Library drains while its network runs, idle.")
            .defineInRange("tapeLibrary4uDrain", 3.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue TAPE_LIBRARY_6U_DRAIN = BUILDER
            .comment("FE per tick a 6U Tape Library drains while its network runs, idle.")
            .defineInRange("tapeLibrary6uDrain", 4.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue TAPE_DRIVE_BUSY_DRAIN = BUILDER
            .comment("FE per tick a Tape Library drains for each of its drives reading or writing.")
            .defineInRange("tapeDriveBusyDrain", 8.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue TAPE_PICKER_DRAIN = BUILDER
            .comment("FE per tick a Tape Library drains while its picker moves.")
            .defineInRange("tapePickerDrain", 4.0, 0.0, 1_000.0);

    public static final ModConfigSpec.IntValue TAPE_BASE_TICKS = BUILDER
            .comment("Ticks an LTO-6 tape takes to read or write, before the items (each newer generation is 15% faster).")
            .defineInRange("tapeBaseTicks", 40, 1, 72_000);

    public static final ModConfigSpec.IntValue TAPE_TICKS_PER_4K = BUILDER
            .comment("Ticks an LTO-6 tape takes to read or write each 4,096 items.")
            .defineInRange("tapeTicksPer4k", 20, 0, 72_000);

    public static final ModConfigSpec.IntValue TAPE_ARCHIVE_INTERVAL = BUILDER
            .comment("Ticks between a Tape Library's looks for items to archive.")
            .defineInRange("tapeArchiveInterval", 200, 20, 72_000);

    public static final ModConfigSpec.IntValue TAPE_DEFAULT_AGE_HOURS = BUILDER
            .comment("A new Tape Library's archive age: hours (game time) an item goes untouched before it's archived.")
            .defineInRange("tapeDefaultAgeHours", 2, 1, 9_999);

    public static final ModConfigSpec.DoubleValue TERMINAL_DESK_DRAIN = BUILDER
            .comment("FE per tick a Terminal Desk drains while its network runs.")
            .defineInRange("terminalDeskDrain", 1.0, 0.0, 1_000.0);

    public static final ModConfigSpec.DoubleValue TERMINAL_DESK_SCREEN_DRAIN = BUILDER
            .comment("FE per tick a Terminal Desk drains on top of that while its screen is on (whenever it's online).")
            .defineInRange("terminalDeskScreenDrain", 2.0, 0.0, 1_000.0);

    public static final ModConfigSpec.IntValue TAPE_DEFAULT_HOT_PERCENT = BUILDER
            .comment("A new Tape Library's free-space trigger: it only archives while hot storage is fuller than this (%).")
            .defineInRange("tapeDefaultHotPercent", 80, 0, 100);

    static {
        BUILDER.pop();
        BUILDER.push("elcl");
    }

    // The Terminal OS and ELCL (docs/elcl).
    public static final ModConfigSpec.IntValue ELCL_SPOOLED_FILE_CAP = BUILDER
            .comment("Spooled files a system keeps; past this the oldest is removed.")
            .defineInRange("spooledFileCap", 200, 1, 100_000);

    public static final ModConfigSpec.IntValue ELCL_MESSAGE_CAP = BUILDER
            .comment("Messages each user's message queue keeps; past this the oldest is removed.")
            .defineInRange("messageCap", 500, 1, 100_000);

    public static final ModConfigSpec.IntValue ELCL_CHARS_PER_STORAGE_BYTE = BUILDER
            .comment("Characters of source member text that take one byte of the network's drive storage (rounded up per member).")
            .defineInRange("charsPerStorageByte", 64, 1, 1_000_000);

    public static final ModConfigSpec.BooleanValue ELCL_MESSAGE_CHAT_NOTICE = BUILDER
            .comment("Whether SNDMSG also tells online recipients in chat that a message is waiting.")
            .define("messageChatNotice", true);

    public static final ModConfigSpec.IntValue ELCL_INTERACTIVE_BUDGET = BUILDER
            .comment("VM instructions an interactive job runs per tick before it yields.")
            .defineInRange("interactiveBudget", 200, 1, 1_000_000);

    public static final ModConfigSpec.IntValue ELCL_BATCH_BUDGET = BUILDER
            .comment("VM instructions each batch job runs per tick before it yields.")
            .defineInRange("batchBudget", 100, 1, 1_000_000);

    public static final ModConfigSpec.IntValue ELCL_GLOBAL_BUDGET = BUILDER
            .comment("VM instructions all jobs on the server run per tick, together.")
            .defineInRange("globalBudget", 2_000, 1, 10_000_000);

    public static final ModConfigSpec.IntValue ELCL_MAX_SOURCE_LINES = BUILDER
            .comment("Lines a source member may have.")
            .defineInRange("maxSourceLines", 5_000, 1, 100_000);

    public static final ModConfigSpec.IntValue ELCL_MAX_LIST_SIZE = BUILDER
            .comment("Elements a *LIST variable may hold.")
            .defineInRange("maxListSize", 4_096, 1, 1_000_000);

    public static final ModConfigSpec.IntValue ELCL_COMPUTE_SERVER_JOBS = BUILDER
            .comment("Batch jobs each Compute Server runs at once.")
            .defineInRange("computeServerJobs", 4, 0, 64);

    public static final ModConfigSpec.IntValue ELCL_DISKETTE_BYTES = BUILDER
            .comment("Bytes of library source an 8\" Diskette holds (SAVLIB).")
            .defineInRange("disketteBytes", 65_536, 1, 16_777_216);

    public static final ModConfigSpec.IntValue ELCL_CRAFT_LOG_RETENTION = BUILDER
            .comment("Ended crafting jobs each network's history keeps by default (the CRFLOGRTN system value's default); past this the oldest is removed.")
            .defineInRange("craftLogRetention", 200, 0, 999);

    public static final ModConfigSpec.IntValue ELCL_MAX_RECORDS_PER_FILE = BUILDER
            .comment("Records a physical file (CRTPF) may hold; a write past this is refused (ELC2208).")
            .defineInRange("maxRecordsPerFile", 10_000, 1, 1_000_000);

    public enum FolderSync { AUTO, TRUE, FALSE }

    public static final ModConfigSpec.EnumValue<FolderSync> ELCL_ALLOW_FOLDER_SYNC = BUILDER
            .comment("Whether libraries sync with <world>/encodedlogistics/libraries/: AUTO is on in single-player and off on dedicated servers.")
            .defineEnum("allowFolderSync", FolderSync.AUTO);

    static {
        BUILDER.pop();
        BUILDER.push("plc");
    }

    // Programmable Logic Controllers (docs/plc).
    public static final ModConfigSpec.IntValue PLC_ENERGY = BUILDER
            .comment("FE per tick a PLC uses while it's powered (from its own buffer, or from its network when cabled to one).")
            .defineInRange("plcEnergy", 2, 0, 1_000);

    public static final ModConfigSpec.IntValue PLC_BUFFER = BUILDER
            .comment("FE a PLC's own buffer holds (filled from any face).")
            .defineInRange("plcBuffer", 1_000, 1, 1_000_000);

    public static final ModConfigSpec.IntValue PLC_INSTRUCTIONS_PER_TICK = BUILDER
            .comment("VM instructions a PLC runs per tick; a scan that needs more carries on next tick.")
            .defineInRange("plcInstructionsPerTick", 50, 1, 100_000);

    public static final ModConfigSpec.IntValue PLC_MAX_PER_CHUNK = BUILDER
            .comment("PLCs that may run in one chunk; another stays in STOP (PLC limit reached).")
            .defineInRange("plcMaxPerChunk", 16, 1, 4_096);

    public static final ModConfigSpec.IntValue PLC_MAX_PER_SERVER = BUILDER
            .comment("PLCs that may run on the server at once; another stays in STOP (PLC limit reached).")
            .defineInRange("plcMaxPerServer", 512, 1, 1_000_000);

    public enum PlcFaultOutputs { HOLD, ZERO }

    public static final ModConfigSpec.EnumValue<PlcFaultOutputs> PLC_FAULT_OUTPUTS = BUILDER
            .comment("What a PLC's outputs do on FAULT: HOLD keeps their last levels, ZERO drops them to 0.")
            .defineEnum("plcFaultOutputs", PlcFaultOutputs.HOLD);

    public static final ModConfigSpec.BooleanValue PLC_OPEN_TO_ANYONE = BUILDER
            .comment("Whether anyone may open a PLC that isn't cabled to a network (false: only the player who placed it, and operators).")
            .define("plcOpenToAnyone", true);

    static {
        BUILDER.pop();
        BUILDER.push("signals");
    }

    // Cage Lights, Alarm Strobes and Speakers (docs/signals).
    public static final ModConfigSpec.DoubleValue SIGNAL_DEVICE_DRAIN = BUILDER
            .comment("FE per tick a Cage Light, Alarm Strobe or Speaker drains from its network while it's cabled to one.")
            .defineInRange("signalDeviceDrain", 0.25, 0.0, 1_000.0);

    public static final ModConfigSpec.IntValue SIREN_MAX_RANGE = BUILDER
            .comment("The farthest an Alarm Strobe's siren can be set to carry, in blocks.")
            .defineInRange("sirenMaxRange", 96, 8, 256);

    public static final ModConfigSpec.IntValue SPEAKER_MAX_RANGE = BUILDER
            .comment("The farthest a Speaker can be set to carry, in blocks.")
            .defineInRange("speakerMaxRange", 64, 8, 256);

    public static final ModConfigSpec.IntValue AUDIO_MAX_BYTES = BUILDER
            .comment("The largest audio file (or web audio) a Speaker plays, in MB.")
            .defineInRange("audioMaxBytes", 4, 1, 64);

    public static final ModConfigSpec.IntValue AUDIO_MAX_SECONDS = BUILDER
            .comment("The longest audio file (or web audio) a Speaker plays, in seconds.")
            .defineInRange("audioMaxSeconds", 180, 1, 3_600);

    public enum WebAudioAllowed { AUTO, TRUE, FALSE }

    public static final ModConfigSpec.EnumValue<WebAudioAllowed> ALLOW_WEB_AUDIO = BUILDER
            .comment("Whether Speakers may play audio from web URLs (fetched by each listening player's own game): AUTO is on in single-player and off on dedicated servers.")
            .defineEnum("allowWebAudio", WebAudioAllowed.AUTO);

    public static final ModConfigSpec.ConfigValue<List<? extends String>> WEB_AUDIO_HOSTS = BUILDER
            .comment("The hosts Speakers may play web audio from (example.com also allows its subdomains); empty: none.")
            .defineListAllowEmpty("webAudioHosts", List.of(), () -> "example.com", entry -> entry instanceof String host && !host.isBlank());

    static {
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();
}
