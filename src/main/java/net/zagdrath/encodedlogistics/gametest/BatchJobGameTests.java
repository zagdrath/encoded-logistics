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
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.device.DeviceSources;
import net.zagdrath.encodedlogistics.elcl.job.JobHost;
import net.zagdrath.encodedlogistics.elcl.job.JobHosts;
import net.zagdrath.encodedlogistics.elcl.job.JobManager;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.JobService;
import net.zagdrath.encodedlogistics.elcl.store.ElclStore;
import net.zagdrath.encodedlogistics.elcl.store.ElclConfig;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.device.ComputeServerDevice;
import net.zagdrath.encodedlogistics.terminal.TerminalCommands;
import net.zagdrath.encodedlogistics.terminal.TerminalContext;
import net.zagdrath.encodedlogistics.terminal.TerminalOutput;

// Batch jobs (Part 4): a job runs on a Compute Server, shows in Work with Active Jobs, logs its commands, and ends with
// ELC0310 when the server loses power; with fake hosts for the Midrange line - the Midrange System (1, +1 with an
// Expansion Cabinet), the Integrated Midrange System (4), the Mainframe (resumes) - jobs queue as *JOBQ when hosts are
// busy, HLDJOB / RLSJOB / ENDJOB work, a saved job resumes after a restart on a resuming host and ends with ELC0310 on
// one that doesn't, an endless loop keeps to its budget, and ended jobs' logs are kept (LOGRTN of them) across a reload.
final class BatchJobGameTests {
    private BatchJobGameTests() {}

    // A host for the tests: its name, capacity and whether it resumes; online unless told not to be.
    static final class FakeHost implements JobHost {
        final String name;
        int capacity;
        final boolean resumes;
        boolean online = true;

        FakeHost(String name, int capacity, boolean resumes) {
            this.name = name;
            this.capacity = capacity;
            this.resumes = resumes;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public int capacity() {
            return capacity;
        }

        @Override
        public boolean resumes() {
            return resumes;
        }

        @Override
        public boolean online() {
            return online;
        }
    }

    record Rig(TerminalContext[] contexts, ElclSystem[] systems, List<FakeHost> hosts) {
        TerminalContext context() {
            return contexts[0];
        }

        ElclSystem system() {
            return systems[0];
        }

        String user() {
            return contexts[0].user();
        }
    }

    @FunctionalInterface
    interface Body {
        void accept(GameTestHelper helper, GameTestSequence sequence, Rig rig);
    }

    // The desk rig, programs TST/NAP, TST/LONG, TST/FOREVER and TST/RESUME, and the fake hosts given (this system's
    // only, taken away after).
    @SuppressWarnings("removal")
    static void rig(GameTestHelper helper, List<FakeHost> hosts, Body body) {
        ElclGameTests.desk(helper);
        // Real seconds as ticks at 50 ms (this server runs them as fast as it can).
        var server = helper.getLevel().getServer();
        net.zagdrath.encodedlogistics.elcl.job.RealTime.set(() -> server.getTickCount() * 50L);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        TerminalContext[] context = new TerminalContext[1];
        ElclSystem[] system = new ElclSystem[1];
        DeviceSources.Source<JobHost> source = s -> s.equals(system[0]) ? List.copyOf(hosts) : List.of();
        Rig rig = new Rig(context, system, hosts);
        GameTestSequence sequence = helper.startSequence()
                .thenIdle(3)
                .thenExecute(() -> {
                    TerminalDeskBlockEntity desk = helper.getBlockEntity(ElclGameTests.DESK, TerminalDeskBlockEntity.class);
                    context[0] = new TerminalContext(helper.getLevel().getServer(), desk.network(), desk, player);
                    helper.assertTrue(context[0].network() != null, "Desk offline");
                    system[0] = new ElclSystem(context[0].server(), context[0].network());
                    ElclServices.jobs().interactive(system[0], context[0].user(), "batch", "ELDESK01");
                    String user = context[0].user();
                    program(system[0], user, "NAP", "PGM", "DLYJOB DLY(1)", "SNDMSG MSG('napped') TOUSR(*REQUESTER)", "ENDPGM");
                    program(system[0], user, "LONG", "PGM", "DLYJOB DLY(3600)", "ENDPGM");
                    program(system[0], user, "FOREVER", "PGM", "DCL VAR(&I) TYPE(*INT)", "DOWHILE COND(*TRUE)", "CHGVAR VAR(&I) VALUE(&I + 1)", "ENDDO",
                            "ENDPGM");
                    program(system[0], user, "RESUME", "PGM", "DCL VAR(&I) TYPE(*INT)", "CHGVAR VAR(&I) VALUE(41)", "DLYJOB DLY(2)",
                            "CHGVAR VAR(&I) VALUE(&I + 1)", "SNDMSG MSG('resumed' *BCAT %CHAR(&I)) TOUSR(*REQUESTER)", "ENDPGM");
                    JobHosts.register(source);
                });
        body.accept(helper, sequence, rig);
        sequence.thenExecute(() -> JobHosts.unregister(source)).thenSucceed();
    }

