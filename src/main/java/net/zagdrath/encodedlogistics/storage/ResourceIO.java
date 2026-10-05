/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

// Another block's inventory, tanks or gas tanks as one face offers them, by StorageKey: what devices move resources
// through (ports, the Inventory Tap, the Gateway), so every type takes the same path. Items come through NeoForge's item
// capability; fluids and gases through its fluid capability (Arcforge gases are fluids: a block's gas tanks are among its
// fluid tanks), each side keeping to its own keys: a FLUID view never lists or moves a gas, nor a PRESSURIZED view a
// liquid. A simulation opens a transaction it never commits.
public interface ResourceIO {
    ResourceIO NONE = new ResourceIO() {
        @Override
        public void listInto(Map<StorageKey, Long> all) {}

        @Override
        public long insert(StorageKey key, long amount, boolean simulate) {
            return 0;
        }

        @Override
        public long extract(StorageKey key, long amount, boolean simulate) {
            return 0;
        }
    };

    // Adds what it holds (that it lets out or not) to all.
    void listInto(Map<StorageKey, Long> all);

    // Puts up to amount in; returns how much went in.
    long insert(StorageKey key, long amount, boolean simulate);

    // Takes up to amount out; returns how much came out.
    long extract(StorageKey key, long amount, boolean simulate);

    default Map<StorageKey, Long> list() {
        Map<StorageKey, Long> all = new LinkedHashMap<>();
        listInto(all);
        return all;
    }

    default long count(StorageKey key) {
        return list().getOrDefault(key, 0L);
    }

    // What a block at pos offers on its side face for one resource type, or null when it offers nothing of it.
    static @Nullable ResourceIO at(Level level, BlockPos pos, @Nullable Direction side, ResourceType type) {
        if (!level.isLoaded(pos)) {
            return null;
        }
        return switch (type) {
            case ITEM -> {
                ResourceHandler<ItemResource> items = level.getCapability(Capabilities.Item.BLOCK, pos, side);
                yield items != null ? items(items) : null;
            }
            case FLUID, PRESSURIZED -> {
                ResourceHandler<FluidResource> fluids = level.getCapability(Capabilities.Fluid.BLOCK, pos, side);
                yield fluids != null ? fluids(fluids, type) : null;
            }
            case ENERGY -> null;
        };
    }

    // Everything a block offers on a face, of every keyed type (the Inventory Tap's view), or null for nothing.
    static @Nullable ResourceIO all(Level level, BlockPos pos, @Nullable Direction side) {
        List<ResourceIO> parts = new ArrayList<>();
        for (ResourceType type : ResourceType.KEYED) {
            ResourceIO io = at(level, pos, side, type);
            if (io != null) {
                parts.add(io);
            }
        }
        return parts.isEmpty() ? null : combined(parts);
    }

    static ResourceIO items(ResourceHandler<ItemResource> handler) {
        return new Items(handler);
    }

    // A fluid handler's FLUID or PRESSURIZED side (null type: both).
    static ResourceIO fluids(ResourceHandler<FluidResource> handler, @Nullable ResourceType type) {
        return new Fluids(handler, type);
    }

    static ResourceIO combined(List<ResourceIO> parts) {
        return parts.size() == 1 ? parts.getFirst() : new Combined(List.copyOf(parts));
    }

    record Items(ResourceHandler<ItemResource> handler) implements ResourceIO {
        @Override
        public void listInto(Map<StorageKey, Long> all) {
            for (int slot = 0; slot < handler.size(); slot++) {
                ItemResource resource = handler.getResource(slot);
                long amount = handler.getAmountAsLong(slot);
                if (!resource.isEmpty() && amount > 0) {
                    all.merge(StorageKey.of(resource.toStack(1)), amount, Long::sum);
                }
            }
        }

        @Override
        public long insert(StorageKey key, long amount, boolean simulate) {
            if (!key.isItem() || amount <= 0) {
                return 0;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int inserted = handler.insert(ItemResource.of(key.stack()), clamp(amount), transaction);
                if (!simulate) {
                    transaction.commit();
                }
                return inserted;
            }
        }

        @Override
        public long extract(StorageKey key, long amount, boolean simulate) {
            if (!key.isItem() || amount <= 0) {
                return 0;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int extracted = handler.extract(ItemResource.of(key.stack()), clamp(amount), transaction);
                if (!simulate) {
                    transaction.commit();
                }
                return extracted;
            }
        }
    }

    record Fluids(ResourceHandler<FluidResource> handler, @Nullable ResourceType type) implements ResourceIO {
        private boolean mine(StorageKey key) {
            return type != null ? key.is(type) : key.is(ResourceType.FLUID) || key.is(ResourceType.PRESSURIZED);
        }

        @Override
        public void listInto(Map<StorageKey, Long> all) {
            for (int tank = 0; tank < handler.size(); tank++) {
                FluidResource resource = handler.getResource(tank);
                long amount = handler.getAmountAsLong(tank);
                if (!resource.isEmpty() && amount > 0) {
                    StorageKey key = StorageKey.fluid(resource);
                    if (mine(key)) {
                        all.merge(key, amount, Long::sum);
                    }
                }
            }
        }

        @Override
        public long insert(StorageKey key, long amount, boolean simulate) {
            FluidResource fluid = mine(key) ? key.fluid() : null;
            if (fluid == null || amount <= 0) {
                return 0;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int inserted = handler.insert(fluid, clamp(amount), transaction);
                if (!simulate) {
                    transaction.commit();
                }
                return inserted;
            }
        }

        @Override
        public long extract(StorageKey key, long amount, boolean simulate) {
            FluidResource fluid = mine(key) ? key.fluid() : null;
            if (fluid == null || amount <= 0) {
                return 0;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int extracted = handler.extract(fluid, clamp(amount), transaction);
                if (!simulate) {
                    transaction.commit();
                }
                return extracted;
            }
        }
    }

    record Combined(List<ResourceIO> parts) implements ResourceIO {
        @Override
        public void listInto(Map<StorageKey, Long> all) {
            parts.forEach(part -> part.listInto(all));
        }

        @Override
        public long insert(StorageKey key, long amount, boolean simulate) {
            long left = amount;
            for (ResourceIO part : parts) {
                if (left <= 0) {
                    break;
                }
                left -= part.insert(key, left, simulate);
            }
            return amount - left;
        }

        @Override
        public long extract(StorageKey key, long amount, boolean simulate) {
            long left = amount;
            for (ResourceIO part : parts) {
                if (left <= 0) {
                    break;
                }
                left -= part.extract(key, left, simulate);
            }
            return amount - left;
        }
    }

    private static int clamp(long amount) {
        return (int) Math.min(Integer.MAX_VALUE, amount);
    }
}
