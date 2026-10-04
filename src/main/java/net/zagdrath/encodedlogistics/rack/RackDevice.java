/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;

// A device mounted in a Server Rack at U u (taking size U from there up). The rack is its network node: each device
// uses laneCost lanes and drains drain() FE/t from the network, and goes online when the rack has its lanes on a
// powered network.
//
// What a device keeps comes in three layers: its settings (saveSettings: what the item carries when it's taken out),
// its full state in the rack (save: settings plus anything else, like its own inventory), and what clients see
// (writeClient: what the rack's renderer, screen and popup need, synced with the rack). Its settings panel gets
// writePanel every few ticks while it's open, and sends actions back (handleAction).
public abstract class RackDevice {
    private final RackDeviceType type;
    private int u;
    private @Nullable RackBlockEntity rack;
    private boolean online;

    protected RackDevice(RackDeviceType type) {
        this.type = type;
    }

    public final RackDeviceType type() {
        return type;
    }

    public final int u() {
        return u;
    }

    public final int size() {
        return type.size();
    }

    // The highest U it takes.
    public final int top() {
        return u + type.size() - 1;
    }

    public final boolean occupies(int unit) {
        return unit >= u && unit <= top();
    }

    public final @Nullable RackBlockEntity rack() {
        return rack;
    }

    public final void attach(RackBlockEntity rack, int u) {
        this.rack = rack;
        this.u = u;
    }

    public final void detach() {
        this.rack = null;
        this.online = false;
    }

    // On a powered network with its lanes (server), or as last synced (client).
    public final boolean isOnline() {
        return online;
    }

    public final void setOnline(boolean online) {
        if (this.online != online) {
            this.online = online;
            onlineChanged();
            changed(false);
        }
    }

    protected void onlineChanged() {}

    public Component name() {
        return type.item().getName(type.item().getDefaultInstance());
    }

    // --- Network ---

    public int laneCost() {
        return 1;
    }

    // FE per tick it drains while its network runs.
    public abstract double drain();

    // --- Behaviour ---

    // Every server tick while it's in a loaded rack.
    public void tick(ServerLevel level) {}

    // Put into a rack (by a player, or null when loaded or placed some other way).
    public void onInstalled(@Nullable ServerPlayer by) {}

    // Taken out of its rack (or the rack was broken).
    public void onRemoved() {}

    public RackDeviceInfo.Status status() {
        return online ? RackDeviceInfo.Status.ONLINE : RackDeviceInfo.Status.OFFLINE;
    }

    public Component statusText() {
        return status().text();
    }

    // The status as clients see it: what the server last synced, or status() on the server.
    private RackDeviceInfo.@Nullable Status shown;

    public final RackDeviceInfo.Status shownStatus() {
        return shown != null ? shown : status();
    }

    public final void setShownStatus(RackDeviceInfo.Status status) {
        shown = status;
    }

    // The popup's lines. Called on the server, for the player looking at it.
    public RackDeviceInfo describe(ServerPlayer viewer) {
        return new RackDeviceInfo(name(), status(), statusText(), lines(viewer));
    }

    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        return List.of();
    }

    // Items it holds besides itself (dropped with it, or given back when it's taken out).
    public List<ItemStack> contents() {
        return List.of();
    }

    // Empties what contents() listed, once they've been dropped or given back.
    public void clearContents() {}

    // An action from its settings panel. The player is allowed to use the rack's screen.
    public void handleAction(ServerPlayer player, int action, int value, String text) {}

    // --- Saving ---

    public void saveSettings(ValueOutput output) {}

    public void loadSettings(ValueInput input) {}

    public void save(ValueOutput output) {
        saveSettings(output);
    }

    public void load(ValueInput input) {
        loadSettings(input);
    }

    public void writeClient(ValueOutput output) {}

    public void readClient(ValueInput input) {}

    // What its settings panel shows; by default its full state.
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        save(output);
    }

    // --- Telling the rack ---

    // Something changed: the rack saves, and syncs to clients; topology: its lanes or drain changed too.
    protected final void changed(boolean topology) {
        if (rack != null) {
            rack.deviceChanged(topology);
        }
    }

    protected final void saveOnly() {
        if (rack != null) {
            rack.setChanged();
        }
    }
}