    static void program(ElclSystem system, String user, String name, String... lines) {
        try {
            try {
                ElclServices.libraries().createLibrary(system, user, "TST", "*PROD", "");
            } catch (ElclException ignored) {}
            ElclServices.libraries().createMember(system, user, "TST", name, "");
            ElclServices.libraries().save(system, user, "TST", name, SourceLine.number(List.of(lines), 1));
            if (!ElclServices.libraries().compile(system, user, "TST", name, "TST", name).created()) {
                throw new IllegalStateException(name + " didn't compile");
            }
        } catch (ElclException e) {
            throw new IllegalStateException(e.getMessage());
        }
    }

    static String run(TerminalContext context, String line) {
        TerminalOutput out = TerminalCommands.execute(context, line);
        return out.message() != null ? out.message().getString() : "";
    }

    static void expect(GameTestHelper helper, TerminalContext context, String line, String wanted) {
        String out = run(context, line);
        helper.assertTrue(out.startsWith(wanted), line + " -> " + out + ", wanted " + wanted);
    }

    private static List<JobService.Job> batch(Rig rig, String name) {
        List<JobService.Job> found = new ArrayList<>();
        for (JobService.Job job : ElclServices.jobs().jobs(rig.system())) {
            if (job.type().equals("BCH") && job.name().equals(name)) {
                found.add(job);
            }
        }
        return found;
    }

    private static long count(Rig rig, String name, String... statuses) {
        return batch(rig, name).stream().filter(job -> List.of(statuses).contains(job.status())).count();
    }

    private static boolean ended(Rig rig, String name, String messageId) {
        return ElclStore.of(rig.system()).jobs.ended.stream()
                .anyMatch(e -> e.job().name().equals(name) && e.entries().stream().anyMatch(entry -> entry.id().equals(messageId)));
    }

    private static boolean queued(Rig rig, String messageId) {
        return ElclServices.messages().messages(rig.system(), rig.user()).stream().anyMatch(m -> m.msgId().equals(messageId));
    }

    static boolean said(Rig rig, String text) {
        return ElclServices.messages().messages(rig.system(), rig.user()).stream().anyMatch(m -> m.text().contains(text));
    }

    // --- Compute Servers ---

    static void computeServer(GameTestHelper helper) {
        BlockPos master = RackGeometry.masterPos(new BlockPos(3, 1, 0), Direction.NORTH, RackGeometry.BOTTOM_FRONT);
        rig(helper, List.of(), (h, sequence, rig) -> sequence
                .thenExecute(() -> RackGameTests.install(h, master, RackDeviceType.COMPUTE_SERVER, 5, ComputeServerDevice.class))
                .thenIdle(4)
                .thenExecute(() -> expect(h, rig.context(), "SBMJOB CMD(CALL PGM(TST/NAP)) JOB(NAP) LOG(*YES)", "ELC0304"))
                .thenWaitUntil(() -> h.assertTrue(batch(rig, "NAP").stream().anyMatch(job -> job.host().equals("CMPSRV01")
                        && (job.status().equals("*ACTIVE") || job.status().equals("*WAIT"))), "NAP not on CMPSRV01: " + batch(rig, "NAP")))
                .thenWaitUntil(() -> h.assertTrue(said(rig, "napped") && ended(rig, "NAP", "ELC0307"), "NAP not done"))
                .thenExecute(() -> h.assertTrue(ElclStore.of(rig.system()).jobs.ended.stream().filter(e -> e.job().name().equals("NAP"))
                        .anyMatch(e -> e.entries().stream().anyMatch(entry -> entry.command() && entry.text().startsWith("CALL"))), "Commands not logged"))
                .thenExecute(() -> expect(h, rig.context(), "SBMJOB CMD(CALL PGM(TST/LONG)) JOB(LONG)", "ELC0304"))
                .thenWaitUntil(() -> h.assertTrue(count(rig, "LONG", "*WAIT") == 1, "LONG not waiting"))
                // The network loses its power: the server goes offline and its job ends.
                .thenExecute(() -> h.setBlock(RackGameTests.CONTROLLER, Blocks.AIR))
                .thenWaitUntil(() -> h.assertTrue(ended(rig, "LONG", "ELC0310") && queued(rig, "ELC0310"), "LONG not ended with ELC0310")));
    }

