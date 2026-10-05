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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.device.DisketteDevice;
import net.zagdrath.encodedlogistics.elcl.device.Diskettes;
import net.zagdrath.encodedlogistics.elcl.device.Printers;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.midrange.CardReaderBlock;
import net.zagdrath.encodedlogistics.midrange.CardReaderBlockEntity;
import net.zagdrath.encodedlogistics.midrange.DisketteData;
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

// The Midrange line's blocks: footprints placed and broken whole, their shapes, and the Expansion Cabinet attaching to a
// Midrange System on either side (one per system, the same facing) and coming loose again; the peripherals working for
// a Midrange System on their network, and the Line Printer's pages.
final class MidrangeGameTests {
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
}
