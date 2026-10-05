/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NetworkStatus;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.device.NasDevice;
import net.zagdrath.encodedlogistics.rack.device.NetworkControllerDevice;
import net.zagdrath.encodedlogistics.rack.device.NetworkControllerDevice.Shown;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// Rack Network Controllers: a rack running a network on its own, a conflict with a controller block and between sizes,
// a redundant pair failing over (and switched over on purpose), and cables into the rack's bottom grommets.
final class RackControllerGameTests {
    private static final StorageKey COBBLESTONE = StorageKey.of(new ItemStack(Items.COBBLESTONE));

    private RackControllerGameTests() {}

    // --- Helpers ---

    static NetworkControllerDevice controller(GameTestHelper helper, BlockPos master, RackDeviceType type, int u, int energy) {
        NetworkControllerDevice controller = RackGameTests.install(helper, master, type, u, NetworkControllerDevice.class);
        try (Transaction transaction = Transaction.openRoot()) {
            controller.fill(energy, transaction);
            transaction.commit();
        }
        return controller;
    }

    private static NasDevice nas(GameTestHelper helper, BlockPos master, int u) {
        NasDevice nas = RackGameTests.install(helper, master, RackDeviceType.NAS, u, NasDevice.class);
        nas.items().set(0, new ItemStack(ModItems.storageDrive(StorageTier.K8).get()));
        nas.itemsChanged();
        return nas;
    }

