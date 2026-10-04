/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.rack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.LightCoordsUtil;
import net.zagdrath.encodedlogistics.client.screen.FirewallPanel;
import net.zagdrath.encodedlogistics.client.screen.RackScreen;
import net.zagdrath.encodedlogistics.client.screen.RouterPanel;
import net.zagdrath.encodedlogistics.client.screen.UpsPanel;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.rack.device.UpsDevice;

// The client side of each rack device type, by type: its settings panel in the rack's screen (none: picking it just
// shows its name and status) and anything its renderer draws over its model.
//
// Render extras draw on the device's front, given in texels of its 128x128 texture (8 texels a pixel, the front from
// (0,0) across 104 texels, x running right to left across the model as the north face's u does).
public final class RackClientDevices {
    // What a renderer draws besides the device's model: capture() takes what it needs from the device (on the client
    // copy, when the frame is extracted), submit() draws it in the device's space (its bottom at y = 0).
    public interface RenderExtra {
        int capture(RackDevice device);

        void submit(RackDeviceType type, RackDeviceInfo.Status status, int data, PoseStack poseStack, SubmitNodeCollector collector, int light);
    }

    private static final Map<RackDeviceType, RenderExtra> EXTRAS = new HashMap<>();
    private static final Map<RackDeviceType, Function<RackScreen, RackScreen.Panel>> PANELS = new HashMap<>();

    static {
        PANELS.put(RackDeviceType.FIREWALL, FirewallPanel::new);
        PANELS.put(RackDeviceType.ROUTER, RouterPanel::new);
        PANELS.put(RackDeviceType.UPS, UpsPanel::new);
        EXTRAS.put(RackDeviceType.ROUTER, new RouterCages());
        EXTRAS.put(RackDeviceType.UPS, new UpsDisplay());
    }

    private RackClientDevices() {}

    public static @Nullable RenderExtra extra(RackDeviceType type) {
        return EXTRAS.get(type);
    }

    public static RackScreen.@Nullable Panel panel(RackDeviceType type, RackScreen screen) {
        Function<RackScreen, RackScreen.Panel> factory = PANELS.get(type);
        return factory != null ? factory.apply(screen) : null;
    }

    // --- Drawing on a front ---

    // Where the front face is, a hair in front of the glow layer.
    private static final float FRONT_Z = 1.72F, RIGHT_X = 14.5F;

    static TextureAtlasSprite sprite(Identifier texture) {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(texture);
    }

    // A quad over the front texels (x, y, w, h) of a device size units tall, showing the texels (u, v, w', h') of a sprite
    // (both in 128ths), tinted.
    static void frontQuad(VertexConsumer buffer, PoseStack.Pose pose, int size, float x, float y, float w, float h, TextureAtlasSprite sprite,
            float u, float v, float uw, float vh, int color, int light) {
        float x1 = (RIGHT_X - x / 8) / 16, x0 = (RIGHT_X - (x + w) / 8) / 16;
        float y1 = (size - y / 8) / 16, y0 = (size - (y + h) / 8) / 16;
        float z = FRONT_Z / 16;
        float u0 = sprite.getU(u / 128), u1 = sprite.getU((u + uw) / 128), v0 = sprite.getV(v / 128), v1 = sprite.getV((v + vh) / 128);
        vertex(buffer, pose, x1, y1, z, u0, v0, color, light);
        vertex(buffer, pose, x1, y0, z, u0, v1, color, light);
        vertex(buffer, pose, x0, y0, z, u1, v1, color, light);
        vertex(buffer, pose, x0, y1, z, u1, v0, color, light);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color, int light) {
        buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(pose, 0, 0, -1);
    }

    // --- The Router: transceivers in its cages ---

    private static final class RouterCages implements RenderExtra {
        @Override
        public int capture(RackDevice device) {
            int cages = 0;
            if (device instanceof RouterDevice router) {
                for (int i = 0; i < RouterDevice.CAGES; i++) {
                    if (router.hasTransceiver(i)) {
                        cages |= 1 << i;
                    }
                }
            }
            return cages;
        }

        @Override
        public void submit(RackDeviceType type, RackDeviceInfo.Status status, int cages, PoseStack poseStack, SubmitNodeCollector collector,
                int light) {
            if (cages == 0) {
                return;
            }
            TextureAtlasSprite sprite = sprite(type.id().withPath("block/rack_device/" + type.id().getPath()));
            collector.submitCustomGeometry(poseStack, Sheets.cutoutBlockItemSheet(), (pose, buffer) -> {
                for (int i = 0; i < RouterDevice.CAGES; i++) {
                    if ((cages >> i & 1) != 0) {
                        frontQuad(buffer, pose, type.size(), 67 + 8 * i, 3, 6, 2, sprite, 0, 64, 6, 2, -1, light);
                    }
                }
            });
        }
    }

    // --- The UPS: battery % on the LCD, the load LEDs ---

    private static final class UpsDisplay implements RenderExtra {
        private static final int GREEN = 0xFF66FF77, AMBER = 0xFFFFB040, RED = 0xFFFF4848;

        @Override
        public int capture(RackDevice device) {
            return device instanceof UpsDevice ups ? Math.clamp(ups.shownPercent(), 0, 100) | Math.clamp(ups.shownLeds(), 0, 10) << 8 : 0;
        }

        @Override
        public void submit(RackDeviceType type, RackDeviceInfo.Status status, int data, PoseStack poseStack, SubmitNodeCollector collector,
                int light) {
            if (status == RackDeviceInfo.Status.OFFLINE) {
                return;
            }
            int percent = data & 0xFF, leds = data >> 8 & 0xFF;
            TextureAtlasSprite sprite = sprite(type.id().withPath("block/rack_device/" + type.id().getPath()));
            String text = percent + "%";
            collector.submitCustomGeometry(poseStack, Sheets.cutoutBlockItemSheet(), (pose, buffer) -> {
                // 7-segment digits (4x7 texels at (5d, 64), '%' at (50, 64)), full-bright on the LCD.
                for (int i = 0; i < text.length(); i++) {
                    char c = text.charAt(i);
                    int glyph = c == '%' ? 50 : 5 * (c - '0');
                    frontQuad(buffer, pose, type.size(), 17 + 5 * i, 5, 4, 7, sprite, glyph, 64, 4, 7, -1, LightCoordsUtil.FULL_BRIGHT);
                }
                // The load bar: lit LEDs green, then amber, then red; the rest dark (covering the baked default).
                for (int i = 0; i < UpsDevice.LOAD_LEDS; i++) {
                    int x = 44 + 3 * i;
                    if (i < leds) {
                        int color = i <= 5 ? GREEN : i <= 7 ? AMBER : RED;
                        // A lit digit texel, tinted.
                        frontQuad(buffer, pose, type.size(), x, 6, 2, 3, sprite, 1, 64, 1, 1, color, LightCoordsUtil.FULL_BRIGHT);
                    } else {
                        frontQuad(buffer, pose, type.size(), x, 6, 2, 3, sprite, x, 6, 2, 3, -1, light);
                    }
                }
            });
        }
    }
}
