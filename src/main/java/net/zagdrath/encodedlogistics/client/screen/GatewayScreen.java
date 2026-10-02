/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.GatewayMenu;
import net.zagdrath.encodedlogistics.net.MenuValuePayload;

// The Gateway's screen (screens/gateway.json): its Processing Schematics, the stock row (amounts at half size; scroll or
// right-click to change them, Shift for 10), the buffer it holds and the inventory.
public class GatewayScreen extends AbstractContainerScreen<GatewayMenu> {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/gateway.png");

    public GatewayScreen(GatewayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 206);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 112;
    }

    private static boolean isStock(Slot slot) {
        return slot.index >= GatewayMenu.STOCK && slot.index < GatewayMenu.BUFFER;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, leftPos, topPos, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
    }

    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        if (isStock(slot) && slot.hasItem()) {
            PartScreens.itemWithAmount(graphics, font, slot.getItem(), slot.x, slot.y);
            return;
        }
        super.extractSlot(graphics, slot, mouseX, mouseY);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, PartScreens.TEXT, false);
        graphics.text(font, Component.translatable("gui.encodedlogistics.gateway.stock"), 8, 39, PartScreens.TEXT_MUTED, false);
        graphics.text(font, Component.translatable("gui.encodedlogistics.gateway.buffer"), 8, 72, PartScreens.TEXT_MUTED, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, PartScreens.TEXT_MUTED, false);
    }

    private void step(Slot slot, boolean up, boolean shift) {
        ItemStack stack = slot.getItem();
        int amount = PartScreens.stepAmount(stack.getCount(), up, shift, stack.getMaxStackSize());
        ClientPacketDistributor.sendToServer(new MenuValuePayload(menu.containerId, slot.index - GatewayMenu.STOCK, amount));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        Slot slot = hoveredSlot;
        if (event.button() == 1 && slot != null && isStock(slot) && slot.hasItem() && menu.getCarried().isEmpty()) {
            step(slot, true, event.hasShiftDown());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Slot slot = hoveredSlot;
        if (slot != null && isStock(slot) && slot.hasItem() && scrollY != 0) {
            step(slot, scrollY > 0, minecraft.hasShiftDown());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
