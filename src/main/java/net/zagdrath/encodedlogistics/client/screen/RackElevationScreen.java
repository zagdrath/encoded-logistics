/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.client.rack.RackElevationPreview;
import net.zagdrath.encodedlogistics.item.RackElevationItem;
import net.zagdrath.encodedlogistics.net.RackElevationPayloads;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.RackTemplate;

// The Rack Elevation's editor (previews/gui_editor.png). Left, the rack screen's own elevation (RackElevationView: 16 of
// the 42 units, scrolling) with the planned devices as ghosts - their fronts at 55 % - the selected one outlined, and
// where a device being dragged would go (the drop target, or red when it breaks the rack's rules). Right, the side
// panel (elevation_editor_side.png): the plan's name, a search field over every kind of rack device (icon, name, size),
// the Contents / Settings / Network storage toggles, and Materials with how many items are missing (its list).
//
// Placing: drag a device from the list onto a unit, or click a free unit and then a device; dragging a ghost moves it.
// Clicking a ghost shows it (its units, planned contents and settings) with Remove. Every change is one Edit to the
// server, which checks it and writes the held item; the screen keeps its own copy in step (RackElevationPayloads.Edit
// #apply, the same rules). JEI: a rack device dragged from its list onto a unit places it (ghostTargets).
public class RackElevationScreen extends Screen {
    private static final Identifier SIDE = EncodedLogistics.id("textures/gui/rack/elevation_editor_side.png");
    private static final int SIDE_W = 128, GAP = 4, WIDTH = RackElevationView.WIDTH + GAP + SIDE_W, HEIGHT = RackElevationView.HEIGHT;
    // The side panel, relative to its top-left: the name row, the well (search, then rows 13 apart), the bottom field.
    private static final int NAME_Y = 22, WELL_X = 9, WELL_W = 111, SEARCH_Y = 40, FIRST_ROW = 51, ROW = 13, LIST_ROWS = 4, VALUE_RIGHT = 117;
    private static final int MATERIALS_Y = 148, MATERIALS_H = 14;
    // A ghost: its front at 55 %.
    private static final int GHOST = 0x8CFFFFFF;
    private static final int ERROR = RackScreen.ERROR, ACCENT = RackScreen.ACCENT, TEXT = RackScreen.TEXT, MUTED = RackScreen.TEXT_MUTED;

    private final InteractionHand hand;
    private RackTemplate template;
    private final RackElevationView view = new RackElevationView(16);
    private final Map<RackDeviceType, RackDevice> ghosts = new HashMap<>();
    private int left, top;
    private @Nullable EditBox name, search;
    private int listScroll;
    // A free unit clicked (then a device in the list places there), the ghost shown, and a drag in progress: a device
    // from the list, or a ghost being moved (from its unit), and whether the mouse has moved since the press.
    private int selectedU, shownU;
    private @Nullable RackDeviceType dragging;
    private int movingFrom;
    private double pressX, pressY;
    private boolean moved;
    // The name typed, sent once typing pauses (or the screen closes).
    private int nameTicks = -1;
    private int ticks;

    private RackElevationScreen(InteractionHand hand, RackTemplate template) {
        super(Component.translatable("gui.encodedlogistics.rack_elevation.title"));
        this.hand = hand;
        this.template = template;
    }

