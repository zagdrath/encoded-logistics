/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.item.LtoTapeItem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.part.PartFilter;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.StorageDevice;
import net.zagdrath.encodedlogistics.rack.TapePicker;
import net.zagdrath.encodedlogistics.rack.TapeRecalls;
import net.zagdrath.encodedlogistics.rack.TapeSource;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.registry.ModSounds;
import net.zagdrath.encodedlogistics.storage.DriveStats;
import net.zagdrath.encodedlogistics.storage.DriveStorage;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.TapeGeneration;

// A Tape Library (4U: 24 tape slots, 2 drive bays; 6U: 48 and 4): the network's cold storage. Its tapes (LTO tapes, in
// DriveStorage like drives) are on the network's cold tier while it's online, whether or not it has a drive; reading or
// writing one takes a drive (an LTO Tape Drive in a bay).
//
// Archiving (every tapeArchiveInterval ticks, with an idle drive): the drives' item types untouched for longer than its
// age setting - not matching its keep-hot filter, not pinned - while hot storage is fuller than its trigger (if that's on),
// oldest first, one type a drive. Of several libraries on a network the one with the most free tape space archives. A
// type goes on the tape already holding it, else the fullest with room (packing tapes).
//
// Recalls (TapeRecalls): the oldest waiting one for an item on its tapes, a tape a drive. Pinned items found on tape are
// recalled by themselves.
//
// Either way the picker loads the tape into the drive (TapePicker), the drive works (tapeBaseTicks + tapeTicksPer4k a
// 4,096 items, 15% faster each generation past LTO-6), the items move, and the picker puts the tape back.
public class TapeLibraryDevice extends RackDevice implements TapeSource {
    public static final int KEEP = PartFilter.SIZE, PINNED = 9;
    public static final int UNIT_MINUTES = 0, UNIT_HOURS = 1, UNIT_DAYS = 2;
    private static final long[] UNIT_TICKS = { 1_200, 72_000, 1_728_000 };
    public static final int ACTION_SET_AGE = 0, ACTION_CYCLE_UNIT = 1, ACTION_SET_PERCENT = 2, ACTION_TOGGLE_TRIGGER = 3, ACTION_SET_KEEP = 4,
            ACTION_SET_PINNED = 5, ACTION_SET_FUZZY = 6;
    // A drive's state, as the panel and the rear show it.
    public static final int DRIVE_NONE = -1, DRIVE_IDLE = 0, DRIVE_READING = 1, DRIVE_WRITING = 2;
    private static final int ASSIGN_INTERVAL = 10;

    // A tape on a drive: reading (a recall) or writing (archiving) one item type.
    private static final class Op {
        static final int WAIT_LOAD = 0, LOADING = 1, WORKING = 2, WAIT_UNLOAD = 3, UNLOADING = 4;
        final boolean write;
        final int slot;
        final UUID tape;
        final ItemKey key;
        final long amount;
        // Writing: the item's last access when it was picked (touched since, it stays hot).
        final long touched;
        int phase, done, work;

        Op(boolean write, int slot, UUID tape, ItemKey key, long amount, long touched, int work) {
            this.write = write;
            this.slot = slot;
            this.tape = tape;
            this.key = key;
            this.amount = amount;
            this.touched = touched;
            this.work = work;
        }
    }

    private final int tapes, bays;
    // Policy.
    private int age = Config.TAPE_DEFAULT_AGE_HOURS.getAsInt(), unit = UNIT_HOURS, percent = Config.TAPE_DEFAULT_HOT_PERCENT.getAsInt();
    private boolean trigger = true;
    private final PartFilter keepHot = new PartFilter();
    private final NonNullList<ItemStack> pinned = NonNullList.withSize(PINNED, ItemStack.EMPTY);
    // Work.
    private final @Nullable Op[] ops;
    private int pickerBay = -1;
    private long pickerStart;
    private boolean pickerLoad;
    private int archiveTimer, assignTimer, backlog, lastBusy;
    private final List<ItemKey> toArchive = new ArrayList<>();
    // Items sent to tape now (CHGITMTIER *COLD): archived next, whatever their age or the trigger.
    private final Set<ItemKey> forced = new LinkedHashSet<>();
    // Tests: the archive age in ticks, overriding the setting.
    private long ageTicksOverride = -1;

    // Client: per slot, the tape (generation * 8 + fill, -1 none); per bay, the drive state; the picker.
    private int[] shownTapes = new int[0], shownBays = new int[0], shownPicker = new int[0];

