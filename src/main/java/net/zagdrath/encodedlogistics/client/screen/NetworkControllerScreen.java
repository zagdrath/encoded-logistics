/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.NetworkControllerMenu;
import net.zagdrath.encodedlogistics.net.NetworkSnapshotPayload;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.network.NetworkStatus;

// The Network Controller screen: energy gauge, status and channels, Stored / Usage / Generation, and a scrolling
// grid of the devices on the network. Layout and colours are those of screens/controller.json and
// screens/common/palette.json.
public class NetworkControllerScreen extends AbstractContainerScreen<NetworkControllerMenu> {
    private static final Identifier BACKGROUND = EncodedLogistics.id("textures/gui/controller.png");
    private static final Identifier CONTROLLER = EncodedLogistics.id("network_controller");

    // palette.json
    private static final int TEXT = 0xFFF0F0F0, TEXT_MUTED = 0xFFB4B4B4, ACCENT = 0xFF00D992, WARNING = 0xFFE8C24A, ERROR = 0xFFFF6B6B;

    private static final int WIDTH = 208, HEIGHT = 187;
    private static final int GAUGE_X = 9, GAUGE_Y = 19, GAUGE_W = 10, GAUGE_H = 50;
    private static final int LED_X = 28, LED_Y = 22, LED_SIZE = 6;
    private static final int STATUS_X = 38, STATUS_Y = 21, CHANNELS_RIGHT = 196;
    private static final int ROW_LABEL_X = 28, ROW_VALUE_X = 96, STORED_Y = 35, USAGE_Y = 46, GENERATION_Y = 57;
    // The devices label sits midway between the overview box and the device list.
    private static final int DEVICES_X = 8, DEVICES_Y = 78;
    private static final int LIST_X = 10, LIST_Y = 97, COLUMNS = 4, ROWS = 4, CELL_W = 44, CELL_H = 20;
    private static final int ICON_X = 2, ICON_Y = 2, COUNT_X = 22, COUNT_Y = 6;
    private static final int SCROLL_X = 195, SCROLL_Y = 96, SCROLL_H = 82, THUMB_W = 6, THUMB_H = 15;

    private int scrollRow;
    private boolean draggingThumb;

    public NetworkControllerScreen(NetworkControllerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
    }

    private NetworkSnapshot snapshot() {
        return NetworkSnapshotPayload.forMenu(menu.containerId);
    }

    private static Identifier sprite(String name) {
        return EncodedLogistics.id("controller/" + name);
    }

    // --- Drawing ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, x, y, 0.0F, 0.0F, imageWidth, imageHeight, 256, 256);
        NetworkSnapshot snapshot = snapshot();

