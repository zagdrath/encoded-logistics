/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.TransferPreconditions;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.multiblock.EnergyCell;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// Energy Storage Drives: FE kept on the drive item itself (drive_energy), so a drive keeps its charge out of a holder and
// works as a portable battery (Handler, its item energy capability). In any drive holder on a network it is part of the
// network's energy pool (ControllerStructures: drives are pool capacity, drained and filled like Capacitor Banks).
//
// Capacity follows the drives' byte model: 1,000 FE a byte (ResourceType.ENERGY), so an 8K drive holds 8,192,000 FE.
public final class EnergyDrives {
    // The drives (by id) whose charge rose over their network's last tick: their lights show charging. Server side.
    private static final Set<UUID> CHARGING = ConcurrentHashMap.newKeySet();

    private EnergyDrives() {}

    public static boolean charging(@Nullable UUID drive) {
        return drive != null && CHARGING.contains(drive);
    }

    // A drive left its network: it isn't charging any more.
    public static void forget(UUID drive) {
        CHARGING.remove(drive);
    }

    public static boolean is(ItemStack stack) {
        return stack.getItem() instanceof StorageDriveItem drive && drive.getType() == ResourceType.ENERGY;
    }

    public static long capacity(StorageTier tier) {
        return tier.bytes() * ResourceType.ENERGY.unitsPerByte();
    }

    public static long capacity(ItemStack stack) {
        return stack.getItem() instanceof StorageDriveItem drive && drive.getType() == ResourceType.ENERGY ? capacity(drive.getTier()) : 0;
    }

    public static long stored(ItemStack stack) {
        return Math.max(0, Math.min(capacity(stack), stack.getOrDefault(ModDataComponents.DRIVE_ENERGY.get(), 0L)));
    }

    public static void set(ItemStack stack, long energy) {
        stack.set(ModDataComponents.DRIVE_ENERGY.get(), Math.max(0, Math.min(capacity(stack), energy)));
    }

    // Puts up to amount in; returns how much fit.
    public static long fill(ItemStack stack, long amount, boolean simulate) {
        long stored = stored(stack), added = Math.max(0, Math.min(amount, capacity(stack) - stored));
        if (added > 0 && !simulate) {
            set(stack, stored + added);
        }
        return added;
    }

    // Takes up to amount out; returns how much it had.
    public static long drain(ItemStack stack, long amount, boolean simulate) {
        long stored = stored(stack), taken = Math.max(0, Math.min(amount, stored));
        if (taken > 0 && !simulate) {
            set(stack, stored - taken);
        }
        return taken;
    }

    // As the drive's stats: bytes of charge (1,000 FE each) out of the tier's bytes, for the fill bars and tooltips.
    public static DriveStats stats(ItemStack stack) {
        if (!(stack.getItem() instanceof StorageDriveItem drive)) {
            return DriveStats.empty(StorageTier.K8);
        }
        long perByte = ResourceType.ENERGY.unitsPerByte();
        return new DriveStats((stored(stack) + perByte - 1) / perByte, drive.getTier().bytes(), 0);
    }

    // The charge light: 0 full, 1 at least 75%, 2 at least 50%, 3 at least 25% (amber), 4 below that (off).
    public static int chargeLight(ItemStack stack) {
        long capacity = capacity(stack);
        double charge = capacity <= 0 ? 0 : (double) stored(stack) / capacity;
        return charge >= 1 ? 0 : charge >= 0.75 ? 1 : charge >= 0.5 ? 2 : charge >= 0.25 ? 3 : 4;
    }

    // An Energy Storage Drive in a holder slot, as part of its network's energy pool. Filling is transactional (rolled
    // back with its transaction); the holder hears of each change (changed), to save and redraw.
    public static final class Cell extends SnapshotJournal<Long> implements EnergyCell {
        private final ItemStack stack;
        private final Runnable changed;
        private LongConsumer received = amount -> {};

        // stack: the drive itself, as the holder keeps it (changed in place).
        public Cell(ItemStack stack, Runnable changed) {
            this.stack = stack;
            this.changed = changed;
        }

        // Hears of FE committed into the drive, for its network's Received figure (as controllers and banks count theirs).
        public Cell received(LongConsumer received) {
            this.received = received;
            return this;
        }

        public @Nullable UUID id() {
            return StorageDriveItem.id(stack);
        }

        public ItemStack stack() {
            return stack;
        }

        // Whether it took in more than it gave over the last tick; its holder redraws when that changes.
        public void charging(boolean charging) {
            UUID id = id();
            if (id != null && (charging ? CHARGING.add(id) : CHARGING.remove(id))) {
                changed.run();
            }
        }

        @Override
        public long getStored() {
            return stored(stack);
        }

        @Override
        public long getCapacity() {
            return capacity(stack);
        }

        @Override
        public int fill(int amount, TransactionContext transaction) {
            int added = (int) Math.max(0, Math.min(amount, capacity(stack) - stored(stack)));
            if (added > 0) {
                updateSnapshots(transaction);
                set(stack, stored(stack) + added);
            }
            return added;
        }

        @Override
        public int drain(int amount) {
            int taken = (int) Math.max(0, Math.min(amount, stored(stack)));
            if (taken > 0) {
                set(stack, stored(stack) - taken);
                changed.run();
            }
            return taken;
        }

        @Override
        protected Long createSnapshot() {
            return stored(stack);
        }

        @Override
        protected void revertToSnapshot(Long snapshot) {
            set(stack, snapshot);
        }

        @Override
        protected void onRootCommit(Long originalState) {
            long now = stored(stack);
            if (now != originalState) {
                changed.run();
            }
            if (now > originalState) {
                received.accept(now - originalState);
            }
        }
    }

    // The drive's item energy capability, out of a holder: charge it in, or power things from it, like any battery item,
    // up to energyDriveMaxTransfer FE a tick.
    public static final class Handler implements EnergyHandler {
        private final ItemAccess access;

        public Handler(ItemAccess access) {
            this.access = access;
        }

        private ItemStack stack() {
            return access.getResource().toStack();
        }

        @Override
        public long getAmountAsLong() {
            return access.getAmount() * stored(stack());
        }

        @Override
        public long getCapacityAsLong() {
            return access.getAmount() * capacity(stack());
        }

        @Override
        public int insert(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            return change(Math.min(amount, Config.ENERGY_DRIVE_MAX_TRANSFER.getAsInt()), transaction);
        }

        @Override
        public int extract(int amount, TransactionContext transaction) {
            TransferPreconditions.checkNonNegative(amount);
            return change(-Math.min(amount, Config.ENERGY_DRIVE_MAX_TRANSFER.getAsInt()), transaction);
        }

        // Adds (or, negative, takes) up to that much from a single drive; returns how much moved.
        private int change(int delta, TransactionContext transaction) {
            if (access.getAmount() != 1 || delta == 0) {
                return 0;
            }
            ItemStack stack = stack().copy();
            long moved = delta > 0 ? fill(stack, delta, false) : drain(stack, -delta, false);
            if (moved <= 0) {
                return 0;
            }
            return access.exchange(ItemResource.of(stack), 1, transaction) == 1 ? (int) moved : 0;
        }
    }
}
