/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.part;

import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.menu.InventoryTapMenu;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.storage.StorageView;

// The Inventory Tap: makes the inventory it faces part of its network's storage, in place (nothing is copied). Access:
// read and write, read only (items only come out) or write only (items only go in). Its filter limits what goes in and
// what the network sees (empty: everything). Priority -999..999, shared with Drive Bays (0): higher is filled first and
// emptied last. Lit while it's online and faces an inventory (checked every CHECK ticks).
public class InventoryTapPart extends CablePart {
    public static final int READ_WRITE = 0, READ = 1, WRITE = 2, MIN_PRIORITY = -999, MAX_PRIORITY = 999;
    private static final int CHECK = 20;

    private final PartFilter filter = new PartFilter();
    private int priority, access = READ_WRITE;
    private boolean attached;
    private int timer;

    public InventoryTapPart(PartType type, CableBlockEntity host, Direction side) {
        super(type, host, side);
    }

    public PartFilter filter() {
        return filter;
    }

    public int priority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = Mth.clamp(priority, MIN_PRIORITY, MAX_PRIORITY);
        changed();
    }

    public int access() {
        return access;
    }

    public void cycleAccess() {
        access = (access + 1) % 3;
        changed();
    }

    public void settingsChanged() {
        changed();
    }

    @Override
    public boolean lit() {
        return isOnline() && attached;
    }

    @Override
    public boolean openMenu(ServerPlayer player) {
        InventoryTapMenu.open(player, this);
        return true;
    }

    @Override
    public void tick(ServerLevel level) {
        if (++timer < CHECK) {
            return;
        }
        timer = 0;
        boolean now = handler(level) != null;
        if (now != attached) {
            attached = now;
            changed();
        }
    }

    private @Nullable ResourceHandler<ItemResource> handler(ServerLevel level) {
        return level.isLoaded(facing()) ? level.getCapability(Capabilities.Item.BLOCK, facing(), side.getOpposite()) : null;
    }

    // The faced inventory as network storage, or null when there's none.
    public @Nullable StorageView view(ServerLevel level) {
        ResourceHandler<ItemResource> handler = handler(level);
        return handler == null ? null : new View(handler);
    }

    private final class View implements StorageView {
        private final ResourceHandler<ItemResource> handler;

        View(ResourceHandler<ItemResource> handler) {
            this.handler = handler;
        }

        @Override
        public int priority() {
            return priority;
        }

        @Override
        public boolean isTap() {
            return true;
        }

        private boolean visible(ItemStack stack) {
            return filter.test(stack, false, true);
        }

        @Override
        public void listInto(Map<StorageKey, Long> all) {
            for (int slot = 0; slot < handler.size(); slot++) {
                ItemResource resource = handler.getResource(slot);
                if (!resource.isEmpty() && visible(resource.toStack(1))) {
                    all.merge(StorageKey.of(resource.toStack(1)), handler.getAmountAsLong(slot), Long::sum);
                }
            }
        }

        @Override
        public long count(StorageKey key) {
            if (!visible(key.stack())) {
                return 0;
            }
            long count = 0;
            for (int slot = 0; slot < handler.size(); slot++) {
                ItemResource resource = handler.getResource(slot);
                if (!resource.isEmpty() && resource.matches(key.stack())) {
                    count += handler.getAmountAsLong(slot);
                }
            }
            return count;
        }

        @Override
        public long insert(StorageKey key, long amount, boolean simulate) {
            if (access == READ || !visible(key.stack()) || amount <= 0) {
                return 0;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int inserted = handler.insert(ItemResource.of(key.stack()), (int) Math.min(amount, Integer.MAX_VALUE), transaction);
                if (!simulate) {
                    transaction.commit();
                }
                return inserted;
            }
        }

        @Override
        public long extract(StorageKey key, long amount, boolean simulate) {
            if (access == WRITE || !visible(key.stack()) || amount <= 0) {
                return 0;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int extracted = handler.extract(ItemResource.of(key.stack()), (int) Math.min(amount, Integer.MAX_VALUE), transaction);
                if (!simulate) {
                    transaction.commit();
                }
                return extracted;
            }
        }
    }

    // --- Saving ---

    @Override
    public void load(ValueInput input) {
        filter.load(input);
        priority = Mth.clamp(input.getIntOr("priority", 0), MIN_PRIORITY, MAX_PRIORITY);
        access = Mth.clamp(input.getIntOr("access", READ_WRITE), READ_WRITE, WRITE);
    }

    @Override
    public void save(ValueOutput output) {
        filter.save(output);
        output.putInt("priority", priority);
        output.putInt("access", access);
    }
}
