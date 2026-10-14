/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.optical;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.encodedlogistics.block.NetworkBridgeBlock;
import net.zagdrath.encodedlogistics.block.OpticalAmplifierBlock;
import net.zagdrath.encodedlogistics.block.OpticalMultiplexerBlock;
import net.zagdrath.encodedlogistics.block.ServerRackBlock;
import net.zagdrath.encodedlogistics.block.cable.CableTier;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.NetworkBridgeBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.OpticalAmplifierBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.device.OpticalAmplifierDevice;

// An optical line, followed through the world: from an Optical Multiplexer's line port (or a Server Rack's connection
// point) along Fiber Cable - and only Fiber Cable - to the other end. On the way it passes through Optical Amplifier
// blocks (along their axis), a Network Bridge pair (a protect path's wireless hop: the light is regenerated there), and
// Server Racks that hold an online rack Optical Amplifier (patched in: in at one connection point, out at another). It
// ends at a multiplexer's line port or a rack the caller wants (Wanted); the shortest way there is the line.
//
// Each stretch of fibre between two points where the light is regenerated (a powered amplifier, a bridge pair, a rack
// amplifier) is a span; the line's loss is its longest span's (SpanLoss.lost).
public final class FiberTrace {
    private static final int MAX_BLOCKS = 32_768;

    public enum End {
        MUX, RACK
    }

    // The ends a trace is after: a multiplexer block (its position) or a rack (its master).
    public interface Wanted {
        boolean mux(BlockPos pos);

        boolean rack(BlockPos master);
    }

    // An amplifier on the line, with the spans before and after it.
    public record Amplifier(BlockPos pos, int before, int after) {}

    // The line found: where it ends, its length in blocks, its longest span, the amplifiers on it (block ones), whether
    // it crosses a bridge pair, the start (a rack connection point's ordinal; 0 from a multiplexer's port) and its last
    // fibre (two lines into the same end by different fibres are separate paths).
    public record Result(End end, BlockPos at, int length, int span, List<Amplifier> amplifiers, boolean bridged, int start, BlockPos last) {
        public int lost() {
            return SpanLoss.lost(span);
        }

        public int lanes() {
            return SpanLoss.lanes(span, SpanLoss.fiberLanes());
        }

        public int quality() {
            return SpanLoss.quality(lost());
        }
    }

    private FiberTrace() {}

