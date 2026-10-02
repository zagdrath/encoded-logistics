/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.part;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// What a Point-to-Point Link carries: items, FE, a redstone signal, or lanes.
public enum LinkType implements StringRepresentable {
    ITEMS("items"),
    ENERGY("energy"),
    REDSTONE("redstone"),
    LANES("lanes");

    public static final Codec<LinkType> CODEC = StringRepresentable.fromEnum(LinkType::values);
    public static final StreamCodec<ByteBuf, LinkType> STREAM_CODEC = ByteBufCodecs.idMapper(LinkType::byId, LinkType::ordinal);

    private final String name;

    LinkType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static LinkType byId(int id) {
        LinkType[] values = values();
        return id >= 0 && id < values.length ? values[id] : ITEMS;
    }
}