    public TapeLibraryDevice(RackDeviceType type, int tapes, int bays) {
        super(type);
        this.tapes = tapes;
        this.bays = bays;
        this.ops = new Op[bays];
    }

    public int tapeSlots() {
        return tapes;
    }

    public int bays() {
        return bays;
    }

    // Magazine rows of 12 on its front (the picker's).
    public int rows() {
        return tapes / 12;
    }

    public PartFilter keepHot() {
        return keepHot;
    }

    public NonNullList<ItemStack> pinned() {
        return pinned;
    }

    public int age() {
        return age;
    }

    public int unit() {
        return unit;
    }

    public int percent() {
        return percent;
    }

    public boolean trigger() {
        return trigger;
    }

    public long ageTicks() {
        return ageTicksOverride >= 0 ? ageTicksOverride : age * UNIT_TICKS[unit];
    }

    public void setAgeTicksForTest(long ticks) {
        ageTicksOverride = ticks;
    }

    // Looks for items to archive on its next tick (tests: instead of waiting for tapeArchiveInterval).
    // Archives an item on the next go, whatever its age and the free-space trigger (not saved: a restart forgets it).
    public void archiveNow(ItemKey key) {
        forced.add(key);
        if (!toArchive.contains(key)) {
            toArchive.addFirst(key);
        }
    }

    public void scanSoon() {
        archiveTimer = Integer.MAX_VALUE - 1;
    }

    public void setTrigger(boolean on, int percent) {
        this.trigger = on;
        this.percent = Mth.clamp(percent, 0, 100);
    }

    public boolean hasDrive(int bay) {
        return items().get(tapes + bay).is(ModItems.LTO_TAPE_DRIVE.get());
    }

    @Override
    public int driveCount() {
        int count = 0;
        for (int bay = 0; bay < bays; bay++) {
            if (hasDrive(bay)) {
                count++;
            }
        }
        return count;
    }

    public int busyDrives() {
        int count = 0;
        for (Op op : ops) {
            if (op != null) {
                count++;
            }
        }
        return count;
    }

    public int tapeCount() {
        int count = 0;
        for (int slot = 0; slot < tapes; slot++) {
            if (items().get(slot).getItem() instanceof LtoTapeItem) {
                count++;
            }
        }
        return count;
    }

    // Its base, and for each busy drive the drive's and (as it does those drives' moves) the picker's. (The network
    // takes it up when the number of busy drives changes.)
    @Override
    public double drain() {
        double base = type() == RackDeviceType.TAPE_LIBRARY_6U ? Config.TAPE_LIBRARY_6U_DRAIN.getAsDouble() : Config.TAPE_LIBRARY_4U_DRAIN.getAsDouble();
        int busy = busyDrives();
        return base + busy * Config.TAPE_DRIVE_BUSY_DRAIN.getAsDouble() + (busy > 0 ? Config.TAPE_PICKER_DRAIN.getAsDouble() : 0);
    }

    // --- Its tapes ---

    public record Tape(int slot, UUID id, TapeGeneration generation) {}

    // The tapes in its slots, ids given to any new one.
    public List<Tape> tapes() {
        List<Tape> list = new ArrayList<>();
        for (int slot = 0; slot < tapes; slot++) {
            ItemStack stack = items().get(slot);
            if (stack.getItem() instanceof LtoTapeItem tape) {
                list.add(new Tape(slot, assignId(stack), tape.generation()));
            }
        }
        return list;
    }

    private static UUID assignId(ItemStack stack) {
        UUID id = LtoTapeItem.id(stack);
        if (id == null) {
            id = UUID.randomUUID();
            stack.set(ModDataComponents.DRIVE_ID.get(), id);
        }
        return id;
    }

    private void refresh(MinecraftServer server, int slot) {
        ItemStack stack = items().get(slot);
        if (stack.getItem() instanceof LtoTapeItem tape) {
            DriveStats stats = DriveStorage.get(server).stats(assignId(stack), tape.generation());
            if (!stats.equals(stack.get(ModDataComponents.DRIVE_STATS.get()))) {
                stack.set(ModDataComponents.DRIVE_STATS.get(), stats);
            }
        }
    }

    // Bytes used and in all on its tapes.
    public long[] coldBytes() {
        long used = 0, total = 0;
        for (int slot = 0; slot < tapes; slot++) {
            ItemStack stack = items().get(slot);
            if (stack.getItem() instanceof LtoTapeItem) {
                DriveStats stats = LtoTapeItem.stats(stack);
                used += stats.bytesUsed();
                total += stats.bytesTotal();
            }
        }
        return new long[] { used, total };
    }

