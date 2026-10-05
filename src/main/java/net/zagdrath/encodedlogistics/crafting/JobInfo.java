/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// A job as its screens show it: what it makes and how many, its runs done of all, whether it has a thread, and (for the
// Job Status screen) each step's output and runs, and how many items it still awaits from tape.
public record JobInfo(UUID id, StorageKey item, long amount, int done, int total, boolean running, List<StepInfo> steps, long awaiting) {
    public record StepInfo(StorageKey output, int done, int total) {
        public static final StreamCodec<RegistryFriendlyByteBuf, StepInfo> STREAM_CODEC = StreamCodec.composite(
                StorageKey.STREAM_CODEC, StepInfo::output,
                ByteBufCodecs.VAR_INT, StepInfo::done,
                ByteBufCodecs.VAR_INT, StepInfo::total,
                StepInfo::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, JobInfo> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, JobInfo::id,
            StorageKey.STREAM_CODEC, JobInfo::item,
            ByteBufCodecs.VAR_LONG, JobInfo::amount,
            ByteBufCodecs.VAR_INT, JobInfo::done,
            ByteBufCodecs.VAR_INT, JobInfo::total,
            ByteBufCodecs.BOOL, JobInfo::running,
            StepInfo.STREAM_CODEC.apply(ByteBufCodecs.list()), JobInfo::steps,
            ByteBufCodecs.VAR_LONG, JobInfo::awaiting,
            JobInfo::new);

    // steps: include each step (for Job Status), or leave them out (for lists).
    public static JobInfo of(CraftingJob job, boolean steps) {
        List<StepInfo> list = new ArrayList<>();
        if (steps) {
            for (CraftingJob.Step step : job.steps) {
                list.add(new StepInfo(StorageKey.entry(step.schematic.output()), Math.min(step.done, step.total), step.total));
            }
        }
        return new JobInfo(job.id, job.target, job.amount, job.done(), job.total(), job.running, list,
                job.awaiting.values().stream().mapToLong(Long::longValue).sum());
    }

    public float progress() {
        return total <= 0 ? 0 : (float) done / total;
    }
}
