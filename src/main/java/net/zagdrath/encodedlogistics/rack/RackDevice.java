/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;

// A device mounted in a Server Rack at U u (taking size U from there up). The rack is its network node: each device
// uses laneCost lanes (none while a switch in the rack pools it: LanePool) and drains drain() FE/t from the network,
// and goes online when the rack has its lanes on a powered network. A pooled device can be put on another segment
// (segment > 0: one the rack links to with a Link Card), and then serves that segment's network instead of the rack's
// own (RackBlockEntity#network).
//
// Devices with item slots (RackDeviceType#slots) hold them in items(); the rack's screen shows them while the device
// is picked, and they come out with it (contents()).
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
    private int segment;
    private final NonNullList<ItemStack> items;

    protected RackDevice(RackDeviceType type) {
        this.type = type;
        this.items = NonNullList.withSize(type.slots().size(), ItemStack.EMPTY);
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

    // A switch's rack-local lanes, or null for any other device.
    public @Nullable LanePool lanePool() {
        return null;
    }

    // What a switch offers the rack: lanes for the other devices in it, and what it costs on the network itself.
    public interface LanePool {
        int capacity();

        int uplinkCost();
    }

    // The segment it serves: 0 the rack's own network, i (1+) the rack's i-th linked segment. Only pooled devices can
    // be on another segment.
    public final int segment() {
        return segment;
    }

    public final void setSegment(int segment) {
        this.segment = Math.max(0, segment);
    }

    // FE per tick it drains while its network runs.
    public abstract double drain();

    // --- Behaviour ---

    // Every server tick while it's in a loaded rack.
    public void tick(ServerLevel level) {}

    // Every client tick, on the client's copy (animations). The copy lives on across syncs while it stays at its unit.
    public void clientTick() {}

    // Right-clicked empty-handed with the rack's front door open (the unit looked at, RackTargeting; sneaking or not,
    // as the player says); true when it did something (else the door toggles, or sneaking opens the rack's screen).
    public boolean use(ServerPlayer player) {
        return false;
    }

    // Used with an item: on its unit with the front door open (atUnit), or anywhere else on the rack; true when it
    // took the use.
    public boolean useItem(ServerPlayer player, ItemStack stack, boolean atUnit) {
        return false;
    }

    // The rack's front door opened or closed.
    public void frontDoorChanged(boolean open) {}

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
        return new RackDeviceInfo(name(), status(), statusText(), lines(viewer), rack != null && rack.scheduler().badges(this));
    }

    protected List<RackDeviceInfo.InfoLine> lines(ServerPlayer viewer) {
        return List.of();
    }

    // Its item slots (RackDeviceType#slots), in order.
    public final NonNullList<ItemStack> items() {
        return items;
    }

    // A slot's item was put in or taken out.
    public void itemsChanged() {
        changed(false);
    }

    // Items it holds besides itself (dropped with it, or given back when it's taken out): its slots, by default.
    public List<ItemStack> contents() {
        List<ItemStack> stacks = new ArrayList<>();
        items.stream().filter(stack -> !stack.isEmpty()).forEach(stack -> stacks.add(stack.copy()));
        return stacks;
    }

    // Empties what contents() listed, once they've been dropped or given back.
    public void clearContents() {
        items.replaceAll(stack -> ItemStack.EMPTY);
    }

    // Saves and loads its slots (for devices with any).
    protected final void saveItems(ValueOutput output) {
        ValueOutput.ValueOutputList list = output.childrenList("items");
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                ValueOutput child = list.addChild();
                child.putInt("slot", i);
                child.store("item", ItemStack.CODEC, items.get(i));
            }
        }
    }

    protected final void loadItems(ValueInput input) {
        clearContents();
        for (ValueInput child : input.childrenListOrEmpty("items")) {
            int slot = child.getIntOr("slot", -1);
            if (slot >= 0 && slot < items.size()) {
                items.set(slot, child.read("item", ItemStack.CODEC).orElse(ItemStack.EMPTY));
            }
        }
    }

    // An action from its settings panel. The player is allowed to use the rack's screen.
    public void handleAction(ServerPlayer player, int action, int value, String text) {}

    // A filter set from a panel: the item text names (dragged in from JEI), else one of what the player is carrying
    // (empty-handed: no filter).
    protected static ItemStack filter(ServerPlayer player, String text) {
        if (!text.isEmpty()) {
            Identifier id = Identifier.tryParse(text);
            return id == null ? ItemStack.EMPTY : BuiltInRegistries.ITEM.getOptional(id).map(ItemStack::new).orElse(ItemStack.EMPTY);
        }
        ItemStack carried = player.containerMenu.getCarried();
        return carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1);
    }

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
