/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.device.FirewallDevice;

// The Server Rack's connection points: FE in through them feeds the network, cables to the same network bond (and the
// rack sheds its lowest-priority devices when they're short, showing Degraded), and a cable from another network is a
// mismatch that carries nothing.
final class RackCablingGameTests {
    private RackCablingGameTests() {}

    private static long stored(GameTestHelper helper) {
        long id = helper.getBlockEntity(RackGameTests.CONTROLLER, NetworkControllerBlockEntity.class).getStructureId();
        NetworkSnapshot snapshot = ControllerStructures.get(helper.getLevel()).snapshot(id);
        return snapshot.stored();
    }

    // FE into the rack's roof grommet goes into its network's controllers.
    static void powerPort(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        BlockPos top = RackGeometry.partPos(master, Direction.NORTH, RackGeometry.TOP_FRONT);
        long[] before = new long[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    before[0] = stored(helper);
                    EnergyHandler port = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(top), Direction.UP);
                    helper.assertTrue(port != null, "No power port on the roof");
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(top), Direction.NORTH) == null,
                            "Power port on the front");
                    try (Transaction transaction = Transaction.openRoot()) {
                        int inserted = port.insert(1_000, transaction);
                        transaction.commit();
                        helper.assertTrue(inserted == 1_000, "Took " + inserted);
                    }
                })
                .thenIdle(1)
                .thenExecute(() -> helper.assertTrue(stored(helper) > before[0] + 900, "Network energy " + before[0] + " -> " + stored(helper)))
                .thenSucceed();
    }

    // Nine 1-lane devices over one 8-lane cable: the Low one is shed (the rest stay), and the header says Degraded. A
    // second cable into the roof bonds with the first: all of them are back.
    static void bondingAndShedding(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        List<FirewallDevice> devices = new ArrayList<>();
        for (int u = 1; u <= 9; u++) {
            devices.add(RackGameTests.install(helper, master, RackDeviceType.FIREWALL, u, FirewallDevice.class));
        }
        devices.get(4).setLanePriority(RackDevice.Priority.LOW);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(!devices.get(4).isOnline(), "Low device kept its lane");
                    helper.assertTrue(devices.stream().filter(RackDevice::isOnline).count() == 8, "Online " + devices.stream().filter(RackDevice::isOnline).count());
                    RackDeviceInfo.Header header = helper.getBlockEntity(master, RackBlockEntity.class).header();
                    helper.assertTrue(header != null && header.degraded(), "Not degraded");
                    // Up from the controller and over to the roof.
                    for (BlockPos pos : new BlockPos[] { new BlockPos(0, 2, 0), new BlockPos(0, 3, 0), new BlockPos(0, 4, 0), new BlockPos(1, 4, 0),
                            new BlockPos(2, 4, 0), new BlockPos(3, 4, 0) }) {
                        RackGameTests.cable(helper, pos);
                    }
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(devices.stream().allMatch(RackDevice::isOnline), "Not all online over two uplinks");
                    RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
                    helper.assertTrue(rack.rackLanes().activeUplinks() == 2, "Uplinks " + rack.rackLanes().activeUplinks());
                    RackDeviceInfo.Header header = rack.header();
                    helper.assertTrue(header != null && !header.degraded(), "Still degraded");
                })
                .thenSucceed();
    }

    // The rack is on the first network; a cable to another network's controller at its roof is a mismatch: it doesn't
    // join (no conflict), and the rack stays up.
    static void mismatch(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        FirewallDevice firewall = RackGameTests.install(helper, master, RackDeviceType.FIREWALL, 1, FirewallDevice.class);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(firewall.isOnline(), "Firewall offline");
                    RackGameTests.controller(helper, new BlockPos(3, 5, 0), 20_000);
                    RackGameTests.cable(helper, new BlockPos(3, 4, 0));
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
                    helper.assertTrue(rack.isMismatched(helper.getLevel(), RackGeometry.Point.TOP_FRONT), "Roof cable not a mismatch");
                    helper.assertTrue(!rack.isMismatched(helper.getLevel(), RackGeometry.Point.REAR_BOTTOM), "Own cable a mismatch");
                    helper.assertTrue(firewall.isOnline(), "Firewall offline with a mismatched cable");
                    helper.assertTrue(rack.pointState(helper.getLevel(), RackGeometry.Point.TOP_FRONT) == RackBlockEntity.PointState.MISMATCH, "Chip");
                    BlockPos top = RackGeometry.partPos(master, Direction.NORTH, RackGeometry.TOP_FRONT);
                    helper.assertTrue(helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(top), Direction.UP) == null,
                            "Power through a mismatched point");
                    helper.setBlock(new BlockPos(3, 5, 0), Blocks.AIR);
                })
                .thenSucceed();
    }

    // Work with Devices' topology: the rack, then its devices under it (top unit first, each once, though the switch pools
    // them so the rack takes no network lanes for them), then the controller beside it.
    static void deviceTopology(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        RackGameTests.install(helper, master, RackDeviceType.UPS, 1, net.zagdrath.encodedlogistics.rack.device.UpsDevice.class);
        RackGameTests.install(helper, master, RackDeviceType.NAS, 3, net.zagdrath.encodedlogistics.rack.device.NasDevice.class);
        RackGameTests.install(helper, master, RackDeviceType.L2_SWITCH_48, 5, net.zagdrath.encodedlogistics.rack.device.SwitchDevice.class);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    var rows = ControllerStructures.deviceRows(helper.getLevel().getServer(),
                            ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(master)));
                    java.util.List<String> shown = rows.stream().map(row -> row.type() + (row.rackDevice() != null ? " U" + row.rackDevice().u() : "")).toList();
                    helper.assertTrue(shown.equals(java.util.List.of("Rack", "Unit U5", "Unit U3", "Unit U1", "Controller")), "Rows " + shown);
                    helper.assertTrue(rows.get(1).depth() == 1 && rows.get(0).depth() == 0 && rows.get(4).depth() == 0, "Depths");
                })
                .thenSucceed();
    }

    // A rack controller pair: each controller listed once, in its own rack, not again at the top.
    static void deviceTopologyPair(GameTestHelper helper) {
        BlockPos a = RackGameTests.rack(helper, new BlockPos(1, 1, 0), Direction.NORTH), b = RackGameTests.rack(helper, new BlockPos(5, 1, 0), Direction.NORTH);
        for (int x = 1; x <= 5; x++) {
            RackGameTests.cable(helper, new BlockPos(x, 1, 2));
        }
        RackControllerGameTests.controller(helper, a, RackDeviceType.NETWORK_CONTROLLER_2U, 1, 100_000);
        RackControllerGameTests.controller(helper, b, RackDeviceType.NETWORK_CONTROLLER_2U, 1, 100_000);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    var rows = ControllerStructures.deviceRows(helper.getLevel().getServer(), ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(a)));
                    java.util.List<String> shown = rows.stream().map(row -> row.type()).toList();
                    helper.assertTrue(shown.equals(java.util.List.of("Rack", "Unit", "Rack", "Unit")), "Rows " + shown);
                })
                .thenSucceed();
    }
}
