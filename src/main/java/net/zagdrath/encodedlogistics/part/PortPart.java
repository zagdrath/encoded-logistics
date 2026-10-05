/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.part;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.menu.PortMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;
import net.zagdrath.encodedlogistics.storage.PressurizedSources;
import net.zagdrath.encodedlogistics.storage.ResourceIO;
import net.zagdrath.encodedlogistics.storage.ResourceType;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// The Ingress Port (pulls items from the inventory it faces into the network) and Egress Port (pushes items from the
// network into it). Once every OPERATION ticks it moves up to portRates[throughput modules] items, paying
// portEnergyPerItem FE each from the network. Ingress takes whatever passes its filter (empty: everything) and leaves
// what the network can't store; egress sends only what passes its filter (empty: nothing), one candidate item per
// operation in turn. Lit while it moved something in its last operation. Modules: one Filter Module (filter options),
// up to three Throughput Modules, one Fuzzy Match Module (its filter's entries can match by tag or damage) and one
// Redstone Control Module, which unlocks its redstone modes: work only while the block it's on is powered (high), only
// while it isn't (low), or one operation each time it's powered (pulse). Without the module it ignores redstone.
//
// Its resource type picks what it moves (one at a time): items (as above), fluids or pressurized gases (portFluidRates
// mB an operation, portEnergyPerBucket FE per 1,000 mB, through the faced block's tanks: ResourceIO), or energy: every
// tick, Ingress takes up to portEnergyRates[throughput modules] FE from the faced block into the network's energy pool,
// and Egress gives as much from the pool to it (never below the share machinePowerReserve keeps). Its filter takes
// fluid and gas entries (Resource Entries) for those types; energy has none.
public class PortPart extends CablePart {
    public static final int OPERATION = 20, MODULE_SLOTS = 4;
    public static final int REDSTONE_IGNORE = 0, REDSTONE_HIGH = 1, REDSTONE_LOW = 2, REDSTONE_PULSE = 3, REDSTONE_MODES = 4;

    private final PartFilter filter = new PartFilter();
    private final NonNullList<ItemStack> modules = NonNullList.withSize(MODULE_SLOTS, ItemStack.EMPTY);
    private int redstoneMode = REDSTONE_IGNORE;
    private ResourceType resourceType = ResourceType.ITEM;
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

    public ResourceType resourceType() {
        return resourceType;
    }

    // The next resource type: item, fluid, pressurized (only with a gas source loaded), energy.
    public void cycleResourceType() {
        ResourceType next = resourceType;
        do {
            next = ResourceType.values()[(next.ordinal() + 1) % ResourceType.values().length];
        } while (next == ResourceType.PRESSURIZED && !PressurizedSources.any() && next != resourceType);
        resourceType = next;
        changed();
    }

    public void setResourceType(ResourceType type) {
        resourceType = type;
        changed();
    }

    public int throughputModules() {
        return (int) Math.min(3, modules.stream().filter(stack -> stack.is(ModItems.THROUGHPUT_MODULE.get())).count());
    }

    // Per operation: items, or mB of fluid or gas; FE a tick for energy.
    public int rate() {
        List<? extends Integer> rates = switch (resourceType) {
            case ITEM -> Config.PORT_RATES.get();
            case FLUID, PRESSURIZED -> Config.PORT_FLUID_RATES.get();
            case ENERGY -> Config.PORT_ENERGY_RATES.get();
        };
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
        // Energy moves every tick, the rest once an operation.
        if (!pulse && resourceType != ResourceType.ENERGY && ++timer < OPERATION) {
            return;
        }
        timer = 0;
        boolean wasActive = active;
        active = false;
        NetworkStorage storage = isOnline() && resourceType != ResourceType.ENERGY ? storage(level) : null;
        long moved = 0;
        if (resourceType == ResourceType.ITEM) {
            ResourceHandler<ItemResource> target = level.getCapability(Capabilities.Item.BLOCK, facing(), side.getOpposite());
            if (storage != null && target != null) {
                int budget = (int) budget(level, rate(), Config.PORT_ENERGY_PER_ITEM.getAsDouble());
                moved = ingress() ? pull(target, storage, budget) : push(target, storage, budget);
                energyCredit -= moved * Config.PORT_ENERGY_PER_ITEM.getAsDouble();
            }
        } else if (resourceType == ResourceType.ENERGY) {
            moved = isOnline() ? moveEnergy(level, rate()) : 0;
        } else {
            ResourceIO target = ResourceIO.at(level, facing(), side.getOpposite(), resourceType);
            if (storage != null && target != null) {
                double cost = Config.PORT_ENERGY_PER_BUCKET.getAsDouble() / 1_000;
                long budget = budget(level, rate(), cost);
                moved = ingress() ? pull(target, storage, budget) : push(target, storage, budget);
                energyCredit -= moved * cost;
            }
        }
        active = moved > 0;
        logMoved(level.getGameTime(), moved);
        if (active != wasActive) {
            changed();
        }
    }

