/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.rack;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.AccessPointBlock;
import net.zagdrath.encodedlogistics.block.WirelessBridgeBlock;
import net.zagdrath.encodedlogistics.block.WirelessPortBlock;
import net.zagdrath.encodedlogistics.net.MachineBridgesPayload;
import net.zagdrath.encodedlogistics.net.WirelessInfoPayloads;

// The wireless blocks' popup by the crosshair (Access Points, Wireless Bridges, Wireless Ports, and machines with a
// Small Wireless Bridge on): the rack's popup (RackHud.popup) with the block's icon, its name and device name (AP01),
// its status and lines - Clients and Uplink, or Controller and Devices / Lanes, or Controller, Inventory and Moved, or a
// machine's Controller, Progress, Energy and Recipe - asked of the server as soon as the crosshair
// lands on it, then every QUERY_INTERVAL ticks. Until its answer is in, nothing shows. Hidden with F1 and while a screen
// is open.
public final class WirelessHud {
    public static final Identifier LAYER = EncodedLogistics.id("wireless_popup");
    private static final int QUERY_INTERVAL = 10, OFFSET_X = 12, OFFSET_Y = -8;

    private static @Nullable BlockPos target;
    private static int steadyTicks;

    private WirelessHud() {}

    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos now = null;
        if (minecraft.level != null && minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            var block = minecraft.level.getBlockState(hit.getBlockPos()).getBlock();
            if (block instanceof AccessPointBlock || block instanceof WirelessBridgeBlock || block instanceof WirelessPortBlock
                    || MachineBridgesPayload.has(hit.getBlockPos())) {
                now = hit.getBlockPos().immutable();
            }
        }
        steadyTicks = now != null && now.equals(target) ? steadyTicks + 1 : 0;
        target = now;
        if (now != null && steadyTicks % QUERY_INTERVAL == 0) {
            ClientPacketDistributor.sendToServer(new WirelessInfoPayloads.Query(now));
        }
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos at = target;
        if (at == null || minecraft.level == null || minecraft.gui.hud.isHidden() || minecraft.gui.screen() != null) {
            return;
        }
        WirelessInfoPayloads.Info info = WirelessInfoPayloads.Info.at(at);
        if (info == null) {
            return;
        }
        ItemStack icon = new ItemStack(minecraft.level.getBlockState(at).getBlock());
        Component subtitle = Component.literal(info.name());
        RackHud.popup(graphics, minecraft.font, icon, subtitle, info.info(), graphics.guiWidth() / 2 + OFFSET_X, graphics.guiHeight() / 2 + OFFSET_Y, null);
    }
}
