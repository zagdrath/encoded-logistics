/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.Arrays;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

// Six redstone channels, one per face, as the Control Interface and the PLC have them: the level each face gives out
// (out, 0-15, kept while the block is inactive and given out again when it's back) and the level arriving there (in).
// Reading a face never sees the block's own output: while it measures it gives out nothing, and redstone dust it powers
// itself (dust keeps its level) only counts when something else drives it higher. Weak power only.
public final class RedstoneChannels {
    public static final Direction[] FACES = Direction.values();

    private final int[] out = new int[6], in = new int[6];
    private boolean measuring;

    // What a face gives out to the block next to it: its level while the block's active, else nothing.
    public int emitted(Direction face, boolean active) {
        return active && !measuring ? out[face.ordinal()] : 0;
    }

    public int output(Direction face) {
        return out[face.ordinal()];
    }

    // The level last read on a face.
    public int input(Direction face) {
        return in[face.ordinal()];
    }

    // The highest level last read on any face (RTVRSIN SIDE(*MAX)).
    public int maxInput() {
        int max = 0;
        for (int level : in) {
            max = Math.max(max, level);
        }
        return max;
    }

    // CHGRSOUT: a face's output (null: every face), clamped to 0-15; the faces that changed (empty: none).
    public Direction[] setOutput(@Nullable Direction face, int levelValue) {
        int value = Math.clamp(levelValue, 0, 15);
        Direction[] changed = new Direction[0];
        for (Direction side : FACES) {
            if ((face == null || face == side) && out[side.ordinal()] != value) {
                out[side.ordinal()] = value;
                changed = Arrays.copyOf(changed, changed.length + 1);
                changed[changed.length - 1] = side;
            }
        }
        return changed;
    }

    // What arrives on each face now, the block's own output left out (active: whether it's giving its out levels out).
    // Returns the levels before, or null when nothing changed.
    public int @Nullable [] read(Level level, BlockPos pos, boolean active) {
        int[] before = in.clone();
        measuring = true;
        try {
            for (Direction face : FACES) {
                BlockPos next = pos.relative(face);
                int value = level.getSignal(next, face);
                // Dust this face powers holds that level itself: only more than that comes from elsewhere.
                if (active && value > 0 && value <= out[face.ordinal()] && level.getBlockState(next).is(Blocks.REDSTONE_WIRE)) {
                    value = 0;
                }
                in[face.ordinal()] = value;
            }
        } finally {
            measuring = false;
        }
        return Arrays.equals(before, in) ? null : before;
    }

    // --- Saving (the out levels; inputs are read again) ---

    public void load(ValueInput input) {
        for (Direction face : FACES) {
            out[face.ordinal()] = Math.clamp(input.getIntOr("out_" + face.getSerializedName(), 0), 0, 15);
        }
    }

    public void save(ValueOutput output) {
        for (Direction face : FACES) {
            if (out[face.ordinal()] != 0) {
                output.putInt("out_" + face.getSerializedName(), out[face.ordinal()]);
            }
        }
    }
}
