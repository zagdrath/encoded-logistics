/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.job;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.ElclMessages;
import net.zagdrath.encodedlogistics.elcl.parse.Parser;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.JobService;
import net.zagdrath.encodedlogistics.elcl.store.ElclStore;
import net.zagdrath.encodedlogistics.elcl.store.JobData;
import net.zagdrath.encodedlogistics.elcl.store.SystemData;
import net.zagdrath.encodedlogistics.elcl.vm.Vm;
import net.zagdrath.encodedlogistics.elcl.vm.VmHost;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;

// Script jobs (OS.md 5): the interactive job of each terminal session (in memory: it ends with the session), and batch
// jobs, kept with the system. SBMJOB puts a batch job on the job queue (*JOBQ; ELC0301 when the system has no job
// host at all); each tick queued jobs go to an online host with room (HOST(*ANY) any, else the one named), in priority
// order, at most QMAXJOB running. A running job's VM saves with it; a host that's unloaded or loses power ends its jobs
// (ELC0310) unless it resumes them, and after a restart only a resuming host's jobs carry on. HLDJOB / RLSJOB pause and
// release; ENDJOB ends (ELC0303 in the log). Ended jobs keep their logs, the last LOGRTN of them. Job IDs are
// nnnnnn/USER/NAME; commands take the number, the name if unique, or the whole ID.
public final class StoredJobService implements JobService {
    // What a batch job's command runs as: a program of that one command.
    static final String QCMD = "QSYS/QCMD";

    // A system's interactive jobs: by number, which session each is, and their logs.
    private static final class Interactive {
        final Map<String, Job> jobs = new LinkedHashMap<>();
        final Map<String, String> sessions = new HashMap<>();
        final Map<String, List<LogEntry>> logs = new HashMap<>();
    }

    private final Map<MinecraftServer, Map<NetworkRef, Interactive>> interactive = new WeakHashMap<>();

    private Interactive interactive(ElclSystem system) {
        return interactive.computeIfAbsent(system.server(), s -> new HashMap<>()).computeIfAbsent(system.network(), n -> new Interactive());
    }

    private static JobData data(ElclSystem system) {
        return ElclStore.of(system).jobs;
    }

    private static void changed(ElclSystem system) {
        ElclStore.of(system).changed();
    }

    private static String upper(String text) {
        return text.trim().toUpperCase(Locale.ROOT);
    }

    // ELC0401 unless the user may manage what owner created.
    private static void manage(ElclSystem system, String user, String owner) throws ElclException {
        if (!ElclServices.users().mayManage(system, user, owner)) {
            throw new ElclException("ELC0401", upper(user), "*JOBCTL");
        }
    }

    private static String number(JobData data) {
        String number = String.format(Locale.ROOT, "%06d", data.nextNumber);
        data.nextNumber = data.nextNumber >= 999_999 ? 1 : data.nextNumber + 1;
        return number;
    }

    // --- Views ---

    private static JobService.Job view(ElclSystem system, JobData.Batch job) {
        JobManager.Run run = JobManager.of(system.server()).run(system, job.number);
        String status = job.status;
        if (status.equals("*ACTIVE") && run != null && run.vm() != null && run.vm().waiting() != null) {
            status = "*WAIT";
        }
        return new Job(job.number, job.name, job.user, "BCH", job.host.isEmpty() ? job.requested : job.host, status, run != null ? run.budgetUse() : 0,
                job.priority, job.log);
    }

    private Job view(ElclSystem system, Job job) {
        JobManager.Run run = JobManager.of(system.server()).run(system, job.number());
        if (run == null || job.status().equals("*HELD")) {
            return job;
        }
        String status = run.vm() != null && run.vm().waiting() != null ? "*WAIT" : "*ACTIVE";
        return new Job(job.number(), job.name(), job.user(), job.type(), job.host(), status, run.budgetUse(), job.priority(), job.log());
    }

