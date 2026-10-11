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

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.data.AtlasIds;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.ValueInput;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.client.SneakKey;
import net.zagdrath.encodedlogistics.client.rack.RackClientDevices;
import net.zagdrath.encodedlogistics.client.rack.RackHud;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.menu.RackMenu;
import net.zagdrath.encodedlogistics.net.RackActionPayload;
import net.zagdrath.encodedlogistics.net.RackPanelPayload;
import net.zagdrath.encodedlogistics.net.RackUnitPayloads;
import net.zagdrath.encodedlogistics.part.PartFilter;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.StorageDevice;
import net.zagdrath.encodedlogistics.rack.device.SanDevice;
import net.zagdrath.encodedlogistics.storage.ResourceType;

// The Server Rack's screen (screens/rack/elevation.json): the rack elevation, U42 at the top and U1 at the bottom,
// sixteen units in view at a time, each device drawn with its real front; the player's inventory below. Pick a device
// for its settings panel (which takes the top half, with a back button), shift-click one to take it out, or click a
// free unit holding a device to mount it there (the drop target shows where it would go, amber if it fits, red if not).
public class RackScreen extends AbstractContainerScreen<RackMenu> {
    // palette.json
    public static final int TEXT = 0xFFF0F0F0, TEXT_MUTED = 0xFFB4B4B4, TEXT_DISABLED = 0xFF7A7A7A, ACCENT = 0xFF00D992, WARNING = 0xFFE8C24A,
            ERROR = 0xFFFF6B6B, AMBER = 0xFFF5B23A;

    private static final Identifier INVENTORY = EncodedLogistics.id("textures/gui/rack/inventory.png");
    private static final Identifier BACK = EncodedLogistics.id("rack/back");
    private static final Identifier[] PRIORITY = { EncodedLogistics.id("rack/priority/high"), EncodedLogistics.id("rack/priority/normal"),
            EncodedLogistics.id("rack/priority/low") };
    private static final Identifier MARK_HIGH = EncodedLogistics.id("rack/priority/mark_high"), MARK_LOW = EncodedLogistics.id("rack/priority/mark_low");

    private static final int SLOT_X = RackElevationView.SLOT_X;
    // Panels' backgrounds: this row (plain body and sides) stretches over the extra height.
    private static final int PANEL_FILL_Y = 166;
    static final int BACK_X = 150, BACK_Y = 2, PRIORITY_X = 132;

    // The elevation (RackElevationView: 16 to RackMenu.MAX_ROWS rows, as the window has room).
    private final RackElevationView view;
    private @Nullable Panel panel;
    private int panelU;

