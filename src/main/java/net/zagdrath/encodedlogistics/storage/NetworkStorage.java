/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongConsumer;

import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.item.ResourceEntryItem;

// A network's storage as its parts see it: every drive in its online Drive Bays (and NAS / SAN devices) and every
// inventory its online Inventory Taps face - the hot tier. Items go in by priority (highest first; drives before taps
// on a tie), and within a priority to the places already holding that item first; they come out lowest priority first
// (taps before drives on a tie).
//
// It holds every keyed resource type (ResourceType.KEYED): items, fluids and pressurized gases, each in the drives and
// taps that take it, through the same paths. Energy is the network's energy pool, not storage.
//
// Behind it, the cold tier (ColdTier: tapes in Tape Libraries; items only). list() and count() are hot only; taking more of an item
// than is hot asks for the rest of it (what's on tape) to be recalled, and it can be taken once it's back.
//
// Crafting jobs waiting for outputs get first claim on items coming in (Claim): whatever path they arrive by (a Gateway, an
// Ingress Port, an Inventory Tap, a terminal), what a waiting run expects goes to its job instead of into storage. The
// mod's own moves (a finished job emptying out, a cancelled one's refund, a Gateway's stock trim) use store(), which
// skips that.
public final class NetworkStorage {
    // Takes what waiting jobs expect out of an insert; returns how many it took (or would, simulating).
    public interface Claim {
        Claim NONE = (key, amount, simulate) -> 0;

        long claim(StorageKey key, long amount, boolean simulate);
    }

    private final List<StorageView> fillOrder, emptyOrder;
    private final Claim claim;
    // Told how many items really went in or came out (not simulations; not fluids or gases): the network's item flow.
    private final LongConsumer moved;
    private final ColdTier cold;

    public NetworkStorage(List<StorageView> views) {
        this(views, count -> {}, ColdTier.NONE);
    }

    public NetworkStorage(List<StorageView> views, LongConsumer moved) {
        this(views, moved, ColdTier.NONE);
    }

    public NetworkStorage(List<StorageView> views, LongConsumer moved, ColdTier cold) {
        this(views, moved, cold, Claim.NONE);
    }

    public NetworkStorage(List<StorageView> views, LongConsumer moved, ColdTier cold, Claim claim) {
        this.moved = moved;
        this.cold = cold;
        this.claim = claim;
        // Shared storage (another segment's, through a Share route) comes after all of the network's own, both ways.
        fillOrder = new ArrayList<>(views);
        fillOrder.sort(Comparator.comparing(StorageView::isShared).thenComparing(Comparator.comparingInt(StorageView::priority).reversed())
                .thenComparing(StorageView::isTap));
        emptyOrder = new ArrayList<>(views);
        emptyOrder.sort(Comparator.comparing(StorageView::isShared).thenComparingInt(StorageView::priority).thenComparing(view -> !view.isTap()));
    }

    // Everything stored hot, added up.
    public Map<StorageKey, Long> list() {
        Map<StorageKey, Long> all = new LinkedHashMap<>();
        for (StorageView view : fillOrder) {
            view.listInto(all);
        }
        return all;
    }

    // Everything of one type stored hot.
    public Map<StorageKey, Long> list(ResourceType type) {
        Map<StorageKey, Long> all = list();
        all.keySet().removeIf(key -> !key.is(type));
        return all;
    }

    public long count(StorageKey key) {
        long count = 0;
        for (StorageView view : fillOrder) {
            count += view.count(key);
        }
        return count;
    }

    // How much of each item is shared in (SharedView), and from where (the first place sharing it).
    public record Shared(long count, Component from) {}

    public Map<StorageKey, Shared> shared() {
        Map<StorageKey, Shared> all = new LinkedHashMap<>();
        for (StorageView view : fillOrder) {
            if (!view.isShared()) {
                continue;
            }
            Map<StorageKey, Long> mine = new LinkedHashMap<>();
            view.listInto(mine);
            mine.forEach((key, count) -> all.merge(key, new Shared(count, view.sharedFrom()), (a, b) -> new Shared(a.count() + b.count(), a.from())));
        }
        return all;
    }

    // --- The cold tier ---

    // Its storage views, in fill order (the inventory report's locations).
    public List<StorageView> views() {
        return List.copyOf(fillOrder);
    }

    public ColdTier cold() {
        return cold;
    }

    // Everything on tape, added up.
    public Map<StorageKey, Long> coldList() {
        Map<StorageKey, Long> all = new LinkedHashMap<>();
        cold.listInto(all);
        return all;
    }

