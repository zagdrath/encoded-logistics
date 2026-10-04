/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.List;

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
            ERROR = 0xFFFF6B6B;

    private static final Identifier ELEVATION = EncodedLogistics.id("textures/gui/rack/elevation.png");
    private static final Identifier INVENTORY = EncodedLogistics.id("textures/gui/rack/inventory.png");
    private static final Identifier SLOT_EMPTY = EncodedLogistics.id("rack/slot_empty"), SELECT_1U = EncodedLogistics.id("rack/select_1u"),
            SELECT_2U = EncodedLogistics.id("rack/select_2u"), DROP_1U = EncodedLogistics.id("rack/drop_target_1u"),
            DROP_2U = EncodedLogistics.id("rack/drop_target_2u"), DROP_BLOCKED = EncodedLogistics.id("rack/drop_blocked"),
            SCROLL_THUMB = EncodedLogistics.id("rack/scroll_thumb"), BACK = EncodedLogistics.id("rack/back");

    private static final int INSET_X = 8, INSET_Y = 18, INSET_W = 140, INSET_H = 146;
    private static final int ROW_H = 9, ROWS = 16, FIRST_ROW_Y = 19, NUMBER_RIGHT = 23, SLOT_X = 26, SLOT_W = 104;
    private static final int SCROLL_X = 150, SCROLL_Y = 18, SCROLL_W = 10, SCROLL_H = 146, THUMB_W = 8, THUMB_H = 15;
    private static final int MAX_SCROLL = RackGeometry.UNITS - ROWS;
    static final int BACK_X = 150, BACK_Y = 2;

    // Rows scrolled down from the top (U42); starts at the bottom, showing U1-U16.
    private int scroll = MAX_SCROLL;
    private boolean draggingThumb;
    private @Nullable Panel panel;
    private int panelU;

    public RackScreen(RackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, RackMenu.WIDTH, RackMenu.HEIGHT);
        this.titleLabelX = 8;
        this.titleLabelY = 5;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = RackMenu.TOP_HEIGHT + 6;
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

        protected Font font() {
            return screen.getFont();
        }
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
        Identifier top = panel != null ? panel.background() : ELEVATION;
        graphics.blit(RenderPipelines.GUI_TEXTURED, top, leftPos, topPos, 0.0F, 0.0F, imageWidth, RackMenu.TOP_HEIGHT, 256, 256);
        graphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY, leftPos, topPos + RackMenu.TOP_HEIGHT, 0.0F, 0.0F, imageWidth, 100, 256, 128);
        if (showingPanel()) {
            boolean hover = over(mouseX, mouseY, BACK_X, BACK_Y, 16, 16);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACK, leftPos + BACK_X, topPos + BACK_Y, 16, 16, hover ? 0xFFFFFFFF : 0xFFC8C8C8);
            if (panel != null) {
                panel.extractBackground(graphics, mouseX, mouseY, partialTick);
            }
            return;
        }
        extractElevation(graphics, mouseX, mouseY);
    }

    private void extractElevation(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        RackBlockEntity rack = menu.rack();
        int x = leftPos, y = topPos;
        graphics.enableScissor(x + INSET_X, y + INSET_Y, x + INSET_X + INSET_W, y + INSET_Y + INSET_H);
        for (int row = 0; row < ROWS; row++) {
            int u = unitAtRow(row);
            if (rack == null || rack.deviceAt(u) == null) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_EMPTY, x + SLOT_X, y + rowY(row), SLOT_W, 8);
            }
        }
        if (rack != null) {
            for (RackDevice device : rack.devices()) {
                int topRow = rowOf(device.top());
                if (topRow + device.size() <= 0 || topRow >= ROWS) {
                    continue;
                }
                front(graphics, device, x + SLOT_X, y + rowY(topRow));
                if (device.u() == menu.picked() || hoveredDevice(mouseX, mouseY) == device) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, device.size() > 1 ? SELECT_2U : SELECT_1U, x + SLOT_X - 1, y + rowY(topRow) - 1,
                            SLOT_W + 2, device.size() * ROW_H + 1);
                }
            }
            // Where the carried device would go.
            RackDeviceType carried = RackDeviceType.of(menu.getCarried());
            int u = hoveredUnit(mouseX, mouseY);
            if (carried != null && u > 0 && rack.deviceAt(u) == null) {
                int topU = Math.min(u + carried.size() - 1, RackGeometry.UNITS);
                int drawY = y + rowY(rowOf(topU)) - 1;
                if (rack.fits(u, carried.size())) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, carried.size() > 1 ? DROP_2U : DROP_1U, x + SLOT_X - 1, drawY, SLOT_W + 2,
                            carried.size() * ROW_H + 1);
                } else {
                    for (int unit = u; unit <= topU; unit++) {
                        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DROP_BLOCKED, x + SLOT_X - 1, y + rowY(rowOf(unit)) - 1, SLOT_W + 2, 10);
                    }
                }
            }
        }
        graphics.disableScissor();
        // The scrollbar's thumb.
        int thumbY = y + SCROLL_Y + 1 + Math.round((SCROLL_H - 2 - THUMB_H) * (float) scroll / MAX_SCROLL);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SCROLL_THUMB, x + SCROLL_X + 1, thumbY, THUMB_W, THUMB_H);
    }

    // A device's real front, from its texture, with its lights for its state.
    private void front(GuiGraphicsExtractor graphics, RackDevice device, int x, int y) {
        int height = 8 * device.size();
        graphics.blit(RenderPipelines.GUI_TEXTURED, device.type().texture(""), x, y, 0.0F, 0.0F, SLOT_W, height, 128, 128);
        RackDeviceInfo.Status status = device.shownStatus();
        if (status == RackDeviceInfo.Status.ONLINE) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, device.type().texture("_on"), x, y, 0.0F, 0.0F, SLOT_W, height, 128, 128);
        } else if (status == RackDeviceInfo.Status.FAULT) {
            // Two frames, blinking.
            boolean lit = (System.currentTimeMillis() / 500 & 1) == 0;
            graphics.blit(RenderPipelines.GUI_TEXTURED, device.type().texture("_fault"), x, y, 0.0F, lit ? 0.0F : 128.0F, SLOT_W, height, 128, 256);
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
        for (int row = 0; row < ROWS; row++) {
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
            if (over(mouseX, mouseY, BACK_X, BACK_Y, 16, 16)) {
                graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.rack.back"), mouseX, mouseY);
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
        };
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
        if (x < INSET_X || x >= INSET_X + INSET_W || y < FIRST_ROW_Y || y >= FIRST_ROW_Y + ROWS * ROW_H) {
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
            if (panel != null && mouseY < topPos + RackMenu.TOP_HEIGHT && panel.mouseClicked(mouseX - leftPos, mouseY - topPos, button, shift)) {
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT && over(mouseX, mouseY, SCROLL_X, SCROLL_Y, SCROLL_W, SCROLL_H)) {
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
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        draggingThumb = false;
        return super.mouseReleased(event);
    }

    private void scrollTo(double mouseY) {
        double fraction = (mouseY - topPos - SCROLL_Y - 1 - THUMB_H / 2.0) / (SCROLL_H - 2 - THUMB_H);
        scroll = Mth.clamp((int) Math.round(fraction * MAX_SCROLL), 0, MAX_SCROLL);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showingPanel()) {
            if (panel != null && panel.mouseScrolled(mouseX - leftPos, mouseY - topPos, scrollY)) {
                return true;
            }
        } else if (mouseY < topPos + RackMenu.TOP_HEIGHT && mouseX >= leftPos && mouseX < leftPos + imageWidth) {
            scroll = Mth.clamp(scroll - (int) Math.signum(scrollY) * 2, 0, MAX_SCROLL);
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
