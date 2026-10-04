/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.job;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.exec.ElclContext;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.store.ElclConfig;
import net.zagdrath.encodedlogistics.elcl.vm.Vm;

// The programs running in jobs on a server (OS.md 5, ELCL_SPEC.md 9): every tick each gets up to its budget of VM
// instructions (interactive 200, batch 100 by default), taking turns so the global cap (2,000) shares out fairly. A job
// waiting (DLYJOB, a recall, a craft) costs nothing. When a program ends, its run says so (an interactive caller gets
// its messages).
public final class JobManager {
    // A program running in a job: its system, job number and user, interactive or batch, what its commands run as,
    // its VM, and the messages it sent (what an interactive caller sees).
    public static final class Run {
        public final ElclSystem system;
        public final String job, user, program;
        public final boolean interactive;
        public final ElclContext context;
        public final List<ElclMessage> output = new ArrayList<>();
        public boolean logCommands;
        // Held (HLDJOB) or paused (its host is down but resumes its jobs): it doesn't run.
        public boolean held, paused;
        @Nullable Vm vm;
        int lastUsed;
        @Nullable Consumer<Run> onEnd;

        public Run(ElclSystem system, String job, String user, String program, boolean interactive, ElclContext context) {
            this.system = system;
            this.job = job;
            this.user = user;
            this.program = program;
            this.interactive = interactive;
            this.context = context;
        }

        public @Nullable Vm vm() {
            return vm;
        }

        // Instructions it ran on the last tick, as a % of its budget.
        public int budgetUse() {
            return Math.min(100, lastUsed * 100 / Math.max(1, budget()));
        }

        int budget() {
            return interactive ? ElclConfig.interactiveBudget() : ElclConfig.batchBudget();
        }
    }

    private static final Map<MinecraftServer, JobManager> MANAGERS = new WeakHashMap<>();
    private final List<Run> runs = new ArrayList<>();
    private int next;

    private JobManager() {}

    public static synchronized JobManager of(MinecraftServer server) {
        return MANAGERS.computeIfAbsent(server, s -> new JobManager());
    }

    // Starts a program in a job (its VM made with the run's host); onEnd hears when it ends.
    public synchronized void start(Run run, Vm vm, @Nullable Consumer<Run> onEnd) {
        run.vm = vm;
        run.onEnd = onEnd;
        runs.add(run);
    }

    public synchronized @Nullable Run run(ElclSystem system, String job) {
        for (Run run : runs) {
            if (run.system.equals(system) && run.job.equals(job)) {
                return run;
            }
        }
        return null;
    }

    public synchronized List<Run> runs() {
        return List.copyOf(runs);
    }

    // ENDJOB: the program stops (before its next instruction).
    public synchronized boolean end(ElclSystem system, String job) {
        Run run = run(system, job);
        if (run == null || run.vm == null) {
            return false;
        }
        run.vm.end();
        run.held = false;
        run.paused = false;
        return true;
    }

    // The server stopping, as far as a system's jobs go (the game tests' restart): its runs are dropped, unended.
    public synchronized void forget(ElclSystem system) {
        runs.removeIf(run -> run.system.equals(system));
    }

    public void tick(MinecraftServer server) {
        List<Run> ended = new ArrayList<>();
        synchronized (this) {
            int global = ElclConfig.globalBudget();
            int count = runs.size();
            for (int i = 0; i < count && global > 0; i++) {
                Run run = runs.get((next + i) % count);
                Vm vm = run.vm;
                if (vm == null || run.held || run.paused) {
                    run.lastUsed = 0;
                    continue;
                }
                run.lastUsed = vm.run(Math.min(run.budget(), global));
                global -= run.lastUsed;
                if (vm.state() == Vm.State.ENDED) {
                    ended.add(run);
                }
            }
            next = count == 0 ? 0 : (next + 1) % count;
            runs.removeAll(ended);
        }
        for (Run run : ended) {
            if (run.onEnd != null) {
                run.onEnd.accept(run);
            }
        }
    }
}
