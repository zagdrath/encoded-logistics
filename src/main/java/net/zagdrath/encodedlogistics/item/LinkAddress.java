/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.zagdrath.encodedlogistics.part.LinkType;

// What a Link Card remembers: a Network Bridge, a Point-to-Point Link endpoint (the side of the cable it's on, and
// what it carries), or a network segment for a Router (the node at one end of a Segment Isolator, and that end), and
// where it is.
public record LinkAddress(Kind kind, GlobalPos pos, Optional<Direction> side, Optional<LinkType> type) {
    public enum Kind implements StringRepresentable {
        BRIDGE("bridge"),
        P2P("p2p"),
        SEGMENT("segment");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        public static final StreamCodec<ByteBuf, Kind> STREAM_CODEC = ByteBufCodecs.idMapper(id -> values()[Math.clamp(id, 0, values().length - 1)], Kind::ordinal);

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final Codec<LinkAddress> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.fieldOf("kind").forGetter(LinkAddress::kind),
            GlobalPos.CODEC.fieldOf("pos").forGetter(LinkAddress::pos),
            Direction.CODEC.optionalFieldOf("side").forGetter(LinkAddress::side),
            LinkType.CODEC.optionalFieldOf("type").forGetter(LinkAddress::type))
            .apply(i, LinkAddress::new));

    public static final StreamCodec<ByteBuf, LinkAddress> STREAM_CODEC = StreamCodec.composite(
            Kind.STREAM_CODEC, LinkAddress::kind,
            GlobalPos.STREAM_CODEC, LinkAddress::pos,
            ByteBufCodecs.optional(Direction.STREAM_CODEC), LinkAddress::side,
            ByteBufCodecs.optional(LinkType.STREAM_CODEC), LinkAddress::type,
            LinkAddress::new);

    public static LinkAddress bridge(GlobalPos pos) {
        return new LinkAddress(Kind.BRIDGE, pos, Optional.empty(), Optional.empty());
    }

    // The segment on the side of a Segment Isolator: node is the block next to that end.
    public static LinkAddress segment(GlobalPos node, Direction side) {
        return new LinkAddress(Kind.SEGMENT, node, Optional.of(side), Optional.empty());
    }

    public static LinkAddress p2p(GlobalPos pos, Direction side, LinkType type) {
        return new LinkAddress(Kind.P2P, pos, Optional.of(side), Optional.of(type));
    }
}
