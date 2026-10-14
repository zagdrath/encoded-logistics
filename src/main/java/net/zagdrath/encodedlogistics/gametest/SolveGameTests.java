/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.cable.CableColor;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.NetworkBridgeBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.SolveStats;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.registry.ModBlocks;

// When networks are worked out again (ControllerStructures, "solves"): only the networks a change touches, a burst of
// changes costing one solve, a remote link's end loading or unloading costing nothing unless the link really came up or
// went down, and a big hall of racks solving quickly.
final class SolveGameTests {
    private SolveGameTests() {}

    private static long id(GameTestHelper helper, BlockPos controller) {
        return helper.getBlockEntity(controller, NetworkControllerBlockEntity.class).getStructureId();
    }

    private static SolveStats stats(GameTestHelper helper, BlockPos controller) {
        SolveStats stats = ControllerStructures.solveStats(helper.getLevel(), id(helper, controller));
        helper.assertTrue(stats != null, "No network at " + controller);
        return stats;
    }

    private static long solves(GameTestHelper helper, BlockPos controller) {
        return stats(helper, controller).total();
    }

    private static void dense(GameTestHelper helper, BlockPos pos) {
        NetworkCableBlock block = ModBlocks.cable(CableTier.DENSE, CableColor.NEUTRAL).get();
        helper.setBlock(pos, block.withConnections(block.defaultBlockState(), helper.getLevel(), helper.absolutePos(pos)));
    }

    // A small network: a powered controller, a cable east of it and a Drive Bay beyond.
    private static void small(GameTestHelper helper, BlockPos controller) {
        RackGameTests.controller(helper, controller, 20_000);
        RackGameTests.cable(helper, controller.east());
        RackGameTests.driveBay(helper, controller.east(2));
    }

    // A data hall as on the profiled server: 50 Server Racks in five rows on a floor of Dense Cable, each cabled at all
    // seven connection points (two to the floor, two to a cable bus over the tops that carries lanes on through the
    // racks, three to a column at the back), three Memory Servers in each; and two small networks beside it. Placing a
    // Drive Bay on one small network solves only that one; placing one on the hall solves only the hall, in well under
    // the target (logged with its size, for the before/after figures).
    static void stressHall(GameTestHelper helper) {
        BlockPos hall = new BlockPos(0, 1, 0), smallA = new BlockPos(40, 1, 0), smallB = new BlockPos(40, 1, 6);
        int cols = 10, rows = 5, width = 3 * cols + 2, depth = 4 * rows + 2;
        RackGameTests.controller(helper, hall, 20_000);
        for (int x = 1; x < width; x++) {
            for (int z = 0; z < depth; z++) {
                dense(helper, new BlockPos(x, 1, z));
            }
        }
        for (int i = 0; i < cols * rows; i++) {
            int x = 2 + 3 * (i % cols), z = 2 + 4 * (i / cols);
            BlockPos master = RackGameTests.rack(helper, new BlockPos(x, 2, z), Direction.NORTH);
            for (int y = 2; y <= 4; y++) {
                dense(helper, new BlockPos(x, y, z + 2));
            }
            for (int u = 1; u <= 3; u++) {
                RackGameTests.install(helper, master, RackDeviceType.MEMORY_SERVER, u, net.zagdrath.encodedlogistics.rack.device.MemoryServerDevice.class);
            }
        }
        for (int row = 0; row < rows; row++) {
            for (int x = 1; x < width; x++) {
                dense(helper, new BlockPos(x, 5, 2 + 4 * row));
                dense(helper, new BlockPos(x, 5, 3 + 4 * row));
            }
        }
        small(helper, smallA);
        small(helper, smallB);
        long[] before = new long[3];
        helper.startSequence()
                .thenIdle(40)
                .thenExecute(() -> {
                    before[0] = solves(helper, hall);
                    before[1] = solves(helper, smallA);
                    before[2] = solves(helper, smallB);
                    // A change on the first small network.
                    RackGameTests.driveBay(helper, smallA.east(2).above());
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(solves(helper, smallA) == before[1] + 1, "Small network solved " + (solves(helper, smallA) - before[1]) + " times");
                    helper.assertTrue(solves(helper, hall) == before[0], "The hall solved for a change elsewhere");
                    helper.assertTrue(solves(helper, smallB) == before[2], "The other small network solved for a change elsewhere");
                    // A change on the hall.
                    RackGameTests.driveBay(helper, new BlockPos(1, 2, 0));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    SolveStats stats = stats(helper, hall);
                    helper.assertTrue(stats.total() == before[0] + 1, "The hall solved " + (stats.total() - before[0]) + " times for one change");
                    helper.assertTrue(solves(helper, smallA) == before[1] + 1 && solves(helper, smallB) == before[2], "A small network solved for the hall");
                    double millis = stats.latestMillis();
                    EncodedLogistics.LOGGER.info("[solve_stress] the hall (50 racks, 7 uplinks each) took {} ms to discover and solve: {}",
                            String.format(Locale.ROOT, "%.2f", millis), stats.describe());
                    helper.assertTrue(millis < 250, "The hall took " + millis + " ms to solve");
                })
                .thenSucceed();
    }

