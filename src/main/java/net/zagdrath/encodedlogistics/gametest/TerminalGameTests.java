/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.block.DriveBayBlock;
import net.zagdrath.encodedlogistics.block.cable.CableColor;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.menu.AccessTerminalMenu;
import net.zagdrath.encodedlogistics.menu.FabricationTerminalMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// Terminal clicks the way a player makes them: the Fabrication Terminal's result taken again and again onto the cursor,
// and the grid's AE2-style actions. The rig: controller (0,1,0) - cable (1,1,0) with a Fabrication Terminal on its top -
// Drive Bay (2,1,0).
final class TerminalGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(0, 1, 0), CABLE = new BlockPos(1, 1, 0), BAY = new BlockPos(2, 1, 0);
    private static final ItemKey NUGGET = ItemKey.of(new ItemStack(Items.GOLD_NUGGET)), INGOT = ItemKey.of(new ItemStack(Items.GOLD_INGOT));
    private static final int RESULT = AccessTerminalMenu.INVENTORY_SLOTS + 9;

    private TerminalGameTests() {}

    private static void rig(GameTestHelper helper) {
        helper.setBlock(CONTROLLER, ModBlocks.NETWORK_CONTROLLER.get());
        helper.setBlock(BAY, ModBlocks.DRIVE_BAY.get().defaultBlockState().setValue(DriveBayBlock.FACING, Direction.EAST));
        NetworkCableBlock block = ModBlocks.cable(CableTier.NORMAL, CableColor.NEUTRAL).get();
        helper.setBlock(CABLE, block.withConnections(block.defaultBlockState(), helper.getLevel(), helper.absolutePos(CABLE)));
        helper.getBlockEntity(BAY, DriveBayBlockEntity.class).setItem(0, new ItemStack(ModItems.storageDrive(StorageTier.K8).get()));
        EnergyHandler energy = helper.getLevel().getCapability(Capabilities.Energy.BLOCK, helper.absolutePos(CONTROLLER), Direction.UP);
        try (Transaction transaction = Transaction.openRoot()) {
            energy.insert(20_000, transaction);
            transaction.commit();
        }
    }

    private static NetworkStorage storage(GameTestHelper helper) {
        NetworkStorage storage = ControllerStructures.get(helper.getLevel()).storageAt(helper.getLevel(), helper.absolutePos(BAY));
        helper.assertTrue(storage != null, "Network offline");
        return storage;
    }

    // Mounts a Fabrication Terminal on the cable's top (the network picks it up over the next ticks).
    private static void mount(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ItemStack part = new ItemStack(PartType.FABRICATION_TERMINAL.item());
        player.setItemInHand(InteractionHand.MAIN_HAND, part);
        BlockPos cable = helper.absolutePos(CABLE);
        helper.getBlockState(CABLE).useItemOn(part, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(cable).add(0, 0.2, 0), Direction.UP, cable, false));
    }

    // Opens the terminal for a mock player, with nuggets in the network and the grid filled for an ingot.
    private static FabricationTerminalMenu open(GameTestHelper helper, ServerPlayer player) {
        storage(helper).insert(NUGGET, 9 * 5, false);
        FabricationTerminalMenu menu = new FabricationTerminalMenu(1, player.getInventory(), helper.absolutePos(CABLE), Direction.UP,
                AccessTerminalMenu.DEFAULT_ROWS);
        player.containerMenu = menu;
        List<List<ItemStack>> inputs = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            inputs.add(List.of(new ItemStack(Items.GOLD_NUGGET)));
        }
        menu.fillGrid(inputs);
        return menu;
    }

    // Clicking the result with ingots already on the cursor crafts another onto them, as long as the network refills the
    // grid: four clicks, four ingots carried - and a quick second click isn't taken for a double-click.
    static void resultStacksOnCursor(GameTestHelper helper) {
        rig(helper);
        mount(helper);
        helper.startSequence()
                .thenIdle(12)
                .thenExecute(() -> {
                    // A fake player: its connection drops the terminal's sync payloads, which a mock player's can't take.
                    ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
                    player.getInventory().clearContent();
                    FabricationTerminalMenu menu = open(helper, player);
                    helper.assertTrue(menu.getSlot(RESULT).getItem().is(Items.GOLD_INGOT), "No ingot in the result");
                    for (int i = 0; i < 4; i++) {
                        menu.clicked(RESULT, 0, ContainerInput.PICKUP, player);
                    }
                    ItemStack carried = menu.getCarried();
                    helper.assertTrue(carried.is(Items.GOLD_INGOT) && carried.getCount() == 4, "Carried " + carried);
                    // Clicking again quickly is a click, not a double-click's collect-all (which takes nothing here).
                    helper.assertFalse(menu.canTakeItemForPickAll(carried, menu.getSlot(RESULT)), "The result counts for double-click collecting");
                })
                .thenSucceed();
    }

    // The grid, as AE2 does it: left takes a stack, right half a stack, shift-right one, Shift+wheel one at a time either
    // way; with an item carried, left puts it all in and right one.
    static void gridActions(GameTestHelper helper) {
        rig(helper);
        mount(helper);
        helper.startSequence()
                .thenIdle(12)
                .thenExecute(() -> {
                    // A fake player: its connection drops the terminal's sync payloads, which a mock player's can't take.
                    ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
                    player.getInventory().clearContent();
                    FabricationTerminalMenu menu = open(helper, player);
                    NetworkStorage storage = storage(helper);
                    storage.insert(INGOT, 100, false);
                    menu.handleClick(player, INGOT, AccessTerminalMenu.TAKE_HALF);
                    helper.assertTrue(menu.getCarried().getCount() == 32, "Half took " + menu.getCarried());
                    menu.handleClick(player, INGOT, AccessTerminalMenu.INSERT_ONE);
                    helper.assertTrue(menu.getCarried().getCount() == 31, "Right-click in left " + menu.getCarried());
                    menu.handleClick(player, INGOT, AccessTerminalMenu.TAKE_ONE);
                    helper.assertTrue(menu.getCarried().getCount() == 32, "Take one left " + menu.getCarried());
                    menu.handleClick(player, NUGGET, AccessTerminalMenu.TAKE_ONE);
                    helper.assertTrue(menu.getCarried().is(Items.GOLD_INGOT) && menu.getCarried().getCount() == 32,
                            "Took a different item onto the cursor: " + menu.getCarried());
                    menu.handleClick(player, INGOT, AccessTerminalMenu.INSERT_CARRIED);
                    helper.assertTrue(menu.getCarried().isEmpty() && storage.count(INGOT) == 100, "Insert left " + menu.getCarried());
                    menu.handleClick(player, INGOT, AccessTerminalMenu.TAKE_ONE);
                    helper.assertTrue(menu.getCarried().getCount() == 1, "Take one onto an empty cursor: " + menu.getCarried());
                    menu.handleClick(player, INGOT, AccessTerminalMenu.TAKE_STACK);
                    helper.assertTrue(menu.getCarried().getCount() == 1, "Left-click with an item carried took more");
                })
                .thenSucceed();
    }
}
