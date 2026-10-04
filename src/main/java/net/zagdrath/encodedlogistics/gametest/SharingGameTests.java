/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.zagdrath.encodedlogistics.block.SegmentIsolatorBlock;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.ItemRouting;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.device.L3SwitchDevice;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// Share routes on an L3 Switch. The rig: three networks split by Segment Isolators - A (controller (0,1,0), cable
// (1,1,0), Drive Bay (1,1,1)), B east of the isolator at (2,1,0) (cable (3,1,0), controller (4,1,0), Drive Bay (3,1,1))
// and C north of the isolator at (1,1,-1) (cable (1,1,-2), controller (1,1,-3), Drive Bay (2,1,-2)) - and a rack on A
// facing south at (1,1,3) with an L3 Switch that knows B (segment 1) and C (segment 2).
final class SharingGameTests {
    private static final BlockPos CABLE_B = new BlockPos(3, 1, 0), CABLE_C = new BlockPos(1, 1, -2), BAY_A = new BlockPos(1, 1, 1),
            BAY_B = new BlockPos(3, 1, 1), BAY_C = new BlockPos(2, 1, -2);
    private static final ItemKey COBBLESTONE = ItemKey.of(new ItemStack(Items.COBBLESTONE)), DIRT = ItemKey.of(new ItemStack(Items.DIRT));
    private static final int A = 0, B = 1, C = 2;

    private SharingGameTests() {}

    // The rig, with drives in the bays the test wants; returns the rack's master.
    private static BlockPos rig(GameTestHelper helper, boolean driveB) {
        RackGameTests.controller(helper, RackGameTests.CONTROLLER, 20_000);
        RackGameTests.controller(helper, new BlockPos(4, 1, 0), 20_000);
        RackGameTests.controller(helper, new BlockPos(1, 1, -3), 20_000);
        RackGameTests.driveBay(helper, BAY_A);
        RackGameTests.driveBay(helper, BAY_B);
        RackGameTests.driveBay(helper, BAY_C);
        if (!driveB) {
            helper.getBlockEntity(BAY_B, DriveBayBlockEntity.class).setItem(0, ItemStack.EMPTY);
        }
        helper.setBlock(new BlockPos(2, 1, 0), ModBlocks.SEGMENT_ISOLATOR.get().defaultBlockState().setValue(SegmentIsolatorBlock.AXIS, Direction.Axis.X));
        helper.setBlock(new BlockPos(1, 1, -1), ModBlocks.SEGMENT_ISOLATOR.get().defaultBlockState().setValue(SegmentIsolatorBlock.AXIS, Direction.Axis.Z));
        BlockPos master = RackGameTests.rack(helper, new BlockPos(1, 1, 3), Direction.SOUTH);
        RackGameTests.cable(helper, RackGameTests.CONTROLLER.east());
        RackGameTests.cable(helper, CABLE_B);
        RackGameTests.cable(helper, CABLE_C);
        RackGameTests.install(helper, master, RackDeviceType.L3_SWITCH, 1, L3SwitchDevice.class);
        return master;
    }