    // Energy mode: up to rate FE between the faced block and the network's energy pool; returns what moved.
    private long moveEnergy(ServerLevel level, int rate) {
        EnergyHandler other = level.getCapability(Capabilities.Energy.BLOCK, facing(), side.getOpposite());
        NetworkRef network = ControllerStructures.get(level).energyNetworkOf(level, host.getBlockPos());
        if (other == null || network == null || rate <= 0) {
            return 0;
        }
        MinecraftServer server = level.getServer();
        if (ingress()) {
            int available, room;
            try (Transaction transaction = Transaction.openRoot()) {
                available = other.extract(rate, transaction);
            }
            try (Transaction transaction = Transaction.openRoot()) {
                room = ControllerStructures.fill(server, network, available, transaction);
            }
            int amount = Math.min(available, room);
            if (amount <= 0) {
                return 0;
            }
            try (Transaction transaction = Transaction.openRoot()) {
                int taken = other.extract(amount, transaction);
                int put = ControllerStructures.fill(server, network, taken, transaction);
                if (taken <= 0 || put != taken) {
                    return 0;
                }
                transaction.commit();
                return put;
            }
        }
        int room;
        try (Transaction transaction = Transaction.openRoot()) {
            room = other.insert(rate, transaction);
        }
        int drawn = room > 0 ? ControllerStructures.drawEnergyAbove(server, network, room, Config.MACHINE_POWER_RESERVE.getAsDouble()) : 0;
        if (drawn <= 0) {
            return 0;
        }
        int given;
        try (Transaction transaction = Transaction.openRoot()) {
            given = other.insert(drawn, transaction);
            transaction.commit();
        }
        if (given < drawn) {
            // What it didn't take goes back into the pool.
            try (Transaction transaction = Transaction.openRoot()) {
                ControllerStructures.fill(server, network, drawn - given, transaction);
                transaction.commit();
            }
        }
        return given;
    }

    private void logMoved(long gameTime, long moved) {
        long second = gameTime / 20;
        int slot = (int) (second % movedLog.length);
        if (movedAt[slot] != second) {
            movedAt[slot] = second;
            movedLog[slot] = 0;
        }
        movedLog[slot] = (int) Math.min(Integer.MAX_VALUE, movedLog[slot] + moved);
    }

    // Items (mB of fluid or gas, FE) moved in the last minute.
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

    // How much (items, mB) of up to units this operation can pay for at cost FE each: tops up the energy credit from the
    // network.
    private long budget(ServerLevel level, long units, double cost) {
        if (cost <= 0) {
            return units;
        }
        double needed = units * cost - energyCredit;
        if (needed > 0) {
            energyCredit += drawEnergy(level, (int) Math.ceil(needed));
        }
        return (long) Math.min(units, Math.floor(energyCredit / cost));
    }

    // Ingress of a fluid or gas: from the faced tanks into the network, what passes the filter (empty: everything).
    private long pull(ResourceIO source, NetworkStorage storage, long budget) {
        long left = budget;
        boolean options = hasFilterModule(), fuzzy = hasFuzzyModule();
        for (Map.Entry<StorageKey, Long> there : source.list().entrySet()) {
            if (left <= 0) {
                break;
            }
            StorageKey key = there.getKey();
            if (!filter.test(key.stack(), options, true, fuzzy)) {
                continue;
            }
            long fits = storage.insert(key, Math.min(left, there.getValue()), true);
            long taken = fits > 0 ? source.extract(key, fits, false) : 0;
            if (taken <= 0) {
                continue;
            }
            long stored = storage.insert(key, taken, false);
            if (stored < taken) {
                source.insert(key, taken - stored, false);
            }
            left -= stored;
        }
        return budget - left;
    }

    // Egress of a fluid or gas: from the network into the faced tanks, the next candidate (empty filter: nothing).
    private long push(ResourceIO target, NetworkStorage storage, long budget) {
        if (filter.isEmpty() || budget <= 0) {
            return 0;
        }
        boolean options = hasFilterModule(), fuzzy = hasFuzzyModule();
        Map<StorageKey, Long> stored = storage.list(resourceType);
        List<StorageKey> candidates = new ArrayList<>();
        for (StorageKey key : stored.keySet()) {
            if (filter.test(key.stack(), options, false, fuzzy)) {
                candidates.add(key);
            }
        }
        candidates.sort(Comparator.comparing(key -> key.id().toString()));
        for (int tried = 0; tried < candidates.size(); tried++) {
            StorageKey key = candidates.get(Math.floorMod(roundRobin + tried, candidates.size()));
            long room = target.insert(key, Math.min(budget, stored.getOrDefault(key, 0L)), true);
            long taken = room > 0 ? storage.extract(key, room, false) : 0;
            if (taken > 0) {
                long inserted = target.insert(key, taken, false);
                if (inserted < taken) {
                    storage.insert(key, taken - inserted, false);
                }
                roundRobin = Math.floorMod(roundRobin + tried + 1, candidates.size());
                return inserted;
            }
        }
        return 0;
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
        resourceType = input.read("resource_type", ResourceType.CODEC).orElse(ResourceType.ITEM);
    }

    @Override
    public void save(ValueOutput output) {
        filter.save(output);
        ContainerHelper.saveAllItems(output.child("modules"), modules);
        output.putInt("redstone", redstoneMode);
        if (resourceType != ResourceType.ITEM) {
            output.store("resource_type", ResourceType.CODEC, resourceType);
        }
    }

    public static @Nullable PortPart at(CableBlockEntity host, Direction side) {
        return host.part(side) instanceof PortPart port ? port : null;
    }
}
