/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;

// Work with Devices' 8=Locate: the device's block picked out for ten seconds - its edges traced in light (in the
// player's dimension).
public final class CrtLocate {
    private static final int TICKS = 200;
    private static @Nullable BlockPos target;
    private static @Nullable String dimension;
    private static int left;

    private CrtLocate() {}

    // "x y z dimension", as the server says where it is.
    static void locate(String where) {
        String[] parts = where.trim().split(" ");
        if (parts.length < 4) {
            return;
        }
        try {
            target = new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            dimension = parts[3];
            left = TICKS;
        } catch (NumberFormatException e) {
            target = null;
        }
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (target == null || left-- <= 0 || minecraft.level == null) {
            target = null;
            return;
        }
        if (!minecraft.level.dimension().identifier().toString().equals(dimension) || left % 4 != 0) {
            return;
        }
        // Along the twelve edges.
        for (int edge = 0; edge < 12; edge++) {
            double t = minecraft.level.getRandom().nextDouble();
            double[] p = switch (edge / 4) {
                case 0 -> new double[] { t, edge % 2, edge / 2 % 2 };
                case 1 -> new double[] { edge % 2, t, edge / 2 % 2 };
                default -> new double[] { edge % 2, edge / 2 % 2, t };
            };
            minecraft.level.addParticle(ParticleTypes.END_ROD, target.getX() + p[0], target.getY() + p[1], target.getZ() + p[2], 0, 0, 0);
        }
    }
}
