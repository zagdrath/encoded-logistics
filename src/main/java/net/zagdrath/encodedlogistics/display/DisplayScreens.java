/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.display;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;

// How Display Panels merge into screens (HANDOFF 1): panels with the same facing, side by side or stacked in one
// plane, are flooded together and split greedily into the largest rectangles up to displayMaxWidth x displayMaxHeight
// (largest area first; ties: the top-most, then the left-most, as seen from the front). Each rectangle is one screen:
// its master is its bottom-left block, holding the content; its blocks show a bezel only on the rectangle's outer edges
// and the status LED on its bottom-right block. A new rectangle takes the content of a screen that was inside it (one
// of the same size first, else the largest).
public final class DisplayScreens {
    // A flood stops here (a wall of panels bigger than this splits in more passes).
    private static final int MAX_FLOOD = 1024;

    private DisplayScreens() {}

    // Screen coordinates: u along the front's right (as seen from the front), v up.
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    public static BlockPos at(BlockPos origin, Direction facing, int u, int v) {
        return origin.relative(right(facing), u).above(v);
    }

    // Re-merges the panels joined to these positions (a panel placed, or the neighbours of one broken).
    public static void remerge(ServerLevel level, List<BlockPos> starts, Direction facing) {
        Set<BlockPos> done = new HashSet<>();
        for (BlockPos start : starts) {
            if (!done.contains(start) && isPanel(level, start, facing)) {
                Set<BlockPos> group = flood(level, start, facing);
                done.addAll(group);
                split(level, group, facing);
            }
        }
        ControllerStructures.get(level).markTopologyChanged();
    }

    private static boolean isPanel(ServerLevel level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof DisplayPanelBlock && state.getValue(DisplayPanelBlock.FACING) == facing;
    }

    private static Set<BlockPos> flood(ServerLevel level, BlockPos start, Direction facing) {
        Set<BlockPos> seen = new HashSet<>(Set.of(start));
        ArrayDeque<BlockPos> queue = new ArrayDeque<>(seen);
        Direction right = right(facing);
        while (!queue.isEmpty() && seen.size() < MAX_FLOOD) {
            BlockPos pos = queue.poll();
            for (Direction side : new Direction[] { right, right.getOpposite(), Direction.UP, Direction.DOWN }) {
                BlockPos next = pos.relative(side);
                if (!seen.contains(next) && isPanel(level, next, facing)) {
                    seen.add(next);
                    queue.add(next);
                }
            }
        }
        return seen;
    }

    // A rectangle of a group, in screen coordinates from the group's origin.
    record Rect(int u, int v, int w, int h) {
        boolean contains(int cu, int cv) {
            return cu >= u && cu < u + w && cv >= v && cv < v + h;
        }
    }

    private static void split(ServerLevel level, Set<BlockPos> group, Direction facing) {
        Direction right = right(facing);
        // Coordinates relative to the first panel.
        BlockPos origin = group.iterator().next();
        Map<Long, BlockPos> cells = new HashMap<>();
        for (BlockPos pos : group) {
            int u = (pos.getX() - origin.getX()) * right.getStepX() + (pos.getZ() - origin.getZ()) * right.getStepZ();
            int v = pos.getY() - origin.getY();
            cells.put(key(u, v), pos);
        }
        // The screens there now, and their content, before anything changes.
        Map<BlockPos, DisplayPanelBlockEntity> oldMasters = new HashMap<>();
        for (BlockPos pos : group) {
            if (level.getBlockEntity(pos) instanceof DisplayPanelBlockEntity panel && panel.isMaster()) {
                oldMasters.put(pos, panel);
            }
        }
        Map<BlockPos, DisplayPanelBlockEntity.Content> contents = new HashMap<>();
        oldMasters.forEach((pos, panel) -> contents.put(pos, panel.content()));
        int maxW = Math.max(1, Config.DISPLAY_MAX_WIDTH.getAsInt()), maxH = Math.max(1, Config.DISPLAY_MAX_HEIGHT.getAsInt());
        Set<Long> left = new HashSet<>(cells.keySet());
        while (!left.isEmpty()) {
            Rect best = largest(left, maxW, maxH);
            for (int du = 0; du < best.w(); du++) {
                for (int dv = 0; dv < best.h(); dv++) {
                    left.remove(key(best.u() + du, best.v() + dv));
                }
            }
            apply(level, cells, best, facing, contents);
        }
    }

