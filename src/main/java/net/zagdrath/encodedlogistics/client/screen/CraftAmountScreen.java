/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.AccessTerminalMenu;
import net.zagdrath.encodedlogistics.net.CraftRequestPayload;
import net.zagdrath.encodedlogistics.storage.ItemKey;

// How many to craft (screens/craft_amount.json): the item, the amount (typed, or stepped by the +/- buttons), and Next,
// which asks the server for the plan (Enter too). Escape goes back to the terminal.
public class CraftAmountScreen extends Screen {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/craft_amount.png");
    private static final int WIDTH = 176, HEIGHT = 92, ITEM_X = 13, ITEM_Y = 31, FIELD_X = 41, FIELD_Y = 37, FIELD_W = 72;
    private static final int[][] STEPS = { { 1, 40, 18 }, { 10, 66, 18 }, { 64, 92, 18 }, { -1, 40, 54 }, { -10, 66, 54 }, { -64, 92, 54 } };
    private static final int STEP_W = 24, STEP_H = 14, NEXT_X = 124, NEXT_Y = 34, NEXT_W = 44, NEXT_H = 18;

    private final Screen terminal;
    private final AccessTerminalMenu menu;
    private final ItemKey key;
    private @Nullable EditBox amount;
    private int left, top;

    public CraftAmountScreen(Screen terminal, AccessTerminalMenu menu, ItemKey key) {
        super(Component.translatable("gui.encodedlogistics.craft.amount"));
        this.terminal = terminal;
        this.menu = menu;
        this.key = key;
    }

    public int containerId() {
        return menu.containerId;
    }

    public Screen terminal() {
        return terminal;
    }

    public AccessTerminalMenu menu() {
        return menu;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        String value = amount != null ? amount.getValue() : "1";
        amount = new EditBox(font, left + FIELD_X + 4, top + FIELD_Y + 2, FIELD_W - 6, 9, Component.translatable("gui.encodedlogistics.craft.amount"));
        amount.setBordered(false);
        amount.setMaxLength(6);
        amount.setTextColor(PartScreens.TEXT);
        PartScreens.numeric(amount, false);
        amount.setValue(value);
        addRenderableWidget(amount);
        setInitialFocus(amount);
    }

    private long value() {
        try {
            return amount == null || amount.getValue().isEmpty() ? 0 : Long.parseLong(amount.getValue());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private void next() {
        long value = value();
        if (value >= 1) {
            ClientPacketDistributor.sendToServer(new CraftRequestPayload(menu.containerId, key, Math.min(value, CraftRequestPayload.MAX_AMOUNT), -1, false));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top, 0.0F, 0.0F, WIDTH, HEIGHT, 256, 256);
        graphics.item(key.stack(), left + ITEM_X, top + ITEM_Y);
        for (int[] step : STEPS) {
            PartScreens.wideButton(graphics, font, left + step[1], top + step[2], STEP_W, STEP_H, Component.literal((step[0] > 0 ? "+" : "") + step[0]),
                    true, mouseX, mouseY);
        }
        PartScreens.wideButton(graphics, font, left + NEXT_X, top + NEXT_Y, NEXT_W, NEXT_H, Component.translatable("gui.encodedlogistics.craft.next"),
                value() >= 1, mouseX, mouseY);
        graphics.text(font, title, left + 8, top + 5, PartScreens.TEXT, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (PartScreens.over(mouseX, mouseY, left + ITEM_X, top + ITEM_Y, 16, 16)) {
            graphics.setTooltipForNextFrame(font, key.stack(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            for (int[] step : STEPS) {
                if (PartScreens.over(event.x(), event.y(), left + step[1], top + step[2], STEP_W, STEP_H) && amount != null) {
                    long value = Math.clamp(value() + step[0], 1, CraftRequestPayload.MAX_AMOUNT);
                    // From 1, +10 and +64 give round numbers.
                    if (value() == 1 && step[0] > 1) {
                        value = step[0];
                    }
                    amount.setValue(Long.toString(value));
                    return true;
                }
            }
            if (PartScreens.over(event.x(), event.y(), left + NEXT_X, top + NEXT_Y, NEXT_W, NEXT_H)) {
                next();
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation()) {
            next();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(terminal);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