    @Override
    public synchronized List<Job> jobs(ElclSystem system) {
        List<Job> jobs = new ArrayList<>();
        for (Job job : interactive(system).jobs.values()) {
            jobs.add(view(system, job));
        }
        for (JobData.Batch job : data(system).batch.values()) {
            jobs.add(view(system, job));
        }
        return jobs;
    }

    // A job by number, nnnnnn/USER/NAME or a name only one job has - running or ended.
    @Override
    public synchronized Job job(ElclSystem system, String id) throws ElclException {
        String key = upper(id);
        List<Job> all = new ArrayList<>(jobs(system));
        for (JobData.Ended ended : data(system).ended) {
            all.add(ended.job());
        }
        Job byName = null;
        boolean twice = false;
        for (Job job : all) {
            if (job.number().equals(key) || job.qualified().equals(key)) {
                return job;
            }
            if (job.name().equals(key) && !job.status().equals("*ENDED")) {
                twice |= byName != null;
                byName = job;
            }
        }
        if (byName == null || twice) {
            throw new ElclException("ELC0302", key);
        }
        return byName;
    }

    private JobData.@Nullable Batch batch(ElclSystem system, String number) {
        return data(system).batch.get(number);
    }

    // --- Interactive jobs ---

    @Override
    public synchronized Job interactive(ElclSystem system, String user, String session, String host) {
        Interactive state = interactive(system);
        String number = state.sessions.get(session);
        if (number != null && state.jobs.containsKey(number)) {
            return state.jobs.get(number);
        }
        number = number(data(system));
        changed(system);
        Job job = new Job(number, "QINTER", upper(user), "INT", host, "*ACTIVE", 0, 5, true);
        state.jobs.put(number, job);
        state.sessions.put(session, number);
        return job;
    }

    @Override
    public synchronized void endInteractive(ElclSystem system, String session) {
        Interactive state = interactive(system);
        String number = state.sessions.remove(session);
        Job job = number != null ? state.jobs.remove(number) : null;
        if (job == null) {
            return;
        }
        JobManager.of(system.server()).end(system, number);
        List<LogEntry> log = state.logs.computeIfAbsent(number, k -> new ArrayList<>());
        log.add(entry(system, ElclMessage.of("ELC0307", job.qualified())));
        state.logs.remove(number);
        retire(system, job, log);
    }

    // An ended job's log kept (the last LOGRTN of them).
    private static void retire(ElclSystem system, Job job, List<LogEntry> log) {
        JobData data = data(system);
        data.ended.add(new JobData.Ended(new Job(job.number(), job.name(), job.user(), job.type(), job.host(), "*ENDED", 0, job.priority(), job.log()),
                system.nowShort(), List.copyOf(log)));
        int keep;
        try {
            keep = Integer.parseInt(ElclServices.sysvals().get(system, "LOGRTN").trim());
        } catch (NumberFormatException e) {
            keep = 50;
        }
        while (data.ended.size() > Math.max(0, keep)) {
            data.ended.removeFirst();
        }
        changed(system);
    }

    // --- Hold, release, end, change ---

    @Override
    public synchronized ElclMessage hold(ElclSystem system, String user, String id) throws ElclException {
        Job job = job(system, id);
        manage(system, user, job.user());
        JobData.Batch batch = batch(system, job.number());
        if (batch != null) {
            batch.status = "*HELD";
            changed(system);
        } else if (interactive(system).jobs.containsKey(job.number())) {
            interactive(system).jobs.put(job.number(), withStatus(job, "*HELD"));
        }
        pause(system, job.number(), true);
        ElclMessage message = ElclMessage.of("ELC0308", job.qualified());
        logMessage(system, job.number(), message);
        return message;
    }

    @Override
    public synchronized ElclMessage release(ElclSystem system, String user, String id) throws ElclException {
        Job job = job(system, id);
        manage(system, user, job.user());
        JobData.Batch batch = batch(system, job.number());
        if (batch != null) {
            batch.status = batch.started ? "*ACTIVE" : "*JOBQ";
            changed(system);
        } else if (interactive(system).jobs.containsKey(job.number())) {
            interactive(system).jobs.put(job.number(), withStatus(job, "*ACTIVE"));
        }
        pause(system, job.number(), false);
        ElclMessage message = ElclMessage.of("ELC0309", job.qualified());
        logMessage(system, job.number(), message);
        return message;
    }

