/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.PortMenu;
import net.zagdrath.encodedlogistics.part.PartFilter;

// An Ingress or Egress Port's screen (screens/ingress_port.json, egress_port.json): the redstone mode button, the 3x3
// ghost filter, four module slots and the inventory. With a Filter Module installed, three more buttons beside the
// redstone one: allow / deny list, match by tag, match components exactly.
public class PortScreen extends AbstractContainerScreen<PortMenu> {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/port.png");
    private static final Identifier GHOST_MODULE = EncodedLogistics.id("port/ghost_module");
    private static final Identifier[] REDSTONE = { EncodedLogistics.id("port/redstone_ignore"), EncodedLogistics.id("port/redstone_high"),
            EncodedLogistics.id("port/redstone_low"), EncodedLogistics.id("port/redstone_pulse") };
    private static final String[] REDSTONE_KEYS = { "ignore", "high", "low", "pulse" };
    private static final int REDSTONE_X = 8, REDSTONE_Y = 18, OPTIONS_X = 30, OPTIONS_Y = 18, OPTIONS_STEP = 20;

    public PortScreen(PortMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 176);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 82;
    }

    private boolean options() {
        return menu.flag(PortMenu.FLAG_FILTER_MODULE);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        PartScreens.button(graphics, x + REDSTONE_X, y + REDSTONE_Y, REDSTONE[Math.min(menu.redstoneMode(), REDSTONE.length - 1)], mouseX, mouseY);
        for (int i = 0; i < 4; i++) {
            if (menu.getSlot(PartFilter.SIZE + i).getItem().isEmpty()) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, GHOST_MODULE, x + PortMenu.MODULE_X, y + PortMenu.MODULE_Y[i], 16, 16);
            }
        }
        if (options()) {
            for (int option = 0; option < 3; option++) {
                int bx = x + OPTIONS_X, by = y + OPTIONS_Y + option * OPTIONS_STEP;
                boolean hover = PartScreens.over(mouseX, mouseY, bx, by, PartScreens.BUTTON_SIZE, PartScreens.BUTTON_SIZE);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? PartScreens.BUTTON_HOVER : PartScreens.BUTTON, bx, by,
                        PartScreens.BUTTON_SIZE, PartScreens.BUTTON_SIZE);
                graphics.item(optionIcon(option), bx + 1, by + 1);
                if (!optionOn(option)) {
                    // Off: dimmed.
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
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        List<Component> lines = new ArrayList<>();
        if (PartScreens.over(mouseX, mouseY, leftPos + REDSTONE_X, topPos + REDSTONE_Y, 18, 18)) {
            lines.add(Component.translatable("gui.encodedlogistics.redstone." + REDSTONE_KEYS[Math.min(menu.redstoneMode(), 3)]));
            lines.add(Component.translatable("gui.encodedlogistics.redstone.needs_module").withColor(PartScreens.TEXT_MUTED));
        } else if (options()) {
            for (int option = 0; option < 3; option++) {
                if (PartScreens.over(mouseX, mouseY, leftPos + OPTIONS_X, topPos + OPTIONS_Y + option * OPTIONS_STEP, 18, 18)) {
                    lines.add(Component.translatable(switch (option) {
                        case 0 -> menu.flag(PortMenu.FLAG_DENY) ? "gui.encodedlogistics.port.deny" : "gui.encodedlogistics.port.allow";
                        case 1 -> menu.flag(PortMenu.FLAG_TAGS) ? "gui.encodedlogistics.port.tags.on" : "gui.encodedlogistics.port.tags.off";
                        default -> menu.flag(PortMenu.FLAG_COMPONENTS) ? "gui.encodedlogistics.port.components.on"
                                : "gui.encodedlogistics.port.components.off";
                    }));
                }
            }
        }
        if (lines.isEmpty() && PartScreens.over(mouseX, mouseY, leftPos + PortMenu.FILTER_X, topPos + PortMenu.FILTER_Y, 54, 54)
                && hoveredSlot != null && !hoveredSlot.hasItem() && menu.getCarried().isEmpty()) {
            lines.add(Component.translatable("gui.encodedlogistics.port.filter"));
            lines.add(Component.translatable(menu.flag(PortMenu.FLAG_INGRESS) ? "gui.encodedlogistics.port.empty_filter.ingress"
                    : "gui.encodedlogistics.port.empty_filter.egress").withColor(PartScreens.TEXT_MUTED));
        }
        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            if (PartScreens.over(event.x(), event.y(), leftPos + REDSTONE_X, topPos + REDSTONE_Y, 18, 18)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PortMenu.BUTTON_REDSTONE);
                return true;
            }
            if (options()) {
                for (int option = 0; option < 3; option++) {
                    if (PartScreens.over(event.x(), event.y(), leftPos + OPTIONS_X, topPos + OPTIONS_Y + option * OPTIONS_STEP, 18, 18)) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PortMenu.BUTTON_DENY + option);
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
