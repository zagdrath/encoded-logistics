/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.RelayAntennaBlockEntity;
import net.zagdrath.encodedlogistics.menu.RelayAntennaMenu;

// The Relay Antenna's screen (screens/relay_antenna.json): its range, four Optical Transceiver slots (a ghost
// transceiver in the empty ones), the players in range with a linked Handheld Terminal (three rows, scroll for more) and
// the inventory.
public class RelayAntennaScreen extends AbstractContainerScreen<RelayAntennaMenu> {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/relay_antenna.png");
    private static final Identifier GHOST = EncodedLogistics.id("relay/ghost_transceiver");
    // The range readout's inset is 8..91 x 17..34, the list's 8..167 x 46..79 (rows of 10 from 48).
    private static final int RANGE_X = 13, RANGE_Y = 22, LINKED_X = 8, LINKED_Y = 37, LIST_X = 12, LIST_Y = 48, ROWS = 3, ROW_HEIGHT = 10,
            LIST_W = 152;

    private int scroll;

    public RelayAntennaScreen(RelayAntennaMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 176);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 83;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        for (int slot = 0; slot < RelayAntennaBlockEntity.SLOTS; slot++) {
            if (menu.getSlot(slot).getItem().isEmpty()) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, GHOST, x + RelayAntennaMenu.SLOT_X[slot], y + RelayAntennaMenu.SLOT_Y, 16, 16);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, PartScreens.TEXT, false);
        graphics.text(font, Component.translatable("gui.encodedlogistics.relay.range", menu.range()), RANGE_X, RANGE_Y, PartScreens.ACCENT, false);
        graphics.text(font, Component.translatable("gui.encodedlogistics.relay.linked"), LINKED_X, LINKED_Y, PartScreens.TEXT_MUTED, false);
        List<RelayAntennaBlockEntity.Linked> linked = menu.linked();
        scroll = Math.clamp(scroll, 0, Math.max(0, linked.size() - ROWS));
        for (int row = 0; row < ROWS && scroll + row < linked.size(); row++) {
            RelayAntennaBlockEntity.Linked entry = linked.get(scroll + row);
            Component text = Component.translatable("gui.encodedlogistics.relay.entry", entry.name(), entry.distance());
            graphics.text(font, font.plainSubstrByWidth(text.getString(), LIST_W), LIST_X, LIST_Y + row * ROW_HEIGHT + 1, PartScreens.TEXT, false);
        }
        if (linked.isEmpty()) {
            graphics.text(font, Component.translatable("gui.encodedlogistics.relay.none"), LIST_X, LIST_Y + 1, PartScreens.TEXT_DISABLED, false);
        }
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, PartScreens.TEXT_MUTED, false);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (PartScreens.over(mouseX, mouseY, leftPos + LIST_X, topPos + LIST_Y, LIST_W, ROWS * ROW_HEIGHT)) {
            scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, menu.linked().size() - ROWS));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
