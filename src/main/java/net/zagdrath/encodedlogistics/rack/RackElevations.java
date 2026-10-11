/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.item.RackElevationItem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// What a Rack Elevation does with a Server Rack, on the server.
//
// Recording (sneak-use, rack access): every installed device's U, type and size; with the plan's Contents / Settings
// toggles on, also what each holds (RackTemplate#plain: no drive ids) and its settings (RackDeviceItem#settings).
//
// Applying (use, rack access), each planned device from U1 up, in the one mode, fill empty:
// - its units free: installed from the sources, then its planned contents and settings if those toggles are on;
// - the same device already at that U: left; its planned contents go into empty slots and its settings on, if toggled;
// - anything else (another device, or units taken): skipped. Nothing is ever removed or replaced.
// Items come from the player's inventory, then the shulker boxes in it, then - with Network
// storage on and EXTRACT on the rack's network - the network's storage. In creative (rackElevationCreativeFree) nothing
// is used up. What's there is installed; what isn't is reported (action bar, and chat with a [show] link to the list).
//
// plan() is what applying would do, without doing it: the preview overlay and popup, and the materials list.
public final class RackElevations {
    // The [show] link's custom click action (CustomClickActionEvent): the materials list for this rack.
    public static final net.minecraft.resources.Identifier SHOW_MATERIALS = EncodedLogistics.id("rack_elevation_materials");

    // What would happen to a planned device: installed, already there (left as it is), skipped, or missing its item.
    public enum State {
        FILL, SAME, SKIP, MISSING;

        private static final State[] VALUES = values();

