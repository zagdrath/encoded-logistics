/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.model.data.ModelData;
import net.neoforged.neoforge.model.data.ModelProperty;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.menu.DriveBayMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.storage.DriveHolder;

// A Drive Bay's ten drive slots (two columns of five, slot i in column i / 5, row i % 5). A drive gets its id the first
// time it goes in; its stats are refreshed from DriveStorage whenever the bay or the network changes it. The client gets
// the drives and whether the bay is online, and the model draws each drive's sled and status light from SLEDS.
public class DriveBayBlockEntity extends BaseContainerBlockEntity implements NetworkDevice, DriveHolder {
    public static final int SLOTS = 10;
    // Per slot: -1 empty, else tier ordinal * 8 + light (0 green, 1 yellow, 2 orange, 3 red, 4 off).
    public static final ModelProperty<int[]> SLEDS = new ModelProperty<>();
    public static final int LIGHT_OFF = 4;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private boolean online;
    private int drives = -1;
    private int[] sleds = new int[0];

    // For the screen: whether the bay is online.
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return online ? 1 : 0;
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return 1;
        }
    };

    public DriveBayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.DRIVE_BAY.get(), pos, state);
    }

    public boolean isOnline() {
        return online;
    }

    public int driveCount() {
        int count = 0;
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void setNetworkOnline(boolean online) {
        if (this.online != online) {
            this.online = online;
            sync();
        }
    }

    // --- Container ---

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.encodedlogistics.drive_bay");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.getItem() instanceof StorageDriveItem;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new DriveBayMenu(containerId, inventory, this, data);
    }

    // The drives changed (put in, taken out): give new ones their id, refresh their stats, redraw the bay, and let the
    // network know when the count changed (it drains per drive).
    @Override
    public void setChanged() {
        super.setChanged();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (int slot = 0; slot < SLOTS; slot++) {
            refreshDrive(serverLevel, slot);
        }
        int count = driveCount();
        if (count != drives) {
            if (drives >= 0) {
                ControllerStructures.get(serverLevel).markTopologyChanged();
            }
            drives = count;
        }
        sync();
    }

    @Override
    public int driveSlots() {
        return SLOTS;
    }

    // The network put items in or took them out of the drive in a slot.
    @Override
    public void driveChanged(int slot) {
        if (level instanceof ServerLevel serverLevel) {
            refreshDrive(serverLevel, slot);
            super.setChanged();
            sync();
        }
    }

    private void refreshDrive(ServerLevel level, int slot) {
        StorageDriveItem.refresh(level.getServer(), items.get(slot));
    }

    // The drive in a slot as the network uses it, or null when the slot is empty.
    @Override
    public @Nullable ItemStack drive(int slot) {
        ItemStack stack = items.get(slot);
        return stack.getItem() instanceof StorageDriveItem && StorageDriveItem.id(stack) != null ? stack : null;
    }

    // --- The model's sleds ---

    private int[] computeSleds() {
        int[] codes = new int[SLOTS];
        for (int slot = 0; slot < SLOTS; slot++) {
            codes[slot] = StorageDriveItem.sledCode(items.get(slot), online);
        }
        return codes;
    }

    // Server: send the bay to clients when what they draw changed.
    private void sync() {
        int[] codes = computeSleds();
        if (!Arrays.equals(codes, sleds) && level != null) {
            sleds = codes;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public ModelData getModelData() {
        return ModelData.of(SLEDS, computeSleds());
    }

    // --- Saving and syncing ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        online = input.getBooleanOr("online", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveCustomOnly(registries);
        if (online) {
            tag.putBoolean("online", true);
        }
        return tag;
    }

    @Override
    public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection connection, ValueInput input) {
        super.onDataPacket(connection, input);
        remesh();
    }

    @Override
    public void handleUpdateTag(ValueInput input) {
        super.handleUpdateTag(input);
        remesh();
    }

    private void remesh() {
        requestModelDataUpdate();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }
}
