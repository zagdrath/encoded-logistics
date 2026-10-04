/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.terminal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.elcl.exec.ElclCommandLine;
import net.zagdrath.encodedlogistics.item.LtoTapeItem;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.StorageDevice;
import net.zagdrath.encodedlogistics.rack.device.TapeLibraryDevice;
import net.zagdrath.encodedlogistics.storage.DriveStats;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// The Terminal Desk's command line (HANDOFF 4): root commands by name, and "show" topics, both open to other devices'
// registration (a device adds its commands in its setup, the desk needs no change). Words are split on spaces, with
// "quoted strings" kept whole. An unknown command says so. Built in: help, show (inventory, drives, lanes, jobs,
// devices, power), withdraw, craft, cancel job, clear.
public final class TerminalCommands {
    private static final Map<String, TerminalCommand> COMMANDS = new LinkedHashMap<>();
    private static final Map<String, ShowTopic> TOPICS = new LinkedHashMap<>();

    private TerminalCommands() {}

    public static void register(TerminalCommand command) {
        if (COMMANDS.putIfAbsent(command.name().toLowerCase(Locale.ROOT), command) != null) {
            throw new IllegalArgumentException("Terminal command " + command.name() + " registered twice");
        }
    }

    public static void registerShowTopic(String topic, ShowTopic show) {
        if (TOPICS.putIfAbsent(topic.toLowerCase(Locale.ROOT), show) != null) {
            throw new IllegalArgumentException("Show topic " + topic + " registered twice");
        }
    }

    public static Map<String, TerminalCommand> commands() {
        return COMMANDS;
    }

