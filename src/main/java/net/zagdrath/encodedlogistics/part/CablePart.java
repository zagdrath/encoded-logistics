/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.part;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// A part's behaviour and state on one side of a cable (or part host): what it saves, what it drops besides itself, how it
// ticks, whether it's lit (its model's lit state), the menu it opens. A part works only while its host is online (on a
// powered network with its lanes); offline, ports idle and lights go out.
public abstract class CablePart {
    protected final PartType type;
    protected final CableBlockEntity host;
    protected final Direction side;

    protected CablePart(PartType type, CableBlockEntity host, Direction side) {
        this.type = type;
        this.host = host;
        this.side = side;
    }

    public PartType type() {
        return type;
    }

    public CableBlockEntity host() {
        return host;
    }

    public Direction side() {
        return side;
    }

    // The block the part faces (the inventory a port or tap works on).
    public BlockPos facing() {
        return host.getBlockPos().relative(side);
    }

    public boolean isOnline() {
        return host.isOnline();
    }

    // Whether the model shows its lit state.
    public boolean lit() {
        return isOnline();
    }

    public void load(ValueInput input) {}

    public void save(ValueOutput output) {}

    // What it drops besides its own item: real items it holds (modules, a crafting grid). Ghost filters drop nothing.
    public List<ItemStack> contents() {
        return List.of();
    }

    // Server, every tick while the host is loaded (for parts whose type ticks).
    public void tick(ServerLevel level) {}

    // The redstone it sends out of its face (the Threshold Sensor), 0-15.
    public int signal() {
        return 0;
    }

    // Opens its menu; false when it has none.
    public boolean openMenu(ServerPlayer player) {
        return false;
    }

    // Its settings or contents changed: save, and redraw if its look changed.
    protected void changed() {
        host.partChanged();
    }

    // The storage it reaches: the network's (Drive Bays and Inventory Taps), while the host is online.
    protected @Nullable NetworkStorage storage(ServerLevel level) {
        return ControllerStructures.get(level).storageAt(level, host.getBlockPos());
    }

    // Takes FE from the network's energy for work done; returns what it got.
    protected int drawEnergy(ServerLevel level, int amount) {
        return ControllerStructures.get(level).drawEnergy(level, host.getBlockPos(), amount);
    }

    // Whether a player can still use this part's menu.
    public boolean stillValid(Player player) {
        return host.part(side) == this && !host.isRemoved() && player.isWithinBlockInteractionRange(host.getBlockPos(), 4.0);
    }
}
