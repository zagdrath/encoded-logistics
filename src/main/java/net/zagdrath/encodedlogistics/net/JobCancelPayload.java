/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.crafting.JobHost;

// Client to server: cancel a job (from the Scheduler Core or Job Status screen).
public record JobCancelPayload(BlockPos core, UUID job) implements CustomPacketPayload {
    public static final Type<JobCancelPayload> TYPE = new Type<>(EncodedLogistics.id("job_cancel"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JobCancelPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, JobCancelPayload::core,
            UUIDUtil.STREAM_CODEC, JobCancelPayload::job,
            JobCancelPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(JobCancelPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            JobHost core = JobAccess.core(player, payload.core());
            if (core != null) {
                core.cancel(payload.job());
            }
        }
    }
}
