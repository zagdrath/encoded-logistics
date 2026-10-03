/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.ThresholdSensorMenu;
import net.zagdrath.encodedlogistics.net.MenuValuePayload;

// The Threshold Sensor's screen (screens/threshold_sensor.json): the ghost item, the threshold (type it, Enter to set),
// the comparison and whether it's emitting.
public class ThresholdSensorScreen extends AbstractContainerScreen<ThresholdSensorMenu> {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/threshold_sensor.png");
    private static final Identifier FIELD = EncodedLogistics.id("threshold_sensor/number_field"),
            FIELD_FOCUSED = EncodedLogistics.id("threshold_sensor/number_field_focused");
    private static final Identifier[] MODES = { EncodedLogistics.id("threshold_sensor/compare_above"),
            EncodedLogistics.id("threshold_sensor/compare_below"), EncodedLogistics.id("threshold_sensor/compare_equal") };
    private static final String[] MODE_KEYS = { "above", "below", "equal" };
    private static final int FIELD_X = 52, FIELD_Y = 35, FIELD_W = 70, FIELD_H = 12, MODE_X = 130, MODE_Y = 31;

    private @Nullable EditBox threshold;
    private int shown = -1;

    public ThresholdSensorScreen(ThresholdSensorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 72;
    }

    @Override
    protected void init() {
        super.init();
        threshold = new EditBox(font, leftPos + FIELD_X + 4, topPos + FIELD_Y + 2, FIELD_W - 6, 9,
                Component.translatable("gui.encodedlogistics.sensor.threshold"));
        threshold.setBordered(false);
        threshold.setMaxLength(10);
        threshold.setTextColor(PartScreens.TEXT);
        PartScreens.numeric(threshold, false);
        threshold.setValue(Integer.toString(menu.threshold()));
        shown = menu.threshold();
        addRenderableWidget(threshold);
    }

    private void submit() {
        if (threshold == null) {
            return;
        }
        try {
            long value = Long.parseLong(threshold.getValue());
            int clamped = (int) Math.max(0, Math.min(Integer.MAX_VALUE, value));
            ClientPacketDistributor.sendToServer(new MenuValuePayload(menu.containerId, ThresholdSensorMenu.VALUE_THRESHOLD, clamped));
        } catch (NumberFormatException e) {
            threshold.setValue(Integer.toString(menu.threshold()));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (threshold != null && !threshold.isFocused() && menu.threshold() != shown) {
            shown = menu.threshold();
            threshold.setValue(Integer.toString(shown));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, threshold != null && threshold.isFocused() ? FIELD_FOCUSED : FIELD, x + FIELD_X,
                y + FIELD_Y, FIELD_W, FIELD_H);
        PartScreens.button(graphics, x + MODE_X, y + MODE_Y, MODES[Math.min(menu.mode(), 2)], mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, PartScreens.TEXT, false);
        graphics.text(font, Component.translatable("gui.encodedlogistics.sensor.item"), 26, 22, PartScreens.TEXT_MUTED, false);
        graphics.text(font, Component.translatable("gui.encodedlogistics.sensor.threshold"), 52, 22, PartScreens.TEXT_MUTED, false);
        boolean emitting = menu.emitting();
        PartScreens.status(graphics, font, PartScreens.STATUS_RIGHT, PartScreens.STATUS_Y,
                Component.translatable(emitting ? "gui.encodedlogistics.sensor.state.on" : "gui.encodedlogistics.sensor.state.off"),
                emitting ? PartScreens.Status.ONLINE : PartScreens.Status.IDLE);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, PartScreens.TEXT_MUTED, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (PartScreens.over(mouseX, mouseY, leftPos + MODE_X, topPos + MODE_Y, 18, 18)) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.sensor." + MODE_KEYS[Math.min(menu.mode(), 2)]),
                    mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && PartScreens.over(event.x(), event.y(), leftPos + MODE_X, topPos + MODE_Y, 18, 18)) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, ThresholdSensorMenu.BUTTON_MODE);
            return true;
        }
        boolean wasFocused = threshold != null && threshold.isFocused();
        boolean handled = super.mouseClicked(event, doubleClick);
        if (wasFocused && threshold != null && !threshold.isFocused()) {
            submit();
        }
        return handled;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (threshold != null && threshold.isFocused()) {
            if (event.isConfirmation()) {
                submit();
                threshold.setFocused(false);
                return true;
            }
            if (!event.isEscape()) {
                return threshold.keyPressed(event) || threshold.canConsumeInput() || super.keyPressed(event);
            }
        }
        return super.keyPressed(event);
    }
}
