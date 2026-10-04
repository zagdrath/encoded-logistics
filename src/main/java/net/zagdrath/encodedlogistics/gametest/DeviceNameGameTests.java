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
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.store.ElclStore;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceItem;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.device.NasDevice;
import net.zagdrath.encodedlogistics.rack.device.UpsDevice;

// Stable device names (Part 2): a network with no stored names (a world from before them) gets the names counting gave;
// a UPS added lower in the rack doesn't rename the others; a removed device frees its name and renames nobody; a
// rename takes effect at once and a duplicate is refused (ELC1308); a device taken to another network keeps its name
// there if it's free, else gets the next number.
final class DeviceNameGameTests {
    private DeviceNameGameTests() {}

    private static NetworkRef network(GameTestHelper helper, BlockPos master) {
        return ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(master));
    }

    private static String name(GameTestHelper helper, BlockPos master, int u) {
        return helper.getBlockEntity(master, RackBlockEntity.class).deviceAt(u).deviceName();
    }

    // Upses at U1, U3 and U5 and a NAS at U7, named as counting named them (bottom up, per type).
    static void migration(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        RackGameTests.install(helper, master, RackDeviceType.UPS, 1, UpsDevice.class);
        RackGameTests.install(helper, master, RackDeviceType.UPS, 3, UpsDevice.class);
        RackGameTests.install(helper, master, RackDeviceType.UPS, 5, UpsDevice.class);
        RackGameTests.install(helper, master, RackDeviceType.NAS, 7, NasDevice.class);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    // As a world from before names were stored: none on the devices, none in the system.
                    RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
                    rack.devices().forEach(device -> device.setDeviceName(""));
                    NetworkRef network = network(helper, master);
                    ElclStore.get(helper.getLevel().getServer()).system(network).deviceNames.clear();
                    List<String> names = new ArrayList<>();
                    ElclDevices.list(helper.getLevel().getServer(), network).forEach(device -> names.add(device.name()));
                    helper.assertTrue(names.containsAll(List.of("UPS01", "UPS02", "UPS03", "NAS01")), "Names " + names);
                    helper.assertTrue(name(helper, master, 1).equals("UPS01") && name(helper, master, 3).equals("UPS02")
                            && name(helper, master, 5).equals("UPS03"), "Not the counted names");
                })
                .thenSucceed();
    }

    static void addRemoveRename(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        RackGameTests.install(helper, master, RackDeviceType.UPS, 3, UpsDevice.class);
        RackGameTests.install(helper, master, RackDeviceType.UPS, 5, UpsDevice.class);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(name(helper, master, 3).equals("UPS01") && name(helper, master, 5).equals("UPS02"), "First names"))
                // A UPS added below them: the next number, nobody renamed.
                .thenExecute(() -> RackGameTests.install(helper, master, RackDeviceType.UPS, 1, UpsDevice.class))
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(name(helper, master, 1).equals("UPS03") && name(helper, master, 3).equals("UPS01")
                        && name(helper, master, 5).equals("UPS02"), "Adding one renamed another"))
                // UPS01 taken out: UPS02 and UPS03 stay; the next one gets UPS01 back.
                .thenExecute(() -> helper.getBlockEntity(master, RackBlockEntity.class).remove(3))
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(name(helper, master, 1).equals("UPS03") && name(helper, master, 5).equals("UPS02"),
                        "Removing one renamed another"))
                .thenExecute(() -> RackGameTests.install(helper, master, RackDeviceType.UPS, 7, UpsDevice.class))
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(name(helper, master, 7).equals("UPS01"), "Freed name not reused: " + name(helper, master, 7));
                    var server = helper.getLevel().getServer();
                    NetworkRef network = network(helper, master);
                    try {
                        ElclDevices.rename(server, network, "UPS02", "MAINUPS");
                    } catch (ElclException e) {
                        helper.fail("Rename: " + e.getMessage());
                    }
                    helper.assertTrue(ElclDevices.find(server, network, "MAINUPS") != null && ElclDevices.find(server, network, "UPS02") == null,
                            "Rename not seen at once");
                    try {
                        ElclDevices.rename(server, network, "UPS03", "MAINUPS");
                        helper.fail("Duplicate name accepted");
                    } catch (ElclException e) {
                        helper.assertTrue(e.elclMessage().id().equals("ELC1308"), "Wanted ELC1308, got " + e.getMessage());
                    }
                })
                .thenIdle(3)
                .thenExecute(() -> helper.assertTrue(name(helper, master, 5).equals("MAINUPS"), "Rename lost on the next solve"))
                .thenSucceed();
    }

    // A second, separate network: a rack with its own rack controller.
    private static BlockPos otherNetwork(GameTestHelper helper) {
        BlockPos rack = RackGameTests.rack(helper, new BlockPos(7, 1, 5), Direction.NORTH);
        RackControllerGameTests.controller(helper, rack, RackDeviceType.NETWORK_CONTROLLER_2U, 1, 100_000);
        return rack;
    }

    static void moved(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        RackGameTests.install(helper, master, RackDeviceType.UPS, 3, UpsDevice.class);
        RackGameTests.install(helper, master, RackDeviceType.UPS, 5, UpsDevice.class);
        BlockPos other = otherNetwork(helper);
        RackGameTests.install(helper, other, RackDeviceType.UPS, 3, UpsDevice.class);
        List<ItemStack> carried = new ArrayList<>();
        helper.startSequence()
                .thenIdle(4)
                .thenExecute(() -> {
                    helper.assertTrue(name(helper, master, 5).equals("UPS02") && name(helper, other, 3).equals("UPS01"), "First names");
                    // UPS02 and UPS01 leave the first network as items.
                    var registries = helper.getLevel().registryAccess();
                    RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
                    carried.add(RackDeviceItem.toStack(rack.remove(5), registries));
                    carried.add(RackDeviceItem.toStack(rack.remove(3), registries));
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    var registries = helper.getLevel().registryAccess();
                    RackBlockEntity rack = helper.getBlockEntity(other, RackBlockEntity.class);
                    RackDevice free = RackDeviceItem.create(carried.get(0), registries), taken = RackDeviceItem.create(carried.get(1), registries);
                    helper.assertTrue(free.deviceName().equals("UPS02") && taken.deviceName().equals("UPS01"), "Names not carried by the items");
                    rack.install(free, 5, null);
                    rack.install(taken, 7, null);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(name(helper, other, 5).equals("UPS02"), "Free name not kept: " + name(helper, other, 5));
                    helper.assertTrue(name(helper, other, 3).equals("UPS01"), "The device already here was renamed: " + name(helper, other, 3));
                    helper.assertTrue(name(helper, other, 7).equals("UPS03"), "Taken name not replaced by the next free: " + name(helper, other, 7));
                })
                .thenSucceed();
    }
}
