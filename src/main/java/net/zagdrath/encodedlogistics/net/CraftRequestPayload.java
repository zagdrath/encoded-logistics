/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.SchedulerCoreBlockEntity;
import net.zagdrath.encodedlogistics.crafting.CraftPlanner;
import net.zagdrath.encodedlogistics.crafting.CraftRequests;
import net.zagdrath.encodedlogistics.crafting.CraftingJob;
import net.zagdrath.encodedlogistics.menu.AccessTerminalMenu;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.storage.ItemKey;

// Client to server: plan crafting an item from an open terminal (start false), or plan and start it (start true) on a
// Scheduler (-1: Auto, else its index in the plan's list). The server answers with a CraftPlanPayload.
public record CraftRequestPayload(int containerId, ItemKey key, long amount, int scheduler, boolean start) implements CustomPacketPayload {
    public static final Type<CraftRequestPayload> TYPE = new Type<>(EncodedLogistics.id("craft_request"));
    public static final long MAX_AMOUNT = 999_999;

    public static final StreamCodec<RegistryFriendlyByteBuf, CraftRequestPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CraftRequestPayload::containerId,
            ItemKey.STREAM_CODEC, CraftRequestPayload::key,
            ByteBufCodecs.VAR_LONG, CraftRequestPayload::amount,
            ByteBufCodecs.VAR_INT, CraftRequestPayload::scheduler,
            ByteBufCodecs.BOOL, CraftRequestPayload::start,
            CraftRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(CraftRequestPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)
                || !(player.containerMenu instanceof AccessTerminalMenu menu) || menu.containerId != payload.containerId() || !menu.stillValid(player)) {
            return;
        }
        BlockPos device = menu.pos();
        if (!NetworkAccess.check(level, device, player, RackPermission.CRAFT)) {
            return;
        }
        long amount = Math.clamp(payload.amount(), 1, MAX_AMOUNT);
        CraftPlanner.Plan plan = CraftRequests.plan(level, device, payload.key(), amount);
        List<SchedulerCoreBlockEntity> schedulers = CraftRequests.schedulers(level, device);
        Optional<CraftPlanPayload.Started> started = Optional.empty();
        if (plan != null && payload.start()) {
            SchedulerCoreBlockEntity scheduler = CraftRequests.choose(schedulers, plan.memory(), payload.scheduler());
            CraftingJob job = scheduler != null ? CraftRequests.start(level, device, plan, scheduler) : null;
            if (job != null) {
                started = Optional.of(new CraftPlanPayload.Started(scheduler.getBlockPos(), job.id));
            }
        }
        List<BlockPos> positions = new ArrayList<>();
        for (SchedulerCoreBlockEntity scheduler : schedulers) {
            positions.add(scheduler.getBlockPos());
        }
        boolean room = plan != null && CraftRequests.choose(schedulers, plan.memory(), payload.scheduler()) != null;
        PacketDistributor.sendToPlayer(player, new CraftPlanPayload(payload.containerId(), payload.key(), amount,
                plan != null ? plan.lines() : List.of(), plan != null ? plan.missing() : 0, plan != null ? plan.memory() : 0,
                plan != null && plan.complete(), positions, room, started));
    }
}
