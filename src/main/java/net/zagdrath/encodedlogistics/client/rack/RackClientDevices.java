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
import net.zagdrath.encodedlogistics.client.screen.FabricationServerPanel;
import net.zagdrath.encodedlogistics.client.screen.FirewallPanel;
import net.zagdrath.encodedlogistics.client.screen.L3SwitchPanel;
import net.zagdrath.encodedlogistics.client.screen.MonitoringPanel;
import net.zagdrath.encodedlogistics.client.screen.NetworkControllerPanel;
import net.zagdrath.encodedlogistics.client.screen.RackScreen;
import net.zagdrath.encodedlogistics.client.screen.RouterPanel;
import net.zagdrath.encodedlogistics.client.screen.ServerPanel;
import net.zagdrath.encodedlogistics.client.screen.StoragePanel;
import net.zagdrath.encodedlogistics.client.screen.SwitchPanel;
import net.zagdrath.encodedlogistics.client.screen.TapeLibraryPanel;
import net.zagdrath.encodedlogistics.client.screen.UpsPanel;
import net.zagdrath.encodedlogistics.client.screen.WirelessControllerPanel;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.StorageDevice;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.rack.device.SanDevice;
import net.zagdrath.encodedlogistics.rack.device.UpsDevice;

// The client side of each rack device type, by type: its settings panel in the rack's screen (none: picking it just
// shows its name and status) and anything its renderer draws over its model.
//
// Render extras draw on the device's front or back, given in texels of its texture (8 texels a pixel, 128 across and
// RackDeviceType#sheetHeight() tall: the front from (0,0) and the back from (0,32) - (0,64) for 5U and 6U - each 104
// texels across, x running right to left across the model on both faces, as their u does).
public final class RackClientDevices {
    // What a renderer draws besides the device's model: capture() takes what it needs from the device (on the client
    // copy, when the frame is extracted), submit() draws it in the device's space (its bottom at y = 0).
    public interface RenderExtra {
        int[] capture(RackDevice device);

        // For extras that animate between ticks.
        default int[] capture(RackDevice device, float partialTick) {
            return capture(device);
        }

        void submit(RackDeviceType type, RackDeviceInfo.Status status, int[] data, PoseStack poseStack, SubmitNodeCollector collector, int light);
    }

    private static final Map<RackDeviceType, RenderExtra> EXTRAS = new HashMap<>();
    private static final Map<RackDeviceType, Function<RackScreen, RackScreen.Panel>> PANELS = new HashMap<>();

