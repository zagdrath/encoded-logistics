/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.client.JobToasts;
import net.zagdrath.encodedlogistics.storage.ItemKey;

// Server to client: a crafting job ended (JobEvents) - what it made and how many, how it ended (JobEvents.Outcome), from a
// Processing Schematic or crafting, why it failed (a reason key, or empty), how long it ran (ticks), and whether the
// player asked for it. The client decides whether to show a toast (JobToasts).
public record JobToastPayload(ItemKey item, long amount, int outcome, boolean processing, String reason, long duration, boolean mine)
        implements CustomPacketPayload {
    public static final Type<JobToastPayload> TYPE = new Type<>(EncodedLogistics.id("job_toast"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JobToastPayload> STREAM_CODEC = StreamCodec.composite(
            ItemKey.STREAM_CODEC, JobToastPayload::item,
            ByteBufCodecs.VAR_LONG, JobToastPayload::amount,
            ByteBufCodecs.VAR_INT, JobToastPayload::outcome,
            ByteBufCodecs.BOOL, JobToastPayload::processing,
            ByteBufCodecs.STRING_UTF8, JobToastPayload::reason,
            ByteBufCodecs.VAR_LONG, JobToastPayload::duration,
            ByteBufCodecs.BOOL, JobToastPayload::mine,
            JobToastPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(JobToastPayload payload, IPayloadContext context) {
        JobToasts.receive(payload);
    }
}
