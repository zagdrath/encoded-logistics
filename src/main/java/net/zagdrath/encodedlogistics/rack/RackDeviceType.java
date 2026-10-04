/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.rack.device.ComputeServerDevice;
import net.zagdrath.encodedlogistics.rack.device.FabricationServerDevice;
import net.zagdrath.encodedlogistics.rack.device.FirewallDevice;
import net.zagdrath.encodedlogistics.rack.device.L3SwitchDevice;
import net.zagdrath.encodedlogistics.rack.device.MemoryServerDevice;
import net.zagdrath.encodedlogistics.rack.device.MonitoringServerDevice;
import net.zagdrath.encodedlogistics.rack.device.NasDevice;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.rack.device.SanDevice;
import net.zagdrath.encodedlogistics.rack.device.SwitchDevice;
import net.zagdrath.encodedlogistics.rack.device.UpsDevice;
import net.zagdrath.encodedlogistics.registry.ModItems;

// A kind of device that mounts in a Server Rack: its id, height in U (1 to 4), the item it is and comes back as, a
// factory for its behaviour, its item slots (as its settings panel shows them) and how many frames its lit overlay
// animates through. Its models are models/block/rack_device/<id>.json, <id>_on.json and <id>_fault.json, and its front
// in the rack's screen is (0,0)-(104,8 * size) of textures/block/rack_device/<id>.png. The client side (its settings
// panel and any render extras) is registered by id in client.rack.RackClientDevices.
//
// Adding a device: a RackDeviceItem for it, an entry here, a RackDevice subclass, its assets, and (for a settings
// panel) an entry in RackClientDevices. Nothing in the rack, its screen, the popup or the outline changes.
public final class RackDeviceType {
    private static final Map<Identifier, RackDeviceType> TYPES = new LinkedHashMap<>();
    // The Storage Drives a NAS or SAN takes.
    public static final TagKey<Item> STORAGE_DRIVES = TagKey.create(Registries.ITEM, EncodedLogistics.id("storage_drives"));

    public static final RackDeviceType FIREWALL = register("firewall", 1, () -> ModItems.FIREWALL.get(), FirewallDevice::new);
    public static final RackDeviceType ROUTER = register("router", 1, () -> ModItems.ROUTER.get(), RouterDevice::new)
            .slots(List.of(new RackSlot(9, 129, RouterDevice::isTransceiver, 1), new RackSlot(27, 129, RouterDevice::isTransceiver, 1),
                    new RackSlot(45, 129, RouterDevice::isTransceiver, 1)));
    public static final RackDeviceType UPS = register("ups", 2, () -> ModItems.UPS.get(), UpsDevice::new);

    // Batch 2: switches, servers, storage. Their lit overlays animate (4 frames).
    public static final RackDeviceType L2_SWITCH_24 = register("l2_switch_24", 1, () -> ModItems.L2_SWITCH_24.get(), SwitchDevice::new).frames(4);
    public static final RackDeviceType L2_SWITCH_48 = register("l2_switch_48", 1, () -> ModItems.L2_SWITCH_48.get(), SwitchDevice::new).frames(4);
    public static final RackDeviceType L3_SWITCH = register("l3_switch", 1, () -> ModItems.L3_SWITCH.get(), L3SwitchDevice::new).frames(4);
    public static final RackDeviceType COMPUTE_SERVER = register("compute_server", 2, () -> ModItems.COMPUTE_SERVER.get(), ComputeServerDevice::new)
            .frames(4);
    public static final RackDeviceType MEMORY_SERVER = register("memory_server", 1, () -> ModItems.MEMORY_SERVER.get(), MemoryServerDevice::new)
            .frames(4);
    public static final RackDeviceType FABRICATION_SERVER = register("fabrication_server", 2, () -> ModItems.FABRICATION_SERVER.get(),
            FabricationServerDevice::new).frames(4).slots(fabricationSlots());
    public static final RackDeviceType MONITORING_SERVER = register("monitoring_server", 1, () -> ModItems.MONITORING_SERVER.get(),
            MonitoringServerDevice::new).frames(4);
    public static final RackDeviceType NAS = register("nas", 2, () -> ModItems.NAS.get(), NasDevice::new).frames(4)
            .slots(RackSlot.grid(35, 23, NasDevice.DRIVES, 1, RackDeviceType::isDrive, 1));
    public static final RackDeviceType SAN = register("san", 4, () -> ModItems.SAN.get(), SanDevice::new).frames(4).slots(sanSlots());

