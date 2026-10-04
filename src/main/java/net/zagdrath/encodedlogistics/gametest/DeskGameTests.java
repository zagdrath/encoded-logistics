/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.block.TerminalDeskBlock;
import net.zagdrath.encodedlogistics.blockentity.SwivelChairBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;
import net.zagdrath.encodedlogistics.entity.SeatEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.terminal.TerminalActions;
import net.zagdrath.encodedlogistics.terminal.TerminalCommands;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalItems;
import net.zagdrath.encodedlogistics.terminal.TerminalOutput;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// The Terminal Desk (its screen coming on, withdrawing to its drawer, the command line) and the Swivel Chair.
final class DeskGameTests {
    private static final ItemKey COBBLESTONE = ItemKey.of(new ItemStack(Items.COBBLESTONE));
    // On the networked rack's cables: the desk faces south, its back on the cable at (1, 1, 2); its drawer half west.
    private static final BlockPos DESK = new BlockPos(1, 1, 3), BAY = new BlockPos(1, 2, 1);

    private DeskGameTests() {}

    private static void desk(GameTestHelper helper) {
        RackGameTests.networkedRack(helper);
        RackGameTests.driveBay(helper, BAY);
        var state = ModBlocks.TERMINAL_DESK.get().defaultBlockState().setValue(TerminalDeskBlock.FACING, Direction.SOUTH);
        helper.setBlock(DESK, state);
        helper.setBlock(TerminalDeskBlock.other(state, DESK), state.setValue(TerminalDeskBlock.PART, TerminalDeskBlock.Part.DUMMY));
    }

    @SuppressWarnings("removal")
    private static TerminalContext context(GameTestHelper helper, ServerPlayer player) {
        TerminalDeskBlockEntity desk = helper.getBlockEntity(DESK, TerminalDeskBlockEntity.class);
        return new TerminalContext(helper.getLevel().getServer(), desk.network(), desk, player);
    }

