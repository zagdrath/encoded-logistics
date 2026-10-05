/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.terminal;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.encodedlogistics.block.ServerRackBlock;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftLog;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.crafting.CraftingJob;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.exec.ElclItems;
import net.zagdrath.encodedlogistics.elcl.exec.MachineCommands;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.ScreenQueries;
import net.zagdrath.encodedlogistics.elcl.store.StoredLibraryService;
import net.zagdrath.encodedlogistics.machine.MachineBridge;
import net.zagdrath.encodedlogistics.machine.MachineInfo;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.StorageDevice;
import net.zagdrath.encodedlogistics.rack.device.TapeLibraryDevice;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.wireless.Wireless;
import net.zagdrath.encodedlogistics.wireless.WirelessDevice;

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
            return context.allowed(RackPermission.VIEW) ? ScreenQueries.handle(context, text)
                    : TerminalOutput.message(Component.literal(ElclMessage.of("ELC0401", context.user(), RackPermission.VIEW.name()).toString()));
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
            case "jobhistory" -> jobHistory(context);
            case "withdraw" -> withdraw(context, words.subList(1, words.size()));
            case "deposit" -> TerminalActions.deposit(context, words.subList(1, words.size()));
            case "craft" -> craft(context, words.subList(1, words.size()));
            case "canceljob" -> TerminalActions.cancel(context, words.size() > 1 ? (int) TerminalItems.amount(words.get(1)) : -1);
            case "jobrecord" -> jobRecord(context, words.size() > 1 ? (int) TerminalItems.amount(words.get(1)) : -1);
            case "removejobrecord" -> removeJobRecord(context, words.size() > 1 ? (int) TerminalItems.amount(words.get(1)) : -1);
            case "job" -> job(context, words.size() > 1 ? (int) TerminalItems.amount(words.get(1)) : -1);
            case "devices" -> devices(context, true);
            case "device" -> device(context, words.size() > 1 ? (int) TerminalItems.amount(words.get(1)) : -1);
            case "machine" -> machine(context, words.size() > 1 ? words.get(1) : "");
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
        // The system's PHOSPHOR (every terminal's default colour) and SECLVL (10: no sign-on).
        ElclSystem system = context.network() != null ? new ElclSystem(context.server(), context.network()) : null;
        out.line(system != null ? ElclServices.sysvals().get(system, "PHOSPHOR") : "*GREEN");
        out.line(system != null ? ElclServices.sysvals().get(system, "SECLVL") : "30");
        return out;
    }

    // --- Inventory ---

    // Whether an item matches a filter: its name or id contains it, or (#tag) it's in that tag. Empty matches all.
    public static boolean matchesFilter(StorageKey key, String filter) {
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
        StorageKey key = TerminalItems.resolve(context, spec);
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
        StorageKey key = TerminalItems.resolve(context, spec);
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
                    .left("PROGRESS", 13).text(" SCHEDULER").attr(TerminalLine.BRIGHT).build());
        }
        for (TerminalActions.JobRow row : TerminalActions.jobs(context)) {
            // The bar: eight cells, "#" done and "." to do; on the screen the scheduler by number (its name won't fit).
            int filled = Math.clamp(row.percent() * 8 / 100, 0, 8);
            String bar = "#".repeat(filled) + ".".repeat(8 - filled);
            // On the screen the job number is the first cell (Work with Jobs' options name the job by it).
            TerminalLine.Builder line = screen ? TerminalLine.builder() : TerminalLine.builder().text("    ");
            out.line(line.left(row.number(), 6).left(row.item().stack().getHoverName(), 26)
                    .right(TerminalItems.count(row.amount()), 5).text("  ").left(row.status(), 9).text(" ").text(bar).right(row.percent() + "%", 5)
                    .text(" ").text(screen ? row.schedulerShort() : row.scheduler())
                    .attr(row.status().equals("Active") ? TerminalLine.BRIGHT : TerminalLine.NORMAL).build());
        }
        if (!screen && out.lines().size() == 1) {
            out.setMessage(Component.translatable("crt.encodedlogistics.msg.no_jobs"));
        }
        return out;
    }

    // --- The desk's screens' requests ---

    // Withdraw Item's Enter: "withdraw <item> <amount> *drawer|*inv".
    private static TerminalOutput withdraw(TerminalContext context, List<String> args) {
        if (!context.allowed(RackPermission.EXTRACT)) {
            return TerminalOutput.message(TerminalActions.notAuthorised(RackPermission.EXTRACT));
        }
        StorageKey key = args.isEmpty() ? null : TerminalItems.resolve(context, args.get(0));
        if (key == null) {
            return noItem(context, args.isEmpty() ? "" : args.get(0));
        }
        long amount = args.size() > 1 ? TerminalItems.amount(args.get(1)) : -1;
        TerminalActions.Destination destination = args.size() > 2 ? TerminalActions.Destination.parse(args.get(2)) : TerminalActions.Destination.DRAWER;
        if (amount <= 0 || destination == null || destination == TerminalActions.Destination.NETWORK) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.invalid_value", String.join(" ", args)));
        }
        return TerminalActions.withdraw(context, key, amount, destination);
    }

    // Craft Item's Enter: "craft <item> <amount> <scheduler> *network|*drawer|*inv".
    private static TerminalOutput craft(TerminalContext context, List<String> args) {
        if (!context.allowed(RackPermission.CRAFT)) {
            return TerminalOutput.message(TerminalActions.notAuthorised(RackPermission.CRAFT));
        }
        StorageKey key = args.isEmpty() ? null : TerminalItems.resolve(context, args.get(0));
        if (key == null) {
            return noItem(context, args.isEmpty() ? "" : args.get(0));
        }
        long amount = args.size() > 1 ? TerminalItems.amount(args.get(1)) : -1;
        String scheduler = args.size() > 2 ? args.get(2) : "*AUTO";
        TerminalActions.Destination destination = args.size() > 3 ? TerminalActions.Destination.parse(args.get(3)) : TerminalActions.Destination.NETWORK;
        if (amount <= 0 || destination == null) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.invalid_value", String.join(" ", args)));
        }
        return TerminalActions.craft(context, key, amount, scheduler, destination);
    }

    // No such item, or several.
    private static TerminalOutput noItem(TerminalContext context, String spec) {
        return TerminalOutput.message(Component.translatable(TerminalItems.matches(context, spec).isEmpty() ? "crt.encodedlogistics.msg.no_item"
                : "crt.encodedlogistics.msg.ambiguous", spec));
    }

    // Work with Jobs' history view (CraftLog), newest first, a row of cells each: number, item id, item name, quantity
    // asked for, quantity made, status, ended (as shown, and as clock ticks to sort by), duration, requested by.
    private static TerminalOutput jobHistory(TerminalContext context) {
        TerminalOutput out = new TerminalOutput();
        if (context.network() == null) {
            return out;
        }
        ElclSystem system = new ElclSystem(context.server(), context.network());
        for (CraftLog.Entry entry : CraftLog.entries(context.server(), context.network())) {
            TerminalLine.Builder row = TerminalLine.builder();
            for (Object cell : new Object[] { String.format(Locale.ROOT, "%04d", entry.number()), registryId(entry.item()), itemName(entry.item()),
                    entry.requested(), entry.produced(), entry.status().label(), system.at(entry.ended()), entry.ended(), CraftLog.duration(entry.duration()),
                    entry.requestedBy() }) {
                // As given: a translatable name stays one (the client shows it in its own language).
                row.left(cell, 0);
            }
            out.line(row.build());
        }
        return out;
    }

    // An ended job's record in full (5=Display in the history view): what DSPJOB shows, the ingredients it used up.
    private static TerminalOutput jobRecord(TerminalContext context, int number) {
        CraftLog.Entry entry = CraftLog.byNumber(context.server(), context.network(), number);
        String shown = String.format(Locale.ROOT, "%04d", Math.max(0, number));
        if (entry == null || context.network() == null) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.no_job", shown));
        }
        ElclSystem system = new ElclSystem(context.server(), context.network());
        TerminalOutput out = new TerminalOutput();
        out.line(field("crt.encodedlogistics.job.job").text(shown).build());
        out.line(field("crt.encodedlogistics.job.item").text(entry.requested() + " x ").text(itemName(entry.item())).attr(TerminalLine.BRIGHT).build());
        out.line(field("crt.encodedlogistics.history.produced").text(Long.toString(entry.produced())).build());
        out.line(field("crt.encodedlogistics.job.status").text(entry.status().label()).attr(entry.status() == CraftLog.Status.FAILED
                ? TerminalLine.BRIGHT : TerminalLine.NORMAL).build());
        if (!entry.reason().isEmpty()) {
            out.line(field("crt.encodedlogistics.history.reason").text(Component.translatable("gui.encodedlogistics.job.reason." + entry.reason())).build());
        }
        out.line(field("crt.encodedlogistics.history.requested_by").text(entry.requestedByFull()).build());
        if (!entry.origin().isEmpty()) {
            out.line(field("crt.encodedlogistics.history.user").text(entry.user()).build());
        }
        out.line(field("crt.encodedlogistics.job.scheduler").text(entry.scheduler()).build());
        out.line(field("crt.encodedlogistics.history.started").text(entry.started() < 0 ? "-" : system.at(entry.started())).build());
        out.line(field("crt.encodedlogistics.history.ended").text(system.at(entry.ended())).build());
        out.line(field("crt.encodedlogistics.history.duration").text(CraftLog.duration(entry.duration())).build());
        out.line(TerminalLine.blank());
        out.line(TerminalLine.builder().left("  " + Component.translatable("crt.encodedlogistics.history.consumed").getString(), 46).right("QTY", 10)
                .attr(TerminalLine.BRIGHT).build());
        if (entry.consumed().isEmpty()) {
            out.line(TerminalLine.builder().text("  ").text(Component.translatable("crt.encodedlogistics.history.none")).build());
        }
        entry.consumed().forEach((item, count) -> out.line(TerminalLine.builder().text("  ").left(itemName(item), 44).right(count, 10).build()));
        return out;
    }

    private static TerminalLine.Builder field(String key) {
        return TerminalLine.builder().left(Component.translatable(key), 28);
    }

    // 4=Remove in the history view (confirmed on the screen): as cancelling a job, it takes CRAFT.
    private static TerminalOutput removeJobRecord(TerminalContext context, int number) {
        if (!context.allowed(RackPermission.CRAFT)) {
            return TerminalOutput.message(TerminalActions.notAuthorised(RackPermission.CRAFT));
        }
        String shown = String.format(Locale.ROOT, "%04d", Math.max(0, number));
        if (context.network() == null || !CraftLog.remove(context.server(), context.network(), number)) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.no_job", shown));
        }
        return TerminalOutput.message(Component.translatable("crt.encodedlogistics.history.removed", shown));
    }

    // A history item (ElclItems' id: COAL_BLOCK, or mod:item) as the registry names it, and its name; the id itself
    // when the item's gone.
    private static String registryId(String item) {
        if (item.indexOf(' ') > 0) {
            // A fluid or gas: "FLUID minecraft:water".
            return item.substring(item.indexOf(' ') + 1);
        }
        try {
            return BuiltInRegistries.ITEM.getKey(ElclItems.resolve(item)).toString();
        } catch (ElclException e) {
            return item;
        }
    }

    private static Component itemName(String item) {
        if (item.indexOf(' ') > 0) {
            return Component.literal(ElclItems.displayName(item));
        }
        try {
            return new ItemStack(ElclItems.resolve(item)).getHoverName();
        } catch (ElclException e) {
            return Component.literal(item);
        }
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

    // Work with Devices' rows (cells: type, device, location, lanes, status), the network's topology: each rack, the
    // devices in it under it on tree lines (top unit first), then the rest of the network beside the racks. For the
    // command line with a header first.
    public static TerminalOutput devices(TerminalContext context, boolean screen) {
        TerminalOutput out = new TerminalOutput();
        if (!screen) {
            out.line(TerminalLine.builder().text("    ").left("TYPE", 12).left("DEVICE", 24).left("LOCATION", 19).left("LANES", 9).text("STATUS")
                    .attr(TerminalLine.BRIGHT).build());
        }
        List<ControllerStructures.DeviceRow> all = ControllerStructures.deviceRows(context.server(), context.network());
        // The names scripts use (UPS01), shown before the device's kind; display order doesn't affect them.
        List<ElclDevices.Device> named = ElclDevices.list(context.server(), context.network());
        Map<NodePos, Integer> partRows = new HashMap<>();
        for (int i = 0; i < all.size(); i++) {
            ControllerStructures.DeviceRow row = all.get(i);
            RackDevice device = row.rackDevice();
            // A rack's last device closes its branch.
            boolean last = i + 1 >= all.size() || all.get(i + 1).depth() == 0;
            String type = device != null ? (last ? " └─U" : " ├─U") + device.u() + (device.size() > 1 ? "-" + device.top() : "") : row.type();
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
            } else if (Wireless.deviceAt(context.server(), row.pos()) instanceof WirelessDevice wireless) {
                // Its status and the controller it works through ("Online WLC01").
                RackDeviceInfo info = wireless.describe(context.server());
                String controller = wireless.controllerName(context.server());
                status = controller.isEmpty() ? info.statusText() : info.statusText().copy().append(" " + controller);
                attr = info.status() == RackDeviceInfo.Status.OFFLINE ? TerminalLine.DIM
                        : info.status() == RackDeviceInfo.Status.ONLINE ? TerminalLine.NORMAL : TerminalLine.BRIGHT;
            } else if (row.laneMissing()) {
                status = Component.translatable("crt.encodedlogistics.dev.no_lane");
                attr = TerminalLine.BRIGHT;
            } else {
                status = Component.translatable(row.online() ? "gui.encodedlogistics.status.online" : "gui.encodedlogistics.status.offline");
                attr = row.online() ? TerminalLine.NORMAL : TerminalLine.DIM;
            }
            // Cell 3 is the device's name (empty when it has none): Work with Devices' 2=Change reads it. A block's parts
            // come in side order, so its nth Part row is its nth part.
            Direction side = null;
            if (row.type().equals("Part") && ControllerStructures.blockEntity(context.server(), row.pos()) instanceof CableBlockEntity cable) {
                int nth = partRows.merge(row.pos(), 1, Integer::sum) - 1;
                for (Direction candidate : Direction.values()) {
                    if (cable.part(candidate) != null && nth-- == 0) {
                        side = candidate;
                        break;
                    }
                }
            }
            String name = ElclDevices.nameAt(named, row.pos(), device, side);
            out.line(TerminalLine.builder().text(screen ? "" : "    ").left(type, 10).text("  ").left(name, name.isEmpty() ? 0 : 11)
                    .left(row.name(), name.isEmpty() ? 22 : 11).text("  ").left(location, 17).text("  ").left(lanes, 7).text("  ").text(status).attr(attr)
                    .build());
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
        String name = ElclDevices.nameAt(ElclDevices.list(context.server(), context.network()), row.pos(), row.rackDevice());
        if (!name.isEmpty()) {
            out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.dev.name"), 28).text(name).build());
        }
        out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.dev.location"), 28)
                .text(pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "  " + row.pos().dimension().identifier()).build());
        if (row.rackDevice() == null && Wireless.deviceAt(context.server(), row.pos()) instanceof WirelessDevice wireless) {
            RackDeviceInfo info = wireless.describe(context.server());
            out.line(TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.dev.status"), 28).text(info.statusText()).build());
            for (RackDeviceInfo.InfoLine line : info.lines()) {
                out.line(TerminalLine.builder().left(line.label(), 28).text(line.value()).build());
            }
        }
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

    // Display Machine (Work with Machines' 5): a bridged machine's status, operation, energy, heat and tanks, its
    // statistics and its settings.
    private static TerminalOutput machine(TerminalContext context, String name) {
        MachineCommands.Machine machine;
        try {
            machine = MachineCommands.find(context.server(), context.network(), name);
        } catch (ElclException e) {
            return TerminalOutput.message(Component.literal(e.elclMessage().toString()));
        }
        TerminalOutput out = new TerminalOutput();
        MachineBridge bridge = machine.bridge();
        MachineInfo info = machine.info();
        out.line(detail("machine", machine.name()).attr(TerminalLine.BRIGHT).build());
        out.line(detail("type", (info != null ? info.name() : bridge.shown()).getString() + "  " + bridge.type()).build());
        BlockPos pos = bridge.pos();
        out.line(detail("location", pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "  " + bridge.dimension().identifier() + "  "
                + bridge.face().getSerializedName()).build());
        out.line(detail("status", machine.status() + "  " + bridge.statusText(context.server(), info).getString()).build());
        String controller = bridge.controllerName(context.server());
        out.line(detail("controller", controller.isEmpty() ? "*NONE" : controller).build());
        if (info != null) {
            out.line(detail("progress", info.percent() < 0 ? "-" : info.percent() + "%" + info.ticksRemaining().stream()
                    .mapToObj(ticks -> "  (" + (ticks + 19) / 20 + "s left)").findFirst().orElse("")).build());
            out.line(detail("recipe", info.recipe().isEmpty() ? "-" : info.recipe()).build());
            info.energy().ifPresent(energy -> out.line(detail("energy", String.format(Locale.ROOT, "%,d / %,d FE  %s, %d FE/t", energy.stored(),
                    energy.capacity(), energy.role(), energy.perTick())).build()));
            info.heat().ifPresent(heat -> out.line(detail("heat", String.format(Locale.ROOT, "%d / %d C  %,d / %,d HU", heat.temperature(),
                    heat.maxTemperature(), heat.stored(), heat.capacity())).build()));
            for (MachineInfo.Tank tank : info.tanks()) {
                out.line(detail("tank", tank.role() + ": " + (tank.amount() <= 0 ? "-" : tank.fluid().getString() + " " + tank.amount()) + " / "
                        + tank.capacity() + " mB").build());
            }
            MachineInfo.Statistics stats = info.statistics();
            out.line(TerminalLine.blank());
            out.line(detail("operations", Long.toString(stats.operations())).build());
            out.line(detail("rate", String.format(Locale.ROOT, "%.2f / min", stats.operationsPerMinute())).build());
            out.line(detail("produced", stats.itemsProduced() + " items, " + stats.fluidProduced() + " mB").build());
            out.line(detail("consumed", stats.itemsConsumed() + " items, " + stats.fluidConsumed() + " mB").build());
            out.line(detail("uptime", String.format(Locale.ROOT, "%ds of %ds loaded", stats.uptimeTicks() / 20, stats.loadedTicks() / 20)).build());
            MachineInfo.Settings settings = info.settings();
            out.line(TerminalLine.blank());
            out.line(detail("enabled", settings.enabled() ? "*YES" : "*NO").build());
            out.line(detail("redstone", settings.redstoneMode().map(mode -> "*" + mode.toUpperCase(Locale.ROOT)).orElse("*NONE")).build());
            if (!settings.sides().isEmpty()) {
                StringBuilder sides = new StringBuilder();
                settings.sides().forEach((side, mode) -> sides.append(sides.isEmpty() ? "" : "  ").append(side).append('=').append(mode));
                out.line(detail("sides", sides.toString()).build());
            } else if (settings.ports() > 0) {
                out.line(detail("ports", Integer.toString(settings.ports())).build());
            }
            out.line(detail("eject", !settings.autoEjectSupported() ? "*NONE" : settings.autoEject() ? "*YES" : "*NO").build());
        }
        out.line(detail("power", bridge.powerFromNetwork() ? "*YES  " + bridge.powered() + " FE/t" : "*NO").build());
        String gateway = bridge.gatewayName(context.server());
        out.line(detail("gateway", gateway.isEmpty() ? "*NONE" : gateway).build());
        return out;
    }

    private static TerminalLine.Builder detail(String key, String value) {
        return TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.mch." + key), 28).text(value);
    }

    private static TerminalOutput locate(TerminalContext context, int index) {
        List<ControllerStructures.DeviceRow> rows = ControllerStructures.deviceRows(context.server(), context.network());
        if (index < 0 || index >= rows.size()) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.no_device"));
        }
        ControllerStructures.DeviceRow row = rows.get(index);
        BlockPos pos = row.pos().pos();
        TerminalOutput out = new TerminalOutput();
        AABB box = locateBox(context, row);
        out.line(String.format(Locale.ROOT, "%.4f %.4f %.4f %.4f %.4f %.4f %s", box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                row.pos().dimension().identifier()));
        out.setMessage(Component.translatable("crt.encodedlogistics.msg.located", row.name(), pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
        return out;
    }

    // What 8=Locate outlines, in world coordinates: a rack device's own units in its rack, otherwise the device's block
    // (its shape, or the whole block when it has none).
    private static AABB locateBox(TerminalContext context, ControllerStructures.DeviceRow row) {
        BlockPos pos = row.pos().pos();
        RackDevice device = row.rackDevice();
        if (device != null && device.rack() != null) {
            RackBlockEntity rack = device.rack();
            Direction facing = rack.getBlockState().getValue(ServerRackBlock.FACING);
            return RackGeometry.toWorld(RackGeometry.deviceBox(device.u(), device.size()), rack.getBlockPos(), facing);
        }
        BlockEntity entity = ControllerStructures.blockEntity(context.server(), row.pos());
        if (entity != null && entity.getLevel() != null) {
            VoxelShape shape = entity.getBlockState().getShape(entity.getLevel(), pos);
            if (!shape.isEmpty()) {
                return shape.bounds().move(pos);
            }
        }
        return new AABB(pos);
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
        // Source members take drive space too (OS.md 3).
        if (context.network() != null) {
            hot[0] += StoredLibraryService.storageBytes(new ElclSystem(context.server(), context.network()));
        }
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
