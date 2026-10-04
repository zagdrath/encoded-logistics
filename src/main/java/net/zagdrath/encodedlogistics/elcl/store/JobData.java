/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.store;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.zagdrath.encodedlogistics.elcl.screen.JobService;

// A system's jobs as saved (OS.md 5): the job number counter, its batch jobs (queued, held, running - a running one with
// its VM's state, saved from the live VM), the last ended jobs with their logs (LOGRTN of them), and its job schedule
// entries and trigger events with what they need to fire. Interactive jobs aren't saved: they end with their session.
public final class JobData {
    public static final class Batch {
        public final String number, name, user, command, submitted;
        public final @Nullable UUID player;
        // HOST(): *ANY or a host's name; where it runs ("" while queued); *JOBQ, *ACTIVE or *HELD.
        public final String requested;
        public String host = "", status = "*JOBQ";
        public int priority = 5;
        public boolean log, resumes, started;
        public final List<JobService.LogEntry> entries = new ArrayList<>();
        // The VM's state as last saved (a loaded job, before it runs again), and how the live one saves (null when none).
        public @Nullable CompoundTag vm;
        public @Nullable Supplier<CompoundTag> live;

        public Batch(String number, String name, String user, @Nullable UUID player, String command, String requested, String submitted) {
            this.number = number;
            this.name = name;
            this.user = user;
            this.player = player;
            this.command = command;
            this.requested = requested;
            this.submitted = submitted;
        }

        public String qualified() {
            return number + "/" + user + "/" + name;
        }
    }

    // A job that has ended: what it was, how it ended, its log.
    public record Ended(JobService.Job job, String ended, List<JobService.LogEntry> entries) {}

    // A job schedule entry: what JobService shows, who made it (it runs as them), and when it's next due - the game
    // clock's tick for *ONCE / *DAILY, real time (epoch ms) for *INTERVAL.
    public static final class Schedule {
        public JobService.ScheduleEntry entry;
        public @Nullable UUID player;
        public long due;

        public Schedule(JobService.ScheduleEntry entry, @Nullable UUID player, long due) {
            this.entry = entry;
            this.player = player;
            this.due = due;
        }
    }

    // A trigger: what JobService shows, who made it, whether its condition has been looked at yet (primed) and held last
    // time (edge triggering), when it last fired (epoch ms, the debounce) and the &DATA of an event that came during
    // the debounce, to fire with once it's over.
    public static final class Trigger {
        public JobService.Trigger trigger;
        public @Nullable UUID player;
        public boolean primed, wasTrue;
        public long lastFired;
        public @Nullable String pending;

        public Trigger(JobService.Trigger trigger, @Nullable UUID player) {
            this.trigger = trigger;
            this.player = player;
        }
    }

    public int nextNumber = 1;
    public final Map<String, Batch> batch = new LinkedHashMap<>();
    // Oldest first.
    public final List<Ended> ended = new ArrayList<>();
    public final Map<String, Schedule> schedules = new LinkedHashMap<>();
    public final Map<String, Trigger> triggers = new LinkedHashMap<>();

    // --- Saving ---