    // Words of a command line: spaces split them, double quotes keep them together.
    public static List<String> words(String line) {
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean quoted = false, any = false;
        for (char c : line.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
                any = true;
            } else if (c == ' ' && !quoted) {
                if (any) {
                    words.add(word.toString());
                    word.setLength(0);
                    any = false;
                }
            } else {
                word.append(c);
                any = true;
            }
        }
        if (any) {
            words.add(word.toString());
        }
        return words;
    }

    // Runs a command line: the first word picks the command (its Firewall permission checked), the rest are its args.
    public static TerminalOutput execute(TerminalContext context, String line) {
        List<String> words = words(line);
        if (words.isEmpty()) {
            return new TerminalOutput();
        }
        TerminalCommand command = COMMANDS.get(words.getFirst().toLowerCase(Locale.ROOT));
        if (command == null) {
            // Not one of the desk's own words: an ELCL command (COMMANDS.md).
            return ElclCommandLine.run(context, line);
        }
        if (command.permission() != null && !context.allowed(command.permission())) {
            return TerminalOutput.message(TerminalActions.notAuthorised(command.permission()));
        }
        TerminalOutput out = command.run(context, words.subList(1, words.size()));
        // The desk's own words are aliases of ELCL commands now (COMMANDS.md 10): say which.
        String alias = "crt.encodedlogistics.alias." + command.name().toLowerCase(Locale.ROOT)
                + (command.name().equals("show") && words.size() > 1 ? "." + words.get(1).toLowerCase(Locale.ROOT) : "");
        if (Language.getInstance().has(alias)) {
            out.lines().addFirst(TerminalLine.of(Component.translatable(alias), TerminalLine.DIM));
        }
        return out;
    }

    // Completions for the last word of a line (a trailing space starts a new one).
    public static List<String> complete(TerminalContext context, String line) {
        List<String> words = new ArrayList<>(words(line));
        if (line.endsWith(" ") || words.isEmpty()) {
            words.add("");
        }
        String last = words.getLast().toLowerCase(Locale.ROOT);
        if (words.size() == 1) {
            return COMMANDS.keySet().stream().filter(name -> name.startsWith(last)).toList();
        }
        TerminalCommand command = COMMANDS.get(words.getFirst().toLowerCase(Locale.ROOT));
        return command != null ? command.complete(context, words.subList(1, words.size())) : List.of();
    }

    // --- Built in ---

    private abstract static class Simple implements TerminalCommand {
        private final String name, usage;
        private final @Nullable RackPermission permission;

        Simple(String name, String usage, @Nullable RackPermission permission) {
            this.name = name;
            this.usage = usage;
            this.permission = permission;
        }

        @Override
        public String name() {
            return name;
        }

        @Override
        public String usage() {
            return usage;
        }

        @Override
        public Component help() {
            return Component.translatable("crt.encodedlogistics.help." + name);
        }

        @Override
        public @Nullable RackPermission permission() {
            return permission;
        }

        TerminalOutput usageMessage() {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.usage", usage));
        }
    }

    static {
        register(new Simple("help", "help [command]", null) {
            @Override
            public TerminalOutput run(TerminalContext context, List<String> args) {
                TerminalOutput out = new TerminalOutput();
                if (!args.isEmpty()) {
                    TerminalCommand command = COMMANDS.get(args.getFirst().toLowerCase(Locale.ROOT));
                    if (command == null) {
                        return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.unknown_command", args.getFirst()));
                    }
                    out.line(TerminalLine.builder().text("    ").text(command.usage()).attr(TerminalLine.BRIGHT).build());
                    out.line(TerminalLine.builder().text("    ").text(command.help()).build());
                    if (command.name().equals("show")) {
                        TOPICS.forEach((topic, show) -> out.line(TerminalLine.builder().text("      ").left(topic, 12).text(show.help()).build()));
                    }
                    return out;
                }
                for (TerminalCommand command : COMMANDS.values()) {
                    out.line(TerminalLine.builder().text("    ").left(command.usage(), 40).text(command.help()).build());
                }
                out.line(TerminalLine.of(Component.translatable("crt.encodedlogistics.help.more"), TerminalLine.DIM));
                return out;
            }

            @Override
            public List<String> complete(TerminalContext context, List<String> args) {
                String start = args.getLast().toLowerCase(Locale.ROOT);
                return args.size() == 1 ? COMMANDS.keySet().stream().filter(name -> name.startsWith(start)).toList() : List.of();
            }
        });

        register(new Simple("show", "show <topic> [filter]", RackPermission.VIEW) {
            @Override
            public TerminalOutput run(TerminalContext context, List<String> args) {
                if (args.isEmpty()) {
                    return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.usage", "show " + String.join("|", TOPICS.keySet())));
                }
                ShowTopic topic = TOPICS.get(args.getFirst().toLowerCase(Locale.ROOT));
                if (topic == null) {
                    return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.unknown_topic", args.getFirst()));
                }
                if (context.network() == null) {
                    return TerminalOutput.message(TerminalActions.offline());
                }
                return topic.show(context, args.subList(1, args.size()));
            }

            @Override
            public List<String> complete(TerminalContext context, List<String> args) {
                String start = args.getLast().toLowerCase(Locale.ROOT);
                return args.size() == 1 ? TOPICS.keySet().stream().filter(name -> name.startsWith(start)).toList() : List.of();
            }
        });

        register(new Simple("withdraw", "withdraw <item> <amount> [*drawer|*inv]", RackPermission.EXTRACT) {
            @Override
            public TerminalOutput run(TerminalContext context, List<String> args) {
                if (args.size() < 2) {
                    return usageMessage();
                }
                ItemKey key = item(context, args.get(0));
                long amount = TerminalItems.amount(args.get(1));
                TerminalActions.Destination destination = args.size() > 2 ? TerminalActions.Destination.parse(args.get(2)) : TerminalActions.Destination.DRAWER;
                if (key == null) {
                    return ambiguous(context, args.get(0));
                }
                if (amount <= 0 || destination == null || destination == TerminalActions.Destination.NETWORK) {
                    return usageMessage();
                }
                return TerminalActions.withdraw(context, key, amount, destination);
            }

            @Override
            public List<String> complete(TerminalContext context, List<String> args) {
                return args.size() == 1 ? TerminalItems.complete(context, args.getFirst())
                        : args.size() == 3 ? List.of("*drawer", "*inv").stream().filter(s -> s.startsWith(args.getLast().toLowerCase(Locale.ROOT))).toList()
                                : List.of();
            }
        });

        register(new Simple("craft", "craft <item> <amount> [scheduler] [*network|*drawer|*inv]", RackPermission.CRAFT) {
            @Override
            public TerminalOutput run(TerminalContext context, List<String> args) {
                if (args.size() < 2) {
                    return usageMessage();
                }
                ItemKey key = item(context, args.get(0));
                long amount = TerminalItems.amount(args.get(1));
                String scheduler = args.size() > 2 ? args.get(2) : "*AUTO";
                TerminalActions.Destination destination = args.size() > 3 ? TerminalActions.Destination.parse(args.get(3)) : TerminalActions.Destination.NETWORK;
                if (key == null) {
                    return ambiguous(context, args.get(0));
                }
                if (amount <= 0 || destination == null) {
                    return usageMessage();
                }
                return TerminalActions.craft(context, key, amount, scheduler, destination);
            }

            @Override
            public List<String> complete(TerminalContext context, List<String> args) {
                return args.size() == 1 ? TerminalItems.complete(context, args.getFirst()) : List.of();
            }
        });

        register(new Simple("cancel", "cancel job <id>", RackPermission.CRAFT) {
            @Override
            public TerminalOutput run(TerminalContext context, List<String> args) {
                if (args.size() < 2 || !args.get(0).equalsIgnoreCase("job") || TerminalItems.amount(args.get(1)) <= 0) {
                    return usageMessage();
                }
                return TerminalActions.cancel(context, (int) TerminalItems.amount(args.get(1)));
            }

            @Override
            public List<String> complete(TerminalContext context, List<String> args) {
                return args.size() == 1 && "job".startsWith(args.getFirst().toLowerCase(Locale.ROOT)) ? List.of("job") : List.of();
            }
        });

        // The screen clears its own history; this just says so.
        register(new Simple("clear", "clear", null) {
            @Override
            public TerminalOutput run(TerminalContext context, List<String> args) {
                return new TerminalOutput();
            }
        });

        registerShowTopic("inventory", new Topic("inventory", TerminalCommands::showInventory));
        registerShowTopic("drives", new Topic("drives", TerminalCommands::showDrives));
        registerShowTopic("lanes", new Topic("lanes", TerminalCommands::showLanes));
        registerShowTopic("jobs", new Topic("jobs", (context, args) -> TerminalService.jobs(context, false)));
        registerShowTopic("devices", new Topic("devices", (context, args) -> TerminalService.devices(context, false)));
        registerShowTopic("power", new Topic("power", TerminalCommands::showPower));
    }

    // A built-in show topic: its help from the lang file.
    private record Topic(String name, BiFunction<TerminalContext, List<String>, TerminalOutput> body) implements ShowTopic {
        @Override
        public Component help() {
            return Component.translatable("crt.encodedlogistics.help.show." + name);
        }

        @Override
        public TerminalOutput show(TerminalContext context, List<String> args) {
            return body.apply(context, args);
        }
    }

    private static @Nullable ItemKey item(TerminalContext context, String spec) {
        return TerminalItems.resolve(context, spec);
    }

    // No such item, or several: lists them.
    private static TerminalOutput ambiguous(TerminalContext context, String spec) {
        List<ItemKey> found = TerminalItems.matches(context, spec);
        if (found.isEmpty()) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.no_item", spec));
        }
        TerminalOutput out = TerminalOutput.message(Component.translatable("crt.encodedlogistics.msg.ambiguous", spec));
        for (ItemKey key : found.subList(0, Math.min(10, found.size()))) {
            out.line(TerminalLine.builder().text("    ").left(TerminalItems.id(key), 40).text(key.stack().getHoverName()).build());
        }
        return out;
    }

    // --- Show topics ---

    // Items whose name or id contains the filter (#tag: in that tag), hot and cold.
    private static TerminalOutput showInventory(TerminalContext context, List<String> args) {
        NetworkStorage storage = context.storage();
        if (storage == null) {
            return TerminalOutput.message(TerminalActions.offline());
        }
        String filter = String.join(" ", args).toLowerCase(Locale.ROOT).trim();
        Map<ItemKey, Long> all = storage.listAll();
        List<ItemKey> keys = new ArrayList<>(all.keySet());
        keys.removeIf(key -> !TerminalService.matchesFilter(key, filter));
        keys.sort(Comparator.comparing(key -> key.stack().getHoverName().getString(), String.CASE_INSENSITIVE_ORDER));
        TerminalOutput out = new TerminalOutput();
        out.line(TerminalLine.builder().text("    ").left("ITEM", 40).right("QUANTITY", 12).text("   LOCATION").attr(TerminalLine.BRIGHT).build());
        for (ItemKey key : keys.subList(0, Math.min(200, keys.size()))) {
            long cold = storage.cold().count(key), hot = all.get(key) - cold;
            out.line(TerminalLine.builder().text("    ").left(key.stack().getHoverName(), 40).right(TerminalItems.count(all.get(key)), 12).text("   ")
                    .text(TerminalService.location(hot, cold)).build());
        }
        out.setMessage(Component.translatable("crt.encodedlogistics.msg.listed", keys.size()));
        return out;
    }

    // Every drive and tape: what holds it, its slot, its tier, bytes used and in all.
    private static TerminalOutput showDrives(TerminalContext context, List<String> args) {
        TerminalOutput out = new TerminalOutput();
        out.line(TerminalLine.builder().text("    ").left("HOLDER", 26).left("SLOT", 6).left("DRIVE", 10).right("USED", 10).right("TOTAL", 11)
                .attr(TerminalLine.BRIGHT).build());
        for (DriveBayBlockEntity bay : ControllerStructures.driveBays(context.server(), context.network())) {
            for (int slot = 0; slot < DriveBayBlockEntity.SLOTS; slot++) {
                ItemStack stack = bay.drive(slot);
                if (stack != null && stack.getItem() instanceof StorageDriveItem drive) {
                    driveLine(out, Component.literal("Drive Bay " + bay.getBlockPos().toShortString()), slot + 1, drive.getTier().label(),
                            StorageDriveItem.stats(stack));
                }
            }
        }
        for (RackDevice device : ControllerStructures.rackDevicesServing(context.server(), context.network())) {
            if (device instanceof StorageDevice storage) {
                for (int slot = 0; slot < storage.drives(); slot++) {
                    ItemStack stack = storage.items().get(slot);
                    if (stack.getItem() instanceof StorageDriveItem drive) {
                        driveLine(out, device.name(), slot + 1, drive.getTier().label(), StorageDriveItem.stats(stack));
                    }
                }
            } else if (device instanceof TapeLibraryDevice library) {
                for (int slot = 0; slot < library.tapeSlots(); slot++) {
                    ItemStack stack = library.items().get(slot);
                    if (stack.getItem() instanceof LtoTapeItem tape) {
                        driveLine(out, device.name(), slot + 1, tape.generation().label(), LtoTapeItem.stats(stack));
                    }
                }
            }
        }
        return out;
    }

    private static void driveLine(TerminalOutput out, Component holder, int slot, String tier, DriveStats stats) {
        out.line(TerminalLine.builder().text("    ").left(holder, 26).left(slot, 6).left(tier, 10).right(StorageDevice.bytes(stats.bytesUsed()), 10)
                .right(StorageDevice.bytes(stats.bytesTotal()), 11).build());
    }

    // The controller's lanes, and each rack's switch pool.
    private static TerminalOutput showLanes(TerminalContext context, List<String> args) {
        NetworkSnapshot snapshot = ControllerStructures.snapshotOf(context.server(), context.network());
        TerminalOutput out = new TerminalOutput();
        out.line("    Controller " + snapshot.lanesUsed() + " / " + snapshot.laneCapacity() + " lanes");
        for (RackBlockEntity rack : ControllerStructures.racks(context.server(), context.network())) {
            RackBlockEntity.Lanes lanes = rack.lanes();
            if (lanes.poolCapacity() > 0) {
                out.line("    Rack " + rack.getBlockPos().toShortString() + " pool " + lanes.poolUsed() + " / " + lanes.poolCapacity() + ", uplink "
                        + lanes.networkLanes());
            }
        }
        return out;
    }

    // Energy stored, in and out, and the ten hungriest kinds of device.
    private static TerminalOutput showPower(TerminalContext context, List<String> args) {
        NetworkSnapshot snapshot = ControllerStructures.snapshotOf(context.server(), context.network());
        TerminalOutput out = new TerminalOutput();
        out.line(String.format(Locale.ROOT, "    Stored %,d / %,d FE    in %+,.1f    out %,.1f FE/t", snapshot.stored(), snapshot.capacity(),
                snapshot.generation(), snapshot.usage()));
        out.line(TerminalLine.builder().text("    ").left("DEVICE", 34).right("COUNT", 6).right("FE/T", 10).attr(TerminalLine.BRIGHT).build());
        List<NetworkSnapshot.DeviceEntry> devices = new ArrayList<>(snapshot.devices());
        devices.sort(Comparator.comparingDouble(NetworkSnapshot.DeviceEntry::drain).reversed());
        for (NetworkSnapshot.DeviceEntry entry : devices.subList(0, Math.min(10, devices.size()))) {
            var item = BuiltInRegistries.ITEM.getValue(entry.item());
            out.line(TerminalLine.builder().text("    ").left(item.getName(item.getDefaultInstance()), 34).right(entry.count(), 6)
                    .right(String.format(Locale.ROOT, "%.1f", entry.drain()), 10).build());
        }
        return out;
    }
}
