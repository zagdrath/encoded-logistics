/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// What the rack's unit popup shows for a device: its name, status (a dot and coloured text) and any number of
// label / value lines, each with an optional bar under it, and whether it's part of its rack's Scheduler (a badge in
// the header). Every device describes itself with one of these (RackDevice#describe), so the popup never changes for
// a new device.
public record RackDeviceInfo(Component name, Status status, Component statusText, List<InfoLine> lines, boolean schedulerBadge,
        Optional<Header> header) {
    public RackDeviceInfo(Component name, Status status, Component statusText, List<InfoLine> lines, boolean schedulerBadge) {
        this(name, status, statusText, lines, schedulerBadge, Optional.empty());
    }

    public RackDeviceInfo(Component name, Status status, Component statusText, List<InfoLine> lines) {
        this(name, status, statusText, lines, false);
    }

    // The same with the rack's header strip over it.
    public RackDeviceInfo withHeader(@Nullable Header header) {
        return new RackDeviceInfo(name, status, statusText, lines, schedulerBadge, Optional.ofNullable(header));
    }

    // The rack's strip above the popup (its lanes and uplinks), from the rack, not the device; degraded: amber with a
    // warning sign.
    public record Header(Component text, boolean degraded) {
        static final StreamCodec<RegistryFriendlyByteBuf, Header> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.TRUSTED_STREAM_CODEC, Header::text,
                ByteBufCodecs.BOOL, Header::degraded,
                Header::new);
    }

    // WARNING: a passing state that isn't a fault (a controller failing over, a UPS on battery): amber.
    public enum Status {
        ONLINE, OFFLINE, FAULT, WARNING;

        private static final Status[] VALUES = values();

        public static Status byId(int id) {
            return id >= 0 && id < VALUES.length ? VALUES[id] : OFFLINE;
        }

        public Component text() {
            return Component.translatable("gui.encodedlogistics.rack.status." + name().toLowerCase(Locale.ROOT));
        }
    }

    // The fill sprite a bar uses: hud/bar_fill, bar_fill_warn or bar_fill_low.
    public enum BarStyle {
        NORMAL, WARN, LOW
    }

    public record Bar(float fraction, BarStyle style) {
        static final StreamCodec<RegistryFriendlyByteBuf, Bar> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.FLOAT, Bar::fraction,
                ByteBufCodecs.idMapper(id -> BarStyle.values()[Math.clamp(id, 0, 2)], BarStyle::ordinal), Bar::style,
                Bar::new);
    }

    public record InfoLine(Component label, Component value, Optional<Bar> bar) {
        public InfoLine(Component label, Component value, @Nullable Bar bar) {
            this(label, value, Optional.ofNullable(bar));
        }

        public InfoLine(Component label, Component value) {
            this(label, value, Optional.empty());
        }

        static final StreamCodec<RegistryFriendlyByteBuf, InfoLine> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.TRUSTED_STREAM_CODEC, InfoLine::label,
                ComponentSerialization.TRUSTED_STREAM_CODEC, InfoLine::value,
                ByteBufCodecs.optional(Bar.STREAM_CODEC), InfoLine::bar,
                InfoLine::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, RackDeviceInfo> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.TRUSTED_STREAM_CODEC, RackDeviceInfo::name,
            ByteBufCodecs.idMapper(Status::byId, Status::ordinal), RackDeviceInfo::status,
            ComponentSerialization.TRUSTED_STREAM_CODEC, RackDeviceInfo::statusText,
            InfoLine.STREAM_CODEC.apply(ByteBufCodecs.list(16)), RackDeviceInfo::lines,
            ByteBufCodecs.BOOL, RackDeviceInfo::schedulerBadge,
            ByteBufCodecs.optional(Header.STREAM_CODEC), RackDeviceInfo::header,
            RackDeviceInfo::new);
}
