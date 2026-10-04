/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.client.rack.RackClientDevices;
import net.zagdrath.encodedlogistics.menu.RackMenu;
import net.zagdrath.encodedlogistics.net.RackActionPayload;
import net.zagdrath.encodedlogistics.net.RackPanelPayload;
import net.zagdrath.encodedlogistics.part.PartFilter;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackGeometry;

// The Server Rack's screen (screens/rack/elevation.json): the rack elevation, U42 at the top and U1 at the bottom,
// sixteen units in view at a time, each device drawn with its real front; the player's inventory below. Pick a device
// for its settings panel (which takes the top half, with a back button), shift-click one to take it out, or click a
// free unit holding a device to mount it there (the drop target shows where it would go, amber if it fits, red if not).
public class RackScreen extends AbstractContainerScreen<RackMenu> {
    // palette.json
    public static final int TEXT = 0xFFF0F0F0, TEXT_MUTED = 0xFFB4B4B4, TEXT_DISABLED = 0xFF7A7A7A, ACCENT = 0xFF00D992, WARNING = 0xFFE8C24A,
            ERROR = 0xFFFF6B6B, AMBER = 0xFFF5B23A;

    private static final Identifier ELEVATION = EncodedLogistics.id("textures/gui/rack/elevation.png");
    private static final Identifier INVENTORY = EncodedLogistics.id("textures/gui/rack/inventory.png");
    private static final Identifier SLOT_EMPTY = EncodedLogistics.id("rack/slot_empty"), SELECT_1U = EncodedLogistics.id("rack/select_1u"),
            SELECT_2U = EncodedLogistics.id("rack/select_2u"), DROP_1U = EncodedLogistics.id("rack/drop_target_1u"),
            DROP_2U = EncodedLogistics.id("rack/drop_target_2u"), DROP_BLOCKED = EncodedLogistics.id("rack/drop_blocked"),
            BACK = EncodedLogistics.id("rack/back");
    private static final Identifier[] PRIORITY = { EncodedLogistics.id("rack/priority/high"), EncodedLogistics.id("rack/priority/normal"),
            EncodedLogistics.id("rack/priority/low") };
    private static final Identifier MARK_HIGH = EncodedLogistics.id("rack/priority/mark_high"), MARK_LOW = EncodedLogistics.id("rack/priority/mark_low");
    // The controller's scrollbar, as on the other screens: an 8 px track, its 6x15 thumb one in from the left.
    private static final Identifier THUMB = EncodedLogistics.id("controller/scroll_thumb"),
            THUMB_HOVER = EncodedLogistics.id("controller/scroll_thumb_hover");

    // At 16 rows (RackMenu.ROWS); the inset and the scrollbar grow with the rows the window has room for.
    private static final int INSET_X = 8, INSET_Y = 18, INSET_W = 140, INSET_H = 146;
    private static final int ROW_H = RackMenu.ROW_H, FIRST_ROW_Y = 19, NUMBER_RIGHT = 23, SLOT_X = 26, SLOT_W = 104;
    private static final int SCROLL_X = 150, SCROLL_Y = 18, SCROLL_W = 8, SCROLL_H = 146, THUMB_W = 6, THUMB_H = 15;
    // The elevation texture's rows repeat every ROW_H from here (a row and its separator line).
    private static final int STRIP_Y = FIRST_ROW_Y + 9 * ROW_H;
    // Panels' backgrounds: this row (plain body and sides) stretches over the extra height.
    private static final int PANEL_FILL_Y = 166;
    static final int BACK_X = 150, BACK_Y = 2, PRIORITY_X = 132;

    // The rows shown (16 to RackMenu.MAX_ROWS, as the window has room) and the extra height they add.
    private final int rows, extra;
    // Rows scrolled down from the top (U42); starts at the top.
    private int scroll;
    private boolean draggingThumb;
    private @Nullable Panel panel;
    private int panelU;

