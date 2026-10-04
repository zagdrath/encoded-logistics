/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.ArrayList;
import java.util.List;

// A Tape Library's picker (animation_specs.json -> tape_library), in the front's texel space: the head parks at (62, 4)
// by the vertical guide; tape slot i sits at (17 + 4 * (i % 12), 4 + 10 * (i / 12)); the drive chute is at x 66, its
// mouth at the window's bottom. A load fetches a tape from its slot and puts it in a drive: along the rail to the slot's
// column, down, grab (4 ticks), up, to the chute, down, insert (4 ticks), back home. An unload is the same the other way.
// The rail moves 3 texels a tick, the head 4, each leg eased. Server and client both time it from here, so the picker's
// moves take exactly as long as the work they stand for.
public final class TapePicker {
    public static final float HOME_X = 62, HOME_Y = 4, CHUTE_X = 66;
    public static final int GRAB_TICKS = 4;
    private static final float RAIL_SPEED = 3, HEAD_SPEED = 4;

    public enum Kind {
        MOVE, GRAB, INSERT
    }

    public record Leg(float x0, float y0, float x1, float y1, int ticks, Kind kind) {}

    // Where the head is and whether it holds the tape (and so whether the tape shows in its slot).
    public record Pose(float x, float y, boolean holding, boolean inSlot) {}

    private TapePicker() {}

    public static float slotX(int slot) {
        return 17 + 4 * (slot % 12);
    }

    public static float slotY(int slot) {
        return 4 + 10 * (slot / 12);
    }

    // The head's y at the drive chute: the bottom of the window, under the last row.
    public static float chuteY(int rows) {
        return 4 + 10 * rows + 1;
    }

    public static List<Leg> path(int rows, int slot, boolean load) {
        float tx = slotX(slot) - 2, ty = slotY(slot), cy = chuteY(rows);
        List<Leg> legs = new ArrayList<>();
        if (load) {
            move(legs, HOME_X, HOME_Y, tx, HOME_Y);
            move(legs, tx, HOME_Y, tx, ty);
            legs.add(new Leg(tx, ty, tx, ty, GRAB_TICKS, Kind.GRAB));
            move(legs, tx, ty, tx, HOME_Y);
            move(legs, tx, HOME_Y, CHUTE_X, HOME_Y);
            move(legs, CHUTE_X, HOME_Y, CHUTE_X, cy);
            legs.add(new Leg(CHUTE_X, cy, CHUTE_X, cy, GRAB_TICKS, Kind.INSERT));
            move(legs, CHUTE_X, cy, CHUTE_X, HOME_Y);
            move(legs, CHUTE_X, HOME_Y, HOME_X, HOME_Y);
        } else {
            move(legs, HOME_X, HOME_Y, CHUTE_X, HOME_Y);
            move(legs, CHUTE_X, HOME_Y, CHUTE_X, cy);
            legs.add(new Leg(CHUTE_X, cy, CHUTE_X, cy, GRAB_TICKS, Kind.GRAB));
            move(legs, CHUTE_X, cy, CHUTE_X, HOME_Y);
            move(legs, CHUTE_X, HOME_Y, tx, HOME_Y);
            move(legs, tx, HOME_Y, tx, ty);
            legs.add(new Leg(tx, ty, tx, ty, GRAB_TICKS, Kind.INSERT));
            move(legs, tx, ty, tx, HOME_Y);
            move(legs, tx, HOME_Y, HOME_X, HOME_Y);
        }
        return legs;
    }

    private static void move(List<Leg> legs, float x0, float y0, float x1, float y1) {
        float distance = x0 != x1 ? Math.abs(x1 - x0) / RAIL_SPEED : Math.abs(y1 - y0) / HEAD_SPEED;
        if (distance > 0) {
            legs.add(new Leg(x0, y0, x1, y1, Math.max(1, (int) Math.ceil(distance)), Kind.MOVE));
        }
    }

    public static int ticks(int rows, int slot, boolean load) {
        int total = 0;
        for (Leg leg : path(rows, slot, load)) {
            total += leg.ticks();
        }
        return total;
    }

    // The ticks at which legs start, for their sounds.
    public static List<Integer> legStarts(int rows, int slot, boolean load) {
        List<Integer> starts = new ArrayList<>();
        int at = 0;
        for (Leg leg : path(rows, slot, load)) {
            starts.add(at);
            at += leg.ticks();
        }
        return starts;
    }

    // The pose t ticks in (past the end: home, done). The tape changes hands halfway through a grab or insert: before
    // the grab it's where it started (its slot for a load, the drive for an unload), until the insert on the head, then
    // where it's going.
    public static Pose at(int rows, int slot, boolean load, float t) {
        float at = 0, x = HOME_X, y = HOME_Y;
        int stage = 2;
        for (Leg leg : path(rows, slot, load)) {
            if (t < at + leg.ticks()) {
                float f = RackGeometry.ease((t - at) / leg.ticks());
                x = leg.x0() + (leg.x1() - leg.x0()) * f;
                y = leg.y0() + (leg.y1() - leg.y0()) * f;
                if (leg.kind() == Kind.GRAB) {
                    stage = f < 0.5F ? 0 : 1;
                } else if (leg.kind() == Kind.INSERT) {
                    stage = f < 0.5F ? 1 : 2;
                } else {
                    stage = stageBefore(rows, slot, load, at);
                }
                break;
            }
            at += leg.ticks();
        }
        return new Pose(x, y, stage == 1, load ? stage == 0 : stage == 2);
    }

    // The stage (0 before the grab, 1 holding, 2 after the insert) at a leg boundary.
    private static int stageBefore(int rows, int slot, boolean load, float t) {
        float at = 0;
        int stage = 0;
        for (Leg leg : path(rows, slot, load)) {
            if (at >= t) {
                break;
            }
            at += leg.ticks();
            if (leg.kind() == Kind.GRAB) {
                stage = 1;
            } else if (leg.kind() == Kind.INSERT) {
                stage = 2;
            }
        }
        return stage;
    }
}