    public static void open(InteractionHand hand) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        ItemStack stack = minecraft.player.getItemInHand(hand);
        if (stack.getItem() instanceof RackElevationItem) {
            minecraft.gui.setScreen(new RackElevationScreen(hand, RackTemplate.orEmpty(stack)));
        }
    }

    InteractionHand hand() {
        return hand;
    }

    RackTemplate template() {
        return template;
    }

    // The materials list toggled Network storage (and told the server).
    void networkToggled(boolean on) {
        template = template.withNetworkStorage(on);
    }

    // --- Layout ---

    @Override
    protected void init() {
        left = (width - WIDTH) / 2;
        top = (height - HEIGHT) / 2;
        int sx = sideX();
        String typed = name != null ? name.getValue() : template.name();
        name = new EditBox(font, sx + 40, top + NAME_Y, VALUE_RIGHT - 40 + 4, 9, Component.translatable("gui.encodedlogistics.rack_elevation.name"));
        name.setBordered(false);
        name.setMaxLength(RackTemplate.MAX_NAME);
        name.setTextColor(TEXT);
        name.setValue(typed);
        name.setResponder(text -> nameTicks = 10);
        addRenderableWidget(name);
        String query = search != null ? search.getValue() : "";
        search = new EditBox(font, sx + WELL_X + 2, top + SEARCH_Y, WELL_W - 4, 9, Component.translatable("gui.encodedlogistics.rack_elevation.search"));
        search.setBordered(false);
        search.setMaxLength(32);
        search.setTextColor(TEXT);
        search.setHint(Component.translatable("gui.encodedlogistics.rack_elevation.search").withColor(RackScreen.TEXT_DISABLED));
        search.setValue(query);
        search.setResponder(text -> listScroll = 0);
        addRenderableWidget(search);
        queryMaterials();
    }

    private int sideX() {
        return left + RackElevationView.WIDTH + GAP;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // --- Following the item ---

    @Override
    public void tick() {
        super.tick();
        ticks++;
        if (nameTicks > 0 && --nameTicks == 0) {
            sendName();
        }
        // The held item changed under us (dropped, swapped): close.
        if (minecraft.player == null || !(minecraft.player.getItemInHand(hand).getItem() instanceof RackElevationItem)) {
            onClose();
            return;
        }
        if (ticks % 40 == 0) {
            queryMaterials();
        }
    }

    @Override
    public void removed() {
        if (nameTicks > 0) {
            sendName();
        }
        super.removed();
    }

    private void sendName() {
        nameTicks = -1;
        if (name != null && !name.getValue().strip().equals(template.name())) {
            edit(new RackElevationPayloads.Edit(hand, RackElevationPayloads.Edit.NAME, 0, "", name.getValue()));
        }
    }

    // Makes a change here and on the server (when the rules allow it).
    private boolean edit(RackElevationPayloads.Edit edit) {
        RackTemplate changed = RackElevationPayloads.Edit.apply(template, edit);
        if (changed == null) {
            return false;
        }
        template = changed;
        ClientPacketDistributor.sendToServer(edit);
        queryMaterials();
        return true;
    }

    private void queryMaterials() {
        ClientPacketDistributor.sendToServer(new RackElevationPayloads.MaterialsQuery(hand, RackElevationPreview.lastRack()));
    }

    // --- The device list ---

    private List<RackDeviceType> listed() {
        String query = search != null ? search.getValue().strip().toLowerCase(Locale.ROOT) : "";
        List<RackDeviceType> types = new ArrayList<>();
        for (RackDeviceType type : RackDeviceType.all()) {
            if (query.isEmpty() || typeName(type).getString().toLowerCase(Locale.ROOT).contains(query)) {
                types.add(type);
            }
        }
        return types;
    }

    private static Component typeName(RackDeviceType type) {
        return new ItemStack(type.item()).getHoverName();
    }

    private RackDevice ghost(RackDeviceType type) {
        return ghosts.computeIfAbsent(type, RackDeviceType::create);
    }

    // The list row under a point (side panel coordinates), or -1.
    private int listRow(double x, double y) {
        if (x < WELL_X || x >= WELL_X + WELL_W || y < FIRST_ROW || y >= FIRST_ROW + LIST_ROWS * ROW) {
            return -1;
        }
        return (int) ((y - FIRST_ROW) / ROW);
    }

    private @Nullable RackDeviceType listedAt(double x, double y) {
        int row = listRow(x, y);
        List<RackDeviceType> types = listed();
        return row >= 0 && listScroll + row < types.size() ? types.get(listScroll + row) : null;
    }

    // --- Drawing ---

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        view.background(graphics, left, top);
        int sx = sideX();
        graphics.blit(RenderPipelines.GUI_TEXTURED, SIDE, sx, top, 0.0F, 0.0F, SIDE_W, HEIGHT, 256, 256);
        bottomEdge(graphics, left, RackElevationView.WIDTH);
        bottomEdge(graphics, sx, SIDE_W);
        extractElevation(graphics, mouseX, mouseY);
        Component heading = template.name().isEmpty() ? title : title.copy().append("  ").append(template.name());
        graphics.text(font, heading, left + 8, top + 5, TEXT, false);
        // The U numbers are drawn in a labels' pose ((0, 0) the screen's corner); a plain screen has none, so move there.
        graphics.pose().pushMatrix();
        graphics.pose().translate(left, top);
        view.numbers(graphics, font, u -> template.at(u) != null);
        graphics.pose().popMatrix();
        if (shownU > 0 && template.at(shownU) != null) {
            extractShown(graphics, mouseX, mouseY, sx);
        } else {
            extractSide(graphics, mouseX, mouseY, sx);
        }
    }

    // A panel's bottom edge, shaded like its right one: the textures are the rack screen's top half, open at the bottom
    // where its inventory joins on, but here each panel stands alone.
    private void bottomEdge(GuiGraphicsExtractor graphics, int x, int w) {
        int bottom = top + HEIGHT;
        graphics.fill(x + 2, bottom - 3, x + w - 2, bottom - 2, 0xFF363636);
        graphics.fill(x + 1, bottom - 2, x + w - 1, bottom - 1, 0xFF262626);
        graphics.fill(x, bottom - 1, x + w, bottom, 0xFF141414);
    }

    // The ghosts, the selected unit and the drag's drop target, clipped to the rows.
    private void extractElevation(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        view.begin(graphics, left, top);
        view.emptySlots(graphics, left, top, u -> template.at(u) == null);
        for (RackTemplate.Planned planned : template.devices()) {
            RackDeviceType type = planned.deviceType();
            if (type == null || !view.visible(planned.u(), planned.size())) {
                continue;
            }
            view.device(graphics, left, top, ghost(type), planned.u(), GHOST);
            boolean hovered = dragging == null && template.at(view.unitAt(mouseX - left, mouseY - top)) == planned;
            if (planned.u() == shownU || hovered) {
                view.select(graphics, left, top, planned.u(), planned.size());
            }
        }
        if (selectedU > 0 && template.at(selectedU) == null && view.visible(selectedU, 1)) {
            int y = top + RackElevationView.rowY(view.rowOf(selectedU)) - 1;
            RackElevationView.outline(graphics, left + RackElevationView.SLOT_X - 1, y, RackElevationView.SLOT_W + 2, RackElevationView.ROW_H + 1,
                    RackElevationView.SELECT, true);
        }
        // Where the device being dragged would go: the drop target, or red where it can't.
        int u = view.unitAt(mouseX - left, mouseY - top);
        if (dragging != null && moved && u > 0) {
            view.drop(graphics, left, top, u, dragging.size(), template.placeable(dragging, u, movingFrom));
        }
        view.end(graphics);
        view.thumb(graphics, left, top, mouseX, mouseY);
    }

    private void extractSide(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int sx) {
        graphics.text(font, Component.translatable("gui.encodedlogistics.rack_elevation.devices"), sx + 6, top + 5, TEXT, false);
        graphics.text(font, Component.translatable("gui.encodedlogistics.rack_elevation.name"), sx + 8, top + NAME_Y, MUTED, false);
        List<RackDeviceType> types = listed();
        RackDeviceType hovered = listedAt(mouseX - sx, mouseY - top);
        for (int row = 0; row < LIST_ROWS && listScroll + row < types.size(); row++) {
            RackDeviceType type = types.get(listScroll + row);
            int y = top + FIRST_ROW + row * ROW;
            graphics.pose().pushMatrix();
            graphics.pose().translate(sx + WELL_X + 2, y + 2);
            graphics.pose().scale(0.5F, 0.5F);
            graphics.item(new ItemStack(type.item()), 0, 0);
            graphics.pose().popMatrix();
            graphics.text(font, font.plainSubstrByWidth(typeName(type).getString(), WELL_W - 34), sx + WELL_X + 13, y + 3,
                    type == hovered || type == dragging ? ACCENT : TEXT, false);
            String size = type.size() + "U";
            graphics.text(font, size, sx + VALUE_RIGHT - font.width(size), y + 3, MUTED, false);
        }
        toggle(graphics, sx, 0, "gui.encodedlogistics.rack_elevation.contents", template.includeContents(), mouseX, mouseY);
        toggle(graphics, sx, 1, "gui.encodedlogistics.rack_elevation.settings", template.includeSettings(), mouseX, mouseY);
        toggle(graphics, sx, 2, "gui.encodedlogistics.rack_elevation.network", template.useNetworkStorage(), mouseX, mouseY);
        // Materials, and how many items are short.
        boolean overMaterials = over(mouseX, mouseY, sx + 8, top + MATERIALS_Y, WELL_W, MATERIALS_H);
        graphics.text(font, Component.translatable("gui.encodedlogistics.rack_elevation.materials"), sx + 11, top + MATERIALS_Y + 3,
                overMaterials ? ACCENT : TEXT, false);
        RackElevationPayloads.Materials materials = RackElevationPayloads.Materials.latest();
        if (materials != null) {
            Component missing = Component.translatable("gui.encodedlogistics.rack_elevation.missing", materials.missing());
            graphics.text(font, missing, sx + VALUE_RIGHT - 2 - font.width(missing), top + MATERIALS_Y + 3, materials.missing() > 0 ? ERROR : ACCENT,
                    false);
        }
    }

    private int toggleY(int index) {
        return top + FIRST_ROW + (LIST_ROWS + index) * ROW;
    }

    private void toggle(GuiGraphicsExtractor graphics, int sx, int index, String key, boolean on, int mouseX, int mouseY) {
        int y = toggleY(index);
        boolean hover = over(mouseX, mouseY, sx + WELL_X, y, WELL_W, ROW);
        graphics.text(font, Component.translatable(key), sx + WELL_X + 2, y + 3, hover ? TEXT : MUTED, false);
        Component value = Component.translatable(on ? "options.on" : "options.off");
        graphics.text(font, value, sx + VALUE_RIGHT - font.width(value), y + 3, on ? ACCENT : MUTED, false);
    }

    // A ghost shown: its name and units, Remove, what it'll hold and whether it has settings; Back.
    private void extractShown(GuiGraphicsExtractor graphics, int mouseX, int mouseY, int sx) {
        RackTemplate.Planned planned = template.at(shownU);
        RackDeviceType type = planned != null ? planned.deviceType() : null;
        if (type == null) {
            return;
        }
        graphics.text(font, typeName(type), sx + 6, top + 5, TEXT, false);
        Component units = planned.size() > 1 ? Component.translatable("gui.encodedlogistics.rack.hud.u2", planned.u(), planned.top())
                : Component.translatable("gui.encodedlogistics.rack.hud.u1", planned.u());
        graphics.text(font, units, sx + 8, top + NAME_Y, MUTED, false);
        // Remove, as the first row of the well.
        int removeY = top + SEARCH_Y - 2;
        boolean overRemove = over(mouseX, mouseY, sx + WELL_X, removeY, WELL_W, ROW - 1);
        graphics.text(font, Component.translatable("gui.encodedlogistics.rack_elevation.remove"), sx + WELL_X + 2, removeY + 2, overRemove ? ERROR : TEXT,
                false);
        // Its planned contents, a read-only slot grid.
        graphics.text(font, Component.translatable("gui.encodedlogistics.rack_elevation.contents"), sx + WELL_X + 2, top + FIRST_ROW + 3, MUTED, false);
        if (planned.contents().isEmpty()) {
            Component none = Component.translatable("gui.encodedlogistics.rack_elevation.none");
            graphics.text(font, none, sx + VALUE_RIGHT - font.width(none), top + FIRST_ROW + 3, MUTED, false);
        }
        int i = 0;
        for (RackTemplate.SlotItem item : planned.contents()) {
            if (i >= 24) {
                break;
            }
            int x = sx + WELL_X + 2 + i % 6 * 18, y = top + FIRST_ROW + ROW + i / 6 * 18;
            graphics.item(item.item().create(), x, y);
            graphics.itemDecorations(font, item.item().create(), x, y);
            i++;
        }
        // Settings: recorded or not.
        int settingsY = toggleY(2);
        graphics.text(font, Component.translatable("gui.encodedlogistics.rack_elevation.settings"), sx + WELL_X + 2, settingsY + 3, MUTED, false);
        Component value = Component.translatable(planned.settings().isPresent() ? "gui.encodedlogistics.rack_elevation.recorded"
                : "gui.encodedlogistics.rack_elevation.none");
        graphics.text(font, value, sx + VALUE_RIGHT - font.width(value), settingsY + 3, planned.settings().isPresent() ? ACCENT : MUTED, false);
        boolean overBack = over(mouseX, mouseY, sx + 8, top + MATERIALS_Y, WELL_W, MATERIALS_H);
        graphics.text(font, Component.translatable("gui.encodedlogistics.rack.back"), sx + 11, top + MATERIALS_Y + 3, overBack ? ACCENT : TEXT, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        boolean showing = shownU > 0 && template.at(shownU) != null;
        if (name != null) {
            name.visible = !showing;
        }
        if (search != null) {
            search.visible = !showing;
        }
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        if (dragging != null && moved) {
            graphics.item(new ItemStack(dragging.item()), mouseX - 8, mouseY - 8);
        }
        int sx = sideX();
        RackDeviceType hovered = showing ? null : listedAt(mouseX - sx, mouseY - top);
        if (hovered != null && dragging == null) {
            graphics.setTooltipForNextFrame(font, new ItemStack(hovered.item()), mouseX, mouseY);
        }
        RackTemplate.Planned planned = template.at(view.unitAt(mouseX - left, mouseY - top));
        if (planned != null && dragging == null && planned.deviceType() != null) {
            graphics.setTooltipForNextFrame(Component.translatable("gui.encodedlogistics.rack_elevation.ghost_hint", typeName(planned.deviceType())),
                    mouseX, mouseY);
        }
    }

    // --- Input ---

    private boolean over(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (super.mouseClicked(event, doubleClick)) {
            return true;
        }
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        double mx = event.x(), my = event.y();
        int sx = sideX();
        // The elevation.
        if (view.press(mx - left, my - top)) {
            return true;
        }
        int u = view.unitAt(mx - left, my - top);
        if (u > 0) {
            RackTemplate.Planned planned = template.at(u);
            pressX = mx;
            pressY = my;
            moved = false;
            if (planned != null && planned.deviceType() != null) {
                dragging = planned.deviceType();
                movingFrom = planned.u();
            } else {
                selectedU = selectedU == u ? 0 : u;
                shownU = 0;
            }
            return true;
        }
        if (shownU > 0 && template.at(shownU) != null) {
            if (over(mx, my, sx + WELL_X, top + SEARCH_Y - 2, WELL_W, ROW - 1)) {
                edit(new RackElevationPayloads.Edit(hand, RackElevationPayloads.Edit.REMOVE, shownU, "", ""));
                shownU = 0;
                return true;
            }
            if (over(mx, my, sx + 8, top + MATERIALS_Y, WELL_W, MATERIALS_H)) {
                shownU = 0;
                return true;
            }
            return false;
        }
        // The list: a press starts a drag; a click (no drag) places at the selected unit.
        RackDeviceType type = listedAt(mx - sx, my - top);
        if (type != null) {
            dragging = type;
            movingFrom = 0;
            pressX = mx;
            pressY = my;
            moved = false;
            return true;
        }
        for (int index = 0; index < 3; index++) {
            if (over(mx, my, sx + WELL_X, toggleY(index), WELL_W, ROW)) {
                int op = index == 0 ? RackElevationPayloads.Edit.CONTENTS : index == 1 ? RackElevationPayloads.Edit.SETTINGS
                        : RackElevationPayloads.Edit.NETWORK;
                boolean on = index == 0 ? template.includeContents() : index == 1 ? template.includeSettings() : template.useNetworkStorage();
                edit(RackElevationPayloads.Edit.toggle(hand, op, !on));
                return true;
            }
        }
        if (over(mx, my, sx + 8, top + MATERIALS_Y, WELL_W, MATERIALS_H)) {
            minecraft.gui.setScreen(new RackElevationMaterialsScreen(this, RackElevationPreview.lastRack()));
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (view.drag(event.y() - top)) {
            return true;
        }
        if (dragging != null) {
            moved |= Math.abs(event.x() - pressX) > 2 || Math.abs(event.y() - pressY) > 2;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        view.release();
        RackDeviceType type = dragging;
        dragging = null;
        if (type == null) {
            return super.mouseReleased(event);
        }
        int u = view.unitAt(event.x() - left, event.y() - top);
        if (!moved) {
            if (movingFrom > 0) {
                // A click on a ghost: show it.
                shownU = movingFrom;
                selectedU = 0;
            } else if (selectedU > 0) {
                // A free unit, then a device.
                if (place(type, selectedU, 0)) {
                    selectedU = 0;
                }
            }
        } else if (u > 0) {
            place(type, u, movingFrom);
        }
        movingFrom = 0;
        moved = false;
        return true;
    }

    // Places a device at u (moving it from `from`, or new): refused when it breaks the rack's rules.
    private boolean place(RackDeviceType type, int u, int from) {
        if (!template.placeable(type, u, from)) {
            return false;
        }
        if (from > 0) {
            if (from == u) {
                return true;
            }
            edit(new RackElevationPayloads.Edit(hand, RackElevationPayloads.Edit.REMOVE, from, "", ""));
        }
        boolean placed = edit(new RackElevationPayloads.Edit(hand, RackElevationPayloads.Edit.PLACE, u, type.id().toString(), ""));
        if (placed && shownU == from && from > 0) {
            shownU = u;
        }
        return placed;
    }

    // Places a rack device item (JEI's ghost drag) at u.
    private void accept(ItemStack stack, int u) {
        RackDeviceType type = RackDeviceType.of(stack);
        if (type != null) {
            place(type, u, 0);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX >= left && mouseX < left + RackElevationView.WIDTH && mouseY >= top && mouseY < top + HEIGHT) {
            view.wheel(scrollY);
            return true;
        }
        int sx = sideX();
        if (listRow(mouseX - sx, mouseY - top) >= 0) {
            int max = Math.max(0, listed().size() - LIST_ROWS);
            listScroll = Math.clamp(listScroll - (int) Math.signum(scrollY), 0, max);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isEscape() && shownU > 0) {
            shownU = 0;
            return true;
        }
        for (EditBox box : new EditBox[] { name, search }) {
            if (box != null && box.isFocused() && box.visible && !event.isEscape()) {
                return box.keyPressed(event) || box.canConsumeInput() || super.keyPressed(event);
            }
        }
        return super.keyPressed(event);
    }

    // --- JEI ---

    // A unit row a rack device dragged from JEI can be dropped on.
    public record GhostTarget(int x, int y, int width, int height, Consumer<ItemStack> accept) {}

    public List<GhostTarget> ghostTargets() {
        List<GhostTarget> targets = new ArrayList<>();
        for (int row = 0; row < view.rows(); row++) {
            int u = view.unitAtRow(row);
            if (u >= 1 && u <= RackGeometry.UNITS && template.at(u) == null) {
                targets.add(new GhostTarget(left + RackElevationView.SLOT_X, top + RackElevationView.rowY(row), RackElevationView.SLOT_W,
                        RackElevationView.ROW_H, stack -> accept(stack, u)));
            }
        }
        return targets;
    }

    public int guiLeft() {
        return left;
    }

    public int guiTop() {
        return top;
    }

    public int guiWidth() {
        return WIDTH;
    }

    public int guiHeight() {
        return HEIGHT;
    }
}
