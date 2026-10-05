/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftPlanner;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.item.HandheldTerminalItem;
import net.zagdrath.encodedlogistics.item.LtoTapeItem;
import net.zagdrath.encodedlogistics.menu.RackConsoleMenu;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.device.RackConsoleDevice;
import net.zagdrath.encodedlogistics.rack.device.TapeLibraryDevice;
import net.zagdrath.encodedlogistics.rack.device.WirelessControllerDevice;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.DriveStorage;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.TapeGeneration;

// Batch 3: Tape Libraries (archiving, recalling, cold items in plans), the Wireless Controller, the Rack Console, 6U
// devices and tape upgrades.
final class Rack3GameTests {
    private static final StorageKey COBBLESTONE = StorageKey.of(new ItemStack(Items.COBBLESTONE)), DIRT = StorageKey.of(new ItemStack(Items.DIRT)),
            LOG = StorageKey.of(new ItemStack(Items.OAK_LOG)), PLANKS = StorageKey.of(new ItemStack(Items.OAK_PLANKS));
    private static final BlockPos BAY = new BlockPos(1, 2, 1);

    private Rack3GameTests() {}

    // A 4U library in the networked rack with one LTO-6 tape and a drive in each bay; archiving whenever items are old
    // (now), whatever hot storage's fill.
    private static TapeLibraryDevice library(GameTestHelper helper, BlockPos master) {
        TapeLibraryDevice library = RackGameTests.install(helper, master, RackDeviceType.TAPE_LIBRARY_4U, 1, TapeLibraryDevice.class);
        library.items().set(0, new ItemStack(ModItems.tape(TapeGeneration.LTO_6).get()));
        library.items().set(24, new ItemStack(ModItems.LTO_TAPE_DRIVE.get()));
        library.items().set(25, new ItemStack(ModItems.LTO_TAPE_DRIVE.get()));
        library.itemsChanged();
        library.setTrigger(false, 0);
        library.setAgeTicksForTest(0);
        return library;
    }

