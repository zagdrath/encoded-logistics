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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.zagdrath.encodedlogistics.crafting.CraftLog;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.crafting.CraftingJob;
import net.zagdrath.encodedlogistics.crafting.JobEvents;
import net.zagdrath.encodedlogistics.crafting.JobHost;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.store.ElclConfig;
import net.zagdrath.encodedlogistics.elcl.store.ElclStore;
import net.zagdrath.encodedlogistics.elcl.store.SystemData;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.terminal.TerminalCommands;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;
import net.zagdrath.encodedlogistics.terminal.TerminalOutput;
import net.zagdrath.encodedlogistics.terminal.TerminalService;

// The crafting job history (CraftLog) on CraftingCompletionGameTests' rig: a record for each way a job ends, whichever
// way its outputs came back; what it keeps across a save and load, CRFLOGRTN; RTVCRFSTS and RTVCRFLOG on it; and the
// queries Work with Jobs' history view asks.
final class CraftHistoryGameTests {
    private static final StorageKey GOLD = StorageKey.of(new ItemStack(Items.GOLD_INGOT));

    private CraftHistoryGameTests() {}

    private static NetworkRef network(GameTestHelper helper, BlockPos master) {
        NetworkRef network = ControllerStructures.networkOf(helper.getLevel(), helper.absolutePos(master));
        helper.assertTrue(network != null, "Rack isn't on a network");
        return network;
    }

    private static List<CraftLog.Entry> entries(GameTestHelper helper, BlockPos master) {
        return CraftLog.entries(helper.getLevel().getServer(), network(helper, master));
    }

    private static CraftRequests.Requester player() {
        return new CraftRequests.Requester(Optional.of(UUID.randomUUID()), "ZAGDRATH", "");
    }

    // A done job's record: the item, 2 asked for and made, the 2 raw iron it took, the rack's Scheduler, who asked, a
    // start before its end and a run that took time.
    private static void assertDone(GameTestHelper helper, BlockPos master) {
        List<CraftLog.Entry> entries = entries(helper, master);
        helper.assertTrue(entries.size() == 1, "Records: " + entries.size());
        CraftLog.Entry entry = entries.getFirst();
        helper.assertTrue(entry.status() == CraftLog.Status.DONE, "Status " + entry.status());
        helper.assertTrue(entry.item().equals("IRON_INGOT") && entry.requested() == 2 && entry.produced() == 2,
                entry.item() + " " + entry.requested() + " / " + entry.produced());
        helper.assertTrue(entry.consumed().equals(java.util.Map.of("RAW_IRON", 2L)), "Consumed " + entry.consumed());
        helper.assertTrue(entry.scheduler().startsWith("Rack Scheduler") && entry.schedulerPos().equals(helper.absolutePos(master)),
                "Scheduler " + entry.scheduler());
        helper.assertTrue(entry.requestedBy().equals("ZAGDRATH") && entry.reason().isEmpty(), "Requested by " + entry.requestedBy());
        helper.assertTrue(entry.started() >= 0 && entry.ended() >= entry.started() && entry.duration() > 0,
                "Times " + entry.started() + " - " + entry.ended() + ", " + entry.duration());
    }

