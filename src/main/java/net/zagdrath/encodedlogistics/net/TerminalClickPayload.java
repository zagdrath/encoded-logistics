/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import java.util.Optional;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.AccessTerminalMenu;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// Client to server: a click on an open terminal's grid - the item clicked (if any) and what to do (AccessTerminalMenu's
// actions).
public record TerminalClickPayload(int containerId, Optional<StorageKey> key, int action) implements CustomPacketPayload {
    public static final Type<TerminalClickPayload> TYPE = new Type<>(EncodedLogistics.id("terminal_click"));

    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalClickPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, TerminalClickPayload::containerId,
            ByteBufCodecs.optional(StorageKey.STREAM_CODEC), TerminalClickPayload::key,
            ByteBufCodecs.VAR_INT, TerminalClickPayload::action,
            TerminalClickPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(TerminalClickPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof AccessTerminalMenu menu
                && menu.containerId == payload.containerId() && menu.stillValid(player)) {
            menu.handleClick(player, payload.key().orElse(null), payload.action());
        }
    }
}