    private final Identifier id;
    private final int size;
    private final Supplier<? extends Item> item;
    private final Function<RackDeviceType, RackDevice> factory;
    private List<RackSlot> slots = List.of();
    private int frames = 1;

    private RackDeviceType(Identifier id, int size, Supplier<? extends Item> item, Function<RackDeviceType, RackDevice> factory) {
        if (size < 1 || size > 4) {
            throw new IllegalArgumentException("Rack device " + id + " is " + size + "U; 1 to 4 fit");
        }
        this.id = id;
        this.size = size;
        this.item = item;
        this.factory = factory;
    }

    public static RackDeviceType register(String name, int size, Supplier<? extends Item> item, Function<RackDeviceType, RackDevice> factory) {
        RackDeviceType type = new RackDeviceType(EncodedLogistics.id(name), size, item, factory);
        if (TYPES.putIfAbsent(type.id, type) != null) {
            throw new IllegalArgumentException("Rack device " + type.id + " registered twice");
        }
        return type;
    }

    private RackDeviceType slots(List<RackSlot> slots) {
        this.slots = List.copyOf(slots);
        return this;
    }

    private RackDeviceType frames(int frames) {
        this.frames = frames;
        return this;
    }

    private static List<RackSlot> fabricationSlots() {
        List<RackSlot> slots = new ArrayList<>(RackSlot.grid(31, 23, 3, 3, stack -> stack.is(ModItems.ENCODED_SCHEMATIC_CRAFTING.get()), 1));
        slots.add(new RackSlot(141, 33, stack -> stack.is(ModItems.THROUGHPUT_MODULE.get()), 1));
        slots.add(new RackSlot(141, 51, stack -> stack.is(ModItems.THROUGHPUT_MODULE.get()), 1));
        return slots;
    }

    private static List<RackSlot> sanSlots() {
        List<RackSlot> slots = new ArrayList<>(RackSlot.grid(9, 23, 6, 4, RackDeviceType::isDrive, 1));
        slots.addAll(RackSlot.grid(125, 23, 2, 2, RouterDevice::isTransceiver, 1));
        return slots;
    }

    public static boolean isDrive(ItemStack stack) {
        return stack.is(STORAGE_DRIVES);
    }

    public static @Nullable RackDeviceType byId(Identifier id) {
        return TYPES.get(id);
    }

    public static @Nullable RackDeviceType of(ItemStack stack) {
        return stack.getItem() instanceof RackDeviceItem item ? item.type() : null;
    }

    public static Collection<RackDeviceType> all() {
        return Collections.unmodifiableCollection(TYPES.values());
    }

    public Identifier id() {
        return id;
    }

    public int size() {
        return size;
    }

    public Item item() {
        return item.get();
    }

    public RackDevice create() {
        return factory.apply(this);
    }

    // The item slots its panel shows (and its device holds), in order.
    public List<RackSlot> slots() {
        return slots;
    }

    // Frames in its lit overlay's texture (128 x 128 each, stacked).
    public int frames() {
        return frames;
    }

    // The model for a state: off, on or fault.
    public Identifier model(RackDeviceInfo.Status status) {
        String suffix = switch (status) {
            case OFFLINE -> "";
            case ONLINE -> "_on";
            case FAULT -> "_fault";
        };
        return id.withPath("block/rack_device/" + id.getPath() + suffix);
    }

    public Identifier texture(String suffix) {
        return id.withPath("textures/block/rack_device/" + id.getPath() + suffix + ".png");
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
