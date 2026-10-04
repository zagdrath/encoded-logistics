/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import java.util.List;
import java.util.Optional;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.client.CrtClient;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// Server to client: the answer to a Terminal Desk screen's request - its kind and topic (the query's first word), the
// lines, and a message for the message line.
public record CrtResponsePayload(int containerId, int kind, String topic, List<TerminalLine> lines, Optional<Component> message)
        implements CustomPacketPayload {
    public static final Type<CrtResponsePayload> TYPE = new Type<>(EncodedLogistics.id("crt_response"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrtResponsePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CrtResponsePayload::containerId,
            ByteBufCodecs.VAR_INT, CrtResponsePayload::kind,
            ByteBufCodecs.STRING_UTF8, CrtResponsePayload::topic,
            TerminalLine.STREAM_CODEC.apply(ByteBufCodecs.list()), CrtResponsePayload::lines,
            ByteBufCodecs.optional(ComponentSerialization.TRUSTED_STREAM_CODEC), CrtResponsePayload::message,
            CrtResponsePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(CrtResponsePayload payload, IPayloadContext context) {
        CrtClient.receive(payload);
    }
}
