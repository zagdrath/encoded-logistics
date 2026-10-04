/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.block.DriveBayBlock;
import net.zagdrath.encodedlogistics.block.FabricatorBlock;
import net.zagdrath.encodedlogistics.block.GatewayBlock;
import net.zagdrath.encodedlogistics.block.SchedulerBlock;
import net.zagdrath.encodedlogistics.block.cable.CableColor;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.FabricatorBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.GatewayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.SchedulerCoreBlockEntity;
import net.zagdrath.encodedlogistics.crafting.JobHost;
import net.zagdrath.encodedlogistics.crafting.CraftPlanner;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.item.SchematicItem;
import net.zagdrath.encodedlogistics.menu.SchematicEncoderMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.SchedulerStructures;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// Phase 3: the Scheduler multiblock, the Schematic Encoder, and autocrafting through a Fabricator and a Gateway. The rig:
// controller (0,1,0) - cable (1,1,0) - Drive Bay (2,1,0) with an 8K drive, the cable placed last so it connects to
// whatever a test put round it.
final class Phase3GameTests {
    private static final BlockPos CONTROLLER = new BlockPos(0, 1, 0), CABLE = new BlockPos(1, 1, 0), BAY = new BlockPos(2, 1, 0);
    private static final ItemKey LOG = ItemKey.of(new ItemStack(Items.OAK_LOG)), PLANKS = ItemKey.of(new ItemStack(Items.OAK_PLANKS)),
            RAW_IRON = ItemKey.of(new ItemStack(Items.RAW_IRON)), INGOT = ItemKey.of(new ItemStack(Items.IRON_INGOT)),
            COBBLESTONE = ItemKey.of(new ItemStack(Items.COBBLESTONE));

    private Phase3GameTests() {}

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

    private static ItemStack schematic(Schematic.Kind kind, List<ItemStack> inputs, List<ItemStack> outputs) {
        ItemStack card = new ItemStack(kind == Schematic.Kind.CRAFTING ? ModItems.ENCODED_SCHEMATIC_CRAFTING.get() : ModItems.ENCODED_SCHEMATIC_PROCESSING.get());
        card.set(ModDataComponents.SCHEMATIC.get(), Schematic.of(kind, inputs, outputs));
        return card;
    }

    private static boolean formed(GameTestHelper helper, BlockPos pos) {
        return SchedulerBlock.isFormed(helper.getBlockState(pos));
    }

