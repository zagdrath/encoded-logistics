/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack.device;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.crafting.CraftLog;
import net.zagdrath.encodedlogistics.crafting.CraftingJob;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackScheduler;

// A Compute or Memory Server: what it gives its network's rack Scheduler (RackScheduler: the pool of every rack on the
// network). Its panel shows its own share, the pool's use of it, and the jobs the pool runs (each with a cancel button:
// JobCancelPayload, to the rack, which resolves to the pool's head).
public abstract class ServerDevice extends RackDevice {
    protected ServerDevice(RackDeviceType type) {
        super(type);
    }

    // The pool this server is part of: its network's, headed by whichever rack heads it.
    protected @Nullable RackScheduler scheduler() {
        return rack() != null ? rack().scheduler().forDevice(this) : null;
    }

    @Override
    public void writePanel(ValueOutput output, ServerPlayer viewer) {
        save(output);
        RackScheduler scheduler = scheduler();
        output.putBoolean("active", scheduler != null && scheduler.active());
        if (scheduler == null) {
            return;
        }
        output.putInt("threads", scheduler.threads());
        output.putInt("threads_used", scheduler.threadsUsed());
        output.putLong("memory", scheduler.memory());
        output.putLong("memory_used", scheduler.memoryUsed());
        ValueOutput.ValueOutputList jobs = output.childrenList("jobs");
        for (CraftingJob job : scheduler.jobs()) {
            ValueOutput child = jobs.addChild();
            child.putString("id", job.id.toString());
            child.putString("target", job.target.stack().getHoverName().getString());
            child.putLong("amount", job.amount);
            child.putBoolean("running", job.running);
            child.putLong("memory", job.memory);
        }
        // The last few that ended on the pool (CraftLog, by its head rack).
        if (rack() != null && rack().getLevel() instanceof ServerLevel level) {
            NetworkRef network = scheduler.network();
            ElclSystem system = network != null ? new ElclSystem(level.getServer(), network) : null;
            ValueOutput.ValueOutputList recent = output.childrenList("recent");
            for (CraftLog.Entry entry : CraftLog.recent(level.getServer(), network, scheduler.hostPos(), CraftLog.RECENT)) {
                ValueOutput child = recent.addChild();
                child.putString("target", CraftLog.stack(entry).getHoverName().getString());
                child.putLong("requested", entry.requested());
                child.putLong("produced", entry.produced());
                child.putInt("status", entry.status().ordinal());
                child.putString("reason", entry.reason());
                child.putString("ended", system != null ? system.at(entry.ended()) : "");
                child.putLong("duration", entry.duration());
            }
        }
    }
}
