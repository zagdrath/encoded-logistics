/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.rack;

import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.device.RackConsoleDevice;

// The Rack Console's moving parts (animation_specs.json -> rack_console), over its body (the device model): the drawer
// (front plate and tray, its light for the state), the keyboard and the lid slide out 9 px together; then the lid
// folds up 95 degrees about its hinge (8, 0.625, 13.5), which rides with the drawer and, as the lid rises, slides 5.5 px
// forward so the screen stands just behind the keyboard, clear of the rack. Past 60 degrees, on a running network,
// the lid shows its lit screen: the Access Terminal's, or the Fabrication Terminal's with a Memory Die.
final class RackConsoleRender implements RackClientDevices.RenderExtra {
    private static final int[] NO_TINTS = new int[0];
    private static final float TRAVEL = 9, LID_DEGREES = 95, HINGE_FORWARD = 5.5F, HINGE_Y = 0.625F, HINGE_Z = 13.5F, SCREEN_AFTER = 60;

    @Override
    public int[] capture(RackDevice device) {
        return capture(device, 1);
    }

    @Override
    public int[] capture(RackDevice device, float partialTick) {
        if (!(device instanceof RackConsoleDevice console)) {
            return new int[] { 0, 0, 0 };
        }
        return new int[] { Float.floatToIntBits(console.drawer(partialTick)), Float.floatToIntBits(console.lid(partialTick)),
                console.fabrication() ? 1 : 0 };
    }

    @Override
    public void submit(RackDeviceType type, RackDeviceInfo.Status status, int[] data, PoseStack poseStack, SubmitNodeCollector collector, int light) {
        float drawer = Float.intBitsToFloat(data[0]), lid = Float.intBitsToFloat(data[1]);
        poseStack.pushPose();
        poseStack.translate(0, 0, -TRAVEL * drawer / 16);
        part(poseStack, collector, switch (status) {
            case OFFLINE -> RackModels.CONSOLE_DRAWER;
            case ONLINE, WARNING -> RackModels.CONSOLE_DRAWER_ON;
            case FAULT -> RackModels.CONSOLE_DRAWER_FAULT;
        }, light);
        part(poseStack, collector, RackModels.CONSOLE_KEYBOARD, light);
        float degrees = LID_DEGREES * lid;
        // 1 - cos keeps the lying lid's front edge behind the drawer's front plate while it slides.
        float forward = HINGE_FORWARD * Math.min(1, 1 - (float) Math.cos(Math.toRadians(degrees)));
        poseStack.translate(0, 0, -forward / 16);
        poseStack.translate(0, HINGE_Y / 16, HINGE_Z / 16);
        poseStack.mulPose(Axis.XP.rotationDegrees(degrees));
        poseStack.translate(0, -HINGE_Y / 16, -HINGE_Z / 16);
        Identifier lit = data.length > 2 && data[2] == 1 ? RackModels.CONSOLE_LID_FAB : RackModels.CONSOLE_LID_ON;
        part(poseStack, collector, degrees > SCREEN_AFTER && status == RackDeviceInfo.Status.ONLINE ? lit : RackModels.CONSOLE_LID, light);
        poseStack.popPose();
    }

    private static void part(PoseStack poseStack, SubmitNodeCollector collector, Identifier model, int light) {
        BlockStateModelPart part = RackModels.get(model);
        if (part != null) {
            collector.submitBlockModel(poseStack, Sheets.cutoutBlockItemSheet(), List.of(part), NO_TINTS, light, OverlayTexture.NO_OVERLAY, 0);
        }
    }
}
