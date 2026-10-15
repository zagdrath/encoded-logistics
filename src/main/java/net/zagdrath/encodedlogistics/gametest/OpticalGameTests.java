/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.zagdrath.encodedlogistics.block.OpticalAmplifierBlock;
import net.zagdrath.encodedlogistics.block.OpticalMultiplexerBlock;
import net.zagdrath.encodedlogistics.block.cable.CableColor;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.OpticalMultiplexerBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.elcl.exec.ElclEvents;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkGraph;
import net.zagdrath.encodedlogistics.network.NetworkLink;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.optical.Multiplexer;
import net.zagdrath.encodedlogistics.optical.SpanLoss;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisLayout;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisPart;
import net.zagdrath.encodedlogistics.rack.device.BladeEnclosureDevice;
import net.zagdrath.encodedlogistics.rack.device.ChassisSwitchDevice;
import net.zagdrath.encodedlogistics.rack.device.NasDevice;
import net.zagdrath.encodedlogistics.rack.device.OpticalTransportShelfDevice;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// Optical gear and the modular chassis (docs/optical): span loss, a multiplexer pair's channel, a Chassis Switch's
// supervisor failover and power, a Blade Enclosure's blades, a Transport Shelf's 1+1 protection.
final class OpticalGameTests {
    private OpticalGameTests() {}

    private static ItemStack part(ChassisPart part) {
        return new ItemStack(ModItems.chassisPart(part).get());
    }

    private static void fiber(GameTestHelper helper, BlockPos pos, CableColor color) {
        NetworkCableBlock block = ModBlocks.cable(CableTier.FIBER, color).get();
        helper.setBlock(pos, block.withConnections(block.defaultBlockState(), helper.getLevel(), helper.absolutePos(pos)));
    }

