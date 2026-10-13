/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.world.level.storage.ValueInput;

// What a panel draws and does, whichever screen hosts it: a block's own (BlockPanelScreen: the Exchange Switch, the
// Directory Server) or a rack unit's (BodyPanel: the Directory Server's rack form). Drawn on the L3 Switch's panel
// background (its inset x 12-153, y 34-139), 176 by 166.
public interface PanelBody {
    // Where it is and what it can use.
    interface Context {
        @Nullable ValueInput data();

        void send(int action, int value, String text);

        Font font();

        // The panel's top-left on screen.
        int left();

        int top();

        void addWidget(EditBox box);

        void removeWidget(EditBox box);
    }

    // Screen coordinates (context.left(), top() is the panel's corner).
    default void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {}

    // The panel's own coordinates.
    void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY);

    default void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {}

    // x, y in the panel's coordinates.
    default boolean mouseClicked(double x, double y, int button, boolean shift) {
        return false;
    }

    default boolean mouseScrolled(double amount) {
        return false;
    }

    default boolean keyPressed(KeyEvent event) {
        return false;
    }

    default void removed() {}
}
