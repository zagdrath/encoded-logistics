/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.display;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.zagdrath.encodedlogistics.crafting.CraftingJob;
import net.zagdrath.encodedlogistics.crafting.JobHost;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.db.DbRecord;
import net.zagdrath.encodedlogistics.elcl.db.FieldDef;
import net.zagdrath.encodedlogistics.elcl.db.Query;
import net.zagdrath.encodedlogistics.elcl.db.RecordFormat;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.exec.ElclItems;
import net.zagdrath.encodedlogistics.elcl.exec.MachineCommands;
import net.zagdrath.encodedlogistics.elcl.exec.ModCommands;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.FileService;
import net.zagdrath.encodedlogistics.elcl.store.SystemData;
import net.zagdrath.encodedlogistics.elcl.store.StoredLibraryService;
import net.zagdrath.encodedlogistics.machine.MachineInfo;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.device.MonitoringServerDevice;
import net.zagdrath.encodedlogistics.rack.device.TapeLibraryDevice;
import net.zagdrath.encodedlogistics.rack.device.UpsDevice;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// The live widgets' data (HANDOFF 3), worked out on the server once a second for each region of a screen: storage hot
// and cold, energy, lanes, active crafting jobs, an item's count, the devices (by type), the UPSes, a file's records (a
// table: a RUNQRY's selection and sort; the first TABLE_ROWS), and graphs - of
// item flow, energy, lanes and crafting from a Monitoring Server on the network when there is one, else (and always for
// *STORAGE and *ITEM) from the screen's own history.
final class DisplayData {
    // A graph's ranges in samples of the screen's own history (one a second).
    private static final int MAX_POINTS = 256;

    private DisplayData() {}

    // Graph stats: their Monitoring Server stat, or -1 when only the screen's history has them.
    static int monitoringStat(String stat) {
        return switch (stat) {
            case "*ITEMFLOW" -> MonitoringServerDevice.ITEMS;
            case "*ENERGY" -> MonitoringServerDevice.ENERGY;
            case "*LANES" -> MonitoringServerDevice.LANES;
            case "*CRAFTING" -> MonitoringServerDevice.JOBS;
            default -> -1;
        };
    }

    static int monitoringRange(String range) {
        return switch (range) {
            case "*1M" -> MonitoringServerDevice.MINUTE;
            case "*1H" -> MonitoringServerDevice.HOUR;
            case "*1D" -> MonitoringServerDevice.DAY;
            default -> MonitoringServerDevice.TEN_MINUTES;
        };
    }

    static int historySamples(String range) {
        return range.equals("*1M") ? 60 : Integer.MAX_VALUE;
    }

    // A machine stat (*MCHOPS: operations a minute, *MCHFE: FE stored) of the machine named in the widget's item.
    static boolean machineStat(String stat) {
        return stat.equals("*MCHOPS") || stat.equals("*MCHFE");
    }

    static String historyKey(DisplayContent.Widget widget) {
        return widget.stat().equals("*ITEM") || machineStat(widget.stat()) ? widget.stat() + " " + widget.item() : widget.stat();
    }

    // Samples the stats the screen's graphs show into its history.
    static void sample(MinecraftServer server, @Nullable NetworkRef network, DisplayContent content, int canvasW, int canvasH, DisplayHistory history) {
        Set<String> keys = new HashSet<>();
        if (network == null) {
            return;
        }
        for (DisplayContent.Region region : content.regions(canvasW, canvasH)) {
            DisplayContent.Widget widget = region.widget();
            if (!widget.kind().equals("*GRAPH")) {
                continue;
            }
            String key = historyKey(widget);
            keys.add(key);
            Float value = current(server, network, widget, history);
            if (value != null) {
                history.add(key, value);
            }
        }
        history.keep(keys);
    }