    // A burst of changes costs one solve, and one more once the interval is up: with resolveInterval 20, five cables
    // added on five ticks in a row solve the network on the first tick, then once the interval since has passed.
    static void interval(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1);
        small(helper, controller);
        long[] at = new long[1];
        int interval = Config.RESOLVE_INTERVAL.getAsInt();
        helper.startSequence()
                .thenExecute(() -> Config.RESOLVE_INTERVAL.set(20))
                .thenIdle(30)
                .thenExecute(() -> {
                    at[0] = solves(helper, controller);
                    RackGameTests.cable(helper, controller.north());
                })
                .thenIdle(1)
                .thenExecute(() -> RackGameTests.cable(helper, controller.north(2)))
                .thenIdle(1)
                .thenExecute(() -> RackGameTests.cable(helper, controller.north(3)))
                .thenIdle(1)
                .thenExecute(() -> RackGameTests.cable(helper, controller.north(4)))
                .thenIdle(1)
                .thenExecute(() -> RackGameTests.cable(helper, controller.north(5)))
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(solves(helper, controller) == at[0] + 1,
                        "Five changes in five ticks solved " + (solves(helper, controller) - at[0]) + " times so far, not once"))
                .thenIdle(25)
                .thenExecute(() -> {
                    long solved = solves(helper, controller) - at[0];
                    Config.RESOLVE_INTERVAL.set(interval);
                    helper.assertTrue(solved == 2, "Five changes in five ticks solved " + solved + " times in all, not twice");
                })
                .thenSucceed();
    }

    // A Network Bridge's chunk unloading and loading again: with its partner unloaded (here, far off in an unloaded
    // chunk) the link was down and stays down, so nothing solves; with its partner loaded the link goes down and up,
    // so its network solves - and an unrelated network next to it never does.
    static void linkEnds(GameTestHelper helper) {
        BlockPos controller = new BlockPos(1, 1, 1), bridge = new BlockPos(3, 1, 1), other = new BlockPos(1, 1, 5);
        BlockPos farBridge = new BlockPos(6, 1, 1);
        RackGameTests.controller(helper, controller, 20_000);
        RackGameTests.cable(helper, controller.east());
        helper.setBlock(bridge, ModBlocks.NETWORK_BRIDGE.get());
        // A Drive Bay reached only through the bridges (no controller of its own: it joins the first network).
        helper.setBlock(farBridge, ModBlocks.NETWORK_BRIDGE.get());
        RackGameTests.cable(helper, farBridge.east());
        RackGameTests.driveBay(helper, farBridge.east(2));
        small(helper, other);
        long[] at = new long[2];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    // Partnered with a spot in a chunk nobody has loaded.
                    BlockPos nowhere = helper.absolutePos(bridge).offset(100_000, 0, 100_000);
                    helper.getBlockEntity(bridge, NetworkBridgeBlockEntity.class).setPartner(GlobalPos.of(helper.getLevel().dimension(), nowhere));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    at[0] = solves(helper, controller);
                    at[1] = solves(helper, other);
                    NetworkBridgeBlockEntity end = helper.getBlockEntity(bridge, NetworkBridgeBlockEntity.class);
                    end.onChunkUnloaded();
                    end.onLoad();
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(solves(helper, controller) == at[0], "An end loading with its partner unloaded solved its network");
                    helper.assertTrue(solves(helper, other) == at[1], "An end loading solved an unrelated network");
                    // Now pair the two bridges here, both loaded.
                    NetworkBridgeBlockEntity a = helper.getBlockEntity(bridge, NetworkBridgeBlockEntity.class);
                    NetworkBridgeBlockEntity b = helper.getBlockEntity(farBridge, NetworkBridgeBlockEntity.class);
                    a.setPartner(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(farBridge)));
                    b.setPartner(GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(bridge)));
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(farBridge.east(2))) != null,
                            "The Drive Bay isn't on the network through the bridges");
                    at[0] = solves(helper, controller);
                    at[1] = solves(helper, other);
                    helper.getBlockEntity(farBridge, NetworkBridgeBlockEntity.class).onChunkUnloaded();
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(solves(helper, controller) == at[0] + 1, "The link going down solved the network "
                            + (solves(helper, controller) - at[0]) + " times");
                    helper.assertTrue(solves(helper, other) == at[1], "The link going down solved an unrelated network");
                    helper.getBlockEntity(farBridge, NetworkBridgeBlockEntity.class).onLoad();
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(solves(helper, controller) == at[0] + 2, "The link coming back didn't solve the network");
                    helper.assertTrue(solves(helper, other) == at[1], "The link coming back solved an unrelated network");
                    helper.setBlock(farBridge.east(2), Blocks.AIR);
                })
                .thenSucceed();
    }
}
