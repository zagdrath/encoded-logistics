/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.util.List;
import java.util.function.IntPredicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.menu.RackMenu;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.StorageDevice;
import net.zagdrath.encodedlogistics.rack.chassis.Chassis;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisLayout;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisPart;
import net.zagdrath.encodedlogistics.rack.device.SanDevice;
import net.zagdrath.encodedlogistics.storage.ResourceType;

// The rack's elevation as a screen draws it (textures/gui/rack/elevation.png, 176 wide): U numbers right-aligned at
// NUMBER_RIGHT, a row ROW_H tall for each unit in view (16, or more as the window allows), each device's real front
// SLOT_W wide at SLOT_X from its own texture, the selection and drop outlines, and the scrollbar. The Server Rack's
// screen (RackScreen) and the Rack Elevation's editor (RackElevationScreen, its planned devices as ghosts) both draw
// with it, so there's one elevation renderer. Coordinates are the screen's: (x, y) its top-left.
public final class RackElevationView {
    public static final Identifier ELEVATION = EncodedLogistics.id("textures/gui/rack/elevation.png");
    private static final Identifier SLOT_EMPTY = EncodedLogistics.id("rack/slot_empty"), SELECT_1U = EncodedLogistics.id("rack/select_1u"),
            SELECT_2U = EncodedLogistics.id("rack/select_2u"), DROP_1U = EncodedLogistics.id("rack/drop_target_1u"),
            DROP_2U = EncodedLogistics.id("rack/drop_target_2u"), DROP_BLOCKED = EncodedLogistics.id("rack/drop_blocked");
    // The controller's scrollbar, as on the other screens: an 8 px track, its 6x15 thumb one in from the left.
    private static final Identifier THUMB = EncodedLogistics.id("controller/scroll_thumb"),
            THUMB_HOVER = EncodedLogistics.id("controller/scroll_thumb_hover");
    private static final Identifier CHARGING = EncodedLogistics.id("textures/block/drive_bay/leds_energy_charging.png");

    public static final int WIDTH = RackMenu.WIDTH, HEIGHT = RackMenu.TOP_HEIGHT;
    // At 16 rows (RackMenu.ROWS); the inset and the scrollbar grow with the rows shown.
    public static final int INSET_X = 15, INSET_Y = 18, INSET_W = 124, INSET_H = 146;
    public static final int ROW_H = RackMenu.ROW_H, FIRST_ROW_Y = 19, NUMBER_RIGHT = 30, SLOT_X = 33, SLOT_W = 104;
    public static final int SCROLL_X = 153, SCROLL_Y = 18, SCROLL_W = 8, SCROLL_H = 146, THUMB_W = 6, THUMB_H = 15;
    // The elevation texture's rows repeat every ROW_H from here (a row and its separator line).
    private static final int STRIP_Y = FIRST_ROW_Y + 9 * ROW_H;
    // The selection and drop-target outlines drawn for devices taller than the 1U and 2U sprites: the same 1 px line,
    // solid or dashed; a drop that doesn't fit, red.
    public static final int SELECT = 0xFF5CF0B8, DROP = 0xFFF5B23A, DROP_REFUSED = 0xFFFF6B6B;

    // The rows shown and the extra height they add over 16.
    private final int rows, extra;
    // Rows scrolled down from the top (U42); starts at the top.
    private int scroll;
    private boolean draggingThumb;

    public RackElevationView(int rows) {
        this.rows = rows;
        this.extra = (rows - RackMenu.ROWS) * ROW_H;
    }

    public int rows() {
        return rows;
    }

    public int extra() {
        return extra;
    }

    public int maxScroll() {
        return RackGeometry.UNITS - rows;
    }

    // --- Rows ---

    public int unitAtRow(int row) {
        return RackGeometry.UNITS - scroll - row;
    }

    public int rowOf(int u) {
        return RackGeometry.UNITS - scroll - u;
    }

    public static int rowY(int row) {
        return FIRST_ROW_Y + row * ROW_H;
    }

