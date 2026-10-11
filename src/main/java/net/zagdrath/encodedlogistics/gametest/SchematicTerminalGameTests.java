/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.phys.AABB;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftingJob;
import net.zagdrath.encodedlogistics.crafting.JobStalls;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackScheduler;
import net.zagdrath.encodedlogistics.rack.device.FabricationServerDevice;
import net.zagdrath.encodedlogistics.rack.device.RackConsoleDevice;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.schematic.SchematicData;
import net.zagdrath.encodedlogistics.schematic.SchematicTerminalService;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.terminal.TerminalCommands;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalOutput;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// The Schematic Terminal handoff: the rack consoles (an upgraded Rack Console converted on load, its die given back; the
// spacing rule across kinds), the terminal's holders (names, locations, badges) and slot clicks, its jobs (steps, stall
// detection and reasons, hold and release, cancel), WRKSCH's and WRKCRFJOB's queries, and RTVSCHLST / RTVCRFSTP.
final class SchematicTerminalGameTests {
    private static final StorageKey PLANKS = StorageKey.of(new ItemStack(Items.OAK_PLANKS));

    private SchematicTerminalGameTests() {}

    private static NetworkRef network(GameTestHelper helper, BlockPos node) {
        NetworkRef ref = ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(node));
        helper.assertTrue(ref != null, "No network at " + node);
        return ref;
    }

    private static ItemStack crafting(ItemStack input, ItemStack output) {
        ItemStack card = new ItemStack(ModItems.ENCODED_SCHEMATIC_CRAFTING.get());
        card.set(ModDataComponents.SCHEMATIC.get(), Schematic.of(Schematic.Kind.CRAFTING, List.of(input), List.of(output)));
        return card;
    }

    private static String run(TerminalContext context, String line) {
        TerminalOutput out = TerminalCommands.execute(context, line);
        StringBuilder text = new StringBuilder();
        out.lines().forEach(l -> text.append(l.text().trim()).append('\n'));
        if (out.message() != null) {
            text.append(out.message().getString());
        }
        return text.toString();
    }

    private static String query(TerminalContext context, String text) {
        TerminalOutput out = TerminalService.handle(context, TerminalService.QUERY, text);
        StringBuilder all = new StringBuilder();
        for (TerminalLine line : out.lines()) {
            line.cells().forEach(cell -> all.append(cell.text().getString()).append(' '));
            all.append('\n');
        }
        if (out.message() != null) {
            all.append(out.message().getString());
        }
        return all.toString();
    }

    // A Rack Console saved with the old Memory Die upgrade loads as a Rack Fabrication Console in its unit with its grid,
    // and the die drops out of the rack; the spacing rule holds between consoles of different kinds.
    static void consoleMigration(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        RackConsoleDevice console = RackGameTests.install(helper, master, RackDeviceType.RACK_CONSOLE, 2, RackConsoleDevice.class);
        console.grid().set(4, new ItemStack(Items.OAK_LOG, 3));
        RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
        CompoundTag tag = rack.saveWithoutMetadata(helper.getLevel().registryAccess());
        ListTag devices = tag.getListOrEmpty("devices");
        for (int i = 0; i < devices.size(); i++) {
            CompoundTag device = devices.getCompoundOrEmpty(i);
            if (device.getStringOr("type", "").equals("encodedlogistics:rack_console")) {
                device.getCompoundOrEmpty("data").putBoolean("fabrication", true);
            }
        }
        rack.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, helper.getLevel().registryAccess(), tag));
        RackConsoleDevice converted = (RackConsoleDevice) rack.deviceAt(2);
        helper.assertTrue(converted != null && converted.type() == RackDeviceType.RACK_FABRICATION_CONSOLE, "Not converted: " + rack.deviceAt(2));
        helper.assertTrue(converted.kind() == RackConsoleDevice.Kind.FABRICATION, "Kind " + converted.kind());
        helper.assertTrue(converted.grid().get(4).is(Items.OAK_LOG) && converted.grid().get(4).getCount() == 3, "Grid lost: " + converted.grid().get(4));
        helper.assertFalse(rack.fits(5, RackDeviceType.RACK_SCHEMATIC_CONSOLE), "A Schematic Console fits 3U from a Fabrication Console");
        helper.assertTrue(rack.fits(10, RackDeviceType.RACK_SCHEMATIC_CONSOLE), "A Schematic Console doesn't fit 8U away");
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    AABB box = new AABB(helper.absolutePos(master)).inflate(3);
                    int dies = helper.getLevel().getEntitiesOfClass(ItemEntity.class, box, entity -> entity.getItem().is(ModItems.MEMORY_DIE.get())).stream()
                            .mapToInt(entity -> entity.getItem().getCount()).sum();
                    helper.assertTrue(dies == 1, "Memory Dies given back: " + dies);
                })
                .thenSucceed();
    }

    // Two Fabrication Servers: their holders' names and locations, the parallel and conflict badges; taking a schematic,
    // putting it back, and shift-clicking one in from the inventory.
    @SuppressWarnings("removal")
    static void holders(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        FabricationServerDevice first = RackGameTests.install(helper, master, RackDeviceType.FABRICATION_SERVER, 1, FabricationServerDevice.class);
        FabricationServerDevice second = RackGameTests.install(helper, master, RackDeviceType.FABRICATION_SERVER, 3, FabricationServerDevice.class);
        first.items().set(0, crafting(new ItemStack(Items.OAK_LOG), new ItemStack(Items.OAK_PLANKS, 4)));
        first.itemsChanged();
        // The same output from a different recipe: both badges.
        second.items().set(0, crafting(new ItemStack(Items.SPRUCE_LOG), new ItemStack(Items.OAK_PLANKS, 4)));
        second.itemsChanged();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    NetworkRef network = network(helper, master);
                    SchematicData.Snapshot snapshot = SchematicTerminalService.snapshot(player, network, network);
                    helper.assertTrue(snapshot.online() && snapshot.edit(), "Snapshot offline or read-only");
                    List<SchematicData.Holder> holders = snapshot.holders();
                    helper.assertTrue(holders.size() == 2, "Holders: " + holders.size());
                    SchematicData.Holder one = holders.getFirst();
                    helper.assertTrue(one.name().startsWith("FABSRV") && one.location().equals("RACK01 U1"), one.name() + " at " + one.location());
                    helper.assertTrue(one.kind() == SchematicData.SERVER && one.slots().size() == FabricationServerDevice.SCHEMATIC_SLOTS, "Slots");
                    helper.assertTrue((one.badges() & SchematicData.PARALLEL) != 0 && one.parallel() == 1, "No parallel badge");
                    helper.assertTrue((one.badges() & SchematicData.CONFLICT) != 0 && one.conflictPreferred().equals(one.name()),
                            "Conflict badge: " + one.badges() + " prefers " + one.conflictPreferred());
                    // Take the schematic, put it back.
                    SchematicTerminalService.act(player, player.inventoryMenu, network, network, SchematicTerminalService.TAKE, one.key(), 0, Optional.empty(), "");
                    helper.assertTrue(first.items().get(0).isEmpty() && player.inventoryMenu.getCarried().is(ModItems.ENCODED_SCHEMATIC_CRAFTING.get()),
                            "Take didn't");
                    SchematicTerminalService.act(player, player.inventoryMenu, network, network, SchematicTerminalService.PUT, one.key(), 2, Optional.empty(), "");
                    helper.assertTrue(first.items().get(2).is(ModItems.ENCODED_SCHEMATIC_CRAFTING.get()) && player.inventoryMenu.getCarried().isEmpty(),
                            "Put didn't");
                    // Shift-click one in from the inventory: the first free slot.
                    player.inventoryMenu.slots.get(9).set(crafting(new ItemStack(Items.BIRCH_LOG), new ItemStack(Items.BIRCH_PLANKS, 4)));
                    SchematicTerminalService.act(player, player.inventoryMenu, network, network, SchematicTerminalService.QUICK_PUT, one.key(), 9,
                            Optional.empty(), "");
                    helper.assertTrue(first.items().get(0).is(ModItems.ENCODED_SCHEMATIC_CRAFTING.get()) && player.inventoryMenu.slots.get(9).getItem().isEmpty(),
                            "Quick put didn't");
                    // A non-schematic doesn't go in.
                    player.inventoryMenu.setCarried(new ItemStack(Items.DIRT));
                    SchematicTerminalService.act(player, player.inventoryMenu, network, network, SchematicTerminalService.PUT, one.key(), 5, Optional.empty(), "");
                    helper.assertTrue(first.items().get(5).isEmpty(), "Dirt went in");
                    player.inventoryMenu.setCarried(ItemStack.EMPTY);
                })
                .thenSucceed();
    }

    // A job on the rig: the Jobs tab's view of it and its steps; once both runs' inputs are in the "furnace", back-dated, it
