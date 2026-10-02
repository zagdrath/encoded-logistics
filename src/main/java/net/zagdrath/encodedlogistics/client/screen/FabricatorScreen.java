/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.FabricatorBlockEntity;
import net.zagdrath.encodedlogistics.menu.FabricatorMenu;

// The Fabricator's screen (screens/fabricator.json): its schematics, the craft's progress arrow, its module slots (the
// ghost module shows in empty ones) and the inventory.
public class FabricatorScreen extends AbstractContainerScreen<FabricatorMenu> {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/fabricator.png");
    private static final Identifier PROGRESS = EncodedLogistics.id("lithography_press/progress"), GHOST_MODULE = EncodedLogistics.id("port/ghost_module");
    private static final int PROGRESS_X = 108, PROGRESS_Y = 35, PROGRESS_W = 24, PROGRESS_H = 17;

    public FabricatorScreen(FabricatorMenu menu, Inventory inventory, Component title) {
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
        int filled = Math.round(PROGRESS_W * menu.progress());
        if (filled > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESS, PROGRESS_W, PROGRESS_H, 0, 0, x + PROGRESS_X, y + PROGRESS_Y, filled, PROGRESS_H);
        }
        for (int i = 0; i < FabricatorBlockEntity.MODULE_SLOTS; i++) {
            if (menu.getSlot(FabricatorBlockEntity.SCHEMATIC_SLOTS + i).getItem().isEmpty()) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, GHOST_MODULE, x + FabricatorMenu.MODULE_X, y + FabricatorMenu.MODULE_Y[i], 16, 16);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, PartScreens.TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, PartScreens.TEXT_MUTED, false);
    }
}
