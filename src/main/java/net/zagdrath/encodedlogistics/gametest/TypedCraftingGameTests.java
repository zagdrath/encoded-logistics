/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluids;
import net.zagdrath.encodedlogistics.blockentity.GatewayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftLog;
import net.zagdrath.encodedlogistics.crafting.CraftPlanner;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.item.ResourceEntryItem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackScheduler;
import net.zagdrath.encodedlogistics.rack.device.ComputeServerDevice;
import net.zagdrath.encodedlogistics.rack.device.MemoryServerDevice;
import net.zagdrath.encodedlogistics.rack.device.NasDevice;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.ResourceType;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// Processing jobs with fluids: a schematic taking a bucket of water and giving a bucket of lava, run through a Gateway
// whose "machine" is a cauldron above it (the Gateway pours the water in, and pulls the lava out once the test turns it
// into a lava cauldron). The rig is CraftingCompletionGameTests' rack Scheduler, with a fluid drive in its NAS too.
final class TypedCraftingGameTests {
    private static final BlockPos GATEWAY = new BlockPos(0, 1, 1), MACHINE = new BlockPos(0, 2, 1);
    private static final StorageKey WATER = StorageKey.fluid(Fluids.WATER), LAVA = StorageKey.fluid(Fluids.LAVA);

    private TypedCraftingGameTests() {}

    private static BlockPos rig(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        RackGameTests.install(helper, master, RackDeviceType.COMPUTE_SERVER, 1, ComputeServerDevice.class);
        RackGameTests.install(helper, master, RackDeviceType.MEMORY_SERVER, 3, MemoryServerDevice.class);
        NasDevice nas = RackGameTests.install(helper, master, RackDeviceType.NAS, 6, NasDevice.class);
        nas.items().set(0, new ItemStack(ModItems.storageDrive(StorageTier.K8).get()));
        nas.items().set(1, new ItemStack(ModItems.storageDrive(ResourceType.FLUID, StorageTier.K8).get()));
        nas.itemsChanged();
        helper.setBlock(GATEWAY, ModBlocks.GATEWAY.get());
        helper.setBlock(MACHINE, Blocks.CAULDRON);
        ItemStack card = new ItemStack(ModItems.ENCODED_SCHEMATIC_PROCESSING.get());
        card.set(ModDataComponents.SCHEMATIC.get(), Schematic.of(Schematic.Kind.PROCESSING, List.of(ResourceEntryItem.of(WATER, 1_000)),
                List.of(ResourceEntryItem.of(LAVA, 1_000))));
        helper.getBlockEntity(GATEWAY, GatewayBlockEntity.class).schematicSlots().set(0, card);
        return master;
    }

    // The schematic's totals are in mB; the job takes the water, the Gateway pours it into the cauldron, pulls the lava
    // out, and the job finishes with the lava in storage and a history record of both by type.
    static void fluidProcessing(GameTestHelper helper) {
        BlockPos master = rig(helper);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    Schematic schematic = helper.getBlockEntity(GATEWAY, GatewayBlockEntity.class).schematics().getFirst();
                    helper.assertTrue(schematic.inputTotals().equals(Map.of(WATER, 1_000L)) && schematic.outputTotals().equals(Map.of(LAVA, 1_000L)),
                            "Totals " + schematic.inputTotals() + " -> " + schematic.outputTotals());
                    NetworkStorage storage = RackGameTests.storage(helper, master);
                    helper.assertTrue(storage.insert(WATER, 1_000, false) == 1_000, "Water didn't fit");
                    BlockPos device = helper.absolutePos(master);
                    CraftPlanner.Plan plan = CraftRequests.plan(helper.getLevel(), device, LAVA, 1_000);
                    helper.assertTrue(plan != null && plan.complete(), "Plan: " + plan);
                    RackScheduler scheduler = helper.getBlockEntity(master, RackBlockEntity.class).scheduler();
                    helper.assertTrue(CraftRequests.start(helper.getLevel(), device, plan, scheduler, CraftRequests.Requester.NONE) != null, "Job didn't start");
                    helper.assertTrue(storage.count(WATER) == 0, "Water still stored: " + storage.count(WATER));
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(MACHINE).is(Blocks.WATER_CAULDRON), "Machine is " + helper.getBlockState(MACHINE));
                    helper.setBlock(MACHINE, Blocks.LAVA_CAULDRON);
                })
                .thenIdle(30)
                .thenExecute(() -> {
                    NetworkStorage storage = RackGameTests.storage(helper, master);
                    helper.assertTrue(storage.count(LAVA) == 1_000, "Network has " + storage.count(LAVA) + " mB lava");
                    helper.assertTrue(helper.getBlockState(MACHINE).is(Blocks.CAULDRON), "Machine is " + helper.getBlockState(MACHINE));
                    RackScheduler scheduler = helper.getBlockEntity(master, RackBlockEntity.class).scheduler();
                    helper.assertTrue(scheduler.jobs().isEmpty(), "Job still running");
                    NetworkRef network = ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(master));
                    List<CraftLog.Entry> entries = CraftLog.entries(helper.getLevel().getServer(), network);
                    helper.assertTrue(entries.size() == 1, "Records: " + entries.size());
                    CraftLog.Entry entry = entries.getFirst();
                    helper.assertTrue(entry.item().equals("FLUID minecraft:lava") && entry.produced() == 1_000, entry.item() + " " + entry.produced());
                    helper.assertTrue(entry.consumed().equals(Map.of("FLUID minecraft:water", 1_000L)), "Consumed " + entry.consumed());
                    helper.assertTrue(CraftLog.stack(entry).getHoverName().getString().equals(LAVA.displayName().getString()), "Record's stack");
                })
                .thenSucceed();
    }
}