    public RackScreen(RackMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, RackMenu.WIDTH, menu.topHeight() + RackMenu.INVENTORY_HEIGHT);
        this.view = new RackElevationView(menu.rows());
        this.titleLabelX = 8;
        this.titleLabelY = 5;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = menu.topHeight() + 6;
    }

    // The top half's height now: the elevation's (it grows with the window), or the open panel's own.
    private int topHeight() {
        if (!showingPanel()) {
            return menu.topHeight();
        }
        return panel != null ? Math.min(RackMenu.TOP_HEIGHT, panel.height()) : RackMenu.TOP_HEIGHT;
    }

    // The screen's height now (imageHeight stays what it was when the screen opened).
    private int screenHeight() {
        return topHeight() + RackMenu.INVENTORY_HEIGHT;
    }

    @Override
    public int getImageHeight() {
        return screenHeight();
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top) {
        return mouseX < left || mouseY < top || mouseX >= left + imageWidth || mouseY >= top + screenHeight();
    }

    @Override
    protected void init() {
        super.init();
        relayout();
    }

    // Sizes the screen to the top half shown, and moves the player's inventory under it (on the client: the server never
    // looks at slot positions; MovedSlot).
    private void relayout() {
        int top = topHeight();
        topPos = (height - screenHeight()) / 2;
        inventoryLabelY = top + 6;
        int dy = top - menu.topHeight();
        for (int i = 0; i < menu.slots.size(); i++) {
            Slot slot = menu.slots.get(i);
            Slot base = slot instanceof MovedSlot moved ? moved.base : slot;
            if (base.container instanceof Inventory) {
                menu.slots.set(i, MovedSlot.of(slot, dy));
            }
        }
    }

    private int lastMouseX, lastMouseY;

    // --- What panels use ---

    // A device's settings panel, in the top half.
    public abstract static class Panel {
        protected final RackScreen screen;

        protected Panel(RackScreen screen) {
            this.screen = screen;
        }

        // How tall it is: the top half's height while it shows (its content and a margin; at most RackMenu.TOP_HEIGHT).
        // The screen shrinks to it and the inventory moves up under it.
        protected int height() {
            return RackMenu.TOP_HEIGHT;
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
            relayout();
            if (panel != null) {
                panel.init();
            }
        }
        queryHovered(lastMouseX, lastMouseY);
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
        int topHeight = topHeight();
        if (showingPanel()) {
            // A panel's background down to its height, then the texture's plain bottom rows.
            Identifier top = panel != null ? panel.background() : RackElevationView.ELEVATION;
            int bottom = RackMenu.TOP_HEIGHT - PANEL_FILL_Y;
            graphics.blit(RenderPipelines.GUI_TEXTURED, top, leftPos, topPos, 0.0F, 0.0F, imageWidth, topHeight - bottom, 256, 256);
            graphics.blit(RenderPipelines.GUI_TEXTURED, top, leftPos, topPos + topHeight - bottom, 0.0F, PANEL_FILL_Y, imageWidth, bottom, 256, 256);
        } else {
            view.background(graphics, leftPos, topPos);
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, INVENTORY, leftPos, topPos + topHeight, 0.0F, 0.0F, imageWidth, RackMenu.INVENTORY_HEIGHT, 256, 128);
        if (showingPanel()) {
            boolean hover = over(mouseX, mouseY, BACK_X, BACK_Y, 16, 16);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACK, leftPos + BACK_X, topPos + BACK_Y, 16, 16, hover ? 0xFFFFFFFF : 0xFFC8C8C8);
            if (panel != null) {
                RackDevice picked = menu.pickedDevice();
                if (picked != null) {
                    // Like the back button: the icon alone, brighter when hovered, in line with the back arrow.
                    boolean overPriority = over(mouseX, mouseY, PRIORITY_X, BACK_Y, 16, 16);
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PRIORITY[picked.lanePriority().ordinal()], leftPos + PRIORITY_X, topPos + BACK_Y, 16, 16,
                            overPriority ? 0xFFFFFFFF : 0xFFC8C8C8);
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
        view.begin(graphics, x, y);
        view.emptySlots(graphics, x, y, u -> rack == null || rack.deviceAt(u) == null);
        if (rack != null) {
            for (RackDevice device : rack.devices()) {
                if (!view.visible(device.u(), device.size())) {
                    continue;
                }
                view.device(graphics, x, y, device, device.u(), -1);
                if (device.lanePriority() != RackDevice.Priority.NORMAL) {
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, device.lanePriority() == RackDevice.Priority.HIGH ? MARK_HIGH : MARK_LOW, x + SLOT_X,
                            y + RackElevationView.rowY(view.rowOf(device.top())), 2, 7);
                }
                if (device.u() == menu.picked() || hoveredDevice(mouseX, mouseY) == device) {
                    view.select(graphics, x, y, device.u(), device.size());
                }
            }
            // Where the carried device would go.
            RackDeviceType carried = RackDeviceType.of(menu.getCarried());
            int u = hoveredUnit(mouseX, mouseY);
            if (carried != null && u > 0 && rack.deviceAt(u) == null) {
                view.drop(graphics, x, y, u, carried.size(), rack.fits(u, carried.size()));
            }
        }
        view.end(graphics);
        view.thumb(graphics, x, y, mouseX, mouseY);
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
        view.numbers(graphics, font, u -> rack != null && rack.deviceAt(u) != null);
    }

    @Override
    protected void extractTooltip(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        super.extractTooltip(graphics, mouseX, mouseY);
        lastMouseX = mouseX;
        lastMouseY = mouseY;
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
            // The same popup as looking at the rack, with the screen's hint under it (once the server's answer is in).
            RackDeviceInfo info = RackUnitPayloads.Info.forUnit(rack.getBlockPos(), device.u(), device.type());
            if (info != null) {
                graphics.nextStratum();
                RackHud.popup(graphics, font, device, info, mouseX + 12, mouseY - 8,
                        Component.translatable(RackClientDevices.hasPanel(device.type()) ? "gui.encodedlogistics.rack.hint" : "gui.encodedlogistics.rack.hint_take",
                                SneakKey.name()));
            }
        }
    }

    // Asks the server about the device under the mouse: at once, then every QUERY_INTERVAL ticks.
    private static final int QUERY_INTERVAL = 10;
    private int hoveredQueried = -1, queryTicks;

    private void queryHovered(int mouseX, int mouseY) {
        RackBlockEntity rack = menu.rack();
        int u = showingPanel() ? 0 : hoveredUnit(mouseX, mouseY);
        RackDevice device = rack != null && u > 0 ? rack.deviceAt(u) : null;
        int unit = device != null ? device.u() : -1;
        if (unit != hoveredQueried) {
            hoveredQueried = unit;
            queryTicks = 0;
        }
        if (device != null && queryTicks++ % QUERY_INTERVAL == 0) {
            ClientPacketDistributor.sendToServer(new RackUnitPayloads.Query(rack.getBlockPos(), device.u()));
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

    // --- Rows ---

    // The unit under the mouse in the elevation, or 0.
    private int hoveredUnit(double mouseX, double mouseY) {
        return view.unitAt(mouseX - leftPos, mouseY - topPos);
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
        // Sneak-clicks follow the vanilla Sneak key (Shift by default).
        boolean shift = SneakKey.held(event.hasShiftDown());
        if (showingPanel()) {
            if (button == InputConstants.MOUSE_BUTTON_LEFT && over(mouseX, mouseY, BACK_X, BACK_Y, 16, 16)) {
                buttonClick(RackMenu.PICK);
                return true;
            }
            if (button == InputConstants.MOUSE_BUTTON_LEFT && panel != null && menu.pickedDevice() != null && over(mouseX, mouseY, PRIORITY_X, BACK_Y, 16, 16)) {
                ClientPacketDistributor.sendToServer(new RackActionPayload(menu.containerId, panelU, RackDevice.ACTION_PRIORITY, 0, ""));
                return true;
            }
            if (panel != null && mouseY < topPos + topHeight() && panel.mouseClicked(mouseX - leftPos, mouseY - topPos, button, shift)) {
                return true;
            }
            return sneakQuickMove(event, shift) || super.mouseClicked(event, doubleClick);
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT && view.press(mouseX - leftPos, mouseY - topPos)) {
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
            // A device with no settings panel (the Rack Console) only comes out (shift-click).
            if (device != null && (shift || RackClientDevices.hasPanel(device.type()))) {
                buttonClick((shift ? RackMenu.TAKE : RackMenu.PICK) + u);
            }
            return true;
        }
        return sneakQuickMove(event, shift) || super.mouseClicked(event, doubleClick);
    }

    // Sneak held on a slot without Shift (Sneak bound to another key): the slot's stack moves across, as a Shift-click
    // does. With Shift down, vanilla does it.
    private boolean sneakQuickMove(MouseButtonEvent event, boolean sneak) {
        int button = event.button();
        if (!sneak || event.hasShiftDown() || !menu.getCarried().isEmpty()
                || (button != InputConstants.MOUSE_BUTTON_LEFT && button != InputConstants.MOUSE_BUTTON_RIGHT)) {
            return false;
        }
        Slot slot = getHoveredSlot();
        if (slot == null) {
            return false;
        }
        slotClicked(slot, slot.index, button == InputConstants.MOUSE_BUTTON_LEFT ? 0 : 1, ContainerInput.QUICK_MOVE);
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (view.drag(event.y() - topPos)) {
            return true;
        }
        if (showingPanel() && panel != null && panel.mouseDragged(event.x() - leftPos, event.y() - topPos)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        view.release();
        if (panel != null) {
            panel.mouseReleased();
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (showingPanel()) {
            if (panel != null && panel.mouseScrolled(mouseX - leftPos, mouseY - topPos, scrollY)) {
                return true;
            }
        } else if (mouseY < topPos + topHeight() && mouseX >= leftPos && mouseX < leftPos + imageWidth) {
            view.wheel(scrollY);
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