    // Done, the ingots back through an Ingress Port.
    static void completedThroughPort(GameTestHelper helper) {
        BlockPos master = CraftingCompletionGameTests.rig(helper, true);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> CraftingCompletionGameTests.start(helper, master, player()))
                .thenIdle(25)
                .thenExecute(() -> CraftingCompletionGameTests.smelt(helper, CraftingCompletionGameTests.OUTPUT))
                .thenIdle(45)
                .thenExecute(() -> assertDone(helper, master))
                .thenSucceed();
    }

    // Done, the ingots back through the Gateway.
    static void completedThroughGateway(GameTestHelper helper) {
        BlockPos master = CraftingCompletionGameTests.rig(helper, false);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> CraftingCompletionGameTests.start(helper, master, player()))
                .thenIdle(25)
                .thenExecute(() -> CraftingCompletionGameTests.smelt(helper, CraftingCompletionGameTests.INPUT))
                .thenIdle(25)
                .thenExecute(() -> assertDone(helper, master))
                .thenSucceed();
    }

    // Cancelled while its raw iron is in the machine (used up, nothing made); failed when the rack's broken, and why.
    static void cancelledAndFailed(GameTestHelper helper) {
        BlockPos master = CraftingCompletionGameTests.rig(helper, true);
        UUID[] job = new UUID[1];
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> job[0] = CraftingCompletionGameTests.start(helper, master, player()))
                .thenIdle(25)
                .thenExecute(() -> {
                    helper.assertTrue(CraftingCompletionGameTests.scheduler(helper, master).cancel(job[0]), "Cancel refused");
                    CraftLog.Entry entry = entries(helper, master).getFirst();
                    helper.assertTrue(entry.status() == CraftLog.Status.CANCELLED && entry.produced() == 0 && entry.id().equals(job[0]),
                            "Cancelled record " + entry);
                    helper.assertTrue(entry.consumed().equals(java.util.Map.of("RAW_IRON", 2L)), "Consumed " + entry.consumed());
                    CraftingCompletionGameTests.start(helper, master, player());
                })
                .thenIdle(5)
                .thenExecute(() -> {
                    CraftingCompletionGameTests.scheduler(helper, master).dropAll(helper.getLevel());
                    List<CraftLog.Entry> entries = entries(helper, master);
                    helper.assertTrue(entries.size() == 2, "Records: " + entries.size());
                    CraftLog.Entry failed = entries.getFirst();
                    helper.assertTrue(failed.status() == CraftLog.Status.FAILED && failed.reason().equals(JobEvents.SCHEDULER_REMOVED),
                            "Failed record " + failed);
                })
                .thenSucceed();
    }

    // A script's job: who asked is the ELCL job (and the schedule entry that submitted it), and its user.
    static void requestedByScript(GameTestHelper helper) {
        BlockPos master = CraftingCompletionGameTests.rig(helper, true);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    UUID id = CraftingCompletionGameTests.start(helper, master, new CraftRequests.Requester(Optional.empty(), "ZAGDRATH", "ZAGDRATH",
                            "000123/ZAGDRATH/RESTOCK *SCDE NIGHTLY"));
                    CraftingCompletionGameTests.scheduler(helper, master).cancel(id);
                    CraftLog.Entry entry = entries(helper, master).getFirst();
                    helper.assertTrue(entry.requestedBy().equals("RESTOCK") && entry.user().equals("ZAGDRATH"), "Requested by " + entry.requestedBy());
                    helper.assertTrue(entry.requestedByFull().equals("000123/ZAGDRATH/RESTOCK *SCDE NIGHTLY"), "In full " + entry.requestedByFull());
                })
                .thenSucceed();
    }

    // A job that ended, straight to the history (as a Scheduler reports it).
    private static UUID ended(GameTestHelper helper, BlockPos master, StorageKey item, JobEvents.Outcome outcome) {
        CraftingJob job = new CraftingJob(UUID.randomUUID(), item, 4, 0, List.of());
        job.user = "ZAGDRATH";
        JobHost host = CraftingCompletionGameTests.scheduler(helper, master);
        JobEvents.ended(helper.getLevel().getServer(), network(helper, master), host, job, outcome, "");
        return job.id;
    }

    private static int number(GameTestHelper helper, BlockPos master, UUID id) {
        CraftLog.Entry entry = CraftLog.find(helper.getLevel().getServer(), network(helper, master), id);
        helper.assertTrue(entry != null, "No record for " + id);
        return entry.number();
    }

    // The newest CRFLOGRTN stay (its default the config's); lowering it trims at once; it all survives a save and
    // load; and a new job's number skips those still in the history.
    static void retentionAndPersistence(GameTestHelper helper) {
        BlockPos master = CraftingCompletionGameTests.rig(helper, false);
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    ElclSystem system = new ElclSystem(helper.getLevel().getServer(), network(helper, master));
                    helper.assertTrue(CraftLog.retention(system) == ElclConfig.craftLogRetention(), "CRFLOGRTN " + CraftLog.retention(system));
                    UUID[] ids = new UUID[5];
                    for (int i = 0; i < ids.length; i++) {
                        ids[i] = ended(helper, master, CraftingCompletionGameTests.INGOT, JobEvents.Outcome.COMPLETED);
                    }
                    helper.assertTrue(entries(helper, master).size() == 5, "Records: " + entries(helper, master).size());
                    try {
                        ElclServices.sysvals().change(system, "QSECOFR", true, "CRFLOGRTN", "3");
                    } catch (ElclException e) {
                        throw new AssertionError(e.getMessage(), e);
                    }
                    List<CraftLog.Entry> entries = entries(helper, master);
                    helper.assertTrue(entries.size() == 3 && entries.getFirst().id().equals(ids[4]) && entries.getLast().id().equals(ids[2]),
                            "After CRFLOGRTN 3: " + entries.stream().map(CraftLog.Entry::id).toList());
                    ended(helper, master, CraftingCompletionGameTests.INGOT, JobEvents.Outcome.FAILED);
                    helper.assertTrue(entries(helper, master).size() == 3, "Past CRFLOGRTN: " + entries(helper, master).size());

                    SystemData data = ElclStore.of(system);
                    SystemData loaded = SystemData.load(data.save());
                    helper.assertTrue(loaded.craftLog.equals(data.craftLog), "Not the same after a save and load");

                    int used = entries(helper, master).getFirst().number();
                    for (int i = 0; i < 20; i++) {
                        int next = ControllerStructures.jobNumber(helper.getLevel().getServer(), network(helper, master), UUID.randomUUID());
                        helper.assertTrue(!CraftLog.numberUsed(helper.getLevel().getServer(), network(helper, master), next) && next != used,
                                "Job number " + next + " is still in the history");
                    }
                })
                .thenSucceed();
    }

    private static String run(TerminalContext context, String line) {
        TerminalOutput out = TerminalCommands.execute(context, line);
        StringBuilder text = new StringBuilder();
        for (TerminalLine l : out.lines()) {
            text.append(l.text().trim()).append('\n');
        }
        if (out.message() != null) {
            text.append(out.message().getString());
        }
        return text.toString();
    }

    private static void expect(GameTestHelper helper, TerminalContext context, String line, String wanted) {
        String out = run(context, line);
        helper.assertTrue(out.contains(wanted), line + " -> " + out.replace('\n', '|') + ", wanted " + wanted);
    }

    // The IDs in out, in the order given (and only those of them that should be there).
    private static void expectList(GameTestHelper helper, TerminalContext context, String line, List<String> wanted, List<String> unwanted) {
        String out = run(context, line);
        int at = -1;
        for (String id : wanted) {
            int found = out.indexOf(id);
            helper.assertTrue(found > at, line + " -> " + out.replace('\n', '|') + ": " + id + " missing or out of order");
            at = found;
        }
        for (String id : unwanted) {
            helper.assertTrue(!out.contains(id), line + " -> " + out.replace('\n', '|') + ": " + id + " shouldn't be there");
        }
    }

    // RTVCRFSTS on ended jobs (and ELC1404 once one's aged out); RTVCRFLOG with each filter; the history view's queries.
    static void commands(GameTestHelper helper) {
        BlockPos master = CraftingCompletionGameTests.rig(helper, false);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    NetworkRef network = network(helper, master);
                    TerminalContext context = new TerminalContext(helper.getLevel().getServer(), network, null, player);
                    String done = String.format("C%04d", number(helper, master, ended(helper, master, CraftingCompletionGameTests.INGOT,
                            JobEvents.Outcome.COMPLETED)));
                    String failed = String.format("C%04d", number(helper, master, ended(helper, master, GOLD, JobEvents.Outcome.FAILED)));
                    String cancelled = String.format("C%04d", number(helper, master, ended(helper, master, CraftingCompletionGameTests.INGOT,
                            JobEvents.Outcome.CANCELLED)));
                    expect(helper, context, "RTVCRFSTS " + done, "RTNSTS = *DONE");
                    expect(helper, context, "RTVCRFSTS " + failed, "RTNSTS = *FAILED");
                    expect(helper, context, "RTVCRFSTS " + cancelled, "RTNSTS = *CANCELLED");
                    expectList(helper, context, "RTVCRFLOG", List.of(cancelled, failed, done), List.of());
                    expectList(helper, context, "RTVCRFLOG ITEM(IRON_INGOT)", List.of(cancelled, done), List.of(failed));
                    expectList(helper, context, "RTVCRFLOG STATUS(*FAILED)", List.of(failed), List.of(done, cancelled));
                    expectList(helper, context, "RTVCRFLOG STATUS(*DONE)", List.of(done), List.of(failed, cancelled));
                    expectList(helper, context, "RTVCRFLOG MAX(2)", List.of(cancelled, failed), List.of(done));

                    // The history view's rows, one record in full, and 4=Remove.
                    TerminalOutput rows = TerminalService.handle(context, TerminalService.QUERY, "jobhistory");
                    helper.assertTrue(rows.lines().size() == 3, "History rows: " + rows.lines().size());
                    String item = rows.lines().getFirst().cells().get(2).text().getString();
                    helper.assertTrue(item.equals("Iron Ingot"), "Item cell: " + item);
                    String number = done.substring(1);
                    TerminalOutput record = TerminalService.handle(context, TerminalService.QUERY, "jobrecord " + number);
                    helper.assertTrue(record.lines().stream().anyMatch(l -> l.text().contains("Rack Scheduler")), "Record has no scheduler");
                    TerminalService.handle(context, TerminalService.QUERY, "removejobrecord " + number);
                    expect(helper, context, "RTVCRFSTS " + done, "ELC1404");

                    // Work with Jobs' rows name a running job by their first cell (what 4=Cancel and 5=Display send).
                    UUID running = CraftingCompletionGameTests.start(helper, master, player());
                    TerminalOutput jobs = TerminalService.handle(context, TerminalService.QUERY, "jobs");
                    String first = jobs.lines().getFirst().cells().getFirst().text().getString().trim();
                    helper.assertTrue(first.equals(String.format("%04d", ControllerStructures.jobNumber(helper.getLevel().getServer(), network, running))),
                            "First cell " + first);
                    helper.assertTrue(!TerminalService.handle(context, TerminalService.QUERY, "job " + first).lines().isEmpty(), "Job " + first + " not found");

                    // Aged out: CRFLOGRTN 1 keeps only the newest.
                    try {
                        ElclServices.sysvals().change(new ElclSystem(helper.getLevel().getServer(), network), "QSECOFR", true, "CRFLOGRTN", "1");
                    } catch (ElclException e) {
                        throw new AssertionError(e.getMessage(), e);
                    }
                    expect(helper, context, "RTVCRFSTS " + failed, "ELC1404");
                    expect(helper, context, "RTVCRFSTS " + cancelled, "RTNSTS = *CANCELLED");
                })
                .thenSucceed();
    }
}
