/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.display.SmallWirelessBridgeBlock;

// Server to client: the Small Wireless Bridges in the player's level (MachineBridges) - the block each is on, the face
// and its LED - for their models and popups, and whether the machine integration is on (so the client knows a bridge in
// hand can go on a machine). Sent whole when one changes and when the player joins or changes level.
public record MachineBridgesPayload(boolean enabled, List<Entry> bridges) implements CustomPacketPayload {
    public static final Type<MachineBridgesPayload> TYPE = new Type<>(EncodedLogistics.id("machine_bridges"));

    public record Entry(BlockPos pos, Direction face, SmallWirelessBridgeBlock.State led) {
        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Entry::pos,
                Direction.STREAM_CODEC, Entry::face,
                ByteBufCodecs.idMapper(id -> SmallWirelessBridgeBlock.State.values()[Math.clamp(id, 0, 2)], SmallWirelessBridgeBlock.State::ordinal),
                Entry::led,
                Entry::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, MachineBridgesPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, MachineBridgesPayload::enabled,
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), MachineBridgesPayload::bridges,
            MachineBridgesPayload::new);

    // Client side: the bridges in the player's level, by block.
    private static volatile Map<BlockPos, Entry> shown = Map.of();
    private static volatile boolean clientEnabled;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(MachineBridgesPayload payload, IPayloadContext context) {
        Map<BlockPos, Entry> now = new HashMap<>();
        payload.bridges().forEach(entry -> now.put(entry.pos(), entry));
        shown = Map.copyOf(now);
        clientEnabled = payload.enabled();
    }

    // Leaving the server: nothing to show.
    public static void clear() {
        shown = Map.of();
        clientEnabled = false;
    }

    public static Map<BlockPos, Entry> shown() {
        return shown;
    }

    public static boolean clientEnabled() {
        return clientEnabled;
    }

    // The face of the block at pos a bridge is on, or null.
    public static @Nullable Direction faceAt(BlockPos pos) {
        Entry entry = shown.get(pos);
        return entry != null ? entry.face() : null;
    }

    public static boolean has(BlockPos pos) {
        return shown.containsKey(pos);
    }
}
