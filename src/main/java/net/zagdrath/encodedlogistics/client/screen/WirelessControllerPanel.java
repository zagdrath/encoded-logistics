/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.storage.ValueInput;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.rack.device.WirelessControllerDevice;

// The Wireless Controller's panel (screens/rack/wireless_controller.json): the Handheld Terminals linked to it, a row
// each - its owner's face and name, the dimension they're in (or were last seen in), a dot lit while they have the
// terminal open through it, and an X to unlink them - then how many are linked and connected.
public class WirelessControllerPanel extends RackScreen.Panel {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/rack/wireless_controller.png");
    private static final Identifier DOT_ON = EncodedLogistics.id("hud/dot_online"), DOT_OFF = EncodedLogistics.id("hud/dot_offline"),
            UNLINK = EncodedLogistics.id("common/cancel_small");
    private static final int LIST_X = 10, LIST_Y = 22, ROWS = 9, ROW_H = 13, WHERE_RIGHT = 130, DOT_RIGHT = 140, UNLINK_RIGHT = 152,
            SUMMARY_X = 12, SUMMARY_Y = 148;

    private record Row(UUID player, String name, String dimension, boolean online, boolean connected) {}

    public WirelessControllerPanel(RackScreen screen) {
        super(screen);
    }

    @Override
    protected Identifier background() {
        return BACKGROUND;
    }

    private List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        ValueInput data = data();
        if (data != null) {
            for (ValueInput child : data.childrenListOrEmpty("entries")) {
                child.read("player", UUIDUtil.CODEC).ifPresent(player -> rows.add(new Row(player, child.getStringOr("name", "?"),
                        child.getStringOr("dimension", ""), child.getBooleanOr("online", false), child.getBooleanOr("connected", false))));
            }
        }
        return rows;
    }

    // "Overworld", "The Nether", "The End", or a modded dimension's path.
    private static Component dimension(String id) {
        Identifier dimension = Identifier.tryParse(id);
        if (dimension == null) {
            return Component.literal(id);
        }
        String key = "gui.encodedlogistics.wireless.dimension." + dimension.getNamespace() + "." + dimension.getPath();
        String path = dimension.getPath().replace('_', ' ');
        return Component.translatableWithFallback(key, path.isEmpty() ? id : Character.toUpperCase(path.charAt(0)) + path.substring(1));
    }

    // --- Drawing ---

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.left(), y = screen.top();
        List<Row> rows = rows();
        for (int i = 0; i < Math.min(ROWS, rows.size()); i++) {
            Row row = rows.get(i);
            int rowY = y + LIST_Y + i * ROW_H;
            PlayerInfo info = screen.getMinecraft().getConnection() != null ? screen.getMinecraft().getConnection().getPlayerInfo(row.player()) : null;
            PlayerSkin skin = info != null ? info.getSkin() : DefaultPlayerSkin.get(row.player());
            PlayerFaceExtractor.extractRenderState(graphics, skin, x + LIST_X + 1, rowY + 2, 8);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, row.connected() ? DOT_ON : DOT_OFF, x + DOT_RIGHT - 5, rowY + 4, 5, 5);
            boolean over = PartScreens.over(mouseX, mouseY, x + UNLINK_RIGHT - 9, rowY + 2, 9, 9);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, UNLINK, x + UNLINK_RIGHT - 9, rowY + 2, 9, 9, over ? 0xFFFFFFFF : 0xFFB0B0B0);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<Row> rows = rows();
        for (int i = 0; i < Math.min(ROWS, rows.size()); i++) {
            Row row = rows.get(i);
            int rowY = LIST_Y + i * ROW_H + 3;
            Component where = dimension(row.dimension());
            int whereWidth = Math.min(font().width(where), 70);
            graphics.text(font(), font().plainSubstrByWidth(where.getString(), 70), WHERE_RIGHT - whereWidth, rowY, RackScreen.TEXT_MUTED, false);
            int nameRoom = WHERE_RIGHT - whereWidth - 4 - (LIST_X + 11);
            graphics.text(font(), font().plainSubstrByWidth(row.name(), nameRoom), LIST_X + 11, rowY, row.online() ? RackScreen.TEXT : RackScreen.TEXT_DISABLED,
                    false);
        }
        ValueInput data = data();
        int connected = data != null ? data.getIntOr("connected", 0) : 0;
        graphics.text(font(), Component.translatable("gui.encodedlogistics.wireless.summary", rows.size(), connected), SUMMARY_X, SUMMARY_Y, RackScreen.TEXT,
                false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        List<Row> rows = rows();
        for (int i = 0; i < Math.min(ROWS, rows.size()); i++) {
            int rowY = screen.top() + LIST_Y + i * ROW_H;
            if (PartScreens.over(mouseX, mouseY, screen.left() + UNLINK_RIGHT - 9, rowY + 2, 9, 9)) {
                graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.wireless.unlink"), mouseX, mouseY);
            } else if (PartScreens.over(mouseX, mouseY, screen.left() + DOT_RIGHT - 6, rowY + 3, 7, 7)) {
                graphics.setTooltipForNextFrame(Component.translatable(rows.get(i).connected() ? "gui.encodedlogistics.wireless.connected"
                        : rows.get(i).online() ? "gui.encodedlogistics.wireless.idle" : "gui.encodedlogistics.wireless.away"), mouseX, mouseY);
            }
        }
    }

    // --- Input ---

    @Override
    protected boolean mouseClicked(double x, double y, int button, boolean shift) {
        if (button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        List<Row> rows = rows();
        for (int i = 0; i < Math.min(ROWS, rows.size()); i++) {
            int rowY = LIST_Y + i * ROW_H;
            if (x >= UNLINK_RIGHT - 9 && x < UNLINK_RIGHT && y >= rowY + 2 && y < rowY + 11) {
                send(WirelessControllerDevice.ACTION_UNLINK, i, "");
                return true;
            }
        }
        return false;
    }
}
