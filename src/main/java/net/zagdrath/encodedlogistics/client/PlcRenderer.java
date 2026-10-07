/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

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
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.plc.PlcBlock;
import net.zagdrath.encodedlogistics.plc.PlcBlockEntity;

// A running PLC's six I/O LEDs on its CPU module (docs/plc HANDOFF 1): one per face, lit - flickering amber, 4 frames
// of 3 ticks as the handoff's leds_run did - only while that face's level changed in the last 10 ticks (the server's
// io bits). The model's state_run overlay has the RUN LED; these go on the CPU face just in front of it, texels
// (1..3, 5) and (1..3, 6) of its 5 x 10 front, in Direction order: down, up, north / south, west, east.
public class PlcRenderer implements BlockEntityRenderer<PlcBlockEntity, PlcRenderer.State> {
    private static final Identifier LED = EncodedLogistics.id("textures/block/plc/io_led.png");
    // The CPU front in the model (facing north): x 9.5-14.5 (texel u from 14.5 down), y 13 down to 3, at z 10.
    private static final float RIGHT = 14.5F, TOP = 13, Z = 9.985F / 16;
    private static final int[][] TEXELS = { { 1, 5 }, { 2, 5 }, { 3, 5 }, { 1, 6 }, { 2, 6 }, { 3, 6 } };

    public static class State extends BlockEntityRenderState {
        int bits, turns;
        long time;
    }

    public PlcRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(PlcBlockEntity plc, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(plc, state, partialTicks, cameraPosition, breakProgress);
        boolean running = plc.getBlockState().getBlock() instanceof PlcBlock && plc.getBlockState().getValue(PlcBlock.STATE) == PlcBlock.Mode.RUN;
        state.bits = running ? plc.ioBits() : 0;
        state.turns = plc.getBlockState().getBlock() instanceof PlcBlock ? plc.getBlockState().getValue(PlcBlock.FACING).get2DDataValue() : 2;
        state.time = plc.getLevel() != null ? plc.getLevel().getGameTime() : 0;
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.bits == 0) {
            return;
        }
        int frame = (int) (state.time / 3 % 4);
        poseStack.pushPose();
        poseStack.translate(0.5F, 0, 0.5F);
        // 2D data value: south 0, west 1, north 2, east 3; the model's y rotation: north 0, east 90, south 180, west 270.
        poseStack.mulPose(Axis.YP.rotationDegrees(-90 * ((state.turns + 2) % 4)));
        poseStack.translate(-0.5F, 0, -0.5F);
        collector.submitCustomGeometry(poseStack, RenderTypes.text(LED), (pose, buffer) -> {
            for (int i = 0; i < TEXELS.length; i++) {
                if ((state.bits & 1 << i) == 0 || (i * 3 + frame) % 4 >= 2) {
                    continue;
                }
                float left = (RIGHT - TEXELS[i][0]) / 16, right = (RIGHT - TEXELS[i][0] - 1) / 16;
                float top = (TOP - TEXELS[i][1]) / 16, bottom = (TOP - TEXELS[i][1] - 1) / 16;
                vertex(buffer, pose, left, top, 0, 0);
                vertex(buffer, pose, left, bottom, 0, 1);
                vertex(buffer, pose, right, bottom, 1, 1);
                vertex(buffer, pose, right, top, 1, 0);
            }
        });
        poseStack.popPose();
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float u, float v) {
        buffer.addVertex(pose, x, y, Z).setColor(-1).setUv(u, v).setLight(LightCoordsUtil.FULL_BRIGHT);
    }
}
