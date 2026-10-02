/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.network;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

// A block that is part of a network without a block entity (cables): NetworkDiscovery asks the block for its node.
// Blocks with a block entity can implement NetworkNodeHost on the block entity instead.
public interface NetworkNodeBlock {
    @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state);
}
