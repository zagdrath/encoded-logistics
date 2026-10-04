/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;

// The rack popup's numbers. The client asks for the unit its crosshair settles on (Query, then every few ticks while it
// stays there); the server answers with the device's description (Info), so the popup can show server-side figures
// without every device's state being synced all the time.
public final class RackUnitPayloads {
    // How far from the rack a player may be to ask.
    private static final double REACH_SQR = 16 * 16;

    private RackUnitPayloads() {}

    public record Query(BlockPos rack, int u) implements CustomPacketPayload {
        public static final Type<Query> TYPE = new Type<>(EncodedLogistics.id("rack_unit_query"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Query> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Query::rack,
                ByteBufCodecs.VAR_INT, Query::u,
                Query::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(Query query, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player) || player.distanceToSqr(Vec3.atCenterOf(query.rack())) > REACH_SQR
                    || !player.level().isLoaded(query.rack()) || !(player.level().getBlockEntity(query.rack()) instanceof RackBlockEntity rack)) {
                return;
            }
            RackDevice device = rack.deviceAt(query.u());
            if (device != null) {
                PacketDistributor.sendToPlayer(player, new Info(query.rack(), device.u(), device.describe(player).withHeader(rack.header())));
            }
        }
    }

    public record Info(BlockPos rack, int u, RackDeviceInfo info) implements CustomPacketPayload {
        public static final Type<Info> TYPE = new Type<>(EncodedLogistics.id("rack_unit_info"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Info> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Info::rack,
                ByteBufCodecs.VAR_INT, Info::u,
                RackDeviceInfo.STREAM_CODEC, Info::info,
                Info::new);

        // Client side: the last answer.
        private static volatile @Nullable Info latest;

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        // The last answer about the device at u in the rack at pos, or null.
        public static @Nullable RackDeviceInfo forUnit(BlockPos rack, int u) {
            Info last = latest;
            return last != null && last.rack().equals(rack) && last.u() == u ? last.info() : null;
        }

        static void handle(Info info, IPayloadContext context) {
            latest = info;
        }
    }
}
