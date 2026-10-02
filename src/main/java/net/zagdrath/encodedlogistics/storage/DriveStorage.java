/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.EncodedLogistics;

// What every Storage Drive holds, by the drive's id (its encodedlogistics:drive_id component), saved with the world (in
// the overworld's data, so a drive keeps its contents wherever it goes). The drive item itself only caches DriveStats.
//
// Capacity: a drive of K bytes holds 8 items per byte, and each type it stores reserves K / 128 bytes; it holds at most
// driveTypeLimit types.
public class DriveStorage extends SavedData {
    private record Entry(ItemStack item, long count) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.fieldOf("item").forGetter(Entry::item),
                Codec.LONG.fieldOf("count").forGetter(Entry::count))
                .apply(i, Entry::new));
    }

    private record SavedDrive(UUID id, List<Entry> items) {
        static final Codec<SavedDrive> CODEC = RecordCodecBuilder.create(i -> i.group(
                UUIDUtil.CODEC.fieldOf("id").forGetter(SavedDrive::id),
                Entry.CODEC.listOf().fieldOf("items").forGetter(SavedDrive::items))
                .apply(i, SavedDrive::new));
    }

    private static final Codec<DriveStorage> CODEC = SavedDrive.CODEC.listOf().fieldOf("drives").codec()
            .xmap(DriveStorage::new, DriveStorage::save);

    public static final SavedDataType<DriveStorage> TYPE = new SavedDataType<>(EncodedLogistics.id("drives"), DriveStorage::new, CODEC);

    private final Map<UUID, Map<ItemKey, Long>> drives = new HashMap<>();

    public DriveStorage() {}

    private DriveStorage(List<SavedDrive> saved) {
        for (SavedDrive drive : saved) {
            Map<ItemKey, Long> items = new LinkedHashMap<>();
            for (Entry entry : drive.items()) {
                if (!entry.item().isEmpty() && entry.count() > 0) {
                    items.merge(ItemKey.of(entry.item()), entry.count(), Long::sum);
                }
            }
            drives.put(drive.id(), items);
        }
    }

    private List<SavedDrive> save() {
        List<SavedDrive> saved = new ArrayList<>(drives.size());
        drives.forEach((id, items) -> {
            if (!items.isEmpty()) {
                List<Entry> entries = new ArrayList<>(items.size());
                items.forEach((key, count) -> entries.add(new Entry(key.stack(), count)));
                saved.add(new SavedDrive(id, entries));
            }
        });
        return saved;
    }

    public static DriveStorage get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(TYPE);
    }

    // A drive's contents; empty for a drive that has never held anything.
    public Map<ItemKey, Long> contents(UUID drive) {
        Map<ItemKey, Long> items = drives.get(drive);
        return items == null ? Map.of() : Collections.unmodifiableMap(items);
    }

    public long count(UUID drive, ItemKey key) {
        Map<ItemKey, Long> items = drives.get(drive);
        return items == null ? 0 : items.getOrDefault(key, 0L);
    }

    // Puts up to amount of an item into a drive of that tier; returns how many fit.
    public long insert(UUID drive, StorageTier tier, ItemKey key, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        Map<ItemKey, Long> items = drives.get(drive);
        int types = items == null ? 0 : items.size();
        long total = items == null ? 0 : items.values().stream().mapToLong(Long::longValue).sum();
        boolean known = items != null && items.containsKey(key);
        if (!known) {
            if (types >= Config.DRIVE_TYPE_LIMIT.getAsInt()) {
                return 0;
            }
            types++;
        }
        long room = (tier.bytes() - types * tier.bytesPerType()) * 8 - total;
        long accepted = Math.max(0, Math.min(amount, room));
        if (accepted > 0 && !simulate) {
            drives.computeIfAbsent(drive, id -> new LinkedHashMap<>()).merge(key, accepted, Long::sum);
            setDirty();
        }
        return accepted;
    }

    // Takes up to amount of an item out of a drive; returns how many it had.
    public long extract(UUID drive, ItemKey key, long amount, boolean simulate) {
        Map<ItemKey, Long> items = drives.get(drive);
        long have = items == null ? 0 : items.getOrDefault(key, 0L);
        long taken = Math.max(0, Math.min(amount, have));
        if (taken > 0 && !simulate) {
            if (taken == have) {
                items.remove(key);
            } else {
                items.put(key, have - taken);
            }
            setDirty();
        }
        return taken;
    }

    public DriveStats stats(UUID drive, StorageTier tier) {
        Map<ItemKey, Long> items = drives.get(drive);
        if (items == null || items.isEmpty()) {
            return DriveStats.empty(tier);
        }
        long total = items.values().stream().mapToLong(Long::longValue).sum();
        long bytes = items.size() * tier.bytesPerType() + (total + 7) / 8;
        return new DriveStats(bytes, tier.bytes(), items.size());
    }
}
