/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import net.minecraft.core.BlockPos;

// A connection between two adjacent nodes, carrying at most capacity channels (the smaller tier of its two ends).
// from is always the lower position, so a link has one identity whichever end it is looked up from.
public record NetworkLink(BlockPos from, BlockPos to, int capacity) {
    public NetworkLink {
        if (from.compareTo(to) > 0) {
            BlockPos swap = from;
            from = to;
            to = swap;
        }
    }

    public BlockPos other(BlockPos end) {
        return end.equals(from) ? to : from;
    }
}