    // The unit at a point relative to the screen's top-left, or 0 when it isn't on a row.
    public int unitAt(double x, double y) {
        if (x < INSET_X || x >= INSET_X + INSET_W || y < FIRST_ROW_Y || y >= FIRST_ROW_Y + rows * ROW_H) {
            return 0;
        }
        return unitAtRow(Mth.floor((y - FIRST_ROW_Y) / ROW_H));
    }

    // Whether any of a device's units are in view.
    public boolean visible(int u, int size) {
        int topRow = rowOf(u + size - 1);
        return topRow + size > 0 && topRow < rows;
    }

    // --- Drawing ---

    // The elevation's background: its top, a row's strip for each extra row, its bottom.
    public void background(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, ELEVATION, x, y, 0.0F, 0.0F, WIDTH, STRIP_Y, 256, 256);
        for (int i = 0; i < extra / ROW_H; i++) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, ELEVATION, x, y + STRIP_Y + i * ROW_H, 0.0F, STRIP_Y, WIDTH, ROW_H, 256, 256);
        }
        graphics.blit(RenderPipelines.GUI_TEXTURED, ELEVATION, x, y + STRIP_Y + extra, 0.0F, STRIP_Y, WIDTH, HEIGHT - STRIP_Y, 256, 256);
    }

    // The rows' area clips what's drawn in it (begin, draw, end).
    public void begin(GuiGraphicsExtractor graphics, int x, int y) {
        graphics.enableScissor(x + INSET_X, y + INSET_Y, x + INSET_X + INSET_W, y + INSET_Y + INSET_H + extra);
    }

    public void end(GuiGraphicsExtractor graphics) {
        graphics.disableScissor();
    }

    // The empty rows (free(u) says which units are empty).
    public void emptySlots(GuiGraphicsExtractor graphics, int x, int y, IntPredicate free) {
        for (int row = 0; row < rows; row++) {
            if (free.test(unitAtRow(row))) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, SLOT_EMPTY, x + SLOT_X, y + rowY(row), SLOT_W, 8);
            }
        }
    }

    // A device's front over its units (u its bottom), tinted (-1: as it is; a ghost: part transparent).
    public void device(GuiGraphicsExtractor graphics, int x, int y, RackDevice device, int u, int color) {
        front(graphics, device, x + SLOT_X, y + rowY(rowOf(u + device.size() - 1)), color);
    }

    // The selection outline round a device's units.
    public void select(GuiGraphicsExtractor graphics, int x, int y, int u, int size) {
        int drawY = y + rowY(rowOf(u + size - 1)) - 1;
        if (size <= 2) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, size > 1 ? SELECT_2U : SELECT_1U, x + SLOT_X - 1, drawY, SLOT_W + 2, size * ROW_H + 1);
        } else {
            outline(graphics, x + SLOT_X - 1, drawY, SLOT_W + 2, size * ROW_H + 1, SELECT, false);
        }
    }

    // Where a device of that size would go at u: the drop target when it fits, else each unit it would take, refused.
    public void drop(GuiGraphicsExtractor graphics, int x, int y, int u, int size, boolean fits) {
        int topU = Math.min(u + size - 1, RackGeometry.UNITS);
        int drawY = y + rowY(rowOf(topU)) - 1;
        if (fits && size <= 2) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, size > 1 ? DROP_2U : DROP_1U, x + SLOT_X - 1, drawY, SLOT_W + 2, size * ROW_H + 1);
        } else if (fits) {
            outline(graphics, x + SLOT_X - 1, drawY, SLOT_W + 2, size * ROW_H + 1, DROP, true);
        } else {
            for (int unit = u; unit <= topU; unit++) {
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DROP_BLOCKED, x + SLOT_X - 1, y + rowY(rowOf(unit)) - 1, SLOT_W + 2, 10);
            }
        }
    }

    // The U numbers, in the labels' pose ((0, 0) the screen's top-left): used units bright.
    public void numbers(GuiGraphicsExtractor graphics, Font font, IntPredicate used) {
        for (int row = 0; row < rows; row++) {
            int u = unitAtRow(row);
            String number = Integer.toString(u);
            graphics.text(font, number, NUMBER_RIGHT - font.width(number), rowY(row), used.test(u) ? RackScreen.TEXT : RackScreen.TEXT_MUTED, false);
        }
    }

    // The scrollbar's thumb (none when every unit shows).
    public void thumb(GuiGraphicsExtractor graphics, int x, int y, int mouseX, int mouseY) {
        if (maxScroll() <= 0) {
            return;
        }
        int top = thumbTop();
        boolean hover = draggingThumb || mouseX >= x + SCROLL_X + 1 && mouseX < x + SCROLL_X + 1 + THUMB_W && mouseY >= y + top
                && mouseY < y + top + THUMB_H;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, hover ? THUMB_HOVER : THUMB, x + SCROLL_X + 1, y + top, THUMB_W, THUMB_H);
    }

    // The thumb's top, relative to the screen, inside the track's 1 px rim.
    private int thumbTop() {
        return SCROLL_Y + 1 + Math.round((SCROLL_H + extra - 2 - THUMB_H) * (float) scroll / maxScroll());
    }

    // --- Scrolling (points relative to the screen's top-left) ---

    // A press on the scrollbar: it scrolls there and follows the drag.
    public boolean press(double x, double y) {
        if (maxScroll() > 0 && x >= SCROLL_X && x < SCROLL_X + SCROLL_W && y >= SCROLL_Y && y < SCROLL_Y + SCROLL_H + extra) {
            draggingThumb = true;
            scrollTo(y);
            return true;
        }
        return false;
    }

    public boolean drag(double y) {
        if (draggingThumb) {
            scrollTo(y);
            return true;
        }
        return false;
    }

    public void release() {
        draggingThumb = false;
    }

    // The wheel: two units a notch.
    public void wheel(double amount) {
        scroll = Mth.clamp(scroll - (int) Math.signum(amount) * 2, 0, Math.max(0, maxScroll()));
    }

    private void scrollTo(double y) {
        double fraction = (y - SCROLL_Y - 1 - THUMB_H / 2.0) / (SCROLL_H + extra - 2 - THUMB_H);
        scroll = Mth.clamp((int) Math.round(fraction * maxScroll()), 0, Math.max(0, maxScroll()));
    }

    // --- A device's front ---

    // A device's real front, from its texture (128 x sheetHeight, frames that tall), with its lights for its state (an
    // animated overlay shown frame by frame, as the block does: 3 ticks a frame), tinted (-1: as it is).
    // Drawn ROW_H a unit (less the last row's gap), so a device fills its rows with no gaps between its units.
    public static void front(GuiGraphicsExtractor graphics, RackDevice device, int x, int y, int color) {
        int height = 8 * device.size(), shown = device.size() * ROW_H - 1, sheet = device.type().sheetHeight();
        graphics.blit(RenderPipelines.GUI_TEXTURED, device.type().texture(""), x, y, 0.0F, 0.0F, SLOT_W, shown, SLOT_W, height, 128, sheet, color);
        RackDeviceInfo.Status status = device.shownStatus();
        String variant = device.shownVariant();
        String lit = variant != null && device.type().variants().containsKey(variant) ? variant
                : status == RackDeviceInfo.Status.ONLINE || status == RackDeviceInfo.Status.WARNING ? "_on" : status == RackDeviceInfo.Status.FAULT ? "_fault"
                        : null;
        if (lit != null) {
            litFront(graphics, device, lit, x, y, shown, height, sheet);
        }
        if (device instanceof StorageDevice storage && !(device instanceof Chassis)) {
            bays(graphics, storage, x, y, shown, height, sheet, color);
        }
        if (device instanceof Chassis chassis) {
            parts(graphics, chassis, x, y, shown, height, color);
        }
    }

    // A modular chassis's parts on its front, in their bays (as ChassisRender draws them), scaled as the front is.
    private static void parts(GuiGraphicsExtractor graphics, Chassis chassis, int x, int y, int shown, int height, int color) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(1.0F, (float) shown / height);
        List<ChassisLayout.Bay> layout = chassis.layout().bays();
        for (int i = 0; i < layout.size(); i++) {
            ChassisLayout.Bay bay = layout.get(i);
            ChassisPart part = Chassis.shownPart(chassis, i);
            if (bay.rear() || part == null) {
                continue;
            }
            graphics.blit(RenderPipelines.GUI_TEXTURED, part.texture(), bay.x(), bay.y(), 0, 0, bay.w(), bay.h(), bay.w(), bay.h(), 128, 64, color);
            if (Chassis.shownLit(chassis, i)) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, EncodedLogistics.id("textures/block/rack_card/" + part.id() + "_on.png"), bay.x(), bay.y(), 0, 0,
                        bay.w(), bay.h(), bay.w(), bay.h(), 128, 256, color);
            }
        }
        graphics.pose().popMatrix();
    }

    // A NAS's or SAN's drives in their bays, as the rack in the world shows them (RackClientDevices.DriveBays): each
    // drive's sled for its tier and type (typed drives from <nas|san>_sleds_<type>.png) and its light (fill, or an energy
    // drive's charge), at the front texture's place, scaled as the front is: front() draws the texture's first SLOT_W
    // columns 1:1 (only its rows are scaled, 8 a unit to ROW_H), so the bays keep their texel x. (Scaling x by SLOT_W/128
    // too squeezed a full SAN's 12 sleds a row into the first 9 bays, leaving the last 3 looking empty.)
    private static void bays(GuiGraphicsExtractor graphics, StorageDevice storage, int x, int y, int shown, int height, int sheet, int color) {
        boolean san = storage instanceof SanDevice;
        int w = san ? 6 : 12, h = 14, sledRow = san ? 80 : 64;
        String kind = san ? "san" : "nas";
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(1.0F, (float) shown / height);
        for (int i = 0; i < storage.drives(); i++) {
            int code = storage.shownBay(i);
            if (code < 0) {
                continue;
            }
            ResourceType type = StorageDriveItem.sledType(code);
            int tier = StorageDriveItem.sledTier(code), lit = StorageDriveItem.sledLight(code);
            int bx = san ? 14 + 6 * (i % 12) : 15 + 12 * i, by = san ? 1 + 15 * (i / 12) : 1;
            Identifier sheetId = type == ResourceType.ITEM ? storage.type().texture("")
                    : EncodedLogistics.id("textures/block/rack_device/" + kind + "_sleds_" + type.getSerializedName() + ".png");
            int row = type == ResourceType.ITEM ? sledRow : 0, sheetH = type == ResourceType.ITEM ? sheet : 16;
            graphics.blit(RenderPipelines.GUI_TEXTURED, sheetId, bx, by, tier * (w + 1), row, w, h, w, h, 128, sheetH, color);
            if (lit == StorageDriveItem.LIGHT_CHARGING) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, CHARGING, bx + w - 3, by + h - 4, 0, 0, 2, 2, 2, 2, 16, 64, color);
            } else {
                graphics.blit(RenderPipelines.GUI_TEXTURED, sheetId, bx + w - 3, by + h - 4, 70 + 3 * Math.min(4, lit), row, 2, 2, 2, 2, 128, sheetH,
                        color);
            }
        }
        graphics.pose().popMatrix();
    }

    // A device's lit overlay as the block atlas has it this moment: the atlas animates these textures (frame time,
    // interpolation and all) just as it does for the rack in the world, so the screen shows the same frame. (Loaded as a
    // plain texture, an animated strip only has its first frame.)
    private static void litFront(GuiGraphicsExtractor graphics, RackDevice device, String suffix, int x, int y, int shown, int height, int sheet) {
        Identifier id = device.type().id().withPath("block/rack_device/" + device.type().id().getPath() + suffix);
        TextureAtlas atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS);
        TextureAtlasSprite sprite = atlas.getSprite(id);
        if (sprite == atlas.missingSprite() || sprite.getU1() <= sprite.getU0() || sprite.getV1() <= sprite.getV0()) {
            return;
        }
        // By the sprite's own UVs (its pixel position in the atlas isn't where its frame's texels start, so working from
        // that put the overlay off its front): the front's part of the frame, SLOT_W x height of 128 x sheet.
        graphics.blit(sprite.atlasLocation(), x, y, x + SLOT_W, y + shown, sprite.getU0(), sprite.getU((float) SLOT_W / 128), sprite.getV0(),
                sprite.getV((float) height / sheet));
    }

    // A 1 px rectangle outline, solid or dashed.
    public static void outline(GuiGraphicsExtractor graphics, int x, int y, int width, int height, int color, boolean dashed) {
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
}