    private static CompoundTag entry(JobService.LogEntry entry) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("command", entry.command());
        tag.putString("id", entry.id());
        tag.putInt("severity", entry.severity());
        tag.putString("text", entry.text());
        tag.putString("from", entry.from());
        tag.putString("time", entry.time());
        return tag;
    }

    private static JobService.LogEntry entry(CompoundTag tag) {
        return new JobService.LogEntry(tag.getBooleanOr("command", false), tag.getStringOr("id", ""), tag.getIntOr("severity", 0), tag.getStringOr("text", ""),
                tag.getStringOr("from", ""), tag.getStringOr("time", ""));
    }

    private static ListTag entries(List<JobService.LogEntry> entries) {
        ListTag list = new ListTag();
        entries.forEach(e -> list.add(entry(e)));
        return list;
    }

    private static List<JobService.LogEntry> entries(ListTag list) {
        List<JobService.LogEntry> entries = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            entries.add(entry(list.getCompoundOrEmpty(i)));
        }
        return entries;
    }

    private static void uuid(CompoundTag tag, @Nullable UUID player) {
        if (player != null) {
            tag.putString("player", player.toString());
        }
    }

    private static @Nullable UUID uuid(CompoundTag tag) {
        try {
            String text = tag.getStringOr("player", "");
            return text.isEmpty() ? null : UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("next_number", nextNumber);
        ListTag jobs = new ListTag();
        for (Batch job : batch.values()) {
            CompoundTag j = new CompoundTag();
            j.putString("number", job.number);
            j.putString("name", job.name);
            j.putString("user", job.user);
            uuid(j, job.player);
            j.putString("command", job.command);
            j.putString("requested", job.requested);
            j.putString("submitted", job.submitted);
            j.putString("host", job.host);
            j.putString("status", job.status);
            j.putInt("priority", job.priority);
            j.putBoolean("log", job.log);
            j.putBoolean("resumes", job.resumes);
            j.putBoolean("started", job.started);
            j.put("entries", entries(job.entries));
            CompoundTag vm = job.live != null ? job.live.get() : job.vm;
            if (vm != null) {
                j.put("vm", vm);
            }
            jobs.add(j);
        }
        tag.put("batch", jobs);
        ListTag ended = new ListTag();
        for (Ended e : this.ended) {
            CompoundTag j = new CompoundTag();
            JobService.Job job = e.job();
            j.putString("number", job.number());
            j.putString("name", job.name());
            j.putString("user", job.user());
            j.putString("type", job.type());
            j.putString("host", job.host());
            j.putInt("priority", job.priority());
            j.putBoolean("log", job.log());
            j.putString("ended", e.ended());
            j.put("entries", entries(e.entries()));
            ended.add(j);
        }
        tag.put("ended", ended);
        ListTag schedules = new ListTag();
        for (Schedule s : this.schedules.values()) {
            CompoundTag j = new CompoundTag();
            JobService.ScheduleEntry entry = s.entry;
            j.putString("job", entry.job());
            j.putString("status", entry.status());
            j.putString("frequency", entry.frequency());
            j.putString("next", entry.next());
            j.putString("command", entry.command());
            j.putString("user", entry.user());
            j.putString("time", entry.time());
            j.putInt("interval", entry.interval());
            j.putLong("due", s.due);
            uuid(j, s.player);
            schedules.add(j);
        }
        tag.put("schedules", schedules);
        ListTag triggers = new ListTag();
        for (Trigger t : this.triggers.values()) {
            CompoundTag j = new CompoundTag();
            JobService.Trigger trigger = t.trigger;
            j.putString("name", trigger.name());
            j.putString("event", trigger.event());
            j.putString("item", trigger.item());
            j.putString("device", trigger.device());
            j.putString("value", trigger.value());
            j.putString("program", trigger.program());
            j.putString("status", trigger.status());
            j.putString("user", trigger.user());
            j.putBoolean("primed", t.primed);
            j.putBoolean("was_true", t.wasTrue);
            j.putLong("last_fired", t.lastFired);
            if (t.pending != null) {
                j.putString("pending", t.pending);
            }
            uuid(j, t.player);
            triggers.add(j);
        }
        tag.put("triggers", triggers);
        return tag;
    }

    public static JobData load(CompoundTag tag) {
        JobData data = new JobData();
        data.nextNumber = Math.max(1, tag.getIntOr("next_number", 1));
        ListTag jobs = tag.getListOrEmpty("batch");
        for (int i = 0; i < jobs.size(); i++) {
            CompoundTag j = jobs.getCompoundOrEmpty(i);
            Batch job = new Batch(j.getStringOr("number", ""), j.getStringOr("name", ""), j.getStringOr("user", ""), uuid(j), j.getStringOr("command", ""),
                    j.getStringOr("requested", "*ANY"), j.getStringOr("submitted", ""));
            job.host = j.getStringOr("host", "");
            job.status = j.getStringOr("status", "*JOBQ");
            job.priority = j.getIntOr("priority", 5);
            job.log = j.getBooleanOr("log", false);
            job.resumes = j.getBooleanOr("resumes", false);
            job.started = j.getBooleanOr("started", false);
            job.entries.addAll(entries(j.getListOrEmpty("entries")));
            job.vm = j.getCompound("vm").orElse(null);
            data.batch.put(job.number, job);
        }
        ListTag ended = tag.getListOrEmpty("ended");
        for (int i = 0; i < ended.size(); i++) {
            CompoundTag j = ended.getCompoundOrEmpty(i);
            JobService.Job job = new JobService.Job(j.getStringOr("number", ""), j.getStringOr("name", ""), j.getStringOr("user", ""), j.getStringOr("type", "BCH"),
                    j.getStringOr("host", ""), "*ENDED", 0, j.getIntOr("priority", 5), j.getBooleanOr("log", false));
            data.ended.add(new Ended(job, j.getStringOr("ended", ""), entries(j.getListOrEmpty("entries"))));
        }
        ListTag schedules = tag.getListOrEmpty("schedules");
        for (int i = 0; i < schedules.size(); i++) {
            CompoundTag j = schedules.getCompoundOrEmpty(i);
            JobService.ScheduleEntry entry = new JobService.ScheduleEntry(j.getStringOr("job", ""), j.getStringOr("status", "*SCD"),
                    j.getStringOr("frequency", "*ONCE"), j.getStringOr("next", ""), j.getStringOr("command", ""), j.getStringOr("user", ""),
                    j.getStringOr("time", ""), j.getIntOr("interval", 0));
            data.schedules.put(entry.job(), new Schedule(entry, uuid(j), j.getLongOr("due", 0)));
        }
        ListTag triggers = tag.getListOrEmpty("triggers");
        for (int i = 0; i < triggers.size(); i++) {
            CompoundTag j = triggers.getCompoundOrEmpty(i);
            JobService.Trigger trigger = new JobService.Trigger(j.getStringOr("name", ""), j.getStringOr("event", ""), j.getStringOr("item", ""),
                    j.getStringOr("device", ""), j.getStringOr("value", ""), j.getStringOr("program", ""), j.getStringOr("status", "*ACTIVE"),
                    j.getStringOr("user", ""));
            Trigger t = new Trigger(trigger, uuid(j));
            t.primed = j.getBooleanOr("primed", true);
            t.wasTrue = j.getBooleanOr("was_true", false);
            t.lastFired = j.getLongOr("last_fired", 0);
            t.pending = j.getString("pending").orElse(null);
            data.triggers.put(trigger.name(), t);
        }
        return data;
    }
}
