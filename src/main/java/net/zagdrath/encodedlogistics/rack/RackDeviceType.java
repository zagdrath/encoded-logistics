/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.rack.device.FirewallDevice;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.rack.device.UpsDevice;
import net.zagdrath.encodedlogistics.registry.ModItems;

// A kind of device that mounts in a Server Rack: its id, height in U (1 or 2), the item it is and comes back as, and a
// factory for its behaviour. Its models are models/block/rack_device/<id>.json, <id>_on.json and <id>_fault.json, and
// its front in the rack's screen is (0,0)-(104,8 * size) of textures/block/rack_device/<id>.png. The client side (its
// settings panel and any render extras) is registered by id in client.rack.RackClientDevices.
//
// Adding a device: a RackDeviceItem for it, an entry here, a RackDevice subclass, its assets, and (for a settings
// panel) an entry in RackClientDevices. Nothing in the rack, its screen, the popup or the outline changes.
public final class RackDeviceType {
    private static final Map<Identifier, RackDeviceType> TYPES = new LinkedHashMap<>();

    public static final RackDeviceType FIREWALL = register("firewall", 1, () -> ModItems.FIREWALL.get(), FirewallDevice::new);
    public static final RackDeviceType ROUTER = register("router", 1, () -> ModItems.ROUTER.get(), RouterDevice::new);
    public static final RackDeviceType UPS = register("ups", 2, () -> ModItems.UPS.get(), UpsDevice::new);

    private final Identifier id;
    private final int size;
    private final Supplier<? extends Item> item;
    private final Function<RackDeviceType, RackDevice> factory;

    private RackDeviceType(Identifier id, int size, Supplier<? extends Item> item, Function<RackDeviceType, RackDevice> factory) {
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
