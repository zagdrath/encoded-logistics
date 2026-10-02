/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// What a Handheld Terminal shows (items/handheld_terminal.json selects its icon by it): not linked to a network,
// linked and in range of one of its Relay Antennas, or linked but out of range.
public enum HandheldLinkState implements StringRepresentable {
    UNLINKED("unlinked"),
    LINKED("linked"),
    OUT_OF_RANGE("out_of_range");

    public static final Codec<HandheldLinkState> CODEC = StringRepresentable.fromEnum(HandheldLinkState::values);
    public static final StreamCodec<ByteBuf, HandheldLinkState> STREAM_CODEC = ByteBufCodecs.idMapper(HandheldLinkState::byId,
            HandheldLinkState::ordinal);

    private final String name;

    HandheldLinkState(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }

    public static HandheldLinkState byId(int id) {
        HandheldLinkState[] values = values();
        return id >= 0 && id < values.length ? values[id] : UNLINKED;
    }
}
