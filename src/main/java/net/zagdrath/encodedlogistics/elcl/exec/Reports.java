/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.JobService;
import net.zagdrath.encodedlogistics.elcl.screen.SpoolService;
import net.zagdrath.encodedlogistics.midrange.Printout;
import net.zagdrath.encodedlogistics.midrange.TapeDriveBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkGraph;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.TapeSource;
import net.zagdrath.encodedlogistics.rack.TapeTier;
import net.zagdrath.encodedlogistics.storage.DriveView;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.StorageView;

// The printed reports (PRTRPT RPT(*INV|*DEV|*JOBLOG|*SPLF), the Line Printer's own screen), as a Printout's body
// (HANDOFF 9; previews/printout_page_*): Printout.COLUMNS wide, the page's header and footer are the printer's. A line
// may start with an ink mark (Printout.MARK_LIGHT, MARK_RED).
//   inventory: ITEM (30) QUANTITY (8, grouped) TIER (5) LOCATION, an item's hot and cold apart (cold in light ink), then
//   the total; devices: NAME TYPE STATUS LANES LOCATION (offline in light ink); job log: TIME JOB EVENT (an error in
//   red); a spooled file: its lines as they are.
public final class Reports {
    public static final String INVENTORY = "inventory", JOB_LOG = "joblog", DEVICES = "devices", SPOOLED = "splf:";
    private static final String RULE = "-".repeat(Printout.COLUMNS);

    private Reports() {}

    // A report's title on its pages.
    public static String title(String report, String name) {
        return switch (report) {
            case INVENTORY -> "NETWORK INVENTORY LISTING";
            case DEVICES -> "DEVICE LIST";
            case JOB_LOG -> "JOB LOG - " + name;
            default -> name;
        };
    }

    // An item as the reports name it: its path, for this mod's and Minecraft's.
    static String itemName(Item item) {
        Identifier id = BuiltInRegistries.ITEM.getKey(item);
        return id.getNamespace().equals(EncodedLogistics.MODID) || id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? id.getPath() : id.toString();
    }

    private static String cut(String text, int width) {
        return text.length() > width ? text.substring(0, width) : text;
    }

