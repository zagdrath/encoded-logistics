/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.zagdrath.encodedlogistics.Config;

// What a Storage Drive item remembers of its contents (which live in DriveStorage): for its tooltip, the Drive Bay's
// fill bars and the status light.
public record DriveStats(long bytesUsed, long bytesTotal, int typesUsed) {
    public static final Codec<DriveStats> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("bytes_used").forGetter(DriveStats::bytesUsed),
            Codec.LONG.fieldOf("bytes_total").forGetter(DriveStats::bytesTotal),
            Codec.INT.fieldOf("types_used").forGetter(DriveStats::typesUsed))
            .apply(i, DriveStats::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DriveStats> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, DriveStats::bytesUsed,
            ByteBufCodecs.VAR_LONG, DriveStats::bytesTotal,
            ByteBufCodecs.VAR_INT, DriveStats::typesUsed,
            DriveStats::new);

    public static DriveStats empty(StorageTier tier) {
        return new DriveStats(0, tier.bytes(), 0);
    }

    // How full the drive is, 0-1: bytes or types, whichever is further along.
    public double fill() {
        double bytes = bytesTotal <= 0 ? 0 : (double) bytesUsed / bytesTotal;
        double types = (double) typesUsed / Config.DRIVE_TYPE_LIMIT.getAsInt();
        return Math.min(1, Math.max(bytes, types));
    }

    // The status light: 0 green below 50%, 1 yellow to 75%, 2 orange below full, 3 red when full.
    public int light() {
        double fill = fill();
        return fill >= 1 ? 3 : fill >= 0.75 ? 2 : fill >= 0.5 ? 1 : 0;
    }
}
