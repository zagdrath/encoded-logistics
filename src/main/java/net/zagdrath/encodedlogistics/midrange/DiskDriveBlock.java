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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.storage.ResourceType;

// The 1311-style Disk Drive (HANDOFF 2, 5): one block, a Storage Drive in it served as its network's hot storage
// (DiskDriveBlockEntity). A lane, from any side but its front; it passes lanes on. ACTIVE: its pack spinning (the lamp).
// DRIVE_TYPE: the type of the drive in it, which picks the pack it shows (item packs, also with none in it).
public class DiskDriveBlock extends MidrangePeripheralBlock {
    public static final EnumProperty<ResourceType> DRIVE_TYPE = EnumProperty.create("drive_type", ResourceType.class);

    public DiskDriveBlock(BlockBehaviour.Properties properties) {
        super("disk_drive", List.of(Vec3i.ZERO), DiskDriveBlockEntity::new, properties);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(DRIVE_TYPE);
    }

    // Not on its front.
    @Override
    public boolean connectsOn(BlockState state, Direction side) {
        return side != state.getValue(FACING);
    }

    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        EnumSet<Direction> sides = EnumSet.allOf(Direction.class);
        sides.remove(state.getValue(FACING));
        return new DeviceNode(pos.immutable(), sides, 1, Config.DISK_DRIVE_DRAIN.getAsDouble(), List.of(), true);
    }
}
