/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.item.SchematicItem;
import net.zagdrath.encodedlogistics.menu.SchematicEncoderMenu;
import net.zagdrath.encodedlogistics.net.MenuValuePayload;

// The Schematic Encoder: the terminal kit with the encoding section for its mode (screens/schematic_encoder.json:
// sections.crafting / sections.processing). Over the section: the mode toggle, Clear, and Encode (enabled when there's
// something to encode and a card for it). In processing mode ghost amounts show at half size; scroll or right-click a
// ghost slot to change its amount (Shift: 10 at a time).
public class SchematicEncoderScreen extends AbstractTerminalScreen<SchematicEncoderMenu> {
    public static final String LAYOUT = "schematic_encoder";
    private static final Identifier[] MODES = { EncodedLogistics.id("encoder/mode_crafting"), EncodedLogistics.id("encoder/mode_processing") };
    private static final int MODE_X = 8, MODE_Y = 8, CLEAR_X = 84, CLEAR_Y = 8, CLEAR_SIZE = 9, ENCODE_X = 150, ENCODE_Y = 29, ENCODE_W = 38,
            ENCODE_H = 14;

    public SchematicEncoderScreen(SchematicEncoderMenu menu, Inventory inventory, Component title) {
        this(menu, inventory, TerminalLayout.load(LAYOUT));
    }

    private SchematicEncoderScreen(SchematicEncoderMenu menu, Inventory inventory, TerminalLayout layout) {
        super(menu, inventory, Component.translatable(layout.titleKey), layout);
    }

    private int sectionTop() {
        return topPos + layout().topHeight + rows() * layout().rowHeight;
    }

    @Override
    protected @Nullable Identifier sectionTexture() {
        TerminalLayout.Section section = layout().section(menu.processing() ? "processing" : "crafting");
        return section != null ? section.texture() : layout().section;
    }

    // Whether Encode does anything: inputs, an output (the recipe's, or the processing outputs), and a card to write.
    private boolean canEncode() {
        boolean inputs = false, outputs = false;
        for (int i = 0; i < 9; i++) {
            inputs |= menu.getSlot(SchematicEncoderMenu.GRID + i).hasItem();
        }
        if (menu.processing()) {
            for (int i = 0; i < 3; i++) {
                outputs |= menu.getSlot(SchematicEncoderMenu.OUTPUTS + i).hasItem();
            }
        } else {
            outputs = menu.getSlot(SchematicEncoderMenu.RESULT).hasItem();
        }
        ItemStack encoded = menu.getSlot(SchematicEncoderMenu.ENCODED).getItem();
        boolean card = encoded.isEmpty() ? menu.getSlot(SchematicEncoderMenu.BLANK).hasItem() : encoded.getItem() instanceof SchematicItem;
        return inputs && outputs && card;
    }

    @Override
    protected void extractSection(GuiGraphicsExtractor graphics, int left, int top, int mouseX, int mouseY) {
        PartScreens.button(graphics, left + MODE_X, top + MODE_Y, MODES[menu.processing() ? 1 : 0], mouseX, mouseY);
        boolean overClear = PartScreens.over(mouseX, mouseY, left + CLEAR_X, top + CLEAR_Y, CLEAR_SIZE, CLEAR_SIZE);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, overClear ? layout().clearHover : layout().clear, left + CLEAR_X, top + CLEAR_Y, CLEAR_SIZE,
                CLEAR_SIZE);
        PartScreens.wideButton(graphics, font, left + ENCODE_X, top + ENCODE_Y, ENCODE_W, ENCODE_H, Component.translatable("gui.encodedlogistics.encoder.encode"),
                canEncode(), mouseX, mouseY);
    }

    private static boolean isAmountSlot(Slot slot) {
        return slot.index >= SchematicEncoderMenu.GRID && slot.index < SchematicEncoderMenu.RESULT;
    }

    @Override
    protected void extractSlot(GuiGraphicsExtractor graphics, Slot slot, int mouseX, int mouseY) {
        if (isAmountSlot(slot) && slot.hasItem()) {
            PartScreens.itemWithAmount(graphics, font, slot.getItem(), slot.x, slot.y);
            return;
        }
        super.extractSlot(graphics, slot, mouseX, mouseY);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        int top = sectionTop();
        if (PartScreens.over(mouseX, mouseY, leftPos + MODE_X, top + MODE_Y, 18, 18)) {
            graphics.setTooltipForNextFrame(Component.translatable(menu.processing() ? "gui.encodedlogistics.encoder.mode.processing"
                    : "gui.encodedlogistics.encoder.mode.crafting"), mouseX, mouseY);
        } else if (PartScreens.over(mouseX, mouseY, leftPos + CLEAR_X, top + CLEAR_Y, CLEAR_SIZE, CLEAR_SIZE)) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.encoder.clear"), mouseX, mouseY);
        }
    }

    private void step(Slot slot, boolean up, boolean shift) {
        int amount = PartScreens.stepAmount(slot.getItem().getCount(), up, shift, SchematicEncoderMenu.MAX_AMOUNT);
        ClientPacketDistributor.sendToServer(new MenuValuePayload(menu.containerId, slot.index - SchematicEncoderMenu.GRID, amount));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int top = sectionTop();
        if (event.button() == 0) {
            int button = PartScreens.over(event.x(), event.y(), leftPos + MODE_X, top + MODE_Y, 18, 18) ? SchematicEncoderMenu.BUTTON_MODE
                    : PartScreens.over(event.x(), event.y(), leftPos + CLEAR_X, top + CLEAR_Y, CLEAR_SIZE, CLEAR_SIZE) ? SchematicEncoderMenu.BUTTON_CLEAR
                            : PartScreens.over(event.x(), event.y(), leftPos + ENCODE_X, top + ENCODE_Y, ENCODE_W, ENCODE_H) && canEncode()
                                    ? SchematicEncoderMenu.BUTTON_ENCODE : -1;
            if (button >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
                return true;
            }
        }
        Slot slot = hoveredSlot;
        if (event.button() == 1 && slot != null && isAmountSlot(slot) && slot.hasItem() && menu.getCarried().isEmpty()) {
            if (menu.processing()) {
                step(slot, true, event.hasShiftDown());
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        Slot slot = hoveredSlot;
        if (slot != null && isAmountSlot(slot) && slot.hasItem() && menu.processing() && scrollY != 0) {
            step(slot, scrollY > 0, minecraft.hasShiftDown());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
