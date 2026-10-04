/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import org.jspecify.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;

// Server to client: the state of the device picked in an open rack screen (RackDevice#writePanel), at unit u.
public record RackPanelPayload(int containerId, int u, CompoundTag data) implements CustomPacketPayload {
    public static final Type<RackPanelPayload> TYPE = new Type<>(EncodedLogistics.id("rack_panel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RackPanelPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RackPanelPayload::containerId,
            ByteBufCodecs.VAR_INT, RackPanelPayload::u,
            ByteBufCodecs.TRUSTED_COMPOUND_TAG, RackPanelPayload::data,
            RackPanelPayload::new);

    // Client side: the last state received.
    private static volatile @Nullable RackPanelPayload latest;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // The state for the open menu's device at u, or null until the server has sent it.
    public static @Nullable CompoundTag forMenu(int containerId, int u) {
        RackPanelPayload last = latest;
        return last != null && last.containerId() == containerId && last.u() == u ? last.data() : null;
    }

    static void handle(RackPanelPayload payload, IPayloadContext context) {
        latest = payload;
    }
}
