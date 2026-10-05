/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.machine.MachineBridge;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.wireless.Wireless;
import net.zagdrath.encodedlogistics.wireless.WirelessDevice;

// The wireless blocks' popup (WirelessHud), as the rack's: the client asks about the block its crosshair settles on
// (Query, then every few ticks while it stays); the server answers with its description and device name (Info).
public final class WirelessInfoPayloads {
    private static final double REACH_SQR = 16 * 16;

    private WirelessInfoPayloads() {}

    public record Query(BlockPos pos) implements CustomPacketPayload {
        public static final Type<Query> TYPE = new Type<>(EncodedLogistics.id("wireless_info_query"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Query> STREAM_CODEC = StreamCodec.composite(BlockPos.STREAM_CODEC, Query::pos, Query::new);

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
            WirelessDevice device = Wireless.deviceAt(level.getServer(), NodePos.of(level.dimension(), query.pos()));
            if (device == null || device instanceof MachineBridge bridge && bridge.link() != null
                    && !NetworkAccess.allowed(level.getServer(), bridge.link().network(), player, RackPermission.VIEW)) {
                return;
            }
            PacketDistributor.sendToPlayer(player, new Info(query.pos(), device.deviceName(), device.describe(level.getServer())));
        }
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
