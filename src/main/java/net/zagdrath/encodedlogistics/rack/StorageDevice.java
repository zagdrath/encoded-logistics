/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.storage.DriveStats;
import net.zagdrath.encodedlogistics.storage.DriveStorage;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.StorageTier;
import net.zagdrath.encodedlogistics.storage.StorageView;

// A rack device holding Storage Drives (NAS, SAN): while online they're part of the storage of the network it serves,
// with a priority (-999..999) and an access mode (read-write, read-only, write-only) as an Inventory Tap has. A drive
// gets its id the first time it goes in; its stats (the item's tooltip, the bay's fill light) are refreshed whenever the
// network changes it. The first drives() item slots hold the drives.
public abstract class StorageDevice extends RackDevice {
    public static final int READ_WRITE = 0, READ = 1, WRITE = 2, MIN_PRIORITY = -999, MAX_PRIORITY = 999;
    public static final int ACTION_SET_PRIORITY = 0, ACTION_STEP_PRIORITY = 1, ACTION_CYCLE_ACCESS = 2;
    public static final int LIGHT_OFF = 4;

    private int priority, access = READ_WRITE;
    // Client: per bay, tier ordinal * 8 + light, or -1 empty.
    private int[] shownBays = new int[0];

    protected StorageDevice(RackDeviceType type) {
        super(type);
    }

    // How many of its slots hold drives.
    public abstract int drives();

    // Its base drain, without the drives.
    protected abstract double baseDrain();

    // Whether its drives are reachable (online, and anything else it needs).
    public boolean ready() {
        return isOnline();
    }

    // Added to its priority (a SAN's extra uplinks).
    protected int priorityBonus() {
        return 0;
    }

    public int priority() {
        return priority;
    }

    public int access() {
        return access;
    }

    public int driveCount() {
        int count = 0;
        for (int slot = 0; slot < drives(); slot++) {
            if (items().get(slot).getItem() instanceof StorageDriveItem) {
                count++;
            }
        }
        return count;
    }

    @Override
    public double drain() {
        return baseDrain() + driveCount() * Config.RACK_DRIVE_DRAIN.getAsDouble();
    }

    // --- As network storage ---

    public List<StorageView> views(MinecraftServer server) {
        return views(server, new HashSet<>());
    }

    // Its drives as storage, skipping drives whose id is in seen (and adding the rest): a copied drive (creative
    // pick-block) shares its id, and so its contents, with the original, so each id counts once on a network.
    public List<StorageView> views(MinecraftServer server, Set<UUID> seen) {
        List<StorageView> views = new ArrayList<>();
        if (!ready()) {
            return views;
        }
        DriveStorage data = DriveStorage.get(server);
        for (int slot = 0; slot < drives(); slot++) {
            ItemStack stack = items().get(slot);
            if (stack.getItem() instanceof StorageDriveItem drive) {
                UUID id = assignId(stack);
                if (seen.add(id)) {
                    views.add(new DriveView(data, slot, id, drive.getTier()));
                }
            }
        }
        return views;
    }

    private static UUID assignId(ItemStack stack) {
        UUID id = StorageDriveItem.id(stack);
        if (id == null) {
            id = UUID.randomUUID();
            stack.set(ModDataComponents.DRIVE_ID.get(), id);
        }
        return id;
    }

    // A drive's stats, refreshed from DriveStorage.
    private void refresh(MinecraftServer server, int slot) {
        ItemStack stack = items().get(slot);
        if (stack.getItem() instanceof StorageDriveItem drive) {
            DriveStats stats = DriveStorage.get(server).stats(assignId(stack), drive.getTier());
            if (!stats.equals(stack.get(ModDataComponents.DRIVE_STATS.get()))) {
                stack.set(ModDataComponents.DRIVE_STATS.get(), stats);
            }
        }
    }

    private void driveChanged(int slot) {
        if (rack() != null && rack().getLevel() instanceof ServerLevel level) {
            refresh(level.getServer(), slot);
        }
        changed(false);
    }

    // Drives in or out: they get their ids and stats, and its drain changes.
    @Override
    public void itemsChanged() {
        if (rack() != null && rack().getLevel() instanceof ServerLevel level) {
            for (int slot = 0; slot < drives(); slot++) {
                refresh(level.getServer(), slot);
            }
        }
        changed(true);
    }

