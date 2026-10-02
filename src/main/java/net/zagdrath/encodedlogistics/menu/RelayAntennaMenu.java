/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.blockentity.RelayAntennaBlockEntity;
import net.zagdrath.encodedlogistics.net.RelayTerminalsPayload;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// The Relay Antenna's screen (screens/relay_antenna.json): its range, four Optical Transceiver slots, the linked
// terminals in range (sent every SYNC_INTERVAL ticks) and the player's inventory. data: range.
public class RelayAntennaMenu extends AbstractContainerMenu {
    public static final int[] SLOT_X = { 107, 124, 141, 158 };
    public static final int SLOT_Y = 17, INVENTORY_Y = 94;
    private static final int SYNC_INTERVAL = 20;

    private final Container relay;
    private final ContainerData data;
    private final @Nullable BlockPos pos;
    private final Player player;
    private @Nullable List<RelayAntennaBlockEntity.Linked> sent;
    private int ticksUntilSync;

    // Client: the linked terminals as last received.
    private List<RelayAntennaBlockEntity.Linked> linked = List.of();

    public RelayAntennaMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(RelayAntennaBlockEntity.SLOTS), new SimpleContainerData(1), null);
    }

    public RelayAntennaMenu(int containerId, Inventory inventory, Container relay, ContainerData data, @Nullable BlockPos pos) {
        super(ModMenuTypes.RELAY_ANTENNA.get(), containerId);
        checkContainerSize(relay, RelayAntennaBlockEntity.SLOTS);
        checkContainerDataCount(data, 1);
        this.relay = relay;
        this.data = data;
        this.pos = pos;
        this.player = inventory.player;
        relay.startOpen(inventory.player);
        for (int slot = 0; slot < RelayAntennaBlockEntity.SLOTS; slot++) {
            addSlot(new Slot(relay, slot, SLOT_X[slot], SLOT_Y) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return stack.is(ModItems.OPTICAL_TRANSCEIVER.get());
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        addDataSlots(data);
    }

    public int range() {
        return data.get(0);
    }

    public List<RelayAntennaBlockEntity.Linked> linked() {
        return linked;
    }

    public void setLinked(List<RelayAntennaBlockEntity.Linked> linked) {
        this.linked = List.copyOf(linked);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (pos == null || !(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level) || --ticksUntilSync > 0) {
            return;
        }
        ticksUntilSync = SYNC_INTERVAL;
        if (level.getBlockEntity(pos) instanceof RelayAntennaBlockEntity antenna) {
            List<RelayAntennaBlockEntity.Linked> now = antenna.linkedTerminals(level);
            if (!now.equals(sent)) {
                sent = now;
                PacketDistributor.sendToPlayer(serverPlayer, new RelayTerminalsPayload(containerId, now));
            }
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        int inventory = RelayAntennaBlockEntity.SLOTS;
        if (index < inventory) {
            if (!moveItemStackTo(stack, inventory, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!stack.is(ModItems.OPTICAL_TRANSCEIVER.get()) || !moveItemStackTo(stack, 0, inventory, false)) {
            return ItemStack.EMPTY;
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
        return relay.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        relay.stopOpen(player);
    }
}
