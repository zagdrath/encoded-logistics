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
import net.zagdrath.encodedlogistics.rack.device.RackConsoleDevice;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.rack.device.SanDevice;
import net.zagdrath.encodedlogistics.rack.device.SwitchDevice;
import net.zagdrath.encodedlogistics.rack.device.TapeLibraryDevice;
import net.zagdrath.encodedlogistics.rack.device.UpsDevice;
import net.zagdrath.encodedlogistics.rack.device.WirelessControllerDevice;
import net.zagdrath.encodedlogistics.registry.ModItems;

// A kind of device that mounts in a Server Rack: its id, height in U (1 to 6), the item it is and comes back as, a
// factory for its behaviour, its item slots (as its settings panel shows them) and how many frames its lit overlay
// animates through. Its models are models/block/rack_device/<id>.json, <id>_on.json and <id>_fault.json, and its front
// in the rack's screen is (0,0)-(104,8 * size) of textures/block/rack_device/<id>.png. That texture is 128 texels
// across and sheetHeight() tall: 128 up to 4U (the back from (0,32), render extras' sprites from (0,64)), 256 for 5U and
// 6U (the back from (0,64), extras from (0,128)); lit overlays stack frames of that height. The client side (its
// settings panel and any render extras) is registered by id in client.rack.RackClientDevices.
//
// Adding a device: a RackDeviceItem for it, an entry here, a RackDevice subclass, its assets, and (for a settings
// panel) an entry in RackClientDevices. Nothing in the rack, its screen, the popup or the outline changes.
public final class RackDeviceType {
    private static final Map<Identifier, RackDeviceType> TYPES = new LinkedHashMap<>();
    // The Storage Drives a NAS or SAN takes, the tapes a Tape Library takes.
    public static final TagKey<Item> STORAGE_DRIVES = TagKey.create(Registries.ITEM, EncodedLogistics.id("storage_drives"));
    public static final TagKey<Item> LTO_TAPES = TagKey.create(Registries.ITEM, EncodedLogistics.id("lto_tapes"));
    public static final int MAX_SIZE = 6;

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

    // Batch 3: the console, wireless, tape.
    public static final RackDeviceType RACK_CONSOLE = register("rack_console", 1, () -> ModItems.RACK_CONSOLE.get(), RackConsoleDevice::new).frames(4);
    public static final RackDeviceType WIRELESS_CONTROLLER = register("wireless_controller", 1, () -> ModItems.WIRELESS_CONTROLLER.get(),
            WirelessControllerDevice::new).frames(4);
    public static final RackDeviceType TAPE_LIBRARY_4U = register("tape_library_4u", 4, () -> ModItems.TAPE_LIBRARY_4U.get(),
            type -> new TapeLibraryDevice(type, 24, 2)).frames(4).slots(tapeSlots(24, new int[] { 9, 49 }));
    public static final RackDeviceType TAPE_LIBRARY_6U = register("tape_library_6u", 6, () -> ModItems.TAPE_LIBRARY_6U.get(),
            type -> new TapeLibraryDevice(type, 48, 4)).frames(4).slots(tapeSlots(48, new int[] { 9, 49, 89, 129 }));

    private final Identifier id;
    private final int size;
    private final Supplier<? extends Item> item;
    private final Function<RackDeviceType, RackDevice> factory;
    private List<RackSlot> slots = List.of();
    private int frames = 1;

    private RackDeviceType(Identifier id, int size, Supplier<? extends Item> item, Function<RackDeviceType, RackDevice> factory) {
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("Rack device " + id + " is " + size + "U; 1 to " + MAX_SIZE + " fit");
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

    // A Tape Library's magazine (8 a row, 20 px apart, scrolling three rows at a time) then its drive bays.
    private static List<RackSlot> tapeSlots(int tapes, int[] bays) {
        List<RackSlot> slots = new ArrayList<>();
        for (int i = 0; i < tapes; i++) {
            int row = i / 8;
            slots.add(new RackSlot(9 + i % 8 * 18, 33 + row * 20, RackDeviceType::isTape, 1, row));
        }
        for (int bay : bays) {
            slots.add(new RackSlot(bay, 99, stack -> stack.is(ModItems.LTO_TAPE_DRIVE.get()), 1));
        }
        return slots;
    }

    public static boolean isDrive(ItemStack stack) {
        return stack.is(STORAGE_DRIVES);
    }

    public static boolean isTape(ItemStack stack) {
        return stack.is(LTO_TAPES);
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

    // Frames in its lit overlay's texture (128 x sheetHeight() each, stacked).
    public int frames() {
        return frames;
    }

    // Its texture's height in texels: 128 up to 4U, 256 above.
    public int sheetHeight() {
        return size > 4 ? 256 : 128;
    }

    // Where its back face starts on its texture.
    public int backOrigin() {
        return size > 4 ? 64 : 32;
    }

    // Where its render extras' sprites start on its texture.
    public int extrasOrigin() {
        return size > 4 ? 128 : 64;
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