    private static Job withStatus(Job job, String status) {
        return new Job(job.number(), job.name(), job.user(), job.type(), job.host(), status, job.budget(), job.priority(), job.log());
    }

    private static void pause(ElclSystem system, String number, boolean held) {
        JobManager.Run run = JobManager.of(system.server()).run(system, number);
        if (run != null) {
            run.held = held;
        }
    }

    // *CNTRLD lets the command running finish; *IMMED stops at once. A queued job just goes.
    @Override
    public synchronized ElclMessage end(ElclSystem system, String user, String id, String option) throws ElclException {
        Job job = job(system, id);
        manage(system, user, job.user());
        if (job.status().equals("*ENDED")) {
            throw new ElclException("ELC0302", upper(id));
        }
        ElclMessage message = ElclMessage.of("ELC0311", job.qualified(), option);
        logMessage(system, job.number(), message);
        JobData.Batch batch = batch(system, job.number());
        if (batch != null) {
            if (JobManager.of(system.server()).run(system, batch.number) != null) {
                endReason.put(batch.number, ElclMessage.of("ELC0303"));
                JobManager.of(system.server()).end(system, batch.number);
            } else {
                finish(system, batch, ElclMessage.of("ELC0303"));
            }
        } else {
            JobManager.of(system.server()).end(system, job.number());
        }
        return message;
    }

    // Why a running batch job is being ended (ENDJOB, a lost host), for its log when it does.
    private final Map<String, ElclMessage> endReason = new HashMap<>();

    @Override
    public synchronized void change(ElclSystem system, String user, String id, int priority, String log) throws ElclException {
        Job job = job(system, id);
        manage(system, user, job.user());
        boolean logging = log.equals("*SAME") ? job.log() : log.equals("*YES");
        JobData.Batch batch = batch(system, job.number());
        if (batch != null) {
            if (priority > 0) {
                batch.priority = priority;
            }
            batch.log = logging;
            JobManager.Run run = JobManager.of(system.server()).run(system, batch.number);
            if (run != null) {
                run.logCommands = logging;
            }
            changed(system);
            return;
        }
        Interactive state = interactive(system);
        if (state.jobs.containsKey(job.number())) {
            state.jobs.put(job.number(), new Job(job.number(), job.name(), job.user(), job.type(), job.host(), job.status(), job.budget(),
                    priority > 0 ? priority : job.priority(), logging));
        }
    }

    // --- Batch jobs ---

    @Override
    public synchronized ElclMessage submit(ElclSystem system, String user, @Nullable UUID player, String command, String name, String host, boolean log)
            throws ElclException {
        String jobName = upper(name);
        String wanted = upper(host);
        List<JobHost> hosts = JobHosts.all(system);
        if (hosts.isEmpty() || !wanted.equals("*ANY") && JobHosts.find(system, wanted) == null) {
            throw new ElclException("ELC0301", jobName);
        }
        Parser.Result parsed = Parser.parseCommand(command);
        if (!parsed.diagnostics().isEmpty()) {
            throw new ElclException(parsed.diagnostics().getFirst().message());
        }
        JobData data = data(system);
        JobData.Batch job = new JobData.Batch(number(data), jobName, upper(user), player, command.trim(), wanted, system.nowShort());
        job.log = log;
        data.batch.put(job.number, job);
        ElclMessage message = ElclMessage.of("ELC0304", job.qualified(), wanted);
        job.entries.add(entry(system, message));
        changed(system);
        return message;
    }