    // A Fiber Cable at pos joined toward side (the side of it facing whatever is asking).
    public static boolean fiberToward(Level level, BlockPos pos, Direction side) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof NetworkCableBlock cable && cable.getTier() == CableTier.FIBER
                && NetworkCableBlock.connection(state, side).connected();
    }

    private static boolean fiber(BlockState state) {
        return state.getBlock() instanceof NetworkCableBlock cable && cable.getTier() == CableTier.FIBER;
    }

    // From a multiplexer block's line port (the fibre in front of its FACING side).
    public static @Nullable Result fromPort(ServerLevel level, BlockPos mux, Direction port, Wanted wanted) {
        BlockPos start = mux.relative(port);
        if (!fiberToward(level, start, port.getOpposite())) {
            return null;
        }
        return trace(level, start, Set.of(mux), null, wanted, 0);
    }

    // From one of a rack's connection points (the fibre there, joined to the rack).
    public static @Nullable Result fromRack(ServerLevel level, RackBlockEntity rack, RackGeometry.Point point, Wanted wanted) {
        BlockPos start = point.outside(rack.getBlockPos(), rack.facing());
        if (!fiberToward(level, start, point.side(rack.facing()).getOpposite())) {
            return null;
        }
        return trace(level, start, Set.of(), rack.getBlockPos(), wanted, point.ordinal());
    }

    // Whether a rack has a Fiber Cable at a connection point.
    public static boolean fiberAt(ServerLevel level, RackBlockEntity rack, RackGeometry.Point point) {
        return fiberToward(level, point.outside(rack.getBlockPos(), rack.facing()), point.side(rack.facing()).getOpposite());
    }

    // How the walk got to a block: from where, and whether the light was regenerated between (a bridge or rack hop).
    private record Step(@Nullable BlockPos from, boolean regenerated, boolean bridged) {}

    private static @Nullable Result trace(ServerLevel level, BlockPos start, Set<BlockPos> sources, @Nullable BlockPos sourceRack, Wanted wanted,
            int startPoint) {
        Map<BlockPos, Step> steps = new HashMap<>();
        // An amplifier's way through: the direction the light travels in it.
        Map<BlockPos, Direction> through = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        steps.put(start, new Step(null, false, false));
        queue.add(start);
        Set<BlockPos> racksSeen = new HashSet<>();
        if (sourceRack != null) {
            racksSeen.add(sourceRack);
        }
        while (!queue.isEmpty() && steps.size() < MAX_BLOCKS) {
            BlockPos at = queue.poll();
            Direction travel = through.get(at);
            if (travel != null) {
                // Through an amplifier: out of its other end.
                BlockPos next = at.relative(travel);
                if (!steps.containsKey(next) && fiberToward(level, next, travel.getOpposite())) {
                    steps.put(next, new Step(at, false, false));
                    queue.add(next);
                }
                continue;
            }
            BlockState state = level.getBlockState(at);
            for (Direction side : Direction.values()) {
                if (!NetworkCableBlock.connection(state, side).connected()) {
                    continue;
                }
                BlockPos next = at.relative(side);
                if (steps.containsKey(next) || sources.contains(next) || !level.isLoaded(next)) {
                    continue;
                }
                BlockState there = level.getBlockState(next);
                if (fiber(there)) {
                    if (NetworkCableBlock.connection(there, side.getOpposite()).connected()) {
                        steps.put(next, new Step(at, false, false));
                        queue.add(next);
                    }
                } else if (there.getBlock() instanceof OpticalAmplifierBlock) {
                    if (there.getValue(OpticalAmplifierBlock.FACING).getAxis() == side.getAxis()) {
                        steps.put(next, new Step(at, false, false));
                        through.put(next, side);
                        queue.add(next);
                    }
                } else if (there.getBlock() instanceof OpticalMultiplexerBlock) {
                    // A line port facing this way.
                    if (there.getValue(OpticalMultiplexerBlock.FACING) == side.getOpposite() && wanted.mux(next)) {
                        return result(level, End.MUX, next, at, steps, startPoint);
                    }
                } else if (there.getBlock() instanceof ServerRackBlock) {
                    RackBlockEntity rack = ServerRackBlock.rack(level, next, there);
                    if (rack == null || !racksSeen.add(rack.getBlockPos())) {
                        continue;
                    }
                    if (wanted.rack(rack.getBlockPos())) {
                        return result(level, End.RACK, rack.getBlockPos(), at, steps, startPoint);
                    }
                    if (amplifies(rack)) {
                        // Patched through its amplifier: out at its other connection points.
                        for (RackGeometry.Point point : RackGeometry.Point.values()) {
                            BlockPos out = point.outside(rack.getBlockPos(), rack.facing());
                            if (!steps.containsKey(out) && fiberToward(level, out, point.side(rack.facing()).getOpposite())) {
                                steps.put(out, new Step(at, true, false));
                                queue.add(out);
                            }
                        }
                    }
                } else if (there.getBlock() instanceof NetworkBridgeBlock && level.getBlockEntity(next) instanceof NetworkBridgeBlockEntity bridge) {
                    GlobalPos partner = bridge.partner();
                    if (partner == null || !partner.dimension().equals(level.dimension()) || !level.isLoaded(partner.pos())
                            || !(level.getBlockEntity(partner.pos()) instanceof NetworkBridgeBlockEntity other) || !bridge.self().equals(other.partner())) {
                        continue;
                    }
                    for (Direction out : Direction.values()) {
                        BlockPos beyond = partner.pos().relative(out);
                        if (!steps.containsKey(beyond) && fiberToward(level, beyond, out.getOpposite())) {
                            steps.put(beyond, new Step(at, true, true));
                            queue.add(beyond);
                        }
                    }
                }
            }
        }
        return null;
    }

    // A rack whose online rack Optical Amplifier patches lines through it.
    private static boolean amplifies(RackBlockEntity rack) {
        for (RackDevice device : rack.devices()) {
            if (device instanceof OpticalAmplifierDevice && device.isOnline()) {
                return true;
            }
        }
        return false;
    }

    // The line back from the last fibre (at) to the start, measured.
    private static Result result(ServerLevel level, End end, BlockPos endAt, BlockPos at, Map<BlockPos, Step> steps, int startPoint) {
        List<BlockPos> path = new ArrayList<>();
        List<Boolean> regenerated = new ArrayList<>();
        boolean bridged = false;
        for (BlockPos pos = at; pos != null; ) {
            Step step = steps.get(pos);
            path.add(pos);
            regenerated.add(step.regenerated());
            bridged |= step.bridged();
            pos = step.from();
        }
        // From the start out.
        Collections.reverse(path);
        Collections.reverse(regenerated);
        List<Integer> spans = new ArrayList<>();
        List<BlockPos> amplifierAt = new ArrayList<>();
        List<Integer> amplifierSpan = new ArrayList<>();
        int current = 0;
        for (int i = 0; i < path.size(); i++) {
            // A hop before this block (shifted: regenerated marks the block reached by the hop).
            if (regenerated.get(i) && current > 0) {
                spans.add(current);
                current = 0;
            }
            BlockState state = level.getBlockState(path.get(i));
            if (state.getBlock() instanceof OpticalAmplifierBlock) {
                amplifierAt.add(path.get(i));
                amplifierSpan.add(spans.size());
                if (state.getValue(OpticalAmplifierBlock.STATE) == OpticalState.ON) {
                    spans.add(current);
                    current = 0;
                    continue;
                }
            }
            current++;
        }
        spans.add(current);
        int longest = spans.stream().mapToInt(Integer::intValue).max().orElse(0);
        List<Amplifier> amplifiers = new ArrayList<>();
        for (int i = 0; i < amplifierAt.size(); i++) {
            int index = amplifierSpan.get(i);
            int before = spans.get(index);
            int after = index + 1 < spans.size() ? spans.get(index + 1) : before;
            amplifiers.add(new Amplifier(amplifierAt.get(i), before, after));
        }
        return new Result(end, endAt, path.size(), longest, amplifiers, bridged, startPoint, at);
    }

    // Powers the block amplifiers on a line (its end online), so they amplify.
    public static void powerAmplifiers(ServerLevel level, Result line) {
        for (Amplifier amplifier : line.amplifiers()) {
            OpticalAmplifierBlockEntity.linePowered(level, amplifier.pos(), amplifier.before(), amplifier.after());
        }
    }
}