        // The energy gauge: Arcforge's drawGauge, cropped from the bottom so its segment lines stay put.
        int height = scaled(snapshot.stored(), snapshot.capacity(), GAUGE_H);
        if (height > 0) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite("energy_bar"), GAUGE_W, GAUGE_H, 0, GAUGE_H - height,
                    x + GAUGE_X, y + GAUGE_Y + GAUGE_H - height, GAUGE_W, height);
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(ledName(snapshot.status())), x + LED_X, y + LED_Y, LED_SIZE, LED_SIZE);

        List<NetworkSnapshot.DeviceEntry> devices = snapshot.devices();
        int hovered = hoveredCell(mouseX, mouseY);
        int first = scrollRow * COLUMNS;
        for (int cell = 0; cell < COLUMNS * ROWS && first + cell < devices.size(); cell++) {
            NetworkSnapshot.DeviceEntry entry = devices.get(first + cell);
            int cellX = x + LIST_X + (cell % COLUMNS) * CELL_W;
            int cellY = y + LIST_Y + (cell / COLUMNS) * CELL_H;
            String background = cell == hovered ? "device_entry_hover" : entry.hasError() ? "device_entry_error" : "device_entry";
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(background), cellX, cellY, CELL_W, CELL_H);
            graphics.item(stack(entry), cellX + ICON_X, cellY + ICON_Y);
        }

        String thumb = maxScroll() == 0 ? "scroll_thumb_disabled" : draggingThumb || overThumb(mouseX, mouseY) ? "scroll_thumb_hover" : "scroll_thumb";
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite(thumb), x + SCROLL_X, y + thumbY(), THUMB_W, THUMB_H);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        NetworkSnapshot snapshot = snapshot();
        NetworkStatus status = snapshot.status();
        graphics.text(font, title, titleLabelX, titleLabelY, TEXT, false);

        graphics.text(font, status.description(), STATUS_X, STATUS_Y, statusColor(status), false);
        Component channels = Component.translatable("gui.encodedlogistics.channels", snapshot.channelsUsed(), snapshot.channelCapacity());
        graphics.text(font, channels, CHANNELS_RIGHT - font.width(channels), STATUS_Y, TEXT_MUTED, false);

        row(graphics, "stored", Component.literal(compact(snapshot.stored()) + " / " + compact(snapshot.capacity()) + " FE"), STORED_Y);
        row(graphics, "usage", Component.literal(perTick(snapshot.usage())), USAGE_Y);
        row(graphics, "received", Component.literal(perTick(snapshot.generation())), GENERATION_Y);

        int total = snapshot.devices().stream().mapToInt(NetworkSnapshot.DeviceEntry::count).sum();
        graphics.text(font, Component.translatable("gui.encodedlogistics.devices", snapshot.devices().size(), total), DEVICES_X, DEVICES_Y,
                TEXT_MUTED, false);

        List<NetworkSnapshot.DeviceEntry> devices = snapshot.devices();
        int first = scrollRow * COLUMNS;
        for (int cell = 0; cell < COLUMNS * ROWS && first + cell < devices.size(); cell++) {
            NetworkSnapshot.DeviceEntry entry = devices.get(first + cell);
            int cellX = LIST_X + (cell % COLUMNS) * CELL_W;
            int cellY = LIST_Y + (cell / COLUMNS) * CELL_H;
            graphics.text(font, "x" + entry.count(), cellX + COUNT_X, cellY + COUNT_Y, entry.hasError() ? ERROR : TEXT, false);
        }
    }

    private void row(GuiGraphicsExtractor graphics, String key, Component value, int y) {
        graphics.text(font, Component.translatable("gui.encodedlogistics." + key), ROW_LABEL_X, y, TEXT_MUTED, false);
        graphics.text(font, value, ROW_VALUE_X, y, TEXT, false);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        NetworkSnapshot snapshot = snapshot();
        List<Component> lines = new ArrayList<>();
        int cell = hoveredCell(mouseX, mouseY);
        int index = scrollRow * COLUMNS + cell;
        if (cell >= 0 && index < snapshot.devices().size()) {
            NetworkSnapshot.DeviceEntry entry = snapshot.devices().get(index);
            lines.add(stack(entry).getHoverName());
            if (entry.item().equals(CONTROLLER)) {
                lines.add(structure(snapshot));
            }
            lines.add(Component.translatable("gui.encodedlogistics.tooltip.installed", entry.count()).withColor(TEXT_MUTED));
            lines.add(Component.translatable("gui.encodedlogistics.tooltip.passive_drain", decimal(entry.drain())).withColor(TEXT_MUTED));
            if (entry.unpowered()) {
                lines.add(Component.translatable("gui.encodedlogistics.tooltip.unpowered").withColor(ERROR));
            } else if (entry.missingChannel() > 0) {
                lines.add(Component.translatable("gui.encodedlogistics.tooltip.missing_channel").withColor(ERROR));
            }
        } else if (inside(mouseX, mouseY, GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H)) {
            lines.add(Component.translatable("gui.encodedlogistics.stored"));
            lines.add(Component.literal(String.format(Locale.ROOT, "%,d / %,d FE", snapshot.stored(), snapshot.capacity())).withColor(TEXT_MUTED));
        }
        if (!lines.isEmpty()) {
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    // The controllers' entry: "3x3x3 frame, 20 blocks", "Single block", or what's wrong with the shape.
    private static Component structure(NetworkSnapshot snapshot) {
        NetworkStatus status = snapshot.status();
        if (status == NetworkStatus.INVALID_SHAPE || status == NetworkStatus.TOO_LARGE) {
            return status.description().copy().withColor(ERROR);
        }
        if (snapshot.single()) {
            return Component.translatable("gui.encodedlogistics.structure.single").withColor(ACCENT);
        }
        return Component.translatable("gui.encodedlogistics.structure.value", snapshot.sizeX(), snapshot.sizeY(), snapshot.sizeZ(),
                snapshot.blocks()).withColor(ACCENT);
    }

    // --- Scrolling ---

    private int rows() {
        return (snapshot().devices().size() + COLUMNS - 1) / COLUMNS;
    }

    private int maxScroll() {
        return Math.max(0, rows() - ROWS);
    }

    private int thumbY() {
        int max = maxScroll();
        int travel = SCROLL_H - THUMB_H;
        return SCROLL_Y + (max == 0 ? 0 : Math.round((float) Math.min(scrollRow, max) * travel / max));
    }

    private boolean overThumb(double mouseX, double mouseY) {
        return inside(mouseX, mouseY, SCROLL_X, thumbY(), THUMB_W, THUMB_H);
    }

    private void scrollTo(double mouseY) {
        int max = maxScroll();
        if (max > 0) {
            float position = (float) (mouseY - topPos - SCROLL_Y - THUMB_H / 2.0) / (SCROLL_H - THUMB_H);
            scrollRow = Mth.clamp(Math.round(position * max), 0, max);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll() > 0 && inside(mouseX, mouseY, LIST_X, SCROLL_Y, SCROLL_X + THUMB_W + 1 - LIST_X, SCROLL_H)) {
            scrollRow = Mth.clamp(scrollRow - (int) Math.signum(scrollY), 0, maxScroll());
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0 && maxScroll() > 0 && inside(event.x(), event.y(), SCROLL_X - 1, SCROLL_Y, THUMB_W + 2, SCROLL_H)) {
            draggingThumb = true;
            scrollTo(event.y());
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (draggingThumb) {
            scrollTo(event.y());
            return true;
        }
        return super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingThumb = false;
        return super.mouseReleased(event);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        scrollRow = Math.min(scrollRow, maxScroll());
    }

    // --- Helpers ---

    // The grid cell under the mouse (0 to COLUMNS * ROWS - 1), or -1.
    private int hoveredCell(double mouseX, double mouseY) {
        if (!inside(mouseX, mouseY, LIST_X, LIST_Y, COLUMNS * CELL_W, ROWS * CELL_H)) {
            return -1;
        }
        int column = (int) (mouseX - leftPos - LIST_X) / CELL_W;
        int row = (int) (mouseY - topPos - LIST_Y) / CELL_H;
        return row * COLUMNS + column;
    }

    private boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        double localX = mouseX - leftPos, localY = mouseY - topPos;
        return localX >= x && localX < x + width && localY >= y && localY < y + height;
    }

    private static ItemStack stack(NetworkSnapshot.DeviceEntry entry) {
        Item item = BuiltInRegistries.ITEM.getValue(entry.item());
        return new ItemStack(item);
    }

    private static String ledName(NetworkStatus status) {
        if (status.isError()) {
            return "led_error";
        }
        return status == NetworkStatus.ONLINE ? "led_online" : "led_warning";
    }

    private static int statusColor(NetworkStatus status) {
        if (status.isError()) {
            return ERROR;
        }
        return status == NetworkStatus.ONLINE ? ACCENT : WARNING;
    }

    private static int scaled(long value, long max, int size) {
        return max <= 0 ? 0 : (int) Math.min(size, Math.round((double) value * size / max));
    }

    // 950, 25k, 1.36M: three significant digits, trailing zeros dropped.
    static String compact(long value) {
        if (value < 1000) {
            return Long.toString(value);
        }
        String[] units = { "k", "M", "G", "T" };
        double scaled = value;
        int unit = -1;
        while (scaled >= 1000 && unit < units.length - 1) {
            scaled /= 1000;
            unit++;
        }
        int decimals = scaled >= 100 ? 0 : scaled >= 10 ? 1 : 2;
        return trim(String.format(Locale.ROOT, "%." + decimals + "f", scaled)) + units[unit];
    }

    private static String perTick(double value) {
        return (value >= 1000 ? compact(Math.round(value)) : decimal(value)) + " FE/t";
    }

    // Up to one decimal, no trailing zero: "2", "12.8".
    private static String decimal(double value) {
        return trim(String.format(Locale.ROOT, "%.1f", value));
    }

    private static String trim(String number) {
        return number.contains(".") ? number.replaceAll("0+$", "").replaceAll("\\.$", "") : number;
    }
}
