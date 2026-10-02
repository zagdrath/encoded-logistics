/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import java.util.List;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.RelayAntennaBlockEntity;
import net.zagdrath.encodedlogistics.menu.RelayAntennaMenu;

// Server to client: the players an open Relay Antenna screen lists (carrying a Handheld Terminal of its network, in
// range) and how far away they are.
public record RelayTerminalsPayload(int containerId, List<RelayAntennaBlockEntity.Linked> linked) implements CustomPacketPayload {
    public static final Type<RelayTerminalsPayload> TYPE = new Type<>(EncodedLogistics.id("relay_terminals"));

    private static final StreamCodec<ByteBuf, RelayAntennaBlockEntity.Linked> LINKED = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, RelayAntennaBlockEntity.Linked::name,
            ByteBufCodecs.VAR_INT, RelayAntennaBlockEntity.Linked::distance,
            RelayAntennaBlockEntity.Linked::new);

    public static final StreamCodec<ByteBuf, RelayTerminalsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RelayTerminalsPayload::containerId,
            LINKED.apply(ByteBufCodecs.list()), RelayTerminalsPayload::linked,
            RelayTerminalsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(RelayTerminalsPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof RelayAntennaMenu menu && menu.containerId == payload.containerId()) {
            menu.setLinked(payload.linked());
        }
    }
}
