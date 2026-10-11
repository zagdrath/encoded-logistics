/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// Copies of Storage Drives, and drives going back to blank.
//
// A drive's contents live in DriveStorage under its drive_id, so a copy of the item (a creative middle-click, or a
// Ctrl+pick-block of a Drive Bay or rack with drives in) would share its original's id, and with it the same contents:
// taking items out of one would take them out of both. Instead, a copy is made a drive of its own as soon as it's seen
// beside its original: it gets a new id holding a copy of the contents, so it starts out with what the original has and
// goes its own way from there (as copying a cell does in AE2). Copies are seen:
// - in a creative player's inventory, cursor and open container, every tick (creativeTick), which is where the
//   middle-click puts them, so a copy normally splits off the moment it's made;
// - when a holder mounts a drive whose id another holder still has mounted (mount), for copies made inside a block's
//   or a rack's item data.
// Of the two, the one that's mounted (or, with neither mounted, the first found) keeps the id.
//
// A drive with an id is one of a kind and stacks to 1. One that's empty again (nothing in it, or no charge on an Energy
// Storage Drive) goes back to blank when it's in a player's inventory (clearIfEmpty), so it stacks with new ones.
public final class DriveCopies {
    // Where a drive is mounted: a holder's slot. The holder is weakly held, so a gone holder lets its entries go.
    public interface Mount {
        // The drive in a slot as mounted now: null when the holder is gone (broken, unloaded, out of its rack).
        @Nullable ItemStack mountedDrive(int slot);
    }

    private record Place(WeakReference<Mount> mount, int slot) {
        @Nullable ItemStack stack() {
            Mount holder = mount.get();
            return holder == null ? null : holder.mountedDrive(slot);
        }

        boolean is(Mount holder, int at) {
            return mount.get() == holder && slot == at;
        }
    }

    // Per server: each id's place, as last mounted.
    private static final Map<MinecraftServer, Map<UUID, Place>> MOUNTS = Collections.synchronizedMap(new WeakHashMap<>());

    private DriveCopies() {}

    // A holder has a drive in a slot: it gets its id the first time, or a new one (with a copy of the contents) when it's
    // a copy of a drive another holder still has mounted. Returns whether an id it already had changed (the holder has
    // to save it). Either way the drive stacks to 1 from then on.
    public static boolean mount(MinecraftServer server, ItemStack stack, Mount holder, int slot) {
        if (!(stack.getItem() instanceof StorageDriveItem)) {
            return false;
        }
        Map<UUID, Place> mounts = mounts(server);
        UUID id = StorageDriveItem.id(stack);
        boolean forked = false;
        if (id == null) {
            id = UUID.randomUUID();
            stack.set(ModDataComponents.DRIVE_ID.get(), id);
        } else {
            Place place = mounts.get(id);
            if (place != null && !place.is(holder, slot) && isTwin(place.stack(), stack, id)) {
                id = fork(server, stack);
                forked = true;
            }
        }
        unique(stack);
        mounts.put(id, new Place(new WeakReference<>(holder), slot));
        return forked;
    }

    // A creative player's inventory, cursor and open container: two drives with one id there are a drive and its copy
    // (or copies), and each copy gets an id of its own. A stack of one id's drives (a copy of a drive from before drives
    // with ids stacked to 1) is one drive.
    public static void creativeTick(ServerPlayer player) {
        Map<UUID, List<Held>> byId = new HashMap<>();
        Set<ItemStack> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Slot slot : player.containerMenu.slots) {
            collect(slot.getItem(), slot, byId, visited);
        }
        for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
            collect(stack, null, byId, visited);
        }
        collect(player.containerMenu.getCarried(), null, byId, visited);
        if (byId.isEmpty()) {
            return;
        }
        MinecraftServer server = player.level().getServer();
        Map<UUID, Place> mounts = mounts(server);
        byId.forEach((id, held) -> {
            if (held.size() < 2) {
                return;
            }
            // The mounted one keeps the id; with none mounted, the first.
            Place place = mounts.get(id);
            ItemStack mounted = place == null ? null : place.stack();
            ItemStack keeper = held.getFirst().stack();
            for (Held h : held) {
                if (h.stack() == mounted) {
                    keeper = h.stack();
                }
            }
            for (Held h : held) {
                if (h.stack() != keeper) {
                    fork(server, h.stack());
                    if (h.slot() != null) {
                        h.slot().setChanged();
                    }
                }
            }
        });
    }

    // A drive in a player's inventory that's empty again goes back to blank: no id, no cached fill, no charge, and it
    // stacks with new drives.
    public static void clearIfEmpty(MinecraftServer server, ItemStack stack) {
        UUID id = StorageDriveItem.id(stack);
        if (id == null || !(stack.getItem() instanceof StorageDriveItem drive)) {
            return;
        }
        if (drive.getType() == ResourceType.ENERGY) {
            if (EnergyDrives.stored(stack) > 0 || EnergyDrives.charging(id)) {
                return;
            }
        } else if (!DriveStorage.get(server).contents(id).isEmpty()) {
            return;
        }
        stack.remove(ModDataComponents.DRIVE_ID.get());
        stack.remove(ModDataComponents.DRIVE_STATS.get());
        stack.remove(ModDataComponents.DRIVE_ENERGY.get());
        // Back to the item's own stack size (removing it would leave a removal on the stack, which a new drive hasn't).
        stack.set(DataComponents.MAX_STACK_SIZE, stack.getPrototype().get(DataComponents.MAX_STACK_SIZE));
    }

    // A drive with an id is one of a kind.
    public static void unique(ItemStack stack) {
        if (stack.getOrDefault(DataComponents.MAX_STACK_SIZE, 0) != 1) {
            stack.set(DataComponents.MAX_STACK_SIZE, 1);
        }
    }

    private record Held(ItemStack stack, @Nullable Slot slot) {}

    private static void collect(ItemStack stack, @Nullable Slot slot, Map<UUID, List<Held>> byId, Set<ItemStack> visited) {
        if (!(stack.getItem() instanceof StorageDriveItem) || !visited.add(stack)) {
            return;
        }
        UUID id = StorageDriveItem.id(stack);
        if (id == null) {
            return;
        }
        if (stack.getCount() > 1) {
            stack.setCount(1);
            if (slot != null) {
                slot.setChanged();
            }
        }
        unique(stack);
        byId.computeIfAbsent(id, key -> new ArrayList<>(2)).add(new Held(stack, slot));
    }

    // Another stack, with this id, where the drive was last mounted.
    private static boolean isTwin(@Nullable ItemStack there, ItemStack stack, UUID id) {
        return there != null && there != stack && !there.isEmpty() && id.equals(StorageDriveItem.id(there));
    }

    // The copy gets a new id, with a copy of what's on the drive (an Energy Storage Drive's charge is on the item already).
    private static UUID fork(MinecraftServer server, ItemStack stack) {
        UUID from = StorageDriveItem.id(stack), to = UUID.randomUUID();
        if (from != null && StorageDriveItem.type(stack) != ResourceType.ENERGY) {
            DriveStorage.get(server).copy(from, to);
        }
        stack.set(ModDataComponents.DRIVE_ID.get(), to);
        unique(stack);
        return to;
    }

    private static Map<UUID, Place> mounts(MinecraftServer server) {
        return MOUNTS.computeIfAbsent(server, key -> new HashMap<>());
    }
}