    // --- Fake hosts: the queue, hold / release / end ---

    static void queueAndHosts(GameTestHelper helper) {
        FakeHost midrange = new FakeHost("MIDRANGE01", 1, false), integrated = new FakeHost("INTEG01", 4, false);
        List<FakeHost> hosts = new ArrayList<>(List.of(midrange));
        rig(helper, hosts, (h, sequence, rig) -> sequence
                .thenExecute(() -> {
                    expect(h, rig.context(), "SBMJOB CMD(CALL TST/LONG) JOB(MR) HOST(MIDRANGE01)", "ELC0304");
                    expect(h, rig.context(), "SBMJOB CMD(CALL TST/LONG) JOB(MR) HOST(MIDRANGE01)", "ELC0304");
                    expect(h, rig.context(), "SBMJOB CMD(CALL TST/LONG) JOB(X) HOST(NOHOST01)", "ELC0301");
                })
                .thenIdle(3)
                .thenExecute(() -> h.assertTrue(count(rig, "MR", "*ACTIVE", "*WAIT") == 1 && count(rig, "MR", "*JOBQ") == 1,
                        "Midrange: " + batch(rig, "MR")))
                // An Expansion Cabinet: room for the second.
                .thenExecute(() -> midrange.capacity = 2)
                .thenIdle(3)
                .thenExecute(() -> h.assertTrue(count(rig, "MR", "*ACTIVE", "*WAIT") == 2, "With an Expansion Cabinet: " + batch(rig, "MR")))
                .thenExecute(() -> {
                    hosts.add(integrated);
                    for (int i = 0; i < 5; i++) {
                        expect(h, rig.context(), "SBMJOB CMD(CALL TST/LONG) JOB(IN) HOST(INTEG01)", "ELC0304");
                    }
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    h.assertTrue(count(rig, "IN", "*ACTIVE", "*WAIT") == 4 && count(rig, "IN", "*JOBQ") == 1, "Integrated: " + batch(rig, "IN"));
                    JobService.Job queued = batch(rig, "IN").stream().filter(job -> job.status().equals("*JOBQ")).findFirst().orElseThrow();
                    expect(h, rig.context(), "HLDJOB JOB(" + queued.number() + ")", "ELC0308");
                    JobService.Job running = batch(rig, "IN").stream().filter(job -> !job.status().equals("*HELD")).findFirst().orElseThrow();
                    expect(h, rig.context(), "ENDJOB JOB(" + running.number() + ") OPTION(*IMMED)", "ELC0311");
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    // A slot came free, but the held job stays held.
                    h.assertTrue(count(rig, "IN", "*HELD") == 1 && count(rig, "IN", "*ACTIVE", "*WAIT") == 3, "Held job started: " + batch(rig, "IN"));
                    h.assertTrue(ended(rig, "IN", "ELC0303"), "No ELC0303 in the ended job's log");
                    JobService.Job held = batch(rig, "IN").stream().filter(job -> job.status().equals("*HELD")).findFirst().orElseThrow();
                    expect(h, rig.context(), "RLSJOB JOB(" + held.number() + ")", "ELC0309");
                })
                .thenIdle(3)
                .thenExecute(() -> {
                    h.assertTrue(count(rig, "IN", "*ACTIVE", "*WAIT") == 4, "Released job didn't start: " + batch(rig, "IN"));
                    for (JobService.Job job : ElclServices.jobs().jobs(rig.system())) {
                        if (job.type().equals("BCH")) {
                            run(rig.context(), "ENDJOB JOB(" + job.number() + ") OPTION(*IMMED)");
                        }
                    }
                })
                .thenWaitUntil(() -> h.assertTrue(ElclServices.jobs().jobs(rig.system()).stream().noneMatch(job -> job.type().equals("BCH")),
                        "Jobs left")));
    }

    // --- A restart: a resuming host carries on, the other ends its job ---

