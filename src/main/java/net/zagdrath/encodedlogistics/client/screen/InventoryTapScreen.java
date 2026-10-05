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
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.client.ResourceRender;
import net.zagdrath.encodedlogistics.menu.InventoryTapMenu;
import net.zagdrath.encodedlogistics.net.MenuValuePayload;
import net.zagdrath.encodedlogistics.part.InventoryTapPart;

// The Inventory Tap's screen (screens/inventory_tap.json): priority (type it, Enter to set; or the steppers), access mode,
// the 3x3 ghost filter and the inventory.
public class InventoryTapScreen extends AbstractContainerScreen<InventoryTapMenu> {
    private final GhostPicker picker = new GhostPicker();

    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/inventory_tap.png");
    private static final Identifier FIELD = EncodedLogistics.id("inventory_tap/number_field"),
            FIELD_FOCUSED = EncodedLogistics.id("inventory_tap/number_field_focused");
    private static final Identifier UP = EncodedLogistics.id("inventory_tap/step_up"), UP_HOVER = EncodedLogistics.id("inventory_tap/step_up_hover");
    private static final Identifier DOWN = EncodedLogistics.id("inventory_tap/step_down"),
            DOWN_HOVER = EncodedLogistics.id("inventory_tap/step_down_hover");
    private static final Identifier[] ACCESS = { EncodedLogistics.id("inventory_tap/access_read_write"), EncodedLogistics.id("inventory_tap/access_read"),
            EncodedLogistics.id("inventory_tap/access_write") };
    private static final String[] ACCESS_KEYS = { "read_write", "read", "write" };
    private static final int FIELD_X = 8, FIELD_Y = 31, FIELD_W = 36, FIELD_H = 12, STEP_X = 46, UP_Y = 31, DOWN_Y = 37, STEP_W = 9, STEP_H = 6;
    private static final int ACCESS_X = 8, ACCESS_Y = 56;

    private @Nullable EditBox priority;
    private int shown = Integer.MIN_VALUE;

    public InventoryTapScreen(InventoryTapMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 176);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 82;
    }

    @Override
    protected void init() {
        super.init();
        priority = new EditBox(font, leftPos + FIELD_X + 4, topPos + FIELD_Y + 2, FIELD_W - 6, 9, Component.translatable("gui.encodedlogistics.tap.priority"));
        priority.setBordered(false);
        priority.setMaxLength(4);
        priority.setTextColor(PartScreens.TEXT);
        PartScreens.numeric(priority, true);
        priority.setValue(Integer.toString(menu.priority()));
        shown = menu.priority();
        addRenderableWidget(priority);
    }

    // Sends the typed priority.
    private void submit() {
        if (priority == null) {
            return;
        }
        try {
            int value = Math.max(InventoryTapPart.MIN_PRIORITY, Math.min(InventoryTapPart.MAX_PRIORITY, Integer.parseInt(priority.getValue())));
            ClientPacketDistributor.sendToServer(new MenuValuePayload(menu.containerId, InventoryTapMenu.VALUE_PRIORITY, value));
        } catch (NumberFormatException e) {
            priority.setValue(Integer.toString(menu.priority()));
        }
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // Follows the server's value unless it's being typed.
        if (priority != null && !priority.isFocused() && menu.priority() != shown) {
            shown = menu.priority();
            priority.setValue(Integer.toString(shown));
        }
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, priority != null && priority.isFocused() ? FIELD_FOCUSED : FIELD, x + FIELD_X, y + FIELD_Y,
                FIELD_W, FIELD_H);
        boolean overUp = PartScreens.over(mouseX, mouseY, x + STEP_X, y + UP_Y, STEP_W, STEP_H);
        boolean overDown = PartScreens.over(mouseX, mouseY, x + STEP_X, y + DOWN_Y, STEP_W, STEP_H);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, overUp ? UP_HOVER : UP, x + STEP_X, y + UP_Y, STEP_W, STEP_H);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, overDown ? DOWN_HOVER : DOWN, x + STEP_X, y + DOWN_Y, STEP_W, STEP_H);
        PartScreens.button(graphics, x + ACCESS_X, y + ACCESS_Y, ACCESS[Math.min(menu.access(), 2)], mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, PartScreens.TEXT, false);
        graphics.text(font, Component.translatable("gui.encodedlogistics.tap.priority"), 8, 21, PartScreens.TEXT_MUTED, false);
        graphics.text(font, Component.translatable("gui.encodedlogistics.tap." + ACCESS_KEYS[Math.min(menu.access(), 2)]), 30, 61,
                PartScreens.TEXT_MUTED, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, PartScreens.TEXT_MUTED, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (picker.isOpen()) {
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
        if (PartScreens.over(mouseX, mouseY, leftPos + ACCESS_X, topPos + ACCESS_Y, 18, 18)) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.tap.access"), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        // Right-clicking an empty filter entry with an empty hand: pick a fluid or gas from a list.
        if (picker.mouseClicked(menu, hoveredSlot, event, null, width, height)) {
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (PartScreens.over(event.x(), event.y(), leftPos + STEP_X, topPos + UP_Y, STEP_W, STEP_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, InventoryTapMenu.BUTTON_UP);
                return true;
            }
            if (PartScreens.over(event.x(), event.y(), leftPos + STEP_X, topPos + DOWN_Y, STEP_W, STEP_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, InventoryTapMenu.BUTTON_DOWN);
                return true;
            }
            if (PartScreens.over(event.x(), event.y(), leftPos + ACCESS_X, topPos + ACCESS_Y, 18, 18)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, InventoryTapMenu.BUTTON_ACCESS);
                return true;
            }
        }
        boolean wasFocused = priority != null && priority.isFocused();
        boolean handled = super.mouseClicked(event, doubleClick);
        if (wasFocused && priority != null && !priority.isFocused()) {
            submit();
        }
        return handled;
    }

    // Enter sets the priority; while typing, keys go to the field (so "e" doesn't close the screen).
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (picker.keyPressed(event)) {
            return true;
        }
        if (priority != null && priority.isFocused()) {
            if (event.isConfirmation()) {
                submit();
                priority.setFocused(false);
                return true;
            }
            if (!event.isEscape()) {
                return priority.keyPressed(event) || priority.canConsumeInput() || super.keyPressed(event);
            }
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return picker.mouseScrolled(scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        picker.extract(graphics, font, mouseX, mouseY);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return picker.charTyped(event) || super.charTyped(event);
    }

    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        if (!ResourceRender.entrySlot(graphics, font, slot)) {
            super.extractSlot(graphics, slot, mouseX, mouseY);
        }
    }
}