    // A graph's stat right now.
    private static @Nullable Float current(MinecraftServer server, NetworkRef network, DisplayContent.Widget widget, DisplayHistory history) {
        NetworkSnapshot snapshot = ControllerStructures.snapshotOf(server, network);
        ControllerStructures.NetworkStats stats = ControllerStructures.stats(server, network);
        return switch (widget.stat()) {
            case "*ENERGY" -> (float) snapshot.usage();
            case "*LANES" -> (float) snapshot.lanesUsed();
            case "*ITEMFLOW" -> stats == null ? null : history.perMinute("*ITEMFLOW", stats.itemsMoved());
            case "*CRAFTING" -> stats == null ? null : history.perMinute("*CRAFTING", stats.jobsDone());
            case "*STORAGE" -> {
                long[] hot = hot(server, network);
                yield hot[1] <= 0 ? 0F : hot[0] * 100F / hot[1];
            }
            case "*ITEM" -> {
                Long count = count(server, network, widget.item());
                yield count == null ? null : count.floatValue();
            }
            case "*MCHOPS", "*MCHFE" -> {
                MachineInfo info = machine(server, network, widget.item());
                yield info == null ? null : widget.stat().equals("*MCHOPS") ? (float) info.statistics().operationsPerMinute()
                        : info.energy().map(energy -> (float) energy.stored()).orElse(null);
            }
            default -> null;
        };
    }

    // The bridged machine with that device name now, or null.
    private static @Nullable MachineInfo machine(MinecraftServer server, NetworkRef network, String name) {
        try {
            return MachineCommands.find(server, network, name).info();
        } catch (ElclException e) {
            return null;
        }
    }

    // --- Frames ---

    static List<DisplayFrame> frames(MinecraftServer server, @Nullable NetworkRef network, DisplayContent content, int canvasW, int canvasH,
            DisplayHistory history) {
        List<DisplayFrame> frames = new ArrayList<>();
        if (network == null) {
            return frames;
        }
        for (DisplayContent.Region region : content.regions(canvasW, canvasH)) {
            if (region.widget().live()) {
                frames.add(frame(server, network, region, history));
            }
        }
        return frames;
    }

    private static DisplayFrame frame(MinecraftServer server, NetworkRef network, DisplayContent.Region region, DisplayHistory history) {
        DisplayContent.Widget widget = region.widget();
        String name = region.name();
        NetworkSnapshot snapshot = ControllerStructures.snapshotOf(server, network);
        return switch (widget.kind()) {
            case "*STORAGE" -> {
                long[] hot = hot(server, network);
                yield new DisplayFrame(name, "STORAGE", percent(hot[0], hot[1]), List.of(hot[0], hot[1]), List.of(), List.of());
            }
            case "*COLD" -> {
                long used = 0, total = 0;
                for (RackDevice device : ControllerStructures.rackDevicesServing(server, network)) {
                    if (device instanceof TapeLibraryDevice library && library.isOnline()) {
                        long[] cold = library.coldBytes();
                        used += cold[0];
                        total += cold[1];
                    }
                }
                yield new DisplayFrame(name, "COLD STORAGE", percent(used, total), List.of(used, total), List.of(), List.of());
            }
            case "*ENERGY" -> new DisplayFrame(name, "ENERGY", compact(Math.round(snapshot.usage())) + " FE/T",
                    List.of(snapshot.stored(), snapshot.capacity()), List.of(), List.of());
            case "*LANES" -> new DisplayFrame(name, "LANES", snapshot.lanesUsed() + "/" + snapshot.laneCapacity(),
                    List.of((long) snapshot.lanesUsed(), (long) snapshot.laneCapacity()), List.of(), List.of());
            case "*JOBS" -> jobs(server, network, name);
            case "*ITEM" -> {
                Long count = count(server, network, widget.item());
                yield new DisplayFrame(name, widget.item(), count == null ? "?" : compact(count), List.of(count == null ? 0 : count), List.of(), List.of());
            }
            case "*DEVICES" -> devices(server, network, name, widget.devType());
            case "*UPS" -> ups(server, network, name);
            case "*GRAPH" -> graph(server, network, name, widget, history);
            case "*TABLE" -> table(server, network, name, widget);
            default -> new DisplayFrame(name, "", "", List.of(), List.of(), List.of());
        };
    }

