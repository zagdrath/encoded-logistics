/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;

// STUB: waiting on elcl.job (job hosts, the job queue, tick budgets, the VM running batch jobs, schedule entries and
// triggers firing, persistence). In memory per system: the interactive job of each open terminal session (QINTER on
// its desk), job logs, schedule entries and triggers as entered. With no job hosts yet, submitting is ELC0301.
final class StubJobService implements JobService {
    private static final class State {
        final Map<String, Job> jobs = new LinkedHashMap<>();
        final Map<String, String> sessions = new HashMap<>();
        final Map<String, List<LogEntry>> logs = new HashMap<>();
        final Map<String, ScheduleEntry> schedules = new LinkedHashMap<>();
        final Map<String, Trigger> triggers = new LinkedHashMap<>();
        int nextNumber = 100;
    }

    private final ElclServices.Store<State> store = new ElclServices.Store<>(system -> new State());

    private static String upper(String text) {
        return text.trim().toUpperCase(Locale.ROOT);
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized List<Job> jobs(ElclSystem system) {
        return List.copyOf(store.of(system).jobs.values());
    }

    // By number, name (when only one job has it) or nnnnnn/USER/NAME.
    // STUB: waiting on elcl.job
    @Override
    public synchronized Job job(ElclSystem system, String id) throws ElclException {
        String key = upper(id);
        Job found = null;
        for (Job job : store.of(system).jobs.values()) {
            if (job.number().equals(key) || job.qualified().equals(key)) {
                return job;
            }
            if (job.name().equals(key)) {
                if (found != null) {
                    throw new ElclException("ELC0302", key);
                }
                found = job;
            }
        }
        if (found == null) {
            throw new ElclException("ELC0302", key);
        }
        return found;
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized Job interactive(ElclSystem system, String user, String session, String host) {
        State state = store.of(system);
        String number = state.sessions.get(session);
        if (number != null && state.jobs.containsKey(number)) {
            return state.jobs.get(number);
        }
        number = String.format(Locale.ROOT, "%06d", state.nextNumber++);
        Job job = new Job(number, "QINTER", upper(user), "INT", host, "*ACTIVE", 0, 5, true);
        state.jobs.put(number, job);
        state.sessions.put(session, number);
        return job;
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized void endInteractive(ElclSystem system, String session) {
        State state = store.of(system);
        String number = state.sessions.remove(session);
        if (number != null) {
            Job job = state.jobs.remove(number);
            if (job != null) {
                logMessage(system, number, ElclMessage.of("ELC0307", job.qualified()));
            }
        }
    }

    private synchronized ElclMessage status(ElclSystem system, String id, String status, String messageId) throws ElclException {
        Job job = job(system, id);
        store.of(system).jobs.put(job.number(), new Job(job.number(), job.name(), job.user(), job.type(), job.host(), status, job.budget(), job.priority(),
                job.log()));
        ElclMessage message = ElclMessage.of(messageId, job.qualified());
        logMessage(system, job.number(), message);
        return message;
    }

    // STUB: waiting on elcl.job
    @Override
    public ElclMessage hold(ElclSystem system, String user, String id) throws ElclException {
        return status(system, id, "*HELD", "ELC0308");
    }

    // STUB: waiting on elcl.job
    @Override
    public ElclMessage release(ElclSystem system, String user, String id) throws ElclException {
        return status(system, id, "*ACTIVE", "ELC0309");
    }

    // STUB: waiting on elcl.job (a controlled end lets the program finish its command; immediate stops it)
    @Override
    public synchronized ElclMessage end(ElclSystem system, String user, String id, String option) throws ElclException {
        Job job = job(system, id);
        ElclMessage message = ElclMessage.of("ELC0311", job.qualified(), option);
        logMessage(system, job.number(), message);
        if (job.type().equals("BCH")) {
            store.of(system).jobs.remove(job.number());
        }
        return message;
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized void change(ElclSystem system, String user, String id, int priority, String log) throws ElclException {
        Job job = job(system, id);
        boolean logging = log.equals("*SAME") ? job.log() : log.equals("*YES");
        store.of(system).jobs.put(job.number(), new Job(job.number(), job.name(), job.user(), job.type(), job.host(), job.status(), job.budget(),
                priority > 0 ? priority : job.priority(), logging));
    }

    // STUB: waiting on elcl.job (job hosts: Midrange System, Compute Servers, Mainframe)
    @Override
    public ElclMessage submit(ElclSystem system, String user, String command, String name, String host, boolean log) throws ElclException {
        throw new ElclException("ELC0301", upper(name));
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized List<LogEntry> log(ElclSystem system, String id) throws ElclException {
        Job job = job(system, id);
        return List.copyOf(store.of(system).logs.getOrDefault(job.number(), List.of()));
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized void logCommand(ElclSystem system, String id, String command) {
        store.of(system).logs.computeIfAbsent(id, k -> new ArrayList<>()).add(new LogEntry(true, "", 0, command, "", system.now()));
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized void logMessage(ElclSystem system, String id, ElclMessage message) {
        store.of(system).logs.computeIfAbsent(id, k -> new ArrayList<>())
                .add(new LogEntry(false, message.id(), message.severity(), message.text(), "QCMD", system.now()));
    }

    // STUB: waiting on elcl.vm (the program and subroutine stack)
    @Override
    public synchronized List<String> callStack(ElclSystem system, String id) throws ElclException {
        Job job = job(system, id);
        return job.type().equals("INT") ? List.of("QCMD       Command processor") : List.of();
    }

    // --- Schedule entries ---

    // STUB: waiting on elcl.job
    @Override
    public synchronized List<ScheduleEntry> scheduleEntries(ElclSystem system) {
        return List.copyOf(store.of(system).schedules.values());
    }

    // The next run as the screens show it: *ONCE / *DAILY at TIME (HHMM, game clock), *INTERVAL seconds from now.
    private static String next(ElclSystem system, String frequency, String time, int interval) {
        long now = system.ticks();
        if (frequency.equals("*INTERVAL")) {
            return ElclSystem.clock(now + interval * 20L, true);
        }
        int hhmm = time.equals("*CURRENT") || time.isBlank() ? -1 : Integer.parseInt(time.length() > 4 ? time.substring(0, 4) : time);
        if (hhmm < 0) {
            return ElclSystem.clock(now, true);
        }
        // Game ticks of that time of day (the day starts at 06:00 = tick 0).
        long secondsOfDay = (hhmm / 100 * 3_600L + hhmm % 100 * 60L - 6 * 3_600L + 86_400L) % 86_400L;
        long tickOfDay = secondsOfDay * 24_000 / 86_400, dayStart = now - now % 24_000;
        long at = dayStart + tickOfDay;
        return ElclSystem.clock(at <= now ? at + 24_000 : at, true);
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized ElclMessage addScheduleEntry(ElclSystem system, String user, String job, String command, String frequency, String time,
            int interval) throws ElclException {
        String name = upper(job);
        State state = store.of(system);
        if (state.schedules.containsKey(name)) {
            throw new ElclException("ELC0305", name);
        }
        state.schedules.put(name, new ScheduleEntry(name, "*SCD", frequency, next(system, frequency, time, interval), command, upper(user), time, interval));
        return ElclMessage.of("ELC0312", name);
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized ElclMessage removeScheduleEntry(ElclSystem system, String user, String job) throws ElclException {
        if (store.of(system).schedules.remove(upper(job)) == null) {
            throw new ElclException("ELC0302", upper(job));
        }
        return ElclMessage.of("ELC0313", upper(job));
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized void holdScheduleEntry(ElclSystem system, String user, String job, boolean hold) throws ElclException {
        ScheduleEntry entry = store.of(system).schedules.get(upper(job));
        if (entry == null) {
            throw new ElclException("ELC0302", upper(job));
        }
        store.of(system).schedules.put(entry.job(), new ScheduleEntry(entry.job(), hold ? "*HLD" : "*SCD", entry.frequency(), entry.next(), entry.command(),
                entry.user(), entry.time(), entry.interval()));
    }

    // --- Triggers ---

    // STUB: waiting on elcl.job
    @Override
    public synchronized List<Trigger> triggers(ElclSystem system) {
        return List.copyOf(store.of(system).triggers.values());
    }

    // STUB: waiting on elcl.job (firing: edge-triggered, debounced 1 second, the program run as a batch job)
    @Override
    public synchronized ElclMessage addTrigger(ElclSystem system, String user, Trigger trigger) throws ElclException {
        State state = store.of(system);
        String name = upper(trigger.name());
        if (state.triggers.containsKey(name)) {
            throw new ElclException("ELC0306", name);
        }
        state.triggers.put(name, new Trigger(name, trigger.event(), trigger.item(), trigger.device(), trigger.value(), trigger.program(), "*ACTIVE",
                upper(user)));
        return ElclMessage.of("ELC0314", name);
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized ElclMessage removeTrigger(ElclSystem system, String user, String name) throws ElclException {
        if (store.of(system).triggers.remove(upper(name)) == null) {
            throw new ElclException("ELC0103", upper(name), "TRG");
        }
        return ElclMessage.of("ELC0315", upper(name));
    }

    // STUB: waiting on elcl.job
    @Override
    public synchronized void holdTrigger(ElclSystem system, String user, String name, boolean hold) throws ElclException {
        Trigger trigger = store.of(system).triggers.get(upper(name));
        if (trigger == null) {
            throw new ElclException("ELC0103", upper(name), "TRG");
        }
        store.of(system).triggers.put(trigger.name(), new Trigger(trigger.name(), trigger.event(), trigger.item(), trigger.device(), trigger.value(),
                trigger.program(), hold ? "*HELD" : "*ACTIVE", trigger.user()));
    }
}
