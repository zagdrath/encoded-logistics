/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// A device that mounts in a Server Rack (RackDeviceType). It carries the device's settings in rack_device_state while
// it's out of a rack, so taking one out and putting it back (or into another rack) keeps them.
public class RackDeviceItem extends Item {
    private final RackDeviceType type;

    public RackDeviceItem(Item.Properties properties, RackDeviceType type) {
        super(properties);
        this.type = type;
    }

    public RackDeviceType type() {
        return type;
    }

    // A new device for this stack, with the settings it carries.
    public static @Nullable RackDevice create(ItemStack stack, HolderLookup.Provider registries) {
        RackDeviceType type = RackDeviceType.of(stack);
        if (type == null) {
            return null;
        }
        RackDevice device = type.create();
        CustomData data = stack.get(ModDataComponents.RACK_DEVICE_STATE.get());
        if (data != null && !data.isEmpty()) {
            loadSettings(device, data.copyTag(), registries);
        }
        return device;
    }

    // The item a device comes back as, carrying its settings.
    public static ItemStack toStack(RackDevice device, HolderLookup.Provider registries) {
        ItemStack stack = new ItemStack(device.type().item());
        CompoundTag tag = settings(device, registries);
        if (!tag.isEmpty()) {
            stack.set(ModDataComponents.RACK_DEVICE_STATE.get(), CustomData.of(tag));
        }
        return stack;
    }

    // A device's settings as its item carries them: its own (saveSettings), its lane priority when it isn't the default,
    // and its device name. A Rack Elevation records the same.
    public static CompoundTag settings(RackDevice device, HolderLookup.Provider registries) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, registries);
        device.saveSettings(output);
        if (device.lanePriority() != device.defaultPriority()) {
            output.putInt("priority", device.lanePriority().ordinal());
        }
        if (!device.deviceName().isEmpty()) {
            output.putString("device_name", device.deviceName());
        }
        return output.buildResult();
    }

    // Puts settings(...) back on a device.
    public static void loadSettings(RackDevice device, CompoundTag tag, HolderLookup.Provider registries) {
        var input = TagValueInput.create(ProblemReporter.DISCARDING, registries, tag);
        device.loadSettings(input);
        input.getInt("priority").ifPresent(id -> device.setLanePriority(RackDevice.Priority.byId(id)));
        device.setDeviceName(input.getStringOr("device_name", ""));
    }
}
