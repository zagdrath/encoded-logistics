/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.block.DriveBayBlock;
import net.zagdrath.encodedlogistics.block.cable.CableColor;
import net.zagdrath.encodedlogistics.block.cable.CableConnection;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// Mounting parts the way a player does (on the face clicked, or on the cable in front of a clicked block), and copied
// drives counting once.
final class PlacementGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(0, 1, 0);

    private PlacementGameTests() {}

    private static void controller(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, ModBlocks.NETWORK_CONTROLLER.get());
        EnergyHandler energy = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(CONTROLLER), Direction.UP);
        try (Transaction transaction = Transaction.openRoot()) {
            energy.insert(20_000, transaction);
            transaction.commit();
        }
    }

    private static void cable(GameTestHelper helper, BlockPos pos) {
        NetworkCableBlock block = ModBlocks.cable(CableTier.NORMAL, CableColor.NEUTRAL).get();
        helper.setBlock(pos, block.withConnections(block.defaultBlockState(), helper.getLevel(), helper.absolutePos(pos)));
    }

    private static PartType part(GameTestHelper helper, BlockPos cable, Direction side) {
        return helper.getBlockEntity(cable, CableBlockEntity.class).getAttachments().part(side);
    }

    // A part used on the north face of a straight east-west cable, off-centre along its arm, goes on that face (not on
    // the arm's own side), and the cable keeps both its connections.
    static void mountsOnClickedFace(GameTestHelper helper) {
        BlockPos left = new BlockPos(1, 1, 1), middle = new BlockPos(2, 1, 1), right = new BlockPos(3, 1, 1);
        cable(helper, left);
        cable(helper, middle);
        cable(helper, right);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.startSequence()
                .thenExecute(() -> {
                    ItemStack stack = new ItemStack(PartType.INVENTORY_TAP.item());
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    BlockPos absolute = helper.absolutePos(middle);
                    // On the arm toward the east, on its north face.
                    Vec3 at = Vec3.atCenterOf(absolute).add(0.35, 0, -3.0 / 16);
                    helper.getBlockState(middle).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(at, Direction.NORTH, absolute, false));
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(part(helper, middle, Direction.NORTH) == PartType.INVENTORY_TAP, "Tap not on the north face");
                    helper.assertTrue(part(helper, middle, Direction.EAST) == null, "Tap went on the arm's side");
                    helper.assertTrue(NetworkCableBlock.connection(helper.getBlockState(middle), Direction.EAST) == CableConnection.CABLE,
                            "East connection lost");
                    helper.assertTrue(NetworkCableBlock.connection(helper.getBlockState(middle), Direction.WEST) == CableConnection.CABLE,
                            "West connection lost");
                })
                .thenSucceed();
    }

    // Every part that faces a block, used on a chest's face with a cable in front of it, mounts on that cable's side
    // toward the chest instead of failing (the space in front isn't empty).
    static void mountsFromBlockFace(GameTestHelper helper) {
        PartType[] types = { PartType.INVENTORY_TAP, PartType.THRESHOLD_SENSOR, PartType.POINT_TO_POINT_LINK, PartType.COLLECTOR_PLANE,
                PartType.DEPLOYER_PLANE, PartType.INGRESS_PORT };
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        for (int i = 0; i < types.length; i++) {
            BlockPos chest = new BlockPos(i, 1, 2), cable = new BlockPos(i, 1, 1);
            helper.setBlock(chest, Blocks.CHEST);
            cable(helper, cable);
        }
        helper.startSequence()
                .thenExecute(() -> {
                    for (int i = 0; i < types.length; i++) {
                        ItemStack stack = new ItemStack(types[i].item());
                        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                        BlockPos chest = helper.absolutePos(new BlockPos(i, 1, 2));
                        BlockHitResult face = new BlockHitResult(Vec3.atCenterOf(chest).add(0, 0, -0.5), Direction.NORTH, chest, false);
                        stack.getItem().onItemUseFirst(stack, new UseOnContext(player, InteractionHand.MAIN_HAND, face));
                    }
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    for (int i = 0; i < types.length; i++) {
                        BlockPos cable = new BlockPos(i, 1, 1);
                        helper.assertTrue(part(helper, cable, Direction.SOUTH) == types[i], types[i] + " not on the cable toward the chest");
                    }
                })
                .thenSucceed();
    }

    // Every part, used the way the game does it (through the player's game mode, so the cable's own useItemOn is
    // skipped while sneaking), mounts on the cable face clicked, sneaking or not.
    static void mountsThroughGameMode(GameTestHelper helper) {
        PartType[] types = PartType.values();
        for (int i = 0; i < types.length; i++) {
            cable(helper, new BlockPos(i, 1, 1));
            cable(helper, new BlockPos(i, 1, 3));
        }
        helper.startSequence()
                .thenExecute(() -> {
                    ServerPlayer player = helper.makeMockServerPlayerInLevel();
                    player.setGameMode(GameType.SURVIVAL);
                    for (int i = 0; i < types.length; i++) {
                        for (boolean sneaking : new boolean[] { false, true }) {
                            ItemStack stack = new ItemStack(types[i].item());
                            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                            player.setShiftKeyDown(sneaking);
                            BlockPos absolute = helper.absolutePos(new BlockPos(i, 1, sneaking ? 3 : 1));
                            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 3.0 / 16, 0), Direction.UP, absolute, false);
                            player.gameMode.useItemOn(player, helper.getLevel(), stack, InteractionHand.MAIN_HAND, hit);
                        }
                    }
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    for (int i = 0; i < types.length; i++) {
                        helper.assertTrue(part(helper, new BlockPos(i, 1, 1), Direction.UP) == types[i], types[i] + " not mounted");
                        helper.assertTrue(part(helper, new BlockPos(i, 1, 3), Direction.UP) == types[i], types[i] + " not mounted while sneaking");
                    }
                })
                .thenSucceed();
    }

    // Two copies of one drive (the same drive_id, as creative pick-block makes) in a bay are one drive's storage: 64 items
    // in show as 64, not 128.
    static void copiedDrivesCountOnce(GameTestHelper helper) {
        BlockPos cable = new BlockPos(1, 1, 0), bay = new BlockPos(2, 1, 0);
        StorageKey cobblestone = StorageKey.of(new ItemStack(Items.COBBLESTONE));
        controller(helper);
        helper.setBlock(bay, ModBlocks.DRIVE_BAY.get().defaultBlockState().setValue(DriveBayBlock.FACING, Direction.EAST));
        cable(helper, cable);
        helper.startSequence()
                .thenExecute(() -> {
                    DriveBayBlockEntity drives = helper.getBlockEntity(bay, DriveBayBlockEntity.class);
                    drives.setItem(0, new ItemStack(ModItems.storageDrive(StorageTier.K8).get()));
                    drives.setItem(1, drives.getItem(0).copy());
                })
                .thenIdle(12)
                .thenExecute(() -> {
                    NetworkStorage storage = ControllerStructures.get(helper.getLevel()).storageAt(helper.getLevel(), helper.absolutePos(bay));
                    helper.assertTrue(storage != null, "Network offline");
                    helper.assertTrue(storage.insert(cobblestone, 64, false) == 64, "Not all inserted");
                    helper.assertTrue(storage.count(cobblestone) == 64, "Counted " + storage.count(cobblestone));
                    helper.assertTrue(storage.list().get(cobblestone) == 64, "Listed " + storage.list().get(cobblestone));
                })
                .thenSucceed();
    }
}
