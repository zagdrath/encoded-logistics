/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.encodedlogistics.block.ServerRackBlock;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.client.ClientRenderHooks;
import net.zagdrath.encodedlogistics.rack.RackElevations;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.RackTemplate;

// The Rack Elevation: a clipboard with a rack plan on it (RackTemplate, the rack_template component; blank without one).
// Sneak-use on a Server Rack records the rack; use on one applies the plan (RackElevations); use anywhere else opens
// the editor (client.screen.RackElevationScreen). Blank ones stack to 16, written ones to 1.
public class RackElevationItem extends Item {
    public RackElevationItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) {
            ClientRenderHooks.openRackElevation(hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockState state = level.getBlockState(context.getClickedPos());
        Player player = context.getPlayer();
        if (player == null || !(state.getBlock() instanceof ServerRackBlock)) {
            // Anywhere else: the editor (use()).
            return InteractionResult.PASS;
        }
        RackBlockEntity rack = ServerRackBlock.rack(level, context.getClickedPos(), state);
        if (rack == null) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        ItemStack stack = context.getItemInHand();
        if (player.isSecondaryUseActive()) {
            RackElevations.record(serverPlayer, rack, stack);
        } else if (RackTemplate.of(stack) != null) {
            RackElevations.apply(serverPlayer, rack, stack);
        } else {
            serverPlayer.sendOverlayMessage(Component.translatable("tooltip.encodedlogistics.rack_elevation.blank"));
        }
        return InteractionResult.SUCCESS;
    }

    // Writes a plan on the elevation the player holds. A stack of blank ones: the one in hand is written and the rest go
    // back into the inventory (a written one stacks to 1).
    public static void writeHeld(ServerPlayer player, ItemStack stack, RackTemplate template) {
        if (stack.getCount() > 1) {
            ItemStack rest = stack.copyWithCount(stack.getCount() - 1);
            stack.setCount(1);
            RackTemplate.write(stack, template);
            player.getInventory().placeItemBackInInventory(rest, Prediction.SERVER_ONLY);
        } else {
            RackTemplate.write(stack, template);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        RackTemplate template = RackTemplate.of(stack);
        if (template == null) {
            tooltip.accept(Component.translatable("tooltip.encodedlogistics.rack_elevation.blank").withStyle(ChatFormatting.GRAY));
            return;
        }
        if (!template.name().isEmpty()) {
            tooltip.accept(Component.literal(template.name()).withStyle(ChatFormatting.AQUA));
        }
        tooltip.accept(Component.translatable("tooltip.encodedlogistics.rack_elevation.summary", template.devices().size(), template.units(),
                RackGeometry.UNITS).withStyle(ChatFormatting.GRAY));
        if (template.includeContents()) {
            tooltip.accept(Component.translatable("tooltip.encodedlogistics.rack_elevation.contents").withStyle(ChatFormatting.DARK_GRAY));
        }
        if (template.includeSettings()) {
            tooltip.accept(Component.translatable("tooltip.encodedlogistics.rack_elevation.settings").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
