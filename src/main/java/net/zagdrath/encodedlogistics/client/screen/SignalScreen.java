/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.List;
import java.util.Optional;

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
import net.zagdrath.encodedlogistics.net.SignalConfigPayload;
import net.zagdrath.encodedlogistics.signal.SignalBlockEntity;
import net.zagdrath.encodedlogistics.signal.SignalMenu;

// A Cage Light's, Alarm Strobe's or Speaker's settings (signals handoff 1), on the Firewall panel's layout and texture:
// the title with the device name; the field row: Status; the list: the device's rows (label muted at x 12, value
// right-aligned at 164, 13 apart) - click one to cycle it (right-click back, shift by one step), or, a text row (a URL,
// notes, the device name), to edit it in the bottom field (Enter sets it, Esc leaves it); and at the bottom right the
// action (a Speaker's Play / Stop). Everything shown is the device's synced state; changes go to the server
// (SignalConfigPayload), which needs the Firewall's build permission.
public class SignalScreen extends AbstractContainerScreen<SignalMenu> {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/rack/firewall.png");
    private static final int STATUS_Y = 18;
    private static final int LIST_X = 10, LIST_Y = 38, LIST_W = 156, ROWS = 8, ROW_H = 13, LABEL_X = 12, VALUE_RIGHT = 164;
    private static final int FIELD_X = 10, FIELD_Y = 151, FIELD_W = 106, ACTION_X = 120, ACTION_Y = 148, ACTION_W = 48, ACTION_H = 14;

    private @Nullable EditBox field;
    // The row the bottom field is editing, or null.
    private @Nullable String editing;

