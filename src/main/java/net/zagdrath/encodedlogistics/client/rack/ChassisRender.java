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
import net.zagdrath.encodedlogistics.rack.chassis.Chassis;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisLayout;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisPart;

// A modular chassis's parts in their bays (the optical handoff 7): each part's faceplate model
// (models/block/rack_card/<part>.json, 0.25 deep, its face at z 0) at its bay - for a bay (x, y, w, h) on a device U
// units tall, at x = 14.45 - (x + w) / 8, y = U - (y + h) / 8, z = 1.5 in front of the chassis's face; on the back,
// turned to face out, from x = 1.55 + x / 8, its face at z 29.5 - with its lit model over it while it works. Empty bays
// show the chassis sheet's blanking plates.
final class ChassisRender implements RackClientDevices.RenderExtra {
    private static final int[] NO_TINTS = new int[0];

    static ChassisLayout layout(RackDeviceType type) {
        return type == RackDeviceType.CHASSIS_SWITCH ? ChassisLayout.SWITCH : type == RackDeviceType.BLADE_ENCLOSURE ? ChassisLayout.BLADE : ChassisLayout.SHELF;
    }

    @Override
    public int[] capture(RackDevice device) {
        return device instanceof Chassis chassis ? chassis.shownParts().clone() : new int[0];
    }

    @Override
    public void submit(RackDeviceType type, RackDeviceInfo.Status status, int[] data, PoseStack poseStack, SubmitNodeCollector collector, int light) {
        List<ChassisLayout.Bay> bays = layout(type).bays();
        int size = type.size();
        for (int i = 0; i < data.length && i < bays.size(); i++) {
            ChassisPart part = ChassisPart.byOrdinal((data[i] & 255) - 1);
            if (part == null) {
                continue;
            }
            ChassisLayout.Bay bay = bays.get(i);
            poseStack.pushPose();
            float y = (size - (bay.y() + bay.h()) / 8.0F) / 16;
            if (bay.rear()) {
                poseStack.translate((1.55F + (bay.x() + bay.w()) / 8.0F) / 16, y, 29.5F / 16);
                poseStack.rotate(Axis.YP.rotationDegrees(180));
            } else {
                poseStack.translate((14.45F - (bay.x() + bay.w()) / 8.0F) / 16, y, 1.5F / 16);
            }
            submit(poseStack, collector, part.model(false), light);
            if ((data[i] & 256) != 0) {
                submit(poseStack, collector, part.model(true), light);
            }
            poseStack.popPose();
        }
    }

    private static void submit(PoseStack poseStack, SubmitNodeCollector collector, Identifier id, int light) {
        BlockStateModelPart model = RackModels.get(id);
        if (model != null) {
            collector.submitBlockModel(poseStack, Sheets.cutoutBlockItemSheet(), List.of(model), NO_TINTS, light, OverlayTexture.NO_OVERLAY, 0);
        }
    }
}
