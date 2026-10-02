/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.crafting.JobInfo;
import net.zagdrath.encodedlogistics.menu.SchedulerCoreMenu;

// Server to client: an open Scheduler Core screen's structure (formed, or SchedulerStructures.Problem's ordinal), its
// threads and job memory in use, and its jobs (in order: running ones have a thread, the rest are queued).
public record SchedulerStatusPayload(int containerId, boolean formed, int problem, int threadsUsed, int threads, long memoryUsed, long memory,
        List<JobInfo> jobs) implements CustomPacketPayload {
    public static final Type<SchedulerStatusPayload> TYPE = new Type<>(EncodedLogistics.id("scheduler_status"));
    public static final SchedulerStatusPayload EMPTY = new SchedulerStatusPayload(-1, false, 0, 0, 0, 0, 0, List.of());

    private static final StreamCodec<RegistryFriendlyByteBuf, List<JobInfo>> JOBS = JobInfo.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, SchedulerStatusPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
        ByteBufCodecs.VAR_INT.encode(buf, payload.containerId());
        ByteBufCodecs.BOOL.encode(buf, payload.formed());
        ByteBufCodecs.VAR_INT.encode(buf, payload.problem());
        ByteBufCodecs.VAR_INT.encode(buf, payload.threadsUsed());
        ByteBufCodecs.VAR_INT.encode(buf, payload.threads());
        ByteBufCodecs.VAR_LONG.encode(buf, payload.memoryUsed());
        ByteBufCodecs.VAR_LONG.encode(buf, payload.memory());
        JOBS.encode(buf, payload.jobs());
    }, buf -> new SchedulerStatusPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
            ByteBufCodecs.VAR_LONG.decode(buf), JOBS.decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(SchedulerStatusPayload payload, IPayloadContext context) {
        if (context.player().containerMenu instanceof SchedulerCoreMenu menu && menu.containerId == payload.containerId()) {
            menu.setStatus(payload);
        }
    }
}
