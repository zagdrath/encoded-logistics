/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.device.NetworkControllerDevice;

// A rack Network Controller's panel (screens/rack/network_controller_<n>u.json): its energy well, Status / Energy /
// Usage (or the standby's idle draw) / Devices, Lanes with a bar, the redundant pair (role, partner, last failover), its
// uplinks (one chip per connection point of its rack: up, down, unused or a network mismatch) and Switch over (asks
// once more before it does it; only while both of a pair are online).
public class NetworkControllerPanel extends RackScreen.Panel {
    private static final Identifier ENERGY_BAR = EncodedLogistics.id("controller/energy_bar"), LANES_BAR = EncodedLogistics.id("common/bar_fill_mint");
    private static final Identifier[] CHIPS = { EncodedLogistics.id("rack/controller/chip_up"), EncodedLogistics.id("rack/controller/chip_down"),
            EncodedLogistics.id("rack/controller/chip_unused"), EncodedLogistics.id("rack/controller/chip_mismatch") };
    // The energy well's inside (its texture's well is wider and taller than the UPS's): the bar is stretched to fill it.
    private static final int GAUGE_X = 9, GAUGE_Y = 23, GAUGE_W = 14, GAUGE_H = 58;
    private static final int LABEL_X = 31, VALUE_RIGHT = 165, READOUT_Y = 25, LINE_H = 11, LANES_Y = 67, BAR_X = 32, BAR_Y = 78, BAR_W = 132;
    private static final int PAIR_TITLE_Y = 86, PAIR_X = 12, PAIR_Y = 98, PAIR_VALUE_X = 82;
    private static final int SWITCH_X = 108, SWITCH_Y = 136, SWITCH_W = 60, SWITCH_H = 14;
    private static final int UPLINKS_TITLE_Y = 140, CHIP_X = 9, CHIP_Y = 153, CHIP_W = 16, CHIP_H = 8, CHIP_SPACING = 20;
    private static final int CONFIRM_TICKS = 60;

    private int confirming;

    public NetworkControllerPanel(RackScreen screen) {
        super(screen);
    }

    @Override
    protected Identifier background() {
        NetworkControllerDevice device = device(NetworkControllerDevice.class);
        String id = device != null ? device.type().id().getPath() : "network_controller_2u";
        return EncodedLogistics.id("textures/gui/rack/" + id + ".png");
    }

    private int chips() {
        NetworkControllerDevice device = device(NetworkControllerDevice.class);
        return device != null && device.size() >= 4 ? 8 : 4;
    }

