/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.CollectorPlaneMenu;
import net.zagdrath.encodedlogistics.menu.PortMenu;
import net.zagdrath.encodedlogistics.net.MenuValuePayload;
import net.zagdrath.encodedlogistics.part.PartFilter;

// The Collector Plane's screen (screens/collector_plane.json): its module slot, the 3x3 ghost filter (which applies
// while a module is in) and the inventory. With a Filter Module, the allow / deny, tag and components buttons beside
// the filter; with a Fuzzy Match Module, right-clicking an entry opens its fuzzy choices (FuzzyPopup).
public class CollectorPlaneScreen extends AbstractContainerScreen<CollectorPlaneMenu> {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/collector_plane.png");
    private static final Identifier GHOST_MODULE = EncodedLogistics.id("port/ghost_module");

    private @Nullable FuzzyPopup popup;

    public CollectorPlaneScreen(CollectorPlaneMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 72;
    }

    private boolean options() {
        return menu.flag(PortMenu.FLAG_FILTER_MODULE);
    }

    private boolean fuzzy() {
        return menu.flag(PortMenu.FLAG_FUZZY_MODULE);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        if (menu.getSlot(PartFilter.SIZE).getItem().isEmpty()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, GHOST_MODULE, x + CollectorPlaneMenu.MODULE_X, y + CollectorPlaneMenu.MODULE_Y, 16, 16);
        }
        if (options()) {
            for (int option = 0; option < 3; option++) {
                int bx = x + CollectorPlaneMenu.OPTIONS_X, by = y + CollectorPlaneMenu.OPTIONS_Y + option * CollectorPlaneMenu.OPTIONS_STEP;
                boolean hover = popup == null && PartScreens.over(mouseX, mouseY, bx, by, PartScreens.BUTTON_SIZE, PartScreens.BUTTON_SIZE);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? PartScreens.BUTTON_HOVER : PartScreens.BUTTON, bx, by,
                        PartScreens.BUTTON_SIZE, PartScreens.BUTTON_SIZE);
                graphics.item(optionIcon(option), bx + 1, by + 1);
                if (!optionOn(option)) {
                    graphics.fill(bx + 1, by + 1, bx + 17, by + 17, 0x99303030);
                }
            }
        }
    }

    private ItemStack optionIcon(int option) {
        return switch (option) {
            case 0 -> new ItemStack(menu.flag(PortMenu.FLAG_DENY) ? Items.BARRIER : Items.PAPER);
            case 1 -> new ItemStack(Items.NAME_TAG);
            default -> new ItemStack(Items.ENCHANTED_BOOK);
        };
    }

    private boolean optionOn(int option) {
        return switch (option) {
            case 0 -> true;
            case 1 -> menu.flag(PortMenu.FLAG_TAGS);
            default -> menu.flag(PortMenu.FLAG_COMPONENTS);
        };
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, PartScreens.TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, PartScreens.TEXT_MUTED, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (fuzzy()) {
            graphics.nextStratum();
            FuzzyMarks.extract(graphics, font, menu, leftPos + CollectorPlaneMenu.FILTER_X, topPos + CollectorPlaneMenu.FILTER_Y, menu::fuzzy);
        }
        if (popup != null) {
            graphics.nextStratum();
            popup.extract(graphics, font, mouseX, mouseY);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (popup != null) {
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
        List<Component> lines = new ArrayList<>();
        if (options()) {
            for (int option = 0; option < 3; option++) {
                if (PartScreens.over(mouseX, mouseY, leftPos + CollectorPlaneMenu.OPTIONS_X, topPos + CollectorPlaneMenu.OPTIONS_Y
                        + option * CollectorPlaneMenu.OPTIONS_STEP, 18, 18)) {
                    lines.add(Component.translatable(switch (option) {
                        case 0 -> menu.flag(PortMenu.FLAG_DENY) ? "gui.encodedlogistics.port.deny" : "gui.encodedlogistics.port.allow";
                        case 1 -> menu.flag(PortMenu.FLAG_TAGS) ? "gui.encodedlogistics.port.tags.on" : "gui.encodedlogistics.port.tags.off";
                        default -> menu.flag(PortMenu.FLAG_COMPONENTS) ? "gui.encodedlogistics.port.components.on"
                                : "gui.encodedlogistics.port.components.off";
                    }));
                }
            }
        }
        if (lines.isEmpty() && fuzzy() && hoveredSlot != null && hoveredSlot.index < PartFilter.SIZE && hoveredSlot.hasItem()) {
            FuzzyMarks.tooltip(hoveredSlot.getItem(), menu.fuzzy(hoveredSlot.index), lines);
        }
        if (lines.isEmpty() && hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            if (hoveredSlot.index < PartFilter.SIZE) {
                lines.add(Component.translatable("gui.encodedlogistics.collector.filter"));
                lines.add(Component.translatable(menu.getSlot(PartFilter.SIZE).hasItem() ? "gui.encodedlogistics.collector.filter.info"
                        : "gui.encodedlogistics.collector.filter.no_module").withColor(PartScreens.TEXT_MUTED));
            } else if (hoveredSlot.index == PartFilter.SIZE) {
                lines.add(Component.translatable("gui.encodedlogistics.collector.module"));
                lines.add(Component.translatable("gui.encodedlogistics.collector.module.info").withColor(PartScreens.TEXT_MUTED));
            }
        }
        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (popup != null) {
            int code = popup.click(event.x(), event.y());
            if (code >= 0) {
                ClientPacketDistributor.sendToServer(new MenuValuePayload(menu.containerId, popup.entry, code));
            }
            popup = null;
            return true;
        }
        if (event.button() == 1 && fuzzy() && menu.getCarried().isEmpty() && hoveredSlot != null && hoveredSlot.index < PartFilter.SIZE
                && hoveredSlot.hasItem()) {
            popup = new FuzzyPopup(font, hoveredSlot.index, hoveredSlot.getItem(), menu.fuzzy(hoveredSlot.index), (int) event.x(), (int) event.y(),
                    width, height);
            return true;
        }
        if (event.button() == 0 && options()) {
            for (int option = 0; option < 3; option++) {
                if (PartScreens.over(event.x(), event.y(), leftPos + CollectorPlaneMenu.OPTIONS_X, topPos + CollectorPlaneMenu.OPTIONS_Y
                        + option * CollectorPlaneMenu.OPTIONS_STEP, 18, 18)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CollectorPlaneMenu.BUTTON_DENY + option);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (popup != null) {
            popup.scroll(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (popup != null && event.isEscape()) {
            popup = null;
            return true;
        }
        return super.keyPressed(event);
    }
}
