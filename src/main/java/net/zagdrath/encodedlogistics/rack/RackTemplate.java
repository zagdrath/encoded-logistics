/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.zagdrath.encodedlogistics.rack.device.RackConsoleDevice;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// What a Rack Elevation (RackElevationItem) holds: a plan of a Server Rack, the encodedlogistics:rack_template
// component. Each planned device is its bottom U, type and size, and optionally what it should hold (its slots' items,
// recorded without what makes an item one of a kind: a drive's or tape's id, fill and charge) and its settings (what
// its item carries out of a rack: RackDeviceItem#settings, with its priority and device name). The toggles say what
// recording copies and applying installs, and where applying looks for items; the mode is how it treats the rack
// (fill empty units only - nothing is ever removed or replaced).
//
// The plan obeys the rack's rules (placeable): a known type, its real size, inside the 42 units, no overlaps, and Rack
// Consoles RackConsoleDevice.SPACING units apart.
public record RackTemplate(String name, List<Planned> devices, boolean includeContents, boolean includeSettings, boolean useNetworkStorage,
        Mode mode) {
    public static final int MAX_NAME = 32;

    public enum Mode implements StringRepresentable {
        FILL_EMPTY;

        public static final Codec<Mode> CODEC = StringRepresentable.fromEnum(Mode::values);

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    // A slot's planned item.
    public record SlotItem(int slot, ItemStackTemplate item) {
        public static final Codec<SlotItem> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, 255).fieldOf("slot").forGetter(SlotItem::slot),
                ItemStackTemplate.CODEC.fieldOf("item").forGetter(SlotItem::item)).apply(i, SlotItem::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, SlotItem> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, SlotItem::slot,
                ItemStackTemplate.STREAM_CODEC, SlotItem::item,
                SlotItem::new);
    }

    // A planned device: its bottom U, type, size, and (optional) contents and settings.
    public record Planned(int u, Identifier type, int size, List<SlotItem> contents, Optional<CompoundTag> settings) {
        public static final Codec<Planned> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(1, RackGeometry.UNITS).fieldOf("u").forGetter(Planned::u),
                Identifier.CODEC.fieldOf("type").forGetter(Planned::type),
                Codec.intRange(1, RackDeviceType.MAX_SIZE).fieldOf("size").forGetter(Planned::size),
                SlotItem.CODEC.listOf().optionalFieldOf("contents", List.of()).forGetter(Planned::contents),
                CompoundTag.CODEC.optionalFieldOf("settings").forGetter(Planned::settings)).apply(i, Planned::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Planned> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Planned::u,
                Identifier.STREAM_CODEC, Planned::type,
                ByteBufCodecs.VAR_INT, Planned::size,
                SlotItem.STREAM_CODEC.apply(ByteBufCodecs.list(256)), Planned::contents,
                ByteBufCodecs.optional(ByteBufCodecs.COMPOUND_TAG), Planned::settings,
                Planned::new);

        public Planned(int u, RackDeviceType type) {
            this(u, type.id(), type.size(), List.of(), Optional.empty());
        }

        public @Nullable RackDeviceType deviceType() {
            return RackDeviceType.byId(type);
        }

        public int top() {
            return u + size - 1;
        }

        public boolean occupies(int unit) {
            return unit >= u && unit <= top();
        }
    }

    public static final Codec<RackTemplate> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.string(0, MAX_NAME).optionalFieldOf("name", "").forGetter(RackTemplate::name),
            Planned.CODEC.listOf().optionalFieldOf("devices", List.of()).forGetter(RackTemplate::devices),
            Codec.BOOL.optionalFieldOf("includeContents", false).forGetter(RackTemplate::includeContents),
            Codec.BOOL.optionalFieldOf("includeSettings", false).forGetter(RackTemplate::includeSettings),
            Codec.BOOL.optionalFieldOf("useNetworkStorage", false).forGetter(RackTemplate::useNetworkStorage),
            Mode.CODEC.optionalFieldOf("mode", Mode.FILL_EMPTY).forGetter(RackTemplate::mode)).apply(i, RackTemplate::new));

    private static final StreamCodec<ByteBuf, Mode> MODE_STREAM_CODEC = ByteBufCodecs.VAR_INT.map(id -> Mode.values()[Math.clamp(id, 0,
            Mode.values().length - 1)], Mode::ordinal);

    public static final StreamCodec<RegistryFriendlyByteBuf, RackTemplate> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_NAME), RackTemplate::name,
            Planned.STREAM_CODEC.apply(ByteBufCodecs.list(RackGeometry.UNITS)), RackTemplate::devices,
            ByteBufCodecs.BOOL, RackTemplate::includeContents,
            ByteBufCodecs.BOOL, RackTemplate::includeSettings,
            ByteBufCodecs.BOOL, RackTemplate::useNetworkStorage,
            MODE_STREAM_CODEC, RackTemplate::mode,
            RackTemplate::new);

    // A new plan: nothing planned, the toggles at their defaults.
    public static final RackTemplate EMPTY = new RackTemplate("", List.of(), false, false, false, Mode.FILL_EMPTY);

    public RackTemplate {
        name = name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
        devices = devices.stream().sorted(Comparator.comparingInt(Planned::u)).toList();
    }

    // --- On the item ---

    public static @Nullable RackTemplate of(ItemStack stack) {
        return stack.get(component());
    }

    // The item's plan, or a new one for a blank item.
    public static RackTemplate orEmpty(ItemStack stack) {
        RackTemplate template = of(stack);
        return template != null ? template : EMPTY;
    }

    // Writes a plan on an item: written, it's one of a kind (stacks to 1).
    public static void write(ItemStack stack, RackTemplate template) {
        stack.set(component(), template);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
    }

    // Back to blank: no plan, and it stacks again.
    public static void clear(ItemStack stack) {
        stack.remove(component());
        stack.set(DataComponents.MAX_STACK_SIZE, stack.getPrototype().get(DataComponents.MAX_STACK_SIZE));
    }

    private static DataComponentType<RackTemplate> component() {
        return ModDataComponents.RACK_TEMPLATE.get();
    }

    // --- The plan ---

    public @Nullable Planned at(int unit) {
        for (Planned planned : devices) {
            if (planned.occupies(unit)) {
                return planned;
            }
        }
        return null;
    }

    // Whether a device of that type fits at u: inside the rack, and no planned device in its way (except one at
    // ignoreU, being moved).
    public boolean placeable(RackDeviceType type, int u, int ignoreU) {
        if (u < 1 || u + type.size() - 1 > RackGeometry.UNITS) {
            return false;
        }
        for (int unit = u; unit < u + type.size(); unit++) {
            Planned there = at(unit);
            if (there != null && there.u() != ignoreU) {
                return false;
            }
        }
        if (type == RackDeviceType.RACK_CONSOLE) {
            for (Planned planned : devices) {
                if (planned.u() != ignoreU && planned.deviceType() == RackDeviceType.RACK_CONSOLE && RackConsoleDevice.tooClose(u, planned.u())) {
                    return false;
                }
            }
        }
        return true;
    }

    // The plan with a device placed at u (null when it doesn't fit).
    public @Nullable RackTemplate place(RackDeviceType type, int u) {
        if (!placeable(type, u, 0)) {
            return null;
        }
        List<Planned> list = new ArrayList<>(devices);
        list.add(new Planned(u, type));
        return withDevices(list);
    }

    // The plan without the device at a unit.
    public RackTemplate remove(int unit) {
        Planned planned = at(unit);
        if (planned == null) {
            return this;
        }
        List<Planned> list = new ArrayList<>(devices);
        list.remove(planned);
        return withDevices(list);
    }

    // Only the devices that obey the rack's rules (a known type, its real size, inside the rack, no overlaps, consoles
    // spaced apart), in U order.
    public RackTemplate valid() {
        List<Planned> kept = new ArrayList<>();
        boolean[] used = new boolean[RackGeometry.UNITS + 1];
        for (Planned planned : devices) {
            RackDeviceType type = planned.deviceType();
            if (type == null || type.size() != planned.size() || planned.u() < 1 || planned.top() > RackGeometry.UNITS) {
                continue;
            }
            boolean free = true;
            for (int unit = planned.u(); unit <= planned.top(); unit++) {
                free &= !used[unit];
            }
            if (type == RackDeviceType.RACK_CONSOLE) {
                for (Planned other : kept) {
                    free &= !(other.deviceType() == RackDeviceType.RACK_CONSOLE && RackConsoleDevice.tooClose(planned.u(), other.u()));
                }
            }
            if (!free) {
                continue;
            }
            for (int unit = planned.u(); unit <= planned.top(); unit++) {
                used[unit] = true;
            }
            kept.add(planned);
        }
        return kept.size() == devices.size() ? this : withDevices(kept);
    }

    // Units its devices take.
    public int units() {
        return devices.stream().mapToInt(Planned::size).sum();
    }

    public RackTemplate withDevices(List<Planned> list) {
        return new RackTemplate(name, list, includeContents, includeSettings, useNetworkStorage, mode);
    }

    public RackTemplate withName(String value) {
        return new RackTemplate(value, devices, includeContents, includeSettings, useNetworkStorage, mode);
    }

    public RackTemplate withContents(boolean value) {
        return new RackTemplate(name, devices, value, includeSettings, useNetworkStorage, mode);
    }

    public RackTemplate withSettings(boolean value) {
        return new RackTemplate(name, devices, includeContents, value, useNetworkStorage, mode);
    }

    public RackTemplate withNetworkStorage(boolean value) {
        return new RackTemplate(name, devices, includeContents, includeSettings, value, mode);
    }

    // --- Contents ---

    // What makes an item one of a kind (a drive's or tape's id, its cached fill, an energy drive's charge, the stack
    // size that goes with them): left out of planned contents, and ignored when matching items to them.
    public static ItemStack plain(ItemStack stack) {
        ItemStack plain = stack.copyWithCount(stack.getCount());
        plain.remove(ModDataComponents.DRIVE_ID.get());
        plain.remove(ModDataComponents.DRIVE_STATS.get());
        plain.remove(ModDataComponents.DRIVE_ENERGY.get());
        plain.set(DataComponents.MAX_STACK_SIZE, plain.getPrototype().get(DataComponents.MAX_STACK_SIZE));
        return plain;
    }

    // Whether an item can stand in for a planned one: the same item, the same components but for what plain() drops.
    public static boolean matches(ItemStack candidate, ItemStackTemplate planned) {
        return !candidate.isEmpty() && ItemStack.isSameItemSameComponents(plain(candidate), planned.create());
    }
}
