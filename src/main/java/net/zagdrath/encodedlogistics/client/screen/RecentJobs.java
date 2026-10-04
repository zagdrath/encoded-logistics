/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.zagdrath.encodedlogistics.crafting.CraftLog;

// The last few jobs a scheduler finished (CraftLog), as the Scheduler Core's screen and the Compute and Memory Server
// panels list them under the active ones: "Recent" and a hint to Work with Jobs' history above, then a line each -
// "32 x Iron Ingot" and how it ended (Done mint, Failed red, Cancelled grey); hovering one shows what it made, when it
// ended, how long it ran and why it failed.
final class RecentJobs {
    record Row(Component item, long requested, long produced, int status, String reason, String ended, long duration) {
        CraftLog.Status outcome() {
            CraftLog.Status[] values = CraftLog.Status.values();
            return values[Math.clamp(status, 0, values.length - 1)];
        }
    }

    private RecentJobs() {}

    // The heading at (x, y), the hint right-aligned to right.
    static void heading(GuiGraphicsExtractor graphics, Font font, int x, int y, int right) {
        graphics.text(font, Component.translatable("gui.encodedlogistics.history.recent"), x, y, PartScreens.TEXT_MUTED, false);
        Component hint = Component.translatable("gui.encodedlogistics.history.view_all");
        graphics.text(font, hint, right - font.width(hint), y, PartScreens.TEXT_DISABLED, false);
    }

    // The rows from (x, y), rowHeight apart, within width; "No finished jobs yet" when there are none.
    static void rows(GuiGraphicsExtractor graphics, Font font, List<Row> rows, int x, int y, int width, int rowHeight) {
        if (rows.isEmpty()) {
            graphics.text(font, Component.translatable("gui.encodedlogistics.history.none"), x, y, PartScreens.TEXT_DISABLED, false);
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            Component status = status(row.outcome());
            int statusWidth = font.width(status);
            String name = font.plainSubstrByWidth(row.requested() + " x " + row.item().getString(), width - statusWidth - 6);
            graphics.text(font, name, x, y + i * rowHeight, PartScreens.TEXT_MUTED, false);
            graphics.text(font, status, x + width - statusWidth, y + i * rowHeight, color(row.outcome()), false);
        }
    }

    // The row under (mouseX, mouseY), relative to the same origin as rows(), or null.
    static @Nullable Row at(List<Row> rows, double mouseX, double mouseY, int x, int y, int width, int rowHeight) {
        if (mouseX < x || mouseX >= x + width || mouseY < y - 1) {
            return null;
        }
        int index = (int) ((mouseY - y + 1) / rowHeight);
        return index >= 0 && index < rows.size() ? rows.get(index) : null;
    }

    static List<Component> tooltip(Row row) {
        List<Component> lines = new ArrayList<>();
        lines.add(row.item());
        lines.add(Component.translatable("gui.encodedlogistics.history.made", row.produced(), row.requested()).withColor(PartScreens.TEXT_MUTED));
        lines.add(Component.translatable("gui.encodedlogistics.history.ended", row.ended(), CraftLog.duration(row.duration())).withColor(PartScreens.TEXT_MUTED));
        if (!row.reason().isEmpty()) {
            lines.add(Component.translatable("gui.encodedlogistics.job.reason." + row.reason()).withColor(PartScreens.ERROR));
        }
        return lines;
    }

    private static Component status(CraftLog.Status status) {
        return Component.translatable("gui.encodedlogistics.history.status." + status.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static int color(CraftLog.Status status) {
        return switch (status) {
            case DONE -> PartScreens.ACCENT;
            case FAILED -> PartScreens.ERROR;
            case CANCELLED -> PartScreens.TEXT_DISABLED;
        };
    }
}