    // Items untouched for longer than the age go from the drives to tape; pinned ones stay hot. The tape remembers them.
    static void tapeArchives(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        RackGameTests.driveBay(helper, BAY);
        TapeLibraryDevice library = library(helper, master);
        library.pinned().set(0, new ItemStack(Items.DIRT));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(library.isOnline(), "Library offline");
                    NetworkStorage storage = RackGameTests.storage(helper, master);
                    helper.assertTrue(storage.insert(COBBLESTONE, 100, false) == 100 && storage.insert(DIRT, 50, false) == 50, "Not stored");
                    library.scanSoon();
                })
                .thenIdle(150)
                .thenExecute(() -> {
                    NetworkStorage storage = RackGameTests.storage(helper, master);
                    helper.assertTrue(storage.count(COBBLESTONE) == 0, "Cobblestone still hot: " + storage.count(COBBLESTONE));
                    helper.assertTrue(storage.coldList().getOrDefault(COBBLESTONE, 0L) == 100, "On tape: " + storage.coldList());
                    helper.assertTrue(storage.listAll().get(COBBLESTONE) == 100, "Hot and cold: " + storage.listAll().get(COBBLESTONE));
                    helper.assertTrue(storage.count(DIRT) == 50 && !storage.coldList().containsKey(DIRT), "Pinned dirt archived");
                    UUID tape = LtoTapeItem.id(library.items().get(0));
                    helper.assertTrue(tape != null && DriveStorage.get(helper.getLevel().getServer()).count(tape, COBBLESTONE) == 100, "Tape doesn't hold it");
                    helper.assertTrue(LtoTapeItem.stats(library.items().get(0)).typesUsed() == 1, "Tape stats not refreshed");
                })
                .thenSucceed();
    }

    // Taking more of an item than is hot recalls the rest from tape: it comes back to hot storage, and the tape loses it.
    static void tapeRecalls(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        RackGameTests.driveBay(helper, BAY);
        TapeLibraryDevice library = library(helper, master);
        library.setAgeTicksForTest(Long.MAX_VALUE / 2);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    UUID tape = library.tapes().getFirst().id();
                    DriveStorage.get(helper.getLevel().getServer()).insert(tape, TapeGeneration.LTO_6, COBBLESTONE, 64, false);
                    NetworkStorage storage = RackGameTests.storage(helper, master);
                    helper.assertTrue(storage.cold().count(COBBLESTONE) == 64, "Not on tape: " + storage.cold().count(COBBLESTONE));
                    helper.assertTrue(storage.cold().eta(COBBLESTONE) > 0, "No recall time");
                    helper.assertTrue(storage.extract(COBBLESTONE, 10, false) == 0, "Cold items came out directly");
                    helper.assertTrue(storage.cold().progress(COBBLESTONE) >= 0, "No recall queued");
                })
                .thenIdle(130)
                .thenExecute(() -> {
                    NetworkStorage storage = RackGameTests.storage(helper, master);
                    helper.assertTrue(storage.count(COBBLESTONE) == 10, "Recalled: " + storage.count(COBBLESTONE));
                    helper.assertTrue(storage.cold().count(COBBLESTONE) == 54, "Left on tape: " + storage.cold().count(COBBLESTONE));
                    helper.assertTrue(storage.extract(COBBLESTONE, 10, false) == 10, "Recalled items not takeable");
                })
                .thenSucceed();
    }

    // A plan counts what's on tape as stored: it takes what's hot and recalls the rest.
    static void planWithTape(GameTestHelper helper) {
        Schematic planks = Schematic.of(Schematic.Kind.CRAFTING, List.of(new ItemStack(Items.OAK_LOG)), List.of(new ItemStack(Items.OAK_PLANKS, 4)));
        CraftPlanner.Plan plan = CraftPlanner.plan(Map.of(LOG, 1L), Map.of(LOG, 3L), CraftPlanner.byOutput(List.of(planks)), PLANKS, 16);
        helper.assertTrue(plan.complete(), "Plan incomplete: " + plan.missing());
        helper.assertTrue(plan.take().get(LOG) == 1 && plan.recall().get(LOG) == 3, "Take " + plan.take() + ", recall " + plan.recall());
        CraftPlanner.Line log = plan.lines().stream().filter(line -> line.key().equals(LOG)).findFirst().orElseThrow();
        helper.assertTrue(log.have() == 4 && log.cold() == 3, "Log line " + log);
        CraftPlanner.Plan short_ = CraftPlanner.plan(Map.of(LOG, 1L), Map.of(LOG, 2L), CraftPlanner.byOutput(List.of(planks)), PLANKS, 16);
        helper.assertTrue(!short_.complete() && short_.missing() == 1, "Short plan complete");
        helper.succeed();
    }

    // A Handheld Terminal linked to a Wireless Controller reaches the network through it (anywhere); unlinked, it doesn't.
    @SuppressWarnings("removal")
    static void wirelessController(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        WirelessControllerDevice controller = RackGameTests.install(helper, master, RackDeviceType.WIRELESS_CONTROLLER, 1, WirelessControllerDevice.class);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack handheld = new ItemStack(ModItems.HANDHELD_TERMINAL.get());
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(controller.isOnline(), "Controller offline");
                    helper.assertTrue(HandheldTerminalItem.wireless(helper.getLevel().getServer(), player, handheld) == null, "Unlinked terminal served");
                    helper.assertTrue(controller.link(player, handheld), "Not linked");
                    helper.assertTrue(HandheldTerminalItem.network(handheld) != null && controller.serves(player), "Link not recorded");
                    helper.assertTrue(HandheldTerminalItem.wireless(helper.getLevel().getServer(), player, handheld) == controller, "Not served");
                    controller.handleAction(player, WirelessControllerDevice.ACTION_UNLINK, 0, "");
                    helper.assertTrue(HandheldTerminalItem.wireless(helper.getLevel().getServer(), player, handheld) == null, "Unlinked terminal still served");
                })
                .thenSucceed();
    }

    // The Rack Console opens and closes; its terminal reaches the network only while it's open, and closing the front
    // door folds it away.
    @SuppressWarnings("removal")
    static void rackConsole(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        RackConsoleDevice console = RackGameTests.install(helper, master, RackDeviceType.RACK_CONSOLE, 2, RackConsoleDevice.class);
        RackGameTests.driveBay(helper, BAY);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
                    rack.setFrontOpen(true);
                    player.setPos(Vec3.atCenterOf(helper.absolutePos(master)).add(0, 0, -1.5));
                    helper.assertTrue(console.isOnline(), "Console offline");
                    helper.assertTrue(console.use(player) && console.isOpen(), "Didn't open");
                    RackConsoleMenu menu = new RackConsoleMenu(1, player.getInventory(), helper.absolutePos(master), console.u(), 6);
                    helper.assertTrue(menu.network() != null && menu.stillValid(player), "Terminal can't reach the network");
                    // Shift-clicking a stack in: it goes into the network.
                    player.getInventory().setItem(9, new ItemStack(Items.COBBLESTONE, 10));
                    menu.quickMoveStack(player, 0);
                    helper.assertTrue(player.getInventory().getItem(9).isEmpty(), "Console didn't take the stack: " + player.getInventory().getItem(9));
                    rack.setFrontOpen(false);
                    helper.assertTrue(!console.isOpen(), "Closing the door didn't close it");
                    helper.assertTrue(menu.network() == null && !menu.stillValid(player), "Terminal works closed");
                })
                .thenSucceed();
    }

    // A 6U device fits where six free units are; upgrading a tape keeps its id (and so what's on it).
    static void sixUnitsAndUpgrades(GameTestHelper helper) {
        BlockPos master = RackGameTests.networkedRack(helper);
        RackBlockEntity rack = helper.getBlockEntity(master, RackBlockEntity.class);
        helper.assertTrue(rack.fits(37, 6) && !rack.fits(38, 6), "6U fit");
        TapeLibraryDevice library = RackGameTests.install(helper, master, RackDeviceType.TAPE_LIBRARY_6U, 37, TapeLibraryDevice.class);
        helper.assertTrue(library.top() == 42 && library.tapeSlots() == 48 && library.bays() == 4, "6U library");
        helper.assertTrue(RackDeviceType.TAPE_LIBRARY_6U.sheetHeight() == 256 && RackDeviceType.TAPE_LIBRARY_6U.backOrigin() == 64, "6U sheet");

        ItemStack tape = new ItemStack(ModItems.tape(TapeGeneration.LTO_6).get());
        UUID id = UUID.randomUUID();
        tape.set(ModDataComponents.DRIVE_ID.get(), id);
        CraftingInput input = CraftingInput.of(2, 2, List.of(tape, new ItemStack(ModItems.FERRITE.get()), new ItemStack(ModItems.FERRITE.get()),
                new ItemStack(ModItems.LOGIC_DIE.get())));
        ItemStack upgraded = helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(recipe -> recipe.value().assemble(input)).orElse(ItemStack.EMPTY);
        helper.assertTrue(upgraded.is(ModItems.tape(TapeGeneration.LTO_7).get()), "Upgrade made " + upgraded);
        helper.assertTrue(id.equals(LtoTapeItem.id(upgraded)), "Upgrade lost the tape's id");
        helper.succeed();
    }
}
