/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.terminal;

import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.tags.TagKey;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.crafting.CraftingJob;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.ScreenQueries;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.StorageDevice;
import net.zagdrath.encodedlogistics.rack.device.TapeLibraryDevice;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// Answers the Terminal Desk's screen: command lines (TerminalCommands), completions, and the screens' queries -
//  info                 the network's name, whether it has a Firewall, the player's name
//  jobs / job <n>       Work with Jobs' rows / one job's details
//  devices / device <i> Work with Devices' rows / one device's details
//  locate <i>           where a device is (the screen highlights it)
//  status               Display Network Status' body
//  detail <item>        an item's details (Work with Inventory, option 5)
//  plan <item> <n>      a craft's plan in a line (the CRAFT prompt)
// Rows come as cells padded to their columns, so the client lines them up in its own language. SCREEN requests are the
// Terminal OS screens' (ScreenQueries).
public final class TerminalService {
    public static final int COMMAND = 0, QUERY = 1, COMPLETE = 2, SCREEN = 3;

    private TerminalService() {}

    // What the response is about: the query's (or command's) first word, or "complete".
    public static String topic(int kind, String text) {
        if (kind == COMPLETE) {
            return "complete";
        }
        List<String> words = TerminalCommands.words(text);
        return words.isEmpty() ? "" : words.getFirst().toLowerCase(Locale.ROOT);
    }

    public static TerminalOutput handle(TerminalContext context, int kind, String text) {
        if (kind == COMMAND) {
            return TerminalCommands.execute(context, text);
        }
        if (kind == COMPLETE) {
            TerminalOutput out = new TerminalOutput();
            TerminalCommands.complete(context, text).forEach(out::line);
            return out;
        }
        if (kind == SCREEN) {
            return context.allowed(RackPermission.VIEW) ? ScreenQueries.handle(context, text) : TerminalOutput.message(TerminalActions.notAuthorised(RackPermission.VIEW));
        }
        List<String> words = TerminalCommands.words(text);
        if (words.isEmpty()) {
            return new TerminalOutput();
        }
        if (!words.getFirst().equals("info") && !context.allowed(RackPermission.VIEW)) {
            return TerminalOutput.message(TerminalActions.notAuthorised(RackPermission.VIEW));
        }
        return switch (words.getFirst()) {
            case "info" -> info(context);
            case "jobs" -> jobs(context, true);
            case "job" -> job(context, words.size() > 1 ? (int) TerminalItems.amount(words.get(1)) : -1);
            case "devices" -> devices(context, true);
            case "device" -> device(context, words.size() > 1 ? (int) TerminalItems.amount(words.get(1)) : -1);
            case "locate" -> locate(context, words.size() > 1 ? (int) TerminalItems.amount(words.get(1)) : -1);
            case "status" -> status(context);
            case "detail" -> detail(context, words.size() > 1 ? words.get(1) : "");
            case "plan" -> plan(context, words.size() > 1 ? words.get(1) : "", words.size() > 2 ? TerminalItems.amount(words.get(2)) : 1);
            default -> new TerminalOutput();
        };
    }

    // --- Info ---

    // "ELNET01": the network's name on the screen.
    public static String networkName(NetworkRef network) {
        return String.format(Locale.ROOT, "ELNET%02d", network.id());
    }

    private static TerminalOutput info(TerminalContext context) {
        TerminalOutput out = new TerminalOutput();
        out.line(context.network() != null ? networkName(context.network()) : "*OFFLINE");
        out.line(ControllerStructures.firewall(context.server(), context.network()) != null ? "1" : "0");
        out.line(context.player().getName().getString());
        // The system's PHOSPHOR, every terminal's default colour.
        out.line(context.network() != null ? ElclServices.sysvals().get(new ElclSystem(context.server(), context.network()), "PHOSPHOR") : "*GREEN");
        return out;
    }

    // --- Inventory ---

    // Whether an item matches a filter: its name or id contains it, or (#tag) it's in that tag. Empty matches all.
    public static boolean matchesFilter(ItemKey key, String filter) {
        if (filter.isEmpty() || filter.equals("*all")) {
            return true;
        }
        if (filter.startsWith("#")) {
            String tag = filter.substring(1);
            return key.stack().typeHolder().tags().anyMatch((TagKey<Item> t) -> t.location().toString().contains(tag));
        }
        return key.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(filter) || TerminalItems.id(key).contains(filter);
    }

    // Where an item is: Hot, Cold (on tape) or Hot+Cold.
    public static Component location(long hot, long cold) {
        String key = cold <= 0 ? "hot" : hot <= 0 ? "cold" : "split";
        return Component.translatable("crt.encodedlogistics.loc." + key);
    }

