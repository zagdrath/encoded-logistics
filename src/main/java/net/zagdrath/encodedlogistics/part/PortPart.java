/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.part;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.menu.PortMenu;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.StorageKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// The Ingress Port (pulls items from the inventory it faces into the network) and Egress Port (pushes items from the
// network into it). Once every OPERATION ticks it moves up to portRates[throughput modules] items, paying
// portEnergyPerItem FE each from the network. Ingress takes whatever passes its filter (empty: everything) and leaves
// what the network can't store; egress sends only what passes its filter (empty: nothing), one candidate item per
// operation in turn. Lit while it moved something in its last operation. Modules: one Filter Module (filter options),
// up to three Throughput Modules, one Fuzzy Match Module (its filter's entries can match by tag or damage) and one
// Redstone Control Module, which unlocks its redstone modes: work only while the block it's on is powered (high), only
// while it isn't (low), or one operation each time it's powered (pulse). Without the module it ignores redstone.
public class PortPart extends CablePart {
    public static final int OPERATION = 20, MODULE_SLOTS = 4;
    public static final int REDSTONE_IGNORE = 0, REDSTONE_HIGH = 1, REDSTONE_LOW = 2, REDSTONE_PULSE = 3, REDSTONE_MODES = 4;

    private final PartFilter filter = new PartFilter();
    private final NonNullList<ItemStack> modules = NonNullList.withSize(MODULE_SLOTS, ItemStack.EMPTY);
    private int redstoneMode = REDSTONE_IGNORE;
    private int timer, roundRobin;
    private double energyCredit;
    private boolean active, wasPowered;
    // Items moved in each of the last 60 seconds (by game second), for "Moved per minute".
    private final int[] movedLog = new int[60];
    private final long[] movedAt = new long[60];

    public PortPart(PartType type, CableBlockEntity host, Direction side) {
        super(type, host, side);
    }

    public boolean ingress() {
        return type == PartType.INGRESS_PORT;
    }

    public PartFilter filter() {
        return filter;
    }

    public NonNullList<ItemStack> modules() {
        return modules;
    }

    public int redstoneMode() {
        return redstoneMode;
    }

    public boolean hasFilterModule() {
        return modules.stream().anyMatch(stack -> stack.is(ModItems.FILTER_MODULE.get()));
    }

    public boolean hasFuzzyModule() {
        return modules.stream().anyMatch(stack -> stack.is(ModItems.FUZZY_MATCH_MODULE.get()));
    }

    public boolean hasRedstoneModule() {
        return modules.stream().anyMatch(stack -> stack.is(ModItems.REDSTONE_CONTROL_MODULE.get()));
    }

    // The next redstone mode (needs a Redstone Control Module).
    public void cycleRedstoneMode() {
        if (hasRedstoneModule()) {
            redstoneMode = (redstoneMode + 1) % REDSTONE_MODES;
            changed();
        }
    }

    public int throughputModules() {
        return (int) Math.min(3, modules.stream().filter(stack -> stack.is(ModItems.THROUGHPUT_MODULE.get())).count());
    }

    public int rate() {
        List<? extends Integer> rates = Config.PORT_RATES.get();
        int index = Math.min(throughputModules(), rates.size() - 1);
        return index < 0 ? 0 : rates.get(index);
    }

    public void settingsChanged() {
        changed();
    }

    @Override
    public boolean lit() {
        return isOnline() && active;
    }

    @Override
    public boolean openMenu(ServerPlayer player) {
        PortMenu.open(player, this);
        return true;
    }

    // --- Working ---

    @Override
    public void tick(ServerLevel level) {
        int mode = hasRedstoneModule() ? redstoneMode : REDSTONE_IGNORE;
        boolean pulse = false;
        if (mode != REDSTONE_IGNORE) {
            boolean powered = level.hasNeighborSignal(host.getBlockPos());
            boolean rising = powered && !wasPowered;
            wasPowered = powered;
            if (mode == REDSTONE_HIGH && !powered || mode == REDSTONE_LOW && powered || mode == REDSTONE_PULSE && !rising) {
                // Held off by redstone: idle (a pulse port just waits for the next pulse).
                if (mode != REDSTONE_PULSE || ++timer >= OPERATION) {
                    timer = 0;
                    setActive(false);
                }
                return;
            }
            pulse = mode == REDSTONE_PULSE;
        }
        if (!pulse && ++timer < OPERATION) {
            return;
        }
        timer = 0;
        boolean wasActive = active;
        active = false;
        NetworkStorage storage = isOnline() ? storage(level) : null;
        ResourceHandler<ItemResource> target = level.getCapability(Capabilities.Item.BLOCK, facing(), side.getOpposite());
        if (storage != null && target != null) {
            int budget = budget(level, rate());
            int moved = ingress() ? pull(target, storage, budget) : push(target, storage, budget);
            energyCredit -= moved * Config.PORT_ENERGY_PER_ITEM.getAsDouble();
            active = moved > 0;
            logMoved(level.getGameTime(), moved);
        }
        if (active != wasActive) {
            changed();
        }
    }

