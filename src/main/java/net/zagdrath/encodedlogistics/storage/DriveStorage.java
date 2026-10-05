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
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.EncodedLogistics;

// What every Storage Drive and LTO tape holds, by its id (its encodedlogistics:drive_id component), saved with the
// world (in the overworld's data, so a drive keeps its contents wherever it goes). The item itself only caches
// DriveStats.
//
// Capacity (DriveCapacity): a medium of K bytes holds 8 items per byte (a Fluid or Pressurized Storage Drive: 1,000 mB,
// a bucket, per byte; ResourceType.unitsPerByte), and each type it stores reserves K / 128 bytes; it holds at most
// driveTypeLimit types. A drive holds only its own resource type (StorageDriveItem); tapes hold items.
//
// Entries save an item as before ("item"), so item-only worlds load unchanged, and a fluid or gas as "resource".
//
// Each stored type also remembers the game tick it was last put in or taken out (archiving to tape goes by it); a type
// stored before that was kept counts as touched when it's first asked about.
public class DriveStorage extends SavedData {
    private record Entry(Optional<ItemStack> item, Optional<StorageKey> resource, long count, long touched) {
        static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemStack.CODEC.optionalFieldOf("item").forGetter(Entry::item),
                StorageKey.TYPED_CODEC.optionalFieldOf("resource").forGetter(Entry::resource),
                Codec.LONG.fieldOf("count").forGetter(Entry::count),
                Codec.LONG.optionalFieldOf("touched", -1L).forGetter(Entry::touched))
                .apply(i, Entry::new));

        static Entry of(StorageKey key, long count, long touched) {
            return key.isItem() ? new Entry(Optional.of(key.stack()), Optional.empty(), count, touched)
                    : new Entry(Optional.empty(), Optional.of(key), count, touched);
        }

        @Nullable StorageKey key() {
            if (resource.isPresent()) {
                return resource.get();
            }
            return item.filter(stack -> !stack.isEmpty()).map(StorageKey::of).orElse(null);
        }
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

    private final Map<UUID, Map<StorageKey, Long>> drives = new HashMap<>();
    // Per drive, the tick each type was last put in or taken out.
    private final Map<UUID, Map<StorageKey, Long>> touched = new HashMap<>();
    // The game time, as of the last get().
    private long clock;

    public DriveStorage() {}

    private DriveStorage(List<SavedDrive> saved) {
        for (SavedDrive drive : saved) {
            Map<StorageKey, Long> items = new LinkedHashMap<>();
            Map<StorageKey, Long> times = new HashMap<>();
            for (Entry entry : drive.items()) {
                StorageKey key = entry.key();
                if (key != null && entry.count() > 0) {
                    items.merge(key, entry.count(), Long::sum);
                    if (entry.touched() >= 0) {
                        times.merge(key, entry.touched(), Math::max);
                    }
                }
            }
            drives.put(drive.id(), items);
            if (!times.isEmpty()) {
                touched.put(drive.id(), times);
            }
        }
    }

    private List<SavedDrive> save() {
        List<SavedDrive> saved = new ArrayList<>(drives.size());
        drives.forEach((id, items) -> {
            if (!items.isEmpty()) {
                Map<StorageKey, Long> times = touched.getOrDefault(id, Map.of());
                List<Entry> entries = new ArrayList<>(items.size());
                items.forEach((key, count) -> entries.add(Entry.of(key, count, times.getOrDefault(key, -1L))));
                saved.add(new SavedDrive(id, entries));
            }
        });
        return saved;
    }

    public static DriveStorage get(MinecraftServer server) {
        DriveStorage storage = server.overworld().getDataStorage().computeIfAbsent(TYPE);
        storage.clock = server.overworld().getGameTime();
        return storage;
    }

    // A drive's contents; empty for a drive that has never held anything.
    public Map<StorageKey, Long> contents(UUID drive) {
        Map<StorageKey, Long> items = drives.get(drive);
        return items == null ? Map.of() : Collections.unmodifiableMap(items);
    }

    public long count(UUID drive, StorageKey key) {
        Map<StorageKey, Long> items = drives.get(drive);
        return items == null ? 0 : items.getOrDefault(key, 0L);
    }

    // Puts up to amount of an item into an item drive (or tape) of that capacity; returns how many fit.
    public long insert(UUID drive, DriveCapacity capacity, StorageKey key, long amount, boolean simulate) {
        return insert(drive, capacity, ResourceType.ITEM, key, amount, simulate);
    }

    // Puts up to amount of a resource into a drive of that capacity holding that type (none of any other type fits);
    // returns how much fit.
    public long insert(UUID drive, DriveCapacity capacity, ResourceType type, StorageKey key, long amount, boolean simulate) {
        long accepted = key.type() == type ? Math.min(amount, room(drive, capacity, type, key)) : 0;
        if (accepted > 0 && !simulate) {
            drives.computeIfAbsent(drive, id -> new LinkedHashMap<>()).merge(key, accepted, Long::sum);
            touch(drive, key);
            setDirty();
        }
        return Math.max(0, accepted);
    }

    // How many more of an item fit in an item drive of that capacity.
    public long room(UUID drive, DriveCapacity capacity, StorageKey key) {
        return room(drive, capacity, ResourceType.ITEM, key);
    }

    // How much more of a resource fits in a drive of that capacity and type, in the type's unit.
    public long room(UUID drive, DriveCapacity capacity, ResourceType type, StorageKey key) {
        Map<StorageKey, Long> items = drives.get(drive);
        int types = items == null ? 0 : items.size();
        long total = items == null ? 0 : items.values().stream().mapToLong(Long::longValue).sum();
        if (items == null || !items.containsKey(key)) {
            if (types >= capacity.typeLimit()) {
                return 0;
            }
            types++;
        }
        long free = capacity.bytes() - types * capacity.bytesPerType();
        return Math.max(0, free * type.unitsPerByte() - total);
    }

    // Takes up to amount of an item out of a drive; returns how many it had.
    public long extract(UUID drive, StorageKey key, long amount, boolean simulate) {
        Map<StorageKey, Long> items = drives.get(drive);
        long have = items == null ? 0 : items.getOrDefault(key, 0L);
        long taken = Math.max(0, Math.min(amount, have));
        if (taken > 0 && !simulate) {
            if (taken == have) {
                items.remove(key);
                Map<StorageKey, Long> times = touched.get(drive);
                if (times != null) {
                    times.remove(key);
                }
            } else {
                items.put(key, have - taken);
                touch(drive, key);
            }
            setDirty();
        }
        return taken;
    }

    private void touch(UUID drive, StorageKey key) {
        touched.computeIfAbsent(drive, id -> new HashMap<>()).put(key, clock);
    }

    // The tick a type in a drive was last put in or taken out (now, for one never stamped); -1 when it isn't there.
    public long lastAccess(UUID drive, StorageKey key) {
        if (count(drive, key) <= 0) {
            return -1;
        }
        Map<StorageKey, Long> times = touched.computeIfAbsent(drive, id -> new HashMap<>());
        Long time = times.get(key);
        if (time == null) {
            times.put(key, clock);
            setDirty();
            return clock;
        }
        return time;
    }

    public long clock() {
        return clock;
    }

    public DriveStats stats(UUID drive, DriveCapacity capacity) {
        return stats(drive, capacity, ResourceType.ITEM);
    }

    public DriveStats stats(UUID drive, DriveCapacity capacity, ResourceType type) {
        Map<StorageKey, Long> items = drives.get(drive);
        if (items == null || items.isEmpty()) {
            return DriveStats.empty(capacity);
        }
        long total = items.values().stream().mapToLong(Long::longValue).sum();
        long perByte = type.unitsPerByte();
        long bytes = items.size() * capacity.bytesPerType() + (total + perByte - 1) / perByte;
        return new DriveStats(bytes, capacity.bytes(), items.size());
    }
}
