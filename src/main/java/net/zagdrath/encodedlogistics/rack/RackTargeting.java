/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;

// Which unit of a rack a player is looking at, through an open door. The rack's blocks are full cubes, so the crosshair
// hits their outer face, but the devices sit a few pixels further in (their fronts at z 1.75, their backs at z 29.25):
// looking at the rack from above or below, the outer face is hit higher or lower than the device under the crosshair.
// So the line of sight is followed in: the nearest device it meets, or else the unit where it crosses the plane the
// devices' faces are in. Used by the popup and outline (client) and by installing by hand and the Link Card (server).
public final class RackTargeting {
    // How far along the line of sight to look.
    private static final double REACH = 8;

    public record Target(int u, @Nullable RackDevice device) {}

    private RackTargeting() {}

    // What a player's line of sight (from eye along look) picks on the side of the rack the crosshair hit, or null when
    // that side's door is closed or it misses the 42U space.
    public static @Nullable Target pick(RackBlockEntity rack, Direction hitSide, Vec3 eye, Vec3 look) {
        Direction facing = rack.facing();
        RackGeometry.Face face = RackGeometry.face(hitSide, facing);
        if (face == RackGeometry.Face.FRONT ? !rack.isFrontOpen() : face != RackGeometry.Face.REAR || !rack.isRearOpen()) {
            return null;
        }
        BlockPos master = rack.getBlockPos();
        Vec3 to = eye.add(look.normalize().scale(REACH));
        RackDevice nearest = null;
        double best = Double.MAX_VALUE;
        for (RackDevice device : rack.devices()) {
            AABB box = RackGeometry.toWorld(RackGeometry.deviceBox(device.u(), device.size()), master, facing);
            Optional<Vec3> hit = box.clip(eye, to);
            if (hit.isPresent() && hit.get().distanceToSqr(eye) < best) {
                best = hit.get().distanceToSqr(eye);
                nearest = device;
            }
        }
        if (nearest != null) {
            return new Target(nearest.u(), nearest);
        }
        // No device: the unit where the line crosses the plane of the devices' faces on that side.
        Vec3 from = RackGeometry.toLocal(eye, master, facing), end = RackGeometry.toLocal(to, master, facing);
        double plane = face == RackGeometry.Face.FRONT ? RackGeometry.DEVICE_Z0 : RackGeometry.DEVICE_Z1;
        if (Math.abs(end.z - from.z) < 1.0E-9) {
            return null;
        }
        double t = (plane - from.z) / (end.z - from.z);
        if (t < 0 || t > 1) {
            return null;
        }
        double x = from.x + (end.x - from.x) * t, y = from.y + (end.y - from.y) * t;
        int u = RackGeometry.unitAt(y);
        if (u == 0 || x < RackGeometry.DEVICE_X0 || x > RackGeometry.DEVICE_X1) {
            return null;
        }
        return new Target(u, rack.deviceAt(u));
    }
}
