/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.display;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.display.DisplayPanelBlock;
import net.zagdrath.encodedlogistics.display.DisplayPanelBlockEntity;
import net.zagdrath.encodedlogistics.display.DisplayScreens;

// A screen's canvas (HANDOFF 2), on its master: one full-bright quad over the glass of every panel of the screen,
// CANVAS_Z in front of it, its texture DisplayCanvases'. In the master's local space (facing north: the screen toward
// -z, its glass at z 8/16), turned by the facing as the block model is; the screen runs to the right (as seen from the
// front: -x) and up from the master.
public class DisplayRenderer implements BlockEntityRenderer<DisplayPanelBlockEntity, DisplayRenderer.State> {
    private static final float GLASS_Z = 8 / 16F, CANVAS_Z = GLASS_Z - 0.004F;

    public static class State extends BlockEntityRenderState {
        @Nullable Identifier texture;
        int width, height, turns;
    }

    public DisplayRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(DisplayPanelBlockEntity panel, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(panel, state, partialTicks, cameraPosition, breakProgress);
        state.texture = panel.isMaster() ? DisplayCanvases.texture(panel) : null;
        state.width = panel.width();
        state.height = panel.height();
        state.turns = panel.getBlockState().getValue(DisplayPanelBlock.FACING).get2DDataValue();
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.texture == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5F, 0, 0.5F);
        // 2D data value: south 0, west 1, north 2, east 3; the model's y rotation: north 0, east 90, south 180, west 270.
        poseStack.mulPose(Axis.YP.rotationDegrees(-90 * ((state.turns + 2) % 4)));
        poseStack.translate(-0.5F, 0, -0.5F);
        float left = 1, right = 1 - state.width, top = state.height;
        collector.submitCustomGeometry(poseStack, RenderTypes.text(state.texture), (pose, buffer) -> {
            vertex(buffer, pose, left, top, 0, 0);
            vertex(buffer, pose, left, 0, 0, 1);
            vertex(buffer, pose, right, 0, 1, 1);
            vertex(buffer, pose, right, top, 1, 0);
        });
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float u, float v) {
        buffer.addVertex(pose, x, y, CANVAS_Z).setColor(-1).setUv(u, v).setLight(LightCoordsUtil.FULL_BRIGHT);
    }

    // The whole screen, so it isn't culled while any of it is in view.
    @Override
    public AABB getRenderBoundingBox(DisplayPanelBlockEntity panel) {
        BlockPos master = panel.getBlockPos();
        Direction facing = panel.getBlockState().getValue(DisplayPanelBlock.FACING);
        BlockPos far = DisplayScreens.at(master, facing, panel.width() - 1, panel.height() - 1);
        return new AABB(master).minmax(new AABB(far)).inflate(0.5);
    }
}
