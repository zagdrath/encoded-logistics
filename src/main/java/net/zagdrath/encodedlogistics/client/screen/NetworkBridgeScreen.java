/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.NetworkBridgeBlockEntity;
import net.zagdrath.encodedlogistics.menu.NetworkBridgeMenu;

// The Network Bridge's screen (screens/network_bridge.json): a status light and line (linked, not linked, partner
// offline), the partner's position and dimension, and a 32-segment meter of the lanes crossing.
public class NetworkBridgeScreen extends AbstractContainerScreen<NetworkBridgeMenu> {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/network_bridge.png");
    private static final Identifier LANE_ON = EncodedLogistics.id("bridge/lane_on");
    private static final Identifier LINKED = EncodedLogistics.id("bridge/status_linked"), UNLINKED = EncodedLogistics.id("bridge/status_unlinked"),
            OFFLINE = EncodedLogistics.id("bridge/status_offline");
    private static final int LIGHT_X = 12, LIGHT_Y = 23, STATUS_X = 22, STATUS_Y = 22, PARTNER_X = 12, PARTNER_Y = 34, DIMENSION_Y = 44,
            METER_X = 9, METER_Y = 63, SEGMENTS = 32, PITCH = 5, LANES_X = 8, LANES_Y = 76;

    public NetworkBridgeScreen(NetworkBridgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 96);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        Identifier light = switch (menu.status()) {
            case LINKED -> LINKED;
            case UNLINKED -> UNLINKED;
            case PARTNER_OFFLINE -> OFFLINE;
        };
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, light, x + LIGHT_X, y + LIGHT_Y, 6, 6);
        // One segment per 1/32 of the lanes the link carries, rounded up so any use shows.
        int lit = menu.lanesUsed() <= 0 ? 0 : Math.min(SEGMENTS, (int) Math.ceil((double) menu.lanesUsed() * SEGMENTS / menu.lanes()));
        for (int segment = 0; segment < lit; segment++) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LANE_ON, x + METER_X + segment * PITCH, y + METER_Y, 2, 8);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, PartScreens.TEXT, false);
        NetworkBridgeBlockEntity.LinkStatus status = menu.status();
        String key = switch (status) {
            case LINKED -> "gui.encodedlogistics.bridge.status.linked";
            case UNLINKED -> "gui.encodedlogistics.bridge.status.unlinked";
            case PARTNER_OFFLINE -> "gui.encodedlogistics.bridge.status.offline";
        };
        graphics.text(font, Component.translatable(key), STATUS_X, STATUS_Y, status == NetworkBridgeBlockEntity.LinkStatus.PARTNER_OFFLINE
                ? PartScreens.ERROR : PartScreens.TEXT, false);
        if (menu.partner().isPresent()) {
            GlobalPos partner = menu.partner().get();
            graphics.text(font, Component.translatable("gui.encodedlogistics.bridge.partner", partner.pos().getX(), partner.pos().getY(),
                    partner.pos().getZ()), PARTNER_X, PARTNER_Y, PartScreens.TEXT_MUTED, false);
            graphics.text(font, Component.literal(partner.dimension().identifier().toString()), PARTNER_X, DIMENSION_Y, PartScreens.TEXT_MUTED, false);
        } else {
            graphics.text(font, Component.translatable("gui.encodedlogistics.bridge.hint"), PARTNER_X, PARTNER_Y, PartScreens.TEXT_DISABLED, false);
        }
        graphics.text(font, Component.translatable("gui.encodedlogistics.bridge.lanes", menu.lanesUsed(), menu.lanes()), LANES_X, LANES_Y,
                PartScreens.TEXT_MUTED, false);
    }
}