    private void logMoved(long gameTime, int moved) {
        long second = gameTime / 20;
        int slot = (int) (second % movedLog.length);
        if (movedAt[slot] != second) {
            movedAt[slot] = second;
            movedLog[slot] = 0;
        }
        movedLog[slot] += moved;
    }

    // Items moved in the last minute.
    public int movedPerMinute(long gameTime) {
        long now = gameTime / 20;
        int sum = 0;
        for (int i = 0; i < movedLog.length; i++) {
            if (now - movedAt[i] < movedLog.length) {
                sum += movedLog[i];
            }
        }
        return sum;
    }

    private void setActive(boolean active) {
        if (this.active != active) {
            this.active = active;
            changed();
        }
    }

    // How many items this operation can pay for: tops up the energy credit from the network.
    private int budget(ServerLevel level, int items) {
        double cost = Config.PORT_ENERGY_PER_ITEM.getAsDouble();
        if (cost <= 0) {
            return items;
        }
        double needed = items * cost - energyCredit;
        if (needed > 0) {
            energyCredit += drawEnergy(level, (int) Math.ceil(needed));
        }
        return (int) Math.min(items, Math.floor(energyCredit / cost));
    }

    // Ingress: from the faced inventory into the network.
    private int pull(ResourceHandler<ItemResource> source, NetworkStorage storage, int budget) {
        int left = budget;
        boolean options = hasFilterModule(), fuzzy = hasFuzzyModule();
        for (int slot = 0; slot < source.size() && left > 0; slot++) {
            ItemResource resource = source.getResource(slot);
            if (resource.isEmpty()) {
                continue;
            }
            ItemStack stack = resource.toStack(1);
            if (!filter.test(stack, options, true, fuzzy)) {
                continue;
            }
            StorageKey key = StorageKey.of(stack);
            int fits = (int) storage.insert(key, Math.min(left, source.getAmountAsInt(slot)), true);
            if (fits <= 0) {
                continue;
            }
            int extracted;
            try (Transaction transaction = Transaction.openRoot()) {
                extracted = source.extract(slot, resource, fits, transaction);
                transaction.commit();
            }
            int stored = (int) storage.insert(key, extracted, false);
            if (stored < extracted) {
                giveBack(source, resource, extracted - stored);
            }
            left -= stored;
        }
        return budget - left;
    }

    // Egress: from the network into the faced inventory, the next candidate item that moves.
    private int push(ResourceHandler<ItemResource> target, NetworkStorage storage, int budget) {
        if (filter.isEmpty() || budget <= 0) {
            return 0;
        }
        boolean options = hasFilterModule(), fuzzy = hasFuzzyModule();
        List<StorageKey> candidates = new ArrayList<>();
        Map<StorageKey, Long> stored = storage.list();
        for (StorageKey key : stored.keySet()) {
            if (filter.test(key.stack(), options, false, fuzzy)) {
                candidates.add(key);
            }
        }
        candidates.sort((a, b) -> a.stack().getItem().getDescriptionId().compareTo(b.stack().getItem().getDescriptionId()));
        for (int tried = 0; tried < candidates.size(); tried++) {
            StorageKey key = candidates.get(Math.floorMod(roundRobin + tried, candidates.size()));
            int want = (int) Math.min(budget, stored.getOrDefault(key, 0L));
            ItemResource resource = ItemResource.of(key.stack());
            int room;
            try (Transaction transaction = Transaction.openRoot()) {
                room = target.insert(resource, want, transaction);
            }
            int taken = room > 0 ? (int) storage.extract(key, room, false) : 0;
            if (taken > 0) {
                int inserted;
                try (Transaction transaction = Transaction.openRoot()) {
                    inserted = target.insert(resource, taken, transaction);
                    transaction.commit();
                }
                if (inserted < taken) {
                    storage.insert(key, taken - inserted, false);
                }
                roundRobin = Math.floorMod(roundRobin + tried + 1, candidates.size());
                return inserted;
            }
        }
        return 0;
    }

    private static void giveBack(ResourceHandler<ItemResource> source, ItemResource resource, int amount) {
        try (Transaction transaction = Transaction.openRoot()) {
            source.insert(resource, amount, transaction);
            transaction.commit();
        }
    }

    // --- Saving ---

    @Override
    public List<ItemStack> contents() {
        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack stack : modules) {
            if (!stack.isEmpty()) {
                contents.add(stack.copy());
            }
        }
        return contents;
    }

    @Override
    public void load(ValueInput input) {
        filter.load(input);
        for (int i = 0; i < MODULE_SLOTS; i++) {
            modules.set(i, ItemStack.EMPTY);
        }
        ContainerHelper.loadAllItems(input.childOrEmpty("modules"), modules);
        redstoneMode = input.getIntOr("redstone", REDSTONE_IGNORE);
    }

    @Override
    public void save(ValueOutput output) {
        filter.save(output);
        ContainerHelper.saveAllItems(output.child("modules"), modules);
        output.putInt("redstone", redstoneMode);
    }

    public static @Nullable PortPart at(CableBlockEntity host, Direction side) {
        return host.part(side) instanceof PortPart port ? port : null;
    }
}
