/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.FunctionGameTestInstance;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.TestData;
import net.minecraft.gametest.framework.TestEnvironmentDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.RegisterGameTestsEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.ControllerState;
import net.zagdrath.encodedlogistics.block.NetworkControllerBlock;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerFrame;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.network.NetworkStatus;
import net.zagdrath.encodedlogistics.registry.ModBlocks;

// In-game tests of the Network Controller, cables and infrastructure, run with `gradlew runGameTestServer` or `/test runall` in a dev client.
public final class EncodedLogisticsGameTests {
    private static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS = DeferredRegister.create(Registries.TEST_FUNCTION,
            EncodedLogistics.MODID);
    private static final Map<String, Consumer<GameTestHelper>> TESTS = new LinkedHashMap<>();

    static {
        TESTS.put("frame_forms", EncodedLogisticsGameTests::frameForms);
        TESTS.put("filled_face_is_invalid", EncodedLogisticsGameTests::filledFaceIsInvalid);
        TESTS.put("long_line_is_too_large", EncodedLogisticsGameTests::longLineIsTooLarge);
        TESTS.put("breaking_splits_structures", EncodedLogisticsGameTests::breakingSplitsStructures);
        TESTS.put("power_cycle", EncodedLogisticsGameTests::powerCycle);
        TESTS.put("energy_pools_across_structure", EncodedLogisticsGameTests::energyPoolsAcrossStructure);
        TESTS.put("cable_colours_connect", CableGameTests::coloursConnect);
        TESTS.put("cable_joins_controller", CableGameTests::cableJoinsController);
        TESTS.put("cable_dyeing_recolours", CableGameTests::dyeingRecolours);
        TESTS.put("cable_shapes", CableGameTests::shapes);
        TESTS.put("fiber_cable", InfrastructureGameTests::fiberCable);
        TESTS.put("anchor_blocks_side", InfrastructureGameTests::anchorBlocksSide);
        TESTS.put("facades", InfrastructureGameTests::facades);
        TESTS.put("inlet_fills_controller_then_bank", InfrastructureGameTests::inletFillsControllerThenBank);
        TESTS.put("isolator_splits_network", InfrastructureGameTests::isolatorSplitsNetwork);
        TESTS.put("lithography_press_etches", Phase1GameTests::lithographyPressEtches);
        TESTS.put("drive_storage", Phase1GameTests::driveStorage);
        TESTS.put("access_terminals", Phase1GameTests::terminals);
        TESTS.forEach((name, test) -> FUNCTIONS.register(name, () -> test));
    }

    private EncodedLogisticsGameTests() {}

    public static void register(IEventBus modEventBus) {
        FUNCTIONS.register(modEventBus);
        modEventBus.addListener(EncodedLogisticsGameTests::registerTests);
    }

    private static void registerTests(RegisterGameTestsEvent event) {
        Holder<TestEnvironmentDefinition<?>> environment = event.registerEnvironment(EncodedLogistics.id("default"));
        TestData<Holder<TestEnvironmentDefinition<?>>> data = new TestData<>(environment, Level.OVERWORLD, Identifier.withDefaultNamespace("empty"),
                200, 0, true, Rotation.NONE, false, 1, 1, false, 10);
        TESTS.keySet().forEach(name -> event.registerTest(EncodedLogistics.id(name), new FunctionGameTestInstance(
                ResourceKey.create(Registries.TEST_FUNCTION, EncodedLogistics.id(name)), data)));
    }

    // --- Helpers ---

