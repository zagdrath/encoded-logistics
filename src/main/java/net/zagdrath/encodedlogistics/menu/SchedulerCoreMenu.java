/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.blockentity.SchedulerCoreBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftLog;
import net.zagdrath.encodedlogistics.crafting.CraftingJob;
import net.zagdrath.encodedlogistics.crafting.JobInfo;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.net.SchedulerStatusPayload;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// The Scheduler Core's screen: its jobs (running, then queued), its threads and job memory in use, and whether the
// structure is formed (or why not). The server sends it all every SYNC_INTERVAL ticks (SchedulerStatusPayload); jobs are
// cancelled with JobCancelPayload.
public class SchedulerCoreMenu extends AbstractContainerMenu {
    private static final int SYNC_INTERVAL = 10;

    private final BlockPos pos;
    private final @Nullable SchedulerCoreBlockEntity core;
    private final Player player;
    private int ticksUntilSync;

    // Client: as last received.
    private SchedulerStatusPayload status = SchedulerStatusPayload.EMPTY;

    public SchedulerCoreMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenuTypes.SCHEDULER_CORE.get(), containerId);
        this.pos = extraData.readBlockPos();
        this.core = null;
        this.player = inventory.player;
    }

    public SchedulerCoreMenu(int containerId, Inventory inventory, SchedulerCoreBlockEntity core) {
        super(ModMenuTypes.SCHEDULER_CORE.get(), containerId);
        this.pos = core.getBlockPos();
        this.core = core;
        this.player = inventory.player;
    }

    public BlockPos pos() {
        return pos;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (core == null || !(player instanceof ServerPlayer serverPlayer) || --ticksUntilSync > 0) {
            return;
        }
        ticksUntilSync = SYNC_INTERVAL;
        PacketDistributor.sendToPlayer(serverPlayer, status(containerId, core));
    }

    private static SchedulerStatusPayload status(int containerId, SchedulerCoreBlockEntity core) {
        List<JobInfo> jobs = new ArrayList<>();
        for (CraftingJob job : core.jobs()) {
            jobs.add(JobInfo.of(job, false));
        }
        // The last few that ended here.
        List<SchedulerStatusPayload.Recent> recent = new ArrayList<>();
        if (core.getLevel() instanceof ServerLevel level) {
            NetworkRef network = ControllerStructures.networkOf(level, core.getBlockPos());
            ElclSystem system = network != null ? new ElclSystem(level.getServer(), network) : null;
            for (CraftLog.Entry entry : CraftLog.recent(level.getServer(), network, core.getBlockPos(), CraftLog.RECENT)) {
                recent.add(new SchedulerStatusPayload.Recent(CraftLog.stack(entry), entry.requested(), entry.produced(), entry.status().ordinal(),
                        entry.reason(), system != null ? system.at(entry.ended()) : "", entry.duration()));
            }
        }
        return new SchedulerStatusPayload(containerId, core.formed(), core.problem().ordinal(), core.threadsUsed(), core.threads(),
                core.memoryUsed(), core.memory(), jobs, recent);
    }

    public void setStatus(SchedulerStatusPayload status) {
        this.status = status;
    }

    public SchedulerStatusPayload status() {
        return status;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return core == null || !core.isRemoved() && player.isWithinBlockInteractionRange(pos, 4.0);
    }
}