    // Hot and cold, added up (what terminals show).
    public Map<StorageKey, Long> listAll() {
        Map<StorageKey, Long> all = list();
        cold.listInto(all);
        return all;
    }

    // --- Moving items ---

    // Puts up to amount of an item into the network (waiting jobs taking what they expect first); returns how many went in.
    public long insert(StorageKey key, long amount, boolean simulate) {
        long claimed = amount > 0 ? Math.min(amount, claim.claim(key, amount, simulate)) : 0;
        return claimed + store(key, amount - claimed, simulate);
    }

    // Puts up to amount of an item into storage itself, with no job claiming any; returns how many went in.
    public long store(StorageKey key, long amount, boolean simulate) {
        if (amount <= 0 || key.isItem() && key.stack().getItem() instanceof ResourceEntryItem) {
            // A Resource Entry only ever stands for a fluid or gas; it's never stored as an item.
            return 0;
        }
        long left = amount;
        int start = 0;
        while (start < fillOrder.size() && left > 0) {
            int priority = fillOrder.get(start).priority(), end = start;
            boolean shared = fillOrder.get(start).isShared();
            while (end < fillOrder.size() && fillOrder.get(end).priority() == priority && fillOrder.get(end).isShared() == shared) {
                end++;
            }
            // Within a priority (shared storage on its own, after all of the network's): where it already is, then
            // anywhere else (each place once, so a simulation adds up).
            boolean[] holds = new boolean[end - start];
            for (int i = start; i < end; i++) {
                holds[i - start] = fillOrder.get(i).count(key) > 0;
            }
            for (int pass = 0; pass < 2 && left > 0; pass++) {
                for (int i = start; i < end && left > 0; i++) {
                    if (holds[i - start] == (pass == 0)) {
                        left -= fillOrder.get(i).insert(key, left, simulate);
                    }
                }
            }
            start = end;
        }
        if (!simulate && amount - left > 0 && key.isItem()) {
            moved.accept(amount - left);
        }
        return amount - left;
    }

    // Takes up to amount of an item out of the network; returns how many came out. What hot storage is short of is
    // recalled from tape, if any is there (not for a simulation).
    public long extract(StorageKey key, long amount, boolean simulate) {
        long left = amount;
        for (StorageView view : emptyOrder) {
            if (left <= 0) {
                break;
            }
            left -= view.extract(key, left, simulate);
        }
        if (!simulate && amount - left > 0 && key.isItem()) {
            moved.accept(amount - left);
        }
        if (!simulate && left > 0 && key.isItem()) {
            long onTape = cold.count(key);
            if (onTape > 0) {
                cold.recall(key, Math.min(left, onTape));
            }
        }
        return amount - left;
    }

    // --- The drives (what archiving works on) ---

    // Everything the item drives hold (not the taps' inventories, nor fluid or pressurized drives).
    public Map<StorageKey, Long> driveContents() {
        Map<StorageKey, Long> all = new LinkedHashMap<>();
        for (StorageView view : fillOrder) {
            if (view.driveId() != null && view.driveType() == ResourceType.ITEM) {
                view.listInto(all);
            }
        }
        return all;
    }

    // The tick an item was last put into or taken out of any drive holding it, or -1 when none does.
    public long lastAccess(StorageKey key) {
        long last = -1;
        for (StorageView view : fillOrder) {
            if (view.driveId() != null) {
                last = Math.max(last, view.lastAccess(key));
            }
        }
        return last;
    }

    // Takes up to amount of an item out of the drives only (archiving; no recall); returns how many came out.
    public long extractFromDrives(StorageKey key, long amount, boolean simulate) {
        long left = amount;
        for (StorageView view : emptyOrder) {
            if (left <= 0) {
                break;
            }
            if (view.driveId() != null) {
                left -= view.extract(key, left, simulate);
            }
        }
        return amount - left;
    }

    // How full the item drives are, 0-1, by bytes (0 with no drives): what archiving to tape goes by.
    public double hotFill() {
        long[] bytes = hotBytes();
        return bytes[1] <= 0 ? 0 : (double) bytes[0] / bytes[1];
    }

    // The item drives' bytes used and in all.
    public long[] hotBytes() {
        return hotBytes(ResourceType.ITEM);
    }

    // The bytes used and in all of the drives of one type.
    public long[] hotBytes(ResourceType type) {
        long used = 0, total = 0;
        for (StorageView view : fillOrder) {
            DriveStats stats = view.stats();
            if (stats != null && view.driveType() == type) {
                used += stats.bytesUsed();
                total += stats.bytesTotal();
            }
        }
        return new long[] { used, total };
    }
}
