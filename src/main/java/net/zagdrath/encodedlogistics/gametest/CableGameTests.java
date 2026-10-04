/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.block.cable.CableColor;
import net.zagdrath.encodedlogistics.block.cable.CableConnection;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.registry.ModBlocks;

// Network Cables: connections and colours, the controller flange, lanes and drain on the network, dyeing, shapes.
final class CableGameTests {
    private CableGameTests() {}

    private static void cable(GameTestHelper helper, BlockPos pos, CableTier tier, CableColor color) {
        // Placed like a player would, so its connections are worked out.
        NetworkCableBlock block = ModBlocks.cable(tier, color).get();
        helper.setBlock(pos, block.withConnections(block.defaultBlockState(), helper.getLevel(), helper.absolutePos(pos)));
    }

    private static CableConnection side(GameTestHelper helper, BlockPos pos, Direction side) {
        return NetworkCableBlock.connection(helper.getBlockState(pos), side);
    }

    private static void assertSide(GameTestHelper helper, BlockPos pos, Direction side, CableConnection expected) {
        CableConnection actual = side(helper, pos, side);
        helper.assertTrue(actual == expected, pos + " " + side + " is " + actual + ", expected " + expected);
    }

    // Neutral joins every colour, a dye only its own and neutral, and normal joins dense of a compatible colour.
    static void coloursConnect(GameTestHelper helper) {
        BlockPos red = new BlockPos(0, 1, 0), blue = new BlockPos(1, 1, 0), neutral = new BlockPos(0, 1, 1), redDense = new BlockPos(0, 1, 2);
        cable(helper, red, CableTier.NORMAL, CableColor.RED);
        cable(helper, blue, CableTier.NORMAL, CableColor.BLUE);
        cable(helper, neutral, CableTier.NORMAL, CableColor.NEUTRAL);
        cable(helper, redDense, CableTier.DENSE, CableColor.RED);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    assertSide(helper, red, Direction.EAST, CableConnection.NONE);
                    assertSide(helper, blue, Direction.WEST, CableConnection.NONE);
                    assertSide(helper, red, Direction.SOUTH, CableConnection.CABLE);
                    assertSide(helper, neutral, Direction.NORTH, CableConnection.CABLE);
                    assertSide(helper, neutral, Direction.SOUTH, CableConnection.CABLE);
                    assertSide(helper, redDense, Direction.NORTH, CableConnection.CABLE);
                })
                .thenSucceed();
    }

    // A cable against a controller ends in a block connection; the controller face then provides 32 lanes, the cable
    // carries 8, and the cables show on the controller's screen with their drain.
    static void cableJoinsController(GameTestHelper helper) {
        BlockPos controller = new BlockPos(0, 1, 0);
        helper.setBlock(controller, ModBlocks.NETWORK_CONTROLLER.get());
        for (int x = 1; x <= 4; x++) {
            cable(helper, new BlockPos(x, 1, 0), CableTier.NORMAL, CableColor.NEUTRAL);
        }
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    assertSide(helper, new BlockPos(1, 1, 0), Direction.WEST, CableConnection.BLOCK);
                    assertSide(helper, new BlockPos(1, 1, 0), Direction.EAST, CableConnection.CABLE);
                    long id = helper.getBlockEntity(controller, NetworkControllerBlockEntity.class).getStructureId();
                    NetworkSnapshot snapshot = ControllerStructures.get(helper.getLevel()).snapshot(id);
                    helper.assertTrue(snapshot.laneCapacity() == 32, "Capacity is " + snapshot.laneCapacity());
                    NetworkSnapshot.DeviceEntry cables = snapshot.devices().stream()
                            .filter(entry -> entry.item().getPath().equals("network_cable")).findFirst().orElse(null);
                    helper.assertTrue(cables != null && cables.count() == 4, "Cables on the screen: " + cables);
                    helper.assertTrue(Math.abs(cables.drain() - 4 * 0.05) < 1e-9, "Cable drain is " + cables.drain());
                    // What Jade shows on a cable: the lanes through it (none, nothing uses one) of the 8 it carries.
                    int[] lanes = ControllerStructures.cableLanes(helper.getLevel().getServer(),
                            NodePos.of(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(2, 1, 0))));
                    helper.assertTrue(lanes != null && lanes[0] == 0 && lanes[1] == 8, "Cable lanes " + java.util.Arrays.toString(lanes));
                })
                // Breaking the cable next to the controller takes the rest off the network.
                .thenExecute(() -> helper.destroyBlock(new BlockPos(1, 1, 0)))
                .thenIdle(2)
                .thenExecute(() -> {
                    long id = helper.getBlockEntity(controller, NetworkControllerBlockEntity.class).getStructureId();
                    NetworkSnapshot snapshot = ControllerStructures.get(helper.getLevel()).snapshot(id);
                    helper.assertTrue(snapshot.laneCapacity() == 0, "Capacity is still " + snapshot.laneCapacity());
                    helper.assertTrue(snapshot.devices().size() == 1, "Cables still listed");
                })
                .thenSucceed();
    }

    // A dye recolours a placed cable (and its connections follow); a water bucket washes it back to neutral.
    static void dyeingRecolours(GameTestHelper helper) {
        BlockPos target = new BlockPos(0, 1, 0), red = new BlockPos(1, 1, 0);
        cable(helper, target, CableTier.DENSE, CableColor.NEUTRAL);
        cable(helper, red, CableTier.NORMAL, CableColor.RED);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> assertSide(helper, target, Direction.EAST, CableConnection.CABLE))
                .thenExecute(() -> use(helper, player, target, new ItemStack(Items.DYE.pick(DyeColor.BLUE), 2)))
                .thenIdle(1)
                .thenExecute(() -> {
                    BlockState state = helper.getBlockState(target);
                    helper.assertTrue(state.getBlock() == ModBlocks.cable(CableTier.DENSE, CableColor.BLUE).get(), "Not blue dense: " + state);
                    helper.assertTrue(player.getMainHandItem().getCount() == 1, "Dye not used up");
                    assertSide(helper, target, Direction.EAST, CableConnection.NONE);
                    assertSide(helper, red, Direction.WEST, CableConnection.NONE);
                })
                .thenExecute(() -> use(helper, player, target, new ItemStack(Items.WATER_BUCKET)))
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockState(target).getBlock() == ModBlocks.cable(CableTier.DENSE, CableColor.NEUTRAL).get(),
                            "Not washed to neutral");
                    helper.assertTrue(player.getMainHandItem().is(Items.WATER_BUCKET), "Bucket was used up");
                    assertSide(helper, red, Direction.WEST, CableConnection.CABLE);
                })
                .thenSucceed();
    }

    private static void use(GameTestHelper helper, Player player, BlockPos pos, ItemStack stack) {
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos absolute = helper.absolutePos(pos);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false);
        helper.getBlockState(pos).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
    }

    // A straight run's shape is the 6px sleeve end to end; a lone cable is just its junction cube.
    static void shapes(GameTestHelper helper) {
        BlockPos middle = new BlockPos(1, 1, 1);
        cable(helper, new BlockPos(1, 1, 0), CableTier.NORMAL, CableColor.NEUTRAL);
        cable(helper, middle, CableTier.NORMAL, CableColor.NEUTRAL);
        cable(helper, new BlockPos(1, 1, 2), CableTier.NORMAL, CableColor.NEUTRAL);
        cable(helper, new BlockPos(4, 1, 4), CableTier.DENSE, CableColor.NEUTRAL);
        helper.startSequence()
                .thenIdle(1)
                .thenExecute(() -> {
                    AABB straight = helper.getBlockState(middle).getShape(helper.getLevel(), helper.absolutePos(middle)).bounds();
                    helper.assertTrue(straight.minZ == 0 && straight.maxZ == 1 && straight.minX == 5 / 16.0 && straight.maxX == 11 / 16.0,
                            "Straight run shape is " + straight);
                    BlockPos lone = new BlockPos(4, 1, 4);
                    AABB cube = helper.getBlockState(lone).getShape(helper.getLevel(), helper.absolutePos(lone)).bounds();
                    helper.assertTrue(cube.minX == 3 / 16.0 && cube.maxY == 13 / 16.0, "Dense junction shape is " + cube);
                })
                .thenSucceed();
    }
}
