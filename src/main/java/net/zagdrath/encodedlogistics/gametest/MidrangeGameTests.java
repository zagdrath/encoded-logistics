/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.crafting.CraftPlanner;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.crafting.RecipeLibraries;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.device.DisketteDevice;
import net.zagdrath.encodedlogistics.elcl.device.Diskettes;
import net.zagdrath.encodedlogistics.elcl.device.Printers;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.job.JobHosts;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.midrange.CardReaderBlock;
import net.zagdrath.encodedlogistics.midrange.CardReaderBlockEntity;
import net.zagdrath.encodedlogistics.midrange.DisketteData;
import net.zagdrath.encodedlogistics.midrange.DisketteMagazineItem;
import net.zagdrath.encodedlogistics.midrange.DisketteStack;
import net.zagdrath.encodedlogistics.midrange.ExpansionCabinetBlock;
import net.zagdrath.encodedlogistics.midrange.FootprintBlock;
import net.zagdrath.encodedlogistics.midrange.KeypunchBlockEntity;
import net.zagdrath.encodedlogistics.midrange.LinePrinterBlockEntity;
import net.zagdrath.encodedlogistics.midrange.MidrangeStates;
import net.zagdrath.encodedlogistics.midrange.MidrangeSystemBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.ItemKey;

// The Midrange line's blocks: footprints placed and broken whole, their shapes, and the Expansion Cabinet attaching to a
// Midrange System on either side (one per system, the same facing) and coming loose again; the peripherals working for
// a Midrange System on their network, and the Line Printer's pages; the systems' IPL, crafting and hosting, and their
// tiers.
final class MidrangeGameTests {
    private static final ItemKey LOG = ItemKey.of(new ItemStack(Items.OAK_LOG)), PLANKS = ItemKey.of(new ItemStack(Items.OAK_PLANKS));
    private MidrangeGameTests() {}

