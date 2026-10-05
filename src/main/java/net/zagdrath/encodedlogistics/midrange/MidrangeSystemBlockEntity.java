/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// The master block of a Midrange System or Integrated Midrange System: online while its lane is on a running network,
// and its device name (MIDRANGE01). The peripherals work while one is online on their network (Midranges.host).
public class MidrangeSystemBlockEntity extends BlockEntity implements NetworkDevice, MidrangeDevice {
    public static final String TYPE = "MIDRANGE";

    private boolean online;
    private String deviceName = "";

    public MidrangeSystemBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.MIDRANGE_SYSTEM.get(), pos, state);
    }

    @Override
    public void setNetworkOnline(boolean online) {
        this.online = online;
    }

    @Override
    public boolean isOnline() {
        return online;
    }

    @Override
    public String deviceType() {
        return TYPE;
    }

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

    // --- Saving (its name goes with the item) ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        deviceName = input.getStringOr("device_name", "");
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
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
