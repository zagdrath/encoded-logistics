/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.IntUnaryOperator;

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
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.net.TerminalItemsPayload;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// The Access Terminal screen's menu: the player's inventory below a grid of the network's items. The grid isn't slots:
// the server sends the network's items (what changed since last time, every SYNC_INTERVAL ticks) and the client asks
// for clicks with TerminalClickPayload. The grid's height (rows) is the client's choice, made when the menu opens, so
// the inventory slots sit under it; the server doesn't care where they are.
public class AccessTerminalMenu extends AbstractContainerMenu {
    public static final int TOP = 19, ROW = 18, BOTTOM = 99, COLUMNS = 9, DEFAULT_ROWS = 6;
    private static final int SYNC_INTERVAL = 5;

    // Click actions, as AE2's terminals have them: left-click takes a stack, right-click half of one, shift-click a stack
    // into the inventory, shift-right-click (or Shift+wheel down) one onto the cursor; with an item carried, left-click
    // puts it all in and right-click (or Shift+wheel up) one; double-clicking an inventory stack puts every stack like it
    // in, along with the one picked up by the first click.
    public static final int TAKE_STACK = 0, TAKE_HALF = 1, TAKE_TO_INVENTORY = 2, INSERT_CARRIED = 3, INSERT_ONE = 4, TAKE_ONE = 5,
            INSERT_ALL_LIKE_CARRIED = 6;

    // The player's inventory slots come first (0-35); terminals with a crafting section add theirs after.
    public static final int INVENTORY_SLOTS = 36;

    // How many grid rows fit the client's window, given the height of any extra section; set by the client.
    public static IntUnaryOperator clientRows = section -> DEFAULT_ROWS;

    // The device it reaches the network through: the block the terminal is on (a Handheld Terminal's: the Relay Antenna
    // it's using, which can change as the player moves).
    protected BlockPos pos;
    protected final Direction side;
    private final int rows;
    protected final Player player;

    // Server: what the client was last sent.
    private @Nullable Map<ItemKey, Long> sent;
    private @Nullable Set<ItemKey> sentCraftables;
    private boolean sentOnline;
    private int ticksUntilSync;

    // Client: the network's items and craftables as last received.
    private final Map<ItemKey, Long> items = new HashMap<>();
    private final Set<ItemKey> craftables = new LinkedHashSet<>();
    private boolean online;
    private int version;

