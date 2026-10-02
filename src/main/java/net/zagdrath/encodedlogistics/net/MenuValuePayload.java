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
import net.zagdrath.encodedlogistics.menu.ValueMenu;

// Client to server: a number typed into an open part screen (a tap's priority, a sensor's threshold).
public record MenuValuePayload(int containerId, int key, int value) implements CustomPacketPayload {
    public static final Type<MenuValuePayload> TYPE = new Type<>(EncodedLogistics.id("menu_value"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MenuValuePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MenuValuePayload::containerId,
            ByteBufCodecs.VAR_INT, MenuValuePayload::key,
            ByteBufCodecs.INT, MenuValuePayload::value,
            MenuValuePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(MenuValuePayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof ValueMenu menu
                && player.containerMenu.containerId == payload.containerId() && player.containerMenu.stillValid(player)) {
            menu.setValue(player, payload.key(), payload.value());
        }
    }
}
