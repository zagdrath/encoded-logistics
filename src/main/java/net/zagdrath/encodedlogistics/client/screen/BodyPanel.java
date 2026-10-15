/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.storage.ValueInput;

// A rack unit's panel that is a PanelBody (the same panel its block form shows): the Directory Server's, the Optical
// Multiplexer's.
public class BodyPanel extends RackScreen.Panel implements PanelBody.Context {
    private final PanelBody body;

    public BodyPanel(RackScreen screen, Function<PanelBody.Context, PanelBody> body) {
        super(screen);
        this.body = body.apply(this);
    }

    public static BodyPanel directory(RackScreen screen) {
        return new BodyPanel(screen, DirectoryPanel::new);
    }

    public static BodyPanel opticalMux(RackScreen screen) {
        return new BodyPanel(screen, OpticalMuxPanel::new);
    }

    @Override
    protected int height() {
        return 166;
    }

    @Override
    protected Identifier background() {
        return PanelBody.BACKGROUND;
    }

    // --- Context ---

    @Override
    public @Nullable ValueInput data() {
        return super.data();
    }

    @Override
    public void send(int action, int value, String text) {
        super.send(action, value, text);
    }

    @Override
    public Font font() {
        return super.font();
    }

    @Override
    public int left() {
        return screen.left();
    }

    @Override
    public int top() {
        return screen.top();
    }

    @Override
    public void addWidget(EditBox box) {
        screen.addPanelWidget(box);
        box.setFocused(true);
        screen.setFocused(box);
    }

    @Override
    public void removeWidget(EditBox box) {
        screen.removePanelWidget(box);
    }

    // --- The body ---

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        body.extractBackground(graphics, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        body.extractLabels(graphics, mouseX, mouseY);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        body.extractTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected boolean mouseClicked(double x, double y, int button, boolean shift) {
        return body.mouseClicked(x, y, button, shift);
    }

    @Override
    protected boolean mouseScrolled(double x, double y, double amount) {
        return body.mouseScrolled(amount);
    }

    @Override
    protected boolean keyPressed(KeyEvent event) {
        return body.keyPressed(event);
    }

    @Override
    protected void removed() {
        body.removed();
    }
}