    // Client constructor, with the terminal's position and side written by the server.
    public AccessTerminalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(ModMenuTypes.ACCESS_TERMINAL.get(), containerId, inventory, extraData.readBlockPos(), extraData.readEnum(Direction.class),
                clientRows.applyAsInt(0), 0);
    }

    public AccessTerminalMenu(int containerId, Inventory inventory, BlockPos pos, Direction side) {
        this(ModMenuTypes.ACCESS_TERMINAL.get(), containerId, inventory, pos, side, DEFAULT_ROWS, 0);
    }

    // section: the height of a section between the grid and the inventory (the Fabrication Terminal's crafting grid).
    protected AccessTerminalMenu(MenuType<?> type, int containerId, Inventory inventory, BlockPos pos, Direction side, int rows, int section) {
        super(type, containerId);
        this.pos = pos;
        this.side = side;
        this.rows = rows;
        this.player = inventory.player;
        addStandardInventorySlots(inventory, 9, TOP + rows * ROW + section + 17);
    }

    public int rows() {
        return rows;
    }

    // The block the terminal is on (a cable or part host), or for a Handheld Terminal the Relay Antenna it uses.
    public BlockPos pos() {
        return pos;
    }

    // Items moved in or out of the network by a click (a Handheld Terminal pays for them).
    protected void moved(int items) {}

    // Opens the terminal on that side of the block at pos (a cable or a part host).
    public static void open(ServerPlayer player, BlockPos pos, Direction side) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new AccessTerminalMenu(id, inventory, pos, side),
                Component.translatable("gui.encodedlogistics.access_terminal")), buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeEnum(side);
                });
    }

    // --- Server ---

    // The network's storage as this player may see it: none without the Firewall's view permission.
    protected @Nullable NetworkStorage storage() {
        return player.level() instanceof ServerLevel level && allowed(RackPermission.VIEW) ? ControllerStructures.get(level).storageAt(level, pos)
                : null;
    }

    // The storage, for putting items in (INSERT) or taking them out (EXTRACT), if the Firewall allows it.
    protected @Nullable NetworkStorage storageFor(RackPermission permission) {
        return allowed(permission) ? storage() : null;
    }

    // Whether the network's Firewall (if any) lets this player do that through the terminal.
    protected boolean allowed(RackPermission permission) {
        return !(player.level() instanceof ServerLevel level) || NetworkAccess.allowed(level, pos, player, permission);
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
        Set<ItemKey> craftable = isOnline && player.level() instanceof ServerLevel level ? CraftRequests.craftables(level, pos) : Set.of();
        boolean craftablesChanged = !craftable.equals(sentCraftables);
        if (full || !changes.isEmpty() || isOnline != sentOnline || craftablesChanged) {
            PacketDistributor.sendToPlayer(serverPlayer, new TerminalItemsPayload(containerId, isOnline, full, changes,
                    craftablesChanged ? Optional.of(List.copyOf(craftable)) : Optional.empty()));
        }
        sent = new HashMap<>(now);
        sentCraftables = craftable;
        sentOnline = isOnline;
    }

    // A click on the grid: take the clicked item (a stack, half a stack, one, or a stack into the inventory) or put the
    // carried stack (or one of it) in.
    public void handleClick(ServerPlayer player, @Nullable ItemKey key, int action) {
        RackPermission permission = action == INSERT_CARRIED || action == INSERT_ONE || action == INSERT_ALL_LIKE_CARRIED ? RackPermission.INSERT
                : RackPermission.EXTRACT;
        NetworkStorage storage = storage();
        if (storage == null || !NetworkAccess.tell(allowed(permission), player, permission)) {
            return;
        }
        ItemStack carried = getCarried();
        switch (action) {
            case INSERT_CARRIED, INSERT_ONE -> {
                if (carried.isEmpty()) {
                    return;
                }
                int amount = action == INSERT_ONE ? 1 : carried.getCount();
                int stored = (int) storage.insert(ItemKey.of(carried), amount, false);
                carried.shrink(stored);
                setCarried(carried);
                moved(stored);
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
                    moved(taken);
                }
            }
            case INSERT_ALL_LIKE_CARRIED -> {
                if (carried.isEmpty()) {
                    return;
                }
                ItemKey like = ItemKey.of(carried);
                int stored = (int) storage.insert(like, carried.getCount(), false);
                carried.shrink(stored);
                setCarried(carried);
                for (int i = 0; i < INVENTORY_SLOTS; i++) {
                    Slot slot = slots.get(i);
                    ItemStack stack = slot.getItem();
                    if (!stack.isEmpty() && like.equals(ItemKey.of(stack))) {
                        int in = (int) storage.insert(like, stack.getCount(), false);
                        stack.shrink(in);
                        slot.setChanged();
                        stored += in;
                    }
                }
                moved(stored);
            }
            case TAKE_ONE -> {
                // Onto the cursor: an empty one, or one carrying the same item with room for another.
                if (key == null || !carried.isEmpty() && (!key.equals(ItemKey.of(carried)) || carried.getCount() >= carried.getMaxStackSize())) {
                    return;
                }
                if (storage.extract(key, 1, false) > 0) {
                    if (carried.isEmpty()) {
                        setCarried(key.toStack(1));
                    } else {
                        carried.grow(1);
                        setCarried(carried);
                    }
                    moved(1);
                }
            }
            case TAKE_TO_INVENTORY -> {
                if (key == null) {
                    return;
                }
                int amount = (int) Math.min(storage.count(key), key.maxStackSize());
                ItemStack stack = key.toStack((int) storage.extract(key, amount, false));
                int taken = stack.getCount();
                moveItemStackTo(stack, 0, INVENTORY_SLOTS, true);
                if (!stack.isEmpty()) {
                    // What didn't fit goes back.
                    storage.insert(ItemKey.of(stack), stack.getCount(), false);
                }
                moved(taken - stack.getCount());
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
        if (storage == null || !slot.hasItem() || !NetworkAccess.tell(allowed(RackPermission.INSERT), player, RackPermission.INSERT)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        int stored = (int) storage.insert(ItemKey.of(stack), stack.getCount(), false);
        stack.shrink(stored);
        slot.setChanged();
        moved(stored);
        // The grid shows it straight away (vanilla broadcasts after the click), not at the next sync.
        ticksUntilSync = 0;
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockEntity(pos) instanceof CableBlockEntity host && host.getAttachments().part(side) != null
                && host.getAttachments().part(side).isTerminal() && player.isWithinBlockInteractionRange(pos, 4.0);
    }

    // --- Client ---

    public void applyUpdate(boolean online, boolean full, List<TerminalItemsPayload.Entry> entries, @Nullable List<ItemKey> craftables) {
        this.online = online;
        if (craftables != null) {
            this.craftables.clear();
            this.craftables.addAll(craftables);
        }
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

    // What the network can craft.
    public Set<ItemKey> craftables() {
        return craftables;
    }

    public boolean isOnline() {
        return online;
    }

    // Goes up with every update, so the screen knows when to rebuild its list.
    public int version() {
        return version;
    }
}
