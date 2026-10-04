/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.rack;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.block.BreakBlockEvent;
import net.zagdrath.encodedlogistics.block.NetworkControllerBlock;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;
import net.zagdrath.encodedlogistics.network.NetworkNodeHost;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.registry.ModItems;

// A Firewall's build permission, where players change a network: breaking or placing a network block on (or next to) a
// network, and mounting parts, anchors or facades on its cables.
public final class FirewallEvents {
    private FirewallEvents() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FirewallEvents::onBreak);
        NeoForge.EVENT_BUS.addListener(FirewallEvents::onPlace);
        NeoForge.EVENT_BUS.addListener(FirewallEvents::onUseBlock);
    }

    private static boolean isNetworkBlock(Level level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof NetworkNodeBlock || state.getBlock() instanceof NetworkControllerBlock
                || level.getBlockEntity(pos) instanceof NetworkNodeHost;
    }

    private static void onBreak(BreakBlockEvent event) {
        if (event.getLevel() instanceof ServerLevel level && isNetworkBlock(level, event.getPos(), event.getState())
                && !NetworkAccess.tell(NetworkAccess.allowedToBuild(level, event.getPos(), event.getPlayer()), event.getPlayer(), RackPermission.BUILD)) {
            event.setCanceled(true);
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof Player player
                && isNetworkBlock(level, event.getPos(), event.getPlacedBlock())
                && !NetworkAccess.tell(NetworkAccess.allowedToBuild(level, event.getPos(), player), player, RackPermission.BUILD)) {
            event.setCanceled(true);
        }
    }

    // Parts, anchors and facades go on cables by using them on the cable (not a block placement).
    private static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(event.getLevel() instanceof ServerLevel level) || !(level.getBlockState(event.getPos()).getBlock() instanceof NetworkCableBlock)
                || !stack.is(ModItems.CABLE_ANCHOR.get()) && !stack.is(ModItems.CABLE_FACADE.get()) && PartType.byItem(stack.getItem()) == null) {
            return;
        }
        if (!NetworkAccess.tell(NetworkAccess.allowed(level, event.getPos(), event.getEntity(), RackPermission.BUILD), event.getEntity(),
                RackPermission.BUILD)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }
}
