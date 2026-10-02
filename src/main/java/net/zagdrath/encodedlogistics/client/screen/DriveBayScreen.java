/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.menu.DriveBayMenu;
import net.zagdrath.encodedlogistics.storage.DriveStats;

// The Drive Bay screen: the ten drive slots in one inset, laid out like the front, each with a fill bar in its status
// light's colour (green, yellow, orange, red), a dim Storage Drive silhouette in empty slots, and Online / Offline (the
// controller's lights) in the title strip. Layout and colours from screens/drive_bay.json and palette.json.
public class DriveBayScreen extends AbstractContainerScreen<DriveBayMenu> {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/drive_bay.png");
    private static final Identifier GHOST = EncodedLogistics.id("drive_bay/ghost_drive");
    private static final Identifier[] FILLS = { EncodedLogistics.id("drive_bay/fill_green"), EncodedLogistics.id("drive_bay/fill_yellow"),
            EncodedLogistics.id("drive_bay/fill_orange"), EncodedLogistics.id("drive_bay/fill_red") };
    // The fill bar sits 20 px right of the slot frame (19 from the item), as tall as the item.
    private static final int BAR_X = 19, BAR_W = 4, BAR_H = 16;

    public DriveBayScreen(DriveBayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 208);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 115;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        for (int i = 0; i < DriveBayBlockEntity.SLOTS; i++) {
            Slot slot = menu.getSlot(i);
            ItemStack stack = slot.getItem();
            if (stack.isEmpty()) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, GHOST, x + slot.x, y + slot.y, 16, 16);
                continue;
            }
            DriveStats stats = StorageDriveItem.stats(stack);
            int height = (int) Math.ceil(stats.fill() * BAR_H);
            if (height > 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, FILLS[stats.light()], BAR_W, BAR_H, 0, BAR_H - height, x + slot.x + BAR_X,
                        y + slot.y + BAR_H - height, BAR_W, height);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, PartScreens.TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, PartScreens.TEXT_MUTED, false);
        boolean online = menu.isOnline();
        PartScreens.status(graphics, font, PartScreens.STATUS_RIGHT, titleLabelY,
                Component.translatable(online ? "gui.encodedlogistics.drive_bay.online" : "gui.encodedlogistics.drive_bay.offline"),
                online ? PartScreens.Status.ONLINE : PartScreens.Status.ERROR);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        for (int i = 0; i < DriveBayBlockEntity.SLOTS; i++) {
            Slot slot = menu.getSlot(i);
            int bx = leftPos + slot.x + BAR_X, by = topPos + slot.y;
            if (slot.hasItem() && mouseX >= bx && mouseX < bx + BAR_W && mouseY >= by && mouseY < by + BAR_H) {
                DriveStats stats = StorageDriveItem.stats(slot.getItem());
                graphics.setComponentTooltipForNextFrame(font, List.of(
                        Component.translatable("gui.encodedlogistics.drive_bay.fill", Math.round(stats.fill() * 100)),
                        Component.translatable("tooltip.encodedlogistics.drive.bytes", String.format(Locale.ROOT, "%,d", stats.bytesUsed()),
                                String.format(Locale.ROOT, "%,d", stats.bytesTotal())).withColor(PartScreens.TEXT_MUTED),
                        Component.translatable("tooltip.encodedlogistics.drive.types", stats.typesUsed(), Config.DRIVE_TYPE_LIMIT.getAsInt())
                                .withColor(PartScreens.TEXT_MUTED)),
                        mouseX, mouseY);
            }
        }
    }
}
