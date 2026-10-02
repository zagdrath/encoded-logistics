/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.LithographyPressMenu;

// The Lithography Press screen: energy bar, photomask / wafer / additive slots with ghost icons when empty, the violet
// progress arrow and the output. Layout and colours are those of screens/lithography_press.json and palette.json.
public class LithographyPressScreen extends AbstractContainerScreen<LithographyPressMenu> {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/lithography_press.png");
    private static final Identifier ENERGY_BAR = EncodedLogistics.id("controller/energy_bar");
    private static final Identifier PROGRESS = EncodedLogistics.id("lithography_press/progress");
    private static final Identifier[] GHOSTS = { EncodedLogistics.id("lithography_press/ghost_photomask"),
            EncodedLogistics.id("lithography_press/ghost_wafer"), EncodedLogistics.id("lithography_press/ghost_additive") };

    private static final int TEXT = 0xFFF0F0F0, TEXT_MUTED = 0xFFB4B4B4;
    private static final int GAUGE_X = 9, GAUGE_Y = 19, GAUGE_W = 10, GAUGE_H = 50, ARROW_X = 70, ARROW_Y = 36, ARROW_W = 24, ARROW_H = 17;

    public LithographyPressScreen(LithographyPressMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 72;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        int capacity = menu.capacity();
        int height = capacity <= 0 ? 0 : (int) Math.min(GAUGE_H, Math.round((double) menu.energy() * GAUGE_H / capacity));
        if (height > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ENERGY_BAR, GAUGE_W, GAUGE_H, 0, GAUGE_H - height, x + GAUGE_X,
                    y + GAUGE_Y + GAUGE_H - height, GAUGE_W, height);
        }
        int width = Math.round(menu.progress() * ARROW_W);
        if (width > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESS, ARROW_W, ARROW_H, 0, 0, x + ARROW_X, y + ARROW_Y, width, ARROW_H);
        }
        for (int slot = 0; slot < GHOSTS.length; slot++) {
            if (!menu.getSlot(slot).hasItem()) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, GHOSTS[slot], x + menu.getSlot(slot).x, y + menu.getSlot(slot).y, 16, 16);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_MUTED, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        double x = mouseX - leftPos, y = mouseY - topPos;
        if (x >= GAUGE_X && x < GAUGE_X + GAUGE_W && y >= GAUGE_Y && y < GAUGE_Y + GAUGE_H) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.encodedlogistics.lithography_press.energy",
                    String.format(Locale.ROOT, "%,d", menu.energy()), String.format(Locale.ROOT, "%,d", menu.capacity()))), mouseX, mouseY);
        } else if (x >= ARROW_X && x < ARROW_X + ARROW_W && y >= ARROW_Y && y < ARROW_Y + ARROW_H) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.encodedlogistics.lithography_press.progress",
                    Math.round(menu.progress() * 100))), mouseX, mouseY);
        }
    }
}
