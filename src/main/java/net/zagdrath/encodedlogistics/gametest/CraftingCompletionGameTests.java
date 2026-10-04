/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.GatewayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftPlanner;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.crafting.CraftingJob;
import net.zagdrath.encodedlogistics.crafting.JobEvents;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.MessageService;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackScheduler;
import net.zagdrath.encodedlogistics.rack.device.ComputeServerDevice;
import net.zagdrath.encodedlogistics.rack.device.MemoryServerDevice;
import net.zagdrath.encodedlogistics.rack.device.NasDevice;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// Processing jobs finishing whatever way their outputs come back. The rig (RackGameTests.networkedRack): a rack Scheduler
// (Compute and Memory Servers, a NAS), a Gateway at (0,1,1) beside the controller with a raw iron -> ingot schematic and
// a chest above it standing in for the furnace's input, and an output chest at (2,2,2) over the cable at (2,1,2), which an
// Ingress Port on that cable's top empties (standing in for the port under the furnace). The Gateway can't reach the
// output chest.
final class CraftingCompletionGameTests {
    private static final BlockPos GATEWAY = new BlockPos(0, 1, 1), INPUT = new BlockPos(0, 2, 1), OUTPUT = new BlockPos(2, 2, 2),
            PORT_CABLE = new BlockPos(2, 1, 2);
    private static final ItemKey RAW_IRON = ItemKey.of(new ItemStack(Items.RAW_IRON)), INGOT = ItemKey.of(new ItemStack(Items.IRON_INGOT));

    private CraftingCompletionGameTests() {}

    // The rig; returns the rack's master.
    private static BlockPos rig(GameTestHelper helper, boolean port) {
        BlockPos master = RackGameTests.networkedRack(helper);
        RackGameTests.install(helper, master, RackDeviceType.COMPUTE_SERVER, 1, ComputeServerDevice.class);
        RackGameTests.install(helper, master, RackDeviceType.MEMORY_SERVER, 3, MemoryServerDevice.class);
        NasDevice nas = RackGameTests.install(helper, master, RackDeviceType.NAS, 6, NasDevice.class);
        nas.items().set(0, new ItemStack(ModItems.storageDrive(StorageTier.K8).get()));
        nas.itemsChanged();
        helper.setBlock(GATEWAY, ModBlocks.GATEWAY.get());
        helper.setBlock(INPUT, Blocks.CHEST);
        helper.setBlock(OUTPUT, Blocks.CHEST);
        ItemStack card = new ItemStack(ModItems.ENCODED_SCHEMATIC_PROCESSING.get());
        card.set(ModDataComponents.SCHEMATIC.get(), Schematic.of(Schematic.Kind.PROCESSING, List.of(new ItemStack(Items.RAW_IRON)),
                List.of(new ItemStack(Items.IRON_INGOT))));
        helper.getBlockEntity(GATEWAY, GatewayBlockEntity.class).schematicSlots().set(0, card);
        if (port) {
            Player player = helper.makeMockPlayer(GameType.SURVIVAL);
            ItemStack stack = new ItemStack(PartType.INGRESS_PORT.item());
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
            BlockPos absolute = helper.absolutePos(PORT_CABLE);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(absolute).add(0, 0.3, 0), Direction.UP, absolute, false);
            helper.getBlockState(PORT_CABLE).useItemOn(stack, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
        }
        return master;
    }

    private static RackScheduler scheduler(GameTestHelper helper, BlockPos master) {
        return helper.getBlockEntity(master, RackBlockEntity.class).scheduler();
    }

    // Raw iron into the network and a 2-ingot job on the rack's Scheduler.
    private static UUID start(GameTestHelper helper, BlockPos master) {
        return start(helper, master, CraftRequests.Requester.NONE);
    }

    private static UUID start(GameTestHelper helper, BlockPos master, CraftRequests.Requester requester) {
        RackGameTests.storage(helper, master).insert(RAW_IRON, 2, false);
        BlockPos device = helper.absolutePos(master);
        CraftPlanner.Plan plan = CraftRequests.plan(helper.getLevel(), device, INGOT, 2);
        helper.assertTrue(plan != null && plan.complete(), "Plan: " + plan);
        RackScheduler scheduler = scheduler(helper, master);
        helper.assertTrue(scheduler.active(), "Rack isn't a Scheduler");
        helper.assertTrue(CraftRequests.start(helper.getLevel(), device, plan, scheduler, requester) != null, "Job didn't start");
        return scheduler.jobs().getFirst().id;
    }

