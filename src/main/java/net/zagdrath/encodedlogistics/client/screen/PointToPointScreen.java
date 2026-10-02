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
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.PointToPointMenu;
import net.zagdrath.encodedlogistics.part.LinkType;

// A Point-to-Point Link's screen (screens/point_to_point_link.json): four buttons for what it carries (the chosen one
// pressed), the in / out button, an Unpair button while paired (type and direction are locked until then, their buttons
// dimmed), and the link's status and partner.
public class PointToPointScreen extends AbstractContainerScreen<PointToPointMenu> {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/point_to_point_link.png");
    private static final Identifier PRESSED = EncodedLogistics.id("terminal/button_pressed");
    private static final Identifier DIR_IN = EncodedLogistics.id("p2p/dir_in"), DIR_OUT = EncodedLogistics.id("p2p/dir_out");
    private static final int TYPE_X = 8, TYPE_Y = 20, TYPE_STEP = 20, DIRECTION_X = 150, DIRECTION_Y = 20, UNPAIR_X = 92, UNPAIR_Y = 20,
            UNPAIR_W = 54, UNPAIR_H = 18, STATUS_X = 12, STATUS_Y = 50, PARTNER_X = 12, PARTNER_Y = 63;

    public PointToPointScreen(PointToPointMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 96);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
    }

    private static Identifier typeIcon(LinkType type) {
        return EncodedLogistics.id("p2p/type_" + type.getSerializedName());
    }

    private boolean locked() {
        return menu.partners() > 0;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        for (LinkType type : LinkType.values()) {
            int bx = x + TYPE_X + type.ordinal() * TYPE_STEP, by = y + TYPE_Y;
            boolean chosen = type == menu.linkType();
            if (chosen) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PRESSED, bx, by, PartScreens.BUTTON_SIZE, PartScreens.BUTTON_SIZE);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, typeIcon(type), bx + 1, by + 1, 16, 16);
            } else {
                PartScreens.button(graphics, bx, by, typeIcon(type), locked() ? -1 : mouseX, mouseY);
                if (locked()) {
                    graphics.fill(bx + 1, by + 1, bx + 17, by + 17, 0x99303030);
                }
            }
        }
        PartScreens.button(graphics, x + DIRECTION_X, y + DIRECTION_Y, menu.output() ? DIR_OUT : DIR_IN, locked() ? -1 : mouseX, mouseY);
        if (locked()) {
            graphics.fill(x + DIRECTION_X + 1, y + DIRECTION_Y + 1, x + DIRECTION_X + 17, y + DIRECTION_Y + 17, 0x99303030);
            PartScreens.wideButton(graphics, font, x + UNPAIR_X, y + UNPAIR_Y, UNPAIR_W, UNPAIR_H, Component.translatable("gui.encodedlogistics.p2p.unpair"),
                    true, mouseX, mouseY);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, title, titleLabelX, titleLabelY, PartScreens.TEXT, false);
        Component status;
        int color = PartScreens.TEXT;
        switch (menu.status()) {
            case PointToPointMenu.STATUS_LINKED -> status = menu.output() || menu.partners() <= 1
                    ? Component.translatable("gui.encodedlogistics.p2p.status.linked")
                    : Component.translatable("gui.encodedlogistics.p2p.status.outputs", menu.partners());
            case PointToPointMenu.STATUS_OFFLINE -> {
                status = Component.translatable("gui.encodedlogistics.p2p.status.offline");
                color = PartScreens.ERROR;
            }
            default -> {
                status = Component.translatable("gui.encodedlogistics.p2p.status.unlinked");
                color = PartScreens.TEXT_MUTED;
            }
        }
        graphics.text(font, Component.translatable(menu.output() ? "gui.encodedlogistics.p2p.output" : "gui.encodedlogistics.p2p.input")
                .append(" - ").append(status), STATUS_X, STATUS_Y, color, false);
        if (menu.partners() > 0) {
            BlockPos partner = menu.partner();
            graphics.text(font, Component.translatable("gui.encodedlogistics.p2p.partner", partner.getX(), partner.getY(), partner.getZ()),
                    PARTNER_X, PARTNER_Y, PartScreens.TEXT_MUTED, false);
        } else {
            graphics.text(font, Component.translatable("gui.encodedlogistics.p2p.hint"), PARTNER_X, PARTNER_Y, PartScreens.TEXT_DISABLED, false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        List<Component> lines = new ArrayList<>();
        for (LinkType type : LinkType.values()) {
            if (PartScreens.over(mouseX, mouseY, leftPos + TYPE_X + type.ordinal() * TYPE_STEP, topPos + TYPE_Y, 18, 18)) {
                lines.add(Component.translatable("gui.encodedlogistics.p2p.type." + type.getSerializedName()));
            }
        }
        if (PartScreens.over(mouseX, mouseY, leftPos + DIRECTION_X, topPos + DIRECTION_Y, 18, 18)) {
            lines.add(Component.translatable(menu.output() ? "gui.encodedlogistics.p2p.output" : "gui.encodedlogistics.p2p.input"));
            lines.add(Component.translatable(menu.output() ? "gui.encodedlogistics.p2p.output.info" : "gui.encodedlogistics.p2p.input.info")
                    .withColor(PartScreens.TEXT_MUTED));
        }
        if (!lines.isEmpty() && locked()) {
            lines.add(Component.translatable("gui.encodedlogistics.p2p.locked").withColor(PartScreens.TEXT_MUTED));
        }
        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            if (locked() && PartScreens.over(event.x(), event.y(), leftPos + UNPAIR_X, topPos + UNPAIR_Y, UNPAIR_W, UNPAIR_H)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PointToPointMenu.BUTTON_UNPAIR);
                return true;
            }
            if (!locked()) {
                for (LinkType type : LinkType.values()) {
                    if (PartScreens.over(event.x(), event.y(), leftPos + TYPE_X + type.ordinal() * TYPE_STEP, topPos + TYPE_Y, 18, 18)) {
                        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PointToPointMenu.BUTTON_TYPE + type.ordinal());
                        return true;
                    }
                }
                if (PartScreens.over(event.x(), event.y(), leftPos + DIRECTION_X, topPos + DIRECTION_Y, 18, 18)) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PointToPointMenu.BUTTON_DIRECTION);
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
