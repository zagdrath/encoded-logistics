/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.DeployerPlaneMenu;

// The Deployer Plane's screen (screens/deployer_plane.json): the place / drop mode button, the 3x3 ghost filter of what
// it deploys, and the inventory.
public class DeployerPlaneScreen extends AbstractContainerScreen<DeployerPlaneMenu> {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/deployer_plane.png");
    private static final Identifier PLACE = EncodedLogistics.id("deployer/mode_place"), DROP = EncodedLogistics.id("deployer/mode_drop");

    public DeployerPlaneScreen(DeployerPlaneMenu menu, Inventory inventory, Component title) {
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
        PartScreens.button(graphics, x + DeployerPlaneMenu.MODE_X, y + DeployerPlaneMenu.MODE_Y, menu.dropMode() ? DROP : PLACE, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, PartScreens.TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, PartScreens.TEXT_MUTED, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (PartScreens.over(mouseX, mouseY, leftPos + DeployerPlaneMenu.MODE_X, topPos + DeployerPlaneMenu.MODE_Y, 18, 18)) {
            graphics.setComponentTooltipForNextFrame(font, List.of(
                    Component.translatable(menu.dropMode() ? "gui.encodedlogistics.deployer.mode.drop" : "gui.encodedlogistics.deployer.mode.place"),
                    Component.translatable(menu.dropMode() ? "gui.encodedlogistics.deployer.mode.drop.info" : "gui.encodedlogistics.deployer.mode.place.info")
                            .withColor(PartScreens.TEXT_MUTED)), mouseX, mouseY);
        } else if (PartScreens.over(mouseX, mouseY, leftPos + DeployerPlaneMenu.FILTER_X, topPos + DeployerPlaneMenu.FILTER_Y, 54, 54)
                && hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.encodedlogistics.deployer.filter"),
                    Component.translatable("gui.encodedlogistics.deployer.filter.info").withColor(PartScreens.TEXT_MUTED)), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && PartScreens.over(event.x(), event.y(), leftPos + DeployerPlaneMenu.MODE_X, topPos + DeployerPlaneMenu.MODE_Y, 18, 18)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, DeployerPlaneMenu.BUTTON_MODE);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
