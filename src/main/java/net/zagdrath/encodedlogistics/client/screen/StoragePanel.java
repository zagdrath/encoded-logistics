/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.storage.ValueInput;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackSlot;
import net.zagdrath.encodedlogistics.rack.StorageDevice;
import net.zagdrath.encodedlogistics.rack.device.NasDevice;
import net.zagdrath.encodedlogistics.rack.device.SanDevice;

// A NAS's or SAN's panel (screens/rack/nas.json, san.json): its drive slots (a ghost drive when empty; the SAN's four
// transceiver cages too), the storage priority (type it and press Enter, or the steppers; Shift steps by 10), the access
// mode, the capacity bar and, for a SAN, its uplink.
public class StoragePanel extends RackScreen.Panel {
    private static final Identifier NAS = EncodedLogistics.id("textures/gui/rack/nas.png"), SAN = EncodedLogistics.id("textures/gui/rack/san.png");
    private static final Identifier GHOST_DRIVE = EncodedLogistics.id("rack/storage/ghost_drive"), GHOST_TRANSCEIVER = EncodedLogistics.id("relay/ghost_transceiver"),
            FIELD = EncodedLogistics.id("inventory_tap/number_field"), FIELD_FOCUSED = EncodedLogistics.id("inventory_tap/number_field_focused"),
            UP = EncodedLogistics.id("inventory_tap/step_up"), UP_HOVER = EncodedLogistics.id("inventory_tap/step_up_hover"),
            DOWN = EncodedLogistics.id("inventory_tap/step_down"), DOWN_HOVER = EncodedLogistics.id("inventory_tap/step_down_hover"),
            BAR = EncodedLogistics.id("common/bar_fill_mint");
    private static final Identifier[] ACCESS = { EncodedLogistics.id("inventory_tap/access_read_write"), EncodedLogistics.id("inventory_tap/access_read"),
            EncodedLogistics.id("inventory_tap/access_write") };
    private static final String[] ACCESS_KEYS = { "read_write", "read", "write" };
    private static final int FIELD_W = 36, FIELD_H = 12, STEP_W = 9, STEP_H = 6;

    private final boolean san;
    private final int top;
    private @Nullable EditBox priority;
    private int shown = Integer.MIN_VALUE;

    public StoragePanel(RackScreen screen) {
        super(screen);
        RackDevice device = screen.pickedDevice();
        san = device instanceof SanDevice;
        // Where the settings start: under the NAS's one row of drives, the SAN's four.
        top = san ? 104 : 55;
    }

    @Override
    protected Identifier background() {
        return san ? SAN : NAS;
    }

    private RackDeviceType type() {
        return san ? RackDeviceType.SAN : RackDeviceType.NAS;
    }

    private int drives() {
        return san ? SanDevice.DRIVES : NasDevice.DRIVES;
    }

    // Field, steppers, access button, capacity bar.
    private int fieldX() {
        return 12;
    }

    private int fieldY() {
        return top + 9;
    }

    private int stepX() {
        return fieldX() + FIELD_W + 2;
    }

    private int accessX() {
        return 60;
    }

    private int accessY() {
        return top + 1;
    }

    private int barY() {
        return san ? 130 : 80;
    }

    @Override
    protected void init() {
        priority = new EditBox(font(), screen.left() + fieldX() + 4, screen.top() + fieldY() + 2, FIELD_W - 6, 9,
                Component.translatable("gui.encodedlogistics.tap.priority"));
        priority.setBordered(false);
        priority.setMaxLength(4);
        priority.setTextColor(RackScreen.TEXT);
        PartScreens.numeric(priority, true);
        screen.addPanelWidget(priority);
    }

    @Override
    protected void removed() {
        if (priority != null) {
            screen.removePanelWidget(priority);
        }
    }

    // Follows the server's value unless it's being typed.
    @Override
    protected void tick() {
        ValueInput data = data();
        if (priority != null && data != null && !priority.isFocused()) {
            int value = data.getIntOr("priority", 0);
            if (value != shown) {
                shown = value;
                priority.setValue(Integer.toString(value));
            }
        }
    }

