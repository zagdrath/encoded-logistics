/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.midrange.PeripheralBlockEntity;
import net.zagdrath.encodedlogistics.net.MachinePayloads;

// A Midrange peripheral's screen (the Keypunch's, Card Reader's, Line Printer's): the machine's slots, then the player's
// inventory. It's a green screen (CrtMachineScreen), so a slot's x / y are where its frame is on the glass, in the
// CRT's virtual pixels from the text's corner (a column is 6, a row 10): the player's inventory at the lower right
// (column 51, rows 12-19). Whether the machine's online goes in a data slot; its messages, and the lines and numbers
// its screen shows, in MachinePayloads.Info.
public abstract class PeripheralMenu extends AbstractContainerMenu {
    public static final int INVENTORY_X = 51 * 6, INVENTORY_Y = 120, SLOT_W = 18, SLOT_H = 16;
    private static final int REFRESH = 20;

    // The header: the system's and device's names and the phosphor (PeripheralBlockEntity.writeOpening).
    public record Opening(String system, String device, String phosphor) {
        public static final Opening SERVER = new Opening("", "", "*GREEN");

        public static Opening read(RegistryFriendlyByteBuf buf) {
            return new Opening(buf.readUtf(), buf.readUtf(), buf.readUtf());
        }
    }

    protected final Container machine;
    protected final Player player;
    protected final @Nullable PeripheralBlockEntity peripheral;
    private final Opening opening;
    private final DataSlot online = DataSlot.standalone();
    private int timer;
    // On the client, from the server.
    private Component message = Component.empty();
    private List<String> lines = List.of();
    private List<Integer> numbers = List.of();
    private int received;

    protected PeripheralMenu(@Nullable MenuType<?> type, int containerId, Inventory inventory, Container machine, @Nullable PeripheralBlockEntity peripheral,
            Opening opening) {
        super(type, containerId);
        this.machine = machine;
        this.player = inventory.player;
        this.peripheral = peripheral;
        this.opening = opening;
        machine.startOpen(player);
        addDataSlot(online);
        if (peripheral != null) {
            online.set(peripheral.isOnline() ? 1 : 0);
        }
    }

    // The player's inventory, after the machine's slots: three rows, then the hotbar a little apart.
    protected void addPlayerSlots(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, 9 + row * 9 + col, INVENTORY_X + col * SLOT_W, INVENTORY_Y + row * 20));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, INVENTORY_X + col * SLOT_W, INVENTORY_Y + 64));
        }
    }

    public Opening opening() {
        return opening;
    }

    public boolean online() {
        return online.get() != 0;
    }

    // The first of the player's slots.
    protected int playerStart() {
        return slots.size() - 36;
    }

    // --- Server ---

    @Override
    public void broadcastChanges() {
        if (peripheral != null && ++timer >= REFRESH) {
            timer = 0;
            online.set(peripheral.isOnline() ? 1 : 0);
            refresh();
        }
        super.broadcastChanges();
    }

    // Every second while it's open (the printer's preview).
    protected void refresh() {}

    // A field typed on the screen.
    public void setText(int key, String text) {}

    // Tells the screen: the message line and its lines and numbers.
    protected void send(Component message, List<String> lines, List<Integer> numbers) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, new MachinePayloads.Info(containerId, message, lines, numbers));
        }
    }

    protected void send(Component message) {
        send(message, List.of(), List.of());
    }

    // --- Client ---

    public void receive(MachinePayloads.Info info) {
        if (!info.message().getString().isEmpty() || info.lines().isEmpty() && info.numbers().isEmpty()) {
            message = info.message();
        }
        if (!info.lines().isEmpty() || !info.numbers().isEmpty()) {
            lines = info.lines();
            numbers = info.numbers();
        }
        received++;
    }

    public Component message() {
        return message;
    }

    public void clearMessage() {
        message = Component.empty();
    }

    public List<String> lines() {
        return lines;
    }

    public int number(int i) {
        return i < numbers.size() ? numbers.get(i) : 0;
    }

    // How many answers have come (the screen notices new ones).
    public int received() {
        return received;
    }

    // --- Slots ---

    // Shift-click: the machine's to the player's inventory; the player's into the first machine slot that takes it.
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int start = playerStart();
        if (index < start) {
            if (!moveItemStackTo(stack, start, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            boolean moved = false;
            for (int target = 0; target < start && !stack.isEmpty(); target++) {
                Slot into = slots.get(target);
                if (!(into instanceof GhostSlot) && into.mayPlace(stack)) {
                    moved |= moveItemStackTo(stack, target, target + 1, false);
                }
            }
            if (!moved) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return machine.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        machine.stopOpen(player);
    }

    // A machine slot that takes what its container allows (one at a time where the container says so).
    protected static class MachineSlot extends Slot {
        public MachineSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return container.canPlaceItem(getContainerSlot(), stack);
        }
    }
}