    // How many of an item are on its tapes.
    public long count(DriveStorage data, ItemKey key) {
        long count = 0;
        for (Tape tape : tapes()) {
            count += data.count(tape.id(), key);
        }
        return count;
    }

    // Its keep-hot or pinned list was changed from outside its panel (CHGITMTIER): saved and synced.
    public void policyChanged() {
        changed(false);
    }

    @Override
    public void itemsChanged() {
        if (rack() != null && rack().getLevel() instanceof ServerLevel level) {
            for (int slot = 0; slot < tapes; slot++) {
                refresh(level.getServer(), slot);
            }
        }
        changed(true);
    }

    // --- Working ---

    private @Nullable TapeRecalls recalls(MinecraftServer server) {
        return rack() != null ? ControllerStructures.recalls(server, rack().network(this)) : null;
    }

    private @Nullable NetworkStorage storage(MinecraftServer server) {
        return rack() != null ? ControllerStructures.storageOf(server, rack().network(this)) : null;
    }

    // Ticks a drive takes to read or write amount items on a tape of that generation.
    public static int workTicks(TapeGeneration generation, long amount) {
        double ticks = Config.TAPE_BASE_TICKS.getAsInt() + Config.TAPE_TICKS_PER_4K.getAsInt() * (amount / 4096.0);
        return Math.max(1, (int) Math.round(ticks * generation.speed()));
    }

    @Override
    public List<UUID> tapeIds() {
        return tapes().stream().map(Tape::id).toList();
    }

    // The first tape holding the item: the picker's trip to it and the read.
    @Override
    public int recallTicks(DriveStorage data, ItemKey key, long amount) {
        for (Tape tape : tapes()) {
            if (data.count(tape.id(), key) > 0) {
                return loadTicks(tape.slot()) + workTicks(tape.generation(), amount);
            }
        }
        return -1;
    }

    public int loadTicks(int slot) {
        return TapePicker.ticks(rows(), slot, true);
    }

    @Override
    public void tick(ServerLevel level) {
        MinecraftServer server = level.getServer();
        TapeRecalls recalls = recalls(server);
        if (!isOnline() || recalls == null) {
            if (busyDrives() > 0) {
                abortAll(recalls);
            }
            return;
        }
        boolean changed = checkOps(recalls);
        changed |= runPicker(level, recalls);
        changed |= work(server, recalls);
        if (++assignTimer >= ASSIGN_INTERVAL) {
            assignTimer = 0;
            requestPinned(server, recalls);
            changed |= assign(server, recalls);
        }
        if (++archiveTimer >= Config.TAPE_ARCHIVE_INTERVAL.getAsInt()) {
            archiveTimer = 0;
            scanForArchive(server, recalls);
        }
        int busy = busyDrives();
        if (busy != lastBusy) {
            lastBusy = busy;
            changed(true);
        } else if (changed) {
            changed(false);
        } else if (busy > 0) {
            saveOnly();
        }
    }

    // Drops ops whose tape or drive is gone, and keeps the network's claims for the rest (after a reload).
    private boolean checkOps(TapeRecalls recalls) {
        boolean changed = false;
        for (int bay = 0; bay < bays; bay++) {
            Op op = ops[bay];
            if (op == null) {
                continue;
            }
            ItemStack stack = items().get(op.slot);
            if (!hasDrive(bay) || !(stack.getItem() instanceof LtoTapeItem) || !op.tape.equals(LtoTapeItem.id(stack))) {
                drop(bay, recalls);
                changed = true;
            } else if (op.write) {
                recalls.startArchiving(op.key);
            } else if (recalls.running(op.key) == null) {
                recalls.claim(op.key, op.amount, loadTicks(op.slot) + op.work);
            }
        }
        return changed;
    }

    private void drop(int bay, @Nullable TapeRecalls recalls) {
        Op op = ops[bay];
        if (op == null) {
            return;
        }
        if (recalls != null) {
            if (op.write) {
                recalls.stopArchiving(op.key);
            } else {
                recalls.finish(op.key, false);
                // Still wanted: back in the queue.
                recalls.request(op.key, op.amount);
            }
        }
        if (pickerBay == bay) {
            pickerBay = -1;
        }
        ops[bay] = null;
    }