    // The "furnace": takes the raw iron out of the input chest and puts ingots where they'll come back from.
    private static void smelt(GameTestHelper helper, BlockPos into) {
        ChestBlockEntity input = helper.getBlockEntity(INPUT, ChestBlockEntity.class);
        helper.assertTrue(input.countItem(Items.RAW_IRON) == 2, "Input has " + input.countItem(Items.RAW_IRON) + " raw iron");
        input.clearContent();
        helper.getBlockEntity(into, ChestBlockEntity.class).setItem(0, new ItemStack(Items.IRON_INGOT, 2));
    }

    private static void assertFinished(GameTestHelper helper, BlockPos master) {
        RackScheduler scheduler = scheduler(helper, master);
        helper.assertTrue(scheduler.jobs().isEmpty(), "Job still listed: " + scheduler.jobs().size());
        helper.assertTrue(scheduler.threadsUsed() == 0 && scheduler.memoryUsed() == 0, "Threads " + scheduler.threadsUsed() + ", memory "
                + scheduler.memoryUsed());
        long ingots = RackGameTests.storage(helper, master).count(INGOT);
        helper.assertTrue(ingots == 2, "Network has " + ingots + " ingots");
    }

    // The ingots come back through an Ingress Port, not the Gateway: the job still gets them, finishes and lets go of its
    // thread and memory (the panels list what the Scheduler holds, so it's gone from them too).
    static void returnedThroughPort(GameTestHelper helper) {
        BlockPos master = rig(helper, true);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> start(helper, master))
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(scheduler(helper, master).threadsUsed() == 1, "No thread in use");
                    smelt(helper, OUTPUT);
                })
                .thenIdle(45)
                .thenExecute(() -> assertFinished(helper, master))
                .thenSucceed();
    }

    // The same job with the ingots back through the Gateway (it pulls them out of the machine).
    static void returnedThroughGateway(GameTestHelper helper) {
        BlockPos master = rig(helper, false);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> start(helper, master))
                .thenIdle(25)
                .thenExecute(() -> smelt(helper, INPUT))
                .thenIdle(25)
                .thenExecute(() -> assertFinished(helper, master))
                .thenSucceed();
    }

    // Cancelled while it waits: its thread and memory are free at once, and the ingots, when they turn up, are just items.
    static void cancelWhileWaiting(GameTestHelper helper) {
        BlockPos master = rig(helper, true);
        UUID[] job = new UUID[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> job[0] = start(helper, master))
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(scheduler(helper, master).cancel(job[0]), "Cancel refused");
                    RackScheduler scheduler = scheduler(helper, master);
                    helper.assertTrue(scheduler.jobs().isEmpty() && scheduler.threadsUsed() == 0 && scheduler.memoryUsed() == 0, "Not released");
                    smelt(helper, OUTPUT);
                })
                .thenIdle(45)
                .thenExecute(() -> helper.assertTrue(RackGameTests.storage(helper, master).count(INGOT) == 2, "Ingots lost"))
                .thenSucceed();
    }

    // Saved and loaded again while it waits (the rack and the Gateway, as a reload does): it carries on and finishes.
    static void survivesReload(GameTestHelper helper) {
        BlockPos master = rig(helper, true);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> start(helper, master))
                .thenIdle(25)
                .thenExecute(() -> {
                    for (BlockPos pos : List.of(master, GATEWAY)) {
                        BlockEntity entity = helper.getLevel().getBlockEntity(helper.absolutePos(pos));
                        CompoundTag saved = entity.saveWithFullMetadata(helper.getLevel().registryAccess());
                        BlockEntity loaded = BlockEntity.loadStatic(helper.absolutePos(pos), helper.getBlockState(pos), saved, helper.getLevel().registryAccess());
                        helper.getLevel().setBlockEntity(loaded);
                    }
                    ControllerStructures.get(helper.getLevel()).markTopologyChanged();
                    CraftingJob job = scheduler(helper, master).jobs().getFirst();
                    helper.assertTrue(job.running, "Job lost its place after loading");
                    smelt(helper, OUTPUT);
                })
                .thenIdle(45)
                .thenExecute(() -> assertFinished(helper, master))
                .thenSucceed();
    }

    // --- Who's told when a job ends (JobEvents) ---

    // The player who asked gets a toast for it, theirs, with the item and amount; it ran long enough to count.
    static void toastToRequester(GameTestHelper helper) {
        BlockPos master = rig(helper, true);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        List<JobEvents.Notice> notices = new ArrayList<>();
        Consumer<JobEvents.Notice> listener = notices::add;
        JobEvents.listen(listener);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> start(helper, master, CraftRequests.Requester.of(player)))
                .thenIdle(25)
                .thenExecute(() -> smelt(helper, OUTPUT))
                .thenIdle(45)
                .thenExecute(() -> {
                    JobEvents.unlisten(listener);
                    assertFinished(helper, master);
                    // (Other tests run alongside, with their own players and jobs.)
                    JobEvents.Notice mine = notices.stream().filter(n -> n.mine() && n.player() == player).findFirst().orElse(null);
                    helper.assertTrue(mine != null, "Requester not told");
                    helper.assertTrue(mine.toast().item().equals(INGOT) && mine.toast().amount() == 2 && mine.toast().processing()
                            && mine.toast().outcome() == JobEvents.Outcome.COMPLETED.ordinal(), "Toast " + mine.toast());
                    helper.assertTrue(mine.toast().duration() >= 25, "Duration " + mine.toast().duration());
                })
                .thenSucceed();
    }

    // Cancelled and failed jobs say so; a failure says why.
    static void toastCancelledAndFailed(GameTestHelper helper) {
        BlockPos master = rig(helper, true);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        List<JobEvents.Notice> notices = new ArrayList<>();
        Consumer<JobEvents.Notice> listener = notices::add;
        JobEvents.listen(listener);
        UUID[] job = new UUID[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    RackGameTests.storage(helper, master).insert(RAW_IRON, 2, false);
                    job[0] = start(helper, master, CraftRequests.Requester.of(player));
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    scheduler(helper, master).cancel(job[0]);
                    helper.assertTrue(notices.stream().anyMatch(n -> n.mine() && n.player() == player
                            && n.toast().outcome() == JobEvents.Outcome.CANCELLED.ordinal()), "No cancelled toast");
                    notices.clear();
                    start(helper, master, CraftRequests.Requester.of(player));
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    scheduler(helper, master).dropAll(helper.getLevel());
                    JobEvents.unlisten(listener);
                    JobEvents.Notice failed = notices.stream().filter(n -> n.mine() && n.player() == player
                            && n.toast().outcome() == JobEvents.Outcome.FAILED.ordinal()).findFirst().orElse(null);
                    helper.assertTrue(failed != null && failed.toast().reason().equals(JobEvents.SCHEDULER_REMOVED), "No failure toast with its reason");
                })
                .thenSucceed();
    }

    // A requester who isn't online gets a Terminal OS message instead; with job toasts off on the server nobody gets
    // a toast, but the message still goes.
    static void offlineRequesterQueued(GameTestHelper helper) {
        BlockPos master = rig(helper, true);
        List<JobEvents.Notice> notices = new ArrayList<>();
        Consumer<JobEvents.Notice> listener = notices::add;
        JobEvents.listen(listener);
        boolean allowed = Config.ALLOW_JOB_TOASTS.get();
        helper.makeMockServerPlayerInLevel();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    // All in this one tick, so the setting (and what's heard) is this test's alone.
                    notices.clear();
                    Config.ALLOW_JOB_TOASTS.set(false);
                    try {
                        UUID job = start(helper, master, new CraftRequests.Requester(Optional.of(UUID.randomUUID()), "AWAY", ""));
                        scheduler(helper, master).cancel(job);
                    } finally {
                        Config.ALLOW_JOB_TOASTS.set(allowed);
                        JobEvents.unlisten(listener);
                    }
                    helper.assertTrue(notices.stream().noneMatch(n -> n.player() != null), "A toast went out with toasts off");
                    helper.assertTrue(notices.stream().anyMatch(n -> "AWAY".equals(n.queuedFor())), "Not queued");
                    NetworkRef network = ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(master));
                    List<MessageService.Message> messages = ElclServices.messages().messages(new ElclSystem(helper.getLevel().getServer(), network), "AWAY");
                    helper.assertTrue(messages.stream().anyMatch(m -> m.text().contains("Iron Ingot x2")), "Queue: " + messages);
                })
                .thenSucceed();
    }
}