    // Every tick: jobs saved running come back (or end), hosts are checked, queued jobs start.
    public synchronized void tick(MinecraftServer server) {
        for (Map.Entry<NetworkRef, SystemData> entry : ElclStore.get(server).systems().entrySet()) {
            JobData data = entry.getValue().jobs;
            if (data.batch.isEmpty()) {
                continue;
            }
            ElclSystem system = new ElclSystem(server, entry.getKey());
            Map<String, JobHost> hosts = new HashMap<>();
            for (JobHost host : JobHosts.all(system)) {
                hosts.put(host.name(), host);
            }
            for (JobData.Batch job : List.copyOf(data.batch.values())) {
                if (!job.started) {
                    continue;
                }
                JobManager.Run run = JobManager.of(server).run(system, job.number);
                JobHost host = hosts.get(job.host);
                boolean up = host != null && host.online();
                if (run == null) {
                    // Saved running (the server restarted): on a resuming host it carries on once the host is up; on any
                    // other it has ended.
                    if (!job.resumes) {
                        finish(system, job, ElclMessage.of("ELC0310", job.host));
                    } else if (up) {
                        resume(system, job);
                    }
                } else if (!up && !job.resumes) {
                    // Unloaded, removed or out of power: its jobs end.
                    endReason.put(job.number, ElclMessage.of("ELC0310", job.host));
                    JobManager.of(server).end(system, job.number);
                } else {
                    run.paused = !up;
                }
            }
            dispatch(system, data, hosts);
        }
    }

    private void dispatch(ElclSystem system, JobData data, Map<String, JobHost> hosts) {
        List<JobData.Batch> queued = new ArrayList<>();
        Map<String, Integer> busy = new HashMap<>();
        int running = 0;
        for (JobData.Batch job : data.batch.values()) {
            if (job.status.equals("*JOBQ")) {
                queued.add(job);
            } else if (job.started) {
                busy.merge(job.host, 1, Integer::sum);
                running++;
            }
        }
        if (queued.isEmpty()) {
            return;
        }
        int max;
        try {
            max = Integer.parseInt(ElclServices.sysvals().get(system, "QMAXJOB").trim());
        } catch (NumberFormatException e) {
            max = 16;
        }
        queued.sort(Comparator.comparingInt((JobData.Batch job) -> job.priority).thenComparing(job -> job.number));
        for (JobData.Batch job : queued) {
            if (running >= max) {
                return;
            }
            JobHost host = null;
            for (JobHost candidate : hosts.values()) {
                if (candidate.online() && busy.getOrDefault(candidate.name(), 0) < candidate.capacity()
                        && (job.requested.equals("*ANY") || candidate.name().equalsIgnoreCase(job.requested))) {
                    host = candidate;
                    break;
                }
            }
            if (host == null) {
                continue;
            }
            busy.merge(host.name(), 1, Integer::sum);
            running++;
            job.host = host.name();
            job.resumes = host.resumes();
            job.started = true;
            job.status = "*ACTIVE";
            changed(system);
            start(system, job, null);
        }
    }

    private static JobManager.Run run(ElclSystem system, JobData.Batch job) {
        JobManager.Run run = new JobManager.Run(system, job.number, job.user, "QCMD", false,
                new BatchContext(system.server(), system.network(), job.user, job.player, job.number));
        run.logCommands = job.log;
        run.held = job.status.equals("*HELD");
        return run;
    }

    // Runs a job's command (as a program of one command), or carries on from its saved VM.
    private void start(ElclSystem system, JobData.Batch job, @Nullable Vm saved) {
        JobManager.Run run = run(system, job);
        Vm vm = saved;
        if (vm == null) {
            try {
                vm = Vm.start(new JobVmHost(run), new VmHost.Loaded(QCMD, List.of("PGM", job.command, "ENDPGM")), List.of());
            } catch (ElclException e) {
                finish(system, job, e.elclMessage(), false);
                return;
            }
        }
        Vm live = vm;
        job.live = live::save;
        job.vm = null;
        JobManager.of(system.server()).start(run, live, ended -> {
            synchronized (this) {
                ElclMessage reason = endReason.remove(job.number);
                // An escape that ended it is in its log already (JobVmHost.escaped).
                boolean failed = reason == null && live.failure() != null;
                finish(system, job, reason != null ? reason : failed ? live.failure() : ElclMessage.of("ELC0307", job.qualified()), failed);
            }
        });
    }

