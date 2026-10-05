/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntSupplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueOutput;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.net.RackPanelPayload;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.RackSlot;
import net.zagdrath.encodedlogistics.rack.device.FirewallDevice;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// The Server Rack's screen: the elevation (or the settings panel of the device picked in it) above the player's
// inventory. Which unit is picked (0: none, the elevation shows) is a data slot. Each device type with item slots
// (RackDeviceType#slots) has a bank of real slots here, at its panel's positions, there only while a device of that
// type is picked and holding its items.
//
// Buttons (clickMenuButton): PICK + u picks the device at u (PICK alone goes back), TAKE + u takes the device at u out
// into the inventory, PUT + u mounts the carried device at u. A picked device's panel gets its state every
// SYNC_INTERVAL ticks (RackPanelPayload) and sends actions with RackActionPayload.
public class RackMenu extends AbstractContainerMenu {
    // TOP_HEIGHT: the top half with the elevation's ROWS rows; it grows ROW_H a row as the client's window has room.
    public static final int WIDTH = 176, TOP_HEIGHT = 168, INVENTORY_HEIGHT = 100, ROWS = 16, ROW_H = 9;
    public static final int PICK = 0, TAKE = 100, PUT = 200;
    private static final int SYNC_INTERVAL = 10;
    public static final int INVENTORY_SLOTS = 36;

    // At most this many elevation rows, however tall the window: past it the elevation scrolls.
    public static final int MAX_ROWS = 24;
    // How many elevation rows fit the client's window (ROWS to MAX_ROWS); set by the client.
    public static IntSupplier clientRows = () -> ROWS;

    private final BlockPos pos;
    private final Player player;
    private final @Nullable RackBlockEntity rack;
    private final DataSlot picked = DataSlot.standalone();
    private final int rows;
    // Each device type's first slot index.
    private final Map<RackDeviceType, Integer> banks = new LinkedHashMap<>();
    private int ticksUntilSync;
    // Client: which of the picked device's slots its panel shows - none on a page without them, and of a scrolling list
    // (RackSlot#scrollRow) the rows from windowFirst, windowRows of them.
    private boolean slotsShown = true;
    private int windowFirst, windowRows = Integer.MAX_VALUE;