        public static State byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : SKIP;
        }
    }

    // A planned device's fate, and (skipped) what's in the way.
    public record Step(RackTemplate.Planned planned, State state, @Nullable Component blocker) {}

    // A line of the materials list: an item, how many the sources have, how many the plan needs.
    public record Material(ItemStack item, long have, int need) {
        public boolean enough() {
            return have >= need;
        }
    }

    // The plan against a rack (or none: no rack, everything "fill"), and the materials it needs.
    public record Plan(List<Step> steps, List<Material> materials) {
        public int count(State state) {
            return (int) steps.stream().filter(step -> step.state() == state).count();
        }

        // Items short, over the whole list.
        public long missing() {
            return materials.stream().mapToLong(material -> Math.max(0, material.need() - material.have())).sum();
        }
    }

    private RackElevations() {}

    // --- Recording ---

    public static void record(ServerPlayer player, RackBlockEntity rack, ItemStack stack) {
        ServerLevel level = (ServerLevel) rack.getLevel();
        if (level == null || !NetworkAccess.rack(level, rack.getBlockPos(), player)) {
            return;
        }
        RackTemplate template = RackTemplate.orEmpty(stack);
        List<RackTemplate.Planned> devices = new ArrayList<>();
        for (RackDevice device : rack.devices()) {
            List<RackTemplate.SlotItem> contents = new ArrayList<>();
            if (template.includeContents()) {
                for (int slot = 0; slot < device.items().size(); slot++) {
                    ItemStack item = device.items().get(slot);
                    if (!item.isEmpty()) {
                        contents.add(new RackTemplate.SlotItem(slot, ItemStackTemplate.fromNonEmptyStack(RackTemplate.plain(item))));
                    }
                }
            }
            Optional<CompoundTag> settings = template.includeSettings() ? Optional.of(RackDeviceItem.settings(device, level.registryAccess()))
                    : Optional.empty();
            devices.add(new RackTemplate.Planned(device.u(), device.type().id(), device.size(), contents, settings));
        }
        Component rackName = rackName(level.getServer(), rack);
        String name = template.name().isEmpty() ? networkName(level.getServer(), rack) : template.name();
        RackTemplate recorded = template.withDevices(devices).withName(name);
        RackElevationItem.writeHeld(player, stack, recorded);
        player.sendSystemMessage(Component.translatable("message.encodedlogistics.rack_elevation.recorded", devices.size(), rackName));
    }

    // The rack's network's system name, or "" off a network.
    private static String networkName(MinecraftServer server, RackBlockEntity rack) {
        NetworkRef network = ControllerStructures.networkOf((ServerLevel) rack.getLevel(), rack.getBlockPos());
        return network != null ? new ElclSystem(server, network).name() : "";
    }

    private static Component rackName(MinecraftServer server, RackBlockEntity rack) {
        String network = networkName(server, rack);
        return network.isEmpty() ? Component.translatable("block.encodedlogistics.server_rack") : Component.literal(network);
    }

    // --- Where items come from ---

    // The player's inventory (and the shulker boxes in it), then the rack network's storage; or, in creative with
    // rackElevationCreativeFree, an endless supply.
    static final class Sources {
        private final ServerPlayer player;
        private final @Nullable NetworkStorage network;
        final boolean free;

        Sources(ServerPlayer player, RackTemplate template, @Nullable RackBlockEntity rack) {
            this.player = player;
            this.free = player.hasInfiniteMaterials() && Config.RACK_ELEVATION_CREATIVE_FREE.getAsBoolean();
            this.network = template.useNetworkStorage() && rack != null ? networkStorage(player, rack) : null;
        }

        // The rack network's storage, when the player may take from it (EXTRACT).
        private static @Nullable NetworkStorage networkStorage(ServerPlayer player, RackBlockEntity rack) {
            if (!(rack.getLevel() instanceof ServerLevel level)) {
                return null;
            }
            NetworkRef network = ControllerStructures.networkOf(level, rack.getBlockPos());
            if (network == null || !NetworkAccess.allowed(level.getServer(), network, player, RackPermission.EXTRACT)) {
                return null;
            }
            return ControllerStructures.sharedStorageOf(level.getServer(), network, false);
        }

        // How many matching items there are, over every source.
        long count(Predicate<ItemStack> match) {
            if (free) {
                return Long.MAX_VALUE / 4;
            }
            long count = 0;
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (match.test(stack)) {
                    count += stack.getCount();
                } else if (isShulkerBox(stack)) {
                    for (ItemStack inner : contents(stack)) {
                        if (match.test(inner)) {
                            count += inner.getCount();
                        }
                    }
                }
            }
            if (network != null) {
                for (Map.Entry<StorageKey, Long> entry : network.list().entrySet()) {
                    if (entry.getKey().isItem() && match.test(entry.getKey().toStack(1))) {
                        count += entry.getValue();
                    }
                }
            }
            return count;
        }

        // Takes up to amount matching items (of one kind), from the first source that has them; EMPTY when none.
        ItemStack take(Predicate<ItemStack> match, int amount, ItemStack fallback) {
            if (free) {
                return fallback.copyWithCount(amount);
            }
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (match.test(stack)) {
                    ItemStack taken = stack.split(Math.min(amount, stack.getCount()));
                    inventory.setChanged();
                    return taken;
                }
            }
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                ItemStack box = inventory.getItem(slot);
                if (!isShulkerBox(box)) {
                    continue;
                }
                NonNullList<ItemStack> inner = contents(box);
                for (ItemStack stack : inner) {
                    if (match.test(stack)) {
                        ItemStack taken = stack.split(Math.min(amount, stack.getCount()));
                        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(inner));
                        inventory.setChanged();
                        return taken;
                    }
                }
            }
            if (network != null) {
                for (StorageKey key : List.copyOf(network.list().keySet())) {
                    if (key.isItem() && match.test(key.toStack(1))) {
                        long got = network.extract(key, amount, false);
                        if (got > 0) {
                            return key.toStack((int) got);
                        }
                    }
                }
            }
            return ItemStack.EMPTY;
        }

        private static boolean isShulkerBox(ItemStack stack) {
            return stack.getItem() instanceof BlockItem block && block.getBlock() instanceof ShulkerBoxBlock && stack.has(DataComponents.CONTAINER);
        }

        private static NonNullList<ItemStack> contents(ItemStack box) {
            NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
            ItemContainerContents contents = box.get(DataComponents.CONTAINER);
            if (contents != null) {
                contents.copyInto(items);
            }
            return items;
        }
    }

    // A rack device item of that type (its settings, if any, don't matter).
    private static Predicate<ItemStack> device(RackDeviceType type) {
        return stack -> !stack.isEmpty() && stack.is(type.item());
    }

    private static Predicate<ItemStack> content(ItemStackTemplate planned) {
        return stack -> RackTemplate.matches(stack, planned);
    }

    // --- Planning ---

    // What applying the plan to the rack would do (rack null: the editor, with no rack - every device "fill" or
    // "missing" by the sources), and the materials it needs, with the sources' counts.
    public static Plan plan(ServerPlayer player, RackTemplate template, @Nullable RackBlockEntity rack) {
        RackTemplate plan = template.valid();
        Sources sources = new Sources(player, plan, rack);
        // Counts as the plan uses them up (two Routers planned, one there: one fills, one is missing).
        Map<RackDeviceType, Long> left = new LinkedHashMap<>();
        Map<RackDeviceType, Integer> needDevices = new LinkedHashMap<>();
        Map<RackDeviceType, Long> haveDevices = new LinkedHashMap<>();
        List<Step> steps = new ArrayList<>();
        Map<String, Material> contentNeeds = new LinkedHashMap<>();
        for (RackTemplate.Planned planned : plan.devices()) {
            RackDeviceType type = planned.deviceType();
            if (type == null) {
                continue;
            }
            RackDevice existing = rack != null ? rack.deviceAt(planned.u()) : null;
            boolean same = existing != null && existing.u() == planned.u() && existing.type() == type;
            State state;
            Component blocker = null;
            if (same) {
                state = State.SAME;
            } else if (rack != null && !rack.fits(planned.u(), planned.size())) {
                state = State.SKIP;
                blocker = blocker(rack, planned);
            } else {
                haveDevices.computeIfAbsent(type, t -> sources.count(device(t)));
                long available = left.computeIfAbsent(type, haveDevices::get);
                needDevices.merge(type, 1, Integer::sum);
                if (available > 0) {
                    left.put(type, available - 1);
                    state = State.FILL;
                } else {
                    state = State.MISSING;
                }
            }
            steps.add(new Step(planned, state, blocker));
            if (plan.includeContents() && state != State.SKIP) {
                for (RackTemplate.SlotItem item : planned.contents()) {
                    if (same && existing.items().size() > item.slot() && !existing.items().get(item.slot()).isEmpty()) {
                        continue;
                    }
                    String key = item.item().toString();
                    Material had = contentNeeds.get(key);
                    long have = had != null ? had.have() : sources.count(content(item.item()));
                    contentNeeds.put(key, new Material(item.item().create(), have, (had != null ? had.need() : 0) + item.item().count()));
                }
            }
        }
        List<Material> materials = new ArrayList<>();
        needDevices.forEach((type, need) -> materials.add(new Material(new ItemStack(type.item()), Math.min(haveDevices.get(type), Integer.MAX_VALUE),
                need)));
        materials.addAll(contentNeeds.values());
        if (sources.free) {
            materials.replaceAll(material -> new Material(material.item(), material.need(), material.need()));
        }
        return new Plan(steps, materials);
    }

    // What's in a skipped device's way: the first device in its units ("Router"), or "blocked" (past the top).
    private static Component blocker(RackBlockEntity rack, RackTemplate.Planned planned) {
        for (int unit = planned.u(); unit <= Math.min(planned.top(), RackGeometry.UNITS); unit++) {
            RackDevice device = rack.deviceAt(unit);
            if (device != null) {
                return device.name();
            }
        }
        return Component.translatable("message.encodedlogistics.rack_elevation.blocked");
    }

    // --- Applying ---

    public record Result(int installed, int skipped, int missing, List<Component> report) {}

    public static @Nullable Result apply(ServerPlayer player, RackBlockEntity rack, ItemStack stack) {
        if (!(rack.getLevel() instanceof ServerLevel level) || !NetworkAccess.rack(level, rack.getBlockPos(), player)) {
            return null;
        }
        RackTemplate template = RackTemplate.of(stack);
        if (template == null) {
            return null;
        }
        template = template.valid();
        Sources sources = new Sources(player, template, rack);
        rack.visitFront();
        int installed = 0, skipped = 0, missing = 0;
        List<Component> report = new ArrayList<>();
        Map<String, Integer> missingItems = new LinkedHashMap<>();
        for (RackTemplate.Planned planned : template.devices()) {
            RackDeviceType type = planned.deviceType();
            if (type == null) {
                continue;
            }
            RackDevice existing = rack.deviceAt(planned.u());
            RackDevice device;
            if (existing != null && existing.u() == planned.u() && existing.type() == type) {
                device = existing;
            } else if (!rack.fits(planned.u(), planned.size())) {
                skipped++;
                report.add(Component.translatable("message.encodedlogistics.rack_elevation.skipped", planned.u(), blocker(rack, planned))
                        .withStyle(ChatFormatting.GOLD));
                continue;
            } else {
                ItemStack item = sources.take(device(type), 1, new ItemStack(type.item()));
                if (item.isEmpty()) {
                    missing++;
                    missingItems.merge(new ItemStack(type.item()).getHoverName().getString(), 1, Integer::sum);
                    continue;
                }
                device = RackDeviceItem.create(item, level.registryAccess());
                if (device == null) {
                    continue;
                }
                rack.install(device, planned.u(), player);
                installed++;
            }
            if (template.includeContents()) {
                boolean changed = false;
                for (RackTemplate.SlotItem item : planned.contents()) {
                    if (item.slot() >= device.items().size() || !device.items().get(item.slot()).isEmpty()
                            || !type.slots().get(item.slot()).accepts().test(item.item().create())) {
                        continue;
                    }
                    int count = Math.min(item.item().count(), type.slots().get(item.slot()).maxStack());
                    ItemStack taken = sources.take(content(item.item()), count, item.item().create());
                    if (taken.isEmpty()) {
                        missing++;
                        missingItems.merge(item.item().create().getHoverName().getString(), count, Integer::sum);
                        continue;
                    }
                    device.items().set(item.slot(), taken);
                    changed = true;
                }
                if (changed) {
                    device.itemsChanged();
                }
            }
            if (template.includeSettings() && planned.settings().isPresent()) {
                RackDeviceItem.loadSettings(device, planned.settings().get().copy(), level.registryAccess());
                rack.deviceChanged(true);
            }
        }
        missingItems.forEach((name, count) -> report.add(Component.translatable("message.encodedlogistics.rack_elevation.missing_item", count, name)
                .withStyle(ChatFormatting.RED)));
        Component summary = Component.translatable("message.encodedlogistics.rack_elevation.summary", installed, skipped, missing);
        player.sendOverlayMessage(summary);
        if (!report.isEmpty()) {
            MutableComponent show = Component.translatable("message.encodedlogistics.rack_elevation.show").withStyle(style -> style
                    .withColor(ChatFormatting.AQUA)
                    .withClickEvent(new ClickEvent.Custom(SHOW_MATERIALS, Optional.of(net.minecraft.nbt.LongTag.valueOf(rack.getBlockPos().asLong()))))
                    .withHoverEvent(new HoverEvent.ShowText(Component.translatable("gui.encodedlogistics.rack_elevation.materials"))));
            player.sendSystemMessage(summary.copy().append(" ").append(show));
            report.forEach(player::sendSystemMessage);
        }
        return new Result(installed, skipped, missing, report);
    }
}
