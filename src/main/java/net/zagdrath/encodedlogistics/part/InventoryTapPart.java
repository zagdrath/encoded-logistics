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
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.menu.InventoryTapMenu;
import net.zagdrath.encodedlogistics.storage.ResourceIO;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.storage.StorageView;

// The Inventory Tap: makes the inventory it faces part of its network's storage, in place (nothing is copied): its item
// slots, and its fluid tanks and gas tanks (fluids and Arcforge gases, through the block's fluid capability; ResourceIO),
// all on the same block. Access:
// read and write, read only (items only come out) or write only (items only go in). Its filter limits what goes in and
// what the network sees (empty: everything). Priority -999..999, shared with Drive Bays (0): higher is filled first and
// emptied last. Lit while it's online and faces an inventory or tank (checked every CHECK ticks). Fluid and gas entries
// in its filter (Resource Entries) limit those.
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

    private @Nullable ResourceIO handler(ServerLevel level) {
        return ResourceIO.all(level, facing(), side.getOpposite());
    }

    // The faced inventory and tanks as network storage, or null when there are none.
    public @Nullable StorageView view(ServerLevel level) {
        ResourceIO handler = handler(level);
        return handler == null ? null : new View(handler);
    }

    private final class View implements StorageView {
        private final ResourceIO handler;

        View(ResourceIO handler) {
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
            handler.list().forEach((key, count) -> {
                if (visible(key.stack())) {
                    all.merge(key, count, Long::sum);
                }
            });
        }

        @Override
        public long count(StorageKey key) {
            return visible(key.stack()) ? handler.count(key) : 0;
        }

        @Override
        public long insert(StorageKey key, long amount, boolean simulate) {
            if (access == READ || !visible(key.stack()) || amount <= 0) {
                return 0;
            }
            return handler.insert(key, amount, simulate);
        }

        @Override
        public long extract(StorageKey key, long amount, boolean simulate) {
            if (access == WRITE || !visible(key.stack()) || amount <= 0) {
                return 0;
            }
            return handler.extract(key, amount, simulate);
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