    private void abortAll(@Nullable TapeRecalls recalls) {
        for (int bay = 0; bay < bays; bay++) {
            drop(bay, recalls);
        }
        changed(false);
    }

    // The picker: one move at a time, loads and unloads in bay order.
    private boolean runPicker(ServerLevel level, TapeRecalls recalls) {
        long now = level.getGameTime();
        if (pickerBay < 0) {
            for (int bay = 0; bay < bays; bay++) {
                Op op = ops[bay];
                if (op != null && (op.phase == Op.WAIT_LOAD || op.phase == Op.WAIT_UNLOAD)) {
                    pickerBay = bay;
                    pickerStart = now;
                    pickerLoad = op.phase == Op.WAIT_LOAD;
                    op.phase = pickerLoad ? Op.LOADING : Op.UNLOADING;
                    return true;
                }
            }
            return false;
        }
        Op op = ops[pickerBay];
        if (op == null) {
            pickerBay = -1;
            return true;
        }
        int elapsed = (int) (now - pickerStart);
        List<TapePicker.Leg> legs = TapePicker.path(rows(), op.slot, pickerLoad);
        int at = 0;
        for (TapePicker.Leg leg : legs) {
            if (elapsed == at && rack() != null) {
                boolean load = leg.kind() != TapePicker.Kind.MOVE;
                level.playSound(null, rack().getBlockPos(), (load ? ModSounds.RACK_TAPE_LOAD : ModSounds.RACK_PICKER_MOVE).value(), SoundSource.BLOCKS,
                        0.35F, 0.9F + level.getRandom().nextFloat() * 0.2F);
            }
            at += leg.ticks();
        }
        if (pickerLoad && !op.write) {
            recalls.progress(op.key, elapsed, at + op.work);
        }
        if (elapsed < at) {
            return false;
        }
        if (pickerLoad) {
            op.phase = Op.WORKING;
        } else {
            ops[pickerBay] = null;
        }
        pickerBay = -1;
        return true;
    }

    // Drives working: when one's done, its items move.
    private boolean work(MinecraftServer server, TapeRecalls recalls) {
        boolean changed = false;
        for (int bay = 0; bay < bays; bay++) {
            Op op = ops[bay];
            if (op == null || op.phase != Op.WORKING) {
                continue;
            }
            op.done++;
            if (!op.write) {
                recalls.progress(op.key, loadTicks(op.slot) + op.done, loadTicks(op.slot) + op.work);
            }
            if (op.done < op.work) {
                continue;
            }
            if (op.write) {
                finishWrite(server, op);
                recalls.stopArchiving(op.key);
            } else {
                recalls.finish(op.key, !finishRead(server, op));
            }
            refresh(server, op.slot);
            op.phase = Op.WAIT_UNLOAD;
            changed = true;
        }
        return changed;
    }

    // Archiving: the item moves from the drives to the tape, unless it was touched while the tape was loading.
    private void finishWrite(MinecraftServer server, Op op) {
        NetworkStorage storage = storage(server);
        if (storage == null || storage.lastAccess(op.key) > op.touched) {
            return;
        }
        DriveStorage data = DriveStorage.get(server);
        TapeGeneration generation = ((LtoTapeItem) items().get(op.slot).getItem()).generation();
        long amount = Math.min(Math.min(op.amount, data.room(op.tape, generation, op.key)), storage.extractFromDrives(op.key, op.amount, true));
        long taken = storage.extractFromDrives(op.key, amount, false);
        long written = data.insert(op.tape, generation, op.key, taken, false);
        if (written < taken) {
            storage.insert(op.key, taken - written, false);
        }
    }

    // A recall: the items move from the tape into hot storage, as many as fit; false when not all of them did.
    private boolean finishRead(MinecraftServer server, Op op) {
        NetworkStorage storage = storage(server);
        if (storage == null) {
            return false;
        }
        DriveStorage data = DriveStorage.get(server);
        long wanted = Math.min(op.amount, data.count(op.tape, op.key));
        long fits = storage.insert(op.key, wanted, true);
        long read = data.extract(op.tape, op.key, fits, false);
        long put = storage.insert(op.key, read, false);
        if (put < read) {
            data.insert(op.tape, ((LtoTapeItem) items().get(op.slot).getItem()).generation(), op.key, read - put, false);
        }
        return put >= wanted;
    }

    private boolean idleBay(int bay) {
        return ops[bay] == null && hasDrive(bay);
    }

