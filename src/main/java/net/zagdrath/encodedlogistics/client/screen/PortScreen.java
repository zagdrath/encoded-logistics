/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.WirelessPortBlockEntity;
import net.zagdrath.encodedlogistics.client.ResourceRender;
import net.zagdrath.encodedlogistics.menu.PortMenu;
import net.zagdrath.encodedlogistics.net.MenuValuePayload;
import net.zagdrath.encodedlogistics.part.PartFilter;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.StorageTier;
import net.zagdrath.encodedlogistics.wireless.Wireless;

// An Ingress or Egress Port's screen (screens/ingress_port.json, egress_port.json): the redstone mode button (it cycles
// once a Redstone Control Module is in), the 3x3 ghost filter, four module slots and the inventory. With a Filter Module
// installed, three more buttons beside the redstone one: allow / deny list, match by tag, match components exactly.
// With a Fuzzy Match Module, right-clicking a filter entry opens its fuzzy choices (FuzzyPopup); fuzzy entries are
// marked with a "~". Under the redstone button, its resource type (item, fluid, pressurized, energy; the matching
// Storage Drive as its icon). A fluid or pressurized port's filter shows fluids and gases (Resource Entries), and
// right-clicking an empty entry with an empty hand picks one from a list (ResourcePicker).
public class PortScreen extends AbstractContainerScreen<PortMenu> {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/port.png");
    private static final Identifier GHOST_MODULE = EncodedLogistics.id("port/ghost_module");
    private static final Identifier[] REDSTONE = { EncodedLogistics.id("port/redstone_ignore"), EncodedLogistics.id("port/redstone_high"),
            EncodedLogistics.id("port/redstone_low"), EncodedLogistics.id("port/redstone_pulse") };
    private static final String[] REDSTONE_KEYS = { "ignore", "high", "low", "pulse" };
    private static final int REDSTONE_X = 8, REDSTONE_Y = 18, OPTIONS_X = 30, OPTIONS_Y = 18, OPTIONS_STEP = 20;
    // A Wireless Port's link, under the redstone button: the status light and the signal bars.
    private static final int LINK_X = 9, LINK_Y = 42, SIGNAL_X = 17, SIGNAL_Y = 41;
    private static final Identifier LINKED = EncodedLogistics.id("bridge/status_linked"), UNLINKED = EncodedLogistics.id("bridge/status_unlinked");

    private static final int TYPE_X = 8, TYPE_Y = 60;

