/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.crafting.JobHost;
import net.zagdrath.encodedlogistics.client.CraftingClient;
import net.zagdrath.encodedlogistics.crafting.CraftingJob;
import net.zagdrath.encodedlogistics.crafting.JobInfo;

// Both ways: the client asks for a job's status (job empty), the server answers with it (empty when the job is gone:
// finished or cancelled). The Job Status screen asks every few ticks while it's open.
public record JobStatusPayload(BlockPos core, UUID id, Optional<JobInfo> job) implements CustomPacketPayload {
    public static final Type<JobStatusPayload> TYPE = new Type<>(EncodedLogistics.id("job_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JobStatusPayload> STREAM_CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, JobStatusPayload::core,
            UUIDUtil.STREAM_CODEC, JobStatusPayload::id,
            ByteBufCodecs.optional(JobInfo.STREAM_CODEC), JobStatusPayload::job,
            JobStatusPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handleServer(JobStatusPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            JobHost core = JobAccess.core(player, payload.core());
            CraftingJob job = core != null ? core.job(payload.id()) : null;
            PacketDistributor.sendToPlayer(player, new JobStatusPayload(payload.core(), payload.id(),
                    Optional.ofNullable(job).map(found -> JobInfo.of(found, true))));
        }
    }

    static void handleClient(JobStatusPayload payload, IPayloadContext context) {
        CraftingClient.jobStatus(payload);
    }
}