    // Online, the desk's screen boots and comes on; it reaches the network; breaking one half breaks the other.
    static void deskScreen(GameTestHelper helper) {
        desk(helper);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    TerminalDeskBlockEntity desk = helper.getBlockEntity(DESK, TerminalDeskBlockEntity.class);
                    helper.assertTrue(desk.isOnline() && desk.network() != null, "Desk offline");
                    helper.assertTrue(desk.screen() == TerminalDeskBlock.Screen.BOOT, "Screen " + desk.screen());
                })
                .thenIdle(TerminalDeskBlockEntity.BOOT_TICKS + 2)
                .thenExecute(() -> {
                    TerminalDeskBlockEntity desk = helper.getBlockEntity(DESK, TerminalDeskBlockEntity.class);
                    helper.assertTrue(desk.screen() == TerminalDeskBlock.Screen.ON, "Screen " + desk.screen());
                    helper.getLevel().destroyBlock(helper.absolutePos(TerminalDeskBlock.other(helper.getBlockState(DESK), DESK)), false);
                    helper.assertBlockNotPresent(ModBlocks.TERMINAL_DESK.get(), DESK);
                })
                .thenSucceed();
    }

    // Withdrawing goes to the drawer as far as it has room (the rest stays in the network) or to the player's inventory.
    @SuppressWarnings("removal")
    static void deskWithdraws(GameTestHelper helper) {
        desk(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    NetworkStorage storage = RackGameTests.storage(helper, BAY);
                    storage.insert(COBBLESTONE, 700, false);
                    TerminalContext context = context(helper, player);
                    TerminalActions.withdraw(context, COBBLESTONE, 100, TerminalActions.Destination.DRAWER);
                    TerminalDeskBlockEntity desk = context.desk();
                    helper.assertTrue(desk.getItem(0).getCount() == 64 && desk.getItem(1).getCount() == 36, "Drawer " + desk.getItem(0) + ", " + desk.getItem(1));
                    TerminalOutput full = TerminalActions.withdraw(context, COBBLESTONE, 600, TerminalActions.Destination.DRAWER);
                    long inDrawer = 0;
                    for (int slot = 0; slot < TerminalDeskBlockEntity.SLOTS; slot++) {
                        inDrawer += desk.getItem(slot).getCount();
                    }
                    helper.assertTrue(inDrawer == 9 * 64, "Drawer holds " + inDrawer);
                    helper.assertTrue(storage.count(COBBLESTONE) == 700 - 9 * 64, "Network has " + storage.count(COBBLESTONE));
                    helper.assertTrue(full.message() != null && full.message().getString().contains("full"), "Message: " + full.message());
                    TerminalActions.withdraw(context, COBBLESTONE, 20, TerminalActions.Destination.INV);
                    helper.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == 20, "Inventory " + player.getInventory().countItem(Items.COBBLESTONE));
                })
                .thenSucceed();
    }

    // The command line: words and quotes, k/M amounts, names and ids, show, withdraw, unknown commands, completion.
    @SuppressWarnings("removal")
    static void deskCommands(GameTestHelper helper) {
        desk(helper);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(TerminalCommands.words("withdraw \"iron ingot\" 2k *inv").equals(List.of("withdraw", "iron ingot", "2k", "*inv")),
                            "Words " + TerminalCommands.words("withdraw \"iron ingot\" 2k *inv"));
                    helper.assertTrue(TerminalItems.amount("2k") == 2_000 && TerminalItems.amount("1.5M") == 1_500_000 && TerminalItems.amount("x") == -1,
                            "Amounts");
                    RackGameTests.storage(helper, BAY).insert(COBBLESTONE, 50, false);
                    TerminalContext context = context(helper, player);
                    helper.assertTrue(TerminalItems.resolve(context, "cobblestone") != null && TerminalItems.resolve(context, "minecraft:cobblestone") != null,
                            "Item not found by id");
                    TerminalOutput shown = TerminalCommands.execute(context, "show inventory cobble");
                    helper.assertTrue(shown.lines().size() == 2 && shown.lines().get(1).text().contains("50"), "show inventory: " + shown.lines().size());
                    TerminalCommands.execute(context, "withdraw cobblestone 10 *inv");
                    helper.assertTrue(player.getInventory().countItem(Items.COBBLESTONE) == 10, "CLI withdraw");
                    TerminalOutput unknown = TerminalCommands.execute(context, "frobnicate");
                    helper.assertTrue(unknown.message() != null && unknown.message().getString().contains("frobnicate"), "Unknown command message");
                    helper.assertTrue(TerminalCommands.complete(context, "sh").equals(List.of("show")), "Completion " + TerminalCommands.complete(context, "sh"));
                    helper.assertTrue(TerminalCommands.complete(context, "show dr").equals(List.of("drives")), "Topic completion");
                    TerminalOutput devices = TerminalService.handle(context, TerminalService.QUERY, "devices");
                    helper.assertTrue(devices.lines().stream().anyMatch(line -> line.text().startsWith("Terminal")), "Devices list has no desk");
                    UUID job = UUID.randomUUID();
                    int number = ControllerStructures.jobNumber(context.server(), context.network(), job);
                    helper.assertTrue(number > 0 && ControllerStructures.jobNumber(context.server(), context.network(), job) == number
                            && job.equals(context.job(number)), "Job numbers");
                })
                .thenSucceed();
    }

    // Sitting: a seat appears under the player, the chair turns to them; it goes when the chair does. Dye tints it.
    @SuppressWarnings("removal")
    static void swivelChair(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, ModBlocks.SWIVEL_CHAIR.get());
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.startSequence()
                .thenExecute(() -> {
                    player.setPos(Vec3.atCenterOf(helper.absolutePos(pos)).add(0, 0, -1));
                    player.setYRot(90);
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), Direction.UP, helper.absolutePos(pos), false);
                    helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit);
                    helper.assertTrue(player.getVehicle() instanceof SeatEntity, "Not seated");
                    helper.assertTrue(Math.abs(helper.getBlockEntity(pos, SwivelChairBlockEntity.class).yaw() - 90) < 1, "Chair didn't turn");
                    helper.getLevel().destroyBlock(helper.absolutePos(pos), false);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getLevel().getEntitiesOfClass(SeatEntity.class, new AABB(helper.absolutePos(pos)).inflate(1)).isEmpty(),
                            "Seat left behind");
                    helper.assertTrue(!player.isPassenger(), "Still seated");
                    CraftingInput input = CraftingInput.of(2, 1, List.of(new ItemStack(ModItems.SWIVEL_CHAIR.get()), new ItemStack(Items.DYE.pick(DyeColor.RED))));
                    ItemStack dyed = helper.getLevel().getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                            .map(recipe -> recipe.value().assemble(input)).orElse(ItemStack.EMPTY);
                    helper.assertTrue(dyed.is(ModItems.SWIVEL_CHAIR.get()) && dyed.has(DataComponents.DYED_COLOR), "Dyed: " + dyed);
                })
                .thenSucceed();
    }
}
