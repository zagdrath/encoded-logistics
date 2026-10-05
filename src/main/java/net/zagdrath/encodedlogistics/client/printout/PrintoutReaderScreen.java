/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.printout;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.item.PrintoutItem;
import net.zagdrath.encodedlogistics.midrange.Printout;

// A Printout's reader (HANDOFF 9; previews/printout_reader): a dark panel with its title, the page at the largest scale
// that fits (a whole multiple, or 1/2, 1/3... when even 1x doesn't), page arrows and "Page n of m". Page Up / Page Down, the
// arrows' keys and the scroll wheel turn pages; Esc closes.
public class PrintoutReaderScreen extends Screen {
    private static final Identifier PANEL = EncodedLogistics.id("hud/panel"), BUTTON = EncodedLogistics.id("terminal/button"),
            BUTTON_HOVER = EncodedLogistics.id("terminal/button_hover");
    private static final int PAD = 6, TITLE_H = 14, FOOT_H = 22, BUTTON_SIZE = 18;
    private static final int TEXT = 0xFFF0F0F0, MUTED = 0xFFB4B4B4;

    private final Printout printout;
    private int page;
    // Laid out in init: the page's place and size, the panel's.
    private int pageX, pageY, pageW, pageH, panelX, panelY, panelW, panelH;

    public PrintoutReaderScreen(Printout printout) {
        super(Component.literal(printout.title()));
        this.printout = printout;
    }

    public static void open(ItemStack stack) {
        Printout printout = PrintoutItem.printout(stack);
        if (printout != null) {
            Minecraft.getInstance().gui.setScreen(new PrintoutReaderScreen(printout));
        }
    }

    @Override
    protected void init() {
        int roomW = width - 2 * PAD - 8, roomH = height - TITLE_H - FOOT_H - 2 * PAD - 8;
        // The largest whole scale that fits, or 1/2, 1/3... when even 1x doesn't.
        int whole = Math.min(roomW / PrintoutPages.WIDTH, roomH / PrintoutPages.HEIGHT);
        float scale;
        if (whole >= 1) {
            scale = whole;
        } else {
            int n = 2;
            while (n < 16 && (PrintoutPages.WIDTH / n > roomW || PrintoutPages.HEIGHT / n > roomH)) {
                n++;
            }
            scale = 1.0F / n;
        }
        pageW = Math.round(PrintoutPages.WIDTH * scale);
        pageH = Math.round(PrintoutPages.HEIGHT * scale);
        panelW = pageW + 2 * PAD;
        panelH = pageH + TITLE_H + FOOT_H + PAD;
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
        pageX = panelX + PAD;
        pageY = panelY + TITLE_H;
    }

    private void turn(int by) {
        int to = Math.clamp(page + by, 0, Math.max(0, printout.pages().size() - 1));
        if (to != page) {
            page = to;
            minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                    net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.0F));
        }
    }

    private boolean over(double mouseX, double mouseY, int x, int y) {
        return mouseX >= x && mouseX < x + BUTTON_SIZE && mouseY >= y && mouseY < y + BUTTON_SIZE;
    }

    private int footY() {
        return pageY + pageH + 2;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, panelX, panelY, panelW, panelH);
        graphics.text(font, title, panelX + PAD, panelY + 4, TEXT, false);
        Identifier texture = PrintoutPages.texture(printout, page);
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, pageX, pageY, 0, 0, pageW, pageH, PrintoutPages.WIDTH, PrintoutPages.HEIGHT,
                PrintoutPages.WIDTH, PrintoutPages.HEIGHT);
        int left = panelX + PAD, right = panelX + panelW - PAD - BUTTON_SIZE, y = footY();
        arrow(graphics, left, y, "<", page > 0, mouseX, mouseY);
        arrow(graphics, right, y, ">", page < printout.pages().size() - 1, mouseX, mouseY);
        Component label = Component.translatable("gui.encodedlogistics.printout.page", page + 1, printout.pages().size());
        graphics.text(font, label, panelX + (panelW - font.width(label)) / 2, y + 5, MUTED, false);
    }

    private void arrow(GuiGraphicsExtractor graphics, int x, int y, String glyph, boolean enabled, int mouseX, int mouseY) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, enabled && over(mouseX, mouseY, x, y) ? BUTTON_HOVER : BUTTON, x, y, BUTTON_SIZE, BUTTON_SIZE);
        graphics.text(font, glyph, x + (BUTTON_SIZE - font.width(glyph)) / 2, y + 5, enabled ? TEXT : MUTED, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int y = footY();
        if (over(event.x(), event.y(), panelX + PAD, y)) {
            turn(-1);
            return true;
        }
        if (over(event.x(), event.y(), panelX + panelW - PAD - BUTTON_SIZE, y)) {
            turn(1);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0) {
            turn(scrollY > 0 ? -1 : 1);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        int key = event.key();
        if (key == InputConstants.KEY_PAGEUP || event.isLeft() || event.isUp()) {
            turn(-1);
            return true;
        }
        if (key == InputConstants.KEY_PAGEDOWN || event.isRight() || event.isDown()) {
            turn(1);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