    private void resume(ElclSystem system, JobData.Batch job) {
        if (job.vm == null) {
            job.status = "*JOBQ";
            job.started = false;
            changed(system);
            return;
        }
        JobManager.Run run = run(system, job);
        try {
            start(system, job, Vm.load(new JobVmHost(run), job.vm));
        } catch (ElclException e) {
            finish(system, job, e.elclMessage(), false);
        }
    }

    // A batch job is over: its last message logged (unless it is already) and, for a lost host or an operator's end,
    // sent to its user; its log kept with the ended jobs.
    private void finish(ElclSystem system, JobData.Batch job, ElclMessage message) {
        finish(system, job, message, false);
    }

    private void finish(ElclSystem system, JobData.Batch job, ElclMessage message, boolean logged) {
        JobData data = data(system);
        if (data.batch.remove(job.number) == null) {
            return;
        }
        job.live = null;
        if (!logged) {
            job.entries.add(entry(system, message));
        }
        if (message.id().equals("ELC0310") || message.id().equals("ELC0303")) {
            ElclServices.messages().send(system, "QSYS", job.user, message.id(), message.severity(), job.qualified() + ": " + message.text());
        }
        retire(system, new Job(job.number, job.name, job.user, "BCH", job.host, "*ENDED", 0, job.priority, job.log), job.entries);
    }

    // --- Logs ---

    private static LogEntry entry(ElclSystem system, ElclMessage message) {
        return new LogEntry(false, message.id(), message.severity(), message.text(), message.severity() >= ElclMessages.ERROR ? "QCMD" : "QSYS",
                system.now());
    }

    @Override
    public synchronized List<LogEntry> log(ElclSystem system, String id) throws ElclException {
        Job job = job(system, id);
        JobData.Batch batch = batch(system, job.number());
        if (batch != null) {
            return List.copyOf(batch.entries);
        }
        List<LogEntry> live = interactive(system).logs.get(job.number());
        if (live != null) {
            return List.copyOf(live);
        }
        for (JobData.Ended ended : data(system).ended) {
            if (ended.job().number().equals(job.number())) {
                return ended.entries();
            }
        }
        return List.of();
    }

    private @Nullable List<LogEntry> logOf(ElclSystem system, String number) {
        JobData.Batch batch = batch(system, number);
        if (batch != null) {
            changed(system);
            return batch.entries;
        }
        Interactive state = interactive(system);
        return state.jobs.containsKey(number) ? state.logs.computeIfAbsent(number, k -> new ArrayList<>()) : null;
    }

    @Override
    public synchronized void logCommand(ElclSystem system, String id, String command) {
        List<LogEntry> log = logOf(system, id);
        if (log != null) {
            log.add(new LogEntry(true, "", 0, command, "", system.now()));
        }
    }

    @Override
    public synchronized void logMessage(ElclSystem system, String id, ElclMessage message) {
        List<LogEntry> log = logOf(system, id);
        if (log != null) {
            log.add(new LogEntry(false, message.id(), message.severity(), message.text(), "QCMD", system.now()));
        }
    }

    @Override
    public synchronized List<String> callStack(ElclSystem system, String id) throws ElclException {
        Job job = job(system, id);
        JobManager.Run run = JobManager.of(system.server()).run(system, job.number());
        if (run != null && run.vm() != null) {
            return run.vm().callStack();
        }
        return job.type().equals("INT") ? List.of("QCMD       Command processor") : List.of();
    }

    // --- Schedule entries ---

    @Override
    public synchronized List<ScheduleEntry> scheduleEntries(ElclSystem system) {
        List<ScheduleEntry> entries = new ArrayList<>();
        data(system).schedules.values().forEach(s -> entries.add(s.entry));
        return entries;
    }

