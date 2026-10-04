/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.rack;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.level.Level;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.TapePicker;
import net.zagdrath.encodedlogistics.rack.device.TapeLibraryDevice;

// A Tape Library's window and rear (HANDOFF_batch3 4.1), in texels of its texture, with ex the extras' origin (64 on the
// 4U's sheet, 128 on the 6U's):
//  - each tape in the magazine: its generation's edge (k * 5, ex), 3 x 9, at slot i's place (17 + 4 * (i % 12),
//    4 + 10 * (i / 12)), and under it a 3 x 1 fill bar (30 + f * 5, ex) - f 0 under half, 1 to 75%, 2 below full, 3 full,
//    4 empty - full-bright;
//  - the picker (TapePicker): its carriage on the rail (the head sprite's top two rows at y 2), the cable down from it
//    (a strip of chassis) and the head (60, ex) 8 x 6, holding the tape it carries; a tape on the head or in a drive
//    isn't in its slot;
//  - each drive on the back: its sled (0, ex + 12) 24 x 10 at bay j (4 + 26 * (j % 2), 2 + 12 * (j / 2)) of the back,
//    and at (+20, +1) its light - idle (26, ex + 12), reading (29), writing (32) - full-bright.
final class TapeLibraryRender implements RackClientDevices.RenderExtra {
    private static final float OVER_Z = 1.69F, FRONT_Z = 1.72F;

    // data: rows, tapes (per slot), then per bay drive state and drive tape, then the picker's x, y (float bits), and the
    // slot it holds (or -1).
    @Override
    public int[] capture(RackDevice device) {
        return capture(device, 1);
    }

    @Override
    public int[] capture(RackDevice device, float partialTick) {
        if (!(device instanceof TapeLibraryDevice library)) {
            return new int[0];
        }
        int slots = library.tapeSlots(), bays = library.bays();
        int[] data = new int[2 + slots + bays * 2 + 3];
        data[0] = library.rows();
        data[1] = bays;
        for (int slot = 0; slot < slots; slot++) {
            data[2 + slot] = library.shownTape(slot);
        }
        for (int bay = 0; bay < bays; bay++) {
            data[2 + slots + bay * 2] = library.shownDrive(bay);
            int inDrive = library.shownDriveTape(bay);
            data[2 + slots + bay * 2 + 1] = inDrive;
            if (inDrive >= 0) {
                data[2 + inDrive] = -1;
            }
        }
        int at = 2 + slots + bays * 2;
        TapePicker.Pose pose = new TapePicker.Pose(TapePicker.HOME_X, TapePicker.HOME_Y, false, true);
        int held = -1;
        int[] picker = library.shownPicker();
        Level level = library.rack() != null ? library.rack().getLevel() : null;
        if (picker.length >= 5 && level != null) {
            long start = (picker[2] & 0xFFFFFFFFL) | (long) picker[3] << 32;
            int slot = picker[0];
            pose = TapePicker.at(library.rows(), slot, picker[1] == 1, level.getGameTime() - start + partialTick);
            if (!pose.inSlot() && slot < slots) {
                // Off the shelf: on the head, or in the drive.
                if (pose.holding()) {
                    held = data[2 + slot] >= 0 ? data[2 + slot] : library.shownTape(slot);
                }
                data[2 + slot] = -1;
            }
        }
        data[at] = Float.floatToIntBits(pose.x());
        data[at + 1] = Float.floatToIntBits(pose.y());
        data[at + 2] = held;
        return data;
    }

    @Override
    public void submit(RackDeviceType type, RackDeviceInfo.Status status, int[] data, PoseStack poseStack, SubmitNodeCollector collector, int light) {
        if (data.length < 2) {
            return;
        }
        int rows = data[0], bays = data[1], slots = rows * 12, size = type.size(), ex = type.extrasOrigin(), back = type.backOrigin();
        int at = 2 + slots + bays * 2;
        float headX = Float.intBitsToFloat(data[at]), headY = Float.intBitsToFloat(data[at + 1]);
        int held = data[at + 2];
        TextureAtlasSprite sprite = RackClientDevices.sprite(type.id().withPath("block/rack_device/" + type.id().getPath()));
        boolean lit = status != RackDeviceInfo.Status.OFFLINE;
        collector.submitCustomGeometry(poseStack, Sheets.cutoutBlockItemSheet(), (pose, buffer) -> {
            for (int slot = 0; slot < slots; slot++) {
                int tape = data[2 + slot];
                if (tape >= 0) {
                    tape(buffer, pose, size, ex, TapePicker.slotX(slot), TapePicker.slotY(slot), tape, sprite, light, lit);
                }
            }
            // The picker: carriage, cable, head - and the tape it holds.
            RackClientDevices.frontQuad(buffer, pose, size, headX, 2, 8, 2, sprite, 60, ex, 8, 2, -1, light, OVER_Z);
            if (headY > 4) {
                RackClientDevices.frontQuad(buffer, pose, size, headX + 3, 4, 2, headY - 4, sprite, 112, 0, 2, 1, -1, light, OVER_Z);
            }
            if (held >= 0) {
                tape(buffer, pose, size, ex, headX + 2.5F, headY + 2, held, sprite, light, lit);
            }
            RackClientDevices.frontQuad(buffer, pose, size, headX, headY, 8, 6, sprite, 60, ex, 8, 6, -1, light, OVER_Z - 0.01F);
            // The drives on the back.
            for (int bay = 0; bay < bays; bay++) {
                int state = data[2 + slots + bay * 2];
                if (state < TapeLibraryDevice.DRIVE_IDLE) {
                    continue;
                }
                float bx = 4 + 26 * (bay % 2), by = back + 2 + 12 * (bay / 2);
                RackClientDevices.backQuad(buffer, pose, size, bx, by, 24, 10, sprite, 0, ex + 12, 24, 10, -1, light);
                if (lit) {
                    RackClientDevices.backQuad(buffer, pose, size, bx + 20, by + 1, 2, 1, sprite, 26 + 3 * state, ex + 12, 2, 1, -1,
                            LightCoordsUtil.FULL_BRIGHT);
                }
            }
        });
    }

    // A tape's edge at (x, y) and its fill bar under it (lit while the library has power).
    private static void tape(com.mojang.blaze3d.vertex.VertexConsumer buffer, PoseStack.Pose pose, int size, int ex, float x, float y, int tape,
            TextureAtlasSprite sprite, int light, boolean lit) {
        int generation = tape / 8, fill = tape % 8;
        RackClientDevices.frontQuad(buffer, pose, size, x, y, 3, 9, sprite, generation * 5, ex, 3, 9, -1, light, FRONT_Z);
        RackClientDevices.frontQuad(buffer, pose, size, x, y + 9, 3, 1, sprite, 30 + Math.min(fill, 4) * 5, ex, 3, 1, -1,
                lit ? LightCoordsUtil.FULL_BRIGHT : light, FRONT_Z);
    }
}
