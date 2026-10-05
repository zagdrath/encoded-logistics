/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.client.ResourceRender;
import net.zagdrath.encodedlogistics.item.ResourceEntryItem;
import net.zagdrath.encodedlogistics.net.GhostSlotPayload;
import net.zagdrath.encodedlogistics.storage.PressurizedSource;
import net.zagdrath.encodedlogistics.storage.PressurizedSources;
import net.zagdrath.encodedlogistics.storage.ResourceType;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// Picking a fluid or gas for a ghost slot from a list, without a container in hand (right-click an empty filter entry
// with an empty hand): every source fluid, and every gas of the loaded PressurizedSources, by name. Typing narrows the
// list; picking sets the slot (GhostSlotPayload with a Resource Entry, as a JEI drag would).
public final class ResourcePicker {
    private static final int ROW = 18, PAD = 3, MAX_ROWS = 8, WIDTH = 150;

    private final int containerId, slot;
    private final List<StorageKey> all;
    private List<StorageKey> shown;
    private String query = "";
    private int x, y, scroll;

    // type: FLUID or PRESSURIZED, or null for both.
    public ResourcePicker(int containerId, int slot, @Nullable ResourceType type, int mouseX, int mouseY, int screenWidth, int screenHeight) {
        this.containerId = containerId;
        this.slot = slot;
        all = candidates(type);
        shown = all;
        x = Math.max(2, Math.min(mouseX, screenWidth - WIDTH - 2));
        y = Math.max(2, Math.min(mouseY, screenHeight - height() - 2));
    }

    public static List<StorageKey> candidates(@Nullable ResourceType type) {
        List<StorageKey> keys = new ArrayList<>();
        if (type == null || type == ResourceType.FLUID) {
            for (Fluid fluid : BuiltInRegistries.FLUID) {
                if (fluid != Fluids.EMPTY && fluid.isSource(fluid.defaultFluidState())) {
                    StorageKey key = StorageKey.fluid(fluid);
                    if (key.is(ResourceType.FLUID)) {
                        keys.add(key);
                    }
                }
            }
        }
        if (type == null || type == ResourceType.PRESSURIZED) {
            for (PressurizedSource source : PressurizedSources.all()) {
                source.all().forEach(gas -> keys.add(StorageKey.pressurized(source.id(), gas)));
            }
        }
        keys.sort(Comparator.comparing(key -> key.displayName().getString(), String.CASE_INSENSITIVE_ORDER));
        return keys;
    }

    private int rows() {
        return Math.max(1, Math.min(MAX_ROWS, shown.size()));
    }

    private int height() {
        return 12 + rows() * ROW + 2 * PAD;
    }

    private int rowAt(double mouseX, double mouseY) {
        if (!PartScreens.over(mouseX, mouseY, x, y + 12 + PAD, WIDTH, rows() * ROW)) {
            return -1;
        }
        int row = scroll + (int) (mouseY - y - 12 - PAD) / ROW;
        return row < shown.size() ? row : -1;
    }

    public void extract(GuiGraphicsExtractor graphics, Font font, int mouseX, int mouseY) {
        int height = height();
        graphics.fill(x - 1, y - 1, x + WIDTH + 1, y + height + 1, 0xFF0E0E0E);
        graphics.fill(x, y, x + WIDTH, y + height, 0xF0262626);
        Component prompt = query.isEmpty() ? Component.translatable("gui.encodedlogistics.picker.search") : Component.literal(query + "_");
        graphics.text(font, prompt, x + PAD + 1, y + PAD, query.isEmpty() ? PartScreens.TEXT_MUTED : PartScreens.TEXT, false);
        if (shown.isEmpty()) {
            graphics.text(font, Component.translatable("gui.encodedlogistics.picker.none"), x + PAD + 1, y + 12 + PAD + 5, PartScreens.TEXT_DISABLED, false);
            return;
        }
        int hovered = rowAt(mouseX, mouseY);
        for (int row = 0; row < rows(); row++) {
            int index = scroll + row;
            if (index >= shown.size()) {
                break;
            }
            int ry = y + 12 + PAD + row * ROW;
            if (index == hovered) {
                graphics.fill(x + 1, ry, x + WIDTH - 1, ry + ROW, 0xFF3A3A3A);
            }
            StorageKey key = shown.get(index);
            ResourceRender.icon(graphics, key, x + PAD, ry + 1);
            graphics.text(font, font.substrByWidth(key.displayName(), WIDTH - 26).getString(), x + PAD + 20, ry + 5, PartScreens.TEXT, false);
        }
        if (shown.size() > MAX_ROWS) {
            int track = rows() * ROW, thumb = Math.max(6, track * MAX_ROWS / shown.size());
            int thumbY = y + 12 + PAD + (track - thumb) * scroll / (shown.size() - MAX_ROWS);
            graphics.fill(x + WIDTH - 3, thumbY, x + WIDTH - 1, thumbY + thumb, 0xFF8A8A8A);
        }
    }

    // A click: on a row it picks that resource for the slot. Returns false when the click was outside (close it).
    public boolean click(double mouseX, double mouseY) {
        int row = rowAt(mouseX, mouseY);
        if (row >= 0) {
            ClientPacketDistributor.sendToServer(new GhostSlotPayload(containerId, slot, ResourceEntryItem.of(shown.get(row), 0)));
            return false;
        }
        return PartScreens.over(mouseX, mouseY, x - 1, y - 1, WIDTH + 2, height() + 2);
    }

    public void scroll(double amount) {
        scroll = Math.clamp(scroll - (int) Math.signum(amount), 0, Math.max(0, shown.size() - MAX_ROWS));
    }

    public void type(char character) {
        if (!Character.isISOControl(character)) {
            query += character;
            refilter();
        }
    }

    public void backspace() {
        if (!query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
            refilter();
        }
    }

    private void refilter() {
        String needle = query.toLowerCase(Locale.ROOT);
        shown = all.stream().filter(key -> key.displayName().getString().toLowerCase(Locale.ROOT).contains(needle)
                || key.id().toString().contains(needle)).toList();
        scroll = 0;
    }
}
