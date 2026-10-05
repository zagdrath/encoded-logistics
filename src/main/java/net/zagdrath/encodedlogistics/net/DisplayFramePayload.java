/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.display.DisplayFrame;

// Server to client, once a second while it changes: a Display Panel screen's live widgets (DisplayFrame per region),
// to the players near it. Kept by the screen's master position for the canvas to draw.
public record DisplayFramePayload(BlockPos pos, List<DisplayFrame> frames) implements CustomPacketPayload {
    public static final Type<DisplayFramePayload> TYPE = new Type<>(EncodedLogistics.id("display_frame"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayFramePayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, DisplayFramePayload::pos,
            DisplayFrame.STREAM_CODEC.apply(ByteBufCodecs.list(64)), DisplayFramePayload::frames,
            DisplayFramePayload::new);

    private static final Map<BlockPos, List<DisplayFrame>> LATEST = new ConcurrentHashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(DisplayFramePayload payload, IPayloadContext context) {
        LATEST.put(payload.pos(), payload.frames());
    }

    public static List<DisplayFrame> frames(BlockPos pos) {
        return LATEST.getOrDefault(pos, List.of());
    }

    public static void forget(BlockPos pos) {
        LATEST.remove(pos);
    }
}