    private static long key(int u, int v) {
        return ((long) u << 32) ^ (v & 0xFFFFFFFFL);
    }

    // The largest rectangle of the cells left (ties: the top-most, then the left-most).
    private static Rect largest(Set<Long> left, int maxW, int maxH) {
        Rect best = null;
        for (long cell : left) {
            int u = (int) (cell >> 32), v = (int) cell;
            // (u, v) as the top-left corner: widths to the right, heights down.
            int width = 0;
            while (width < maxW && left.contains(key(u + width, v))) {
                width++;
            }
            int limit = width;
            for (int h = 1; h <= maxH && left.contains(key(u, v - h + 1)); h++) {
                int row = 0;
                while (row < limit && left.contains(key(u + row, v - h + 1))) {
                    row++;
                }
                limit = Math.min(limit, row);
                if (limit == 0) {
                    break;
                }
                Rect rect = new Rect(u, v - h + 1, limit, h);
                if (best == null || better(rect, best)) {
                    best = rect;
                }
            }
        }
        return best;
    }

    private static boolean better(Rect a, Rect b) {
        int areaA = a.w() * a.h(), areaB = b.w() * b.h();
        if (areaA != areaB) {
            return areaA > areaB;
        }
        int topA = a.v() + a.h(), topB = b.v() + b.h();
        return topA != topB ? topA > topB : a.u() < b.u();
    }

    // Sets up one screen: its blocks' edges and LED, the master's size and content.
    private static void apply(ServerLevel level, Map<Long, BlockPos> cells, Rect rect, Direction facing,
            Map<BlockPos, DisplayPanelBlockEntity.Content> contents) {
        BlockPos master = cells.get(key(rect.u(), rect.v()));
        DisplayPanelBlockEntity.Content content = null;
        int contentArea = -1;
        for (Map.Entry<BlockPos, DisplayPanelBlockEntity.Content> old : contents.entrySet()) {
            Long at = null;
            for (Map.Entry<Long, BlockPos> cell : cells.entrySet()) {
                if (cell.getValue().equals(old.getKey())) {
                    at = cell.getKey();
                }
            }
            if (at == null || !rect.contains((int) (at >> 32), (int) (long) at)) {
                continue;
            }
            DisplayPanelBlockEntity.Content candidate = old.getValue();
            int area = candidate.width() == rect.w() && candidate.height() == rect.h() ? Integer.MAX_VALUE : candidate.width() * candidate.height();
            if (area > contentArea) {
                content = candidate;
                contentArea = area;
            }
        }
        List<BlockPos> members = new ArrayList<>();
        for (int du = 0; du < rect.w(); du++) {
            for (int dv = 0; dv < rect.h(); dv++) {
                BlockPos pos = cells.get(key(rect.u() + du, rect.v() + dv));
                members.add(pos);
                BlockState state = level.getBlockState(pos);
                BlockState shown = state.setValue(DisplayPanelBlock.LEFT, du > 0).setValue(DisplayPanelBlock.RIGHT, du < rect.w() - 1)
                        .setValue(DisplayPanelBlock.BOTTOM, dv > 0).setValue(DisplayPanelBlock.TOP, dv < rect.h() - 1)
                        .setValue(DisplayPanelBlock.LED, du == rect.w() - 1 && dv == 0);
                if (shown != state) {
                    level.setBlock(pos, shown, Block.UPDATE_CLIENTS);
                }
                if (level.getBlockEntity(pos) instanceof DisplayPanelBlockEntity panel) {
                    panel.joinScreen(master);
                }
            }
        }
        if (level.getBlockEntity(master) instanceof DisplayPanelBlockEntity panel) {
            panel.becomeMaster(rect.w(), rect.h(), content);
        }
    }
}
