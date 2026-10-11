/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.SchematicTerminalMenu;

// Client to server: a Schematic Terminal click (SchematicTerminalService's actions): on a holder (by key) and slot, or a
// job (and step, in slot), with text for a rename.
public record SchematicTerminalActionPayload(int containerId, int action, String holder, int slot, Optional<UUID> job, String text)
        implements CustomPacketPayload {
    public static final Type<SchematicTerminalActionPayload> TYPE = new Type<>(EncodedLogistics.id("schematic_terminal_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SchematicTerminalActionPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SchematicTerminalActionPayload::containerId,
            ByteBufCodecs.VAR_INT, SchematicTerminalActionPayload::action,
            ByteBufCodecs.stringUtf8(256), SchematicTerminalActionPayload::holder,
            ByteBufCodecs.VAR_INT, SchematicTerminalActionPayload::slot,
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC), SchematicTerminalActionPayload::job,
            ByteBufCodecs.stringUtf8(64), SchematicTerminalActionPayload::text,
            SchematicTerminalActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(SchematicTerminalActionPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof SchematicTerminalMenu menu
                && menu.containerId == payload.containerId() && menu.stillValid(player)) {
            menu.handleAction(player, payload.action(), payload.holder(), payload.slot(), payload.job(), payload.text());
        }
    }
}
