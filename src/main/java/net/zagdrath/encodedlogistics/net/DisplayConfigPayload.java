/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.DisplayPanelMenu;

// Client to server: a change on a Display Panel's configuration screen (DisplayPanelMenu.apply): mode, name,
// background, regions or widget, with its data.
public record DisplayConfigPayload(int containerId, String action, CompoundTag data) implements CustomPacketPayload {
    public static final Type<DisplayConfigPayload> TYPE = new Type<>(EncodedLogistics.id("display_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayConfigPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DisplayConfigPayload::containerId,
            ByteBufCodecs.stringUtf8(16), DisplayConfigPayload::action,
            ByteBufCodecs.COMPOUND_TAG, DisplayConfigPayload::data,
            DisplayConfigPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(DisplayConfigPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof DisplayPanelMenu menu
                && menu.containerId == payload.containerId() && menu.stillValid(player)) {
            menu.apply(player, payload.action(), payload.data());
        }
    }
}
