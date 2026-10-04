/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

// Work with Devices' 8=Locate: a cyan box blinking around the device for ten seconds - a rack device's own units, or
// the device's block (in the player's dimension).
public final class CrtLocate {
    private static final int TICKS = 200, BLINK = 10;
    private static final int COLOR = 0xFF20F0FF;
    private static final float WIDTH = 3.0F;
    // Just outside the device, so its own faces don't hide the lines.
    private static final double INFLATE = 0.02;
    private static @Nullable AABB target;
    private static @Nullable String dimension;
    private static int left;

    private CrtLocate() {}

    // "minX minY minZ maxX maxY maxZ dimension", as the server says where it is.
    static void locate(String where) {
        String[] parts = where.trim().split(" ");
        if (parts.length < 7) {
            return;
        }
        try {
            double[] v = new double[6];
            for (int i = 0; i < 6; i++) {
                v[i] = Double.parseDouble(parts[i]);
            }
            target = new AABB(v[0], v[1], v[2], v[3], v[4], v[5]).inflate(INFLATE);
            dimension = parts[6].toLowerCase(Locale.ROOT);
            left = TICKS;
        } catch (NumberFormatException e) {
            target = null;
        }
    }

    public static void tick() {
        if (target != null && --left <= 0) {
            target = null;
        }
    }

    // On for BLINK ticks, off for half that.
    public static void render(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        AABB box = target;
        if (box == null || minecraft.level == null || !minecraft.level.dimension().identifier().toString().equals(dimension)
                || left % (BLINK + BLINK / 2) >= BLINK) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(box.minX - camera.x, box.minY - camera.y, box.minZ - camera.z);
        event.getSubmitNodeCollector().submitShapeOutline(poseStack, Shapes.create(0, 0, 0, box.getXsize(), box.getYsize(), box.getZsize()),
                RenderTypes.linesTranslucent(), COLOR, WIDTH, false);
        poseStack.popPose();
    }
}