    private static long[] hot(MinecraftServer server, NetworkRef network) {
        NetworkStorage storage = ControllerStructures.sharedStorageOf(server, network, false);
        if (storage == null) {
            return new long[2];
        }
        long[] hot = storage.hotBytes();
        return new long[] { hot[0] + StoredLibraryService.storageBytes(new ElclSystem(server, network)), hot[1] };
    }

    private static @Nullable Long count(MinecraftServer server, NetworkRef network, String spec) {
        NetworkStorage storage = ControllerStructures.sharedStorageOf(server, network, false);
        if (storage == null || spec.isBlank()) {
            return null;
        }
        try {
            Item item = ElclItems.resolve(spec);
            return ElclItems.count(storage, item, "*ALL");
        } catch (ElclException e) {
            return null;
        }
    }

    // Rows: "dot|text|value", dot one of on, off, warn, fault.
    private static DisplayFrame jobs(MinecraftServer server, NetworkRef network, String name) {
        List<String> rows = new ArrayList<>();
        for (JobHost host : ControllerStructures.schedulersOf(server, network)) {
            for (CraftingJob job : host.jobs()) {
                int done = 0, total = 0;
                for (CraftingJob.Step step : job.steps) {
                    done += step.done;
                    total += step.total;
                }
                String id = String.format(Locale.ROOT, "C%04d", ControllerStructures.jobNumber(server, network, job.id));
                String item = ElclItems.text(job.target);
                rows.add((job.running ? (job.awaiting.isEmpty() ? "on" : "warn") : "off") + "|" + id + " " + item + "|"
                        + (total == 0 ? 100 : done * 100 / total) + "%");
            }
        }
        return new DisplayFrame(name, "CRAFTING JOBS", Integer.toString(rows.size()), List.of((long) rows.size()), rows, List.of());
    }

    private static DisplayFrame devices(MinecraftServer server, NetworkRef network, String name, String type) {
        List<String> rows = new ArrayList<>();
        for (ElclDevices.Device device : ElclDevices.list(server, network)) {
            if (!type.isBlank() && !type.equalsIgnoreCase("*ALL") && !device.type().equalsIgnoreCase(type)) {
                continue;
            }
            String status = ModCommands.status(device);
            String dot = switch (status) {
                case "*ONLINE" -> "on";
                case "*FAULT" -> "fault";
                case "*DISABLED" -> "warn";
                default -> "off";
            };
            rows.add(dot + "|" + device.name() + "|" + status.substring(1));
        }
        return new DisplayFrame(name, "DEVICES", Integer.toString(rows.size()), List.of((long) rows.size()), rows, List.of());
    }

    private static DisplayFrame ups(MinecraftServer server, NetworkRef network, String name) {
        List<String> rows = new ArrayList<>();
        long sum = 0;
        boolean battery = false;
        int count = 0;
        for (RackDevice device : ControllerStructures.rackDevicesServing(server, network)) {
            if (device instanceof UpsDevice ups && ups.isOnline()) {
                count++;
                sum += ups.percent();
                battery |= ups.onBattery();
                rows.add((ups.onBattery() ? "warn" : "on") + "|" + device.deviceName() + "|" + (ups.onBattery() ? "BATT " : "") + ups.percent() + "%");
            }
        }
        long charge = count == 0 ? 0 : sum / count;
        return new DisplayFrame(name, "UPS", count == 0 ? "NONE" : battery ? "BATTERY" : "MAINS", List.of(charge, 100L), rows, List.of());
    }

