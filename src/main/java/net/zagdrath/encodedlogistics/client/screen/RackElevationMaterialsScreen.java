/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.item.RackElevationItem;
import net.zagdrath.encodedlogistics.net.RackElevationPayloads;
import net.zagdrath.encodedlogistics.rack.RackTemplate;

// A Rack Elevation's materials list (previews/gui_materials.png, elevation_materials.png): "Have / need" and how many
// items are missing, then a row per device kind - and per planned content, with Contents on - its name and "have / need"
// (green when there's enough, red when short). The counts are the server's (RackElevationPayloads.Materials): the
// player's inventory and the shulker boxes in it, plus the rack network's storage when Use network storage is on (the
// bottom field toggles it) and the player may take from it; in creative (rackElevationCreativeFree) everything's there.
// Opened from the editor's Materials field, or from an apply report's [show] link.
public class RackElevationMaterialsScreen extends Screen {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/rack/elevation_materials.png");
    private static final int WIDTH = 176, HEIGHT = 168, HEADER_Y = 22, FIRST_ROW = 37, ROW = 13, ROWS = 8, RIGHT = 164;
    private static final int FIELD_X = 8, FIELD_Y = 148, FIELD_W = 110, FIELD_H = 14;

    private final @Nullable Screen parent;
    private final long rack;
    private int left, top, scroll, ticks;

    public RackElevationMaterialsScreen(@Nullable Screen parent, long rack) {
        super(Component.translatable("gui.encodedlogistics.rack_elevation.materials"));
        this.parent = parent;
        this.rack = rack;
    }

    // From a report's [show] (the answer is in already), while an elevation is held.
    public static void openFromReport(long rack) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && (minecraft.player.getMainHandItem().getItem() instanceof RackElevationItem
                || minecraft.player.getOffhandItem().getItem() instanceof RackElevationItem)) {
            minecraft.gui.setScreen(new RackElevationMaterialsScreen(null, rack));
        }
    }

    private @Nullable InteractionHand heldHand() {
        if (parent instanceof RackElevationScreen editor) {
            return editor.hand();
        }
        if (minecraft.player == null) {
            return null;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            if (minecraft.player.getItemInHand(hand).getItem() instanceof RackElevationItem) {
                return hand;
            }
        }
        return null;
    }

    private RackTemplate template() {
        InteractionHand hand = heldHand();
        return hand != null && minecraft.player != null ? RackTemplate.orEmpty(minecraft.player.getItemInHand(hand)) : RackTemplate.EMPTY;
    }

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        query();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void query() {
        InteractionHand hand = heldHand();
        if (hand != null) {
            ClientPacketDistributor.sendToServer(new RackElevationPayloads.MaterialsQuery(hand, rack));
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (heldHand() == null) {
            onClose();
            return;
        }
        if (++ticks % 20 == 0) {
            query();
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, left, top, 0.0F, 0.0F, WIDTH, HEIGHT, 256, 256);
        RackTemplate template = template();
        Component heading = template.name().isEmpty() ? title : title.copy().append("  ").append(template.name());
        graphics.text(font, heading, left + 8, top + 5, RackScreen.TEXT, false);
        graphics.text(font, Component.translatable("gui.encodedlogistics.rack_elevation.have_need"), left + 8, top + HEADER_Y, RackScreen.TEXT_MUTED,
                false);
        RackElevationPayloads.Materials materials = RackElevationPayloads.Materials.latest();
        List<RackElevationPayloads.Line> lines = materials != null ? materials.lines() : List.of();
        if (materials != null) {
            Component missing = Component.translatable("gui.encodedlogistics.rack_elevation.missing", materials.missing());
            graphics.text(font, missing, left + WIDTH - 8 - font.width(missing), top + HEADER_Y, materials.missing() > 0 ? RackScreen.ERROR
                    : RackScreen.ACCENT, false);
        }
        scroll = Math.clamp(scroll, 0, Math.max(0, lines.size() - ROWS));
        for (int row = 0; row < ROWS && scroll + row < lines.size(); row++) {
            RackElevationPayloads.Line line = lines.get(scroll + row);
            int y = top + FIRST_ROW + row * ROW + 2;
            String count = (line.have() > 99_999 ? "99999+" : Long.toString(line.have())) + " / " + line.need();
            int countColor = line.enough() ? RackScreen.ACCENT : RackScreen.ERROR;
            graphics.text(font, font.plainSubstrByWidth(line.item().getHoverName().getString(), RIGHT - 14 - font.width(count)), left + 11, y,
                    line.enough() ? RackScreen.TEXT : RackScreen.ERROR, false);
            graphics.text(font, count, left + RIGHT - font.width(count), y, countColor, false);
        }
        if (lines.isEmpty()) {
            graphics.text(font, Component.translatable("gui.encodedlogistics.rack_elevation.nothing"), left + 11, top + FIRST_ROW + 2, RackScreen.TEXT_MUTED,
                    false);
        }
        boolean hover = mouseX >= left + FIELD_X && mouseX < left + FIELD_X + FIELD_W && mouseY >= top + FIELD_Y && mouseY < top + FIELD_Y + FIELD_H;
        graphics.text(font, Component.translatable("gui.encodedlogistics.rack_elevation.use_network"), left + FIELD_X + 3, top + FIELD_Y + 3,
                hover ? RackScreen.ACCENT : RackScreen.TEXT_MUTED, false);
        boolean on = template.useNetworkStorage();
        Component value = Component.translatable(on ? "options.on" : "options.off");
        graphics.text(font, value, left + WIDTH - 8 - font.width(value), top + FIELD_Y + 3, on ? RackScreen.ACCENT : RackScreen.TEXT_MUTED, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        RackElevationPayloads.Materials materials = RackElevationPayloads.Materials.latest();
        if (materials == null || mouseX < left + 8 || mouseX >= left + WIDTH - 8) {
            return;
        }
        int row = (mouseY - top - FIRST_ROW) / ROW;
        if (mouseY >= top + FIRST_ROW && row < ROWS && scroll + row < materials.lines().size()) {
            graphics.setTooltipForNextFrame(font, materials.lines().get(scroll + row).item(), mouseX, mouseY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double x = event.x() - left, y = event.y() - top;
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT && x >= FIELD_X && x < FIELD_X + FIELD_W && y >= FIELD_Y && y < FIELD_Y + FIELD_H) {
            InteractionHand hand = heldHand();
            if (hand != null) {
                boolean on = !template().useNetworkStorage();
                ClientPacketDistributor.sendToServer(RackElevationPayloads.Edit.toggle(hand, RackElevationPayloads.Edit.NETWORK, on));
                if (parent instanceof RackElevationScreen editor) {
                    editor.networkToggled(on);
                }
                query();
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scroll -= (int) Math.signum(scrollY);
        return true;
    }

}
