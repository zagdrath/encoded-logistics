/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.EnumSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;

// The 1311-style Disk Drive (HANDOFF 2, 5): one block, a Storage Drive in it served as its network's hot storage
// (DiskDriveBlockEntity). A lane, from any side but its front; it passes lanes on. ACTIVE: its pack spinning (the lamp).
public class DiskDriveBlock extends MidrangePeripheralBlock {
    public DiskDriveBlock(BlockBehaviour.Properties properties) {
        super("disk_drive", List.of(Vec3i.ZERO), DiskDriveBlockEntity::new, properties);
    }

    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        EnumSet<Direction> sides = EnumSet.allOf(Direction.class);
        sides.remove(state.getValue(FACING));
        return new DeviceNode(pos.immutable(), sides, 1, Config.DISK_DRIVE_DRAIN.getAsDouble(), List.of(), true);
    }
}
