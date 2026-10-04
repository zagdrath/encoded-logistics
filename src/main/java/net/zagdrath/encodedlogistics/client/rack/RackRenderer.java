/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.rack;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackGeometry;

// Draws what moves or changes in a Server Rack, on its master: the three door leaves, swung by how open they are
// (door_animation.json: the front leaf -110 degrees about (14.5, 0.5), the rear leaves +110 / -110 about (14.5, 31.1)
// and (1.5, 31.1)), and each device's model for its state at its unit, with its render extras (RackClientDevices).
// Everything is in the master's local space turned by the rack's facing, as the frame model is. With both doors shut
// the devices aren't drawn beyond rackCullDistance.
public class RackRenderer implements BlockEntityRenderer<RackBlockEntity, RackRenderer.State> {
    private static final int[] NO_TINTS = new int[0], NONE = new int[0];
    private static final float OPEN_DEGREES = 110;

    public static class State extends BlockEntityRenderState {
        int turns;
        float front, rear;
        final List<DeviceDraw> devices = new ArrayList<>();
    }

    record DeviceDraw(RackDeviceType type, int u, RackDeviceInfo.Status status, int[] extra) {}

    public RackRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(RackBlockEntity rack, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(rack, state, partialTicks, cameraPosition, breakProgress);
        state.turns = RackGeometry.turns(rack.facing());
        state.front = rack.frontOpenness(partialTicks);
        state.rear = rack.rearOpenness(partialTicks);
        state.devices.clear();
        double cull = Config.RACK_CULL_DISTANCE.getAsInt();
        boolean shut = state.front <= 0 && state.rear <= 0;
        if (shut && Vec3.atCenterOf(rack.getBlockPos()).distanceToSqr(cameraPosition) > cull * cull) {
            return;
        }
        for (RackDevice device : rack.devices()) {
            RackClientDevices.RenderExtra extra = RackClientDevices.extra(device.type());
            state.devices.add(new DeviceDraw(device.type(), device.u(), device.shownStatus(), extra != null ? extra.capture(device) : NONE));
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        poseStack.pushPose();
        poseStack.translate(0.5F, 0, 0.5F);
        poseStack.rotate(Axis.YP.rotationDegrees(-90 * state.turns));
        poseStack.translate(-0.5F, 0, -0.5F);

        door(poseStack, collector, state, RackModels.DOOR_FRONT, 14.5F, 0.5F, -OPEN_DEGREES * state.front);
        door(poseStack, collector, state, RackModels.DOOR_REAR_LEFT, 14.5F, 31.1F, OPEN_DEGREES * state.rear);
        door(poseStack, collector, state, RackModels.DOOR_REAR_RIGHT, 1.5F, 31.1F, -OPEN_DEGREES * state.rear);

        for (DeviceDraw device : state.devices) {
            BlockStateModelPart model = RackModels.get(device.type().model(device.status()));
            poseStack.pushPose();
            poseStack.translate(0, RackGeometry.unitBottom(device.u()) / 16, 0);
            if (model != null) {
                collector.submitBlockModel(poseStack, Sheets.cutoutBlockItemSheet(), List.of(model), NO_TINTS, state.lightCoords,
                        OverlayTexture.NO_OVERLAY, 0);
            }
            RackClientDevices.RenderExtra extra = RackClientDevices.extra(device.type());
            if (extra != null) {
                extra.submit(device.type(), device.status(), device.extra(), poseStack, collector, state.lightCoords);
            }
            poseStack.popPose();
        }
        poseStack.popPose();
    }

    private static void door(PoseStack poseStack, SubmitNodeCollector collector, State state, Identifier model, float pivotX, float pivotZ,
            float degrees) {
        BlockStateModelPart part = RackModels.get(model);
        if (part == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(pivotX / 16, 0, pivotZ / 16);
        poseStack.rotate(Axis.YP.rotationDegrees(degrees));
        poseStack.translate(-pivotX / 16, 0, -pivotZ / 16);
        collector.submitBlockModel(poseStack, Sheets.cutoutBlockItemSheet(), List.of(part), NO_TINTS, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }

    // The whole rack (and its doors swung open), so it isn't culled while any of it is in view.
    @Override
    public AABB getRenderBoundingBox(RackBlockEntity rack) {
        return rack.bounds().inflate(1);
    }
}