    // Links B and C to the rack, then adds the routes.
    private static L3SwitchDevice link(GameTestHelper helper, BlockPos master) {
        RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
        rack.toggleSegment(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(CABLE_B)));
        rack.toggleSegment(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(CABLE_C)));
        helper.assertTrue(rack.segmentCount() == 3, "Segments " + rack.segmentCount());
        return (L3SwitchDevice) rack.deviceAt(1);
    }

    // A Share route from source to dest passing everything, with its options.
    private static void share(L3SwitchDevice l3, int source, int dest, boolean readWrite, boolean crafting, boolean bidirectional) {
        l3.routes().add(new ItemRouting.Route(source, dest, ItemRouting.everything(), ItemRouting.Mode.SHARE, readWrite, crafting, bidirectional));
    }

    private static NetworkRef network(GameTestHelper helper, BlockPos node) {
        NetworkRef ref = ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(node));
        helper.assertTrue(ref != null, "No network at " + node);
        return ref;
    }

    private static NetworkStorage own(GameTestHelper helper, BlockPos node) {
        NetworkStorage storage = ControllerStructures.storageOf(helper.getLevel().getServer(), network(helper, node));
        helper.assertTrue(storage != null, "Down at " + node);
        return storage;
    }

    // What a terminal (crafting false) or a Scheduler (true) on the network at node sees.
    private static NetworkStorage seen(GameTestHelper helper, BlockPos node, boolean crafting) {
        NetworkStorage storage = ControllerStructures.sharedStorageOf(helper.getLevel().getServer(), network(helper, node), crafting);
        helper.assertTrue(storage != null, "Down at " + node);
        return storage;
    }

    private static void run(GameTestHelper helper, boolean driveB, Consumer<L3SwitchDevice> routes, Consumer<BlockPos> check) {
        BlockPos master = rig(helper, driveB);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> routes.accept(link(helper, master)))
                .thenIdle(3)
                .thenExecute(() -> check.accept(master))
                .thenSucceed();
    }

    // Read: B sees and can take A's items; what B puts in never lands in A.
    static void shareRead(GameTestHelper helper) {
        run(helper, true, l3 -> share(l3, A, B, false, true, false), master -> {
            own(helper, BAY_A).insert(COBBLESTONE, 10, false);
            NetworkStorage b = seen(helper, CABLE_B, false);
            helper.assertTrue(b.count(COBBLESTONE) == 10, "B sees " + b.count(COBBLESTONE));
            helper.assertTrue(own(helper, CABLE_B).count(COBBLESTONE) == 0, "Moved, not shared");
            helper.assertTrue(b.shared().get(COBBLESTONE) != null, "Not marked shared");
            helper.assertTrue(b.extract(COBBLESTONE, 3, false) == 3 && own(helper, BAY_A).count(COBBLESTONE) == 7, "Withdraw from A");
            b.insert(COBBLESTONE, 5, false);
            helper.assertTrue(own(helper, BAY_A).count(COBBLESTONE) == 7, "Read share took an insert");
            helper.assertTrue(own(helper, CABLE_B).count(COBBLESTONE) == 5, "B's insert not local");
            // A doesn't see B's (one way).
            helper.assertTrue(seen(helper, BAY_A, false).count(COBBLESTONE) == 7, "A sees B's");
        });
    }

    // Read/Write: B's inserts overflow into A's storage while B has none of its own, and go to B's own once it has.
    static void shareReadWrite(GameTestHelper helper) {
        BlockPos master = rig(helper, false);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    share(link(helper, master), A, B, true, true, false);
                    seen(helper, CABLE_B, false).insert(DIRT, 4, false);
                    helper.assertTrue(own(helper, BAY_A).count(DIRT) == 4, "No overflow into A: " + own(helper, BAY_A).count(DIRT));
                    helper.getBlockEntity(BAY_B, DriveBayBlockEntity.class).setItem(0, new ItemStack(ModItems.storageDrive(StorageTier.K8).get()));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    seen(helper, CABLE_B, false).insert(DIRT, 3, false);
                    helper.assertTrue(own(helper, CABLE_B).count(DIRT) == 3, "Not local first: B has " + own(helper, CABLE_B).count(DIRT));
                    helper.assertTrue(own(helper, BAY_A).count(DIRT) == 4, "A took more: " + own(helper, BAY_A).count(DIRT));
                })
                .thenSucceed();
    }

    // Crafting on B sees A's items when the route allows it, not when it doesn't.
    static void shareCrafting(GameTestHelper helper) {
        BlockPos master = rig(helper, true);
        L3SwitchDevice[] l3 = new L3SwitchDevice[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    l3[0] = link(helper, master);
                    share(l3[0], A, B, true, true, false);
                    own(helper, BAY_A).insert(COBBLESTONE, 10, false);
                    helper.assertTrue(seen(helper, CABLE_B, true).count(COBBLESTONE) == 10, "Crafting doesn't see it");
                    ItemRouting.toggleShare(l3[0].routes(), ItemRouting.SHARE_CRAFTING);
                    helper.assertTrue(seen(helper, CABLE_B, true).count(COBBLESTONE) == 0, "Crafting sees it with crafting off");
                    helper.assertTrue(seen(helper, CABLE_B, false).count(COBBLESTONE) == 10, "Terminals lost it");
                })
                .thenSucceed();
    }

    // Both ways: each side sees the other's items once, with the right counts.
    static void shareBidirectional(GameTestHelper helper) {
        run(helper, true, l3 -> share(l3, A, B, true, true, true), master -> {
            own(helper, BAY_A).insert(COBBLESTONE, 10, false);
            own(helper, CABLE_B).insert(DIRT, 5, false);
            for (BlockPos side : new BlockPos[] { BAY_A, CABLE_B }) {
                NetworkStorage storage = seen(helper, side, false);
                helper.assertTrue(storage.count(COBBLESTONE) == 10 && storage.count(DIRT) == 5,
                        "At " + side + ": " + storage.count(COBBLESTONE) + " cobblestone, " + storage.count(DIRT) + " dirt");
            }
        });
    }

    // A shares to B and B to C: C sees B's items, not A's.
    static void shareNotTransitive(GameTestHelper helper) {
        run(helper, true, l3 -> {
            share(l3, A, B, true, true, false);
            share(l3, B, C, true, true, false);
        }, master -> {
            own(helper, BAY_A).insert(COBBLESTONE, 10, false);
            own(helper, CABLE_B).insert(DIRT, 5, false);
            NetworkStorage c = seen(helper, CABLE_C, false);
            helper.assertTrue(c.count(DIRT) == 5, "C doesn't see B's");
            helper.assertTrue(c.count(COBBLESTONE) == 0, "C sees A's through B");
        });
    }

    // The switch gone: the shared items are gone from B at once; the route moves nothing either way meanwhile.
    static void shareStopsWithSwitch(GameTestHelper helper) {
        BlockPos master = rig(helper, true);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    share(link(helper, master), A, B, true, true, false);
                    own(helper, BAY_A).insert(COBBLESTONE, 10, false);
                    helper.assertTrue(seen(helper, CABLE_B, false).count(COBBLESTONE) == 10, "Not shared");
                    helper.getBlockEntity(master, RackBlockEntity.class).remove(1);
                    helper.assertTrue(seen(helper, CABLE_B, false).count(COBBLESTONE) == 0, "Still shared without the switch");
                })
                .thenIdle(25)
                .thenExecute(() -> helper.assertTrue(own(helper, BAY_A).count(COBBLESTONE) == 10, "Items moved"))
                .thenSucceed();
    }

    // A route saved before modes loads as Move; a Move route still moves A's items into B.
    static void oldRoutesMove(GameTestHelper helper) {
        CompoundTag saved = new CompoundTag();
        ListTag list = new ListTag();
        CompoundTag old = new CompoundTag();
        old.putInt("source", 0);
        old.putInt("dest", 1);
        list.add(old);
        saved.put("routes", list);
        ItemRouting.Route loaded = ItemRouting.load(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), saved), "routes", 2, 8)
                .getFirst();
        helper.assertTrue(loaded.mode() == ItemRouting.Mode.MOVE && !loaded.idle(), "Old route loaded as " + loaded);
        BlockPos master = rig(helper, true);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    link(helper, master).routes().add(loaded);
                    own(helper, BAY_A).insert(COBBLESTONE, 10, false);
                })
                .thenIdle(45)
                .thenExecute(() -> {
                    helper.assertTrue(own(helper, BAY_A).count(COBBLESTONE) == 0, "A still has " + own(helper, BAY_A).count(COBBLESTONE));
                    helper.assertTrue(own(helper, CABLE_B).count(COBBLESTONE) == 10, "B has " + own(helper, CABLE_B).count(COBBLESTONE));
                })
                .thenSucceed();
    }
}