    @Override
    public synchronized ElclMessage addScheduleEntry(ElclSystem system, String user, @Nullable UUID player, String job, String command, String frequency,
            String time, int interval) throws ElclException {
        String name = upper(job);
        JobData data = data(system);
        if (data.schedules.containsKey(name)) {
            throw new ElclException("ELC0305", name);
        }
        Parser.Result parsed = Parser.parseCommand(command);
        if (!parsed.diagnostics().isEmpty()) {
            throw new ElclException(parsed.diagnostics().getFirst().message());
        }
        ScheduleEntry entry = new ScheduleEntry(name, "*SCD", frequency, "", command.trim(), upper(user), time, interval);
        JobData.Schedule schedule = new JobData.Schedule(entry, player, 0);
        Schedules.plan(system, schedule, true);
        data.schedules.put(name, schedule);
        changed(system);
        return ElclMessage.of("ELC0312", name);
    }

    @Override
    public synchronized ElclMessage removeScheduleEntry(ElclSystem system, String user, String job) throws ElclException {
        JobData.Schedule schedule = data(system).schedules.get(upper(job));
        if (schedule == null) {
            throw new ElclException("ELC0302", upper(job));
        }
        manage(system, user, schedule.entry.user());
        data(system).schedules.remove(upper(job));
        changed(system);
        return ElclMessage.of("ELC0313", upper(job));
    }

    @Override
    public synchronized void holdScheduleEntry(ElclSystem system, String user, String job, boolean hold) throws ElclException {
        JobData.Schedule schedule = data(system).schedules.get(upper(job));
        if (schedule == null) {
            throw new ElclException("ELC0302", upper(job));
        }
        manage(system, user, schedule.entry.user());
        ScheduleEntry entry = schedule.entry;
        schedule.entry = new ScheduleEntry(entry.job(), hold ? "*HLD" : "*SCD", entry.frequency(), entry.next(), entry.command(), entry.user(), entry.time(),
                entry.interval());
        changed(system);
    }

    // --- Triggers ---

    @Override
    public synchronized List<Trigger> triggers(ElclSystem system) {
        List<Trigger> triggers = new ArrayList<>();
        data(system).triggers.values().forEach(t -> triggers.add(t.trigger));
        return triggers;
    }

    @Override
    public synchronized ElclMessage addTrigger(ElclSystem system, String user, @Nullable UUID player, Trigger trigger) throws ElclException {
        JobData data = data(system);
        String name = upper(trigger.name());
        if (data.triggers.containsKey(name)) {
            throw new ElclException("ELC0306", name);
        }
        data.triggers.put(name, new JobData.Trigger(new Trigger(name, trigger.event(), trigger.item(), trigger.device(), trigger.value(), trigger.program(),
                "*ACTIVE", upper(user)), player));
        changed(system);
        return ElclMessage.of("ELC0314", name);
    }

    @Override
    public synchronized ElclMessage removeTrigger(ElclSystem system, String user, String name) throws ElclException {
        JobData.Trigger trigger = data(system).triggers.get(upper(name));
        if (trigger == null) {
            throw new ElclException("ELC0103", upper(name), "TRG");
        }
        manage(system, user, trigger.trigger.user());
        data(system).triggers.remove(upper(name));
        changed(system);
        return ElclMessage.of("ELC0315", upper(name));
    }

    @Override
    public synchronized void holdTrigger(ElclSystem system, String user, String name, boolean hold) throws ElclException {
        JobData.Trigger t = data(system).triggers.get(upper(name));
        if (t == null) {
            throw new ElclException("ELC0103", upper(name), "TRG");
        }
        manage(system, user, t.trigger.user());
        Trigger trigger = t.trigger;
        t.trigger = new Trigger(trigger.name(), trigger.event(), trigger.item(), trigger.device(), trigger.value(), trigger.program(),
                hold ? "*HELD" : "*ACTIVE", trigger.user());
        changed(system);
    }
}
