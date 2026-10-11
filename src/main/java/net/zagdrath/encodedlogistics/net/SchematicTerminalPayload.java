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
import net.zagdrath.encodedlogistics.menu.SchematicTerminalMenu;
import net.zagdrath.encodedlogistics.schematic.SchematicData;

// Server to client: what the open Schematic Terminal shows (SchematicTerminalMenu.broadcastChanges).
public record SchematicTerminalPayload(int containerId, SchematicData.Snapshot snapshot) implements CustomPacketPayload {
    public static final Type<SchematicTerminalPayload> TYPE = new Type<>(EncodedLogistics.id("schematic_terminal"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SchematicTerminalPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SchematicTerminalPayload::containerId,
            SchematicData.Snapshot.STREAM_CODEC, SchematicTerminalPayload::snapshot,
            SchematicTerminalPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(SchematicTerminalPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof SchematicTerminalMenu menu && menu.containerId == payload.containerId()) {
            menu.apply(payload.snapshot());
        }
    }
}
