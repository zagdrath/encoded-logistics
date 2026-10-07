/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.ShapeRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.world.phys.shapes.VoxelShape;

// 26.1 has no submitShapeOutline: outlines are submitted as custom geometry, or drawn straight into a buffer by block
// outline renderers.
public final class ShapeOutlines {
    private ShapeOutlines() {}

    public static void submit(SubmitNodeCollector collector, PoseStack poseStack, VoxelShape shape, RenderType renderType, int color, float width) {
        collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
            PoseStack at = new PoseStack();
            at.last().set(pose);
            ShapeRenderer.renderShape(at, buffer, shape, 0, 0, 0, color, width);
        });
    }

    public static void draw(PoseStack poseStack, VertexConsumer buffer, VoxelShape shape, int color, float width) {
        ShapeRenderer.renderShape(poseStack, buffer, shape, 0, 0, 0, color, width);
    }
}