    private static DisplayFrame graph(MinecraftServer server, NetworkRef network, String name, DisplayContent.Widget widget, DisplayHistory history) {
        float[] series = null;
        int stat = monitoringStat(widget.stat());
        if (stat >= 0 || machineStat(widget.stat())) {
            for (RackDevice device : ControllerStructures.rackDevicesServing(server, network)) {
                if (device instanceof MonitoringServerDevice monitoring && device.isOnline()) {
                    series = stat >= 0 ? monitoring.series(stat, monitoringRange(widget.range()))
                            : monitoring.machineSeries(historyKey(widget), monitoringRange(widget.range()));
                    break;
                }
            }
        }
        if (series == null) {
            series = history.last(historyKey(widget), historySamples(widget.range()));
        }
        List<Float> points = new ArrayList<>();
        // At most MAX_POINTS points: each the mean of its stretch.
        int n = series.length, buckets = Math.min(n, MAX_POINTS);
        for (int b = 0; b < buckets; b++) {
            int from = b * n / buckets, to = Math.max(from + 1, (b + 1) * n / buckets);
            float sum = 0;
            for (int i = from; i < to; i++) {
                sum += series[i];
            }
            points.add(sum / (to - from));
        }
        String label = switch (widget.stat()) {
            case "*ITEMFLOW" -> "ITEM FLOW /MIN";
            case "*ENERGY" -> "ENERGY DRAW FE/T";
            case "*LANES" -> "LANES USED";
            case "*CRAFTING" -> "CRAFTS /MIN";
            case "*STORAGE" -> "STORAGE %";
            case "*MCHOPS" -> widget.item().toUpperCase(Locale.ROOT) + " OPS /MIN";
            case "*MCHFE" -> widget.item().toUpperCase(Locale.ROOT) + " FE";
            default -> widget.item().toUpperCase(Locale.ROOT);
        };
        String value = points.isEmpty() ? "-" : compact(Math.round(points.getLast()));
        return new DisplayFrame(name, label, value, List.of(), List.of(), points);
    }

    // A table's rows (TABLE_ROWS at most): the headings, then the records the selection takes in its order, each a row of
    // tab-separated values; numbers: 1 for a numeric column (right-aligned), 0 else. The label is the file, the value how
    // many records it selected; a file gone or a selection that fails shows "!" and its message.
    static final int TABLE_ROWS = 64;
    private static final String TAB = "\t";

    private static DisplayFrame table(MinecraftServer server, NetworkRef network, String name, DisplayContent.Widget widget) {
        ElclSystem system = new ElclSystem(server, network);
        String file = widget.file();
        try {
            int slash = file.indexOf('/');
            String library = slash >= 0 ? file.substring(0, slash) : "*LIBL", member = file.substring(slash + 1);
            FileService.Who who = new FileService.Who(SystemData.SYSTEM_OWNER, true);
            RecordFormat format = ElclServices.files().format(system, library, member);
            List<DbRecord> records = Query.run(ElclServices.files().records(system, who, library, member), Query.selection(widget.select(), format, file),
                    Query.sort(widget.sort().isBlank() ? List.of() : List.of(widget.sort().split(" +")), format, file));
            List<String> rows = new ArrayList<>();
            List<String> heading = new ArrayList<>();
            List<Long> numeric = new ArrayList<>();
            for (FieldDef field : format.fields()) {
                heading.add(String.join(" ", field.heading()));
                numeric.add(field.numeric() ? 1L : 0L);
            }
            rows.add(String.join(TAB, heading));
            for (DbRecord record : records) {
                if (rows.size() >= TABLE_ROWS) {
                    break;
                }
                List<String> cells = new ArrayList<>();
                for (int i = 0; i < format.fields().size(); i++) {
                    cells.add(format.fields().get(i).text(record.value(i)));
                }
                rows.add(String.join(TAB, cells));
            }
            return new DisplayFrame(name, file, compact(records.size()), numeric.subList(0, Math.min(16, numeric.size())), rows, List.of());
        } catch (ElclException e) {
            return new DisplayFrame(name, file, "!", List.of(), List.of("!" + e.elclMessage().text()), List.of());
        }
    }

    // --- Formatting ---

    static String percent(long used, long total) {
        return total <= 0 ? "-" : (used * 100 / total) + "%";
    }

    // 1,840; 12.3K; 1.2M.
    static String compact(long value) {
        if (Math.abs(value) < 10_000) {
            return String.format(Locale.ROOT, "%,d", value);
        }
        if (Math.abs(value) < 1_000_000) {
            return String.format(Locale.ROOT, "%.1fK", value / 1_000.0);
        }
        return String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0);
    }
}