    private @Nullable FuzzyPopup popup;
    private final GhostPicker picker = new GhostPicker();

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
        boolean typeHover = PartScreens.over(mouseX, mouseY, x + TYPE_X, y + TYPE_Y, PartScreens.BUTTON_SIZE, PartScreens.BUTTON_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, typeHover ? PartScreens.BUTTON_HOVER : PartScreens.BUTTON, x + TYPE_X, y + TYPE_Y,
                PartScreens.BUTTON_SIZE, PartScreens.BUTTON_SIZE);
        graphics.item(new ItemStack(ModItems.storageDrive(menu.resourceType(), StorageTier.K8).get()), x + TYPE_X + 1, y + TYPE_Y + 1);
        if (menu.flag(PortMenu.FLAG_WIRELESS)) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, menu.signal() > 0 ? LINKED : UNLINKED, x + LINK_X, y + LINK_Y, 6, 6);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, EncodedLogistics.id("handheld/signal_" + Math.min(4, menu.signal())), x + SIGNAL_X, y + SIGNAL_Y,
                    12, 10);
        }
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

    // "Linked to WLC01 - signal 4/4", what's wrong, or how to link it.
    private Component linkTooltip() {
        WirelessPortBlockEntity port = menu.pos() != null && minecraft != null && minecraft.level != null
                && minecraft.level.getBlockEntity(menu.pos()) instanceof WirelessPortBlockEntity found ? found : null;
        if (!menu.flag(PortMenu.FLAG_LINKED) || port == null) {
            return Component.translatable("gui.encodedlogistics.wireless.port.not_linked");
        }
        if (port.shownProblem() != Wireless.Problem.NONE) {
            return Component.translatable("gui.encodedlogistics.wireless.port.problem", port.shownController().isEmpty() ? "-" : port.shownController(),
                    port.shownProblem().text());
        }
        return Component.translatable("gui.encodedlogistics.wireless.port.linked", port.shownController(), menu.signal());
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, PartScreens.TEXT, false);
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, PartScreens.TEXT_MUTED, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (menu.flag(PortMenu.FLAG_FUZZY_MODULE)) {
            graphics.nextStratum();
            FuzzyMarks.extract(graphics, font, menu, leftPos + PortMenu.FILTER_X, topPos + PortMenu.FILTER_Y, menu::fuzzy);
        }
        if (popup != null) {
            graphics.nextStratum();
            popup.extract(graphics, font, mouseX, mouseY);
        }
        picker.extract(graphics, font, mouseX, mouseY);
    }

    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        if (!ResourceRender.entrySlot(graphics, font, slot)) {
            super.extractSlot(graphics, slot, mouseX, mouseY);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (popup != null || picker.isOpen()) {
            return;
        }
        super.extractTooltip(graphics, mouseX, mouseY);
        List<Component> lines = new ArrayList<>();
        if (PartScreens.over(mouseX, mouseY, leftPos + TYPE_X, topPos + TYPE_Y, 18, 18)) {
            lines.add(Component.translatable("gui.encodedlogistics.port.type", Component.translatable("tooltip.encodedlogistics.resource."
                    + menu.resourceType().getSerializedName())));
            lines.add(Component.translatable("gui.encodedlogistics.port.type." + menu.resourceType().getSerializedName()).withColor(PartScreens.TEXT_MUTED));
        } else if (menu.flag(PortMenu.FLAG_WIRELESS) && PartScreens.over(mouseX, mouseY, leftPos + LINK_X, topPos + SIGNAL_Y, SIGNAL_X + 12 - LINK_X, 10)) {
            lines.add(linkTooltip());
        } else if (PartScreens.over(mouseX, mouseY, leftPos + REDSTONE_X, topPos + REDSTONE_Y, 18, 18)) {
            lines.add(Component.translatable("gui.encodedlogistics.redstone." + REDSTONE_KEYS[Math.min(menu.redstoneMode(), 3)]));
            if (!menu.flag(PortMenu.FLAG_REDSTONE_MODULE)) {
                lines.add(Component.translatable("gui.encodedlogistics.redstone.needs_module").withColor(PartScreens.TEXT_MUTED));
            }
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
        if (lines.isEmpty() && menu.flag(PortMenu.FLAG_FUZZY_MODULE) && hoveredSlot != null && hoveredSlot.index < PartFilter.SIZE
                && hoveredSlot.hasItem()) {
            FuzzyMarks.tooltip(hoveredSlot.getItem(), menu.fuzzy(hoveredSlot.index), lines);
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
        // Right-clicking an empty filter entry with an empty hand on a fluid or pressurized port: pick from a list.
        if (picker.mouseClicked(menu, hoveredSlot, event, menu.resourceType(), width, height)) {
            return true;
        }
        if (popup != null) {
            int code = popup.click(event.x(), event.y());
            if (code >= 0) {
                ClientPacketDistributor.sendToServer(new MenuValuePayload(menu.containerId, popup.entry, code));
            }
            popup = null;
            return true;
        }
        // Right-clicking a filter entry with an empty hand and a Fuzzy Match Module in: its fuzzy choices.
        if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT && menu.flag(PortMenu.FLAG_FUZZY_MODULE) && menu.getCarried().isEmpty() && hoveredSlot != null
                && hoveredSlot.index < PartFilter.SIZE && hoveredSlot.hasItem()) {
            popup = new FuzzyPopup(font, hoveredSlot.index, hoveredSlot.getItem(), menu.fuzzy(hoveredSlot.index), (int) event.x(), (int) event.y(),
                    width, height);
            return true;
        }
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            if (PartScreens.over(event.x(), event.y(), leftPos + REDSTONE_X, topPos + REDSTONE_Y, 18, 18)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PortMenu.BUTTON_REDSTONE);
                return true;
            }
            if (PartScreens.over(event.x(), event.y(), leftPos + TYPE_X, topPos + TYPE_Y, 18, 18)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PortMenu.BUTTON_TYPE);
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (picker.mouseScrolled(scrollY)) {
            return true;
        }
        if (popup != null) {
            popup.scroll(scrollY);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (picker.keyPressed(event)) {
            return true;
        }
        if (popup != null && event.isEscape()) {
            popup = null;
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        return picker.charTyped(event) || super.charTyped(event);
    }
}
