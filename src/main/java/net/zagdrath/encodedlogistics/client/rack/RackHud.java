/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.rack;

import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ExtractBlockOutlineRenderStateEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.ServerRackBlock;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.client.screen.RackScreen;
import net.zagdrath.encodedlogistics.net.RackUnitPayloads;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.RackTargeting;

// The rack's unit popup and outline (screens/rack/hud.json). While the crosshair is on a rack's front or back with
// that side's door open, it points at a unit (RackTargeting: followed in to the devices, not the outer face): the
// device there, or the empty unit, is outlined (instead of the block outline, which a rack never shows), and the
// popup by the crosshair shows the device's icon, name, units, status and its own lines (RackDeviceInfo, asked of the
// server as soon as the crosshair lands on it, then every QUERY_INTERVAL ticks; answers are kept per unit, so going back
// to one shows it at once). Until a device's answer is in, only its outline shows: the popup is always the whole thing.
// Above it, as wide as it, the rack's strip (lanes and uplinks). An empty unit just says so. With the doors closed
// there's neither. Hidden with F1 and while a screen is open.
public final class RackHud {
    public static final Identifier LAYER = EncodedLogistics.id("rack_unit_popup");

    private static final Identifier PANEL = EncodedLogistics.id("hud/panel"), DIVIDER = EncodedLogistics.id("hud/divider"),
            DOT_ONLINE = EncodedLogistics.id("hud/dot_online"), DOT_OFFLINE = EncodedLogistics.id("hud/dot_offline"),
            DOT_FAULT = EncodedLogistics.id("hud/dot_fault"), DOT_WARNING = EncodedLogistics.id("hud/dot_warning"),
            WARNING_SIGN = EncodedLogistics.id("rack/controller/warning"), BAR_TRACK = EncodedLogistics.id("hud/bar_track"),
            BAR_FILL = EncodedLogistics.id("hud/bar_fill"), BAR_WARN = EncodedLogistics.id("hud/bar_fill_warn"),
            BAR_LOW = EncodedLogistics.id("hud/bar_fill_low");
    private static final int OFFSET_X = 12, OFFSET_Y = -8, PAD_X = 5, PAD_Y = 4, MIN_W = 96, MAX_W = 180;
    private static final int HEADER_STRIP_H = 14;
    private static final int HEADER_H = 18, DIVIDER_TOP = 2, DIVIDER_BOTTOM = 3, LINE_H = 10, BAR_H = 6, GAP = 8;
    private static final int PANEL_COLOR = ARGB.color(Math.round(0.92F * 255), 0xFFFFFF);
    private static final int OUTLINE_COLOR = ARGB.color(Math.round(0.85F * 255), 0x5CF0B8);
    private static final float OUTLINE_WIDTH = 2.0F, OUTLINE_INFLATE = 0.15F;
    private static final int QUERY_INTERVAL = 10;
    private static final Identifier BADGE_SPRITE = EncodedLogistics.id("hud/badge_scheduler");
    private static final int BADGE = 9, SCHEDULER = 0xFFE8C24A;

    // What the crosshair points at in a rack: its master, the unit, and the device there (if any).
    public record Target(BlockPos master, Direction facing, int u, @Nullable RackDevice device) {
        boolean sameUnit(@Nullable Target other) {
            return other != null && master.equals(other.master) && (device != null ? other.device == device : other.device == null && u == other.u);
        }
    }

    private static @Nullable Target target;
    private static int steadyTicks;

    private RackHud() {}

    public static @Nullable Target target(ClientLevel level, BlockHitResult hit) {
        BlockState state = level.getBlockState(hit.getBlockPos());
        if (!(state.getBlock() instanceof ServerRackBlock)) {
            return null;
        }
        RackBlockEntity rack = ServerRackBlock.rack(level, hit.getBlockPos(), state);
        if (rack == null) {
            return null;
        }
        Entity viewer = Minecraft.getInstance().getCameraEntity();
        if (viewer == null) {
            return null;
        }
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        RackTargeting.Target at = RackTargeting.pick(rack, hit.getDirection(), viewer.getEyePosition(partialTick), viewer.getViewVector(partialTick));
        return at == null ? null : new Target(rack.getBlockPos(), rack.facing(), at.u(), at.device());
    }

