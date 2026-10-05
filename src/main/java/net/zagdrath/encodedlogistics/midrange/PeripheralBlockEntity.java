/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.ListedDevice;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// A Midrange peripheral's block entity (the Keypunch's, Card Reader's, Line Printer's; on a footprint's master): its
// items, its device name (with the item), and whether it's working: online while a Midrange System on its network is
// (Midranges.host) - beside one, or cabled to its network. ACTIVE shows on the block for a while after it punches,
// reads or prints.
public abstract class PeripheralBlockEntity extends BaseContainerBlockEntity implements MidrangeDevice, ListedDevice {
    private NonNullList<ItemStack> items;
    private String deviceName = "";
    private int active;

    protected PeripheralBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state, int size) {
        super(type, pos, state);
        items = NonNullList.withSize(size, ItemStack.EMPTY);
    }

    @Override
    protected Component getDefaultName() {
        return getBlockState().getBlock().getName();
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
        return items.size();
    }

    // --- Online ---

    @Override
    public boolean isOnline() {
        return level instanceof ServerLevel serverLevel && Midranges.host(serverLevel, worldPosition) != null;
    }

    @Override
    public boolean listedOnline() {
        return isOnline();
    }

    public @Nullable NetworkRef network() {
        return level instanceof ServerLevel serverLevel ? ControllerStructures.networkOf(serverLevel, worldPosition) : null;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, PeripheralBlockEntity peripheral) {
        if (peripheral.active > 0 && --peripheral.active == 0) {
            peripheral.showActive(false);
        }
    }

    // An item used on it (HANDOFF 3: cards, a diskette, paper): true when it took some.
    public boolean insert(ItemStack stack) {
        return false;
    }

    // A sneak-use with an empty hand: what comes out.
    public List<ItemStack> eject() {
        return List.of();
    }

    // Punching, reading or printing (ACTIVE shows).
    public boolean active() {
        return active > 0;
    }

    // Shows ACTIVE for a while, with its sound.
    protected void activate(int ticks, Holder<SoundEvent> sound) {
        active = ticks;
        showActive(true);
        if (level != null) {
            level.playSound(null, worldPosition, sound.value(), SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    private void showActive(boolean on) {
        BlockState state = getBlockState();
        if (level != null && state.hasProperty(MidrangeStates.ACTIVE) && state.getValue(MidrangeStates.ACTIVE) != on) {
            level.setBlock(worldPosition, state.setValue(MidrangeStates.ACTIVE, on), Block.UPDATE_CLIENTS);
        }
    }

    // --- Its name, and what its screen's header shows ---

    @Override
    public String deviceName() {
        return deviceName;
    }

    @Override
    public void setDeviceName(String name) {
        if (!deviceName.equals(name)) {
            deviceName = name;
            setChanged();
        }
    }

    public void writeOpening(RegistryFriendlyByteBuf buf) {
        Midranges.writeOpening(this, this, buf);
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(getContainerSize(), ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        deviceName = input.getStringOr("device_name", "");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        if (!deviceName.isEmpty()) {
            output.putString("device_name", deviceName);
        }
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        String carried = components.get(ModDataComponents.DEVICE_NAME.get());
        if (carried != null) {
            deviceName = carried;
        }
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!deviceName.isEmpty()) {
            components.set(ModDataComponents.DEVICE_NAME.get(), deviceName);
        }
    }

    @Override
    public void removeComponentsFromTag(ValueOutput output) {
        super.removeComponentsFromTag(output);
        output.discard("device_name");
    }
}
