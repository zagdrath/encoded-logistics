/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.RackMenu;

// Client to server: an action in the settings panel of the device at unit u of an open rack screen (what action, value
// and text mean is up to the device: RackDevice#handleAction).
public record RackActionPayload(int containerId, int u, int action, int value, String text) implements CustomPacketPayload {
    public static final Type<RackActionPayload> TYPE = new Type<>(EncodedLogistics.id("rack_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RackActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, RackActionPayload::containerId,
            ByteBufCodecs.VAR_INT, RackActionPayload::u,
            ByteBufCodecs.VAR_INT, RackActionPayload::action,
            ByteBufCodecs.INT, RackActionPayload::value,
            ByteBufCodecs.stringUtf8(64), RackActionPayload::text,
            RackActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(RackActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof RackMenu menu
                && menu.containerId == payload.containerId() && menu.stillValid(player)) {
            menu.handleAction(player, payload.u(), payload.action(), payload.value(), payload.text());
        }
    }
}
