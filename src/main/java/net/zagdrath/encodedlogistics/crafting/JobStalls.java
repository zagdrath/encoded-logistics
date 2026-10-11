/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.crafting;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.zagdrath.encodedlogistics.blockentity.GatewayBlockEntity;

// Why a stalled job (JobRunner: no progress for jobStallSeconds) isn't moving, where that can be told, and which step
// it's stuck on. Reasons are lang keys under gui.encodedlogistics.jobs.reason:
//  tape             items it waits for are still coming back from tape
//  missing_machine  no online holder has the step's schematic, or a Gateway's machine is missing or not formed
//  unpowered        a Gateway's bridged machine has no power
//  output_full      a Gateway's bridged machine can't put its output anywhere
//  gateway_blocked  a Gateway can't push the step's inputs into its machine
//  ingredients      nothing is running and it doesn't hold the inputs for any step
// A stall with no reason found says only that it's stalled.
public final class JobStalls {
    public record Stall(@Nullable String reason, int step) {}

    private JobStalls() {}

    public static Stall find(ServerLevel level, CraftingJob job, List<CraftingProvider> providers) {
        if (!job.awaiting.isEmpty()) {
            return new Stall("tape", firstOpen(job));
        }
        for (int index = 0; index < job.steps.size(); index++) {
            CraftingJob.Step step = job.steps.get(index);
            if (step.finished()) {
                continue;
            }
            List<CraftingProvider> able = new ArrayList<>();
            for (CraftingProvider provider : providers) {
                if (provider.accepts(step.schematic)) {
                    able.add(provider);
                }
            }
            if (step.running > 0) {
                // In a holder: a Gateway can say what's holding it up.
                String reason = gatewayReason(level, able, job);
                if (reason != null) {
                    return new Stall(reason, index);
                }
                continue;
            }
            if (step.left() > 0 && job.holds(step.schematic.inputTotals())) {
                if (able.isEmpty()) {
                    return new Stall("missing_machine", index);
                }
                String reason = gatewayReason(level, able, null);
                return new Stall(reason, index);
            }
        }
        if (job.inFlight() == 0) {
            return new Stall("ingredients", firstOpen(job));
        }
        return new Stall(null, firstOpen(job));
    }

    private static @Nullable String gatewayReason(ServerLevel level, List<CraftingProvider> able, @Nullable CraftingJob job) {
        for (CraftingProvider provider : able) {
            if (provider instanceof GatewayBlockEntity gateway) {
                String reason = gateway.stallReason(level, job != null ? job.id : null);
                if (reason != null) {
                    return reason;
                }
            }
        }
        return null;
    }

    // The first step not done yet, or -1.
    private static int firstOpen(CraftingJob job) {
        for (int index = 0; index < job.steps.size(); index++) {
            if (!job.steps.get(index).finished()) {
                return index;
            }
        }
        return -1;
    }
}