    private static GlobalPos at(GameTestHelper helper, BlockPos pos) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(pos));
    }

    // A 300-block Fiber Cable run between two devices loses a lane (256 + 32); a powered amplifier in its middle splits it
    // into two spans that lose none. Worked out on graphs alone.
    static void spanLoss(GameTestHelper helper) {
        ResourceKey<Level> dimension = helper.getLevel().dimension();
        NetworkGraph plain = run(dimension, 300, -1);
        Map<NodePos, SpanLoss.Span> spans = SpanLoss.apply(plain);
        NetworkLink middle = plain.link(NodePos.of(dimension, new BlockPos(150, 0, 0)), Direction.EAST);
        helper.assertTrue(middle != null && middle.capacity() == SpanLoss.fiberLanes() - 1, "Span of 300 carries " + (middle != null ? middle.capacity() : -1));
        SpanLoss.Span span = spans.get(NodePos.of(dimension, new BlockPos(10, 0, 0)));
        helper.assertTrue(span != null && span.length() == 300 && span.lost() == 1, "Span " + span);
        NetworkGraph amplified = run(dimension, 300, 150);
        SpanLoss.apply(amplified);
        NetworkLink after = amplified.link(NodePos.of(dimension, new BlockPos(200, 0, 0)), Direction.EAST);
        helper.assertTrue(after != null && after.capacity() == SpanLoss.fiberLanes(), "Amplified span carries " + (after != null ? after.capacity() : -1));
        helper.assertTrue(SpanLoss.lanes(1_000, 32) == 32 - (1_000 - 256) / 32 && SpanLoss.lanes(100_000, 32) == 1, "Loss floor");
        helper.succeed();
    }

    // Devices at x 0 and length + 1, Fiber Cable between (an amplifier at amplifierAt, if not -1).
    private static NetworkGraph run(ResourceKey<Level> dimension, int length, int amplifierAt) {
        NetworkGraph graph = new NetworkGraph();
        graph.addNode(dimension, new DeviceNode(BlockPos.ZERO, EnumSet.of(Direction.EAST), 1, 0, List.of(), true));
        for (int x = 1; x <= length; x++) {
            BlockPos pos = new BlockPos(x, 0, 0);
            NetworkNode node = x == amplifierAt ? new OpticalAmplifierBlock.AmplifierNode(pos, EnumSet.of(Direction.EAST, Direction.WEST), true)
                    : new NetworkCableBlock.CableNode(pos, EnumSet.of(Direction.EAST, Direction.WEST), SpanLoss.fiberLanes(), 0, true);
            graph.addNode(dimension, node);
        }
        graph.addNode(dimension, new DeviceNode(new BlockPos(length + 1, 0, 0), EnumSet.of(Direction.WEST), 1, 0, List.of(), true));
        for (int x = 0; x <= length; x++) {
            graph.connect(NodePos.of(dimension, new BlockPos(x, 0, 0)), Direction.EAST);
        }
        return graph;
    }

    // Two Optical Multiplexer blocks, each powered by its own network, with a Fiber Cable line between their ports.
    // Paired, and channel 1 given network X (a controller's) at one end and a lone cable with a Drive Bay at the other:
    // the channel joins them, so the Drive Bay comes online on X - and only through the channel.
    static void multiplexerChannel(GameTestHelper helper) {
        BlockPos muxA = new BlockPos(1, 1, 0), muxB = new BlockPos(8, 1, 0);
        RackGameTests.controller(helper, new BlockPos(0, 1, 0), 20_000);
        RackGameTests.controller(helper, new BlockPos(9, 1, 0), 20_000);
        helper.setBlock(muxA, ModBlocks.OPTICAL_MULTIPLEXER.get().defaultBlockState().setValue(OpticalMultiplexerBlock.FACING, Direction.EAST));
        helper.setBlock(muxB, ModBlocks.OPTICAL_MULTIPLEXER.get().defaultBlockState().setValue(OpticalMultiplexerBlock.FACING, Direction.WEST));
        for (int x = 2; x <= 7; x++) {
            fiber(helper, new BlockPos(x, 1, 0), CableColor.NEUTRAL);
        }
        // Network X and the far side's lone cable and bay.
        BlockPos clientX = new BlockPos(1, 1, 3), clientY = new BlockPos(8, 1, 3), bayY = new BlockPos(8, 1, 4);
        RackGameTests.controller(helper, new BlockPos(0, 1, 3), 20_000);
        RackGameTests.cable(helper, clientX);
        RackGameTests.cable(helper, clientY);
        RackGameTests.driveBay(helper, bayY);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    Multiplexer a = helper.getBlockEntity(muxA, OpticalMultiplexerBlockEntity.class).multiplexer();
                    Multiplexer b = helper.getBlockEntity(muxB, OpticalMultiplexerBlockEntity.class).multiplexer();
                    helper.assertTrue(!ControllerStructures.get(helper.getLevel()).isDeviceOnline(helper.getLevel(), helper.absolutePos(bayY)),
                            "The far bay is online before the channel");
                    helper.assertTrue(Multiplexer.pair(helper.getLevel().getServer(), a, b), "Not paired");
                    helper.assertTrue(a.assign(at(helper, clientX)) == 0 && b.assign(at(helper, clientY)) == 0, "Channels not given");
                })
                .thenWaitUntil(() -> {
                    Multiplexer a = helper.getBlockEntity(muxA, OpticalMultiplexerBlockEntity.class).multiplexer();
                    helper.assertTrue(a.lineState() == Multiplexer.LineState.UP && a.up(0), "Line " + a.lineState() + ", channel up " + a.up(0));
                    helper.assertTrue(a.trace() != null && a.trace().length() == 6 && a.channelLanes() == SpanLoss.fiberLanes(),
                            "Line length " + (a.trace() != null ? a.trace().length() : -1));
                    helper.assertTrue(ControllerStructures.get(helper.getLevel()).isDeviceOnline(helper.getLevel(), helper.absolutePos(bayY)),
                            "The far bay isn't online through the channel");
                })
                .thenExecute(() -> {
                    // The line cut: the channel goes, and the bay with it.
                    helper.setBlock(new BlockPos(4, 1, 0), Blocks.AIR);
                })
                .thenWaitUntil(() -> {
                    Multiplexer a = helper.getBlockEntity(muxA, OpticalMultiplexerBlockEntity.class).multiplexer();
                    helper.assertTrue(a.lineState() == Multiplexer.LineState.NO_LINE && a.fault(), "Line " + a.lineState());
                    helper.assertTrue(!ControllerStructures.get(helper.getLevel()).isDeviceOnline(helper.getLevel(), helper.absolutePos(bayY)),
                            "The far bay stays online with the line cut");
                })
                .thenSucceed();
    }

    // A Chassis Switch with both supervisors, two power supplies, an Access Card and an Uplink Card with a transceiver
    // pools a NAS in its rack (48 lanes). Supervisor A taken out: B takes over in the same moment (*SUPFAILOVER), the pool
    // stays. A power supply out: degraded.
    static void chassisSwitch(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        ChassisSwitchDevice chassis = RackGameTests.install(helper, master, RackDeviceType.CHASSIS_SWITCH, 1, ChassisSwitchDevice.class);
        NasDevice nas = Rack2GameTests.storageDevice(helper, master, RackDeviceType.NAS, 11, NasDevice.class, 0, 0);
        chassis.items().set(0, part(ChassisPart.SUPERVISOR_CARD));
        chassis.items().set(1, part(ChassisPart.SUPERVISOR_CARD));
        chassis.items().set(2, part(ChassisPart.ACCESS_CARD));
        chassis.items().set(3, part(ChassisPart.UPLINK_CARD));
        chassis.items().set(ChassisLayout.SWITCH_CAGES_FROM + ChassisLayout.CAGES_PER_CARD, new ItemStack(ModItems.OPTICAL_TRANSCEIVER.get()));
        chassis.items().set(10, part(ChassisPart.CHASSIS_PSU));
        chassis.items().set(11, part(ChassisPart.CHASSIS_PSU));
        chassis.itemsChanged();
        int[] events = new int[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
                    helper.assertTrue(chassis.isOnline() && chassis.running(), "Chassis online " + chassis.isOnline() + ", running " + chassis.running());
                    helper.assertTrue(chassis.capacity() == 48 && rack.lanes().isPooled(nas), "Pool " + chassis.capacity() + ", NAS pooled "
                            + rack.lanes().isPooled(nas));
                    helper.assertTrue(chassis.activeSupervisor() == 0 && chassis.status() == RackDeviceInfo.Status.ONLINE, "Supervisor "
                            + chassis.activeSupervisor() + ", " + chassis.status());
                })
                .thenExecute(() -> {
                    events[0] = ElclEvents.recent().size();
                    chassis.items().set(0, ItemStack.EMPTY);
                    chassis.itemsChanged();
                    helper.assertTrue(chassis.activeSupervisor() == 1 && chassis.running(), "No failover to B");
                    helper.assertTrue(ElclEvents.recent().stream().skip(events[0]).anyMatch(event -> event.event().equals("*SUPFAILOVER")), "No *SUPFAILOVER");
                    // A degraded chassis: one supervisor.
                    helper.assertTrue(chassis.status() == RackDeviceInfo.Status.WARNING, "Not degraded on one supervisor");
                    chassis.items().set(0, part(ChassisPart.SUPERVISOR_CARD));
                    chassis.items().set(11, ItemStack.EMPTY);
                    chassis.itemsChanged();
                    helper.assertTrue(chassis.powerSupplies() == 1 && chassis.status() == RackDeviceInfo.Status.WARNING
                            && RackDeviceType.DEGRADED.equals(chassis.modelVariant()), "Not degraded on one power supply");
                })
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(master, RackBlockEntity.class).lanes().isPooled(nas), "Pool lost"))
                .thenSucceed();
    }

    // A Blade Enclosure with two Compute Blades, a Memory Blade and a Storage Blade (a drive behind it), an interconnect,
    // a management module and a power supply: its network's rack Scheduler has the blades' threads and memory, and the
    // drive is network storage; a drive behind an empty slot isn't.
    static void bladeEnclosure(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        BladeEnclosureDevice blades = RackGameTests.install(helper, master, RackDeviceType.BLADE_ENCLOSURE, 1, BladeEnclosureDevice.class);
        int front = ChassisLayout.BLADE_BAYS_FROM;
        blades.items().set(front, part(ChassisPart.COMPUTE_BLADE));
        blades.items().set(front + 1, part(ChassisPart.COMPUTE_BLADE));
        blades.items().set(front + 2, part(ChassisPart.MEMORY_BLADE));
        blades.items().set(front + 3, part(ChassisPart.STORAGE_BLADE));
        blades.items().set(front + 16, part(ChassisPart.INTERCONNECT_MODULE));
        blades.items().set(front + 18, part(ChassisPart.MANAGEMENT_MODULE));
        blades.items().set(front + 20, part(ChassisPart.BLADE_PSU));
        // The Storage Blade (slot 3) opens drive bays 6 and 7; bay 0 has no blade in front.
        blades.items().set(6, new ItemStack(ModItems.storageDrive(StorageTier.K8).get()));
        blades.items().set(0, new ItemStack(ModItems.storageDrive(StorageTier.K8).get()));
        blades.itemsChanged();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
                    helper.assertTrue(blades.isOnline() && blades.running(), "Enclosure online " + blades.isOnline());
                    int threads = (int) Math.ceil(2 * BladeEnclosureDevice.threadsPerBlade() - 1.0E-9);
                    helper.assertTrue(blades.schedulerThreads() == threads && blades.schedulerMemory() == BladeEnclosureDevice.memoryPerBlade(),
                            "Threads " + blades.schedulerThreads() + ", memory " + blades.schedulerMemory());
                    helper.assertTrue(rack.scheduler().active() && rack.scheduler().threads() == threads, "Scheduler " + rack.scheduler().active());
                    helper.assertTrue(blades.views(helper.getLevel().getServer()).size() == 1, "Storage views " + blades.views(helper.getLevel().getServer()).size());
                    helper.assertTrue(blades.status() == RackDeviceInfo.Status.WARNING, "Not degraded on one interconnect");
                })
                .thenSucceed();
    }

    // Two racks with Transport Shelves, one network over two Fiber Cable runs between them (red, the shorter, and blue):
    // red is the working path, blue the protect path. Red cut: onto protect (*PROTSWITCH); restored: back (revertive).
    static void shelfProtection(GameTestHelper helper) {
        BlockPos masterA = RackGameTests.networkedRack(helper);
        BlockPos masterB = RackGameTests.rack(helper, new BlockPos(9, 1, 0), Direction.NORTH);
        // Red: rack A's rear middle to rack B's, 7 blocks. Blue: their rear tops, round by z 3, 9 blocks.
        for (int x = 3; x <= 9; x++) {
            fiber(helper, new BlockPos(x, 2, 2), CableColor.RED);
        }
        for (BlockPos pos : new BlockPos[] { new BlockPos(3, 3, 2), new BlockPos(3, 3, 3), new BlockPos(4, 3, 3), new BlockPos(5, 3, 3),
                new BlockPos(6, 3, 3), new BlockPos(7, 3, 3), new BlockPos(8, 3, 3), new BlockPos(9, 3, 3), new BlockPos(9, 3, 2) }) {
            fiber(helper, pos, CableColor.BLUE);
        }
        OpticalTransportShelfDevice a = RackGameTests.install(helper, masterA, RackDeviceType.OPTICAL_TRANSPORT_SHELF, 1, OpticalTransportShelfDevice.class);
        OpticalTransportShelfDevice b = RackGameTests.install(helper, masterB, RackDeviceType.OPTICAL_TRANSPORT_SHELF, 1, OpticalTransportShelfDevice.class);
        for (OpticalTransportShelfDevice shelf : List.of(a, b)) {
            shelf.items().set(0, part(ChassisPart.CONTROLLER_CARD));
            shelf.items().set(1, part(ChassisPart.CONTROLLER_CARD));
            shelf.items().set(2, part(ChassisPart.PROTECTION_CARD));
            shelf.itemsChanged();
        }
        BlockPos cut = new BlockPos(6, 2, 2);
        int[] events = new int[1];
        helper.startSequence()
                .thenExecute(() -> helper.assertTrue(OpticalTransportShelfDevice.pair(a, b), "Not paired"))
                .thenWaitUntil(() -> {
                    helper.assertTrue(a.isOnline() && b.isOnline(), "Shelves online " + a.isOnline() + ", " + b.isOnline());
                    helper.assertTrue(a.path(OpticalTransportShelfDevice.Path.WORKING) != null && a.path(OpticalTransportShelfDevice.Path.PROTECT) != null,
                            "Paths " + a.path(OpticalTransportShelfDevice.Path.WORKING) + ", " + a.path(OpticalTransportShelfDevice.Path.PROTECT));
                    helper.assertTrue(a.path(OpticalTransportShelfDevice.Path.WORKING).length() == 7 && a.activePath() == OpticalTransportShelfDevice.Path.WORKING,
                            "Working path " + a.path(OpticalTransportShelfDevice.Path.WORKING).length() + ", active " + a.activePath());
                    helper.assertTrue(a.status() == RackDeviceInfo.Status.ONLINE && a.quality(a.activeTrace()) == 100, "Shelf " + a.status());
                })
                .thenExecute(() -> {
                    events[0] = ElclEvents.recent().size();
                    helper.setBlock(cut, Blocks.AIR);
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(a.activePath() == OpticalTransportShelfDevice.Path.PROTECT && a.onProtect(), "Not on protect: " + a.activePath());
                    helper.assertTrue(ElclEvents.recent().stream().skip(events[0]).anyMatch(event -> event.event().equals("*PROTSWITCH")), "No *PROTSWITCH");
                    helper.assertTrue(a.status() == RackDeviceInfo.Status.WARNING, "Not shown on protect");
                })
                .thenExecute(() -> fiber(helper, cut, CableColor.RED))
                .thenWaitUntil(() -> helper.assertTrue(a.activePath() == OpticalTransportShelfDevice.Path.WORKING, "Not back on working: " + a.activePath()))
                .thenSucceed();
    }
}
