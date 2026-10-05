/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import net.zagdrath.encodedlogistics.client.rack.WirelessHud;
import net.zagdrath.encodedlogistics.display.SmallWirelessBridgeBlock;
import net.zagdrath.encodedlogistics.net.MachineBridgesPayload;
import net.zagdrath.encodedlogistics.registry.ModBlocks;

// The Small Wireless Bridges on machines (MachineBridgesPayload): each drawn as the Small Wireless Bridge block's model
// for its LED, in the space in front of the face it's on, facing the machine, lit as that space is. Bridges further than
// RANGE blocks from the camera aren't drawn. The one under the crosshair gets a block outline.
public final class MachineBridgeRenderer {
    private static final double RANGE = 96;
    private static final int OUTLINE_COLOR = 0x66000000;
    private static final float OUTLINE_WIDTH = 2.0F;
    private static final int[] NO_TINTS = new int[0];
    private static final RandomSource RANDOM = RandomSource.create();

    private MachineBridgeRenderer() {}

    public static void render(SubmitCustomGeometryEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || MachineBridgesPayload.shown().isEmpty()) {
            return;
        }
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        PoseStack poseStack = event.getPoseStack();
        // The one under the crosshair is outlined as a block would be (WirelessHud finds it).
        AABB hovered = WirelessHud.bridgeBox();
        if (hovered != null && !minecraft.gui.hud.isHidden()) {
            poseStack.pushPose();
            poseStack.translate(hovered.minX - camera.x, hovered.minY - camera.y, hovered.minZ - camera.z);
            event.getSubmitNodeCollector().submitShapeOutline(poseStack, Shapes.create(0, 0, 0, hovered.getXsize(), hovered.getYsize(), hovered.getZsize()),
                    RenderTypes.linesTranslucent(), OUTLINE_COLOR, OUTLINE_WIDTH, false);
            poseStack.popPose();
        }
        for (MachineBridgesPayload.Entry entry : MachineBridgesPayload.shown().values()) {
            BlockPos at = entry.pos().relative(entry.face());
            if (camera.distanceToSqr(Vec3.atCenterOf(at)) > RANGE * RANGE || !minecraft.level.isLoaded(at)) {
                continue;
            }
            BlockState state = ModBlocks.SMALL_WIRELESS_BRIDGE.get().defaultBlockState().setValue(SmallWirelessBridgeBlock.FACING, entry.face().getOpposite())
                    .setValue(SmallWirelessBridgeBlock.STATE, entry.led());
            List<BlockStateModelPart> parts = new ArrayList<>();
            RANDOM.setSeed(42L);
            minecraft.getModelManager().getBlockStateModelSet().get(state).collectParts(minecraft.level, at, state, RANDOM, parts);
            poseStack.pushPose();
            poseStack.translate(at.getX() - camera.x, at.getY() - camera.y, at.getZ() - camera.z);
            event.getSubmitNodeCollector().submitBlockModel(poseStack, Sheets.cutoutBlockItemSheet(), parts, NO_TINTS,
                    LightCoordsUtil.getLightCoords(minecraft.level, at), OverlayTexture.NO_OVERLAY, 0);
            poseStack.popPose();
        }
    }
}
