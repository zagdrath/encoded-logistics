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
        TESTS.put("facade_dressing", InfrastructureGameTests::facadeDressing);
        TESTS.put("inlet_fills_controller_then_bank", InfrastructureGameTests::inletFillsControllerThenBank);
        TESTS.put("isolator_splits_network", InfrastructureGameTests::isolatorSplitsNetwork);
        TESTS.put("lithography_press_etches", Phase1GameTests::lithographyPressEtches);
        TESTS.put("drive_storage", Phase1GameTests::driveStorage);
        TESTS.put("access_terminals", Phase1GameTests::terminals);
        TESTS.put("ingress_port", Phase2GameTests::ingressPort);
        TESTS.put("egress_port", Phase2GameTests::egressPort);
        TESTS.put("inventory_tap", Phase2GameTests::inventoryTap);
        TESTS.put("threshold_sensor", Phase2GameTests::thresholdSensor);
        TESTS.put("metal_dusts", MaterialGameTests::dusts);
        TESTS.put("every_item_craftable", RecipeGameTests::everyItemCraftable);
        TESTS.put("scheduler_forms", Phase3GameTests::schedulerForms);
        TESTS.put("encoder_encodes", Phase3GameTests::encoderEncodes);
        TESTS.put("ghost_drag", Phase3GameTests::ghostDrag);
        TESTS.put("fabricator_crafts", Phase3GameTests::fabricatorCrafts);
        TESTS.put("gateway_processes", Phase3GameTests::gatewayProcesses);
        TESTS.put("bridge_joins_networks", Phase4GameTests::bridgeJoinsNetworks);
        TESTS.put("bridge_cross_dimension", Phase4GameTests::bridgeCrossDimension);
        TESTS.put("relay_range", Phase4GameTests::relayRange);
        TESTS.put("point_to_point", Phase4GameTests::pointToPoint);
        TESTS.put("point_to_point_lanes", Phase4GameTests::pointToPointLanes);
        TESTS.put("planes", Phase4GameTests::planes);
        TESTS.put("fuzzy_filter", Phase4GameTests::fuzzyFilter);
        TESTS.put("redstone_control", Phase4GameTests::redstoneControl);
        TESTS.put("mounts_on_clicked_face", PlacementGameTests::mountsOnClickedFace);
        TESTS.put("mounts_from_block_face", PlacementGameTests::mountsFromBlockFace);
        TESTS.put("mounts_through_game_mode", PlacementGameTests::mountsThroughGameMode);
        TESTS.put("result_stacks_on_cursor", TerminalGameTests::resultStacksOnCursor);
        TESTS.put("terminal_grid_actions", TerminalGameTests::gridActions);
        TESTS.put("copied_drives_count_once", PlacementGameTests::copiedDrivesCountOnce);
        TESTS.put("rack_places_and_breaks", RackGameTests::placesAndBreaks);
        TESTS.put("rack_unit_rules", RackGameTests::unitRules);
        TESTS.put("rack_geometry", RackGameTests::geometry);
        TESTS.put("rack_targeting", RackGameTests::targeting);
        TESTS.put("rack_on_network", RackGameTests::onNetwork);
        TESTS.put("rack_firewall", RackGameTests::firewall);
        TESTS.put("rack_ups", RackGameTests::upsCoversAndRecharges);
        TESTS.put("rack_ups_full_buffers", RackGameTests::upsOnFullBuffers);
        TESTS.put("rack_ups_on_battery", RackGameTests::upsOnBatteryIndication);
        TESTS.put("rack_router", RackGameTests::routerMovesItems);
        TESTS.put("rack_lane_pool", Rack2GameTests::lanePool);
        TESTS.put("rack_storage_devices", Rack2GameTests::storageDevices);
        TESTS.put("rack_scheduler", Rack2GameTests::rackScheduler);
        TESTS.put("rack_segments", Rack2GameTests::segments);
        TESTS.put("rack_router_wan", Rack2GameTests::routerWan);
        TESTS.put("rack_route_filters", Rack2GameTests::routeFilters);
        TESTS.put("rack_tape_archives", Rack3GameTests::tapeArchives);
        TESTS.put("rack_tape_recalls", Rack3GameTests::tapeRecalls);
        TESTS.put("rack_plan_with_tape", Rack3GameTests::planWithTape);
        TESTS.put("rack_wireless_controller", Rack3GameTests::wirelessController);
        TESTS.put("rack_console", Rack3GameTests::rackConsole);
        TESTS.put("rack_six_units_and_upgrades", Rack3GameTests::sixUnitsAndUpgrades);
        TESTS.put("desk_screen", DeskGameTests::deskScreen);
        TESTS.put("desk_withdraws", DeskGameTests::deskWithdraws);
        TESTS.put("desk_commands", DeskGameTests::deskCommands);
        TESTS.put("control_interface", ControlInterfaceGameTests::controlInterface);
        TESTS.put("elcl_os_commands", ElclGameTests::osCommands);
        TESTS.put("elcl_persistence", ElclStoreGameTests::persistence);
        TESTS.put("elcl_storage_full", ElclStoreGameTests::storageFull);
        TESTS.put("elcl_retention", ElclStoreGameTests::retention);
        TESTS.put("elcl_library_authority", ElclStoreGameTests::libraryAuthority);
        TESTS.put("device_names_migration", DeviceNameGameTests::migration);
        TESTS.put("device_names_stable", DeviceNameGameTests::addRemoveRename);
        TESTS.put("device_names_moved", DeviceNameGameTests::moved);
        TESTS.put("device_locate_box", ElclStoreGameTests::locateBox);
        TESTS.put("elcl_mod_commands", ElclVmGameTests::modCommands);
        TESTS.put("elcl_interactive_call", ElclVmGameTests::interactiveCall);
        TESTS.put("elcl_examples", ElclVmGameTests::examples);
        TESTS.put("batch_compute_server", BatchJobGameTests::computeServer);
        TESTS.put("batch_queue_and_hosts", BatchJobGameTests::queueAndHosts);
        TESTS.put("batch_restart", BatchJobGameTests::restart);
        TESTS.put("batch_budget", BatchJobGameTests::budget);
        TESTS.put("batch_logs", BatchJobGameTests::logs);
        TESTS.put("schedule_entries", SchedulingGameTests::schedules);
        TESTS.put("trigger_items", SchedulingGameTests::itemTrigger);
        TESTS.put("trigger_events", SchedulingGameTests::eventTriggers);
        TESTS.put("trigger_power", SchedulingGameTests::powerTriggers);
        TESTS.put("trigger_storage", SchedulingGameTests::storageTrigger);
        TESTS.put("swivel_chair", DeskGameTests::swivelChair);
        TESTS.put("rack_monitoring", Rack2GameTests::monitoring);
        TESTS.put("rack_copied_drives", Rack2GameTests::copiedDrives);
        TESTS.put("rack_controller_standalone", RackControllerGameTests::standalone);
        TESTS.put("rack_controller_conflict_block", RackControllerGameTests::conflictWithBlock);
        TESTS.put("rack_controller_conflict_sizes", RackControllerGameTests::conflictBetweenSizes);
        TESTS.put("rack_controller_pair_failover", RackControllerGameTests::pairFailover);
        TESTS.put("rack_bottom_entry", RackControllerGameTests::bottomEntry);
        TESTS.put("rack_power_port", RackCablingGameTests::powerPort);
        TESTS.put("rack_bonding_and_shedding", RackCablingGameTests::bondingAndShedding);
        TESTS.put("rack_mismatch", RackCablingGameTests::mismatch);
        TESTS.put("device_topology", RackCablingGameTests::deviceTopology);
        TESTS.put("device_topology_pair", RackCablingGameTests::deviceTopologyPair);
        TESTS.put("job_returned_through_port", CraftingCompletionGameTests::returnedThroughPort);
        TESTS.put("job_returned_through_gateway", CraftingCompletionGameTests::returnedThroughGateway);
        TESTS.put("job_cancel_while_waiting", CraftingCompletionGameTests::cancelWhileWaiting);
        TESTS.put("job_survives_reload", CraftingCompletionGameTests::survivesReload);
        TESTS.put("job_toast_to_requester", CraftingCompletionGameTests::toastToRequester);
        TESTS.put("job_toast_cancelled_failed", CraftingCompletionGameTests::toastCancelledAndFailed);
        TESTS.put("job_offline_requester_queued", CraftingCompletionGameTests::offlineRequesterQueued);
        TESTS.put("share_read", SharingGameTests::shareRead);
        TESTS.put("share_read_write", SharingGameTests::shareReadWrite);
        TESTS.put("share_crafting", SharingGameTests::shareCrafting);
        TESTS.put("share_bidirectional", SharingGameTests::shareBidirectional);
        TESTS.put("share_not_transitive", SharingGameTests::shareNotTransitive);
        TESTS.put("share_stops_with_switch", SharingGameTests::shareStopsWithSwitch);
        TESTS.put("share_old_routes_move", SharingGameTests::oldRoutesMove);
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
