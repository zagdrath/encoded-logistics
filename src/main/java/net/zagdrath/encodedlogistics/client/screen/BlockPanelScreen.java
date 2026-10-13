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
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.BlockPanelMenu;
import net.zagdrath.encodedlogistics.net.BlockPanelPayloads;

// A block's panel screen (BlockPanelMenu): the L3 Switch's panel background with the host's title, and the panel body
// its kind names (ExchangePanel, DirectoryPanel).
public class BlockPanelScreen extends AbstractContainerScreen<BlockPanelMenu> implements PanelBody.Context {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/rack/l3_switch.png");
    private final @Nullable PanelBody body;

    public BlockPanelScreen(BlockPanelMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
        this.body = switch (menu.kind()) {
            case "exchange" -> new ExchangePanel(this);
            default -> null;
        };
    }

    // --- Context ---

    @Override
    public @Nullable ValueInput data() {
        CompoundTag tag = BlockPanelPayloads.Data.forMenu(menu.containerId);
        return tag != null && minecraft != null && minecraft.level != null
                ? TagValueInput.create(ProblemReporter.DISCARDING, minecraft.level.registryAccess(), tag) : null;
    }

    @Override
    public void send(int action, int value, String text) {
        ClientPacketDistributor.sendToServer(new BlockPanelPayloads.Action(menu.containerId, action, value, text));
    }

    @Override
    public Font font() {
        return font;
    }

    @Override
    public int left() {
        return leftPos;
    }

    @Override
    public int top() {
        return topPos;
    }

    @Override
    public void addWidget(EditBox box) {
        addRenderableWidget(box);
        box.setFocused(true);
        setFocused(box);
    }

    @Override
    public void removeWidget(EditBox box) {
        super.removeWidget(box);
    }

    // --- Screen ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        if (body != null) {
            body.extractBackground(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, RackScreen.TEXT, false);
        if (body != null) {
            body.extractLabels(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (body != null) {
            body.extractTooltip(graphics, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (body != null && body.mouseClicked(event.x() - leftPos, event.y() - topPos, event.button(), event.hasShiftDown())) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        return body != null && body.mouseScrolled(scrollY) || super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return body != null && body.keyPressed(event) || super.keyPressed(event);
    }

    @Override
    public void removed() {
        if (body != null) {
            body.removed();
        }
        super.removed();
    }
}
