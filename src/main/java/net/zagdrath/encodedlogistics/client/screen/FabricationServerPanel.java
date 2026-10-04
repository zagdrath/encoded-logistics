/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.storage.ValueInput;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackSlot;
import net.zagdrath.encodedlogistics.rack.device.FabricationServerDevice;

// The Fabrication Server's panel (screens/rack/fabrication_server.json): its nine schematic slots, the progress arrow,
// its two Throughput Module slots (a ghost when empty) and the craft it's on.
public class FabricationServerPanel extends RackScreen.Panel {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/rack/fabrication_server.png"),
            PROGRESS = EncodedLogistics.id("lithography_press/progress"), GHOST_MODULE = EncodedLogistics.id("port/ghost_module");
    private static final int PROGRESS_X = 92, PROGRESS_Y = 40, PROGRESS_W = 24, PROGRESS_H = 17, JOB_X = 12, JOB_Y = 88;

    public FabricationServerPanel(RackScreen screen) {
        super(screen);
    }

    // Its job box ends at 123.
    @Override
    protected int height() {
        return 130;
    }

    @Override
    protected Identifier background() {
        return BACKGROUND;
    }

    @Override
    protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        int x = screen.left(), y = screen.top();
        for (int i = FabricationServerDevice.SCHEMATIC_SLOTS; i < RackDeviceType.FABRICATION_SERVER.slots().size(); i++) {
            Slot slot = screen.getMenu().deviceSlot(i);
            RackSlot spec = RackDeviceType.FABRICATION_SERVER.slots().get(i);
            if (slot == null || slot.getItem().isEmpty()) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, GHOST_MODULE, x + spec.x(), y + spec.y(), 16, 16);
            }
        }
        ValueInput data = data();
        int duration = data != null ? data.getIntOr("duration_now", 0) : 0;
        if (duration > 0) {
            int width = Math.round(PROGRESS_W * Math.min(1F, (float) data.getIntOr("progress_now", 0) / duration));
            if (width > 0) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PROGRESS, PROGRESS_W, PROGRESS_H, 0, 0, x + PROGRESS_X, y + PROGRESS_Y, width, PROGRESS_H);
            }
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        ValueInput data = data();
        int duration = data != null ? data.getIntOr("duration_now", 0) : 0;
        if (data == null || duration <= 0) {
            graphics.text(font(), Component.translatable("gui.encodedlogistics.fab.idle"), JOB_X, JOB_Y, RackScreen.TEXT_MUTED, false);
            return;
        }
        graphics.text(font(), font().plainSubstrByWidth(Component.translatable("gui.encodedlogistics.fab.job", data.getStringOr("job", "?"),
                data.getLongOr("job_count", 1)).getString(), 152), JOB_X, JOB_Y, RackScreen.TEXT, false);
        graphics.text(font(), Component.translatable("gui.encodedlogistics.fab.progress", data.getIntOr("progress_now", 0) * 100 / duration),
                JOB_X, JOB_Y + 12, RackScreen.TEXT_MUTED, false);
    }
}
