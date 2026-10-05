/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.zagdrath.encodedlogistics.block.AccessPointBlock;
import net.zagdrath.encodedlogistics.block.WirelessPortBlock;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.WirelessBridgeBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.WirelessPortBlockEntity;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.device.WirelessControllerDevice;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.wireless.Wireless;

// Wireless on RackGameTests' networked rack, with a Wireless Controller in it, a Drive Bay at BAY and an Access Point
// cabled at AP: a Wireless Ingress Port on a chest (no cable) linked to the controller moves the chest into the network;
// past the Access Point's slots the most recently linked are over capacity; with no Access Point the controller faults
// and its clients drop off. A Wireless Bridge brings a Drive Bay cabled to it onto the controller's network, and goes
// again when unlinked. The links survive the rack and the port being saved and loaded.
final class WirelessGameTests {
    private static final BlockPos BAY = new BlockPos(1, 2, 1), AP = new BlockPos(1, 2, 2), CHEST = new BlockPos(5, 1, 4), PORT = new BlockPos(5, 1, 3),
            BRIDGE = new BlockPos(6, 1, 6), REMOTE_BAY = new BlockPos(7, 1, 6);
    private static final StorageKey COBBLESTONE = StorageKey.of(new ItemStack(Items.COBBLESTONE));

    private WirelessGameTests() {}

    // The rig: the controller in unit 1, the Drive Bay, the Access Point (puck up, its bottom on the cable) and the
    // port on its chest. Returns the rack's master.
    private static BlockPos rig(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        RackGameTests.install(helper, master, RackDeviceType.WIRELESS_CONTROLLER, 1, WirelessControllerDevice.class);
        RackGameTests.driveBay(helper, BAY);
        helper.setBlock(AP, ModBlocks.ACCESS_POINT.get().defaultBlockState().setValue(AccessPointBlock.FACING, Direction.UP));
        helper.setBlock(CHEST, Blocks.CHEST);
        helper.setBlock(PORT, ModBlocks.WIRELESS_INGRESS_PORT.get().defaultBlockState().setValue(WirelessPortBlock.FACING, Direction.SOUTH));
        return master;
    }

    private static WirelessControllerDevice controller(GameTestHelper helper, BlockPos master) {
        return (WirelessControllerDevice) helper.getBlockEntity(master, RackBlockEntity.class).deviceAt(1);
    }

