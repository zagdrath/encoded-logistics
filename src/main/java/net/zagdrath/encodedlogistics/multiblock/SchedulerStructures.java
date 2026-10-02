/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.multiblock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.SchedulerBlock;
import net.zagdrath.encodedlogistics.blockentity.SchedulerCoreBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModBlocks;

// The Scheduler multiblocks in a level. Placing or breaking a scheduler block (or a Core loading) queues it; once per
// tick the queued blocks are flood-filled into groups of touching scheduler blocks and each group is checked: it forms
// when it fills its bounding box (a solid cuboid), no axis is longer than schedulerMaxSize, and it holds exactly one Core.
// Every block's FORMED is set from that, and each Core is told its structure (or why it didn't form). Nothing is saved:
// the blocks keep FORMED, and Cores re-queue themselves when they load.
public class SchedulerStructures extends SavedData {
    private static final int MAX_GROUP = 4096;

    public enum Problem {
        NONE, NO_CORE, TWO_CORES, NOT_CUBOID, TOO_LARGE;

        public String key() {
            return "gui.encodedlogistics.scheduler.problem." + name().toLowerCase(Locale.ROOT);
        }
    }

    public static final SavedDataType<SchedulerStructures> TYPE = new SavedDataType<>(EncodedLogistics.id("scheduler_structures"),
            SchedulerStructures::new, MapCodec.unit(SchedulerStructures::new).codec());

    private final Set<BlockPos> pending = new HashSet<>();
    // Each block of a formed structure, and its Core.
    private final Map<BlockPos, BlockPos> cores = new HashMap<>();

    public SchedulerStructures() {}

    public static SchedulerStructures get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(TYPE);
    }

    public void queue(BlockPos pos) {
        pending.add(pos.immutable());
    }

    // A scheduler block was broken: its structure (whatever is left of it) is checked again.
    public void removed(BlockPos pos) {
        BlockPos core = cores.remove(pos);
        if (core != null) {
            cores.values().removeIf(core::equals);
        }
        for (Direction side : Direction.values()) {
            pending.add(pos.relative(side));
        }
        pending.add(pos.immutable());
    }

    // The Core of the formed structure the block at pos is part of, or null.
    public @Nullable BlockPos coreOf(BlockPos pos) {
        return cores.get(pos);
    }

    public void tick(ServerLevel level) {
        if (pending.isEmpty()) {
            return;
        }
        List<BlockPos> order = new ArrayList<>(pending);
        order.sort(Comparator.naturalOrder());
        pending.clear();
        Set<BlockPos> done = new HashSet<>();
        boolean changed = false;
        for (BlockPos start : order) {
            if (done.contains(start)) {
                continue;
            }
            if (!isScheduler(level, start)) {
                if (cores.remove(start) != null) {
                    changed = true;
                }
                continue;
            }
            List<BlockPos> group = floodFill(level, start);
            done.addAll(group);
            changed |= form(level, group);
        }
        if (changed) {
            ControllerStructures.get(level).markTopologyChanged();
        }
    }

    // Checks one group and applies the result; true when any block's FORMED changed.
    private boolean form(ServerLevel level, List<BlockPos> group) {
        group.sort(Comparator.naturalOrder());
        List<BlockPos> coreBlocks = new ArrayList<>();
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int buffers = 0, threads = 0;
        for (BlockPos pos : group) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.SCHEDULER_CORE.get())) {
                coreBlocks.add(pos);
            } else if (state.is(ModBlocks.JOB_BUFFER.get())) {
                buffers++;
            } else if (state.is(ModBlocks.THREAD_UNIT.get())) {
                threads++;
            }
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }
        int sizeX = maxX - minX + 1, sizeY = maxY - minY + 1, sizeZ = maxZ - minZ + 1;
        int limit = Config.SCHEDULER_MAX_SIZE.getAsInt();
        Problem problem;
        if (coreBlocks.isEmpty()) {
            problem = Problem.NO_CORE;
        } else if (coreBlocks.size() > 1) {
            problem = Problem.TWO_CORES;
        } else if (group.size() >= MAX_GROUP || sizeX > limit || sizeY > limit || sizeZ > limit) {
            problem = Problem.TOO_LARGE;
        } else if ((long) sizeX * sizeY * sizeZ != group.size()) {
            problem = Problem.NOT_CUBOID;
        } else {
            problem = Problem.NONE;
        }
        boolean formed = problem == Problem.NONE;
        boolean changed = false;
        for (BlockPos pos : group) {
            cores.remove(pos);
            BlockState state = level.getBlockState(pos);
            if (state.getValue(SchedulerBlock.FORMED) != formed) {
                level.setBlock(pos, state.setValue(SchedulerBlock.FORMED, formed), Block.UPDATE_ALL);
                changed = true;
            }
        }
        if (formed) {
            BlockPos core = coreBlocks.getFirst();
            for (BlockPos pos : group) {
                cores.put(pos, core);
            }
        }
        for (BlockPos core : coreBlocks) {
            if (level.getBlockEntity(core) instanceof SchedulerCoreBlockEntity entity) {
                entity.setStructure(formed ? List.copyOf(group) : List.of(), problem, buffers, threads);
            }
        }
        return changed;
    }

    private static List<BlockPos> floodFill(ServerLevel level, BlockPos start) {
        List<BlockPos> group = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        while (!queue.isEmpty() && group.size() < MAX_GROUP) {
            BlockPos pos = queue.poll();
            group.add(pos);
            for (Direction side : Direction.values()) {
                BlockPos next = pos.relative(side);
                if (seen.add(next) && isScheduler(level, next)) {
                    queue.add(next);
                }
            }
        }
        return group;
    }

    private static boolean isScheduler(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.getBlockState(pos).getBlock() instanceof SchedulerBlock;
    }
}