// stalls (with no reason the Gateway can
    // tell while its chest holds the inputs); held and released; WRKSCH's and WRKCRFJOB's queries; RTVSCHLST and
    // RTVCRFSTP; cancelled.
    @SuppressWarnings("removal")
    static void jobs(GameTestHelper helper) {
        BlockPos master = CraftingCompletionGameTests.rig(helper, false);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        UUID[] id = new UUID[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> id[0] = CraftingCompletionGameTests.start(helper, master, CraftRequestsAccess.requester(player)))
                // Both runs' raw iron in the "furnace": nothing more happens until it's smelted.
                .thenWaitUntil(() -> helper.assertTrue(helper.getBlockEntity(CraftingCompletionGameTests.INPUT,
                        net.minecraft.world.level.block.entity.ChestBlockEntity.class).countItem(Items.RAW_IRON) == 2, "Inputs not pushed yet"))
                .thenExecute(() -> {
                    NetworkRef network = network(helper, master);
                    SchematicData.Snapshot snapshot = SchematicTerminalService.snapshot(player, network, network);
                    SchematicData.Job job = snapshot.jobs().stream().filter(each -> each.id().equals(id[0])).findFirst().orElse(null);
                    helper.assertTrue(job != null, "Job not listed");
                    helper.assertTrue(job.status() == SchematicData.ACTIVE && job.scheduler().equals("RACKSCHED") && job.mine(), "Job " + job);
                    helper.assertTrue(!job.steps().isEmpty() && job.steps().getFirst().state() == SchematicData.STEP_ACTIVE, "Steps " + job.steps());
                    helper.assertTrue(snapshot.schedulers().size() == 1 && snapshot.schedulers().getFirst().threadsUsed() >= 1, "Schedulers");
                    helper.assertTrue(snapshot.holders().stream().anyMatch(holder -> holder.name().startsWith("GATEWAY") && holder.name().contains(" > ")),
                            "Gateway holder: " + snapshot.holders().stream().map(SchematicData.Holder::name).toList());
                    // Back-dated: the next look sees no progress for long enough.
                    CraftingJob running = CraftingCompletionGameTests.scheduler(helper, master).job(id[0]);
                    running.progressAt = helper.getLevel().getGameTime() - 100_000;
                    running.progressMark = running.done() * 1_000_003 + running.inFlight() * 1_009 + running.awaiting.size();
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    NetworkRef network = network(helper, master);
                    RackScheduler scheduler = CraftingCompletionGameTests.scheduler(helper, master);
                    CraftingJob running = scheduler.job(id[0]);
                    helper.assertTrue(running.stalled, "Didn't stall");
                    SchematicData.Snapshot snapshot = SchematicTerminalService.snapshot(player, network, network);
                    helper.assertTrue(snapshot.jobs().getFirst().status() == SchematicData.STALLED, "Status " + snapshot.jobs().getFirst().status());
                    TerminalContext context = new TerminalContext(helper.getLevel().getServer(), network, null, player);
                    int number = ControllerStructures.jobNumber(helper.getLevel().getServer(), network, id[0]);
                    String crfstp = run(context, "RTVCRFSTP C" + String.format("%04d", number));
                    helper.assertTrue(crfstp.contains("IRON_INGOT 0/2 *STALLED") && crfstp.contains("RTNSTALL = *UNKNOWN"), "RTVCRFSTP -> " + crfstp.replace("\n", "|"));
                    helper.assertTrue(run(context, "RTVCRFSTP C9999").contains("ELC2502"), "No ELC2502");
                    String schlst = run(context, "RTVSCHLST");
                    helper.assertTrue(schlst.contains("IRON_INGOT") && schlst.contains("RTNCOUNT = 1"), "RTVSCHLST -> " + schlst.replace("\n", "|"));
                    helper.assertTrue(run(context, "RTVSCHLST HOLDER(NOSUCH)").contains("ELC2501"), "No ELC2501");
                    String rows = query(context, "schematics *ALL");
                    helper.assertTrue(rows.contains("GATEWAY") && rows.contains("Iron Ingot") && rows.contains("PROC"), "WRKSCH rows: " + rows);
                    String steps = query(context, "jobsteps " + number);
                    helper.assertTrue(steps.contains("Iron Ingot") && steps.contains("Stalled"), "Job steps: " + steps);
                    // Hold, release, cancel.
                    SchematicTerminalService.act(player, player.inventoryMenu, network, network, SchematicTerminalService.HOLD, "", 0, Optional.of(id[0]), "");
                    helper.assertTrue(scheduler.isHeld(id[0]), "Not held");
                    helper.assertTrue(SchematicTerminalService.snapshot(player, network, network).jobs().getFirst().status() == SchematicData.HELD, "Not shown held");
                    SchematicTerminalService.act(player, player.inventoryMenu, network, network, SchematicTerminalService.RELEASE, "", 0, Optional.of(id[0]), "");
                    helper.assertFalse(scheduler.isHeld(id[0]), "Not released");
                    SchematicTerminalService.act(player, player.inventoryMenu, network, network, SchematicTerminalService.CANCEL, "", 0, Optional.of(id[0]), "");
                    helper.assertTrue(scheduler.job(id[0]) == null, "Not cancelled");
                    SchematicData.Snapshot after = SchematicTerminalService.snapshot(player, network, network);
                    helper.assertTrue(after.jobs().stream().anyMatch(each -> each.id().equals(id[0]) && each.status() == SchematicData.CANCELLED
                            && each.record().startsWith("C")), "Cancelled job not in the recent ones");
                })
                .thenSucceed();
    }

    // Why a stalled job doesn't move: inputs held but nobody holds the schematic, items still on tape, nothing to run.
    static void stallReasons(GameTestHelper helper) {
        Schematic schematic = Schematic.of(Schematic.Kind.CRAFTING, List.of(new ItemStack(Items.OAK_LOG)), List.of(new ItemStack(Items.OAK_PLANKS, 4)));
        CraftingJob holding = new CraftingJob(UUID.randomUUID(), PLANKS, 4, 1, List.of(new CraftingJob.Step(schematic, 1, 0, 0)));
        holding.add(new ItemStack(Items.OAK_LOG));
        JobStalls.Stall missing = JobStalls.find(helper.getLevel(), holding, List.of());
        helper.assertTrue("missing_machine".equals(missing.reason()) && missing.step() == 0, "Holding inputs: " + missing);
        CraftingJob awaiting = new CraftingJob(UUID.randomUUID(), PLANKS, 4, 1, List.of(new CraftingJob.Step(schematic, 1, 0, 0)));
        awaiting.awaiting.put(StorageKey.of(new ItemStack(Items.OAK_LOG)), 1L);
        helper.assertTrue("tape".equals(JobStalls.find(helper.getLevel(), awaiting, List.of()).reason()), "Awaiting tape");
        CraftingJob empty = new CraftingJob(UUID.randomUUID(), PLANKS, 4, 1, List.of(new CraftingJob.Step(schematic, 1, 0, 0)));
        helper.assertTrue("ingredients".equals(JobStalls.find(helper.getLevel(), empty, List.of()).reason()), "Nothing to run");
        helper.assertTrue(SchematicTerminalService.reasonSpecial("output_full").equals("*OUTPUTFULL")
                && SchematicTerminalService.stateSpecial(SchematicData.STEP_STALLED).equals("*STALLED"), "ELCL names");
        helper.succeed();
    }

    // The requester a test player starts a job as (so the job is theirs).
    private static final class CraftRequestsAccess {
        static net.zagdrath.encodedlogistics.crafting.CraftRequests.Requester requester(ServerPlayer player) {
            return new net.zagdrath.encodedlogistics.crafting.CraftRequests.Requester(Optional.of(player.getUUID()), player.getGameProfile().name(), "");
        }
    }
}