    private static NetworkRef network(GameTestHelper helper, BlockPos master) {
        NetworkRef network = ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(master));
        helper.assertTrue(network != null, "Rack not on a network");
        return network;
    }

    private static Wireless.Problem problem(GameTestHelper helper, BlockPos pos) {
        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
        helper.assertTrue(entity instanceof net.zagdrath.encodedlogistics.wireless.WirelessClient, "No wireless client at " + pos);
        return Wireless.problem(helper.getLevel().getServer(), (net.zagdrath.encodedlogistics.wireless.WirelessClient) entity);
    }

    // The port moves its chest into the network over the air; the Access Point's slots fill in link order; the
    // devices get their names; with the Access Point gone the controller faults and its clients go offline.
    static void portCapacityAndFault(GameTestHelper helper) {
        BlockPos master = rig(helper);
        // Eight more clients (Wireless Egress Ports: a lane and a small drain each), for nine in all.
        List<BlockPos> others = new java.util.ArrayList<>();
        for (int y = 1; y <= 8; y++) {
            others.add(new BlockPos(6, y, 0));
        }
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    WirelessControllerDevice controller = controller(helper, master);
                    helper.assertTrue(controller.isOnline(), "Controller offline");
                    helper.getBlockEntity(CHEST, ChestBlockEntity.class).setItem(0, new ItemStack(Items.COBBLESTONE, 10));
                    helper.assertTrue(controller.link(helper.getBlockEntity(PORT, WirelessPortBlockEntity.class)), "Port not linked");
                    for (BlockPos pos : others) {
                        helper.setBlock(pos, ModBlocks.WIRELESS_EGRESS_PORT.get());
                    }
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    for (BlockPos pos : others) {
                        helper.assertTrue(controller(helper, master).link(helper.getBlockEntity(pos, WirelessPortBlockEntity.class)), "Port not linked");
                    }
                })
                .thenIdle(60)
                .thenExecute(() -> {
                    WirelessControllerDevice controller = controller(helper, master);
                    helper.assertTrue(controller.accessPointsOnline() == 1 && controller.slots() == 8, "APs " + controller.accessPointsOnline() + ", controller online "
                            + controller.isOnline());
                    helper.assertTrue(problem(helper, PORT) == Wireless.Problem.NONE, "Port: " + problem(helper, PORT));
                    helper.assertTrue(helper.getBlockEntity(PORT, WirelessPortBlockEntity.class).isOnline(), "Port has no lane");
                    long stored = RackGameTests.storage(helper, BAY).count(COBBLESTONE);
                    helper.assertTrue(stored == 10, "Network has " + stored + " cobblestone");
                    // Nine clients, eight slots: the last linked waits.
                    helper.assertTrue(controller.admittedCount() == 8, "Admitted " + controller.admittedCount());
                    helper.assertTrue(problem(helper, others.getLast()) == Wireless.Problem.OVER_CAPACITY, "Last linked: " + problem(helper, others.getLast()));
                    helper.assertTrue(problem(helper, others.getFirst()) == Wireless.Problem.NONE, "First linked: " + problem(helper, others.getFirst()));
                    List<String> names = ElclDevices.list(helper.getLevel().getServer(), network(helper, master)).stream().map(ElclDevices.Device::name).toList();
                    helper.assertTrue(names.contains("AP01") && names.contains("WINGRESS01") && names.contains("WEGRESS01"), "Names " + names);
                    helper.setBlock(AP, Blocks.AIR);
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    WirelessControllerDevice controller = controller(helper, master);
                    helper.assertTrue(controller.status() == RackDeviceInfo.Status.FAULT, "Controller " + controller.status());
                    helper.assertTrue(problem(helper, PORT) == Wireless.Problem.NO_ACCESS_POINTS, "Port: " + problem(helper, PORT));
                    helper.assertFalse(helper.getBlockEntity(PORT, WirelessPortBlockEntity.class).isOnline(), "Port still online");
                })
                .thenSucceed();
    }

    // A Wireless Bridge brings a Drive Bay cabled to it onto the controller's network; unlinked, it's gone again.
    static void bridgeSegment(GameTestHelper helper) {
        BlockPos master = rig(helper);
        helper.setBlock(BRIDGE, ModBlocks.WIRELESS_BRIDGE.get());
        RackGameTests.driveBay(helper, REMOTE_BAY);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(REMOTE_BAY)) == null, "Remote bay on a network already");
                    helper.assertTrue(controller(helper, master).link(helper.getBlockEntity(BRIDGE, WirelessBridgeBlockEntity.class)), "Bridge not linked");
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    NetworkRef network = network(helper, master);
                    helper.assertTrue(network.equals(ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(REMOTE_BAY))),
                            "Remote bay not on the controller's network");
                    WirelessBridgeBlockEntity bridge = helper.getBlockEntity(BRIDGE, WirelessBridgeBlockEntity.class);
                    helper.assertTrue(bridge.lanesUsed(helper.getLevel().getServer()) > 0, "No lanes cross the bridge");
                    int behind = ControllerStructures.devicesBehind(helper.getLevel().getServer(), NodePos.of(bridge.self()));
                    helper.assertTrue(behind == 1, "Devices behind it: " + behind);
                    List<String> names = ElclDevices.list(helper.getLevel().getServer(), network).stream().map(ElclDevices.Device::name).toList();
                    helper.assertTrue(names.contains("WBRIDGE01"), "Names " + names);
                    controller(helper, master).unlink(0);
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockEntity(BRIDGE, WirelessBridgeBlockEntity.class).link() == null, "Bridge still linked");
                    helper.assertTrue(ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(REMOTE_BAY)) == null, "Remote bay still on the network");
                })
                .thenSucceed();
    }

    // The rack and the port saved and loaded (as a reload does): the controller still lists the port, which is back on
    // its network.
    static void survivesReload(GameTestHelper helper) {
        BlockPos master = rig(helper);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> controller(helper, master).link(helper.getBlockEntity(PORT, WirelessPortBlockEntity.class)))
                .thenIdle(30)
                .thenExecute(() -> {
                    for (BlockPos pos : List.of(master, PORT)) {
                        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
                        CompoundTag saved = entity.saveWithFullMetadata(helper.getLevel().registryAccess());
                        BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(pos), helper.getBlockState(pos), saved, helper.getLevel().registryAccess());
                        helper.getLevel().setBlockEntity(loaded);
                    }
                    ControllerStructures.get(helper.getLevel()).markTopologyChanged();
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    WirelessControllerDevice controller = controller(helper, master);
                    WirelessPortBlockEntity port = helper.getBlockEntity(PORT, WirelessPortBlockEntity.class);
                    helper.assertTrue(controller.lists(port.self()), "Controller lost the port");
                    helper.assertTrue(port.link() != null && problem(helper, PORT) == Wireless.Problem.NONE, "Port: " + problem(helper, PORT));
                    helper.assertTrue(port.isOnline(), "Port has no lane after loading");
                })
                .thenSucceed();
    }
}