    static void restart(GameTestHelper helper) {
        List<FakeHost> hosts = List.of(new FakeHost("MAINFRM01", 32, true), new FakeHost("MIDRANGE01", 1, false));
        rig(helper, hosts, (h, sequence, rig) -> sequence
                .thenExecute(() -> {
                    expect(h, rig.context(), "SBMJOB CMD(CALL TST/RESUME) JOB(MF) HOST(MAINFRM01)", "ELC0304");
                    expect(h, rig.context(), "SBMJOB CMD(CALL TST/RESUME) JOB(MR) HOST(MIDRANGE01)", "ELC0304");
                })
                .thenWaitUntil(() -> h.assertTrue(count(rig, "MF", "*WAIT") == 1 && count(rig, "MR", "*WAIT") == 1, "Not both in DLYJOB"))
                .thenExecute(() -> {
                    // The server stops (its runs go, unended) and starts again (the saved data loaded back).
                    JobManager.of(rig.system().server()).forget(rig.system());
                    ElclStore.get(rig.system().server()).reload(rig.system().network());
                })
                .thenWaitUntil(() -> h.assertTrue(said(rig, "resumed 42"), "Mainframe job didn't resume"))
                .thenWaitUntil(() -> h.assertTrue(ended(rig, "MR", "ELC0310") && ended(rig, "MF", "ELC0307"), "Midrange job not ended ELC0310")));
    }

    // --- An endless loop keeps to its budget; ENDJOB ends it ---

    static void budget(GameTestHelper helper) {
        List<FakeHost> hosts = List.of(new FakeHost("MIDRANGE01", 1, false));
        int[] ticks = new int[1];
        rig(helper, hosts, (h, sequence, rig) -> sequence
                .thenExecute(() -> expect(h, rig.context(), "SBMJOB CMD(CALL TST/FOREVER) JOB(SPIN)", "ELC0304"))
                .thenWaitUntil(() -> {
                    JobService.Job job = batch(rig, "SPIN").stream().findFirst().orElseThrow();
                    JobManager.Run run = JobManager.of(rig.system().server()).run(rig.system(), job.number());
                    h.assertTrue(run != null && run.vm() != null && run.vm().executed() > 0, "SPIN not running");
                    h.assertTrue(run.vm().executed() <= (long) ElclConfig.batchBudget() * (++ticks[0] + 5), "Over budget: " + run.vm().executed());
                    h.assertTrue(ticks[0] >= 10, "Watching");
                })
                .thenExecute(() -> {
                    JobService.Job job = batch(rig, "SPIN").stream().findFirst().orElseThrow();
                    expect(h, rig.context(), "ENDJOB JOB(SPIN)", "ELC0311");
                })
                .thenWaitUntil(() -> h.assertTrue(ended(rig, "SPIN", "ELC0303"), "SPIN not ended")));
    }

    // --- Ended jobs' logs: LOGRTN of them, kept across a reload ---

    static void logs(GameTestHelper helper) {
        List<FakeHost> hosts = List.of(new FakeHost("INTEG01", 4, false));
        rig(helper, hosts, (h, sequence, rig) -> sequence
                .thenExecute(() -> {
                    try {
                        ElclServices.sysvals().change(rig.system(), rig.user(), true, "LOGRTN", "2");
                    } catch (ElclException e) {
                        h.fail(e.getMessage());
                    }
                    for (String name : List.of("ONE", "TWO", "THREE")) {
                        expect(h, rig.context(), "SBMJOB CMD(SNDMSG MSG('" + name + "') TOUSR(*REQUESTER)) JOB(" + name + ")", "ELC0304");
                    }
                })
                .thenWaitUntil(() -> h.assertTrue(said(rig, "THREE") && said(rig, "ONE") && ElclServices.jobs().jobs(rig.system()).stream()
                        .noneMatch(job -> job.type().equals("BCH")), "Jobs not done"))
                .thenExecute(() -> ElclStore.get(rig.system().server()).reload(rig.system().network()))
                .thenExecute(() -> {
                    var ended = ElclStore.of(rig.system()).jobs.ended;
                    h.assertTrue(ended.size() == 2, "Kept " + ended.size() + " logs");
                    h.assertTrue(ended.stream().noneMatch(e -> e.job().name().equals("ONE")), "The oldest log kept");
                    try {
                        String number = ended.getLast().job().number();
                        h.assertTrue(ElclServices.jobs().log(rig.system(), number).stream().anyMatch(e -> e.id().equals("ELC0307")),
                                "Ended job's log lost");
                    } catch (ElclException e) {
                        h.fail(e.getMessage());
                    }
                }));
    }
}