    public SignalScreen(SignalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 168);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
    }

    private @Nullable SignalBlockEntity device() {
        return menu.device();
    }

    @Override
    protected void init() {
        super.init();
        field = new EditBox(font, leftPos + FIELD_X + 3, topPos + FIELD_Y + 1, FIELD_W - 6, 9, Component.translatable("gui.encodedlogistics.signal.edit"));
        field.setBordered(false);
        field.setMaxLength(256);
        field.setTextColor(PartScreens.TEXT);
        field.setVisible(false);
        addRenderableWidget(field);
    }

    // --- Drawing ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        SignalBlockEntity device = device();
        if (device == null) {
            return;
        }
        List<SignalBlockEntity.Row> rows = device.rows();
        for (int i = 0; i < ROWS && i < rows.size(); i++) {
            SignalBlockEntity.Row row = rows.get(i);
            int rowY = topPos + LIST_Y + i * ROW_H;
            boolean selected = row.key().equals(editing);
            if (selected || row.editable() && PartScreens.over(mouseX, mouseY, leftPos + LIST_X, rowY, LIST_W, ROW_H)) {
                graphics.fill(leftPos + LIST_X, rowY, leftPos + LIST_X + LIST_W, rowY + ROW_H - 1, selected ? 0x3000D992 : 0x18FFFFFF);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        SignalBlockEntity device = device();
        Component heading = device == null || device.deviceName().isEmpty() ? title
                : Component.translatable("gui.encodedlogistics.signal.title", title, device.deviceName());
        graphics.text(font, heading, titleLabelX, titleLabelY, PartScreens.TEXT, false);
        if (device == null) {
            return;
        }
        graphics.text(font, Component.translatable("gui.encodedlogistics.signal.status"), LABEL_X, STATUS_Y + 3, PartScreens.TEXT_MUTED, false);
        PartScreens.status(graphics, font, VALUE_RIGHT, STATUS_Y + 3, device.statusText(), switch (device.status()) {
            case ONLINE -> PartScreens.Status.ONLINE;
            case WARNING -> PartScreens.Status.WARNING;
            case FAULT -> PartScreens.Status.ERROR;
            case OFFLINE -> PartScreens.Status.IDLE;
        });
        List<SignalBlockEntity.Row> rows = device.rows();
        for (int i = 0; i < ROWS && i < rows.size(); i++) {
            SignalBlockEntity.Row row = rows.get(i);
            int textY = LIST_Y + i * ROW_H + 3;
            graphics.text(font, row.label(), LABEL_X, textY, PartScreens.TEXT_MUTED, false);
            int room = VALUE_RIGHT - LABEL_X - font.width(row.label()) - 8;
            String value = font.plainSubstrByWidth(row.value().getString(), room);
            if (value.length() < row.value().getString().length()) {
                value = font.plainSubstrByWidth(row.value().getString(), room - font.width("…")) + "…";
            }
            graphics.text(font, value, VALUE_RIGHT - font.width(value), textY, row.editable() ? PartScreens.TEXT : PartScreens.TEXT_MUTED, false);
        }
        // The bottom field: what's being edited, else a hint.
        if (editing == null) {
            Component hint = Component.translatable("gui.encodedlogistics.signal.hint");
            graphics.text(font, font.plainSubstrByWidth(hint.getString(), FIELD_W - 6), FIELD_X + 3, FIELD_Y + 1, PartScreens.TEXT_DISABLED, false);
        }
        Component action = editing != null ? Component.translatable("gui.encodedlogistics.signal.set") : device.action();
        if (action != null) {
            boolean hover = PartScreens.over(mouseX, mouseY, leftPos + ACTION_X, topPos + ACTION_Y, ACTION_W, ACTION_H);
            graphics.text(font, action, VALUE_RIGHT - font.width(action), FIELD_Y + 1, hover ? PartScreens.ACCENT : PartScreens.TEXT, false);
        }
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        SignalBlockEntity device = device();
        boolean left = event.button() == InputConstants.MOUSE_BUTTON_LEFT, right = event.button() == InputConstants.MOUSE_BUTTON_RIGHT;
        if (device == null || !left && !right) {
            return super.mouseClicked(event, doubleClick);
        }
        double x = event.x() - leftPos, y = event.y() - topPos;
        if (left && in(x, y, ACTION_X, ACTION_Y, ACTION_W, ACTION_H)) {
            if (editing != null) {
                commit();
            } else if (device.action() != null) {
                send("action", 0, null);
            }
            return true;
        }
        List<SignalBlockEntity.Row> rows = device.rows();
        for (int i = 0; i < ROWS && i < rows.size(); i++) {
            SignalBlockEntity.Row row = rows.get(i);
            if (!in(x, y, LIST_X, LIST_Y + i * ROW_H, LIST_W, ROW_H)) {
                continue;
            }
            if (!row.editable()) {
                return true;
            }
            if (row.text()) {
                edit(row.key(), device.text(row.key()));
            } else {
                int step = (right ? -1 : 1) * (event.hasShiftDown() ? 10 : 1);
                send(row.key(), step, null);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void edit(String key, String text) {
        if (field == null) {
            return;
        }
        editing = key;
        field.setValue(text);
        field.setVisible(true);
        field.setFocused(true);
        setFocused(field);
    }

    private void commit() {
        if (field != null && editing != null) {
            send(editing, 0, field.getValue());
        }
        stopEditing();
    }

    private void stopEditing() {
        editing = null;
        if (field != null) {
            field.setFocused(false);
            field.setVisible(false);
        }
    }

    // Typing goes to the field while editing (the inventory key doesn't close the screen then): Enter sets, Esc leaves it.
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (editing != null && field != null) {
            if (event.isEscape()) {
                stopEditing();
                return true;
            }
            if (event.isConfirmation()) {
                commit();
                return true;
            }
            return field.keyPressed(event) || field.canConsumeInput();
        }
        return super.keyPressed(event);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        // The device gone or the row no longer there (the source changed): stop editing.
        SignalBlockEntity device = device();
        if (editing != null && (device == null || device.rows().stream().noneMatch(row -> row.key().equals(editing)))) {
            stopEditing();
        }
    }

    private void send(String key, int step, @Nullable String text) {
        ClientPacketDistributor.sendToServer(new SignalConfigPayload(menu.containerId, key, step, Optional.ofNullable(text)));
    }

    private static boolean in(double x, double y, int left, int top, int width, int height) {
        return x >= left && x < left + width && y >= top && y < top + height;
    }
}