    // Places a footprint block's master as a player would (its dummies with it), facing north.
    private static void place(GameTestHelper helper, Block block, BlockPos pos) {
        BlockState state = block.defaultBlockState().setValue(FootprintBlock.FACING, Direction.NORTH);
        helper.setBlock(pos, state);
        block.setPlacedBy(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos), null, ItemStack.EMPTY);
    }

    private static boolean shaped(GameTestHelper helper, BlockPos pos) {
        return !helper.getBlockState(pos).getShape(helper.getLevel(), helper.absolutePos(pos), CollisionContext.empty()).isEmpty();
    }

    // A Midrange System facing north: its dummy to the east; a Line Printer's three; shapes on every block; breaking a
    // dummy breaks the whole footprint.
    static void footprints(GameTestHelper helper) {
        BlockPos system = new BlockPos(1, 1, 1), printer = new BlockPos(1, 1, 4);
        place(helper, ModBlocks.MIDRANGE_SYSTEM.get(), system);
        place(helper, ModBlocks.LINE_PRINTER.get(), printer);
        helper.assertBlockProperty(system.east(), FootprintBlock.PART, FootprintBlock.Part.DUMMY);
        for (BlockPos pos : new BlockPos[] { printer.east(), printer.above(), printer.east().above() }) {
            helper.assertBlockProperty(pos, FootprintBlock.PART, FootprintBlock.Part.DUMMY);
        }
        helper.assertTrue(shaped(helper, system) && shaped(helper, system.east()) && shaped(helper, printer.above()), "A footprint block has no shape");
        FootprintBlock block = (FootprintBlock) ModBlocks.LINE_PRINTER.get();
        helper.assertTrue(helper.absolutePos(printer).equals(block.master(helper.getLevel(), helper.absolutePos(printer.east().above()),
                helper.getBlockState(printer.east().above()))), "A dummy can't find its master");
        helper.getLevel().destroyBlock(helper.absolutePos(printer.east().above()), false);
        helper.assertBlockNotPresent(ModBlocks.LINE_PRINTER.get(), printer);
        helper.assertBlockNotPresent(ModBlocks.LINE_PRINTER.get(), printer.east());
        helper.getLevel().destroyBlock(helper.absolutePos(system.east()), false);
        helper.assertBlockNotPresent(ModBlocks.MIDRANGE_SYSTEM.get(), system);
        helper.succeed();
    }

    // A cabinet left of the master: attached pos, the system's expansion neg; broken, the system's back to none. One right
    // of the dummy: neg / pos; a second on the other side then stays unattached, as does one facing another way.
    static void expansionCabinet(GameTestHelper helper) {
        BlockPos system = new BlockPos(2, 1, 2), left = system.west(), right = system.east(2), other = new BlockPos(2, 1, 4);
        place(helper, ModBlocks.MIDRANGE_SYSTEM.get(), system);
        BlockState cabinet = ModBlocks.EXPANSION_CABINET.get().defaultBlockState().setValue(ExpansionCabinetBlock.FACING, Direction.NORTH);
        helper.setBlock(left, cabinet);
        helper.assertBlockProperty(left, ExpansionCabinetBlock.ATTACHED, MidrangeStates.Side.POS);
        helper.assertBlockProperty(system, MidrangeStates.EXPANSION, MidrangeStates.Side.NEG);
        helper.setBlock(left, Blocks.AIR);
        helper.assertBlockProperty(system, MidrangeStates.EXPANSION, MidrangeStates.Side.NONE);

        helper.setBlock(right, cabinet);
        helper.assertBlockProperty(right, ExpansionCabinetBlock.ATTACHED, MidrangeStates.Side.NEG);
        helper.assertBlockProperty(system, MidrangeStates.EXPANSION, MidrangeStates.Side.POS);
        helper.setBlock(left, cabinet);
        helper.assertBlockProperty(left, ExpansionCabinetBlock.ATTACHED, MidrangeStates.Side.NONE);
        helper.assertBlockProperty(system, MidrangeStates.EXPANSION, MidrangeStates.Side.POS);

        // Facing another way beside another system: nothing.
        place(helper, ModBlocks.MIDRANGE_SYSTEM.get(), other);
        helper.setBlock(other.west(), cabinet.setValue(ExpansionCabinetBlock.FACING, Direction.SOUTH));
        helper.assertBlockProperty(other.west(), ExpansionCabinetBlock.ATTACHED, MidrangeStates.Side.NONE);
        helper.assertBlockProperty(other, MidrangeStates.EXPANSION, MidrangeStates.Side.NONE);
        helper.succeed();
    }

    // On RackGameTests' networked rack: a Midrange System cabled to it, a Card Reader beside it, a Keypunch in front and a
    // Line Printer beside its dummy. They're online and named (MIDRANGE01, KEYPUNCH01, CARDRDR01, PRT01); the Keypunch
    // punches a log-to-planks card; the Card Reader reads it onto a diskette (twice: it replaces itself) and is ELCL's
    // diskette drive; the Line Printer prints the device list as a book, one paper a page, and is *DFT. With the system
    // gone, they're offline and refuse.
    static void peripherals(GameTestHelper helper) {
        BlockPos rack = RackGameTests.networkedRack(helper);
        BlockPos system = new BlockPos(3, 1, 4), reader = new BlockPos(2, 1, 4), keypunch = new BlockPos(3, 1, 5), printer = new BlockPos(5, 1, 4);
        RackGameTests.cable(helper, new BlockPos(3, 1, 3));
        place(helper, ModBlocks.MIDRANGE_SYSTEM.get(), system);
        place(helper, ModBlocks.KEYPUNCH.get(), keypunch);
        place(helper, ModBlocks.LINE_PRINTER.get(), printer);
        helper.setBlock(reader, ModBlocks.CARD_READER.get().defaultBlockState().setValue(CardReaderBlock.FACING, Direction.NORTH));
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    MinecraftServer server = helper.getLevel().getServer();
                    NetworkRef network = ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(rack));
                    helper.assertTrue(network != null, "No network");
                    helper.assertTrue(helper.getBlockEntity(system, MidrangeSystemBlockEntity.class).isOnline(), "Midrange System offline");
                    KeypunchBlockEntity punch = helper.getBlockEntity(keypunch, KeypunchBlockEntity.class);
                    CardReaderBlockEntity read = helper.getBlockEntity(reader, CardReaderBlockEntity.class);
                    LinePrinterBlockEntity print = helper.getBlockEntity(printer, LinePrinterBlockEntity.class);
                    helper.assertTrue(punch.isOnline() && read.isOnline() && print.isOnline(), "A peripheral is offline");
                    List<String> names = ElclDevices.list(server, network).stream().map(ElclDevices.Device::name).toList();
                    helper.assertTrue(names.containsAll(List.of("MIDRANGE01", "KEYPUNCH01", "CARDRDR01", "PRT01")), "Names " + names);

                    // Punch: a log makes four planks.
                    punch.grid().setItem(0, new ItemStack(Items.OAK_LOG));
                    punch.setItem(KeypunchBlockEntity.BLANK, new ItemStack(ModItems.PUNCH_CARD.get(), 2));
                    punch.punch();
                    ItemStack card = punch.getItem(KeypunchBlockEntity.PUNCHED);
                    Schematic recipe = card.get(ModDataComponents.PUNCHED_RECIPE.get());
                    helper.assertTrue(recipe != null && recipe.output().is(Items.OAK_PLANKS) && recipe.output().getCount() == 4, "Punched " + recipe);
                    helper.assertTrue(punch.getItem(KeypunchBlockEntity.BLANK).getCount() == 1, "Blank card not used");

                    // Read it, twice: still one recipe.
                    read.setItem(0, card.copyWithCount(1));
                    read.setItem(CardReaderBlockEntity.DISKETTE, new ItemStack(ModItems.DISKETTE_8IN.get()));
                    read.read();
                    read.read();
                    DisketteData data = read.getItem(CardReaderBlockEntity.DISKETTE).get(ModDataComponents.DISKETTE_RECIPES.get());
                    helper.assertTrue(data != null && data.recipes().size() == 1 && data.label().equals(DisketteStack.DEFAULT_LABEL), "Diskette " + data);
                    ElclSystem elcl = new ElclSystem(server, network);
                    try {
                        DisketteDevice drive = Diskettes.find(elcl, "CARDRDR01");
                        helper.assertTrue(drive.mounted().size() == 1, "No diskette mounted");
                    } catch (ElclException e) {
                        helper.fail("Card Reader not a drive: " + e.getMessage());
                    }

                    // Print the device list on three paper.
                    print.setItem(LinePrinterBlockEntity.PAPER, new ItemStack(Items.PAPER, 3));
                    try {
                        helper.assertTrue(Printers.find(elcl, "*DFT") == print, "Not the default printer");
                    } catch (ElclException e) {
                        helper.fail("No printer: " + e.getMessage());
                    }
                    print.printReport(LinePrinterBlockEntity.DEVICES, "");
                    ItemStack book = print.getItem(LinePrinterBlockEntity.OUTPUT);
                    WrittenBookContent content = book.get(DataComponents.WRITTEN_BOOK_CONTENT);
                    helper.assertTrue(book.is(Items.WRITTEN_BOOK) && content != null && content.author().equals("PRT01"), "Printed " + book);
                    helper.assertTrue(print.getItem(LinePrinterBlockEntity.PAPER).getCount() == 3 - content.pages().size(), "Paper not one a page");
                    helper.setBlock(system, Blocks.AIR);
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    CardReaderBlockEntity read = helper.getBlockEntity(reader, CardReaderBlockEntity.class);
                    helper.assertFalse(read.isOnline(), "Card Reader online with no system");
                    String refused = read.read().getString();
                    helper.assertTrue(refused.equals(Component.translatable("crt.encodedlogistics.machine.no_host").getString()), "Read with no system: " + refused);
                })
                .thenSucceed();
    }

    // Pages: 14 rows of about 19 characters; a long line takes more rows.
    static void printerPages(GameTestHelper helper) {
        List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            lines.add("line " + i);
        }
        helper.assertTrue(LinePrinterBlockEntity.pages(lines).size() == 3, "30 short lines: " + LinePrinterBlockEntity.pages(lines).size() + " pages");
        helper.assertTrue(LinePrinterBlockEntity.pages(List.of("x".repeat(19 * 14))).size() == 1, "A full page split");
        helper.assertTrue(LinePrinterBlockEntity.pages(List.of()).size() == 1, "Nothing printed no page");
        helper.succeed();
    }

    private static final Schematic LOG_TO_PLANKS = Schematic.of(Schematic.Kind.CRAFTING, List.of(new ItemStack(Items.OAK_LOG)),
            List.of(new ItemStack(Items.OAK_PLANKS, 4)));

    private static ItemStack diskette(String label) {
        ItemStack diskette = new ItemStack(ModItems.DISKETTE_8IN.get());
        diskette.set(ModDataComponents.DISKETTE_RECIPES.get(), new DisketteData(label, List.of(LOG_TO_PLANKS), List.of()));
        return diskette;
    }

    // A Midrange System cabled to RackGameTests' networked rack, with a Drive Bay: it IPLs when it comes online, then
    // runs. With a diskette in, its recipe is the network's, it's a Craft Plan choice, ELCL's batch host (1 job) and
    // diskette drive; a job on it crafts the planks itself and puts them in the network. Held, it isn't a choice; an IPL
    // takes it down a while.
    static void systemCrafts(GameTestHelper helper) {
        BlockPos rack = RackGameTests.networkedRack(helper);
        BlockPos system = new BlockPos(3, 1, 4), bay = new BlockPos(1, 2, 1);
        RackGameTests.driveBay(helper, bay);
        RackGameTests.cable(helper, new BlockPos(3, 1, 3));
        place(helper, ModBlocks.MIDRANGE_SYSTEM.get(), system);
        helper.startSequence()
                .thenIdle(5)
                .thenExecute(() -> {
                    MidrangeSystemBlockEntity midrange = helper.getBlockEntity(system, MidrangeSystemBlockEntity.class);
                    helper.assertTrue(midrange.isOnline() && !midrange.running() && midrange.iplLeft() > 0, "Not in IPL: " + midrange.statusCode());
                    helper.assertBlockProperty(system, MidrangeStates.STATE, MidrangeStates.Run.IPL);
                    helper.assertTrue(midrange.insert(diskette("TESTLIB")), "Diskette not taken");
                    helper.assertFalse(midrange.canTake(diskette("MORE")), "Slot B open without a cabinet");
                })
                .thenIdle(Config.MIDRANGE_IPL_TICKS.getAsInt() + 2)
                .thenExecute(() -> {
                    ServerLevel level = helper.getLevel();
                    MinecraftServer server = level.getServer();
                    MidrangeSystemBlockEntity midrange = helper.getBlockEntity(system, MidrangeSystemBlockEntity.class);
                    helper.assertTrue(midrange.running() && midrange.statusCode().equals("A6"), "Not running: " + midrange.statusCode());
                    helper.assertBlockProperty(system, MidrangeStates.STATE, MidrangeStates.Run.RUN);
                    NetworkRef network = ControllerStructures.networkOf(level, helper.absolutePos(rack));
                    helper.assertTrue(RecipeLibraries.recipes(server, network).contains(LOG_TO_PLANKS), "Diskette recipe not the network's");
                    ElclSystem elcl = new ElclSystem(server, network);
                    net.zagdrath.encodedlogistics.elcl.job.JobHost batch = JobHosts.find(elcl, "MIDRANGE01");
                    helper.assertTrue(batch != null && batch.capacity() == 1 && batch.online() && !batch.resumes(), "Not a batch host");
                    try {
                        helper.assertTrue(Diskettes.find(elcl, "MIDRANGE01").mounted().getFirst().label().equals("TESTLIB"), "Drive's diskette");
                    } catch (ElclException e) {
                        helper.fail("Not a diskette drive: " + e.getMessage());
                    }
                    BlockPos device = helper.absolutePos(system);
                    helper.assertTrue(CraftRequests.schedulers(level, device).contains(midrange), "Not a Craft Plan choice");
                    RackGameTests.storage(helper, bay).insert(LOG, 1, false);
                    CraftPlanner.Plan plan = CraftRequests.plan(level, device, PLANKS, 4);
                    helper.assertTrue(plan != null && plan.complete(), "Plan: " + plan);
                    helper.assertTrue(CraftRequests.start(level, device, plan, midrange) != null, "Job didn't start");
                })
                .thenIdle(Config.MIDRANGE_STEP_TICKS.getAsInt() + 10)
                .thenExecute(() -> {
                    MidrangeSystemBlockEntity midrange = helper.getBlockEntity(system, MidrangeSystemBlockEntity.class);
                    long planks = RackGameTests.storage(helper, bay).count(PLANKS);
                    helper.assertTrue(planks == 4, "Network has " + planks + " planks");
                    helper.assertTrue(midrange.jobs().isEmpty(), "Job not finished");
                    midrange.setHeld(true);
                    helper.assertFalse(CraftRequests.schedulers(helper.getLevel(), helper.absolutePos(system)).contains(midrange), "Held but a choice");
                    midrange.setHeld(false);
                    midrange.startIpl();
                    helper.assertFalse(midrange.running() || midrange.batchHost().online(), "Running during IPL");
                })
                .thenSucceed();
    }

    // Capacities: a Midrange System 1 thread / 64 / 1 batch job, with an Expansion Cabinet 2 / 128 / 2 and a second
    // diskette slot; an Integrated Midrange System 4 / 512 / 4, its magazine's diskettes its drive's.
    static void tiers(GameTestHelper helper) {
        BlockPos system = new BlockPos(2, 1, 2), integrated = new BlockPos(2, 1, 5);
        place(helper, ModBlocks.MIDRANGE_SYSTEM.get(), system);
        place(helper, ModBlocks.INTEGRATED_MIDRANGE.get(), integrated);
        MidrangeSystemBlockEntity midrange = helper.getBlockEntity(system, MidrangeSystemBlockEntity.class);
        helper.assertTrue(midrange.threads() == 1 && midrange.memory() == 64 && midrange.batchJobs() == 1 && !midrange.integrated(), "Tier 1");
        helper.setBlock(system.west(), ModBlocks.EXPANSION_CABINET.get().defaultBlockState().setValue(ExpansionCabinetBlock.FACING, Direction.NORTH));
        helper.assertTrue(midrange.expanded() && midrange.threads() == 2 && midrange.memory() == 128 && midrange.batchJobs() == 2, "With the cabinet");
        helper.assertTrue(midrange.insert(diskette("A")) && midrange.insert(diskette("B")) && midrange.diskettes().size() == 2, "Two diskettes");
        helper.assertTrue(midrange.recipes().size() == 2 && midrange.mounted().size() == 2, "Both diskettes read");

        MidrangeSystemBlockEntity tier2 = helper.getBlockEntity(integrated, MidrangeSystemBlockEntity.class);
        helper.assertTrue(tier2.integrated() && tier2.threads() == 4 && tier2.memory() == 512 && tier2.batchJobs() == 4, "Tier 2");
        helper.assertFalse(tier2.canTake(diskette("C")), "Tier 2 took a bare diskette");
        ItemStack magazine = new ItemStack(ModItems.DISKETTE_MAGAZINE.get());
        DisketteMagazineItem.insert(magazine, diskette("C"));
        DisketteMagazineItem.insert(magazine, diskette("D"));
        helper.assertTrue(tier2.insert(magazine), "Magazine not taken");
        helper.assertTrue(tier2.diskettes().size() == 2 && tier2.mounted().size() == 2 && tier2.mounted().get(1).label().equals("D"), "Magazine's diskettes");
        helper.succeed();
    }
}