    // A Core with a Job Buffer and a Thread Unit in a row forms (2 threads, base memory plus a buffer's); an L shape doesn't,
    // nor do two Cores; a Thread Unit cut off from its Core stays unformed while the Core alone forms.
    static void schedulerForms(GameTestHelper helper) {
        BlockPos core = new BlockPos(0, 1, 0), buffer = new BlockPos(1, 1, 0), thread = new BlockPos(2, 1, 0);
        helper.setBlock(core, ModBlocks.SCHEDULER_CORE.get());
        helper.setBlock(buffer, ModBlocks.JOB_BUFFER.get());
        helper.setBlock(thread, ModBlocks.THREAD_UNIT.get());
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(formed(helper, core) && formed(helper, buffer) && formed(helper, thread), "Row didn't form");
                    SchedulerCoreBlockEntity entity = helper.getBlockEntity(core, SchedulerCoreBlockEntity.class);
                    helper.assertTrue(entity.size() == 3 && entity.threads() == 2, "Size " + entity.size() + ", threads " + entity.threads());
                    helper.assertTrue(entity.memory() == 4_096 + 16_384, "Memory " + entity.memory());
                    helper.setBlock(new BlockPos(0, 1, 1), ModBlocks.JOB_BUFFER.get());
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertFalse(formed(helper, core), "L shape formed");
                    helper.assertTrue(helper.getBlockEntity(core, SchedulerCoreBlockEntity.class).problem() == SchedulerStructures.Problem.NOT_CUBOID,
                            "Problem " + helper.getBlockEntity(core, SchedulerCoreBlockEntity.class).problem());
                    helper.setBlock(new BlockPos(0, 1, 1), Blocks.AIR);
                    helper.setBlock(new BlockPos(3, 1, 0), ModBlocks.SCHEDULER_CORE.get());
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertFalse(formed(helper, core), "Two cores formed");
                    helper.assertTrue(helper.getBlockEntity(core, SchedulerCoreBlockEntity.class).problem() == SchedulerStructures.Problem.TWO_CORES,
                            "Problem " + helper.getBlockEntity(core, SchedulerCoreBlockEntity.class).problem());
                    helper.setBlock(new BlockPos(3, 1, 0), Blocks.AIR);
                    helper.setBlock(buffer, Blocks.AIR);
                })
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(formed(helper, core), "Lone core not formed");
                    helper.assertFalse(formed(helper, thread), "Thread Unit without a core formed");
                    helper.assertTrue(helper.getBlockEntity(core, SchedulerCoreBlockEntity.class).threads() == 1, "Lone core threads");
                })
                .thenSucceed();
    }

    // The Schematic Encoder writes a crafting schematic (the grid's recipe result as the output) onto a blank card, then
    // rewrites that card as a processing schematic with amounts.
    static void encoderEncodes(GameTestHelper helper) {
        rig(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        helper.startSequence()
                .thenExecute(() -> {
                    ItemStack stack = new ItemStack(PartType.SCHEMATIC_ENCODER.item());
                    player.setItemInHand(InteractionHand.MAIN_HAND, stack);
                    BlockPos absolute = helper.absolutePos(CABLE);
                    BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0, 0.3), Direction.SOUTH, absolute, false);
                    helper.getBlockState(CABLE).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    player.setPos(Vec3.atCenterOf(helper.absolutePos(CABLE)).add(0, 0, 1));
                    SchematicEncoderMenu menu = new SchematicEncoderMenu(1, player.getInventory(), helper.absolutePos(CABLE), Direction.SOUTH);
                    helper.assertTrue(menu.stillValid(player), "Encoder menu not valid");
                    menu.getSlot(SchematicEncoderMenu.BLANK).set(new ItemStack(ModItems.SCHEMATIC_CARD.get(), 2));
                    menu.setRecipe(true, List.of(new ItemStack(Items.OAK_LOG)), List.of());
                    ItemStack result = menu.getSlot(SchematicEncoderMenu.RESULT).getItem();
                    helper.assertTrue(result.is(Items.OAK_PLANKS) && result.getCount() == 4, "Result " + result);
                    menu.clickMenuButton(player, SchematicEncoderMenu.BUTTON_ENCODE);
                    ItemStack encoded = menu.getSlot(SchematicEncoderMenu.ENCODED).getItem();
                    Schematic crafting = SchematicItem.schematic(encoded);
                    helper.assertTrue(encoded.is(ModItems.ENCODED_SCHEMATIC_CRAFTING.get()) && crafting != null, "Not encoded: " + encoded);
                    helper.assertTrue(crafting.output().is(Items.OAK_PLANKS) && crafting.output().getCount() == 4, "Output " + crafting.output());
                    helper.assertTrue(menu.getSlot(SchematicEncoderMenu.BLANK).getItem().getCount() == 1, "Blank card not used");

                    menu.setRecipe(false, List.of(new ItemStack(Items.RAW_IRON, 2)), List.of(new ItemStack(Items.IRON_INGOT, 2)));
                    menu.clickMenuButton(player, SchematicEncoderMenu.BUTTON_ENCODE);
                    Schematic processing = SchematicItem.schematic(menu.getSlot(SchematicEncoderMenu.ENCODED).getItem());
                    helper.assertTrue(menu.getSlot(SchematicEncoderMenu.ENCODED).getItem().is(ModItems.ENCODED_SCHEMATIC_PROCESSING.get())
                            && processing != null && processing.kind() == Schematic.Kind.PROCESSING, "Not rewritten as processing");
                    helper.assertTrue(processing.inputTotals().get(RAW_IRON) == 2 && processing.outputCount(INGOT) == 2, "Amounts " + processing);
                    helper.assertTrue(menu.getSlot(SchematicEncoderMenu.BLANK).getItem().getCount() == 1, "Rewriting used a blank card");
                })
                .thenSucceed();
    }

    // A job for 8 planks from 2 logs in storage runs on a lone Scheduler Core through a Fabricator holding the log's
    // schematic; 12 planks would need a third log, so that plan has it missing.
    static void fabricatorCrafts(GameTestHelper helper) {
        BlockPos fabricator = new BlockPos(1, 2, 0), core = new BlockPos(1, 1, 1);
        helper.setBlock(fabricator, ModBlocks.FABRICATOR.get().defaultBlockState().setValue(FabricatorBlock.FACING, Direction.NORTH));
        helper.setBlock(core, ModBlocks.SCHEDULER_CORE.get());
        rig(helper);
        helper.getBlockEntity(fabricator, FabricatorBlockEntity.class).setItem(0,
                schematic(Schematic.Kind.CRAFTING, List.of(new ItemStack(Items.OAK_LOG)), List.of(new ItemStack(Items.OAK_PLANKS, 4))));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    BlockPos device = helper.absolutePos(BAY);
                    storage(helper).insert(LOG, 2, false);
                    helper.assertTrue(CraftRequests.craftables(helper.getLevel(), device).contains(PLANKS), "Planks not craftable");
                    CraftPlanner.Plan tooFew = CraftRequests.plan(helper.getLevel(), device, PLANKS, 12);
                    helper.assertTrue(tooFew != null && tooFew.missing() == 1 && !tooFew.complete(), "12 planks plan: " + tooFew);
                    CraftPlanner.Plan plan = CraftRequests.plan(helper.getLevel(), device, PLANKS, 8);
                    helper.assertTrue(plan != null && plan.complete() && plan.take().get(LOG) == 2, "8 planks plan: " + plan);
                    List<JobHost> schedulers = CraftRequests.schedulers(helper.getLevel(), device);
                    helper.assertTrue(schedulers.size() == 1, "Schedulers: " + schedulers.size());
                    helper.assertTrue(CraftRequests.start(helper.getLevel(), device, plan, schedulers.getFirst()) != null, "Job didn't start");
                    helper.assertTrue(storage(helper).count(LOG) == 0, "Logs not taken");
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    helper.assertTrue(helper.getBlockEntity(fabricator, FabricatorBlockEntity.class).isWorking(), "Fabricator idle");
                    helper.assertTrue(helper.getBlockState(fabricator).getValue(FabricatorBlock.ACTIVE), "Fabricator not lit");
                })
                .thenIdle(50)
                .thenExecute(() -> {
                    helper.assertTrue(storage(helper).count(PLANKS) == 8, "Network has " + storage(helper).count(PLANKS) + " planks");
                    helper.assertTrue(helper.getBlockEntity(core, SchedulerCoreBlockEntity.class).jobs().isEmpty(), "Job not finished");
                })
                .thenSucceed();
    }

    // A Gateway runs a raw iron -> ingot schematic through the chest next to it (standing in for a furnace): it pushes
    // both runs' raw iron in, pulls the first ingot back out, takes the second through its handler (as a machine pushing
    // it), and the job puts both into the network. Meanwhile it keeps 8 cobblestone stocked in its buffer.
    static void gatewayProcesses(GameTestHelper helper) {
        BlockPos gateway = new BlockPos(1, 1, 1), machine = new BlockPos(2, 1, 1), core = new BlockPos(1, 2, 0);
        helper.setBlock(gateway, ModBlocks.GATEWAY.get());
        helper.setBlock(machine, Blocks.CHEST);
        helper.setBlock(core, ModBlocks.SCHEDULER_CORE.get());
        rig(helper);
        GatewayBlockEntity entity = helper.getBlockEntity(gateway, GatewayBlockEntity.class);
        entity.schematicSlots().set(0, schematic(Schematic.Kind.PROCESSING, List.of(new ItemStack(Items.RAW_IRON)), List.of(new ItemStack(Items.IRON_INGOT))));
        entity.stock().set(0, new ItemStack(Items.COBBLESTONE, 8));
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    BlockPos device = helper.absolutePos(BAY);
                    storage(helper).insert(RAW_IRON, 2, false);
                    storage(helper).insert(COBBLESTONE, 16, false);
                    CraftPlanner.Plan plan = CraftRequests.plan(helper.getLevel(), device, INGOT, 2);
                    helper.assertTrue(plan != null && plan.complete(), "Plan: " + plan);
                    helper.assertTrue(CraftRequests.start(helper.getLevel(), device, plan, CraftRequests.schedulers(helper.getLevel(), device).getFirst()) != null,
                            "Job didn't start");
                })
                .thenIdle(6)
                .thenExecute(() -> {
                    ChestBlockEntity chest = helper.getBlockEntity(machine, ChestBlockEntity.class);
                    helper.assertTrue(chest.countItem(Items.RAW_IRON) == 2, "Chest has " + chest.countItem(Items.RAW_IRON) + " raw iron");
                    chest.clearContent();
                    chest.setItem(0, new ItemStack(Items.IRON_INGOT));
                    ResourceHandler<ItemResource> handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(gateway), Direction.EAST);
                    try (Transaction transaction = Transaction.openRoot()) {
                        helper.assertTrue(handler.insert(ItemResource.of(new ItemStack(Items.STONE)), 1, transaction) == 0, "Took stone");
                        helper.assertTrue(handler.insert(ItemResource.of(new ItemStack(Items.IRON_INGOT)), 1, transaction) == 1, "Refused the ingot");
                        transaction.commit();
                    }
                })
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(storage(helper).count(INGOT) == 2, "Network has " + storage(helper).count(INGOT) + " ingots");
                    helper.assertTrue(helper.getBlockEntity(machine, ChestBlockEntity.class).countItem(Items.IRON_INGOT) == 0, "Ingot left in the machine");
                    helper.assertTrue(helper.getBlockEntity(core, SchedulerCoreBlockEntity.class).jobs().isEmpty(), "Job not finished");
                    helper.assertTrue(storage(helper).count(COBBLESTONE) == 8, "Network has " + storage(helper).count(COBBLESTONE) + " cobblestone");
                    ResourceHandler<ItemResource> handler = helper.getLevel().getCapability(Capabilities.Item.BLOCK, helper.absolutePos(gateway), Direction.UP);
                    helper.assertTrue(handler.getResource(0).matches(new ItemStack(Items.COBBLESTONE)) && handler.getAmountAsInt(0) == 8,
                            "Buffer has " + handler.getAmountAsInt(0));
                    helper.assertTrue(helper.getBlockState(gateway).getValue(GatewayBlock.ACTIVE), "Gateway not lit");
                })
                .thenSucceed();
    }
}
