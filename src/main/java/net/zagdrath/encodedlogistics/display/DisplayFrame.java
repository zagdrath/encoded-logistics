/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.display;

import java.util.List;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// What a live widget shows this second (DisplayData works it out on the server; the canvas draws it): its region,
// a label and value text, numbers (a gauge's used and total), rows (a list's "dot|text|value"), a graph's series.
public record DisplayFrame(String region, String label, String value, List<Long> numbers, List<String> rows, List<Float> series) {
    public static final StreamCodec<RegistryFriendlyByteBuf, DisplayFrame> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DisplayFrame::region,
            ByteBufCodecs.STRING_UTF8, DisplayFrame::label,
            ByteBufCodecs.STRING_UTF8, DisplayFrame::value,
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list(16)), DisplayFrame::numbers,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(64)), DisplayFrame::rows,
            ByteBufCodecs.FLOAT.apply(ByteBufCodecs.list(1024)), DisplayFrame::series,
            DisplayFrame::new);

    public DisplayFrame {
        numbers = List.copyOf(numbers);
        rows = List.copyOf(rows);
        series = List.copyOf(series);
    }

    public long number(int i) {
        return i < numbers.size() ? numbers.get(i) : 0;
    }
}
