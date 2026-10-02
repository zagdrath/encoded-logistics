/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;

// Server to client: the snapshot of the network whose screen is open.
public record NetworkSnapshotPayload(int containerId, NetworkSnapshot snapshot) implements CustomPacketPayload {
    public static final Type<NetworkSnapshotPayload> TYPE = new Type<>(EncodedLogistics.id("network_snapshot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, NetworkSnapshotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, NetworkSnapshotPayload::containerId,
            NetworkSnapshot.STREAM_CODEC, NetworkSnapshotPayload::snapshot,
            NetworkSnapshotPayload::new);

    // Client side: the last snapshot received.
    private static volatile @Nullable NetworkSnapshotPayload latest;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // The snapshot for the open menu, or EMPTY until the server has sent one.
    public static NetworkSnapshot forMenu(int containerId) {
        NetworkSnapshotPayload last = latest;
        return last != null && last.containerId() == containerId ? last.snapshot() : NetworkSnapshot.EMPTY;
    }

    static void handle(NetworkSnapshotPayload payload, IPayloadContext context) {
        latest = payload;
    }
}
