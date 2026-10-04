/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.encodedlogistics.menu.FabricationTerminalMenu;
import net.zagdrath.encodedlogistics.menu.RackConsoleFabricationMenu;

// The Fabrication Terminal: the terminal kit with its crafting section (screens/fabrication_terminal.json and
// terminal/crafting_section.json) - the grid and result are the menu's slots; this adds the clear button.
public class FabricationTerminalScreen extends AbstractTerminalScreen<FabricationTerminalMenu> {
    public static final String LAYOUT = "fabrication_terminal";
    private static final int CLEAR_SIZE = 9;

    public FabricationTerminalScreen(FabricationTerminalMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, TerminalLayout.load(menu instanceof RackConsoleFabricationMenu ? "rack_console_fabrication" : LAYOUT));
    }

    private FabricationTerminalScreen(FabricationTerminalMenu menu, Inventory inventory, TerminalLayout layout) {
        super(menu, inventory, Component.translatable(layout.titleKey), layout);
    }

    private boolean overClear(double mouseX, double mouseY) {
        int x = leftPos + layout().clearLeft, y = topPos + layout().topHeight + rows() * layout().rowHeight + layout().clearTop;
        return mouseX >= x && mouseX < x + CLEAR_SIZE && mouseY >= y && mouseY < y + CLEAR_SIZE;
    }

    @Override
    protected void extractSection(GuiGraphicsExtractor graphics, int left, int top, int mouseX, int mouseY) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, overClear(mouseX, mouseY) ? layout().clearHover : layout().clear,
                left + layout().clearLeft, top + layout().clearTop, CLEAR_SIZE, CLEAR_SIZE);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (overClear(mouseX, mouseY)) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.terminal.clear_grid"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && overClear(event.x(), event.y())) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, FabricationTerminalMenu.BUTTON_CLEAR);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }
}