    private boolean anyIdleBay() {
        for (int bay = 0; bay < bays; bay++) {
            if (idleBay(bay)) {
                return true;
            }
        }
        return false;
    }

    private Set<Integer> slotsInUse() {
        Set<Integer> used = new HashSet<>();
        for (Op op : ops) {
            if (op != null) {
                used.add(op.slot);
            }
        }
        return used;
    }

    // Gives idle drives work: waiting recalls it can serve first, then items to archive.
    private boolean assign(MinecraftServer server, TapeRecalls recalls) {
        boolean changed = false;
        DriveStorage data = DriveStorage.get(server);
        for (int bay = 0; bay < bays; bay++) {
            if (!idleBay(bay)) {
                continue;
            }
            Op op = nextRecall(data, recalls);
            if (op == null) {
                op = nextArchive(server, data, recalls);
            }
            if (op != null) {
                ops[bay] = op;
                changed = true;
            }
        }
        return changed;
    }

    private @Nullable Op nextRecall(DriveStorage data, TapeRecalls recalls) {
        Set<Integer> used = slotsInUse();
        for (ItemKey key : recalls.waiting()) {
            if (recalls.busy(key)) {
                continue;
            }
            // The tape holding the most of it.
            Tape best = null;
            long most = 0;
            for (Tape tape : tapes()) {
                long count = data.count(tape.id(), key);
                if (count > most && !used.contains(tape.slot())) {
                    best = tape;
                    most = count;
                }
            }
            if (best == null) {
                continue;
            }
            long amount = Math.min(recalls.waitingAmount(key), most);
            int work = workTicks(best.generation(), amount);
            recalls.claim(key, amount, loadTicks(best.slot()) + work);
            return new Op(false, best.slot(), best.id(), key, amount, -1, work);
        }
        return null;
    }

    private @Nullable Op nextArchive(MinecraftServer server, DriveStorage data, TapeRecalls recalls) {
        NetworkStorage storage = storage(server);
        if (storage == null) {
            return null;
        }
        Set<Integer> used = slotsInUse();
        while (!toArchive.isEmpty()) {
            ItemKey key = toArchive.removeFirst();
            backlog = toArchive.size();
            long last = storage.lastAccess(key);
            long amount = storage.extractFromDrives(key, Long.MAX_VALUE, true);
            boolean force = forced.remove(key);
            if (last < 0 || amount <= 0 || !force && !archivable(key, last, data.clock()) || recalls.busy(key)) {
                continue;
            }
            Tape tape = tapeFor(data, key, used);
            if (tape == null || !recalls.startArchiving(key)) {
                continue;
            }
            amount = Math.min(amount, data.room(tape.id(), tape.generation(), key));
            return new Op(true, tape.slot(), tape.id(), key, amount, last, workTicks(tape.generation(), amount));
        }
        return null;
    }

    // Where an item goes: a tape already holding it with room, else the fullest with room.
    private @Nullable Tape tapeFor(DriveStorage data, ItemKey key, Set<Integer> used) {
        Tape best = null;
        long bestUsed = -1;
        for (Tape tape : tapes()) {
            if (used.contains(tape.slot()) || data.room(tape.id(), tape.generation(), key) <= 0) {
                continue;
            }
            if (data.count(tape.id(), key) > 0) {
                return tape;
            }
            long bytes = data.stats(tape.id(), tape.generation()).bytesUsed();
            if (bytes > bestUsed) {
                best = tape;
                bestUsed = bytes;
            }
        }
        return best;
    }

    private boolean archivable(ItemKey key, long lastAccess, long now) {
        return now - lastAccess >= ageTicks() && !keepHot.test(key.stack(), true, false, true) && !isPinned(key);
    }

    public boolean isPinned(ItemKey key) {
        for (ItemStack stack : pinned) {
            if (!stack.isEmpty() && ItemKey.of(stack).equals(key)) {
                return true;
            }
        }
        return false;
    }

