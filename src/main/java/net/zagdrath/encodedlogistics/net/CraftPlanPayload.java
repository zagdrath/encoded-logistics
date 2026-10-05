/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.client.CraftingClient;
import net.zagdrath.encodedlogistics.crafting.CraftPlanner;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// Server to client: a crafting plan for an open terminal - the ingredient tree, how many items are missing, the job
// memory it needs, whether it can be made (complete), the network's Schedulers and whether the chosen one has room,
// the job when it was just started, and how long recalling its ingredients from tape should take (ticks, 0 for none).
public record CraftPlanPayload(int containerId, StorageKey key, long amount, List<CraftPlanner.Line> lines, int missing, long memory, boolean complete,
        List<BlockPos> schedulers, boolean room, Optional<Started> started, int recallTicks) implements CustomPacketPayload {
    public static final Type<CraftPlanPayload> TYPE = new Type<>(EncodedLogistics.id("craft_plan"));

    public record Started(BlockPos core, UUID job) {
        static final StreamCodec<RegistryFriendlyByteBuf, Started> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Started::core,
                UUIDUtil.STREAM_CODEC, Started::job,
                Started::new);
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, List<CraftPlanner.Line>> LINES = CraftPlanner.Line.STREAM_CODEC.apply(ByteBufCodecs.list());
    private static final StreamCodec<RegistryFriendlyByteBuf, List<BlockPos>> POSITIONS = BlockPos.STREAM_CODEC.<RegistryFriendlyByteBuf>cast()
            .apply(ByteBufCodecs.list());
    private static final StreamCodec<RegistryFriendlyByteBuf, Optional<Started>> STARTED = ByteBufCodecs.optional(Started.STREAM_CODEC);

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftPlanPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
        ByteBufCodecs.VAR_INT.encode(buf, payload.containerId());
        StorageKey.STREAM_CODEC.encode(buf, payload.key());
        ByteBufCodecs.VAR_LONG.encode(buf, payload.amount());
        LINES.encode(buf, payload.lines());
        ByteBufCodecs.VAR_INT.encode(buf, payload.missing());
        ByteBufCodecs.VAR_LONG.encode(buf, payload.memory());
        ByteBufCodecs.BOOL.encode(buf, payload.complete());
        POSITIONS.encode(buf, payload.schedulers());
        ByteBufCodecs.BOOL.encode(buf, payload.room());
        STARTED.encode(buf, payload.started());
        ByteBufCodecs.VAR_INT.encode(buf, payload.recallTicks());
    }, buf -> new CraftPlanPayload(ByteBufCodecs.VAR_INT.decode(buf), StorageKey.STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
            LINES.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.BOOL.decode(buf),
            POSITIONS.decode(buf), ByteBufCodecs.BOOL.decode(buf), STARTED.decode(buf), ByteBufCodecs.VAR_INT.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Whether Start can be pressed: nothing missing and a Scheduler with room.
    public boolean canStart() {
        return complete && room;
    }

    static void handle(CraftPlanPayload payload, IPayloadContext context) {
        CraftingClient.plan(payload);
    }
}