    public RackScreen(RackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, RackMenu.WIDTH, menu.topHeight() + RackMenu.INVENTORY_HEIGHT);
        this.rows = menu.rows();
        this.extra = menu.topHeight() - RackMenu.TOP_HEIGHT;
        this.titleLabelX = 8;
        this.titleLabelY = 5;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = menu.topHeight() + 6;
    }

    private int maxScroll() {
        return RackGeometry.UNITS - rows;
    }

    // --- What panels use ---

    // A device's settings panel, in the top half.
    public abstract static class Panel {
        protected final RackScreen screen;

        protected Panel(RackScreen screen) {
            this.screen = screen;
        }

        // The top half's background texture (176x168 of a 256x256).
        protected abstract Identifier background();

        protected void init() {}

        protected void removed() {}

        protected void tick() {}

        // Screen coordinates (the panel's top-left is screen.left(), screen.top()).
        protected void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {}

        // In the pose labels use: (0, 0) is the panel's top-left.
        protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {}

        protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {}

        // x, y relative to the panel.
        protected boolean mouseClicked(double x, double y, int button, boolean shift) {
            return false;
        }

        protected boolean mouseScrolled(double x, double y, double amount) {
            return false;
        }

        // A drag with the left button, x, y relative to the panel; true when the panel took it.
        protected boolean mouseDragged(double x, double y) {
            return false;
        }

        protected void mouseReleased() {}

        protected boolean keyPressed(KeyEvent event) {
            return false;
        }

        // The device's state the server last sent (RackDevice#writePanel), or null.
        protected @Nullable ValueInput data() {
            CompoundTag tag = RackPanelPayload.forMenu(screen.getMenu().containerId, screen.panelU);
            return tag != null && screen.getMinecraft().level != null
                    ? TagValueInput.create(ProblemReporter.DISCARDING, screen.getMinecraft().level.registryAccess(), tag) : null;
        }

        // The device rebuilt from that state (once per state received), or null.
        private @Nullable CompoundTag builtFrom;
        private @Nullable RackDevice built;

        protected <T extends RackDevice> @Nullable T device(Class<T> type) {
            CompoundTag tag = RackPanelPayload.forMenu(screen.getMenu().containerId, screen.panelU);
            RackDevice device = screen.pickedDevice();
            if (tag == null || device == null || !type.isInstance(device)) {
                return null;
            }
            if (tag != builtFrom) {
                ValueInput input = data();
                if (input == null) {
                    return null;
                }
                built = device.type().create();
                built.load(input);
                builtFrom = tag;
            }
            return type.isInstance(built) ? type.cast(built) : null;
        }

        protected void send(int action, int value, String text) {
            ClientPacketDistributor.sendToServer(new RackActionPayload(screen.getMenu().containerId, screen.panelU, action, value, text));
        }

        // An action naming an item (a filter dragged in from JEI): the device reads it as RackDevice#filter.
        protected void sendItem(int action, int value, ItemStack stack) {
            send(action, value, BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        }

        // Where an item can be dropped as a filter (JEI's ghost drag), in screen coordinates.
        protected List<GhostTarget> ghostTargets() {
            return List.of();
        }

        protected Font font() {
            return screen.getFont();
        }
    }

    // A filter box an item dragged from JEI can be dropped on.
    public record GhostTarget(int x, int y, int width, int height, Consumer<ItemStack> accept) {}

    public List<GhostTarget> ghostTargets() {
        return panel != null ? panel.ghostTargets() : List.of();
    }

    int left() {
        return leftPos;
    }

    int top() {
        return topPos;
    }

    <T extends GuiEventListener & Renderable & NarratableEntry> T addPanelWidget(T widget) {
        return addRenderableWidget(widget);
    }

    void removePanelWidget(GuiEventListener widget) {
        removeWidget(widget);
    }

    @Nullable RackDevice pickedDevice() {
        return menu.pickedDevice();
    }

    // --- Following the picked device ---

    @Override
    protected void containerTick() {
        super.containerTick();
        int picked = menu.picked();
        if (picked != panelU) {
            if (panel != null) {
                panel.removed();
            }
            panelU = picked;
            RackDevice device = menu.pickedDevice();
            panel = device != null ? RackClientDevices.panel(device.type(), this) : null;
            if (panel != null) {
                panel.init();
            }
        }
        if (panel != null) {
            panel.tick();
        }
    }

    @Override
    public void removed() {
        if (panel != null) {
            panel.removed();
        }
        super.removed();
    }

    private boolean showingPanel() {
        return panelU > 0;
    }

    // --- Drawing ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        int topHeight = menu.topHeight();
        if (showingPanel()) {
            // A panel's background, its plain bottom stretched over the extra height.
            Identifier top = panel != null ? panel.background() : ELEVATION;
            graphics.blit(RenderPipelines.GUI_TEXTURED, top, leftPos, topPos, 0.0F, 0.0F, imageWidth, PANEL_FILL_Y, 256, 256);
            graphics.blit(RenderPipelines.GUI_TEXTURED, top, leftPos, topPos + PANEL_FILL_Y, 0.0F, PANEL_FILL_Y, imageWidth, extra, imageWidth, 1, 256, 256);
            graphics.blit(RenderPipelines.GUI_TEXTURED, top, leftPos, topPos + PANEL_FILL_Y + extra, 0.0F, PANEL_FILL_Y, imageWidth,
                    RackMenu.TOP_HEIGHT - PANEL_FILL_Y, 256, 256);
        } else {
            // The elevation: its top, a row's strip for each extra row, its bottom.
            graphics.blit(RenderPipelines.GUI_TEXTURED, ELEVATION, leftPos, topPos, 0.0F, 0.0F, imageWidth, STRIP_Y, 256, 256);
            for (int i = 0; i < extra / ROW_H; i++) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, ELEVATION, leftPos, topPos + STRIP_Y + i * ROW_H, 0.0F, STRIP_Y, imageWidth, ROW_H, 256, 256);
            }
            graphics.blit(RenderPipelines.GUI_TEXTURED, ELEVATION, leftPos, topPos + STRIP_Y + extra, 0.0F, STRIP_Y, imageWidth,
                    RackMenu.TOP_HEIGHT - STRIP_Y, 256, 256);
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY, leftPos, topPos + topHeight, 0.0F, 0.0F, imageWidth, RackMenu.INVENTORY_HEIGHT, 256, 128);
        if (showingPanel()) {
            boolean hover = over(mouseX, mouseY, BACK_X, BACK_Y, 16, 16);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACK, leftPos + BACK_X, topPos + BACK_Y, 16, 16, hover ? 0xFFFFFFFF : 0xFFC8C8C8);
            if (panel != null) {
                RackDevice picked = menu.pickedDevice();
                if (picked != null) {
                    boolean overPriority = over(mouseX, mouseY, PRIORITY_X, BACK_Y, 16, 16);
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, overPriority ? PartScreens.BUTTON_HOVER : PartScreens.BUTTON, leftPos + PRIORITY_X - 1,
                            topPos + BACK_Y - 1, 18, 18);
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PRIORITY[picked.lanePriority().ordinal()], leftPos + PRIORITY_X, topPos + BACK_Y, 16, 16);
                }
                panel.extractBackground(graphics, mouseX, mouseY, partialTick);
            }
            return;
        }
        extractElevation(graphics, mouseX, mouseY);
    }

    private void extractElevation(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        RackBlockEntity rack = menu.rack();
        int x = leftPos, y = topPos;
        graphics.enableScissor(x + INSET_X, y + INSET_Y, x + INSET_X + INSET_W, y + INSET_Y + INSET_H + extra);
        for (int row = 0; row < rows; row++) {
            int u = unitAtRow(row);
            if (rack == null || rack.deviceAt(u) == null) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_EMPTY, x + SLOT_X, y + rowY(row), SLOT_W, 8);
            }
        }
        if (rack != null) {
            for (RackDevice device : rack.devices()) {
                int topRow = rowOf(device.top());
                if (topRow + device.size() <= 0 || topRow >= rows) {
                    continue;
                }
                front(graphics, device, x + SLOT_X, y + rowY(topRow));
                if (device.lanePriority() != RackDevice.Priority.NORMAL) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, device.lanePriority() == RackDevice.Priority.HIGH ? MARK_HIGH : MARK_LOW, x + SLOT_X,
                            y + rowY(topRow), 2, 7);
                }
                if (device.u() == menu.picked() || hoveredDevice(mouseX, mouseY) == device) {
                    if (device.size() <= 2) {
                        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, device.size() > 1 ? SELECT_2U : SELECT_1U, x + SLOT_X - 1,
                                y + rowY(topRow) - 1, SLOT_W + 2, device.size() * ROW_H + 1);
                    } else {
                        outline(graphics, x + SLOT_X - 1, y + rowY(topRow) - 1, SLOT_W + 2, device.size() * ROW_H + 1, SELECT, false);
                    }
                }
            }
            // Where the carried device would go.
            RackDeviceType carried = RackDeviceType.of(menu.getCarried());
            int u = hoveredUnit(mouseX, mouseY);
            if (carried != null && u > 0 && rack.deviceAt(u) == null) {
                int topU = Math.min(u + carried.size() - 1, RackGeometry.UNITS);
                int drawY = y + rowY(rowOf(topU)) - 1;
                if (rack.fits(u, carried.size()) && carried.size() <= 2) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, carried.size() > 1 ? DROP_2U : DROP_1U, x + SLOT_X - 1, drawY, SLOT_W + 2,
                            carried.size() * ROW_H + 1);
                } else if (rack.fits(u, carried.size())) {
                    outline(graphics, x + SLOT_X - 1, drawY, SLOT_W + 2, carried.size() * ROW_H + 1, DROP, true);
                } else {
                    for (int unit = u; unit <= topU; unit++) {
                        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DROP_BLOCKED, x + SLOT_X - 1, y + rowY(rowOf(unit)) - 1, SLOT_W + 2, 10);
                    }
                }
            }
        }
        graphics.disableScissor();
        // The scrollbar's thumb (none when every unit shows).
        if (maxScroll() <= 0) {
            return;
        }
        int thumbY = y + thumbTop();
        boolean hover = draggingThumb || over(mouseX, mouseY, SCROLL_X + 1, thumbTop(), THUMB_W, THUMB_H);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? THUMB_HOVER : THUMB, x + SCROLL_X + 1, thumbY, THUMB_W, THUMB_H);
    }

    // A route's filter box (the 12x12 item at x+1, y+1 inside it): a dark well, its border lit while hovered.
    public static void filterBox(GuiGraphicsExtractor graphics, int x, int y, boolean hovered) {
        filterBox(graphics, x, y, 14, hovered);
    }

    // The same, size square (18 for a full-size item).
    public static void filterBox(GuiGraphicsExtractor graphics, int x, int y, int size, boolean hovered) {
        filterBox(graphics, x, y, size, hovered ? ACCENT : 0xFF5A5A5A);
    }

    // With its border in a colour.
    public static void filterBox(GuiGraphicsExtractor graphics, int x, int y, int size, int border) {
        graphics.fill(x, y, x + size, y + size, border);
        graphics.fill(x + 1, y + 1, x + size - 1, y + size - 1, 0xFF1C1C1C);
    }

    // A route's filter box in its row (the Router's, the L3 Switch's): its first entry, a "*" (drawn with the labels) for
    // an empty deny list, nothing for an empty allow list; a deny list's border red. Screen coordinates.
    public static void routeBox(GuiGraphicsExtractor graphics, PartFilter filter, int x, int y, boolean hovered) {
        filterBox(graphics, x, y, 14, hovered ? ACCENT : filter.deny() ? 0xFF9A4040 : 0xFF5A5A5A);
        List<ItemStack> entries = filter.nonEmpty();
        if (!entries.isEmpty()) {
            graphics.pose().pushMatrix();
            graphics.pose().translate(x + 1, y + 1);
            graphics.pose().scale(0.75F, 0.75F);
            graphics.item(entries.getFirst(), 0, 0);
            graphics.pose().popMatrix();
        }
    }

    // The selection and drop-target outlines drawn for devices taller than the 1U and 2U sprites (3U, 4U): the same
    // 1 px line, solid or dashed.
    private static final int SELECT = 0xFF5CF0B8, DROP = 0xFFF5B23A;

    private static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color, boolean dashed) {
        for (int i = 0; i < width; i++) {
            if (!dashed || i % 2 == 0) {
                graphics.fill(x + i, y, x + i + 1, y + 1, color);
                graphics.fill(x + i, y + height - 1, x + i + 1, y + height, color);
            }
        }
        for (int i = 1; i < height - 1; i++) {
            if (!dashed || i % 2 == 0) {
                graphics.fill(x, y + i, x + 1, y + i + 1, color);
                graphics.fill(x + width - 1, y + i, x + width, y + i + 1, color);
            }
        }
    }

    // A device's real front, from its texture (128 x sheetHeight, frames that tall), with its lights for its state (an animated overlay shown frame by frame,
    // as the block does: 3 ticks a frame).
    private void front(GuiGraphicsExtractor graphics, RackDevice device, int x, int y) {
        int height = 8 * device.size(), sheet = device.type().sheetHeight();
        graphics.blit(RenderPipelines.GUI_TEXTURED, device.type().texture(""), x, y, 0.0F, 0.0F, SLOT_W, height, 128, sheet);
        RackDeviceInfo.Status status = device.shownStatus();
        String variant = device.shownVariant();
        Integer variantFrames = variant != null ? device.type().variants().get(variant) : null;
        if (variantFrames != null) {
            int frame = (int) (System.currentTimeMillis() / 200 % variantFrames);
            graphics.blit(RenderPipelines.GUI_TEXTURED, device.type().texture(variant), x, y, 0.0F, frame * sheet, SLOT_W, height, 128,
                    sheet * variantFrames);
        } else if (status == RackDeviceInfo.Status.ONLINE || status == RackDeviceInfo.Status.WARNING) {
            int frames = device.type().frames();
            int frame = (int) (System.currentTimeMillis() / 150 % frames);
            graphics.blit(RenderPipelines.GUI_TEXTURED, device.type().texture("_on"), x, y, 0.0F, frame * sheet, SLOT_W, height, 128, sheet * frames);
        } else if (status == RackDeviceInfo.Status.FAULT) {
            // Two frames, blinking.
            boolean lit = (System.currentTimeMillis() / 500 & 1) == 0;
            graphics.blit(RenderPipelines.GUI_TEXTURED, device.type().texture("_fault"), x, y, 0.0F, lit ? 0.0F : sheet, SLOT_W, height, 128, sheet * 2);
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, TEXT_MUTED, false);
        if (showingPanel()) {
            RackDevice device = menu.pickedDevice();
            if (device != null) {
                graphics.text(font, device.name(), titleLabelX, titleLabelY, TEXT, false);
            }
            if (panel != null) {
                panel.extractLabels(graphics, mouseX, mouseY);
            }
            return;
        }
        graphics.text(font, title, titleLabelX, titleLabelY, TEXT, false);
        RackBlockEntity rack = menu.rack();
        if (rack != null) {
            Component free = Component.translatable("gui.encodedlogistics.rack.free", rack.freeUnits());
            graphics.text(font, free, 168 - font.width(free), titleLabelY, TEXT_MUTED, false);
        }
        for (int row = 0; row < rows; row++) {
            int u = unitAtRow(row);
            String number = Integer.toString(u);
            boolean used = rack != null && rack.deviceAt(u) != null;
            graphics.text(font, number, NUMBER_RIGHT - font.width(number), rowY(row), used ? TEXT : TEXT_MUTED, false);
        }
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        if (showingPanel()) {
            RackDevice picked = menu.pickedDevice();
            if (over(mouseX, mouseY, BACK_X, BACK_Y, 16, 16)) {
                graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.rack.back"), mouseX, mouseY);
            } else if (panel != null && picked != null && over(mouseX, mouseY, PRIORITY_X, BACK_Y, 16, 16)) {
                graphics.setComponentTooltipForNextFrame(font, List.of(Component.translatable("gui.encodedlogistics.rack.priority", picked.lanePriority().label()),
                        Component.translatable("gui.encodedlogistics.rack.priority.hint").withColor(TEXT_MUTED)), mouseX, mouseY);
            } else if (panel != null) {
                panel.extractTooltip(graphics, mouseX, mouseY);
            }
            return;
        }
        RackBlockEntity rack = menu.rack();
        int u = hoveredUnit(mouseX, mouseY);
        if (rack == null || u == 0) {
            return;
        }
        RackDeviceType carried = RackDeviceType.of(menu.getCarried());
        if (carried != null && !rack.fits(u, carried.size()) && rack.deviceAt(u) == null) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.rack.no_room", carried.size()).withColor(ERROR), mouseX, mouseY);
            return;
        }
        RackDevice device = rack.deviceAt(u);
        if (device != null && carried == null) {
            List<Component> lines = new ArrayList<>();
            lines.add(device.name());
            lines.add(unitRange(device).copy().withColor(TEXT_MUTED));
            RackDeviceInfo.Status status = device.shownStatus();
            lines.add(status.text().copy().withColor(statusColor(status)));
            lines.add(Component.translatable("gui.encodedlogistics.rack.hint").withColor(TEXT_DISABLED));
            graphics.setComponentTooltipForNextFrame(font, lines, mouseX, mouseY);
        }
    }

    public static Component unitRange(RackDevice device) {
        return device.size() > 1 ? Component.translatable("gui.encodedlogistics.rack.hud.u2", device.u(), device.top())
                : Component.translatable("gui.encodedlogistics.rack.hud.u1", device.u());
    }

    public static int statusColor(RackDeviceInfo.Status status) {
        return switch (status) {
            case ONLINE -> ACCENT;
            case OFFLINE -> TEXT_MUTED;
            case FAULT -> ERROR;
            case WARNING -> AMBER;
        };
    }

    // The thumb's top, relative to the screen, inside the track's 1 px rim.
    private int thumbTop() {
        return SCROLL_Y + 1 + Math.round((SCROLL_H + extra - 2 - THUMB_H) * (float) scroll / maxScroll());
    }

    // --- Rows ---

    private int unitAtRow(int row) {
        return RackGeometry.UNITS - scroll - row;
    }

    private int rowOf(int u) {
        return RackGeometry.UNITS - scroll - u;
    }

    private static int rowY(int row) {
        return FIRST_ROW_Y + row * ROW_H;
    }

    // The unit under the mouse in the elevation, or 0.
    private int hoveredUnit(double mouseX, double mouseY) {
        double x = mouseX - leftPos, y = mouseY - topPos;
        if (x < INSET_X || x >= INSET_X + INSET_W || y < FIRST_ROW_Y || y >= FIRST_ROW_Y + rows * ROW_H) {
            return 0;
        }
        return unitAtRow(Mth.floor((y - FIRST_ROW_Y) / ROW_H));
    }

    private @Nullable RackDevice hoveredDevice(double mouseX, double mouseY) {
        RackBlockEntity rack = menu.rack();
        int u = hoveredUnit(mouseX, mouseY);
        return rack != null && u > 0 ? rack.deviceAt(u) : null;
    }

    boolean over(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + width && mouseY >= topPos + y && mouseY < topPos + y + height;
    }

    // --- Input ---

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        int button = event.button();
        boolean shift = event.hasShiftDown();
        if (showingPanel()) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT && over(mouseX, mouseY, BACK_X, BACK_Y, 16, 16)) {
                buttonClick(RackMenu.PICK);
                return true;
            }
            if (button == InputConstants.MOUSE_BUTTON_LEFT && panel != null && menu.pickedDevice() != null && over(mouseX, mouseY, PRIORITY_X, BACK_Y, 16, 16)) {
                ClientPacketDistributor.sendToServer(new RackActionPayload(menu.containerId, panelU, RackDevice.ACTION_PRIORITY, 0, ""));
                return true;
            }
            if (panel != null && mouseY < topPos + menu.topHeight() && panel.mouseClicked(mouseX - leftPos, mouseY - topPos, button, shift)) {
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT && maxScroll() > 0 && over(mouseX, mouseY, SCROLL_X, SCROLL_Y, SCROLL_W, SCROLL_H + extra)) {
            draggingThumb = true;
            scrollTo(mouseY);
            return true;
        }
        int u = hoveredUnit(mouseX, mouseY);
        RackBlockEntity rack = menu.rack();
        if (u > 0 && rack != null && button == InputConstants.MOUSE_BUTTON_LEFT) {
            RackDevice device = rack.deviceAt(u);
            if (RackDeviceType.of(menu.getCarried()) != null) {
                if (device == null) {
                    buttonClick(RackMenu.PUT + u);
                }
                return true;
            }
            if (device != null) {
                buttonClick((shift ? RackMenu.TAKE : RackMenu.PICK) + u);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (draggingThumb) {
            scrollTo(event.y());
            return true;
        }
        if (showingPanel() && panel != null && panel.mouseDragged(event.x() - leftPos, event.y() - topPos)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingThumb = false;
        if (panel != null) {
            panel.mouseReleased();
        }
        return super.mouseReleased(event);
    }

    private void scrollTo(double mouseY) {
        double fraction = (mouseY - topPos - SCROLL_Y - 1 - THUMB_H / 2.0) / (SCROLL_H + extra - 2 - THUMB_H);
        scroll = Mth.clamp((int) Math.round(fraction * maxScroll()), 0, Math.max(0, maxScroll()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showingPanel()) {
            if (panel != null && panel.mouseScrolled(mouseX - leftPos, mouseY - topPos, scrollY)) {
                return true;
            }
        } else if (mouseY < topPos + menu.topHeight() && mouseX >= leftPos && mouseX < leftPos + imageWidth) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * 2, 0, Math.max(0, maxScroll()));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (panel != null && panel.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    private void buttonClick(int id) {
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    // The carried stack, for panels that set ghost filters.
    ItemStack carried() {
        return menu.getCarried();
    }
}
