/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.block.DriveBayBlock;
import net.zagdrath.encodedlogistics.block.LithographyPressBlock;
import net.zagdrath.encodedlogistics.block.PartHostBlock;
import net.zagdrath.encodedlogistics.block.cable.CableColor;
import net.zagdrath.encodedlogistics.block.cable.CableConnection;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.LithographyPressBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.DriveStats;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// Phase 1: the Lithography Press, Storage Drives in a Drive Bay, and Access Terminals on a cable and on a block face.
final class Phase1GameTests {
    private Phase1GameTests() {}

    private static void cable(GameTestHelper helper, BlockPos pos) {
        NetworkCableBlock block = ModBlocks.cable(CableTier.NORMAL, CableColor.NEUTRAL).get();
        helper.setBlock(pos, block.withConnections(block.defaultBlockState(), helper.getLevel(), helper.absolutePos(pos)));
    }

    private static int insertEnergy(GameTestHelper helper, BlockPos pos, Direction side, int amount) {
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), side);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    private static int extractItems(GameTestHelper helper, BlockPos pos, Direction side, ItemStack like, int amount) {
        ResourceHandler<ItemResource> handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(pos), side);
        try (Transaction transaction = Transaction.openRoot()) {
            int extracted = handler.extract(ItemResource.of(like), amount, transaction);
            transaction.commit();
            return extracted;
        }
    }

    // --- Lithography Press ---

    // A Logic Die from a wafer and redstone through the Logic Photomask: the press spends its FE over the recipe's time,
    // uses up one wafer and one redstone, keeps the photomask, and gives the die up from below only.
    static void lithographyPressEtches(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.LITHOGRAPHY_PRESS.get());
        helper.startSequence()
                .thenExecute(() -> {
                    LithographyPressBlockEntity press = helper.getBlockEntity(pos, LithographyPressBlockEntity.class);
                    press.setItem(LithographyPressBlockEntity.PHOTOMASK, new ItemStack(ModItems.LOGIC_PHOTOMASK.get()));
                    press.setItem(LithographyPressBlockEntity.WAFER, new ItemStack(ModItems.SILICON_WAFER.get(), 2));
                    press.setItem(LithographyPressBlockEntity.ADDITIVE, new ItemStack(Items.REDSTONE, 2));
                })
                // 512 FE/t in for 10 ticks: more than one etch needs.
                .thenExecuteFor(10, () -> insertEnergy(helper, pos, Direction.NORTH, 512))
                .thenExecute(() -> helper.assertTrue(helper.getBlockState(pos).getValue(LithographyPressBlock.ACTIVE), "Press not working"))
                .thenIdle(100)
                .thenExecute(() -> {
                    LithographyPressBlockEntity press = helper.getBlockEntity(pos, LithographyPressBlockEntity.class);
                    helper.assertTrue(press.getItem(LithographyPressBlockEntity.OUTPUT).is(ModItems.LOGIC_DIE.get()), "No Logic Die");
                    helper.assertTrue(press.getItem(LithographyPressBlockEntity.PHOTOMASK).is(ModItems.LOGIC_PHOTOMASK.get()), "Photomask used up");
                    helper.assertTrue(press.getItem(LithographyPressBlockEntity.WAFER).getCount() == 1, "Wafers: " + press.getItem(1));
                    helper.assertTrue(extractItems(helper, pos, Direction.UP, new ItemStack(ModItems.LOGIC_PHOTOMASK.get()), 1) == 0,
                            "Photomask came out");
                    helper.assertTrue(extractItems(helper, pos, Direction.NORTH, new ItemStack(ModItems.LOGIC_DIE.get()), 1) == 0,
                            "Die came out of a side");
                    helper.assertTrue(extractItems(helper, pos, Direction.DOWN, new ItemStack(ModItems.LOGIC_DIE.get()), 1) >= 1,
                            "Die didn't come out below");
                })
                .thenSucceed();
    }

    // --- Drives ---

    // An 8K drive in a powered Drive Bay: the network stores into it and takes out of it; it holds (8,192 - 64 per type)
    // * 8 items; its stats follow and its light turns red when full.
    static void driveStorage(GameTestHelper helper) {
        BlockPos controller = new BlockPos(0, 1, 0), bayPos = new BlockPos(2, 1, 0);
        helper.setBlock(controller, ModBlocks.NETWORK_CONTROLLER.get());
        helper.setBlock(bayPos, ModBlocks.DRIVE_BAY.get().defaultBlockState().setValue(DriveBayBlock.FACING, Direction.EAST));
        cable(helper, new BlockPos(1, 1, 0));
        StorageTier tier = StorageTier.K8;
        long[] accepted = new long[1];
        helper.startSequence()
                .thenExecute(() -> helper.getBlockEntity(bayPos, DriveBayBlockEntity.class).setItem(0, new ItemStack(ModItems.storageDrive(tier).get())))
                .thenExecute(() -> insertEnergy(helper, controller, Direction.UP, 4_000))
                .thenIdle(3)
                .thenExecute(() -> {
                    DriveBayBlockEntity bay = helper.getBlockEntity(bayPos, DriveBayBlockEntity.class);
                    helper.assertTrue(bay.isOnline(), "Drive Bay offline");
                    helper.assertTrue(StorageDriveItem.id(bay.getItem(0)) != null, "Drive has no id");
                    NetworkStorage storage = ControllerStructures.get(helper.getLevel()).storageAt(helper.getLevel(), helper.absolutePos(bayPos));
                    helper.assertTrue(storage != null, "No storage");
                    ItemKey stone = ItemKey.of(new ItemStack(Items.STONE));
                    helper.assertTrue(storage.insert(stone, 1_000, false) == 1_000, "Stone didn't fit");
                    helper.assertTrue(storage.extract(stone, 400, false) == 400, "Couldn't take stone out");
                    Map<ItemKey, Long> items = storage.list();
                    helper.assertTrue(items.get(stone) == 600, "Network holds " + items);
                    accepted[0] = storage.insert(ItemKey.of(new ItemStack(Items.COBBLESTONE)), 1_000_000, false);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    long room = (tier.bytes() - 2 * tier.bytesPerType()) * 8 - 600;
                    helper.assertTrue(accepted[0] == room, "Took " + accepted[0] + " cobblestone, expected " + room);
                    DriveStats stats = StorageDriveItem.stats(helper.getBlockEntity(bayPos, DriveBayBlockEntity.class).getItem(0));
                    helper.assertTrue(stats.bytesUsed() == tier.bytes() && stats.typesUsed() == 2, "Stats: " + stats);
                    helper.assertTrue(stats.light() == 3, "Light " + stats.light() + " on a full drive");
                })
                .thenSucceed();
    }

    // --- Access Terminal ---

    // A terminal on a cable side stops that side connecting and comes online with its lane; one on top of the
    // controller sits in a part host and joins the network too.
    static void terminals(GameTestHelper helper) {
        BlockPos controller = new BlockPos(0, 1, 0), cablePos = new BlockPos(1, 1, 0), host = new BlockPos(0, 2, 0);
        helper.setBlock(controller, ModBlocks.NETWORK_CONTROLLER.get());
        cable(helper, cablePos);
        cable(helper, new BlockPos(1, 2, 0));
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.startSequence()
                .thenExecute(() -> insertEnergy(helper, controller, Direction.UP, 4_000))
                .thenIdle(2)
                .thenExecute(() -> {
                    assertSide(helper, cablePos, Direction.UP, CableConnection.CABLE);
                    // On the cable's top.
                    ItemStack terminal = new ItemStack(ModItems.ACCESS_TERMINAL.get(), 2);
                    player.setItemInHand(InteractionHand.MAIN_HAND, terminal);
                    BlockPos absolute = helper.absolutePos(cablePos);
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0.3, 0), Direction.UP, absolute, false);
                    helper.getBlockState(cablePos).useItemOn(terminal, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
                    // On the controller's top: a part host above it.
                    BlockPos top = helper.absolutePos(controller);
                    BlockHitResult face = new BlockHitResult(Vec3.atCenterOf(top).add(0, 0.5, 0), Direction.UP, top, false);
                    terminal.getItem().onItemUseFirst(terminal, new UseOnContext(player, InteractionHand.MAIN_HAND, face));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockEntity(cablePos, CableBlockEntity.class).getAttachments().part(Direction.UP) == PartType.ACCESS_TERMINAL, "No terminal");
                    assertSide(helper, cablePos, Direction.UP, CableConnection.NONE);
                    helper.assertTrue(helper.getBlockEntity(cablePos, CableBlockEntity.class).isOnline(), "Cable terminal offline");
                    helper.assertTrue(helper.getBlockState(host).getBlock() instanceof PartHostBlock, "No part host");
                    CableBlockEntity hostEntity = helper.getBlockEntity(host, CableBlockEntity.class);
                    helper.assertTrue(hostEntity.getAttachments().part(Direction.DOWN) == PartType.ACCESS_TERMINAL, "Host terminal not against the controller");
                    helper.assertTrue(hostEntity.isOnline(), "Host terminal offline");
                    helper.assertTrue(player.getMainHandItem().isEmpty(), "Terminals not used up");
                    long id = helper.getBlockEntity(controller, NetworkControllerBlockEntity.class).getStructureId();
                    NetworkSnapshot snapshot = ControllerStructures.get(helper.getLevel()).snapshot(id);
                    helper.assertTrue(snapshot.lanesUsed() == 2, "Lanes used: " + snapshot.lanesUsed());
                    NetworkSnapshot.DeviceEntry terminals = snapshot.devices().stream()
                            .filter(entry -> entry.item().getPath().equals("access_terminal")).findFirst().orElse(null);
                    helper.assertTrue(terminals != null && terminals.count() == 2, "Terminals listed: " + terminals);
                })
                .thenSucceed();
    }

    private static void assertSide(GameTestHelper helper, BlockPos pos, Direction side, CableConnection expected) {
        CableConnection actual = NetworkCableBlock.connection(helper.getBlockState(pos), side);
        helper.assertTrue(actual == expected, pos + " " + side + " is " + actual + ", expected " + expected);
    }
}
