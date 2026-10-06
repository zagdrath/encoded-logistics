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
import net.zagdrath.encodedlogistics.signal.SignalMenu;

// Client to server: a change on a signal device's settings screen (SignalMenu.apply): a row clicked (its key and step)
// or given text, or the screen's action.
public record SignalConfigPayload(int containerId, String key, int step, Optional<String> text) implements CustomPacketPayload {
    public static final Type<SignalConfigPayload> TYPE = new Type<>(EncodedLogistics.id("signal_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SignalConfigPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SignalConfigPayload::containerId,
            ByteBufCodecs.stringUtf8(16), SignalConfigPayload::key,
            ByteBufCodecs.VAR_INT, SignalConfigPayload::step,
            ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(512)), SignalConfigPayload::text,
            SignalConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(SignalConfigPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof SignalMenu menu
                && menu.containerId == payload.containerId() && menu.stillValid(player)) {
            menu.apply(player, payload.key(), Math.clamp(payload.step(), -10, 10), payload.text().orElse(null));
        }
    }
}
