/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import org.joml.Matrix4f;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.zagdrath.encodedlogistics.block.cable.CableShapes;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.item.CableFacadeItem;
import net.zagdrath.encodedlogistics.registry.ModItems;

// While a Cable Facade is in hand and the crosshair is on a cable, a ghost of the panel shows on the side it would go
// (NetworkCableBlock.facadeSide): see-through, outlined, tinted like the block it copies (white when blank).
public final class FacadePreview {
    private static final int OUTLINE_COLOR = 0xC0FFFFFF;
    private static final float OUTLINE_WIDTH = 2.0F;
    private static final int FILL_ALPHA = 0x50;

    private FacadePreview() {}

    public static void outline(ExtractBlockOutlineRenderStateEvent event) {
        if (!(event.getBlockState().getBlock() instanceof NetworkCableBlock) || !(event.getHitResult() instanceof BlockHitResult hit)) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        ItemStack facade = player == null ? ItemStack.EMPTY : held(player);
        if (facade.isEmpty()) {
            return;
        }
        BlockPos pos = event.getBlockPos();
        Direction side = NetworkCableBlock.facadeSide(event.getLevel(), pos, hit.getLocation(), hit.getDirection());
        if (side == null) {
            return;
        }
        VoxelShape panel = CableShapes.facade(side);
        AABB box = panel.bounds().inflate(0.002);
        BlockState target = CableFacadeItem.target(facade);
        int rgb = target != null ? target.getMapColor(event.getLevel(), pos).col : 0xFFFFFF;
        int fill = FILL_ALPHA << 24 | rgb & 0xFFFFFF;
        event.addCustomRenderer((state, collector, poseStack, levelState) -> {
            Vec3 camera = levelState.cameraRenderState.pos;
            poseStack.pushPose();
            poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
            collector.submitCustomGeometry(poseStack, RenderTypes.debugQuads(), (pose, buffer) -> box(buffer, pose.pose(), box, fill));
            collector.submitShapeOutline(poseStack, Shapes.create(box), RenderTypes.linesTranslucent(), OUTLINE_COLOR, OUTLINE_WIDTH, false);
            poseStack.popPose();
            // The cable's own outline still shows.
            return false;
        });
    }

    private static ItemStack held(LocalPlayer player) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.is(ModItems.CABLE_FACADE.get())) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    // The six faces of a box as quads.
    private static void box(VertexConsumer buffer, Matrix4f pose, AABB box, int color) {
        float x0 = (float) box.minX, y0 = (float) box.minY, z0 = (float) box.minZ, x1 = (float) box.maxX, y1 = (float) box.maxY, z1 = (float) box.maxZ;
        quad(buffer, pose, color, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
        quad(buffer, pose, color, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
        quad(buffer, pose, color, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
        quad(buffer, pose, color, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        quad(buffer, pose, color, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
        quad(buffer, pose, color, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
    }

    private static void quad(VertexConsumer buffer, Matrix4f pose, int color, float... corners) {
        for (int i = 0; i < 12; i += 3) {
            buffer.addVertex(pose, corners[i], corners[i + 1], corners[i + 2]).setColor(color);
        }
    }
}