    private static TerminalOutput detail(TerminalContext context, String spec) {
        NetworkStorage storage = context.storage();
        ItemKey key = TerminalItems.resolve(context, spec);
        if (storage == null || key == null) {
            return TerminalOutput.message(storage == null ? TerminalActions.offline() : Component.translatable("crt.encodedlogistics.msg.no_item", spec));
        }
        long hot = storage.count(key), cold = storage.cold().count(key);
        TerminalOutput out = new TerminalOutput();
        out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.detail.item"), 28).text(TerminalItems.id(key)).build());
        out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.detail.name"), 28).text(key.stack().getHoverName())
                .attr(TerminalLine.BRIGHT).build());
        out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.detail.hot"), 28).text(TerminalItems.count(hot)).build());
        out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.detail.cold"), 28).text(TerminalItems.count(cold))
                .attr(cold > 0 ? TerminalLine.BRIGHT : TerminalLine.NORMAL).build());
        if (cold > 0) {
            int eta = storage.cold().eta(key);
            out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.detail.recall"), 28)
                    .text(eta < 0 ? Component.translatable("tooltip.encodedlogistics.tape.no_drive") : Component.literal("~" + TerminalItems.seconds(eta, true)))
                    .build());
        }
        boolean craftable = CraftRequests.craftables(context.server(), context.network()).contains(key);
        out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.detail.craftable"), 28)
                .text(Component.translatable(craftable ? "crt.encodedlogistics.yes" : "crt.encodedlogistics.no")).build());
        List<String> tags = key.stack().typeHolder().tags().map(tag -> "#" + tag.location()).sorted().limit(8).toList();
        out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.detail.tags"), 28).text(tags.isEmpty() ? "-" : tags.getFirst())
                .build());
        for (String tag : tags.subList(Math.min(1, tags.size()), tags.size())) {
            out.line(TerminalLine.builder().left("", 28).text(tag).build());
        }
        return out;
    }

    private static TerminalOutput plan(TerminalContext context, String spec, long amount) {
        ItemKey key = TerminalItems.resolve(context, spec);
        if (key == null) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.no_item", spec));
        }
        return new TerminalOutput().line(TerminalActions.planSummary(context, key, Math.max(1, amount)), TerminalLine.DIM);
    }

    // --- Jobs ---

    // Work with Jobs' rows (cells: job, item, qty, status, progress bar and %, scheduler); for the command line with a
    // header first.
    public static TerminalOutput jobs(TerminalContext context, boolean screen) {
        TerminalOutput out = new TerminalOutput();
        if (!screen) {
            out.line(TerminalLine.builder().text("    ").left("JOB", 6).left("ITEM", 26).right("QTY", 5).text("  ").left("STATUS", 9).text(" ")
                    .left("PROGRESS", 14).text("  SCHEDULER").attr(TerminalLine.BRIGHT).build());
        }
        for (TerminalActions.JobRow row : TerminalActions.jobs(context)) {
            String bar = "#".repeat(row.percent() / 10);
            out.line(TerminalLine.builder().text(screen ? "" : "    ").left(row.number(), 6).left(row.item().stack().getHoverName(), 26)
                    .right(TerminalItems.count(row.amount()), 5).text("  ").left(row.status(), 9).text(" ").left(bar, 10).right(row.percent() + "%", 4)
                    .text("  ").text(row.scheduler()).attr(row.status().equals("Active") ? TerminalLine.BRIGHT : TerminalLine.NORMAL).build());
        }
        if (!screen && out.lines().size() == 1) {
            out.setMessage(Component.translatable("crt.encodedlogistics.msg.no_jobs"));
        }
        return out;
    }

    private static TerminalOutput job(TerminalContext context, int number) {
        for (TerminalActions.JobRow row : TerminalActions.jobs(context)) {
            if (Integer.parseInt(row.number()) == number) {
                CraftingJob job = row.job();
                TerminalOutput out = new TerminalOutput();
                out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.job.job"), 28).text(row.number()).build());
                out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.job.item"), 28).text(TerminalItems.count(job.amount) + " x ")
                        .text(job.target.stack().getHoverName()).attr(TerminalLine.BRIGHT).build());
                out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.job.status"), 28).text(row.status()).build());
                out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.job.progress"), 28)
                        .text(job.done() + " / " + job.total() + " (" + row.percent() + "%)").build());
                out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.job.scheduler"), 28).text(row.scheduler()).build());
                long awaiting = job.awaiting.values().stream().mapToLong(Long::longValue).sum();
                if (awaiting > 0) {
                    out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.job.awaiting"), 28).text(TerminalItems.count(awaiting))
                            .attr(TerminalLine.BRIGHT).build());
                }
                out.line(TerminalLine.blank());
                out.line(TerminalLine.builder().left("  STEP", 30).right("DONE", 8).right("TOTAL", 8).attr(TerminalLine.BRIGHT).build());
                for (CraftingJob.Step step : job.steps) {
                    out.line(TerminalLine.builder().text("  ").left(step.schematic.output().getHoverName(), 28).right(Math.min(step.done, step.total), 8)
                            .right(step.total, 8).build());
                }
                return out;
            }
        }
        return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.no_job", String.format("%04d", Math.max(0, number))));
    }

    // --- Devices ---

    // Work with Devices' rows (cells: type, device, location, lanes, status); for the command line with a header first.
    public static TerminalOutput devices(TerminalContext context, boolean screen) {
        TerminalOutput out = new TerminalOutput();
        if (!screen) {
            out.line(TerminalLine.builder().text("    ").left("TYPE", 12).left("DEVICE", 24).left("LOCATION", 19).left("LANES", 9).text("STATUS")
                    .attr(TerminalLine.BRIGHT).build());
        }
        for (ControllerStructures.DeviceRow row : ControllerStructures.deviceRows(context.server(), context.network())) {
            RackDevice device = row.rackDevice();
            String type = device != null ? "  U" + device.u() + (device.size() > 1 ? "-U" + device.top() : "") : row.type();
            BlockPos pos = row.pos().pos();
            String location = device != null ? "R " + pos.getX() + "," + pos.getZ() + " U" + device.u() : pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
            String lanes = Integer.toString(row.lanes());
            if (device != null && device.lanePool() != null && device.rack() != null) {
                lanes = device.rack().lanes().poolUsed() + " / " + device.rack().lanes().poolCapacity();
            }
            Component status;
            int attr;
            if (device != null) {
                RackDeviceInfo.Status state = device.status();
                status = device.statusText();
                attr = state == RackDeviceInfo.Status.OFFLINE ? TerminalLine.DIM
                        : state == RackDeviceInfo.Status.FAULT || !status.getString().equals(state.text().getString()) ? TerminalLine.BRIGHT : TerminalLine.NORMAL;
            } else if (row.laneMissing()) {
                status = Component.translatable("crt.encodedlogistics.dev.no_lane");
                attr = TerminalLine.BRIGHT;
            } else {
                status = Component.translatable(row.online() ? "gui.encodedlogistics.status.online" : "gui.encodedlogistics.status.offline");
                attr = row.online() ? TerminalLine.NORMAL : TerminalLine.DIM;
            }
            out.line(TerminalLine.builder().text(screen ? "" : "    ").left(type, 10).text("  ").left(row.name(), 22).text("  ").left(location, 17).text("  ")
                    .left(lanes, 7).text("  ").text(status).attr(attr).build());
        }
        return out;
    }

    private static TerminalOutput device(TerminalContext context, int index) {
        List<ControllerStructures.DeviceRow> rows = ControllerStructures.deviceRows(context.server(), context.network());
        if (index < 0 || index >= rows.size()) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.no_device"));
        }
        ControllerStructures.DeviceRow row = rows.get(index);
        TerminalOutput out = new TerminalOutput();
        BlockPos pos = row.pos().pos();
        out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.dev.device"), 28).text(row.name()).attr(TerminalLine.BRIGHT).build());
        out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.dev.location"), 28)
                .text(pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "  " + row.pos().dimension().identifier()).build());
        if (row.rackDevice() != null && context.player() instanceof ServerPlayer viewer) {
            RackDevice device = row.rackDevice();
            out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.dev.units"), 28)
                    .text("U" + device.u() + (device.size() > 1 ? "-U" + device.top() : "")).build());
            RackDeviceInfo info = device.describe(viewer);
            out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.dev.status"), 28).text(info.statusText()).build());
            for (RackDeviceInfo.InfoLine line : info.lines()) {
                TerminalLine.Builder builder = TerminalLine.builder().left(line.label(), 28).text(line.value());
                line.bar().ifPresent(bar -> builder.text("  " + gauge(bar.fraction(), 20)));
                out.line(builder.build());
            }
        } else {
            out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.dev.lanes"), 28).text(Integer.toString(row.lanes())).build());
            out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.dev.status"), 28)
                    .text(Component.translatable(row.laneMissing() ? "crt.encodedlogistics.dev.no_lane"
                            : row.online() ? "gui.encodedlogistics.status.online" : "gui.encodedlogistics.status.offline"))
                    .build());
        }
        return out;
    }

    private static TerminalOutput locate(TerminalContext context, int index) {
        List<ControllerStructures.DeviceRow> rows = ControllerStructures.deviceRows(context.server(), context.network());
        if (index < 0 || index >= rows.size()) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.no_device"));
        }
        ControllerStructures.DeviceRow row = rows.get(index);
        BlockPos pos = row.pos().pos();
        TerminalOutput out = new TerminalOutput();
        out.line(pos.getX() + " " + pos.getY() + " " + pos.getZ() + " " + row.pos().dimension().identifier());
        out.setMessage(Component.translatable("crt.encodedlogistics.msg.located", row.name(), pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
        return out;
    }

    // --- Network status ---

    // "[#########.....]" n wide inside the brackets.
    public static String gauge(double fraction, int width) {
        int filled = (int) Math.round(Math.clamp(fraction, 0, 1) * width);
        return "[" + "#".repeat(filled) + ".".repeat(width - filled) + "]";
    }

    private static TerminalLine head(String key) {
        return TerminalLine.of(Component.translatable("crt.encodedlogistics.status." + key), TerminalLine.BRIGHT);
    }

    private static TerminalLine field(String key, String value) {
        return TerminalLine.builder().text("  ").left(Component.translatable("crt.encodedlogistics.status." + key), 23).text(":   ").text(value).build();
    }

    private static TerminalOutput status(TerminalContext context) {
        NetworkSnapshot snapshot = ControllerStructures.snapshotOf(context.server(), context.network());
        NetworkStorage storage = context.storage();
        long[] hot = storage != null ? storage.hotBytes() : new long[2];
        long coldUsed = 0, coldTotal = 0;
        for (RackDevice device : ControllerStructures.rackDevicesServing(context.server(), context.network())) {
            if (device instanceof TapeLibraryDevice library && library.isOnline()) {
                long[] bytes = library.coldBytes();
                coldUsed += bytes[0];
                coldTotal += bytes[1];
            }
        }
        List<TerminalActions.JobRow> jobs = TerminalActions.jobs(context);
        long active = jobs.stream().filter(row -> row.job().running).count();
        int schedulers = CraftRequests.schedulers(context.server(), context.network()).size();
        int online = 0, offline = 0, fault = 0;
        for (ControllerStructures.DeviceRow row : ControllerStructures.deviceRows(context.server(), context.network())) {
            if (row.laneMissing() || row.rackDevice() != null && row.rackDevice().status() == RackDeviceInfo.Status.FAULT) {
                fault++;
            } else if (row.online()) {
                online++;
            } else {
                offline++;
            }
        }
        TerminalOutput out = new TerminalOutput();
        out.line(head("storage"));
        out.line(field("hot", gauge(fraction(hot[0], hot[1]), 30) + "  " + StorageDevice.bytes(hot[0]) + " / " + StorageDevice.bytes(hot[1]) + "   "
                + percent(hot[0], hot[1])));
        out.line(field("cold", gauge(fraction(coldUsed, coldTotal), 30) + "  " + StorageDevice.bytes(coldUsed) + " / " + StorageDevice.bytes(coldTotal)
                + "   " + percent(coldUsed, coldTotal)));
        out.line(TerminalLine.blank());
        out.line(head("energy"));
        out.line(field("stored", gauge(fraction(snapshot.stored(), snapshot.capacity()), 30) + "  " + TerminalItems.count(snapshot.stored()) + " / "
                + TerminalItems.count(snapshot.capacity()) + " FE"));
        out.line(field("in_out", String.format(Locale.ROOT, "%+,.1f / -%,.1f FE/t", snapshot.generation(), snapshot.usage())));
        out.line(TerminalLine.blank());
        out.line(head("lanes"));
        out.line(field("used", gauge(fraction(snapshot.lanesUsed(), snapshot.laneCapacity()), 30) + "  " + snapshot.lanesUsed() + " / "
                + snapshot.laneCapacity()));
        out.line(TerminalLine.blank());
        out.line(head("crafting"));
        out.line(field("active", String.format(Locale.ROOT, "%-6d Queued  . . :   %-6d Schedulers  . . :   %d", active, jobs.size() - active, schedulers)));
        out.line(TerminalLine.blank());
        out.line(head("devices"));
        out.line(field("online", String.format(Locale.ROOT, "%-6d Offline . . :   %-6d Fault . . . . . :   %d", online, offline, fault)));
        return out;
    }

    private static double fraction(long part, long whole) {
        return whole <= 0 ? 0 : (double) part / whole;
    }

    private static String percent(long part, long whole) {
        return (whole <= 0 ? 0 : Math.round(100.0 * part / whole)) + "%";
    }
}
