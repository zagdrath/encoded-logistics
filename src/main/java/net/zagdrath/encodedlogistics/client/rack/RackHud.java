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

// The rack's unit popup and outline (screens/rack/hud.json). While the crosshair is on a rack's front (or its back with
// the rear doors open) it points at a unit: a device there is outlined (instead of the block outline, which a rack
// never shows) and the popup by the crosshair shows its icon, name, units, status and its own lines (RackDeviceInfo,
// asked of the server when the crosshair settles, then every QUERY_INTERVAL ticks); an empty unit just says so.
// Hidden with F1 and while a screen is open.
public final class RackHud {
    public static final Identifier LAYER = EncodedLogistics.id("rack_unit_popup");

    private static final Identifier PANEL = EncodedLogistics.id("hud/panel"), DIVIDER = EncodedLogistics.id("hud/divider"),
            DOT_ONLINE = EncodedLogistics.id("hud/dot_online"), DOT_OFFLINE = EncodedLogistics.id("hud/dot_offline"),
            DOT_FAULT = EncodedLogistics.id("hud/dot_fault"), BAR_TRACK = EncodedLogistics.id("hud/bar_track"),
            BAR_FILL = EncodedLogistics.id("hud/bar_fill"), BAR_WARN = EncodedLogistics.id("hud/bar_fill_warn"),
            BAR_LOW = EncodedLogistics.id("hud/bar_fill_low");
    private static final int OFFSET_X = 12, OFFSET_Y = -8, PAD_X = 5, PAD_Y = 4, MIN_W = 96, MAX_W = 180;
    private static final int HEADER_H = 18, DIVIDER_TOP = 2, DIVIDER_BOTTOM = 3, LINE_H = 10, BAR_H = 6, GAP = 8;
    private static final int PANEL_COLOR = ARGB.color(Math.round(0.92F * 255), 0xFFFFFF);
    private static final int OUTLINE_COLOR = ARGB.color(Math.round(0.85F * 255), 0x5CF0B8);
    private static final float OUTLINE_WIDTH = 2.0F, OUTLINE_INFLATE = 0.15F;
    private static final int SETTLE_TICKS = 2, QUERY_INTERVAL = 10;

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
        Direction facing = state.getValue(ServerRackBlock.FACING);
        RackGeometry.Face face = RackGeometry.face(hit.getDirection(), facing);
        if (face == RackGeometry.Face.OTHER || face == RackGeometry.Face.REAR && !rack.isRearOpen()) {
            return null;
        }
        int u = RackGeometry.unitAt(RackGeometry.toLocal(hit.getLocation(), rack.getBlockPos(), facing).y);
        return u == 0 ? null : new Target(rack.getBlockPos(), facing, u, rack.deviceAt(u));
    }

    // --- Following the crosshair ---

    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        Target now = minecraft.level != null && minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
                ? target(minecraft.level, hit) : null;
        steadyTicks = now != null && now.sameUnit(target) ? steadyTicks + 1 : 0;
        target = now;
        if (now != null && now.device() != null && steadyTicks >= SETTLE_TICKS && (steadyTicks - SETTLE_TICKS) % QUERY_INTERVAL == 0) {
            ClientPacketDistributor.sendToServer(new RackUnitPayloads.Query(now.master(), now.device().u()));
        }
    }

    // --- The outline ---

    // Racks never show the block outline; a targeted device gets its own.
    public static void outline(ExtractBlockOutlineRenderStateEvent event) {
        if (!(event.getBlockState().getBlock() instanceof ServerRackBlock)) {
            return;
        }
        Target at = target(event.getLevel(), event.getHitResult());
        VoxelShape box = null;
        BlockPos master = at != null ? at.master() : event.getBlockPos();
        if (at != null && at.device() != null) {
            AABB local = RackGeometry.deviceBox(at.device().u(), at.device().size()).inflate(OUTLINE_INFLATE);
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
        RackDeviceInfo info = RackUnitPayloads.Info.forUnit(at.master(), device.u());
        if (info == null) {
            info = new RackDeviceInfo(device.name(), device.shownStatus(), device.shownStatus().text(), List.of());
        }
        Component units = RackScreen.unitRange(device);
        int content = Math.max(20 + Math.max(font.width(info.name()), font.width(units)), 8 + font.width(info.statusText()));
        for (RackDeviceInfo.InfoLine line : info.lines()) {
            content = Math.max(content, font.width(line.label()) + GAP + font.width(line.value()));
        }
        int width = Mth.clamp(content + 2 * PAD_X, MIN_W, MAX_W), inner = width - 2 * PAD_X;
        int height = PAD_Y + HEADER_H + DIVIDER_TOP + 1 + DIVIDER_BOTTOM + LINE_H + PAD_Y;
        for (RackDeviceInfo.InfoLine line : info.lines()) {
            height += LINE_H + (line.bar().isPresent() ? BAR_H : 0);
        }
        int x = clampX(graphics, centerX + OFFSET_X, width), y = clampY(graphics, centerY + OFFSET_Y, height);
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, PANEL, x, y, width, height, PANEL_COLOR);
        int left = x + PAD_X, lineY = y + PAD_Y;

        graphics.item(new ItemStack(device.type().item()), left, lineY);
        graphics.text(font, font.substrByWidth(info.name(), inner - 20).getString(), left + 20, lineY, RackScreen.TEXT, false);
        graphics.text(font, units, left + 20, lineY + 9, RackScreen.TEXT_MUTED, false);
        lineY += HEADER_H + DIVIDER_TOP;
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, DIVIDER, left, lineY, inner, 1);
        lineY += 1 + DIVIDER_BOTTOM;

        Identifier dot = switch (info.status()) {
            case ONLINE -> DOT_ONLINE;
            case OFFLINE -> DOT_OFFLINE;
            case FAULT -> DOT_FAULT;
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
    }

    private static int clampX(GuiGraphicsExtractor graphics, int x, int width) {
        return Mth.clamp(x, 2, Math.max(2, graphics.guiWidth() - width - 2));
    }

    private static int clampY(GuiGraphicsExtractor graphics, int y, int height) {
        return Mth.clamp(y, 2, Math.max(2, graphics.guiHeight() - height - 2));
    }
}