    // Client constructor, with the rack's master position written by the server.
    public RackMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), null, clientRows.getAsInt());
    }

    public RackMenu(int containerId, Inventory inventory, RackBlockEntity rack) {
        this(containerId, inventory, rack.getBlockPos(), rack, ROWS);
    }

    private RackMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable RackBlockEntity serverRack, int rows) {
        super(ModMenuTypes.SERVER_RACK.get(), containerId);
        this.pos = pos;
        this.player = inventory.player;
        this.rack = serverRack;
        this.rows = rows;
        addStandardInventorySlots(inventory, 8, topHeight() + 17);
        for (RackDeviceType type : RackDeviceType.all()) {
            if (type.slots().isEmpty()) {
                continue;
            }
            banks.put(type, slots.size());
            Container container = serverRack != null ? new DeviceItems(type) : new SimpleContainer(type.slots().size());
            for (int i = 0; i < type.slots().size(); i++) {
                addSlot(new DeviceSlot(container, i, type));
            }
        }
        addDataSlot(picked);
    }

    // The elevation's rows, and the top half's height with them.
    public int rows() {
        return rows;
    }

    public int topHeight() {
        return TOP_HEIGHT + (rows - ROWS) * ROW_H;
    }

    public BlockPos pos() {
        return pos;
    }

    // The rack: the server's, or the client's copy.
    public @Nullable RackBlockEntity rack() {
        if (rack != null) {
            return rack;
        }
        return player.level().getBlockEntity(pos) instanceof RackBlockEntity clientRack ? clientRack : null;
    }

    public int picked() {
        return picked.get();
    }

    public @Nullable RackDevice pickedDevice() {
        RackBlockEntity at = rack();
        return at != null && picked.get() > 0 ? at.deviceAt(picked.get()) : null;
    }

    // Client: which of the picked device's slots show (its panel says, as it pages and scrolls).
    public void setSlotWindow(boolean shown, int first, int rows) {
        slotsShown = shown;
        windowFirst = first;
        windowRows = rows;
    }

    public void resetSlotWindow() {
        setSlotWindow(true, 0, Integer.MAX_VALUE);
    }

    // The menu slot showing the picked device's i-th item slot, or null.
    public @Nullable Slot deviceSlot(int i) {
        RackDevice device = pickedDevice();
        Integer start = device != null ? banks.get(device.type()) : null;
        return start != null && i < device.type().slots().size() ? slots.get(start + i) : null;
    }

    // --- Buttons ---

    @Override
    public boolean clickMenuButton(Player player, int id) {
        RackBlockEntity at = rack;
        if (at == null || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }
        if (id >= PICK && id <= PICK + 42) {
            int u = id - PICK;
            RackDevice device = u > 0 ? at.deviceAt(u) : null;
            picked.set(device != null ? device.u() : 0);
            ticksUntilSync = 0;
            return true;
        }
        if (id > TAKE && id <= TAKE + 42) {
            if (picked.get() > 0 && at.deviceAt(picked.get()) != null && at.deviceAt(picked.get()).occupies(id - TAKE)) {
                picked.set(0);
            }
            return at.takeOut(serverPlayer, id - TAKE);
        }
        if (id > PUT && id <= PUT + 42) {
            ItemStack carried = getCarried();
            if (RackDeviceType.of(carried) == null) {
                return false;
            }
            boolean done = at.installFromHand(serverPlayer, carried, id - PUT);
            setCarried(carried);
            return done;
        }
        return false;
    }

    // An action from the picked device's panel. The Firewall decides who may change it; everything else needs rack
    // access on the rack's network.
    public void handleAction(ServerPlayer player, int u, int action, int value, String text) {
        RackDevice device = rack != null && u == picked.get() ? rack.deviceAt(u) : null;
        if (device == null) {
            return;
        }
        if (!(device instanceof FirewallDevice) && !NetworkAccess.guard(player.level(), pos, player, RackPermission.RACK)) {
            return;
        }
        if (action == RackDevice.ACTION_PRIORITY) {
            device.setLanePriority(device.lanePriority().next());
        } else {
            device.handleAction(player, action, value, text);
        }
        ticksUntilSync = 0;
    }

    // --- Syncing the panel ---

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer serverPlayer) || !(serverPlayer.level() instanceof ServerLevel level) || --ticksUntilSync > 0) {
            return;
        }
        ticksUntilSync = SYNC_INTERVAL;
        RackDevice device = pickedDevice();
        if (device == null) {
            if (picked.get() != 0) {
                picked.set(0);
            }
            return;
        }
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        device.writePanel(output, serverPlayer);
        PacketDistributor.sendToPlayer(serverPlayer, new RackPanelPayload(containerId, device.u(), output.buildResult()));
    }

    // --- Moving items ---

    // From the inventory: a rack device goes to the lowest place it fits, anything else into the picked device's slots
    // that take it. From a device's slot: back to the inventory.
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        if (index >= INVENTORY_SLOTS) {
            ItemStack copy = stack.copy();
            if (!moveItemStackTo(stack, 0, INVENTORY_SLOTS, true)) {
                return ItemStack.EMPTY;
            }
            slot.setChanged();
            return copy;
        }
        RackDeviceType type = RackDeviceType.of(stack);
        if (type != null && rack != null && player instanceof ServerPlayer serverPlayer) {
            int u = rack.lowestFit(type.size());
            if (u > 0 && rack.installFromHand(serverPlayer, stack, u)) {
                slot.setChanged();
            }
            return ItemStack.EMPTY;
        }
        RackDevice device = pickedDevice();
        Integer start = device != null ? banks.get(device.type()) : null;
        if (start != null) {
            ItemStack copy = stack.copy();
            if (moveItemStackTo(stack, start, start + device.type().slots().size(), false)) {
                slot.setChanged();
                return copy;
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        RackBlockEntity at = rack();
        return at != null && !at.isRemoved() && player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= 64;
    }

    // --- Devices' item slots ---

    // The picked device's items when it's of this type (server side).
    private final class DeviceItems implements Container {
        private final RackDeviceType type;

        DeviceItems(RackDeviceType type) {
            this.type = type;
        }

        private @Nullable RackDevice device() {
            RackDevice device = pickedDevice();
            return device != null && device.type() == type ? device : null;
        }

        @Override
        public int getContainerSize() {
            return type.slots().size();
        }

        @Override
        public boolean isEmpty() {
            RackDevice device = device();
            return device == null || device.items().stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int slot) {
            RackDevice device = device();
            return device != null ? device.items().get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            RackDevice device = device();
            if (device == null || device.items().get(slot).isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = device.items().get(slot).split(count);
            device.itemsChanged();
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            RackDevice device = device();
            if (device == null) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = device.items().get(slot);
            device.items().set(slot, ItemStack.EMPTY);
            return taken;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            RackDevice device = device();
            if (device != null) {
                device.items().set(slot, stack);
                device.itemsChanged();
            }
        }

        @Override
        public int getMaxStackSize() {
            return 64;
        }

        @Override
        public void setChanged() {
            RackDevice device = device();
            if (device != null) {
                device.itemsChanged();
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {}
    }

    private final class DeviceSlot extends Slot {
        private final RackDeviceType type;
        private final RackSlot spec;

        DeviceSlot(Container container, int slot, RackDeviceType type) {
            super(container, slot, type.slots().get(slot).x(), type.slots().get(slot).y());
            this.type = type;
            this.spec = type.slots().get(slot);
        }

        // On the client also where the panel shows it (the server takes clicks on any of the picked device's slots).
        @Override
        public boolean isActive() {
            RackDevice device = pickedDevice();
            if (device == null || device.type() != type) {
                return false;
            }
            if (!player.level().isClientSide()) {
                return true;
            }
            return slotsShown && (spec.scrollRow() < 0 || spec.scrollRow() >= windowFirst && spec.scrollRow() - windowFirst < windowRows);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isActive() && spec.accepts().test(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return isActive();
        }

        @Override
        public int getMaxStackSize() {
            return spec.maxStack();
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return Math.min(spec.maxStack(), stack.getMaxStackSize());
        }
    }
}
