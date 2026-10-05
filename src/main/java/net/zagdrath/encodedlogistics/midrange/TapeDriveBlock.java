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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;

// The 729-style Tape Drive (HANDOFF 2, 5): 2.5 blocks tall on a 1 x 3 footprint - its master (the cabinet and the reel
// window, a model 32 px tall), the middle (empty) and the top (the control strip). Its reel is cold storage
// (TapeDriveBlockEntity). A lane, on the master, from any side but its front; the others pass lanes on. ACTIVE: reading
// or writing (the lamps on the top).
public class TapeDriveBlock extends MidrangePeripheralBlock {
    private static final List<Vec3i> FOOTPRINT = List.of(Vec3i.ZERO, new Vec3i(0, 1, 0), new Vec3i(0, 2, 0));

    public TapeDriveBlock(BlockBehaviour.Properties properties) {
        super("tape_drive", FOOTPRINT, TapeDriveBlockEntity::new, properties);
    }

    @Override
    public EnumProperty<Part> part() {
        return TAPE_PART;
    }

    @Override
    protected BlockState dummyState(BlockState master, Vec3i offset) {
        return master.setValue(TAPE_PART, offset.getY() == 1 ? Part.MIDDLE : Part.TOP);
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
        boolean master = isMaster(state);
        return new DeviceNode(pos.immutable(), sides, master ? 1 : 0, master ? Config.TAPE_DRIVE_DRAIN.getAsDouble() : 0, List.of(), true);
    }
}