    // Bytes used and in all, over its drives.
    public long[] capacity() {
        long used = 0, total = 0;
        for (int slot = 0; slot < drives(); slot++) {
            ItemStack stack = items().get(slot);
            if (stack.getItem() instanceof StorageDriveItem) {
                DriveStats stats = StorageDriveItem.stats(stack);
                used += stats.bytesUsed();
                total += stats.bytesTotal();
            }
        }
        return new long[] { used, total };
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        long[] capacity = capacity();
        float fraction = capacity[1] <= 0 ? 0 : (float) capacity[0] / capacity[1];
        RackDeviceInfo.BarStyle style = fraction > 0.95F ? RackDeviceInfo.BarStyle.LOW : fraction >= 0.75F ? RackDeviceInfo.BarStyle.WARN
                : RackDeviceInfo.BarStyle.NORMAL;
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.storage.capacity"),
                Component.translatable("gui.encodedlogistics.storage.capacity", bytes(capacity[0]), bytes(capacity[1])),
                new RackDeviceInfo.Bar(fraction, style)));
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.storage.drives"),
                Component.literal(driveCount() + " / " + drives())));
        return lines;
    }

    // "512K", "1.5M": bytes in the tiers' units.
    public static String bytes(long bytes) {
        if (bytes >= 1024L * 1024) {
            double m = bytes / (1024.0 * 1024);
            return (m == Math.floor(m) ? Long.toString((long) m) : String.format(Locale.ROOT, "%.1f", m)) + "M";
        }
        if (bytes >= 1024) {
            return bytes / 1024 + "K";
        }
        return Long.toString(bytes);
    }

    // --- Panel ---

    @Override
    public void handleAction(ServerPlayer player, int action, int value, String text) {
        switch (action) {
            case ACTION_SET_PRIORITY -> priority = Mth.clamp(value, MIN_PRIORITY, MAX_PRIORITY);
            case ACTION_STEP_PRIORITY -> priority = Mth.clamp(priority + value, MIN_PRIORITY, MAX_PRIORITY);
            case ACTION_CYCLE_ACCESS -> access = (access + 1) % 3;
            default -> {
                return;
            }
        }
        changed(false);
    }

    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        save(output);
        long[] capacity = capacity();
        output.putLong("used", capacity[0]);
        output.putLong("total", capacity[1]);
        output.putBoolean("ready", ready());
    }

    // --- Saving ---

    @Override
    public void saveSettings(ValueOutput output) {
        output.putInt("priority", priority);
        output.putInt("access", access);
    }

    @Override
    public void loadSettings(ValueInput input) {
        priority = Mth.clamp(input.getIntOr("priority", 0), MIN_PRIORITY, MAX_PRIORITY);
        access = Mth.clamp(input.getIntOr("access", READ_WRITE), READ_WRITE, WRITE);
    }

    @Override
    public void save(ValueOutput output) {
        saveSettings(output);
        saveItems(output);
    }

    @Override
    public void load(ValueInput input) {
        loadSettings(input);
        loadItems(input);
    }

    // What the renderer draws in each bay: the drive's tier and fill light.
    @Override
    public void writeClient(ValueOutput output) {
        int[] bays = new int[drives()];
        for (int slot = 0; slot < drives(); slot++) {
            ItemStack stack = items().get(slot);
            bays[slot] = stack.getItem() instanceof StorageDriveItem drive
                    ? drive.getTier().ordinal() * 8 + (ready() ? StorageDriveItem.stats(stack).light() : LIGHT_OFF) : -1;
        }
        output.putIntArray("bays", bays);
    }

    @Override
    public void readClient(ValueInput input) {
        shownBays = input.getIntArray("bays").orElse(new int[0]);
    }

    // Client: a bay's tier ordinal * 8 + light, or -1.
    public int shownBay(int bay) {
        return bay < shownBays.length ? shownBays[bay] : -1;
    }

    // A drive in one of its slots, as network storage: its device's priority and access mode.
    private final class DriveView implements StorageView {
        private final DriveStorage data;
        private final int slot;
        private final UUID id;
        private final StorageTier tier;

        DriveView(DriveStorage data, int slot, UUID id, StorageTier tier) {
            this.data = data;
            this.slot = slot;
            this.id = id;
            this.tier = tier;
        }

        @Override
        public int priority() {
            return priority + priorityBonus();
        }

        @Override
        public boolean isTap() {
            return false;
        }

        @Override
        public void listInto(Map<ItemKey, Long> all) {
            data.contents(id).forEach((key, count) -> all.merge(key, count, Long::sum));
        }

        @Override
        public long count(ItemKey key) {
            return data.count(id, key);
        }

        @Override
        public long insert(ItemKey key, long amount, boolean simulate) {
            if (access == READ) {
                return 0;
            }
            long accepted = data.insert(id, tier, key, amount, simulate);
            if (accepted > 0 && !simulate) {
                driveChanged(slot);
            }
            return accepted;
        }

        @Override
        public UUID driveId() {
            return id;
        }

        @Override
        public long lastAccess(ItemKey key) {
            return data.lastAccess(id, key);
        }

        @Override
        public DriveStats stats() {
            return data.stats(id, tier);
        }

        @Override
        public long extract(ItemKey key, long amount, boolean simulate) {
            if (access == WRITE) {
                return 0;
            }
            long taken = data.extract(id, key, amount, simulate);
            if (taken > 0 && !simulate) {
                driveChanged(slot);
            }
            return taken;
        }
    }
}