    static {
        PANELS.put(RackDeviceType.FIREWALL, FirewallPanel::new);
        PANELS.put(RackDeviceType.ROUTER, RouterPanel::new);
        PANELS.put(RackDeviceType.UPS, UpsPanel::new);
        PANELS.put(RackDeviceType.NETWORK_CONTROLLER_2U, NetworkControllerPanel::new);
        PANELS.put(RackDeviceType.NETWORK_CONTROLLER_4U, NetworkControllerPanel::new);
        PANELS.put(RackDeviceType.L2_SWITCH_24, SwitchPanel::new);
        PANELS.put(RackDeviceType.L2_SWITCH_48, SwitchPanel::new);
        PANELS.put(RackDeviceType.L3_SWITCH, L3SwitchPanel::new);
        PANELS.put(RackDeviceType.COMPUTE_SERVER, ServerPanel::new);
        PANELS.put(RackDeviceType.MEMORY_SERVER, ServerPanel::new);
        PANELS.put(RackDeviceType.FABRICATION_SERVER, FabricationServerPanel::new);
        PANELS.put(RackDeviceType.MONITORING_SERVER, MonitoringPanel::new);
        PANELS.put(RackDeviceType.NAS, StoragePanel::new);
        PANELS.put(RackDeviceType.SAN, StoragePanel::new);
        EXTRAS.put(RackDeviceType.ROUTER, new RouterCages());
        EXTRAS.put(RackDeviceType.UPS, new UpsDisplay());
        EXTRAS.put(RackDeviceType.NAS, new DriveBays(false));
        EXTRAS.put(RackDeviceType.SAN, new DriveBays(true));
        EXTRAS.put(RackDeviceType.RACK_CONSOLE, new RackConsoleRender());
        EXTRAS.put(RackDeviceType.TAPE_LIBRARY_4U, new TapeLibraryRender());
        EXTRAS.put(RackDeviceType.TAPE_LIBRARY_6U, new TapeLibraryRender());
        PANELS.put(RackDeviceType.WIRELESS_CONTROLLER, WirelessControllerPanel::new);
        PANELS.put(RackDeviceType.TAPE_LIBRARY_4U, TapeLibraryPanel::new);
        PANELS.put(RackDeviceType.TAPE_LIBRARY_6U, TapeLibraryPanel::new);
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
    // Quads just in front of the chassis's front (z 1.75), and a layer above them for anything drawn over another quad.
    private static final float FRONT_Z = 1.72F, OVER_Z = 1.69F, RIGHT_X = 14.5F;

    static TextureAtlasSprite sprite(Identifier texture) {
        return Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getSprite(texture);
    }

    // A quad over the front texels (x, y, w, h) of a device size units tall, showing the texels (u, v, w', h') of a sprite
    // (its texture's texels), tinted.
    static void frontQuad(VertexConsumer buffer, PoseStack.Pose pose, int size, float x, float y, float w, float h, TextureAtlasSprite sprite,
            float u, float v, float uw, float vh, int color, int light) {
        frontQuad(buffer, pose, size, x, y, w, h, sprite, u, v, uw, vh, color, light, FRONT_Z);
    }

    // The same at depth frontZ (OVER_Z over another front quad).
    static void frontQuad(VertexConsumer buffer, PoseStack.Pose pose, int size, float x, float y, float w, float h, TextureAtlasSprite sprite,
            float u, float v, float uw, float vh, int color, int light, float frontZ) {
        float x1 = (RIGHT_X - x / 8) / 16, x0 = (RIGHT_X - (x + w) / 8) / 16;
        float y1 = (size - y / 8) / 16, y0 = (size - (y + h) / 8) / 16;
        float z = frontZ / 16, sheet = sheetHeight(size);
        float u0 = sprite.getU(u / 128), u1 = sprite.getU((u + uw) / 128), v0 = sprite.getV(v / sheet), v1 = sprite.getV((v + vh) / sheet);
        vertex(buffer, pose, x1, y1, z, u0, v0, color, light);
        vertex(buffer, pose, x1, y0, z, u0, v1, color, light);
        vertex(buffer, pose, x0, y0, z, u1, v1, color, light);
        vertex(buffer, pose, x0, y1, z, u1, v0, color, light);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color, int light) {
        vertex(buffer, pose, x, y, z, u, v, color, light, -1);
    }

    private static void vertex(VertexConsumer buffer, PoseStack.Pose pose, float x, float y, float z, float u, float v, int color, int light, int normalZ) {
        buffer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(pose, 0, 0, normalZ);
    }

    // A device's texture height and where its back face starts on it (RackDeviceType#sheetHeight, #backOrigin).
    static float sheetHeight(int size) {
        return size > 4 ? 256 : 128;
    }

    static float backOrigin(int size) {
        return size > 4 ? 64 : 32;
    }

    // The same on the back face (y in the texture's texels, the back from backOrigin; the face just behind the chassis's
    // back at z 29.25).
    private static final float BACK_Z = 29.28F;

    static void backQuad(VertexConsumer buffer, PoseStack.Pose pose, int size, float x, float y, float w, float h, TextureAtlasSprite sprite,
            float u, float v, float uw, float vh, int color, int light) {
        float x1 = (RIGHT_X - x / 8) / 16, x0 = (RIGHT_X - (x + w) / 8) / 16;
        float back = backOrigin(size), sheet = sheetHeight(size);
        float y1 = (size - (y - back) / 8) / 16, y0 = (size - (y + h - back) / 8) / 16;
        float z = BACK_Z / 16;
        float u0 = sprite.getU(u / 128), u1 = sprite.getU((u + uw) / 128), v0 = sprite.getV(v / sheet), v1 = sprite.getV((v + vh) / sheet);
        // South-facing: from the low x end; the texel at x (u0) sits at the high x end.
        vertex(buffer, pose, x0, y1, z, u1, v0, color, light, 1);
        vertex(buffer, pose, x0, y0, z, u1, v1, color, light, 1);
        vertex(buffer, pose, x1, y0, z, u0, v1, color, light, 1);
        vertex(buffer, pose, x1, y1, z, u0, v0, color, light, 1);
    }

    // --- The Router: transceivers in its cages ---

    private static final class RouterCages implements RenderExtra {
        @Override
        public int[] capture(RackDevice device) {
            int cages = 0;
            if (device instanceof RouterDevice router) {
                for (int i = 0; i < RouterDevice.CAGES; i++) {
                    if (router.hasTransceiver(i)) {
                        cages |= 1 << i;
                    }
                }
            }
            return new int[] { cages };
        }

        @Override
        public void submit(RackDeviceType type, RackDeviceInfo.Status status, int[] data, PoseStack poseStack, SubmitNodeCollector collector,
                int light) {
            int cages = data[0];
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

    // --- The UPS: battery % on the LCD (on battery, a marquee between showings of it), the load LEDs ---

    private static final class UpsDisplay implements RenderExtra {
        private static final int GREEN = 0xFF66FF77, AMBER = 0xFFFFB040, RED = 0xFFFF4848;
        // The LCD's window and its digits' colour (the marquee's white glyphs are tinted with it).
        private static final int LCD_X = 15, LCD_W = 24, MARQUEE_Y = 5, LCD_COLOR = 0xFFA4FFBB;
        // The marquee's 3x5 glyphs: at (4i, 72) for the first 32 of these, (4(i - 32), 78) after (ups_marquee_font.txt).
        private static final String GLYPHS = " ABCDEFGHILMNOPRSTUVWY0123456789%:-";
        // The digits show this long between passes; a pass scrolls a texel every 2 ticks.
        private static final int DIGITS_TICKS = 80, TICKS_PER_TEXEL = 2;

        @Override
        public int[] capture(RackDevice device) {
            if (!(device instanceof UpsDevice ups)) {
                return new int[] { 0, 0, -1 };
            }
            int packed = Math.clamp(ups.shownPercent(), 0, 100) | Math.clamp(ups.shownLeds(), 0, 10) << 8 | (ups.shownBattery() ? 1 << 16 : 0);
            return new int[] { packed, ups.clientTicks(), (int) Math.min(Integer.MAX_VALUE, ups.shownRuntime()) };
        }

        // "ON BATTERY - 5M 44S" (just "ON BATTERY" with no load to go by).
        static String marquee(long runtime) {
            String text = "ON BATTERY";
            if (runtime >= 0) {
                text += " - " + UpsDevice.runtime(runtime).getString().toUpperCase(java.util.Locale.ROOT);
            }
            return text;
        }

        // Where the marquee's left edge is this tick (relative to the LCD's), or Integer.MIN_VALUE while the digits show.
        static int marqueeOffset(int ticks, int width) {
            int pass = (width + LCD_W) * TICKS_PER_TEXEL, cycle = pass + DIGITS_TICKS;
            int at = Math.floorMod(ticks, cycle);
            return at < pass ? LCD_W - at / TICKS_PER_TEXEL : Integer.MIN_VALUE;
        }

        @Override
        public void submit(RackDeviceType type, RackDeviceInfo.Status status, int[] packed, PoseStack poseStack, SubmitNodeCollector collector,
                int light) {
            if (status == RackDeviceInfo.Status.OFFLINE) {
                return;
            }
            int data = packed[0];
            int percent = data & 0xFF, leds = data >> 8 & 0xFF;
            boolean battery = (data >> 16 & 1) != 0;
            TextureAtlasSprite sprite = sprite(type.id().withPath("block/rack_device/" + type.id().getPath()));
            String text = percent + "%";
            String marquee = battery && packed.length > 2 ? marquee(packed[2]) : "";
            int offset = battery && packed.length > 2 ? marqueeOffset(packed[1], marquee.length() * 4) : Integer.MIN_VALUE;
            collector.submitCustomGeometry(poseStack, Sheets.cutoutBlockItemSheet(), (pose, buffer) -> {
                if (offset != Integer.MIN_VALUE) {
                    // The marquee, clipped to the LCD.
                    for (int i = 0; i < marquee.length(); i++) {
                        int glyph = Math.max(0, GLYPHS.indexOf(marquee.charAt(i)));
                        int u = glyph < 32 ? 4 * glyph : 4 * (glyph - 32), v = glyph < 32 ? 72 : 78;
                        int x0 = offset + 4 * i, from = Math.max(0, -x0), to = Math.min(3, LCD_W - x0);
                        if (to > from) {
                            frontQuad(buffer, pose, type.size(), LCD_X + x0 + from, MARQUEE_Y, to - from, 5, sprite, u + from, v, to - from, 5, LCD_COLOR,
                                    LightCoordsUtil.FULL_BRIGHT);
                        }
                    }
                } else {
                    // 7-segment digits (4x7 texels at (5d, 64), '%' at (50, 64)), full-bright on the LCD.
                    for (int i = 0; i < text.length(); i++) {
                        char c = text.charAt(i);
                        int glyph = c == '%' ? 50 : 5 * (c - '0');
                        frontQuad(buffer, pose, type.size(), 17 + 5 * i, 5, 4, 7, sprite, glyph, 64, 4, 7, -1, LightCoordsUtil.FULL_BRIGHT);
                    }
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

    // --- The NAS and SAN: drives in their bays, the SAN's transceivers in its rear cages ---

    // Each occupied bay shows its drive's tier sled, and the drive's fill light (full-bright) at the sled's lower right:
    // NAS bays are 12x14 from (15 + 12i, 1), sleds at (13t, 64); SAN bays 6x14 in a 12 x 2 grid from (14, 1), sleds at
    // (7t, 80); the 2x2 lights at (70 + 3k, sled row), k 0 green, 1 yellow, 2 orange, 3 red, 4 off.
    private static final class DriveBays implements RenderExtra {
        private final boolean san;

        DriveBays(boolean san) {
            this.san = san;
        }

        @Override
        public int[] capture(RackDevice device) {
            if (!(device instanceof StorageDevice storage)) {
                return new int[0];
            }
            int[] data = new int[storage.drives() + 1];
            for (int i = 0; i < storage.drives(); i++) {
                data[i] = storage.shownBay(i);
            }
            int cages = 0;
            if (device instanceof SanDevice sanDevice) {
                for (int i = 0; i < SanDevice.CAGES; i++) {
                    if (sanDevice.hasTransceiver(i)) {
                        cages |= 1 << i;
                    }
                }
            }
            data[storage.drives()] = cages;
            return data;
        }

        @Override
        public void submit(RackDeviceType type, RackDeviceInfo.Status status, int[] data, PoseStack poseStack, SubmitNodeCollector collector,
                int light) {
            if (data.length == 0) {
                return;
            }
            TextureAtlasSprite sprite = sprite(type.id().withPath("block/rack_device/" + type.id().getPath()));
            int bays = data.length - 1, w = san ? 6 : 12, h = 14, sledRow = san ? 80 : 64;
            collector.submitCustomGeometry(poseStack, Sheets.cutoutBlockItemSheet(), (pose, buffer) -> {
                for (int i = 0; i < bays; i++) {
                    if (data[i] < 0) {
                        continue;
                    }
                    int tier = data[i] / 8, lit = Math.min(4, data[i] % 8);
                    int x = san ? 14 + 6 * (i % 12) : 15 + 12 * i, y = san ? 1 + 15 * (i / 12) : 1;
                    frontQuad(buffer, pose, type.size(), x, y, w, h, sprite, tier * (w + 1), sledRow, w, h, -1, light);
                    frontQuad(buffer, pose, type.size(), x + w - 3, y + h - 4, 2, 2, sprite, 70 + 3 * lit, sledRow, 2, 2, -1,
                            lit == 4 ? light : LightCoordsUtil.FULL_BRIGHT, OVER_Z);
                }
                int cages = data[bays];
                for (int j = 0; j < SanDevice.CAGES; j++) {
                    if ((cages >> j & 1) != 0) {
                        backQuad(buffer, pose, type.size(), 57 + 10 * j, 35, 6, 2, sprite, 0, 112, 6, 2, -1, light);
                    }
                }
            });
        }
    }
}
