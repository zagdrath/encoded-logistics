/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.access.ItemAccess;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

// Items that hold a fluid or gas: buckets, tanks and other fluid containers (NeoForge's fluid item capability), and a
// PressurizedSource's gas containers (Arcforge's Gas Cartridges). What one holds sets a ghost filter entry; terminals
// fill and empty them (ItemAccess, so the item in hand or in a slot changes in place).
public final class ResourceContainers {
    private ResourceContainers() {}

    // The fluid or gas an item holds (of that type; null: either), or null for none.
    public static @Nullable StorageKey contents(ItemStack stack, @Nullable ResourceType type) {
        if (stack.isEmpty() || type == ResourceType.ITEM || type == ResourceType.ENERGY) {
            return null;
        }
        ItemAccess access = ItemAccess.forStack(stack.copyWithCount(1));
        ResourceHandler<FluidResource> fluids = access.getCapability(Capabilities.Fluid.ITEM);
        if (fluids != null) {
            for (int tank = 0; tank < fluids.size(); tank++) {
                FluidResource resource = fluids.getResource(tank);
                if (!resource.isEmpty() && fluids.getAmountAsLong(tank) > 0) {
                    StorageKey key = StorageKey.fluid(resource);
                    if (type == null || key.is(type)) {
                        return key;
                    }
                }
            }
        }
        if (type == null || type == ResourceType.PRESSURIZED) {
            for (PressurizedSource source : PressurizedSources.all()) {
                PressurizedSource.Container container = source.container(access);
                Identifier gas = container != null ? container.gas() : null;
                if (gas != null && container.amount() > 0) {
                    return StorageKey.pressurized(source.id(), gas);
                }
            }
        }
        return null;
    }

    // Fills the container behind access with up to amount of a fluid or gas; returns how much went in.
    public static long fill(ItemAccess access, StorageKey key, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        if (key.is(ResourceType.PRESSURIZED)) {
            long done = gas(access, key, amount, simulate, true);
            if (done > 0) {
                return done;
            }
        }
        FluidResource fluid = key.fluid();
        ResourceHandler<FluidResource> handler = fluid != null ? access.getCapability(Capabilities.Fluid.ITEM) : null;
        if (handler == null) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int inserted = handler.insert(fluid, (int) Math.min(Integer.MAX_VALUE, amount), transaction);
            if (!simulate) {
                transaction.commit();
            }
            return inserted;
        }
    }

    // Empties up to amount of a fluid or gas out of the container behind access; returns how much came out.
    public static long drain(ItemAccess access, StorageKey key, long amount, boolean simulate) {
        if (amount <= 0) {
            return 0;
        }
        if (key.is(ResourceType.PRESSURIZED)) {
            long done = gas(access, key, amount, simulate, false);
            if (done > 0) {
                return done;
            }
        }
        FluidResource fluid = key.fluid();
        ResourceHandler<FluidResource> handler = fluid != null ? access.getCapability(Capabilities.Fluid.ITEM) : null;
        if (handler == null) {
            return 0;
        }
        try (Transaction transaction = Transaction.openRoot()) {
            int extracted = handler.extract(fluid, (int) Math.min(Integer.MAX_VALUE, amount), transaction);
            if (!simulate) {
                transaction.commit();
            }
            return extracted;
        }
    }

    // Fills containers, in order, from the network with up to amount of a fluid or gas (each as far as it takes); returns
    // how much went in. A Terminal Desk's drawer or a player's inventory, for a withdrawal or MOVITM.
    public static long fillFrom(NetworkStorage storage, StorageKey key, long amount, List<ItemAccess> containers) {
        long moved = 0;
        for (ItemAccess access : containers) {
            long left = Math.min(amount - moved, storage.count(key));
            if (left <= 0) {
                break;
            }
            // Fill each container as many times as it takes some (a stack of empty buckets, one at a time).
            while (left > 0) {
                long room = fill(access, key, left, true);
                long taken = room > 0 ? storage.extract(key, room, false) : 0;
                if (taken <= 0) {
                    break;
                }
                long filled = fill(access, key, taken, false);
                if (filled < taken) {
                    storage.insert(key, taken - filled, false);
                }
                moved += filled;
                left -= filled;
                if (filled <= 0) {
                    break;
                }
            }
        }
        return moved;
    }

    private static long gas(ItemAccess access, StorageKey key, long amount, boolean simulate, boolean fill) {
        PressurizedSource source = key.source() != null ? PressurizedSources.get(key.source()) : null;
        PressurizedSource.Container container = source != null ? source.container(access) : null;
        if (container == null || key.gas() == null) {
            return 0;
        }
        return fill ? container.insert(key.gas(), amount, simulate) : container.extract(key.gas(), amount, simulate);
    }
}
