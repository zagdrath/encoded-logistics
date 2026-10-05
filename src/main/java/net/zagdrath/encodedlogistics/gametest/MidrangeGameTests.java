/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftPlanner;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.crafting.RecipeLibraries;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.device.DisketteDevice;
import net.zagdrath.encodedlogistics.elcl.device.Diskettes;
import net.zagdrath.encodedlogistics.elcl.device.Printers;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.exec.Reports;
import net.zagdrath.encodedlogistics.elcl.job.JobHosts;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.menu.KeypunchMenu;
import net.zagdrath.encodedlogistics.menu.MidrangePanelMenu;
import net.zagdrath.encodedlogistics.menu.PeripheralMenu;
import net.zagdrath.encodedlogistics.midrange.CardReaderBlock;
import net.zagdrath.encodedlogistics.midrange.CardReaderBlockEntity;
import net.zagdrath.encodedlogistics.midrange.DiskDriveBlockEntity;
import net.zagdrath.encodedlogistics.midrange.DisketteData;
import net.zagdrath.encodedlogistics.midrange.DisketteMagazineItem;
import net.zagdrath.encodedlogistics.midrange.DisketteStack;
import net.zagdrath.encodedlogistics.midrange.ExpansionCabinetBlock;
import net.zagdrath.encodedlogistics.midrange.FootprintBlock;
import net.zagdrath.encodedlogistics.midrange.IntegratedMidrangeBlock;
import net.zagdrath.encodedlogistics.midrange.KeypunchBlockEntity;
import net.zagdrath.encodedlogistics.midrange.LinePrinterBlockEntity;
import net.zagdrath.encodedlogistics.midrange.MidrangeStates;
import net.zagdrath.encodedlogistics.midrange.MidrangeSystemBlockEntity;
import net.zagdrath.encodedlogistics.midrange.Printout;
import net.zagdrath.encodedlogistics.midrange.TapeDriveBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NetworkStatus;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.StorageTier;

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

    // A Midrange System facing north is one block; a Line Printer's dummy is above it; an Integrated Midrange System's
    // five (left, right, and above the console) are round its master; shapes on every block; breaking a dummy breaks the
    // whole footprint.
    static void footprints(GameTestHelper helper) {
        BlockPos system = new BlockPos(1, 1, 1), printer = new BlockPos(1, 1, 4), integrated = new BlockPos(4, 1, 4);
        place(helper, ModBlocks.MIDRANGE_SYSTEM.get(), system);
        place(helper, ModBlocks.LINE_PRINTER.get(), printer);
        place(helper, ModBlocks.INTEGRATED_MIDRANGE.get(), integrated);
        helper.assertBlockNotPresent(ModBlocks.MIDRANGE_SYSTEM.get(), system.east());
        helper.assertBlockProperty(printer.above(), FootprintBlock.PART, FootprintBlock.Part.DUMMY);
        for (BlockPos pos : new BlockPos[] { integrated.west(), integrated.east(), integrated.west().above(), integrated.above() }) {
            helper.assertBlockProperty(pos, FootprintBlock.PART, FootprintBlock.Part.DUMMY);
        }
        helper.assertBlockNotPresent(ModBlocks.INTEGRATED_MIDRANGE.get(), integrated.east().above());
        helper.assertTrue(shaped(helper, system) && shaped(helper, printer) && shaped(helper, integrated.west().above()) && shaped(helper, integrated.east()),
                "A footprint block has no shape");
        // One outline over the whole model, from any of its blocks: the master's runs over its neighbours.
        net.minecraft.world.phys.AABB outline = helper.getBlockState(integrated).getShape(helper.getLevel(), helper.absolutePos(integrated)).bounds();
        helper.assertTrue(outline.minX < 0 && outline.maxX > 1 && outline.maxY > 1, "Integrated outline " + outline);
        net.minecraft.world.phys.AABB collision = helper.getBlockState(integrated)
                .getCollisionShape(helper.getLevel(), helper.absolutePos(integrated)).bounds();
        helper.assertTrue(collision.minX >= 0 && collision.maxX <= 1 && collision.maxY <= 1, "Integrated collision " + collision);
        FootprintBlock block = (FootprintBlock) ModBlocks.INTEGRATED_MIDRANGE.get();
        helper.assertTrue(helper.absolutePos(integrated).equals(block.master(helper.getLevel(), helper.absolutePos(integrated.west().above()),
                helper.getBlockState(integrated.west().above()))), "A dummy can't find its master");
        helper.getLevel().destroyBlock(helper.absolutePos(printer.above()), false);
        helper.assertBlockNotPresent(ModBlocks.LINE_PRINTER.get(), printer);
        helper.getLevel().destroyBlock(helper.absolutePos(integrated.west().above()), false);
        helper.assertBlockNotPresent(ModBlocks.INTEGRATED_MIDRANGE.get(), integrated);
        helper.assertBlockNotPresent(ModBlocks.INTEGRATED_MIDRANGE.get(), integrated.east());
        helper.succeed();
    }

    // The Integrated system's click zones, by where a click lands on the model, for each facing: the console hood and
    // keyboard; the left body and magazine unit (the control panel); the right body (nothing).
    static void integratedZones(GameTestHelper helper) {
        BlockPos master = helper.absolutePos(new BlockPos(2, 1, 2));
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            // Model px (facing north) to the world, as the blockstate turns the model.
            java.util.function.BiFunction<double[], Direction, net.minecraft.world.phys.Vec3> at = (p, f) -> {
                double x = p[0], z = p[2];
                double wx = switch (f) {
                    case EAST -> 16 - z;
                    case SOUTH -> 16 - x;
                    case WEST -> z;
                    default -> x;
                };
                double wz = switch (f) {
                    case EAST -> x;
                    case SOUTH -> 16 - z;
                    case WEST -> 16 - x;
                    default -> z;
                };
                return new net.minecraft.world.phys.Vec3(master.getX() + wx / 16, master.getY() + p[1] / 16, master.getZ() + wz / 16);
            };
            helper.assertTrue(IntegratedMidrangeBlock.zone(master, facing, at.apply(new double[] { 2, 18, 9 }, facing)) == IntegratedMidrangeBlock.Zone.CONSOLE,
                    "The console hood, facing " + facing);
            helper.assertTrue(IntegratedMidrangeBlock.zone(master, facing, at.apply(new double[] { 0, 14, 4.5 }, facing)) == IntegratedMidrangeBlock.Zone.CONSOLE,
                    "The keyboard, facing " + facing);
            helper.assertTrue(IntegratedMidrangeBlock.zone(master, facing, at.apply(new double[] { 15, 6, 4 }, facing)) == IntegratedMidrangeBlock.Zone.CONTROL_PANEL,
                    "The left body, facing " + facing);
            helper.assertTrue(IntegratedMidrangeBlock.zone(master, facing, at.apply(new double[] { 15, 16, 10 }, facing)) == IntegratedMidrangeBlock.Zone.CONTROL_PANEL,
                    "The magazine unit, facing " + facing);
            helper.assertTrue(IntegratedMidrangeBlock.zone(master, facing, at.apply(new double[] { -3, 6, 4 }, facing)) == IntegratedMidrangeBlock.Zone.NONE,
                    "The right body, facing " + facing);
        }
        helper.succeed();
    }

    // A cabinet west of the system: attached pos (the system on its +x side), the system expanded; broken, it isn't. One
    // to the east: neg; a second on the other side then stays unattached, as does one facing another way.
    static void expansionCabinet(GameTestHelper helper) {
        BlockPos system = new BlockPos(2, 1, 2), left = system.west(), right = system.east(), other = new BlockPos(2, 1, 4);
        place(helper, ModBlocks.MIDRANGE_SYSTEM.get(), system);
        BlockState cabinet = ModBlocks.EXPANSION_CABINET.get().defaultBlockState().setValue(ExpansionCabinetBlock.FACING, Direction.NORTH);
        helper.setBlock(left, cabinet);
        helper.assertBlockProperty(left, ExpansionCabinetBlock.ATTACHED, MidrangeStates.Side.POS);
        helper.assertBlockProperty(system, MidrangeStates.EXPANSION, true);
        helper.setBlock(left, Blocks.AIR);
        helper.assertBlockProperty(system, MidrangeStates.EXPANSION, false);

        helper.setBlock(right, cabinet);
        helper.assertBlockProperty(right, ExpansionCabinetBlock.ATTACHED, MidrangeStates.Side.NEG);
        helper.assertBlockProperty(system, MidrangeStates.EXPANSION, true);
        helper.setBlock(left, cabinet);
        helper.assertBlockProperty(left, ExpansionCabinetBlock.ATTACHED, MidrangeStates.Side.NONE);
        helper.assertBlockProperty(system, MidrangeStates.EXPANSION, true);
        // The attached one gone: the other takes its place.
        helper.setBlock(right, Blocks.AIR);
        helper.assertBlockProperty(left, ExpansionCabinetBlock.ATTACHED, MidrangeStates.Side.POS);
        helper.assertBlockProperty(system, MidrangeStates.EXPANSION, true);

        // Facing another way beside another system: nothing.
        place(helper, ModBlocks.MIDRANGE_SYSTEM.get(), other);
        helper.setBlock(other.west(), cabinet.setValue(ExpansionCabinetBlock.FACING, Direction.SOUTH));
        helper.assertBlockProperty(other.west(), ExpansionCabinetBlock.ATTACHED, MidrangeStates.Side.NONE);
        helper.assertBlockProperty(other, MidrangeStates.EXPANSION, false);
        helper.succeed();
    }

    // A Midrange System (its network's controller, charged) with a Card Reader beside it, a Keypunch in front and a Line
    // Printer on its other side. They're online and named (MIDRANGE01, KEYPUNCH01, CARDRDR01, PRT01); the Keypunch
    // punches a log-to-planks card; the Card Reader reads it onto a diskette (twice: it replaces itself) and is ELCL's
    // diskette drive; the Line Printer prints the device list as a Printout, one paper a page, and is *DFT. With the system
    // gone, they're offline and refuse.
    static void peripherals(GameTestHelper helper) {
        BlockPos system = new BlockPos(3, 1, 4), reader = new BlockPos(2, 1, 4), keypunch = new BlockPos(3, 1, 5), printer = new BlockPos(4, 1, 4);
        place(helper, ModBlocks.MIDRANGE_SYSTEM.get(), system);
        helper.getBlockEntity(system, MidrangeSystemBlockEntity.class).charge(50_000);
        place(helper, ModBlocks.KEYPUNCH.get(), keypunch);
        place(helper, ModBlocks.LINE_PRINTER.get(), printer);
        helper.setBlock(reader, ModBlocks.CARD_READER.get().defaultBlockState().setValue(CardReaderBlock.FACING, Direction.NORTH));
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    MinecraftServer server = helper.getLevel().getServer();
                    NetworkRef network = ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(system));
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
                    ItemStack paper = printed(helper, printer);
                    Printout content = paper.get(ModDataComponents.PRINTOUT.get());
                    helper.assertTrue(paper.is(ModItems.PRINTOUT.get()) && content != null && content.printer().equals("PRT01")
                            && content.title().equals("DEVICE LIST") && content.report().equals(Reports.DEVICES), "Printed " + content);
                    helper.assertTrue(content.pages().getFirst().lines().stream().anyMatch(line -> line.text().startsWith("KEYPUNCH01")), "Device rows");
                    helper.assertTrue(print.getItem(LinePrinterBlockEntity.PAPER).getCount() == 3 - content.pages().size(), "Paper not one a page");
                    // Out of paper part-way: the pages so far come out, the rest waits for paper.
                    print.getItem(LinePrinterBlockEntity.PAPER).setCount(1);
                    List<String> lines = new java.util.ArrayList<>();
                    for (int i = 0; i < 100; i++) {
                        lines.add("line " + i);
                    }
                    print.print("LONG", Reports.SPOOLED + "LONG", lines);
                    helper.assertTrue(print.waiting() != null && print.waiting().pages().size() == 2 && !print.hasPaper(), "Rest not waiting");
                    print.insert(new ItemStack(Items.PAPER, 5));
                })
                .thenIdle(70)
                .thenExecute(() -> {
                    LinePrinterBlockEntity print = helper.getBlockEntity(printer, LinePrinterBlockEntity.class);
                    helper.assertTrue(print.waiting() == null && print.paper() == 3, "Rest not printed: " + print.paper());
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

    // What a printer at a position printed: the item that came out of its front (empty for none).
    static ItemStack printed(GameTestHelper helper, BlockPos printer) {
        net.minecraft.world.phys.AABB around = new net.minecraft.world.phys.AABB(helper.absolutePos(printer)).inflate(1.5);
        return helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, around).stream().map(item -> item.getItem())
                .findFirst().orElse(ItemStack.EMPTY);
    }

    // The green screens without slots (HANDOFF 3): items go in on the blocks and out with a sneak-use; the control
    // panel's 8=Make default library mounts that diskette first, 4=Eject gives it to the player; the Keypunch's typed
    // item names fill its grid (an unknown one doesn't); the Card Reader's plan says which cards are new and which
    // replace a recipe; the Line Printer takes paper.
    @SuppressWarnings("removal")
    static void screens(GameTestHelper helper) {
        BlockPos system = new BlockPos(2, 1, 2), keypunch = new BlockPos(5, 1, 2), reader = new BlockPos(2, 1, 5), printer = new BlockPos(5, 1, 5);
        place(helper, ModBlocks.MIDRANGE_SYSTEM.get(), system);
        helper.setBlock(system.west(), ModBlocks.EXPANSION_CABINET.get().defaultBlockState().setValue(ExpansionCabinetBlock.FACING, Direction.NORTH));
        place(helper, ModBlocks.KEYPUNCH.get(), keypunch);
        place(helper, ModBlocks.LINE_PRINTER.get(), printer);
        helper.setBlock(reader, ModBlocks.CARD_READER.get().defaultBlockState().setValue(CardReaderBlock.FACING, Direction.NORTH));
        net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();

        MidrangeSystemBlockEntity midrange = helper.getBlockEntity(system, MidrangeSystemBlockEntity.class);
        helper.assertTrue(midrange.insert(diskette("A")) && midrange.insert(diskette("B")), "Diskettes not taken");
        MidrangePanelMenu panel = new MidrangePanelMenu(1, player.getInventory(), midrange, midrange, PeripheralMenu.Opening.SERVER);
        panel.setText(PeripheralMenu.OPTION + 1, "8");
        helper.assertTrue(midrange.defaultDrive() == 1 && midrange.mounted().getFirst().label().equals("B"), "Drive 2 not the default");
        panel.setText(PeripheralMenu.OPTION + 0, "4");
        helper.assertTrue(midrange.positions().getFirst().isEmpty() && player.getInventory().countItem(ModItems.DISKETTE_8IN.get()) == 1, "Not ejected to the player");
        helper.assertTrue(midrange.ejectLast().is(ModItems.DISKETTE_8IN.get()) && midrange.diskettes().isEmpty(), "Sneak-use didn't eject");

        KeypunchBlockEntity punch = helper.getBlockEntity(keypunch, KeypunchBlockEntity.class);
        KeypunchMenu keys = new KeypunchMenu(2, player.getInventory(), punch, punch, PeripheralMenu.Opening.SERVER);
        keys.setText(0, "oak_log");
        keys.setText(1, "no_such_thing");
        punch.updateResult();
        helper.assertTrue(punch.grid().getItem(0).is(Items.OAK_LOG) && punch.grid().getItem(1).isEmpty(), "Grid from typed names");
        helper.assertTrue(punch.result().getItem(0).is(Items.OAK_PLANKS), "Grid crafts " + punch.result().getItem(0));
        helper.assertTrue(KeypunchBlockEntity.item("minecraft:iron_ingot") == Items.IRON_INGOT && KeypunchBlockEntity.item("logic_die") == ModItems.LOGIC_DIE.get(),
                "Item names");
        ItemStack blanks = new ItemStack(ModItems.PUNCH_CARD.get(), 5);
        helper.assertTrue(punch.insert(blanks) && blanks.isEmpty() && punch.getItem(KeypunchBlockEntity.BLANK).getCount() == 5, "Blank cards not loaded");
        helper.assertTrue(punch.eject().getFirst().getCount() == 5, "Cards not taken out");

        CardReaderBlockEntity read = helper.getBlockEntity(reader, CardReaderBlockEntity.class);
        ItemStack card = new ItemStack(ModItems.PUNCH_CARD.get());
        card.set(ModDataComponents.PUNCHED_RECIPE.get(), LOG_TO_PLANKS);
        ItemStack other = new ItemStack(ModItems.PUNCH_CARD.get());
        other.set(ModDataComponents.PUNCHED_RECIPE.get(), Schematic.of(Schematic.Kind.CRAFTING, List.of(new ItemStack(Items.OAK_PLANKS, 2)),
                List.of(new ItemStack(Items.STICK, 4))));
        helper.assertTrue(read.insert(card) && read.insert(other) && read.insert(diskette("C")), "Reader didn't take cards and a diskette");
        List<String> plan = CardReaderBlockEntity.plan(DisketteStack.data(read.getItem(CardReaderBlockEntity.DISKETTE)), read.hopper());
        helper.assertTrue(plan.equals(List.of("replaces 1", "new")), "Plan " + plan);
        helper.assertTrue(read.eject().getFirst().is(ModItems.DISKETTE_8IN.get()) && read.eject().size() == 2, "Reader eject order");

        LinePrinterBlockEntity print = helper.getBlockEntity(printer, LinePrinterBlockEntity.class);
        ItemStack paper = new ItemStack(Items.PAPER, 10);
        helper.assertTrue(print.insert(paper) && print.paper() == 10 && !print.insert(new ItemStack(Items.STICK)), "Paper");
        helper.assertTrue(print.status().equals("*OFFLINE"), "Status with no system: " + print.status());
        helper.succeed();
    }

    // A Midrange System is its network's controller (HANDOFF 4): with power, a Drive Bay beside it is online on its
    // lanes (4 faces' worth); an Integrated system has 6 faces' worth and a 2U's buffer. Another controller cabled on
    // (a Network Controller block) is a conflict: no lanes, the system shows E8; gone, it runs again.
    @SuppressWarnings("removal")
    static void controller(GameTestHelper helper) {
        BlockPos system = new BlockPos(2, 1, 2), bay = new BlockPos(3, 1, 2), integrated = new BlockPos(2, 1, 6), cable = new BlockPos(1, 1, 2),
                block = new BlockPos(0, 1, 2), behind = integrated.south(), beside = integrated.east(2);
        place(helper, ModBlocks.MIDRANGE_SYSTEM.get(), system);
        place(helper, ModBlocks.INTEGRATED_MIDRANGE.get(), integrated);
        RackGameTests.driveBay(helper, bay);
        // The Integrated system takes the network at its centre's back, not at its side blocks.
        RackGameTests.driveBay(helper, behind);
        RackGameTests.driveBay(helper, beside);
        helper.getBlockEntity(system, MidrangeSystemBlockEntity.class).charge(50_000);
        helper.getBlockEntity(integrated, MidrangeSystemBlockEntity.class).charge(50_000);
        helper.startSequence()
                .thenIdle(10)
                .thenExecute(() -> {
                    MinecraftServer server = helper.getLevel().getServer();
                    MidrangeSystemBlockEntity midrange = helper.getBlockEntity(system, MidrangeSystemBlockEntity.class);
                    NetworkRef network = ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(bay));
                    helper.assertTrue(network != null && network.equals(ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(system))),
                            "The bay and system aren't on one network");
                    ControllerStructures.NetworkStats stats = ControllerStructures.stats(server, network);
                    int face = Config.LANES_PER_CONTROLLER_FACE.getAsInt();
                    helper.assertTrue(stats != null && stats.online() && stats.laneCapacity() == 4 * face && stats.lanesUsed() == 1, "Lanes " + stats);
                    helper.assertTrue(midrange.isOnline() && helper.getBlockEntity(bay, DriveBayBlockEntity.class).isOnline(), "Not online");
                    helper.assertTrue(midrange.getCapacity() == 4 * Config.CONTROLLER_ENERGY_PER_BLOCK.getAsInt(), "Buffer " + midrange.getCapacity());
                    MidrangeSystemBlockEntity tier2 = helper.getBlockEntity(integrated, MidrangeSystemBlockEntity.class);
                    ControllerStructures.NetworkStats own = ControllerStructures.stats(server, ControllerStructures.networkOf(helper.getLevel(),
                            helper.absolutePos(integrated)));
                    helper.assertTrue(own != null && own.laneCapacity() == 6 * face && tier2.getCapacity() == 20 * Config.CONTROLLER_ENERGY_PER_BLOCK.getAsInt(),
                            "Integrated " + own + " " + tier2.getCapacity());
                    helper.assertTrue(helper.getBlockEntity(behind, DriveBayBlockEntity.class).isOnline(), "Not on the network at its back");
                    helper.assertFalse(helper.getBlockEntity(beside, DriveBayBlockEntity.class).isOnline(), "On the network at its side");
                    // Work with Inventory's deposit: everything outside the hotbar goes in.
                    net.minecraft.server.level.ServerPlayer player = helper.makeMockServerPlayerInLevel();
                    player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 32));
                    player.getInventory().setItem(0, new ItemStack(Items.DIRT, 5));
                    net.zagdrath.encodedlogistics.terminal.TerminalContext context = new net.zagdrath.encodedlogistics.terminal.TerminalContext(server, network,
                            null, player);
                    net.zagdrath.encodedlogistics.terminal.TerminalService.handle(context, net.zagdrath.encodedlogistics.terminal.TerminalService.QUERY,
                            "deposit *all");
                    helper.assertTrue(player.getInventory().getItem(9).isEmpty() && player.getInventory().getItem(0).getCount() == 5, "Deposit took the wrong stacks");
                    helper.assertTrue(RackGameTests.storage(helper, bay).count(ItemKey.of(new ItemStack(Items.COBBLESTONE))) == 32, "Not deposited");
                    net.zagdrath.encodedlogistics.terminal.TerminalService.handle(context, net.zagdrath.encodedlogistics.terminal.TerminalService.QUERY,
                            "deposit 0");
                    helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "Hotbar slot not deposited");
                    RackGameTests.cable(helper, cable);
                    RackGameTests.controller(helper, block, 20_000);
                })
                .thenIdle(10)
                .thenExecute(() -> {
                    MidrangeSystemBlockEntity midrange = helper.getBlockEntity(system, MidrangeSystemBlockEntity.class);
                    helper.assertTrue(midrange.networkStatus() == NetworkStatus.CONFLICT && midrange.statusCode().equals("E8"), "No conflict: "
                            + midrange.networkStatus() + " " + midrange.statusCode());
                    helper.assertFalse(helper.getBlockEntity(bay, DriveBayBlockEntity.class).isOnline(), "Bay online in a conflict");
                    helper.setBlock(block, Blocks.AIR);
                })
                .thenIdle(10)
                .thenExecute(() -> helper.assertTrue(helper.getBlockEntity(system, MidrangeSystemBlockEntity.class).isOnline(), "Not back after the conflict"))
                .thenSucceed();
    }

    // The Midrange line's storage, on a Midrange System's network: a Disk Drive serves its Storage Drive as hot storage
    // once it has spun up (DISK01), and spins down before it comes out; a Tape Drive (TAPE01, 1 x 3) threads its reel,
    // archives an item onto it from hot storage, and reads a recall of it back.
    @SuppressWarnings("removal")
    static void storageDrives(GameTestHelper helper) {
        BlockPos system = new BlockPos(2, 1, 2), disk = new BlockPos(3, 1, 2), tape = new BlockPos(2, 1, 3);
        ItemKey cobble = ItemKey.of(new ItemStack(Items.COBBLESTONE));
        place(helper, ModBlocks.MIDRANGE_SYSTEM.get(), system);
        helper.getBlockEntity(system, MidrangeSystemBlockEntity.class).charge(50_000);
        helper.setBlock(disk, ModBlocks.DISK_DRIVE.get().defaultBlockState().setValue(FootprintBlock.FACING, Direction.NORTH));
        BlockState tapeState = ModBlocks.TAPE_DRIVE.get().defaultBlockState().setValue(FootprintBlock.FACING, Direction.SOUTH);
        helper.setBlock(tape, tapeState);
        ModBlocks.TAPE_DRIVE.get().setPlacedBy(helper.getLevel(), helper.absolutePos(tape), tapeState, null, ItemStack.EMPTY);
        helper.assertBlockProperty(tape.above(2), FootprintBlock.TAPE_PART, FootprintBlock.Part.TOP);
        // Quicker than in play, to fit the test's time (only Tape Drives use these).
        int load = Config.TAPE_LOAD_TICKS.getAsInt(), base = Config.TAPE_DRIVE_BASE_TICKS.getAsInt();
        Config.TAPE_LOAD_TICKS.set(10);
        Config.TAPE_DRIVE_BASE_TICKS.set(10);
        DiskDriveBlockEntity diskDrive = helper.getBlockEntity(disk, DiskDriveBlockEntity.class);
        TapeDriveBlockEntity tapeDrive = helper.getBlockEntity(tape, TapeDriveBlockEntity.class);
        helper.assertTrue(diskDrive.insert(new ItemStack(ModItems.storageDrive(StorageTier.K8).get())), "Pack not taken");
        helper.assertTrue(tapeDrive.insert(new ItemStack(ModItems.TAPE_REEL.get())), "Reel not taken");
        helper.assertTrue(diskDrive.drive(0) == null, "Readable before spin-up");
        helper.startSequence()
                .thenIdle(Config.DISK_SPIN_UP_TICKS.getAsInt() + 10)
                .thenExecute(() -> {
                    MinecraftServer server = helper.getLevel().getServer();
                    NetworkRef network = ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(disk));
                    helper.assertTrue(diskDrive.isOnline() && diskDrive.state() == DiskDriveBlockEntity.State.SPINNING, "Disk " + diskDrive.state());
                    List<String> names = ElclDevices.list(server, network).stream().map(ElclDevices.Device::name).toList();
                    helper.assertTrue(names.containsAll(List.of("DISK01", "TAPE01")), "Names " + names);
                    NetworkStorage storage = RackGameTests.storage(helper, disk);
                    helper.assertTrue(storage.insert(cobble, 100, false) == 100 && storage.count(cobble) == 100, "Not stored on the pack");
                })
                .thenWaitUntil(() -> helper.assertTrue(tapeDrive.state() == TapeDriveBlockEntity.State.READY, "Reel not threaded: " + tapeDrive.state()))
                .thenExecute(tapeDrive::archiveForTest)
                .thenWaitUntil(() -> helper.assertTrue(tapeDrive.used() == 100, "Not archived: " + tapeDrive.used() + " " + tapeDrive.state()))
                .thenExecute(() -> {
                    NetworkStorage storage = RackGameTests.storage(helper, disk);
                    helper.assertTrue(storage.count(cobble) == 0 && storage.cold().count(cobble) == 100,
                            "Cold count " + storage.count(cobble) + " " + storage.cold().count(cobble));
                    // Taking some asks for a recall.
                    storage.extract(cobble, 40, false);
                })
                .thenWaitUntil(() -> helper.assertTrue(RackGameTests.storage(helper, disk).extractFromDrives(cobble, 100, true) >= 40, "Not recalled"))
                .thenExecute(() -> {
                    helper.assertTrue(diskDrive.ejectLater(helper.makeMockServerPlayerInLevel()), "No spin-down");
                    helper.assertTrue(diskDrive.drive(0) == null && diskDrive.state() == DiskDriveBlockEntity.State.SPIN_DOWN, "Readable while spinning down");
                })
                .thenIdle(Config.DISK_SPIN_DOWN_TICKS.getAsInt() + 2)
                .thenExecute(() -> {
                    Config.TAPE_LOAD_TICKS.set(load);
                    Config.TAPE_DRIVE_BASE_TICKS.set(base);
                    helper.assertTrue(diskDrive.pack().isEmpty(), "Pack still in");
                })
                .thenSucceed();
    }

    // Printout pages (HANDOFF 9): 42 body rows a page; a line longer than 56 columns goes on the next row after a light
    // ">"; an ink mark gives a line its ink; nothing printed is still a page. The header: system, title centred, date;
    // the footer ends the report on the last page.
    static void printerPages(GameTestHelper helper) {
        List<String> lines = new java.util.ArrayList<>();
        for (int i = 0; i < 43; i++) {
            lines.add("line " + i);
        }
        helper.assertTrue(Printout.paginate(lines.subList(0, 30)).size() == 1 && Printout.paginate(lines).size() == 2, "Pages of short lines");
        List<Printout.Page> wrapped = Printout.paginate(List.of("x".repeat(120), Printout.MARK_RED + "alert", Printout.MARK_LIGHT + "cold"));
        List<Printout.Line> rows = wrapped.getFirst().lines();
        helper.assertTrue(rows.size() == 5 && rows.get(0).text().length() == 56 && rows.get(1).text().startsWith(">") && rows.get(1).ink() == Printout.LIGHT
                && rows.get(2).text().equals(">" + "x".repeat(9)), "Wrapped " + rows);
        helper.assertTrue(rows.get(3).ink() == Printout.RED && rows.get(3).text().equals("alert") && rows.get(4).ink() == Printout.LIGHT, "Inks " + rows);
        helper.assertTrue(Printout.paginate(List.of()).size() == 1, "Nothing printed no page");
        Printout printout = new Printout("JOB LOG - NAP", Reports.JOB_LOG, "ELNET01", "DAY 2  14:32:07", "PRT01", Printout.paginate(lines));
        String header = printout.header(0);
        helper.assertTrue(header.length() == 56 && header.startsWith("ELNET01") && header.endsWith("DAY 2  14:32:07") && header.contains("JOB LOG - NAP"),
                "Header '" + header + "'");
        helper.assertTrue(printout.subheader(1).endsWith("PAGE   2 OF 2  ") && printout.footer(0).contains("CONTINUED") && printout.footer(1).contains("END OF REPORT"),
                "Page lines");
        helper.succeed();
    }

    private static final Schematic LOG_TO_PLANKS = Schematic.of(Schematic.Kind.CRAFTING, List.of(new ItemStack(Items.OAK_LOG)),
            List.of(new ItemStack(Items.OAK_PLANKS, 4)));

    private static ItemStack diskette(String label) {
        ItemStack diskette = new ItemStack(ModItems.DISKETTE_8IN.get());
        diskette.set(ModDataComponents.DISKETTE_RECIPES.get(), new DisketteData(label, List.of(LOG_TO_PLANKS), List.of()));
        return diskette;
    }

    // A Midrange System as its network's controller (charged), with a Drive Bay beside it: it IPLs when it comes online, then
    // runs. With a diskette in, its recipe is the network's, it's a Craft Plan choice, ELCL's batch host (1 job) and
    // diskette drive; a job on it crafts the planks itself and puts them in the network. Held, it isn't a choice; an IPL
    // takes it down a while.
    static void systemCrafts(GameTestHelper helper) {
        BlockPos system = new BlockPos(3, 1, 4), bay = new BlockPos(4, 1, 4);
        RackGameTests.driveBay(helper, bay);
        place(helper, ModBlocks.MIDRANGE_SYSTEM.get(), system);
        helper.getBlockEntity(system, MidrangeSystemBlockEntity.class).charge(50_000);
        helper.startSequence()
                .thenIdle(10)
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
                    NetworkRef network = ControllerStructures.networkOf(level, helper.absolutePos(system));
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