    @Override
    protected void tick() {
        if (confirming > 0) {
            confirming--;
        }
    }

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.left(), y = screen.top();
        ValueInput data = data();
        NetworkControllerDevice device = device(NetworkControllerDevice.class);
        if (data == null || device == null) {
            return;
        }
        long capacity = Math.max(1, data.getLongOr("network_capacity", 0)), stored = data.getLongOr("network_stored", 0);
        if (device.shown() == NetworkControllerDevice.Shown.STANDBY || device.shown() == NetworkControllerDevice.Shown.HANDING_OVER) {
            // The standby's own buffer.
            capacity = Math.max(1, data.getIntOr("capacity", 1));
            stored = device.getEnergy();
        }
        int height = (int) Math.min(GAUGE_H, Math.round((double) stored * GAUGE_H / capacity));
        if (height > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ENERGY_BAR, GAUGE_W, GAUGE_H, 0, GAUGE_H - height, x + GAUGE_X,
                    y + GAUGE_Y + GAUGE_H - height, GAUGE_W, height);
        }
        int total = data.getIntOr("lanes_total", 0), used = data.getIntOr("lanes_used", 0);
        int filled = total <= 0 ? 0 : Math.round(BAR_W * Math.clamp((float) used / total, 0.0F, 1.0F));
        if (filled > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LANES_BAR, 200, 6, 0, 0, x + BAR_X, y + BAR_Y, filled, 2);
        }
        List<int[]> uplinks = uplinks(data);
        for (int i = 0; i < chips(); i++) {
            int state = i < uplinks.size() ? uplinks.get(i)[0] : RackBlockEntity.PointState.UNUSED.ordinal();
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, CHIPS[Math.clamp(state, 0, 3)], x + CHIP_X + i * CHIP_SPACING, y + CHIP_Y, CHIP_W, CHIP_H);
        }
        Component text = Component.translatable(confirming > 0 ? "gui.encodedlogistics.controller.switch_over.sure" : "gui.encodedlogistics.controller.switch_over");
        PartScreens.wideButton(graphics, font(), x + SWITCH_X, y + SWITCH_Y, SWITCH_W, SWITCH_H, text, data.getBooleanOr("can_switch", false), mouseX, mouseY);
    }

    // Each chip's state (RackBlockEntity.PointState) from the panel's data.
    private static List<int[]> uplinks(ValueInput data) {
        List<int[]> uplinks = new ArrayList<>();
        for (ValueInput child : data.childrenListOrEmpty("uplinks")) {
            uplinks.add(new int[] { child.getIntOr("state", 2), child.getIntOr("lanes", 0) });
        }
        return uplinks;
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ValueInput data = data();
        NetworkControllerDevice device = device(NetworkControllerDevice.class);
        if (data == null || device == null) {
            return;
        }
        RackDeviceInfo.Status kind = RackDeviceInfo.Status.byId(data.getIntOr("status_kind", 1));
        row(graphics, 0, "status", Component.literal(data.getStringOr("status", "")), RackScreen.statusColor(kind));
        boolean standby = device.shown() == NetworkControllerDevice.Shown.STANDBY;
        row(graphics, 1, "energy", Component.literal(NetworkControllerDevice.compact(standby ? device.getEnergy() : data.getLongOr("network_stored", 0)) + "/"
                + NetworkControllerDevice.compact(standby ? data.getIntOr("capacity", 0) : data.getLongOr("network_capacity", 0)) + " FE"), RackScreen.TEXT);
        row(graphics, 2, "usage", standby ? Component.translatable("hud.encodedlogistics.controller.idle", NetworkControllerDevice.decimal(data.getDoubleOr("idle", 0)))
                : Component.literal(NetworkControllerDevice.compact(Math.round(data.getDoubleOr("usage", 0))) + " FE/t"), RackScreen.TEXT);
        row(graphics, 3, "devices", Component.literal(Integer.toString(data.getIntOr("devices", 0))), RackScreen.TEXT);
        Component lanes = device.shown().failover() ? Component.translatable("hud.encodedlogistics.status.paused")
                : Component.literal(data.getIntOr("lanes_used", 0) + " / " + data.getIntOr("lanes_total", 0));
        graphics.text(font(), Component.translatable("gui.encodedlogistics.controller.lanes"), LABEL_X, LANES_Y, RackScreen.TEXT_MUTED, false);
        graphics.text(font(), lanes, VALUE_RIGHT - font().width(lanes), LANES_Y, RackScreen.TEXT, false);

        graphics.text(font(), Component.translatable("gui.encodedlogistics.controller.pair"), 8, PAIR_TITLE_Y, RackScreen.TEXT_MUTED, false);
        String partner = data.getStringOr("partner", "");
        String partnerRole = device.shown() == NetworkControllerDevice.Shown.STANDBY ? "active" : "standby";
        pair(graphics, 0, "role", data.getStringOr("role", ""));
        pair(graphics, 1, "partner", partner.isEmpty() ? "—" : partner + " (" + Component.translatable("gui.encodedlogistics.controller.partner." + partnerRole).getString() + ")");
        String last = data.getStringOr("last_failover_text", "");
        pair(graphics, 2, "last_failover", last.isEmpty() ? Component.translatable("gui.encodedlogistics.controller.never").getString() : last);
        graphics.text(font(), Component.translatable("gui.encodedlogistics.controller.uplinks"), 8, UPLINKS_TITLE_Y, RackScreen.TEXT_MUTED, false);
    }

    private void row(GuiGraphicsExtractor graphics, int line, String key, Component value, int color) {
        int y = READOUT_Y + line * LINE_H;
        graphics.text(font(), Component.translatable("gui.encodedlogistics.controller." + key), LABEL_X, y, RackScreen.TEXT_MUTED, false);
        graphics.text(font(), value, VALUE_RIGHT - font().width(value), y, color, false);
    }

    private void pair(GuiGraphicsExtractor graphics, int line, String key, String value) {
        int y = PAIR_Y + line * LINE_H;
        graphics.text(font(), Component.translatable("gui.encodedlogistics.controller." + key), PAIR_X, y, RackScreen.TEXT_MUTED, false);
        graphics.text(font(), font().plainSubstrByWidth(value, 168 - PAIR_VALUE_X), PAIR_VALUE_X, y, RackScreen.TEXT, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ValueInput data = data();
        if (data == null) {
            return;
        }
        if (screen.over(mouseX, mouseY, SWITCH_X, SWITCH_Y, SWITCH_W, SWITCH_H) && confirming > 0) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.controller.switch_over.confirm"), mouseX, mouseY);
            return;
        }
        int i = 0;
        for (ValueInput child : data.childrenListOrEmpty("uplinks")) {
            if (i < chips() && screen.over(mouseX, mouseY, CHIP_X + i * CHIP_SPACING, CHIP_Y, CHIP_W, CHIP_H)) {
                RackBlockEntity.PointState state = RackBlockEntity.PointState.values()[Math.clamp(child.getIntOr("state", 2), 0, 3)];
                List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable("gui.encodedlogistics.controller.uplink.tooltip", i + 1, child.getStringOr("point", ""),
                        child.getStringOr("cable", "—").isEmpty() ? "—" : child.getStringOr("cable", ""), child.getIntOr("lanes", 0)));
                lines.add(Component.translatable("gui.encodedlogistics.controller.uplink." + state.name().toLowerCase(Locale.ROOT))
                        .withColor(state == RackBlockEntity.PointState.UP ? RackScreen.ACCENT : state == RackBlockEntity.PointState.UNUSED ? RackScreen.TEXT_MUTED
                                : RackScreen.ERROR));
                graphics.setComponentTooltipForNextFrame(font(), lines, mouseX, mouseY);
                return;
            }
            i++;
        }
        if (screen.over(mouseX, mouseY, GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H)) {
            graphics.setTooltipForNextFrame(Component.literal(String.format(Locale.ROOT, "%,d / %,d FE", data.getLongOr("network_stored", 0),
                    data.getLongOr("network_capacity", 0))), mouseX, mouseY);
        }
    }

    @Override
    protected boolean mouseClicked(double x, double y, int button, boolean shift) {
        ValueInput data = data();
        if (button == InputConstants.MOUSE_BUTTON_LEFT && data != null && data.getBooleanOr("can_switch", false) && x >= SWITCH_X
                && x < SWITCH_X + SWITCH_W && y >= SWITCH_Y && y < SWITCH_Y + SWITCH_H) {
            if (confirming > 0) {
                confirming = 0;
                send(NetworkControllerDevice.ACTION_SWITCH_OVER, 0, "");
            } else {
                confirming = CONFIRM_TICKS;
            }
            return true;
        }
        return false;
    }
}
