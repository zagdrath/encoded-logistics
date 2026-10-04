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
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.crafting.JobInfo;
import net.zagdrath.encodedlogistics.menu.SchedulerCoreMenu;

// Server to client: an open Scheduler Core screen's structure (formed, or SchedulerStructures.Problem's ordinal), its
// threads and job memory in use, its jobs (in order: running ones have a thread, the rest are queued) and the last few
// that ended there (CraftLog, newest first).
public record SchedulerStatusPayload(int containerId, boolean formed, int problem, int threadsUsed, int threads, long memoryUsed, long memory,
        List<JobInfo> jobs, List<Recent> recent) implements CustomPacketPayload {
    public static final Type<SchedulerStatusPayload> TYPE = new Type<>(EncodedLogistics.id("scheduler_status"));
    public static final SchedulerStatusPayload EMPTY = new SchedulerStatusPayload(-1, false, 0, 0, 0, 0, 0, List.of(), List.of());

    // An ended job: its item, how many were asked for and made, how it ended (CraftLog.Status' ordinal) and why it
    // failed, when it ended (as the screens show it) and how long it ran (game ticks).
    public record Recent(ItemStack item, long requested, long produced, int status, String reason, String ended, long duration) {
        static final StreamCodec<RegistryFriendlyByteBuf, Recent> STREAM_CODEC = StreamCodec.of((buf, recent) -> {
            ItemStack.OPTIONAL_STREAM_CODEC.encode(buf, recent.item());
            ByteBufCodecs.VAR_LONG.encode(buf, recent.requested());
            ByteBufCodecs.VAR_LONG.encode(buf, recent.produced());
            ByteBufCodecs.VAR_INT.encode(buf, recent.status());
            ByteBufCodecs.STRING_UTF8.encode(buf, recent.reason());
            ByteBufCodecs.STRING_UTF8.encode(buf, recent.ended());
            ByteBufCodecs.VAR_LONG.encode(buf, recent.duration());
        }, buf -> new Recent(ItemStack.OPTIONAL_STREAM_CODEC.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.STRING_UTF8.decode(buf),
                ByteBufCodecs.VAR_LONG.decode(buf)));
    }

    private static final StreamCodec<RegistryFriendlyByteBuf, List<JobInfo>> JOBS = JobInfo.STREAM_CODEC.apply(ByteBufCodecs.list());
    private static final StreamCodec<RegistryFriendlyByteBuf, List<Recent>> RECENT = Recent.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<RegistryFriendlyByteBuf, SchedulerStatusPayload> STREAM_CODEC = StreamCodec.of((buf, payload) -> {
        ByteBufCodecs.VAR_INT.encode(buf, payload.containerId());
        ByteBufCodecs.BOOL.encode(buf, payload.formed());
        ByteBufCodecs.VAR_INT.encode(buf, payload.problem());
        ByteBufCodecs.VAR_INT.encode(buf, payload.threadsUsed());
        ByteBufCodecs.VAR_INT.encode(buf, payload.threads());
        ByteBufCodecs.VAR_LONG.encode(buf, payload.memoryUsed());
        ByteBufCodecs.VAR_LONG.encode(buf, payload.memory());
        JOBS.encode(buf, payload.jobs());
        RECENT.encode(buf, payload.recent());
    }, buf -> new SchedulerStatusPayload(ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
            ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
            ByteBufCodecs.VAR_LONG.decode(buf), JOBS.decode(buf), RECENT.decode(buf)));

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
