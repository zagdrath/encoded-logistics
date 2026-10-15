/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.block.ExchangeSwitchBlock;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.machine.MachineBridge;
import net.zagdrath.encodedlogistics.midrange.FootprintBlock;
import net.zagdrath.encodedlogistics.midrange.MidrangeHud;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.optical.SpanLoss;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.wireless.Wireless;
import net.zagdrath.encodedlogistics.wireless.WirelessDevice;

// The wireless blocks' (and the Midrange Disk and Tape Drives' and the signal devices') popup (WirelessHud), as the rack's: the client asks about the block its crosshair settles on
// (Query, then every few ticks while it stays); the server answers with its description and device name (Info).
public final class WirelessInfoPayloads {
    private static final double REACH_SQR = 16 * 16;

    private WirelessInfoPayloads() {}

    // bridge: about the Small Wireless Bridge on the machine at pos itself (answered as the space in front of its face),
    // not the machine.
    public record Query(BlockPos pos, boolean bridge) implements CustomPacketPayload {
        public static final Type<Query> TYPE = new Type<>(EncodedLogistics.id("wireless_info_query"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Query> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Query::pos,
                ByteBufCodecs.BOOL, Query::bridge, Query::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        // A machine with a Small Wireless Bridge on answers only players with view permission on its network.
        static void handle(Query query, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player) || player.distanceToSqr(Vec3.atCenterOf(query.pos())) > REACH_SQR
                    || !player.level().isLoaded(query.pos())) {
                return;
            }
            ServerLevel level = player.level();
            // A Midrange Disk or Tape Drive (any of its blocks), or a signal device: its own popup.
            BlockState state = level.getBlockState(query.pos());
            BlockPos master = state.getBlock() instanceof FootprintBlock footprint ? footprint.master(level, query.pos(), state)
                    : state.getBlock() instanceof ExchangeSwitchBlock ? ExchangeSwitchBlock.lower(query.pos(), state) : query.pos();
            // A Fiber Cable: the network it's on, its span since the last amplifier, and the lanes running through it out of
            // what it carries after the span's loss (to players with view permission on that network).
            if (state.getBlock() instanceof NetworkCableBlock cable && cable.getTier() == CableTier.FIBER) {
                ControllerStructures.FiberInfo fiber = ControllerStructures.fiberInfo(level.getServer(), NodePos.of(level.dimension(), query.pos()));
                if (fiber == null || NetworkAccess.allowed(level.getServer(), fiber.network(), player, RackPermission.VIEW)) {
                    PacketDistributor.sendToPlayer(player, new Info(query.pos(), "", fiberInfo(level.getServer(), state, fiber)));
                }
                return;
            }
            if (master != null && level.getBlockEntity(master) instanceof MidrangeHud hud) {
                PacketDistributor.sendToPlayer(player, new Info(query.pos(), hud.deviceName(), hud.hudInfo()));
                return;
            }
            WirelessDevice device = Wireless.deviceAt(level.getServer(), NodePos.of(level.dimension(), query.pos()));
            if (device == null || device instanceof MachineBridge bridge && bridge.link() != null
                    && !NetworkAccess.allowed(level.getServer(), bridge.link().network(), player, RackPermission.VIEW)) {
                return;
            }
            if (query.bridge()) {
                if (device instanceof MachineBridge bridge) {
                    PacketDistributor.sendToPlayer(player, new Info(query.pos().relative(bridge.face()), bridge.deviceName(),
                            bridge.describeBridge(level.getServer())));
                }
                return;
            }
            PacketDistributor.sendToPlayer(player, new Info(query.pos(), device.deviceName(), device.describe(level.getServer())));
        }
    }

    private static RackDeviceInfo fiberInfo(MinecraftServer server, BlockState state, ControllerStructures.@Nullable FiberInfo fiber) {
        if (fiber == null) {
            return new RackDeviceInfo(state.getBlock().getName(), RackDeviceInfo.Status.OFFLINE, Component.translatable("hud.encodedlogistics.fiber.no_network"),
                    List.of());
        }
        SpanLoss.Span span = fiber.span();
        int lost = span != null ? span.lost() : 0;
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.fiber.network"),
                Component.literal(RouterDevice.networkName(server, fiber.network()))));
        if (span != null) {
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.fiber.span"), Component.literal(span.length() + " m")));
        }
        if (fiber.adHoc()) {
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.fiber.lanes"),
                    Component.translatable("hud.encodedlogistics.fiber.ad_hoc")));
        } else {
            int capacity = Math.max(1, fiber.capacity());
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("hud.encodedlogistics.fiber.lanes"),
                    Component.literal(fiber.used() + " / " + fiber.capacity()),
                    new RackDeviceInfo.Bar(Math.min(1.0F, (float) fiber.used() / capacity), lost > 0 ? RackDeviceInfo.BarStyle.WARN
                            : RackDeviceInfo.BarStyle.NORMAL)));
        }
        return new RackDeviceInfo(state.getBlock().getName(), lost > 0 ? RackDeviceInfo.Status.WARNING : RackDeviceInfo.Status.ONLINE,
                Component.translatable(lost > 0 ? "hud.encodedlogistics.fiber.loss" : "hud.encodedlogistics.fiber.ok", lost), lines);
    }

    public record Info(BlockPos pos, String name, RackDeviceInfo info) implements CustomPacketPayload {
        public static final Type<Info> TYPE = new Type<>(EncodedLogistics.id("wireless_info"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Info> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Info::pos,
                ByteBufCodecs.STRING_UTF8, Info::name,
                RackDeviceInfo.STREAM_CODEC, Info::info,
                Info::new);

        // Client side: the answers so far, by position (the most recently seen kept).
        private static final int KEPT = 64;
        private static final Map<BlockPos, Info> ANSWERS = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<BlockPos, Info> eldest) {
                return size() > KEPT;
            }
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Info info, IPayloadContext context) {
            ANSWERS.put(info.pos(), info);
        }

        public static @Nullable Info at(BlockPos pos) {
            return ANSWERS.get(pos);
        }
    }
}