    private static void controller(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.NETWORK_CONTROLLER.get());
    }

    // The edge blocks of a box with its low corner at (0, 1, 0).
    private static void frame(GameTestHelper helper, int sizeX, int sizeY, int sizeZ) {
        BlockPos min = new BlockPos(0, 1, 0);
        BlockPos max = min.offset(sizeX - 1, sizeY - 1, sizeZ - 1);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (ControllerFrame.isEdge(pos, min, max)) {
                controller(helper, pos.immutable());
            }
        }
    }

    private static BlockState state(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockState(pos);
    }

    private static void assertShown(GameTestHelper helper, BlockPos pos, boolean formed, ControllerState shown) {
        BlockState state = state(helper, pos);
        helper.assertTrue(state.getValue(NetworkControllerBlock.FORMED) == formed, "formed at " + pos + " is " + state.getValue(NetworkControllerBlock.FORMED));
        helper.assertTrue(state.getValue(NetworkControllerBlock.STATE) == shown, "state at " + pos + " is " + state.getValue(NetworkControllerBlock.STATE));
    }

    private static NetworkSnapshot snapshot(GameTestHelper helper, BlockPos pos) {
        long id = helper.getBlockEntity(pos, NetworkControllerBlockEntity.class).getStructureId();
        return ControllerStructures.get(helper.getLevel()).snapshot(id);
    }

    private static long structureId(GameTestHelper helper, BlockPos pos) {
        return helper.getBlockEntity(pos, NetworkControllerBlockEntity.class).getStructureId();
    }

    private static int insert(GameTestHelper helper, BlockPos pos, int amount) {
        EnergyHandler handler = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(pos), Direction.UP);
        helper.assertTrue(handler != null, "No energy handler at " + pos);
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(amount, transaction);
            transaction.commit();
            return inserted;
        }
    }

    // --- Tests ---

    // A 3x3x3 frame forms one 20-block structure; without power it shows offline.
    private static void frameForms(GameTestHelper helper) {
        frame(helper, 3, 3, 3);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    long id = structureId(helper, new BlockPos(0, 1, 0));
                    for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(0, 1, 0), new BlockPos(2, 3, 2))) {
                        if (state(helper, pos).getBlock() instanceof NetworkControllerBlock) {
                            assertShown(helper, pos, true, ControllerState.OFFLINE);
                            helper.assertTrue(structureId(helper, pos) == id, "Two structures in one frame");
                        }
                    }
                    NetworkSnapshot snapshot = snapshot(helper, new BlockPos(0, 1, 0));
                    helper.assertTrue(snapshot.blocks() == 20, "Frame has " + snapshot.blocks() + " blocks");
                    helper.assertTrue(snapshot.sizeX() == 3 && snapshot.sizeY() == 3 && snapshot.sizeZ() == 3, "Frame is not 3x3x3");
                    helper.assertTrue(snapshot.capacity() == 20 * 25_000L, "Buffer is " + snapshot.capacity());
                    helper.assertTrue(snapshot.status() == NetworkStatus.NO_POWER, "Status is " + snapshot.status());
                })
                .thenSucceed();
    }

    // Filling a face centre turns the whole group red; removing it forms the frame again.
    private static void filledFaceIsInvalid(GameTestHelper helper) {
        frame(helper, 3, 3, 3);
        BlockPos centre = new BlockPos(1, 2, 0);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> controller(helper, centre))
                .thenIdle(2)
                .thenExecute(() -> {
                    assertShown(helper, new BlockPos(0, 1, 0), false, ControllerState.ERROR);
                    assertShown(helper, centre, false, ControllerState.ERROR);
                    helper.assertTrue(snapshot(helper, centre).status() == NetworkStatus.INVALID_SHAPE, "Not invalid_shape");
                })
                .thenExecute(() -> helper.setBlock(centre, Blocks.AIR))
                .thenIdle(2)
                .thenExecute(() -> assertShown(helper, new BlockPos(2, 3, 2), true, ControllerState.OFFLINE))
                .thenSucceed();
    }

    // A line of 8 is too large; one of 7 forms.
    private static void longLineIsTooLarge(GameTestHelper helper) {
        for (int x = 0; x < 8; x++) {
            controller(helper, new BlockPos(x, 1, 0));
        }
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    assertShown(helper, new BlockPos(3, 1, 0), false, ControllerState.ERROR);
                    helper.assertTrue(snapshot(helper, new BlockPos(0, 1, 0)).status() == NetworkStatus.TOO_LARGE, "Not too_large");
                })
                .thenExecute(() -> helper.setBlock(new BlockPos(7, 1, 0), Blocks.AIR))
                .thenIdle(2)
                .thenExecute(() -> assertShown(helper, new BlockPos(0, 1, 0), true, ControllerState.OFFLINE))
                .thenSucceed();
    }

    // Breaking the middle of a line leaves two separate single blocks.
    private static void breakingSplitsStructures(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            controller(helper, new BlockPos(x, 1, 0));
        }
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(structureId(helper, new BlockPos(0, 1, 0)) == structureId(helper, new BlockPos(2, 1, 0)),
                        "Line is not one structure"))
                .thenExecute(() -> helper.setBlock(new BlockPos(1, 1, 0), Blocks.AIR))
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(structureId(helper, new BlockPos(0, 1, 0)) != structureId(helper, new BlockPos(2, 1, 0)),
                            "Halves still share a structure");
                    assertShown(helper, new BlockPos(0, 1, 0), false, ControllerState.OFFLINE);
                    helper.assertTrue(snapshot(helper, new BlockPos(2, 1, 0)).single(), "Half is not a single block");
                })
                .thenSucceed();
    }

    // FE brings a controller online (and lit); draining the buffer takes it to no_power; more FE brings it back.
    private static void powerCycle(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        controller(helper, pos);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> helper.assertTrue(insert(helper, pos, 10) == 10, "Didn't take 10 FE"))
                .thenIdle(1)
                .thenExecute(() -> {
                    assertShown(helper, pos, false, ControllerState.ONLINE);
                    helper.assertTrue(state(helper, pos).getLightEmission() == 4, "Not lit while online");
                    NetworkSnapshot snapshot = snapshot(helper, pos);
                    helper.assertTrue(snapshot.status() == NetworkStatus.ONLINE, "Status is " + snapshot.status());
                    helper.assertTrue(snapshot.usage() == 2.0, "Usage is " + snapshot.usage());
                })
                // 2 FE/t drains 10 FE in 5 ticks.
                .thenIdle(6)
                .thenExecute(() -> {
                    assertShown(helper, pos, false, ControllerState.OFFLINE);
                    helper.assertTrue(snapshot(helper, pos).status() == NetworkStatus.NO_POWER, "Not no_power once drained");
                    helper.assertTrue(state(helper, pos).getLightEmission() == 0, "Lit while offline");
                })
                .thenExecute(() -> insert(helper, pos, 1_000))
                .thenIdle(1)
                .thenExecute(() -> assertShown(helper, pos, false, ControllerState.ONLINE))
                .thenSucceed();
    }

    // FE fed into one face fills the whole structure, not just that block, and every face reports the structure's
    // buffer (what Jade shows).
    private static void energyPoolsAcrossStructure(GameTestHelper helper) {
        for (int x = 0; x < 3; x++) {
            controller(helper, new BlockPos(x, 1, 0));
        }
        BlockPos end = new BlockPos(0, 1, 0);
        int[] taken = new int[1];
        helper.startSequence()
                .thenIdle(2)
                // Up to 4,096 FE/t per block: 12,288 a tick for three blocks.
                .thenExecute(() -> taken[0] = insert(helper, end, 60_000))
                .thenExecute(() -> helper.assertTrue(taken[0] == 3 * 4_096, "Took " + taken[0] + " in one tick"))
                .thenExecute(() -> {
                    for (int tick = 0; tick < 10; tick++) {
                        helper.getBlockEntity(new BlockPos(tick % 3, 1, 0), NetworkControllerBlockEntity.class).takeReceived();
                        insert(helper, end, 60_000);
                    }
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    EnergyHandler far = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(new BlockPos(2, 1, 0)), Direction.UP);
                    helper.assertTrue(far != null && far.getCapacityAsLong() == 75_000, "Far face reports " + (far == null ? null : far.getCapacityAsLong()));
                    helper.assertTrue(far.getAmountAsLong() > 25_000, "Only " + far.getAmountAsLong() + " FE stored: not pooled");
                    NetworkSnapshot snapshot = snapshot(helper, end);
                    helper.assertTrue(snapshot.capacity() == 75_000 && snapshot.stored() > 25_000, "Screen shows " + snapshot.stored() + " / "
                            + snapshot.capacity());
                })
                .thenSucceed();
    }
}