    // --- Following the crosshair ---

    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Target now = minecraft.level != null && minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? target(minecraft.level, hit) : null;
        steadyTicks = now != null && now.sameUnit(target) ? steadyTicks + 1 : 0;
        target = now;
        if (now != null && now.device() != null && steadyTicks % QUERY_INTERVAL == 0) {
            ClientPacketDistributor.sendToServer(new RackUnitPayloads.Query(now.master(), now.device().u()));
        }
    }

    // --- The outline ---

    // Racks never show the block outline; the targeted device (or empty unit) gets its own.
    public static void outline(ExtractBlockOutlineRenderStateEvent event) {
        if (!(event.getBlockState().getBlock() instanceof ServerRackBlock)) {
            return;
        }
        Target at = target(event.getLevel(), event.getHitResult());
        VoxelShape box = null;
        BlockPos master = at != null ? at.master() : event.getBlockPos();
        if (at != null) {
            AABB local = (at.device() != null ? RackGeometry.deviceBox(at.device().u(), at.device().size()) : RackGeometry.deviceBox(at.u(), 1))
                    .inflate(OUTLINE_INFLATE);
            AABB world = RackGeometry.toWorld(local, master, at.facing()).move(-master.getX(), -master.getY(), -master.getZ());
            box = Shapes.create(world);
        }
        VoxelShape shape = box;
        event.addCustomRenderer((state, collector, poseStack, levelState) -> {
            if (shape != null) {
                Vec3 camera = levelState.cameraRenderState.pos;
                poseStack.pushPose();
                poseStack.translate(master.getX() - camera.x, master.getY() - camera.y, master.getZ() - camera.z);
                collector.submitShapeOutline(poseStack, shape, RenderTypes.linesTranslucent(), OUTLINE_COLOR, OUTLINE_WIDTH, false);
                poseStack.popPose();
            }
            return true;
        });
    }

    // --- The popup ---

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        Target at = target;
        if (at == null || minecraft.gui.hud.isHidden() || minecraft.gui.screen() != null) {
            return;
        }
        Font font = minecraft.font;
        int centerX = graphics.guiWidth() / 2, centerY = graphics.guiHeight() / 2;
        RackDevice device = at.device();
        if (device == null) {
            Component text = Component.translatable("gui.encodedlogistics.rack.hud.empty", at.u());
            int width = Math.max(MIN_W, font.width(text) + 2 * PAD_X), height = 9 + 2 * PAD_Y;
            int x = clampX(graphics, centerX + OFFSET_X, width), y = clampY(graphics, centerY + OFFSET_Y, height);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, x, y, width, height, PANEL_COLOR);
            graphics.text(font, text, x + PAD_X, y + PAD_Y, RackScreen.TEXT_MUTED, false);
            return;
        }
        RackDeviceInfo info = RackUnitPayloads.Info.forUnit(at.master(), device.u(), device.type());
        if (info == null) {
            // Not in yet (a moment at most): no half popup.
            return;
        }
        popup(graphics, font, device, info, centerX + OFFSET_X, centerY + OFFSET_Y, null);
    }

    // A device's popup (the rack's strip above it), its top left at (anchorX, anchorY) or as near as the window allows,
    // with an optional muted line at the bottom (the rack screen's hint). The HUD's and the rack screen's hover.
    public static void popup(GuiGraphicsExtractor graphics, Font font, RackDevice device, RackDeviceInfo info, int anchorX, int anchorY,
            @Nullable Component footer) {
        // The rack's strip goes above the popup, so the popup starts lower.
        int headerH = info.header().isPresent() ? HEADER_STRIP_H + 2 : 0;
        Component units = RackScreen.unitRange(device);
        Component badge = Component.translatable("hud.encodedlogistics.rack.scheduler");
        int badgeWidth = info.schedulerBadge() ? 8 + BADGE + 2 + font.width(badge) : 0;
        int content = Math.max(20 + Math.max(font.width(info.name()), font.width(units) + badgeWidth), 8 + font.width(info.statusText()));
        if (footer != null) {
            content = Math.max(content, font.width(footer));
        }
        for (RackDeviceInfo.InfoLine line : info.lines()) {
            content = Math.max(content, font.width(line.label()) + GAP + font.width(line.value()));
        }
        // As wide as the rack's strip above it, if that's wider.
        int headerWidth = info.header().map(header -> headerWidth(font, header)).orElse(0);
        int width = Math.max(Mth.clamp(content + 2 * PAD_X, MIN_W, MAX_W), headerWidth), inner = width - 2 * PAD_X;
        int height = PAD_Y + HEADER_H + DIVIDER_TOP + 1 + DIVIDER_BOTTOM + LINE_H + PAD_Y;
        for (RackDeviceInfo.InfoLine line : info.lines()) {
            height += LINE_H + (line.bar().isPresent() ? BAR_H : 0);
        }
        if (footer != null) {
            height += DIVIDER_TOP + 1 + DIVIDER_BOTTOM + LINE_H;
        }
        int x = clampX(graphics, anchorX, width), y = clampY(graphics, anchorY, height + headerH) + headerH;
        if (info.header().isPresent()) {
            header(graphics, font, info.header().get(), x, y - headerH, width);
        }
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, x, y, width, height, PANEL_COLOR);
        int left = x + PAD_X, lineY = y + PAD_Y;

        graphics.item(new ItemStack(device.type().item()), left, lineY);
        graphics.text(font, font.substrByWidth(info.name(), inner - 20).getString(), left + 20, lineY, RackScreen.TEXT, false);
        graphics.text(font, units, left + 20, lineY + 9, RackScreen.TEXT_MUTED, false);
        if (info.schedulerBadge()) {
            // Part of its rack's Scheduler: the badge and "Scheduler", right-aligned opposite the units.
            int textX = left + inner - font.width(badge);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BADGE_SPRITE, textX - 2 - BADGE, lineY + 8, BADGE, BADGE);
            graphics.text(font, badge, textX, lineY + 9, SCHEDULER, false);
        }
        lineY += HEADER_H + DIVIDER_TOP;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DIVIDER, left, lineY, inner, 1);
        lineY += 1 + DIVIDER_BOTTOM;

        Identifier dot = switch (info.status()) {
            case ONLINE -> DOT_ONLINE;
            case OFFLINE -> DOT_OFFLINE;
            case FAULT -> DOT_FAULT;
            case WARNING -> DOT_WARNING;
        };
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, dot, left, lineY + 1, 5, 5);
        graphics.text(font, info.statusText(), left + 8, lineY, RackScreen.statusColor(info.status()), false);
        lineY += LINE_H;

        for (RackDeviceInfo.InfoLine line : info.lines()) {
            graphics.text(font, line.label(), left, lineY, RackScreen.TEXT_MUTED, false);
            graphics.text(font, line.value(), left + inner - font.width(line.value()), lineY, RackScreen.TEXT, false);
            lineY += LINE_H;
            if (line.bar().isPresent()) {
                RackDeviceInfo.Bar bar = line.bar().get();
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BAR_TRACK, left, lineY - 1, inner, 5);
                int fill = Math.round((inner - 2) * Mth.clamp(bar.fraction(), 0.0F, 1.0F));
                if (fill > 0) {
                    Identifier sprite = switch (bar.style()) {
                        case NORMAL -> BAR_FILL;
                        case WARN -> BAR_WARN;
                        case LOW -> BAR_LOW;
                    };
                    graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, left + 1, lineY, fill, 3);
                }
                lineY += BAR_H;
            }
        }
        if (footer != null) {
            lineY += DIVIDER_TOP;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DIVIDER, left, lineY, inner, 1);
            lineY += 1 + DIVIDER_BOTTOM;
            graphics.text(font, font.plainSubstrByWidth(footer.getString(), inner), left, lineY, RackScreen.TEXT_DISABLED, false);
        }
    }

    // The rack's strip, as wide as the popup under it; degraded, amber behind a warning sign.
    private static int headerWidth(Font font, RackDeviceInfo.Header header) {
        return PAD_X + (header.degraded() ? 9 + 3 : 0) + font.width(header.text()) + PAD_X;
    }

    private static void header(GuiGraphicsExtractor graphics, Font font, RackDeviceInfo.Header header, int x, int y, int width) {
        int icon = header.degraded() ? 9 + 3 : 0;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, x, y, width, HEADER_STRIP_H, PANEL_COLOR);
        if (header.degraded()) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, WARNING_SIGN, x + PAD_X, y + (HEADER_STRIP_H - 8) / 2, 9, 8);
        }
        graphics.text(font, header.text(), x + PAD_X + icon, y + (HEADER_STRIP_H - 8) / 2, header.degraded() ? RackScreen.AMBER : RackScreen.TEXT_MUTED, false);
    }

    private static int clampX(GuiGraphicsExtractor graphics, int x, int width) {
        return Mth.clamp(x, 2, Math.max(2, graphics.guiWidth() - width - 2));
    }

    private static int clampY(GuiGraphicsExtractor graphics, int y, int height) {
        return Mth.clamp(y, 2, Math.max(2, graphics.guiHeight() - height - 2));
    }
}
