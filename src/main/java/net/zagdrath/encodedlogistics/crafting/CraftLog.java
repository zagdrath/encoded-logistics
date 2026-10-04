/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.crafting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.exec.ElclItems;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.store.ElclConfig;
import net.zagdrath.encodedlogistics.elcl.store.ElclStore;
import net.zagdrath.encodedlogistics.elcl.store.SystemData;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackScheduler;
import net.zagdrath.encodedlogistics.storage.ItemKey;

// The network's crafting job history: a record of every job that ended (JobEvents.ended) - done, failed or cancelled -
// kept with its Terminal OS data (SystemData.craftLog, oldest first) and saved with it. The last CRFLOGRTN of them
// stay (the system value; its default the craftLogRetention config), the oldest going first. Work with Jobs' history
// view, RTVCRFSTS, RTVCRFLOG, the *CRAFTEND trigger's WAIT and the Scheduler and server panels' recent jobs read it.
public final class CraftLog {
    public enum Status {
        DONE, FAILED, CANCELLED;

        static Status of(JobEvents.Outcome outcome) {
            return switch (outcome) {
                case COMPLETED -> DONE;
                case FAILED -> FAILED;
                case CANCELLED -> CANCELLED;
            };
        }

        // "*DONE", as RTVCRFSTS and RTVCRFLOG name it.
        public String special() {
            return "*" + name();
        }

        // "Done", as the screens show it.
        public String label() {
            return name().charAt(0) + name().substring(1).toLowerCase(Locale.ROOT);
        }
    }

    // One ended job: its number (C0042) and id; the item (its id), how many were asked for and how many were made; how it
    // ended (and why it failed: a gui.encodedlogistics.job.reason key); who asked - the player's Terminal OS user, and
    // the ELCL job (its schedule entry or trigger) when a script did; the scheduler that ran it (Scheduler or Rack
    // Scheduler, where); the overworld clock when it started (-1 unknown) and ended, how long it ran (game ticks);
    // and what it used up from storage (item id to count).
    public record Entry(int number, UUID id, String item, long requested, long produced, Status status, String reason, String user, String origin,
            String scheduler, BlockPos schedulerPos, long started, long ended, long duration, Map<String, Long> consumed) {
        public String jobId() {
            return String.format(Locale.ROOT, "C%04d", number);
        }

        // Who asked, short (a list's column): the ELCL job's name, or the user.
        public String requestedBy() {
            if (origin.isEmpty()) {
                return user;
            }
            String job = origin.split(" ", 2)[0];
            String[] parts = job.split("/");
            return parts[parts.length - 1];
        }

        // Who asked, in full: "ZAGDRATH", or "000123/ZAGDRATH/RESTOCK *SCDE NIGHTLY".
        public String requestedByFull() {
            return origin.isEmpty() ? user : origin;
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putInt("number", number);
            tag.putString("id", id.toString());
            tag.putString("item", item);
            tag.putLong("requested", requested);
            tag.putLong("produced", produced);
            tag.putString("status", status.name());
            tag.putString("reason", reason);
            tag.putString("user", user);
            tag.putString("origin", origin);
            tag.putString("scheduler", scheduler);
            tag.putLong("scheduler_pos", schedulerPos.asLong());
            tag.putLong("started", started);
            tag.putLong("ended", ended);
            tag.putLong("duration", duration);
            ListTag items = new ListTag();
            consumed.forEach((key, count) -> {
                CompoundTag c = new CompoundTag();
                c.putString("item", key);
                c.putLong("count", count);
                items.add(c);
            });
            tag.put("consumed", items);
            return tag;
        }

        public static @Nullable Entry load(CompoundTag tag) {
            UUID id;
            try {
                id = UUID.fromString(tag.getStringOr("id", ""));
            } catch (IllegalArgumentException e) {
                return null;
            }
            Status status;
            try {
                status = Status.valueOf(tag.getStringOr("status", "DONE"));
            } catch (IllegalArgumentException e) {
                status = Status.DONE;
            }
            Map<String, Long> consumed = new LinkedHashMap<>();
            ListTag items = tag.getListOrEmpty("consumed");
            for (int i = 0; i < items.size(); i++) {
                CompoundTag c = items.getCompoundOrEmpty(i);
                consumed.put(c.getStringOr("item", ""), c.getLongOr("count", 0));
            }
            return new Entry(tag.getIntOr("number", 0), id, tag.getStringOr("item", ""), tag.getLongOr("requested", 0), tag.getLongOr("produced", 0),
                    status, tag.getStringOr("reason", ""), tag.getStringOr("user", ""), tag.getStringOr("origin", ""), tag.getStringOr("scheduler", ""),
                    BlockPos.of(tag.getLongOr("scheduler_pos", 0)), tag.getLongOr("started", -1), tag.getLongOr("ended", -1),
                    tag.getLongOr("duration", 0), consumed);
        }
    }

    // How many finished jobs the Scheduler and server panels show.
    public static final int RECENT = 5;

    private CraftLog() {}

