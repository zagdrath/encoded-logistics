/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.Item;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.JobService;
import net.zagdrath.encodedlogistics.elcl.screen.SpoolService;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// The printed reports (PRTRPT RPT(*INV|*DEV|*JOBLOG|*SPLF), and the Line Printer's own screen): a heading line - the
// report, the time, the system - a blank line, then the report's lines.
public final class Reports {
    private Reports() {}

    public static List<String> heading(ElclSystem system, String report) {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "%-40s %s   %s", report + " report", system.nowShort(), system.name()));
        lines.add("");
        return lines;
    }

    // Every item: hot and cold counts, by id.
    public static List<String> inventory(ElclSystem system, NetworkStorage storage) {
        List<String> lines = heading(system, "*INV");
        lines.add(String.format(Locale.ROOT, "%-40s %14s %14s", "Item", "Hot", "Cold"));
        Map<Item, Long> hot = ElclItems.totals(storage, "*HOT"), cold = ElclItems.totals(storage, "*COLD");
        List<Item> items = new ArrayList<>(ElclItems.totals(storage, "*ALL").keySet());
        items.sort(Comparator.comparing(ElclItems::id));
        for (Item item : items) {
            lines.add(String.format(Locale.ROOT, "%-40s %,14d %,14d", ElclItems.id(item), hot.getOrDefault(item, 0L), cold.getOrDefault(item, 0L)));
        }
        return lines;
    }

    // Every named device: name, type, status.
    public static List<String> devices(ElclSystem system) {
        List<String> lines = heading(system, "*DEV");
        lines.add(String.format(Locale.ROOT, "%-12s %-10s %s", "Device", "Type", "Status"));
        for (ElclDevices.Device device : ElclDevices.list(system.server(), system.network())) {
            lines.add(String.format(Locale.ROOT, "%-12s %-10s %s", device.name(), device.type(), ModCommands.status(device)));
        }
        return lines;
    }

    // A job's log: its commands (> ...) and messages (ID  text).
    public static List<String> jobLog(ElclSystem system, JobService.Job job) throws ElclException {
        List<String> lines = heading(system, "*JOBLOG");
        for (JobService.LogEntry entry : ElclServices.jobs().log(system, job.number())) {
            lines.add(entry.command() ? "> " + entry.text() : entry.id() + "  " + entry.text());
        }
        return lines;
    }

    // A spooled file of a user's (or a job's): the newest for *LAST, else the newest with that name; null for none.
    public static SpoolService.@Nullable SpooledFile spooled(ElclSystem system, @Nullable String user, @Nullable String job, String name) {
        for (SpoolService.SpooledFile file : ElclServices.spool().files(system, user, job)) {
            if (name.equals("*LAST") || file.name().equalsIgnoreCase(name)) {
                return file;
            }
        }
        return null;
    }

    public static List<String> spooled(ElclSystem system, SpoolService.SpooledFile file) {
        List<String> lines = heading(system, "*SPLF");
        lines.addAll(file.lines());
        return lines;
    }
}