    // Every item, hot and cold apart: its quantity, tier and where it is (the device holding most of it).
    public static List<String> inventory(ElclSystem system, NetworkStorage storage) {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "%-30s %8s %-5s %s", "ITEM", "QUANTITY", "TIER", "LOCATION"));
        lines.add(RULE);
        List<ElclDevices.Device> devices = ElclDevices.list(system.server(), system.network());
        Map<Item, Long> hot = ElclItems.totals(storage, "*HOT"), cold = ElclItems.totals(storage, "*COLD");
        List<Item> items = new ArrayList<>(ElclItems.totals(storage, "*ALL").keySet());
        items.sort(Comparator.comparing(Reports::itemName));
        Map<Item, List<ItemKey>> keys = new HashMap<>();
        for (ItemKey key : storage.listAll().keySet()) {
            keys.computeIfAbsent(key.stack().getItem(), k -> new ArrayList<>()).add(key);
        }
        List<StorageView> views = storage.views();
        long total = 0;
        for (Item item : items) {
            long here = hot.getOrDefault(item, 0L), there = cold.getOrDefault(item, 0L);
            if (here > 0) {
                lines.add(String.format(Locale.ROOT, "%-30s %,8d %-5s %s", cut(itemName(item), 30), here, "HOT", hotLocation(views, devices, keys.getOrDefault(item, List.of()))));
            }
            if (there > 0) {
                lines.add(Printout.MARK_LIGHT + String.format(Locale.ROOT, "%-30s %,8d %-5s %s", cut(itemName(item), 30), there, "COLD",
                        coldLocation(storage, devices, keys.getOrDefault(item, List.of()))));
            }
            total += here + there;
        }
        lines.add("");
        lines.add(String.format(Locale.ROOT, "%-30s %,8d %s", "TOTAL", total, items.size() + " ITEM TYPES"));
        return lines;
    }

    // The device whose drive holds the most of an item in hot storage.
    private static String hotLocation(List<StorageView> views, List<ElclDevices.Device> devices, List<ItemKey> keys) {
        Map<String, Long> by = new HashMap<>();
        for (StorageView view : views) {
            long count = 0;
            for (ItemKey key : keys) {
                count += view.count(key);
            }
            if (count > 0) {
                by.merge(location(view, devices), count, Long::sum);
            }
        }
        return by.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("*NETWORK");
    }

    // Where a view's items are: its device's name (DISK01), a Drive Bay, a rack's storage, an Inventory Tap.
    private static String location(StorageView view, List<ElclDevices.Device> devices) {
        if (view instanceof DriveView drive && drive.bay() instanceof BlockEntity entity && entity.getLevel() != null) {
            String name = ElclDevices.nameAt(devices, NetworkGraph.at(entity.getLevel().dimension(), entity.getBlockPos()), null);
            return !name.isEmpty() ? name : entity instanceof DriveBayBlockEntity ? "DRIVEBAY" : "*DRIVE";
        }
        return view.isTap() ? "*TAP" : view.driveId() != null ? "*RACK" : "*NETWORK";
    }

    // The Tape Library or Tape Drive holding an item.
    private static String coldLocation(NetworkStorage storage, List<ElclDevices.Device> devices, List<ItemKey> keys) {
        if (storage.cold() instanceof TapeTier tier) {
            for (ItemKey key : keys) {
                TapeSource source = tier.holder(key);
                if (source instanceof TapeDriveBlockEntity drive) {
                    return drive.deviceName();
                }
                if (source instanceof RackDevice rack && rack.rack() != null) {
                    String name = ElclDevices.nameAt(devices, NetworkGraph.at(rack.rack().getLevel().dimension(), rack.rack().getBlockPos()), rack);
                    return name.isEmpty() ? "*TAPE" : name;
                }
            }
        }
        return "*TAPE";
    }

    // Every named device: name, type, status, its lanes and where it is.
    public static List<String> devices(ElclSystem system) {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "%-10s %-8s %-9s %5s  %s", "NAME", "TYPE", "STATUS", "LANES", "LOCATION"));
        lines.add(RULE);
        Map<Object, Integer> lanes = new HashMap<>();
        for (ControllerStructures.DeviceRow row : ControllerStructures.deviceRows(system.server(), system.network())) {
            lanes.put(row.rackDevice() != null ? row.rackDevice() : row.pos(), row.lanes());
        }
        for (ElclDevices.Device device : ElclDevices.list(system.server(), system.network())) {
            String status = ModCommands.status(device);
            Integer used = lanes.get(device.rack() != null ? device.rack() : device.pos());
            String where = device.pos().pos().getX() + "," + device.pos().pos().getY() + "," + device.pos().pos().getZ();
            String line = String.format(Locale.ROOT, "%-10s %-8s %-9s %5s  %s", cut(device.name(), 10), cut(device.type(), 8), status,
                    used == null ? "-" : used.toString(), where);
            lines.add(status.equals("*ONLINE") ? line : Printout.MARK_LIGHT + line);
        }
        return lines;
    }

    // A job's log: the time, the job, the event (a command as > ...; a message as its id and text, red for an error).
    public static List<String> jobLog(ElclSystem system, JobService.Job job) throws ElclException {
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "%-8s %-5s %s", "TIME", "JOB", "EVENT"));
        lines.add(RULE);
        for (JobService.LogEntry entry : ElclServices.jobs().log(system, job.number())) {
            String time = entry.time().length() > 8 ? entry.time().substring(entry.time().length() - 8) : entry.time();
            String event = entry.command() ? "> " + entry.text() : entry.text() + " (" + entry.id() + ")";
            String line = String.format(Locale.ROOT, "%-8s %-5s %s", time, cut(job.number(), 5), event);
            lines.add(!entry.command() && entry.severity() >= 30 ? Printout.MARK_RED + line : line);
        }
        if (lines.size() == 2) {
            lines.add("(no entries)");
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
        return new ArrayList<>(file.lines());
    }
}
