/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.rack;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.ServerRackBlock;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.net.RackElevationPayloads;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackElevations;
import net.zagdrath.encodedlogistics.rack.RackTemplate;

// Looking at a Server Rack while holding a written Rack Elevation: the plan against that rack, asked of the server as
// soon as the crosshair lands on the rack and then every QUERY_INTERVAL ticks (RackElevationPayloads.PreviewQuery), shown
// - on the rack's front as ghosts over each planned device's units (RackRenderer: elevation_ghost_{fill,skip,missing}.png,
//   green would be installed or is there already, amber skipped, red free but its item isn't available);
// - in the rack's popup (previews/hud_popup.png): "Rack Elevation <name>", Preview, and Fill / Skip / Missing.
// The last rack previewed is what the editor's materials count network storage against.
public final class RackElevationPreview {
    public static final Identifier LAYER = EncodedLogistics.id("rack_elevation_preview");
    private static final int QUERY_INTERVAL = 10;
    private static final long RECENT_MS = 60_000;

    private static @Nullable BlockPos target;
    private static int steadyTicks;
    private static long lastRack = Long.MIN_VALUE, lastSeen;

    private RackElevationPreview() {}

    // The hand holding a written elevation, or null.
    public static @Nullable InteractionHand heldHand() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return null;
        }
        for (InteractionHand hand : InteractionHand.values()) {
            if (RackTemplate.of(minecraft.player.getItemInHand(hand)) != null) {
                return hand;
            }
        }
        return null;
    }

    // The rack (its master) the crosshair is on, any face, door open or not.
    private static @Nullable BlockPos lookedAt(Minecraft minecraft) {
        if (minecraft.level == null || !(minecraft.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        BlockState state = minecraft.level.getBlockState(hit.getBlockPos());
        if (!(state.getBlock() instanceof ServerRackBlock)) {
            return null;
        }
        RackBlockEntity rack = ServerRackBlock.rack(minecraft.level, hit.getBlockPos(), state);
        return rack != null ? rack.getBlockPos() : null;
    }

    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        InteractionHand hand = heldHand();
        BlockPos now = hand != null ? lookedAt(minecraft) : null;
        steadyTicks = now != null && now.equals(target) ? steadyTicks + 1 : 0;
        target = now;
        if (now != null && hand != null) {
            lastRack = now.asLong();
            lastSeen = Util.getMillis();
            if (steadyTicks % QUERY_INTERVAL == 0) {
                ClientPacketDistributor.sendToServer(new RackElevationPayloads.PreviewQuery(now, hand));
            }
        }
    }

    // The rack last previewed, if lately (Long.MIN_VALUE: none).
    public static long lastRack() {
        return Util.getMillis() - lastSeen < RECENT_MS ? lastRack : Long.MIN_VALUE;
    }

    // Whether the popup shows the preview (the rack's own unit popup stands aside).
    public static boolean active() {
        BlockPos at = target;
        return at != null && RackElevationPayloads.Preview.forRack(at) != null;
    }

    // What the overlay draws on a rack: each planned device's units and fate, while an elevation is held; else null.
    public static @Nullable List<RackElevationPayloads.Step> overlay(BlockPos rack) {
        if (heldHand() == null) {
            return null;
        }
        RackElevationPayloads.Preview preview = RackElevationPayloads.Preview.forRack(rack);
        return preview != null ? preview.steps() : null;
    }

    // --- The popup ---

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos at = target;
        InteractionHand hand = heldHand();
        if (at == null || hand == null || minecraft.player == null || minecraft.gui.hud.isHidden() || minecraft.gui.screen() != null) {
            return;
        }
        RackElevationPayloads.Preview preview = RackElevationPayloads.Preview.forRack(at);
        if (preview == null) {
            return;
        }
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("gui.encodedlogistics.rack_elevation.fill"),
                Component.translatable("gui.encodedlogistics.rack_elevation.fill_value", preview.fill())));
        MutableComponent skip = Component.literal(Integer.toString(preview.skip()));
        if (preview.skip() > 0 && !preview.firstSkip().getString().isEmpty()) {
            skip.append(" (").append(preview.firstSkip()).append(")");
        }
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("gui.encodedlogistics.rack_elevation.skip"), skip));
        MutableComponent missing = Component.literal(Integer.toString(preview.missing().size()));
        if (!preview.missing().isEmpty()) {
            missing.append(" (");
            for (int i = 0; i < Math.min(3, preview.missing().size()); i++) {
                missing.append(i > 0 ? Component.literal(", ") : Component.empty()).append(preview.missing().get(i));
            }
            missing.append(preview.missing().size() > 3 ? ", ...)" : ")");
        }
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("gui.encodedlogistics.rack_elevation.missing_label"), missing));
        RackDeviceInfo info = new RackDeviceInfo(Component.translatable("item.encodedlogistics.rack_elevation"), RackDeviceInfo.Status.WARNING,
                Component.translatable("gui.encodedlogistics.rack_elevation.preview"), lines);
        ItemStack icon = minecraft.player.getItemInHand(hand);
        Component name = Component.literal(preview.name());
        RackHud.popup(graphics, minecraft.font, icon, name, info, graphics.guiWidth() / 2 + 12, graphics.guiHeight() / 2 - 8, null);
    }

    // The ghost texture for a fate: green would be installed (or is there already), amber skipped, red missing.
    public static Identifier ghostTexture(RackElevations.State state) {
        return EncodedLogistics.id("textures/misc/elevation_ghost_" + switch (state) {
            case FILL, SAME -> "fill";
            case SKIP -> "skip";
            case MISSING -> "missing";
        } + ".png");
    }

    public static void clear() {
        target = null;
        lastRack = Long.MIN_VALUE;
        RackElevationPayloads.Preview.clear();
    }
}
