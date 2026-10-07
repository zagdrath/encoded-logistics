/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.rack;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.AccessPointBlock;
import net.zagdrath.encodedlogistics.block.WirelessBridgeBlock;
import net.zagdrath.encodedlogistics.block.WirelessPortBlock;
import net.zagdrath.encodedlogistics.display.SmallWirelessBridgeBlock;
import net.zagdrath.encodedlogistics.midrange.DiskDriveBlock;
import net.zagdrath.encodedlogistics.midrange.TapeDriveBlock;
import net.zagdrath.encodedlogistics.net.MachineBridgesPayload;
import net.zagdrath.encodedlogistics.net.WirelessInfoPayloads;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.signal.SignalBlock;

// The wireless blocks' popup by the crosshair (Access Points, Wireless Bridges, Wireless Ports, and Small Wireless
// Bridges - not the machines they're on - the Midrange Disk and Tape Drives, and the Cage Lights, Alarm Strobes and Speakers): the rack's popup (RackHud.popup) with the block's icon, its name and device name
// (AP01), its status and lines - Clients and Uplink, or Controller and Devices / Lanes, or Controller, Inventory and
// Moved, or a bridge's Controller, Machine, Lanes and Power - asked of the server as soon as the crosshair
// lands on it, then every QUERY_INTERVAL ticks. Until its answer is in, nothing shows. Hidden with F1 and while a screen
// is open.
//
// A Small Wireless Bridge is a block in front of its machine's face; one still drawn without its block (its space was
// taken) has its model's box tested against the look ray here, and when it's nearer than the block the crosshair is on,
// the popup is the bridge's (Controller, Machine, Lanes, Drain, Gateway, Power) and its box is outlined (bridgeBox).
public final class WirelessHud {
    public static final Identifier LAYER = EncodedLogistics.id("wireless_popup");
    private static final int QUERY_INTERVAL = 10, OFFSET_X = 12, OFFSET_Y = -8;
    private static final double REACH = 6;

    // What's targeted: a block (or the machine a bridge is on, with bridge set), and where its answer is kept (the block,
    // or the space in front of the bridge's face).
    private static @Nullable BlockPos target, answerAt;
    private static boolean bridge;
    private static @Nullable AABB bridgeBox;
    private static int steadyTicks;

    private WirelessHud() {}

    public static void tick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos now = null, answer = null;
        boolean onBridge = false;
        AABB box = null;
        double blockDistance = Double.MAX_VALUE;
        if (minecraft.level != null && minecraft.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            BlockState state = minecraft.level.getBlockState(hit.getBlockPos());
            var block = state.getBlock();
            if (block instanceof AccessPointBlock || block instanceof WirelessBridgeBlock || block instanceof WirelessPortBlock
                    || block instanceof DiskDriveBlock || block instanceof TapeDriveBlock || block instanceof SignalBlock) {
                now = hit.getBlockPos().immutable();
                answer = now;
            } else if (block instanceof SmallWirelessBridgeBlock && MachineBridgesPayload.has(hit.getBlockPos().relative(state.getValue(SmallWirelessBridgeBlock.FACING)))) {
                // A Small Wireless Bridge's block: the bridge's popup (the machine behind it has none of its own).
                now = hit.getBlockPos().relative(state.getValue(SmallWirelessBridgeBlock.FACING));
                answer = hit.getBlockPos().immutable();
                onBridge = true;
            }
            LocalPlayer player = minecraft.player;
            blockDistance = player != null ? hit.getLocation().distanceTo(player.getEyePosition()) : Double.MAX_VALUE;
        }
        LocalPlayer player = minecraft.player;
        if (minecraft.level != null && player != null) {
            Vec3 eye = player.getEyePosition();
            Vec3 end = eye.add(player.getViewVector(1.0F).scale(REACH));
            double nearest = blockDistance + 1.0E-4;
            for (MachineBridgesPayload.Entry entry : MachineBridgesPayload.shown().values()) {
                BlockPos at = entry.pos().relative(entry.face());
                // Only bridges drawn without their block (MachineBridgeRenderer); a placed one is targeted as a block.
                if (at.distToCenterSqr(eye) > (REACH + 2) * (REACH + 2) || minecraft.level.getBlockState(at).is(ModBlocks.SMALL_WIRELESS_BRIDGE.get())) {
                    continue;
                }
                AABB shape = ModBlocks.SMALL_WIRELESS_BRIDGE.get().defaultBlockState()
                        .setValue(SmallWirelessBridgeBlock.FACING, entry.face().getOpposite()).getShape(minecraft.level, at).bounds().move(at);
                Optional<Vec3> crossed = shape.clip(eye, end);
                if (crossed.isPresent() && crossed.get().distanceTo(eye) < nearest) {
                    nearest = crossed.get().distanceTo(eye);
                    now = entry.pos();
                    answer = at;
                    onBridge = true;
                    box = shape;
                }
            }
        }
        steadyTicks = now != null && now.equals(target) && onBridge == bridge ? steadyTicks + 1 : 0;
        target = now;
        answerAt = answer;
        bridge = onBridge;
        bridgeBox = box;
        if (now != null && steadyTicks % QUERY_INTERVAL == 0) {
            ClientPacketDistributor.sendToServer(new WirelessInfoPayloads.Query(now, onBridge));
        }
    }

    // The box of the Small Wireless Bridge under the crosshair (for its outline), or null.
    public static @Nullable AABB bridgeBox() {
        return bridgeBox;
    }

    public static void render(GuiGraphicsExtractor graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockPos at = target, answer = answerAt;
        if (at == null || answer == null || minecraft.level == null || minecraft.options.hideGui || minecraft.screen != null) {
            return;
        }
        WirelessInfoPayloads.Info info = WirelessInfoPayloads.Info.at(answer);
        if (info == null) {
            return;
        }
        ItemStack icon = bridge ? ModItems.SMALL_WIRELESS_BRIDGE.toStack() : new ItemStack(minecraft.level.getBlockState(at).getBlock());
        Component subtitle = Component.literal(info.name());
        RackHud.popup(graphics, minecraft.font, icon, subtitle, info.info(), graphics.guiWidth() / 2 + OFFSET_X, graphics.guiHeight() / 2 + OFFSET_Y, null);
    }
}
