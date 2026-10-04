/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

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
import net.zagdrath.encodedlogistics.rack.device.FirewallDevice;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// The Server Rack's screen: the elevation (or the settings panel of the device picked in it) above the player's
// inventory. Which unit is picked (0: none, the elevation shows) is a data slot. The Router's three transceiver cages
// are real slots, there only while a Router is picked.
//
// Buttons (clickMenuButton): PICK + u picks the device at u (PICK alone goes back), TAKE + u takes the device at u out
// into the inventory, PUT + u mounts the carried device at u. A picked device's panel gets its state every
// SYNC_INTERVAL ticks (RackPanelPayload) and sends actions with RackActionPayload.
public class RackMenu extends AbstractContainerMenu {
    public static final int WIDTH = 176, TOP_HEIGHT = 168, HEIGHT = TOP_HEIGHT + 100;
    public static final int PICK = 0, TAKE = 100, PUT = 200;
    public static final int[] CAGE_X = { 9, 27, 45 };
    public static final int CAGE_Y = 129;
    private static final int SYNC_INTERVAL = 10;
    private static final int INVENTORY_SLOTS = 36;

    private final BlockPos pos;
    private final Player player;
    private final @Nullable RackBlockEntity rack;
    private final DataSlot picked = DataSlot.standalone();
    private int ticksUntilSync;

    // Client constructor, with the rack's master position written by the server.
    public RackMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), null);
    }

    public RackMenu(int containerId, Inventory inventory, RackBlockEntity rack) {
        this(containerId, inventory, rack.getBlockPos(), rack);
    }

    private RackMenu(int containerId, Inventory inventory, BlockPos pos, @Nullable RackBlockEntity serverRack) {
        super(ModMenuTypes.SERVER_RACK.get(), containerId);
        this.pos = pos;
        this.player = inventory.player;
        this.rack = serverRack;
        addStandardInventorySlots(inventory, 8, TOP_HEIGHT + 17);
        Container cages = serverRack != null ? new Cages() : new SimpleContainer(RouterDevice.CAGES);
        for (int i = 0; i < RouterDevice.CAGES; i++) {
            addSlot(new CageSlot(cages, i, CAGE_X[i], CAGE_Y));
        }
        addDataSlot(picked);
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

    private @Nullable RouterDevice pickedRouter() {
        return pickedDevice() instanceof RouterDevice router ? router : null;
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

    // An action from the picked device's panel. The Firewall decides who may change it; everything else needs build
    // permission on the rack's network.
    public void handleAction(ServerPlayer player, int u, int action, int value, String text) {
        RackDevice device = rack != null && u == picked.get() ? rack.deviceAt(u) : null;
        if (device == null) {
            return;
        }
        if (!(device instanceof FirewallDevice) && !NetworkAccess.check(player.level(), pos, player, RackPermission.BUILD)) {
            return;
        }
        device.handleAction(player, action, value, text);
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

    // From the inventory: a rack device goes to the lowest place it fits, a transceiver into a free cage of the picked
    // Router. From a cage: back to the inventory.
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
        if (RouterDevice.isTransceiver(stack) && pickedRouter() != null) {
            ItemStack copy = stack.copy();
            if (moveItemStackTo(stack, INVENTORY_SLOTS, INVENTORY_SLOTS + RouterDevice.CAGES, false)) {
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

    // --- The Router's cages ---

    // The picked Router's transceivers (server side).
    private final class Cages implements Container {
        private @Nullable RouterDevice router() {
            return pickedRouter();
        }

        @Override
        public int getContainerSize() {
            return RouterDevice.CAGES;
        }

        @Override
        public boolean isEmpty() {
            RouterDevice router = router();
            return router == null || router.transceivers().stream().allMatch(ItemStack::isEmpty);
        }

        @Override
        public ItemStack getItem(int slot) {
            RouterDevice router = router();
            return router != null ? router.transceivers().get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int count) {
            RouterDevice router = router();
            if (router == null || router.transceivers().get(slot).isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = router.transceivers().get(slot).split(count);
            router.transceiversChanged();
            return taken;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            RouterDevice router = router();
            if (router == null) {
                return ItemStack.EMPTY;
            }
            ItemStack taken = router.transceivers().get(slot);
            router.transceivers().set(slot, ItemStack.EMPTY);
            return taken;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            RouterDevice router = router();
            if (router != null) {
                router.transceivers().set(slot, stack);
                router.transceiversChanged();
            }
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public void setChanged() {
            RouterDevice router = router();
            if (router != null) {
                router.transceiversChanged();
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {}
    }

    private final class CageSlot extends Slot {
        CageSlot(Container container, int slot, int x, int y) {
            super(container, slot, x, y);
        }

        @Override
        public boolean isActive() {
            return pickedRouter() != null;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return isActive() && RouterDevice.isTransceiver(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return isActive();
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }
}
