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
import net.zagdrath.encodedlogistics.menu.TerminalDeskMenu;

// Client to server: a Terminal Desk screen's request (TerminalService.COMMAND / QUERY / COMPLETE) and its text.
public record CrtRequestPayload(int containerId, int kind, String text) implements CustomPacketPayload {
    public static final Type<CrtRequestPayload> TYPE = new Type<>(EncodedLogistics.id("crt_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CrtRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CrtRequestPayload::containerId,
            ByteBufCodecs.VAR_INT, CrtRequestPayload::kind,
            ByteBufCodecs.stringUtf8(256), CrtRequestPayload::text,
            CrtRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(CrtRequestPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof TerminalDeskMenu menu
                && menu.containerId == payload.containerId() && menu.stillValid(player)) {
            menu.handle(player, payload.kind(), payload.text());
        }
    }
}
