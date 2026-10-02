/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.item.HandheldLinkState;
import net.zagdrath.encodedlogistics.menu.HandheldTerminalMenu;

// The Handheld Terminal: the terminal kit as screens/handheld_terminal.json lays it out, plus a side panel on its right
// (gui/terminal/handheld_panel.png): the battery's energy bar, the link light (linked, out of range, not linked) and
// signal bars for how far into its antenna's range the player is. Offline (out of range, or a flat battery) the grid
// says why.
public class HandheldTerminalScreen extends AbstractTerminalScreen<HandheldTerminalMenu> {
    public static final String LAYOUT = "handheld_terminal";
    private static final Identifier PANEL = EncodedLogistics.id("textures/gui/terminal/handheld_panel.png");
    private static final Identifier ENERGY_BAR = EncodedLogistics.id("controller/energy_bar");
    private static final Identifier[] SIGNAL = { EncodedLogistics.id("handheld/signal_0"), EncodedLogistics.id("handheld/signal_1"),
            EncodedLogistics.id("handheld/signal_2"), EncodedLogistics.id("handheld/signal_3"), EncodedLogistics.id("handheld/signal_4") };
    private static final int PANEL_W = 26, PANEL_H = 96, PANEL_TOP = 4, BAR_X = 8, BAR_Y = 9, BAR_W = 10, BAR_H = 50, LIGHT_X = 10, LIGHT_Y = 70,
            SIGNAL_X = 7, SIGNAL_Y = 78;

    public HandheldTerminalScreen(HandheldTerminalMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, TerminalLayout.load(LAYOUT));
    }

    private HandheldTerminalScreen(HandheldTerminalMenu menu, Inventory inventory, TerminalLayout layout) {
        super(menu, inventory, Component.translatable(layout.titleKey), layout);
    }

    private int panelX() {
        return leftPos + imageWidth;
    }

    private int panelY() {
        return topPos + PANEL_TOP;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = panelX(), y = panelY();
        graphics.blit(RenderPipelines.GUI_TEXTURED, PANEL, x, y, 0.0F, 0.0F, PANEL_W, PANEL_H, 64, 128);
        int height = (int) Math.round((double) menu.energy() * BAR_H / menu.capacity());
        if (height > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ENERGY_BAR, BAR_W, BAR_H, 0, BAR_H - height, x + BAR_X, y + BAR_Y + BAR_H - height, BAR_W,
                    height);
        }
        Identifier light = EncodedLogistics.id("handheld/link_" + menu.linkState().getSerializedName());
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, light, x + LIGHT_X, y + LIGHT_Y, 6, 6);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SIGNAL[Math.clamp(menu.signal(), 0, 4)], x + SIGNAL_X, y + SIGNAL_Y, 12, 10);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int x = panelX(), y = panelY();
        if (PartScreens.over(mouseX, mouseY, x + BAR_X, y + BAR_Y, BAR_W, BAR_H)) {
            graphics.setTooltipForNextFrame(Component.translatable("tooltip.encodedlogistics.handheld.energy",
                    String.format(Locale.ROOT, "%,d", menu.energy()), String.format(Locale.ROOT, "%,d", menu.capacity())), mouseX, mouseY);
        } else if (PartScreens.over(mouseX, mouseY, x + SIGNAL_X - 2, y + LIGHT_Y - 2, 16, 20)) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.handheld." + menu.linkState().getSerializedName()), mouseX,
                    mouseY);
        }
    }

    @Override
    protected Component offlineMessage() {
        if (menu.linkState() != HandheldLinkState.LINKED) {
            return Component.translatable("gui.encodedlogistics.handheld." + menu.linkState().getSerializedName());
        }
        return menu.energy() <= 0 ? Component.translatable("message.encodedlogistics.handheld.empty") : super.offlineMessage();
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return super.hasClickedOutside(mouseX, mouseY, left, top) && !PartScreens.over(mouseX, mouseY, panelX(), panelY(), PANEL_W, PANEL_H);
    }
}