    private void submit() {
        if (priority == null) {
            return;
        }
        try {
            send(StorageDevice.ACTION_SET_PRIORITY, Math.clamp(Integer.parseInt(priority.getValue()), StorageDevice.MIN_PRIORITY, StorageDevice.MAX_PRIORITY), "");
        } catch (NumberFormatException e) {
            priority.setValue(Integer.toString(shown));
        }
    }

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.left(), y = screen.top();
        for (int i = 0; i < type().slots().size(); i++) {
            Slot slot = screen.getMenu().deviceSlot(i);
            RackSlot spec = type().slots().get(i);
            if (slot == null || slot.getItem().isEmpty()) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, i < drives() ? GHOST_DRIVE : GHOST_TRANSCEIVER, x + spec.x(), y + spec.y(), 16, 16);
            }
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, priority != null && priority.isFocused() ? FIELD_FOCUSED : FIELD, x + fieldX(), y + fieldY(),
                FIELD_W, FIELD_H);
        boolean overUp = PartScreens.over(mouseX, mouseY, x + stepX(), y + fieldY(), STEP_W, STEP_H);
        boolean overDown = PartScreens.over(mouseX, mouseY, x + stepX(), y + fieldY() + STEP_H, STEP_W, STEP_H);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, overUp ? UP_HOVER : UP, x + stepX(), y + fieldY(), STEP_W, STEP_H);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, overDown ? DOWN_HOVER : DOWN, x + stepX(), y + fieldY() + STEP_H, STEP_W, STEP_H);
        ValueInput data = data();
        int access = data != null ? Math.clamp(data.getIntOr("access", 0), 0, 2) : 0;
        PartScreens.button(graphics, x + accessX(), y + accessY(), ACCESS[access], mouseX, mouseY);
        if (data != null) {
            long total = data.getLongOr("total", 0);
            PartScreens.bar(graphics, BAR, x + 12, y + barY(), 152, total <= 0 ? 0 : (float) data.getLongOr("used", 0) / total);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font(), Component.translatable("gui.encodedlogistics.tap.priority"), fieldX(), top, RackScreen.TEXT_MUTED, false);
        ValueInput data = data();
        if (data == null) {
            return;
        }
        Component capacity = Component.translatable("gui.encodedlogistics.storage.capacity", StorageDevice.bytes(data.getLongOr("used", 0)),
                StorageDevice.bytes(data.getLongOr("total", 0)));
        graphics.text(font(), capacity, 164 - font().width(capacity), top, RackScreen.TEXT_MUTED, false);
        if (san) {
            int transceivers = 0;
            for (int i = 0; i < SanDevice.CAGES; i++) {
                Slot slot = screen.getMenu().deviceSlot(SanDevice.DRIVES + i);
                if (slot != null && !slot.getItem().isEmpty()) {
                    transceivers++;
                }
            }
            graphics.text(font(), transceivers > 0 ? Component.translatable("gui.encodedlogistics.san.uplink", transceivers)
                    : Component.translatable("gui.encodedlogistics.san.no_uplink"), 12, 148, transceivers > 0 ? RackScreen.TEXT : RackScreen.ERROR, false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ValueInput data = data();
        if (data != null && screen.over(mouseX, mouseY, accessX(), accessY(), 18, 18)) {
            graphics.setComponentTooltipForNextFrame(font(), List.of(Component.translatable("gui.encodedlogistics.tap.access"),
                    Component.translatable("gui.encodedlogistics.tap." + ACCESS_KEYS[Math.clamp(data.getIntOr("access", 0), 0, 2)])
                            .withColor(RackScreen.TEXT_MUTED)), mouseX, mouseY);
        }
    }

    @Override
    protected boolean mouseClicked(double x, double y, int button, boolean shift) {
        boolean wasFocused = priority != null && priority.isFocused();
        if (button == InputConstants.MOUSE_BUTTON_LEFT) {
            if (x >= stepX() && x < stepX() + STEP_W && y >= fieldY() && y < fieldY() + 2 * STEP_H) {
                send(StorageDevice.ACTION_STEP_PRIORITY, (y < fieldY() + STEP_H ? 1 : -1) * (shift ? 10 : 1), "");
                return true;
            }
            if (x >= accessX() && x < accessX() + 18 && y >= accessY() && y < accessY() + 18) {
                send(StorageDevice.ACTION_CYCLE_ACCESS, 0, "");
                return true;
            }
        }
        if (wasFocused && !(x >= fieldX() && x < fieldX() + FIELD_W && y >= fieldY() && y < fieldY() + FIELD_H)) {
            submit();
            priority.setFocused(false);
        }
        return false;
    }

    // Enter sets the priority; while typing, keys go to the field (so "e" doesn't close the screen).
    @Override
    protected boolean keyPressed(KeyEvent event) {
        if (priority == null || !priority.isFocused()) {
            return false;
        }
        if (event.isConfirmation()) {
            submit();
            priority.setFocused(false);
            return true;
        }
        if (event.isEscape()) {
            return false;
        }
        return priority.keyPressed(event) || priority.canConsumeInput();
    }
}
