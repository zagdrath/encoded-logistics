/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;

// Server to client: the figures of the Capacitor Bank whose screen is open. input / output are FE per tick averaged
// over the last second.
public record CapacitorBankPayload(int containerId, long stored, long capacity, double input, double output) implements CustomPacketPayload {
    public static final Type<CapacitorBankPayload> TYPE = new Type<>(EncodedLogistics.id("capacitor_bank"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CapacitorBankPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CapacitorBankPayload::containerId,
            ByteBufCodecs.VAR_LONG, CapacitorBankPayload::stored,
            ByteBufCodecs.VAR_LONG, CapacitorBankPayload::capacity,
            ByteBufCodecs.DOUBLE, CapacitorBankPayload::input,
            ByteBufCodecs.DOUBLE, CapacitorBankPayload::output,
            CapacitorBankPayload::new);

    // Client side: the last figures received.
    private static volatile @Nullable CapacitorBankPayload latest;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // The figures for the open menu, or zeros until the server has sent some.
    public static CapacitorBankPayload forMenu(int containerId) {
        CapacitorBankPayload last = latest;
        return last != null && last.containerId() == containerId ? last : new CapacitorBankPayload(containerId, 0, 0, 0, 0);
    }

    static void handle(CapacitorBankPayload payload, IPayloadContext context) {
        latest = payload;
    }
}
