/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.net.TerminalItemsPayload;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// The Access Terminal screen's menu: the player's inventory below a grid of the network's items. The grid isn't slots:
// the server sends the network's items (what changed since last time, every SYNC_INTERVAL ticks) and the client asks
// for clicks with TerminalClickPayload. The grid's height (rows) is the client's choice, made when the menu opens, so
// the inventory slots sit under it; the server doesn't care where they are.
public class AccessTerminalMenu extends AbstractContainerMenu {
    public static final int TOP = 19, ROW = 18, BOTTOM = 99, COLUMNS = 9, DEFAULT_ROWS = 6;
    private static final int SYNC_INTERVAL = 10;

    // Click actions.
    public static final int TAKE_STACK = 0, TAKE_HALF = 1, TAKE_TO_INVENTORY = 2, INSERT_CARRIED = 3, INSERT_ONE = 4;

    // How many grid rows fit the client's window; set by the client.
    public static IntSupplier clientRows = () -> DEFAULT_ROWS;

    private final BlockPos pos;
    private final Direction side;
    private final int rows;
    private final Player player;

    // Server: what the client was last sent.
    private @Nullable Map<ItemKey, Long> sent;
    private boolean sentOnline;
    private int ticksUntilSync;

    // Client: the network's items as last received.
    private final Map<ItemKey, Long> items = new HashMap<>();
    private boolean online;
    private int version;

    // Client constructor, with the terminal's position and side written by the server.
    public AccessTerminalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), extraData.readEnum(Direction.class), clientRows.getAsInt());
    }

    public AccessTerminalMenu(int containerId, Inventory inventory, BlockPos pos, Direction side) {
        this(containerId, inventory, pos, side, DEFAULT_ROWS);
    }

    private AccessTerminalMenu(int containerId, Inventory inventory, BlockPos pos, Direction side, int rows) {
        super(ModMenuTypes.ACCESS_TERMINAL.get(), containerId);
        this.pos = pos;
        this.side = side;
        this.rows = rows;
        this.player = inventory.player;
        addStandardInventorySlots(inventory, 9, TOP + rows * ROW + 17);
    }

    public int rows() {
        return rows;
    }

    // Opens the terminal on that side of the block at pos (a cable or a part host).
    public static void open(ServerPlayer player, BlockPos pos, Direction side) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new AccessTerminalMenu(id, inventory, pos, side),
                Component.translatable("gui.encodedlogistics.access_terminal")), buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeEnum(side);
                });
    }

    // --- Server ---

    private @Nullable NetworkStorage storage() {
        return player.level() instanceof ServerLevel level ? ControllerStructures.get(level).storageAt(level, pos) : null;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(player instanceof ServerPlayer serverPlayer) || --ticksUntilSync > 0) {
            return;
        }
        ticksUntilSync = SYNC_INTERVAL;
        NetworkStorage storage = storage();
        Map<ItemKey, Long> now = storage != null ? storage.list() : Map.of();
        boolean isOnline = storage != null;
        List<TerminalItemsPayload.Entry> changes = new ArrayList<>();
        boolean full = sent == null;
        if (full) {
            now.forEach((key, count) -> changes.add(new TerminalItemsPayload.Entry(key, count)));
        } else {
            now.forEach((key, count) -> {
                if (!count.equals(sent.get(key))) {
                    changes.add(new TerminalItemsPayload.Entry(key, count));
                }
            });
            sent.keySet().forEach(key -> {
                if (!now.containsKey(key)) {
                    changes.add(new TerminalItemsPayload.Entry(key, 0));
                }
            });
        }
        if (full || !changes.isEmpty() || isOnline != sentOnline) {
            PacketDistributor.sendToPlayer(serverPlayer, new TerminalItemsPayload(containerId, isOnline, full, changes));
        }
        sent = new HashMap<>(now);
        sentOnline = isOnline;
    }

    // A click on the grid: take the clicked item (a stack, half a stack, or into the inventory) or put the carried
    // stack (or one of it) in.
    public void handleClick(ServerPlayer player, @Nullable ItemKey key, int action) {
        NetworkStorage storage = storage();
        if (storage == null) {
            return;
        }
        ItemStack carried = getCarried();
        switch (action) {
            case INSERT_CARRIED, INSERT_ONE -> {
                if (carried.isEmpty()) {
                    return;
                }
                int amount = action == INSERT_ONE ? 1 : carried.getCount();
                carried.shrink((int) storage.insert(ItemKey.of(carried), amount, false));
                setCarried(carried);
            }
            case TAKE_STACK, TAKE_HALF -> {
                if (key == null || !carried.isEmpty()) {
                    return;
                }
                long available = storage.count(key);
                int amount = (int) Math.min(available, key.maxStackSize());
                if (action == TAKE_HALF) {
                    amount = (amount + 1) / 2;
                }
                int taken = (int) storage.extract(key, amount, false);
                if (taken > 0) {
                    setCarried(key.toStack(taken));
                }
            }
            case TAKE_TO_INVENTORY -> {
                if (key == null) {
                    return;
                }
                int amount = (int) Math.min(storage.count(key), key.maxStackSize());
                ItemStack stack = key.toStack((int) storage.extract(key, amount, false));
                moveItemStackTo(stack, 0, slots.size(), true);
                if (!stack.isEmpty()) {
                    // What didn't fit goes back.
                    storage.insert(ItemKey.of(stack), stack.getCount(), false);
                }
            }
            default -> {}
        }
        ticksUntilSync = 0;
        broadcastChanges();
    }

    // Shift-clicking a stack in the inventory puts it into the network.
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        NetworkStorage storage = storage();
        if (storage == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        stack.shrink((int) storage.insert(ItemKey.of(stack), stack.getCount(), false));
        slot.setChanged();
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof CableBlockEntity host && host.getAttachments().terminal(side)
                && player.isWithinBlockInteractionRange(pos, 4.0);
    }

    // --- Client ---

    public void applyUpdate(boolean online, boolean full, List<TerminalItemsPayload.Entry> entries) {
        this.online = online;
        if (full) {
            items.clear();
        }
        for (TerminalItemsPayload.Entry entry : entries) {
            if (entry.count() <= 0) {
                items.remove(entry.key());
            } else {
                items.put(entry.key(), entry.count());
            }
        }
        version++;
    }

    public Map<ItemKey, Long> items() {
        return items;
    }

    public boolean isOnline() {
        return online;
    }

    // Goes up with every update, so the screen knows when to rebuild its list.
    public int version() {
        return version;
    }
}