    // Lists what's due for archiving, oldest first, if this library should archive now.
    private void scanForArchive(MinecraftServer server, TapeRecalls recalls) {
        toArchive.clear();
        toArchive.addAll(forced);
        backlog = toArchive.size();
        NetworkStorage storage = storage(server);
        if (storage == null || !anyIdleBay() || tapeCount() == 0 || trigger && storage.hotFill() * 100 < percent || !mostFreeSpace(server)) {
            return;
        }
        long now = DriveStorage.get(server).clock();
        List<Map.Entry<ItemKey, Long>> due = new ArrayList<>();
        for (ItemKey key : storage.driveContents().keySet()) {
            long last = storage.lastAccess(key);
            if (last >= 0 && archivable(key, last, now) && !recalls.busy(key)) {
                due.add(Map.entry(key, last));
            }
        }
        due.sort(Map.Entry.comparingByValue());
        due.forEach(entry -> {
            if (!forced.contains(entry.getKey())) {
                toArchive.add(entry.getKey());
            }
        });
        backlog = toArchive.size();
    }

    // Of the libraries on its network with an idle drive, whether it has the most free tape space (the first such, on a
    // tie).
    private boolean mostFreeSpace(MinecraftServer server) {
        long mine = coldBytes()[1] - coldBytes()[0];
        for (RackDevice device : ControllerStructures.rackDevicesServing(server, rack().network(this))) {
            if (device != this && device instanceof TapeLibraryDevice other && other.isOnline() && other.anyIdleBay()) {
                long theirs = other.coldBytes()[1] - other.coldBytes()[0];
                if (theirs > mine) {
                    return false;
                }
            }
        }
        return true;
    }

    // Pinned items found on its tapes come back by themselves.
    private void requestPinned(MinecraftServer server, TapeRecalls recalls) {
        DriveStorage data = DriveStorage.get(server);
        for (ItemStack stack : pinned) {
            if (!stack.isEmpty()) {
                ItemKey key = ItemKey.of(stack);
                long count = count(data, key);
                if (count > 0) {
                    recalls.request(key, count);
                }
            }
        }
    }

    @Override
    public void onRemoved() {
        if (rack() != null && rack().getLevel() instanceof ServerLevel level) {
            TapeRecalls recalls = recalls(level.getServer());
            for (int bay = 0; bay < bays; bay++) {
                drop(bay, recalls);
            }
        }
    }

    @Override
    protected void onlineChanged() {
        toArchive.clear();
    }

    // --- What it shows ---

    // What it's doing: recalling an item, archiving some, or idle.
    public Component activity() {
        for (Op op : ops) {
            if (op != null && !op.write) {
                return Component.translatable("gui.encodedlogistics.tape.activity.recalling",
                        Component.empty().append(op.key.stack().getHoverName()).append(String.format(Locale.ROOT, " x%,d", op.amount)));
            }
        }
        int writing = 0;
        for (Op op : ops) {
            if (op != null && op.write) {
                writing++;
            }
        }
        if (writing + backlog > 0) {
            return Component.translatable("gui.encodedlogistics.tape.activity.archiving", writing + backlog);
        }
        return Component.translatable("gui.encodedlogistics.tape.activity.idle");
    }

    // The panel's activity lines: what it's recalling, what's being archived and how many more are due; idle else.
    public List<Component> activityLines() {
        List<Component> lines = new ArrayList<>();
        int writing = 0;
        for (Op op : ops) {
            if (op != null && !op.write) {
                lines.add(Component.translatable("gui.encodedlogistics.tape.activity.recalling",
                        Component.empty().append(op.key.stack().getHoverName()).append(String.format(Locale.ROOT, " x%,d", op.amount))));
            } else if (op != null) {
                writing++;
            }
        }
        if (writing + backlog > 0) {
            lines.add(Component.translatable("gui.encodedlogistics.tape.activity.archiving", writing + backlog));
        }
        if (lines.isEmpty()) {
            lines.add(Component.translatable(driveCount() == 0 ? "gui.encodedlogistics.tape.drive.empty" : "gui.encodedlogistics.tape.activity.idle"));
        }
        return lines;
    }

    private int driveState(int bay) {
        if (!hasDrive(bay)) {
            return DRIVE_NONE;
        }
        Op op = ops[bay];
        return op == null || op.phase != Op.WORKING ? DRIVE_IDLE : op.write ? DRIVE_WRITING : DRIVE_READING;
    }

    @Override
    public Component statusText() {
        if (status() != RackDeviceInfo.Status.ONLINE) {
            return super.statusText();
        }
        if (driveCount() == 0) {
            return Component.translatable("gui.encodedlogistics.tape.drive.empty");
        }
        return Component.translatable(busyDrives() == 0 ? "hud.encodedlogistics.tape.idle" : hasRecall() ? "hud.encodedlogistics.tape.recalling"
                : "hud.encodedlogistics.tape.archiving");
    }