    private static NetworkStatus status(GameTestHelper helper, BlockPos master) {
        RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
        NetworkRef ref = ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(master));
        if (ref == null) {
            ref = new NetworkRef(helper.getLevel().dimension(), rack.controllerStructure());
        }
        return ControllerStructures.statusOf(helper.getLevel().getServer(), ref);
    }

    private static NetworkStorage storage(GameTestHelper helper, BlockPos master) {
        NetworkStorage storage = ControllerStructures.storageOf(helper.getLevel().getServer(),
                ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(master)));
        helper.assertTrue(storage != null, "Rack network down");
        return storage;
    }

    // --- Tests ---

    // A rack with a 2U controller and a NAS is a whole network: online, storage, its lanes, and the controller standalone.
    static void standalone(GameTestHelper helper) {
        BlockPos master = RackGameTests.rack(helper, new BlockPos(2, 1, 0), Direction.NORTH);
        NetworkControllerDevice controller = controller(helper, master, RackDeviceType.NETWORK_CONTROLLER_2U, 1, 100_000);
        NasDevice nas = nas(helper, master, 3);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(status(helper, master) == NetworkStatus.ONLINE, "Status " + status(helper, master));
                    helper.assertTrue(nas.isOnline(), "NAS offline");
                    helper.assertTrue(controller.isOnline() && controller.shown() == Shown.ACTIVE, "Controller " + controller.shown());
                    helper.assertTrue(storage(helper, master).insert(COBBLESTONE, 32, false) == 32, "NAS didn't take cobblestone");
                    helper.assertTrue(controller.lanes() == 192, "2U lanes " + controller.lanes());
                    helper.assertTrue(controller.getCapacity() == 500_000, "2U buffer " + controller.getCapacity());
                    // Its drain comes out of its own buffer.
                    helper.assertTrue(controller.getEnergy() < 100_000, "Nothing drained");
                })
                .thenSucceed();
    }

    // A rack controller on a controller block's network: both in conflict, all of it offline; the block goes, it recovers.
    static void conflictWithBlock(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        NetworkControllerDevice controller = controller(helper, master, RackDeviceType.NETWORK_CONTROLLER_2U, 1, 100_000);
        NasDevice nas = nas(helper, master, 3);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(controller.shown() == Shown.CONFLICT, "Controller " + controller.shown());
                    helper.assertTrue(!nas.isOnline(), "NAS online in a conflict");
                    helper.setBlock(RackGameTests.CONTROLLER, Blocks.AIR);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(controller.shown() == Shown.ACTIVE, "After the block went: " + controller.shown());
                    helper.assertTrue(nas.isOnline(), "NAS still offline");
                })
                .thenSucceed();
    }

    // A 2U with a 4U is a conflict too; without the 4U it runs.
    static void conflictBetweenSizes(GameTestHelper helper) {
        BlockPos master = RackGameTests.rack(helper, new BlockPos(2, 1, 0), Direction.NORTH);
        NetworkControllerDevice two = controller(helper, master, RackDeviceType.NETWORK_CONTROLLER_2U, 1, 100_000);
        controller(helper, master, RackDeviceType.NETWORK_CONTROLLER_4U, 3, 100_000);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(two.shown() == Shown.CONFLICT, "2U " + two.shown());
                    helper.getBlockEntity(master, RackBlockEntity.class).remove(3);
                })
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(two.shown() == Shown.ACTIVE, "2U alone " + two.shown()))
                .thenSucceed();
    }

    // Two racks cabled together, a 2U in each: a pair. The active one faults: the network fails over (storage paused) and
    // the standby is active within 20 ticks; the old one comes back as standby. Switch over swaps them back.
    static void pairFailover(GameTestHelper helper) {
        BlockPos a = RackGameTests.rack(helper, new BlockPos(1, 1, 0), Direction.NORTH), b = RackGameTests.rack(helper, new BlockPos(5, 1, 0), Direction.NORTH);
        for (int x = 1; x <= 5; x++) {
            RackGameTests.cable(helper, new BlockPos(x, 1, 2));
        }
        NetworkControllerDevice first = controller(helper, a, RackDeviceType.NETWORK_CONTROLLER_2U, 1, 100_000);
        NetworkControllerDevice second = controller(helper, b, RackDeviceType.NETWORK_CONTROLLER_2U, 1, 100_000);
        NasDevice nas = nas(helper, b, 3);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    helper.assertTrue(first.isActive() && first.shown() == Shown.ACTIVE_PAIR, "First " + first.shown());
                    helper.assertTrue(second.shown() == Shown.STANDBY, "Second " + second.shown());
                    helper.assertTrue(second.drain() < first.drain(), "Standby draws " + second.drain());
                    helper.assertTrue(nas.isOnline(), "NAS offline");
                    first.setFault(true);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(status(helper, a) == NetworkStatus.FAILOVER, "Status " + status(helper, a));
                    helper.assertTrue(second.shown() == Shown.TAKING_OVER, "Second " + second.shown());
                    helper.assertTrue(nas.isOnline(), "NAS lost its lanes in the failover");
                    helper.assertTrue(ControllerStructures.storageOf(helper.getLevel().getServer(),
                            ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(a))) == null, "Storage not paused");
                })
                .thenIdle(20)
                .thenExecute(() -> {
                    helper.assertTrue(second.isActive(), "Standby didn't take over");
                    helper.assertTrue(status(helper, a) == NetworkStatus.ONLINE, "Status after " + status(helper, a));
                    helper.assertTrue(second.lastFailover() >= 0, "No failover time");
                    first.setFault(false);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(first.shown() == Shown.STANDBY, "Old active back as " + first.shown());
                    second.handleAction(null, NetworkControllerDevice.ACTION_SWITCH_OVER, 0, "");
                })
                .thenIdle(25)
                .thenExecute(() -> helper.assertTrue(first.isActive() && second.shown() == Shown.STANDBY, "Switch over: " + first.shown() + " / "
                        + second.shown()))
                .thenSucceed();
    }

    // A controller block under the rack feeds it through the bottom grommet.
    static void bottomEntry(GameTestHelper helper) {
        RackGameTests.controller(helper, new BlockPos(2, 1, 0), 20_000);
        BlockPos master = RackGameTests.rack(helper, new BlockPos(2, 2, 0), Direction.NORTH);
        RackDevice nas = nas(helper, master, 3);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> helper.assertTrue(nas.isOnline(), "NAS offline through the bottom"))
                .thenSucceed();
    }
}
