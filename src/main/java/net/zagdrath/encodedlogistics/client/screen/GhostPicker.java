/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.zagdrath.encodedlogistics.menu.GhostSlot;
import net.zagdrath.encodedlogistics.storage.ResourceType;

// A screen's fluid and gas picker for its ghost slots (ResourcePicker): right-clicking an empty ghost slot with an empty
// hand opens it, for the types the slot takes. The screen forwards its input here first; each method returns true when
// the picker took the event.
final class GhostPicker {
    private @Nullable ResourcePicker picker;

    boolean isOpen() {
        return picker != null;
    }

    // types: FLUID or PRESSURIZED for a device on one of them, null for both; the picker doesn't open for ITEM or ENERGY.
    boolean mouseClicked(AbstractContainerMenu menu, @Nullable Slot hovered, MouseButtonEvent event, @Nullable ResourceType types, int width, int height) {
        if (picker != null) {
            if (!picker.click(event.x(), event.y())) {
                picker = null;
            }
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && types != ResourceType.ITEM && types != ResourceType.ENERGY
                && menu.getCarried().isEmpty() && hovered instanceof GhostSlot && hovered.isActive() && !hovered.hasItem()) {
            picker = new ResourcePicker(menu.containerId, hovered.index, types, (int) event.x(), (int) event.y(), width, height);
            return true;
        }
        return false;
    }

    void extract(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        if (picker != null) {
            graphics.nextStratum();
            picker.extract(graphics, font, mouseX, mouseY);
        }
    }

    boolean mouseScrolled(double scrollY) {
        if (picker != null) {
            picker.scroll(scrollY);
            return true;
        }
        return false;
    }

    boolean keyPressed(KeyEvent event) {
        if (picker == null) {
            return false;
        }
        if (event.isEscape()) {
            picker = null;
        } else if (event.key() == InputConstants.KEY_BACKSPACE) {
            picker.backspace();
        }
        return true;
    }

    boolean charTyped(CharacterEvent event) {
        if (picker == null) {
            return false;
        }
        picker.type((char) event.codepoint());
        return true;
    }
}
