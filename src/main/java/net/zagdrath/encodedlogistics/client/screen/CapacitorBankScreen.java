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
import net.zagdrath.encodedlogistics.menu.CapacitorBankMenu;
import net.zagdrath.encodedlogistics.net.CapacitorBankPayload;

// The Capacitor Bank screen: the energy bar, the fill percentage, and Stored / Input / Output / Capacity. Layout and
// colours are those of screens/capacitor_bank.json and screens/common/palette.json.
public class CapacitorBankScreen extends AbstractContainerScreen<CapacitorBankMenu> {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/capacitor_bank.png");
    private static final Identifier ENERGY_BAR = EncodedLogistics.id("controller/energy_bar");

    // palette.json, plus the orange used for energy going out.
    private static final int TEXT = 0xFFF0F0F0, TEXT_MUTED = 0xFFB4B4B4, ACCENT = 0xFF00D992, OUTPUT = 0xFFFFA040;

    private static final int WIDTH = 176, HEIGHT = 80;
    private static final int GAUGE_X = 9, GAUGE_Y = 19, GAUGE_W = 10, GAUGE_H = 50;
    private static final int FILL_RIGHT = 168;
    private static final int LABEL_X = 28, VALUE_X = 84, STORED_Y = 22, INPUT_Y = 35, OUTPUT_Y = 46, CAPACITY_Y = 57;

    public CapacitorBankScreen(CapacitorBankMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
    }

    private CapacitorBankPayload figures() {
        return CapacitorBankPayload.forMenu(menu.containerId);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        CapacitorBankPayload figures = figures();
        // Drawn bottom-up and cropped from the bottom, like the controller's gauge.
        int height = figures.capacity() <= 0 ? 0 : (int) Math.min(GAUGE_H, Math.round((double) figures.stored() * GAUGE_H / figures.capacity()));
        if (height > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ENERGY_BAR, GAUGE_W, GAUGE_H, 0, GAUGE_H - height,
                    leftPos + GAUGE_X, topPos + GAUGE_Y + GAUGE_H - height, GAUGE_W, height);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        CapacitorBankPayload figures = figures();
        graphics.text(font, title, titleLabelX, titleLabelY, TEXT, false);
        int percent = figures.capacity() <= 0 ? 0 : (int) (figures.stored() * 100 / figures.capacity());
        Component fill = Component.translatable("gui.encodedlogistics.capacitor_bank.fill", percent);
        graphics.text(font, fill, FILL_RIGHT - font.width(fill), titleLabelY, ACCENT, false);

        row(graphics, "stored", Component.literal(String.format(Locale.ROOT, "%,d FE", figures.stored())), TEXT, STORED_Y);
        row(graphics, "input", Component.literal("+" + rate(figures.input())), ACCENT, INPUT_Y);
        row(graphics, "output", Component.literal("-" + rate(figures.output())), OUTPUT, OUTPUT_Y);
        row(graphics, "capacity", Component.literal(String.format(Locale.ROOT, "%,d FE", figures.capacity())), TEXT, CAPACITY_Y);
    }

    private void row(GuiGraphicsExtractor graphics, String key, Component value, int color, int y) {
        graphics.text(font, Component.translatable("gui.encodedlogistics.capacitor_bank." + key), LABEL_X, y, TEXT_MUTED, false);
        graphics.text(font, value, VALUE_X, y, color, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        double x = mouseX - leftPos, y = mouseY - topPos;
        if (x >= GAUGE_X && x < GAUGE_X + GAUGE_W && y >= GAUGE_Y && y < GAUGE_Y + GAUGE_H) {
            CapacitorBankPayload figures = figures();
            graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.encodedlogistics.capacitor_bank.stored"),
                    Component.literal(String.format(Locale.ROOT, "%,d / %,d FE", figures.stored(), figures.capacity())).withColor(TEXT_MUTED)),
                    mouseX, mouseY);
        }
    }

    // "480 FE/t", "12.5 FE/t": whole numbers without a decimal.
    private static String rate(double value) {
        long whole = Math.round(value);
        String number = Math.abs(value - whole) < 0.05 ? String.format(Locale.ROOT, "%,d", whole) : String.format(Locale.ROOT, "%,.1f", value);
        return number + " FE/t";
    }
}