    // A record's item, to show (empty when the item's gone).
    public static ItemStack stack(Entry entry) {
        try {
            return new ItemStack(ElclItems.resolve(entry.item()));
        } catch (ElclException e) {
            return ItemStack.EMPTY;
        }
    }

    // A run's length (game ticks) as the screens show it: "45s", "3m 05s", "1h 02m".
    public static String duration(long ticks) {
        long seconds = Math.max(0, ticks) / 20;
        if (seconds < 60) {
            return seconds + "s";
        }
        if (seconds < 3_600) {
            return String.format(Locale.ROOT, "%dm %02ds", seconds / 60, seconds % 60);
        }
        return String.format(Locale.ROOT, "%dh %02dm", seconds / 3_600, seconds / 60 % 60);
    }

    private static SystemData data(MinecraftServer server, NetworkRef network) {
        return ElclStore.of(new ElclSystem(server, network));
    }

    // CRFLOGRTN: how many ended jobs the system keeps (0: none).
    public static int retention(ElclSystem system) {
        try {
            return Integer.parseInt(ElclServices.sysvals().get(system, "CRFLOGRTN"));
        } catch (NumberFormatException e) {
            return ElclConfig.craftLogRetention();
        }
    }

    // A job ended (JobEvents.ended): its record, the oldest past CRFLOGRTN removed.
    static void record(MinecraftServer server, NetworkRef network, @Nullable JobHost host, CraftingJob job, JobEvents.Outcome outcome, String reason) {
        Map<ItemKey, Long> atEnd = job.atEnd();
        Map<String, Long> consumed = new LinkedHashMap<>();
        job.taken.forEach((key, count) -> {
            long used = count - atEnd.getOrDefault(key, 0L);
            if (used > 0) {
                consumed.merge(ElclItems.id(key.stack().getItem()), used, Long::sum);
            }
        });
        long produced = outcome == JobEvents.Outcome.COMPLETED && job.returned == null ? job.amount : atEnd.getOrDefault(job.target, 0L);
        long now = server.overworld().getGameTime();
        Entry entry = new Entry(ControllerStructures.jobNumber(server, network, job.id), job.id, ElclItems.id(job.target.stack().getItem()), job.amount,
                produced, Status.of(outcome), reason, job.user, job.origin, host != null ? scheduler(host) : "", host != null ? host.hostPos() : BlockPos.ZERO,
                job.startedClock, server.overworld().getOverworldClockTime(), job.started >= 0 ? Math.max(0, now - job.started) : 0, consumed);
        ElclSystem system = new ElclSystem(server, network);
        SystemData data = ElclStore.of(system);
        data.craftLog.add(entry);
        trim(system);
        data.changed();
    }

    // "Rack Scheduler 12, 64, -30" / "Scheduler 3, 70, 8".
    static String scheduler(JobHost host) {
        BlockPos pos = host.hostPos();
        return (host instanceof RackScheduler ? "Rack Scheduler " : "Scheduler ") + pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    // Down to CRFLOGRTN (after a record, or the value lowered).
    public static void trim(ElclSystem system) {
        SystemData data = ElclStore.of(system);
        int keep = Math.max(0, retention(system));
        boolean changed = false;
        while (data.craftLog.size() > keep) {
            data.craftLog.removeFirst();
            changed = true;
        }
        if (changed) {
            data.changed();
        }
    }

    // Newest first.
    public static List<Entry> entries(MinecraftServer server, NetworkRef network) {
        return List.copyOf(data(server, network).craftLog).reversed();
    }

    public static @Nullable Entry find(MinecraftServer server, @Nullable NetworkRef network, UUID id) {
        if (network == null) {
            return null;
        }
        for (Entry entry : entries(server, network)) {
            if (entry.id().equals(id)) {
                return entry;
            }
        }
        return null;
    }

    // The newest record with that number.
    public static @Nullable Entry byNumber(MinecraftServer server, @Nullable NetworkRef network, int number) {
        if (network == null) {
            return null;
        }
        for (Entry entry : entries(server, network)) {
            if (entry.number() == number) {
                return entry;
            }
        }
        return null;
    }

    // Whether a record has the number (a new job's number skips it).
    public static boolean numberUsed(MinecraftServer server, NetworkRef network, int number) {
        return byNumber(server, network, number) != null;
    }

    // Removes the record with that number; false when there's none.
    public static boolean remove(MinecraftServer server, NetworkRef network, int number) {
        SystemData data = data(server, network);
        if (data.craftLog.removeIf(entry -> entry.number() == number)) {
            data.changed();
            return true;
        }
        return false;
    }

    // The last few that a scheduler ran, newest first.
    public static List<Entry> recent(MinecraftServer server, @Nullable NetworkRef network, BlockPos scheduler, int count) {
        List<Entry> recent = new ArrayList<>();
        if (network == null) {
            return recent;
        }
        for (Entry entry : entries(server, network)) {
            if (recent.size() >= count) {
                break;
            }
            if (entry.schedulerPos().equals(scheduler)) {
                recent.add(entry);
            }
        }
        return recent;
    }
}