    private boolean hasRecall() {
        for (Op op : ops) {
            if (op != null && !op.write) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        long[] cold = coldBytes();
        float fraction = cold[1] <= 0 ? 0 : (float) cold[0] / cold[1];
        return List.of(
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.tape.tapes"), Component.literal(tapeCount() + " / " + tapes)),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.tape.cold"),
                        Component.translatable("gui.encodedlogistics.storage.capacity", StorageDevice.bytes(cold[0]), StorageDevice.bytes(cold[1])),
                        new RackDeviceInfo.Bar(fraction, fraction > 0.95F ? RackDeviceInfo.BarStyle.LOW : RackDeviceInfo.BarStyle.NORMAL)),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.tape.drives"), Component.literal(busyDrives() + " / " + driveCount())),
                new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.tape.activity"), activity()));
    }

    // --- Panel ---

    @Override
    public void handleAction(ServerPlayer player, int action, int value, String text) {
        switch (action) {
            case ACTION_SET_AGE -> age = Mth.clamp(value, 1, 9_999);
            case ACTION_CYCLE_UNIT -> unit = (unit + 1) % UNIT_TICKS.length;
            case ACTION_SET_PERCENT -> percent = Mth.clamp(value, 0, 100);
            case ACTION_TOGGLE_TRIGGER -> trigger = !trigger;
            case ACTION_SET_KEEP -> {
                if (value < 0 || value >= KEEP) {
                    return;
                }
                keepHot.set(value, filter(player, text));
            }
            case ACTION_SET_PINNED -> {
                if (value < 0 || value >= PINNED) {
                    return;
                }
                pinned.set(value, filter(player, text));
            }
            case ACTION_SET_FUZZY -> keepHot.setFuzzy(value >> 8, value & 0xFF);
            default -> {
                return;
            }
        }
        changed(false);
    }

    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        save(output);
        long[] cold = coldBytes();
        output.putLong("cold_used", cold[0]);
        output.putLong("cold_total", cold[1]);
        output.store("activity", ComponentSerialization.CODEC.listOf(), activityLines());
        int[] drives = new int[bays];
        for (int bay = 0; bay < bays; bay++) {
            drives[bay] = driveState(bay);
        }
        output.putIntArray("drives", drives);
        NetworkStorage storage = storage(viewer.level().getServer());
        output.putInt("hot_percent", storage != null ? (int) Math.round(storage.hotFill() * 100) : 0);
        int[] fuzzy = new int[KEEP];
        for (int i = 0; i < KEEP; i++) {
            fuzzy[i] = keepHot.fuzzy(i);
        }
        output.putIntArray("fuzzy", fuzzy);
    }

    // --- Saving ---

    @Override
    public void saveSettings(ValueOutput output) {
        output.putInt("age", age);
        output.putInt("unit", unit);
        output.putInt("percent", percent);
        output.putBoolean("trigger", trigger);
        keepHot.save(output.child("keep_hot"));
        ValueOutput.ValueOutputList list = output.childrenList("pinned");
        for (int i = 0; i < PINNED; i++) {
            if (!pinned.get(i).isEmpty()) {
                ValueOutput child = list.addChild();
                child.putInt("slot", i);
                child.store("item", ItemStack.CODEC, pinned.get(i));
            }
        }
    }

    @Override
    public void loadSettings(ValueInput input) {
        age = Mth.clamp(input.getIntOr("age", Config.TAPE_DEFAULT_AGE_HOURS.getAsInt()), 1, 9_999);
        unit = Mth.clamp(input.getIntOr("unit", UNIT_HOURS), 0, UNIT_TICKS.length - 1);
        percent = Mth.clamp(input.getIntOr("percent", Config.TAPE_DEFAULT_HOT_PERCENT.getAsInt()), 0, 100);
        trigger = input.getBooleanOr("trigger", true);
        keepHot.load(input.childOrEmpty("keep_hot"));
        pinned.replaceAll(stack -> ItemStack.EMPTY);
        for (ValueInput child : input.childrenListOrEmpty("pinned")) {
            int slot = child.getIntOr("slot", -1);
            if (slot >= 0 && slot < PINNED) {
                pinned.set(slot, child.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
            }
        }
    }

    @Override
    public void save(ValueOutput output) {
        saveSettings(output);
        saveItems(output);
        ValueOutput.ValueOutputList list = output.childrenList("ops");
        for (int bay = 0; bay < bays; bay++) {
            Op op = ops[bay];
            if (op != null) {
                ValueOutput child = list.addChild();
                child.putInt("bay", bay);
                child.putBoolean("write", op.write);
                child.putInt("slot", op.slot);
                child.store("tape", UUIDUtil.CODEC, op.tape);
                child.store("item", ItemKey.CODEC, op.key);
                child.putLong("amount", op.amount);
                child.putLong("touched", op.touched);
                // A tape mid-move restarts its move.
                child.putInt("phase", op.phase == Op.LOADING ? Op.WAIT_LOAD : op.phase == Op.UNLOADING ? Op.WAIT_UNLOAD : op.phase);
                child.putInt("done", op.done);
                child.putInt("work", op.work);
            }
        }
    }

    @Override
    public void load(ValueInput input) {
        loadSettings(input);
        loadItems(input);
        for (int bay = 0; bay < bays; bay++) {
            ops[bay] = null;
        }
        pickerBay = -1;
        for (ValueInput child : input.childrenListOrEmpty("ops")) {
            int bay = child.getIntOr("bay", -1);
            Optional<UUID> tape = child.read("tape", UUIDUtil.CODEC);
            Optional<ItemKey> key = child.read("item", ItemKey.CODEC);
            int slot = child.getIntOr("slot", -1);
            if (bay < 0 || bay >= bays || tape.isEmpty() || key.isEmpty() || slot < 0 || slot >= tapes) {
                continue;
            }
            Op op = new Op(child.getBooleanOr("write", false), slot, tape.get(), key.get(), child.getLongOr("amount", 0), child.getLongOr("touched", -1),
                    child.getIntOr("work", 1));
            op.phase = Mth.clamp(child.getIntOr("phase", Op.WAIT_LOAD), Op.WAIT_LOAD, Op.WAIT_UNLOAD);
            op.done = child.getIntOr("done", 0);
            ops[bay] = op;
        }
    }

    // --- Client ---

    @Override
    public void writeClient(ValueOutput output) {
        int[] shown = new int[tapes];
        for (int slot = 0; slot < tapes; slot++) {
            ItemStack stack = items().get(slot);
            shown[slot] = stack.getItem() instanceof LtoTapeItem tape ? tape.generation().ordinal() * 8 + fill(LtoTapeItem.stats(stack)) : -1;
        }
        output.putIntArray("tapes", shown);
        // Per bay: state + 1 (0 no drive), and the slot of the tape in it + 1 (0 none).
        int[] drives = new int[bays * 2];
        for (int bay = 0; bay < bays; bay++) {
            drives[bay * 2] = driveState(bay) + 1;
            Op op = ops[bay];
            drives[bay * 2 + 1] = op != null && (op.phase == Op.WORKING || op.phase == Op.WAIT_UNLOAD || op.phase == Op.UNLOADING) ? op.slot + 1 : 0;
        }
        output.putIntArray("bays", drives);
        Op moving = pickerBay >= 0 ? ops[pickerBay] : null;
        output.putIntArray("picker", moving != null
                ? new int[] { moving.slot, pickerLoad ? 1 : 0, (int) pickerStart, (int) (pickerStart >>> 32), pickerBay } : new int[0]);
    }

    // A tape's fill bar: 0 under half, 1 to 75%, 2 below full, 3 full, 4 empty.
    public static int fill(DriveStats stats) {
        return stats.bytesUsed() <= 0 ? 4 : stats.light();
    }

    @Override
    public void readClient(ValueInput input) {
        shownTapes = input.getIntArray("tapes").orElse(new int[0]);
        shownBays = input.getIntArray("bays").orElse(new int[0]);
        shownPicker = input.getIntArray("picker").orElse(new int[0]);
    }

    // Client: a slot's tape (generation * 8 + fill), or -1.
    public int shownTape(int slot) {
        return slot < shownTapes.length ? shownTapes[slot] : -1;
    }

    // Client: a bay's drive state (DRIVE_*).
    public int shownDrive(int bay) {
        return bay * 2 < shownBays.length ? shownBays[bay * 2] - 1 : DRIVE_NONE;
    }

    // Client: the slot of the tape in a bay's drive, or -1.
    public int shownDriveTape(int bay) {
        return bay * 2 + 1 < shownBays.length ? shownBays[bay * 2 + 1] - 1 : -1;
    }

    // Client: the picker's move - slot, load (1) or unload (0), start tick (low, high), bay - or empty while it's parked.
    public int[] shownPicker() {
        return shownPicker;
    }
}
