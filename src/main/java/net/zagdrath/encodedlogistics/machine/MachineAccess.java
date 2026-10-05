/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.machine;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.zagdrath.encodedlogistics.storage.ResourceIO;

// What Encoded Logistics asks of another mod's machines, through the block a Small Wireless Bridge is on: provided by
// that mod's integration (compat.arcforge.ArcforgeMachines) and set on MachineBridges only while it's loaded and
// compatible, so nothing else touches the machine mod's classes. Server thread only. Every write goes through the
// machine's own rules; missing features are empty or UNSUPPORTED, never exceptions.
public interface MachineAccess {
    // How a setting change went (the machine mod's own result, plus NO_MACHINE and NOT_FORMED from here).
    enum Result {
        APPLIED, UNCHANGED, UNSUPPORTED, INVALID, REJECTED, NO_MACHINE, NOT_FORMED;

        public boolean succeeded() {
            return this == APPLIED || this == UNCHANGED;
        }
    }

    // A bridged machine's events, as the machine reports them (server thread; quick, and never changing the machine).
    interface Listener {
        void statusChanged(MachineInfo.State previous, MachineInfo.State current, Component reason);

        void operationCompleted(List<ItemStack> produced);
    }

    // Whether the block at pos is one of the mod's machines (any block of a formed multiblock, or an unformed one's
    // controller).
    boolean isMachine(ServerLevel level, BlockPos pos);

    // The machine there now, or null when there's none (unloaded, broken, or a casing of an unformed structure).
    @Nullable MachineInfo info(ServerLevel level, BlockPos pos);

    Result setEnabled(ServerLevel level, BlockPos pos, boolean enabled);

    // mode: a MachineInfo.Settings redstone mode id.
    Result setRedstoneMode(ServerLevel level, BlockPos pos, String mode);

    // side and mode: MachineInfo.Settings ids.
    Result setSideMode(ServerLevel level, BlockPos pos, String side, String mode);

    Result setAutoEject(ServerLevel level, BlockPos pos, boolean autoEject);

    // Its items, with its slot rules (insert into inputs, fuel and catalysts; extract from outputs), or null for none.
    @Nullable ResourceHandler<ItemResource> items(ServerLevel level, BlockPos pos);

    // Its tanks, gas tanks included (Arcforge keeps gases as fluids), with its tank rules, or null for none.
    default @Nullable ResourceHandler<FluidResource> fluids(ServerLevel level, BlockPos pos) {
        return null;
    }

    // Its items, fluids and gases together, as ResourceIO moves them, or null for none.
    default @Nullable ResourceIO resources(ServerLevel level, BlockPos pos) {
        List<ResourceIO> parts = new ArrayList<>();
        ResourceHandler<ItemResource> items = items(level, pos);
        if (items != null) {
            parts.add(ResourceIO.items(items));
        }
        ResourceHandler<FluidResource> fluids = fluids(level, pos);
        if (fluids != null) {
            parts.add(ResourceIO.fluids(fluids, null));
        }
        return parts.isEmpty() ? null : ResourceIO.combined(parts);
    }

    // Keeps listener told of the machine's events, following it when it's reloaded or re-formed; call it each tick the
    // machine is bridged (cheap when nothing changed).
    void watch(ServerLevel level, BlockPos pos, Listener listener);

    // Stops listening to the machine at pos.
    void unwatch(ServerLevel level, BlockPos pos);
}
