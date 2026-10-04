/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.job;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.server.MinecraftServer;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.JobService;
import net.zagdrath.encodedlogistics.elcl.store.ElclStore;
import net.zagdrath.encodedlogistics.elcl.store.JobData;
import net.zagdrath.encodedlogistics.elcl.store.SystemData;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;

// When a job schedule entry is next due (COMMANDS.md 9, HANDOFF open question 2): *ONCE and *DAILY at TIME(HHMM) on
// the game clock (the next time it comes round; *CURRENT: now), *INTERVAL every INTERVAL() real seconds. `due` is a
// game clock tick (overworld day time) for the first two and epoch milliseconds for *INTERVAL; `next` is shown.
public final class Schedules {
    private Schedules() {}

    // Every tick: entries due on loaded networks submit their command as a batch job, as the user who added them (a
    // failure, such as no job host, goes to that user's message queue). *ONCE entries go once they've run; the others
    // are planned again. Held (*HLD) entries wait.
    public static void tick(MinecraftServer server) {
        for (Map.Entry<NetworkRef, SystemData> entry : ElclStore.get(server).systems().entrySet()) {
            JobData data = entry.getValue().jobs;
            if (data.schedules.isEmpty() || !ControllerStructures.loaded(server, entry.getKey())) {
                continue;
            }
            ElclSystem system = new ElclSystem(server, entry.getKey());
            for (JobData.Schedule schedule : List.copyOf(data.schedules.values())) {
                JobService.ScheduleEntry e = schedule.entry;
                boolean due = realTime(schedule) ? RealTime.millis() >= schedule.due : system.ticks() >= schedule.due;
                if (!e.status().equals("*SCD") || !due) {
                    continue;
                }
                try {
                    ElclServices.jobs().submit(system, e.user(), schedule.player, e.command(), e.job(), "*ANY", false, "*SCDE " + e.job());
                } catch (ElclException failed) {
                    Triggers.report(system, e.user(), "Schedule entry " + e.job(), failed.elclMessage());
                }
                if (e.frequency().equals("*ONCE")) {
                    data.schedules.remove(e.job());
                } else {
                    plan(system, schedule, false);
                }
                entry.getValue().changed();
            }
        }
    }

    public static boolean realTime(JobData.Schedule schedule) {
        return schedule.entry.frequency().equals("*INTERVAL");
    }

    // Works out its next run: from now when it's new (first), else the one after the run just made.
    public static void plan(ElclSystem system, JobData.Schedule schedule, boolean first) {
        JobService.ScheduleEntry entry = schedule.entry;
        long now = system.ticks();
        String next;
        if (realTime(schedule)) {
            long interval = Math.max(1, entry.interval()) * 1_000L;
            schedule.due = (first ? RealTime.millis() : Math.max(schedule.due, RealTime.millis() - interval)) + interval;
            // Shown on the game clock, as near as real seconds go (20 ticks each).
            next = ElclSystem.clock(now + (schedule.due - RealTime.millis()) / 50, true);
        } else if (first) {
            schedule.due = at(entry.time(), now);
            next = ElclSystem.clock(schedule.due, true);
        } else {
            // The next time round still to come (a network unloaded for days runs it once, not for each day missed).
            do {
                schedule.due += 24_000;
            } while (schedule.due <= now);
            next = ElclSystem.clock(schedule.due, true);
        }
        schedule.entry = new JobService.ScheduleEntry(entry.job(), entry.status(), entry.frequency(), next, entry.command(), entry.user(), entry.time(),
                entry.interval());
    }

    // The game tick of the next time HHMM (or HHMMSS) comes round; *CURRENT: now.
    static long at(String time, long now) {
        String text = time.trim().toUpperCase(Locale.ROOT);
        if (text.isEmpty() || text.equals("*CURRENT") || !text.matches("\\d{4}(\\d{2})?")) {
            return now;
        }
        int seconds = Integer.parseInt(text.substring(0, 2)) * 3_600 + Integer.parseInt(text.substring(2, 4)) * 60
                + (text.length() == 6 ? Integer.parseInt(text.substring(4, 6)) : 0);
        // The game day starts at 06:00 (tick 0).
        long tickOfDay = (long) ((seconds - 6 * 3_600 + 86_400) % 86_400) * 24_000 / 86_400;
        long dayStart = now - Math.floorMod(now, 24_000L);
        long at = dayStart + tickOfDay;
        return at <= now ? at + 24_000 : at;
    }
}
