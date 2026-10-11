/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.client.crt.CrtLocate;

// Server to client: outline a box for ten seconds ("minX minY minZ maxX maxY maxZ dimension"), as Work with Devices'
// 8=Locate does (CrtLocate): the Schematic Terminal's Locate.
public record LocatePayload(String where) implements CustomPacketPayload {
    public static final Type<LocatePayload> TYPE = new Type<>(EncodedLogistics.id("locate"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LocatePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(256), LocatePayload::where,
            LocatePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(LocatePayload payload, IPayloadContext context) {
        CrtLocate.locate(payload.where());
    }
}
