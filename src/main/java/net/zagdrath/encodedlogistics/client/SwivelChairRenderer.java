/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ModelEvent;
import net.neoforged.neoforge.client.model.standalone.SimpleUnbakedStandaloneModel;
import net.neoforged.neoforge.client.model.standalone.StandaloneModelKey;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.SwivelChairBlockEntity;
import net.zagdrath.encodedlogistics.entity.SeatEntity;

// The Swivel Chair's seat and back (models/block/swivel_chair/seat.json), turned about the column (8, y, 8) to the
// chair's yaw - its sitter's, smoothly, while someone sits - and its fabric tinted with the chair's dye.
public class SwivelChairRenderer implements BlockEntityRenderer<SwivelChairBlockEntity, SwivelChairRenderer.State> {
    public static final Identifier SEAT = EncodedLogistics.id("block/swivel_chair/seat");
    private static final StandaloneModelKey<BlockStateModelPart> KEY = new StandaloneModelKey<>(SEAT::toString);

    public static class State extends BlockEntityRenderState {
        float yaw;
        int color = -1;
    }

    public SwivelChairRenderer(BlockEntityRendererProvider.Context context) {}

    public static void register(ModelEvent.RegisterStandalone event) {
        event.register(KEY, SimpleUnbakedStandaloneModel.simpleModelWrapper(SEAT));
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(SwivelChairBlockEntity chair, State state, float partialTicks, Vec3 cameraPosition,
            ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(chair, state, partialTicks, cameraPosition, breakProgress);
        state.color = chair.color();
        state.yaw = chair.yaw();
        if (chair.getLevel() != null) {
            for (SeatEntity seat : chair.getLevel().getEntitiesOfClass(SeatEntity.class, new AABB(chair.getBlockPos()))) {
                Entity rider = seat.getFirstPassenger();
                if (rider != null) {
                    state.yaw = Mth.rotLerp(partialTicks, rider.yRotO, rider.getYRot());
                }
            }
        }
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        BlockStateModelPart seat = Minecraft.getInstance().getModelManager().getStandaloneModel(KEY);
        if (seat == null) {
            return;
        }
        poseStack.pushPose();
        // The seat's front faces north (yaw 180); turn it to face the yaw.
        poseStack.translate(0.5F, 0, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(180 - state.yaw));
        poseStack.translate(-0.5F, 0, -0.5F);
        collector.submitBlockModel(poseStack, Sheets.cutoutBlockItemSheet(), List.of(seat), new int[] { state.color }, state.lightCoords,
                OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
