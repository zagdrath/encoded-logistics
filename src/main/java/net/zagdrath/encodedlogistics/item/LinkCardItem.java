/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.block.PartHostBlock;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkBridgeBlockEntity;
import net.zagdrath.encodedlogistics.part.PointToPointPart;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// The Link Card pairs Network Bridges and Point-to-Point Link endpoints. Sneak-use it on one to store its address (a
// card taken off a stack of blank ones gets it); use it on the partner to pair the two - Bridge with Bridge, or a
// Point-to-Point Link input with an output carrying the same thing. The card keeps the address, so more outputs can be
// paired to the same input. Sneak-use in the air clears it. Blank cards stack; written ones don't.
public class LinkCardItem extends Item {
    public LinkCardItem(Item.Properties properties) {
        super(properties);
    }

    public static @Nullable LinkAddress address(ItemStack stack) {
        return stack.get(ModDataComponents.LINK_ADDRESS.get());
    }

    // What a use landed on: a Bridge, or the Point-to-Point Link on the side looked at.
    private record Target(LinkAddress address, @Nullable NetworkBridgeBlockEntity bridge, @Nullable PointToPointPart p2p) {}

    private static @Nullable Target target(Level level, BlockPos pos, Vec3 hit) {
        if (level.getBlockEntity(pos) instanceof NetworkBridgeBlockEntity bridge) {
            return new Target(LinkAddress.bridge(GlobalPos.of(level.dimension(), pos)), bridge, null);
        }
        if (level.getBlockEntity(pos) instanceof CableBlockEntity host) {
            Direction side = level.getBlockState(pos).getBlock() instanceof PartHostBlock ? PartHostBlock.mount(host.getAttachments())
                    : NetworkCableBlock.sideAt(hit, pos);
            if (side != null && host.part(side) instanceof PointToPointPart p2p) {
                return new Target(LinkAddress.p2p(GlobalPos.of(level.dimension(), pos), side, p2p.linkType()), null, p2p);
            }
        }
        return null;
    }

    // Before the block reacts, so a Bridge or Point-to-Point Link doesn't open its screen instead.
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        Target target = target(level, context.getClickedPos(), context.getClickLocation());
        if (target == null || player == null) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            store(stack, player, context.getHand(), target.address());
            player.sendOverlayMessage(Component.translatable("message.encodedlogistics.link_card.stored"));
            level.playSound(null, context.getClickedPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.4F);
            return InteractionResult.SUCCESS;
        }
        LinkAddress stored = address(stack);
        if (stored == null) {
            player.sendOverlayMessage(Component.translatable("tooltip.encodedlogistics.link_card.hint"));
            return InteractionResult.SUCCESS;
        }
        Component message = pair(serverLevel, stored, target);
        player.sendOverlayMessage(message);
        return InteractionResult.SUCCESS;
    }

    private static Component pair(ServerLevel level, LinkAddress stored, Target target) {
        if (stored.kind() != target.address().kind()) {
            return Component.translatable("message.encodedlogistics.link_card.mismatch");
        }
        if (target.bridge() != null) {
            NetworkBridgeBlockEntity other = NetworkBridgeBlockEntity.at(level.getServer(), stored.pos(), true);
            if (other == null) {
                return Component.translatable("message.encodedlogistics.link_card.gone");
            }
            if (other == target.bridge()) {
                return Component.translatable("message.encodedlogistics.link_card.same");
            }
            if (!NetworkBridgeBlockEntity.pair(other, target.bridge())) {
                return Component.translatable("message.encodedlogistics.link_card.dimension");
            }
            level.playSound(null, target.bridge().getBlockPos(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5F, 1.6F);
            return Component.translatable("message.encodedlogistics.link_card.paired");
        }
        PointToPointPart p2p = target.p2p();
        if (!stored.pos().dimension().equals(level.dimension())) {
            return Component.translatable("message.encodedlogistics.link_card.p2p_dimension");
        }
        level.getChunkAt(stored.pos().pos());
        PointToPointPart other = stored.side().map(side -> PointToPointPart.at(level, stored.pos().pos(), side)).orElse(null);
        if (other == null) {
            return Component.translatable("message.encodedlogistics.link_card.gone");
        }
        return switch (PointToPointPart.pair(other, p2p)) {
            case PAIRED -> {
                level.playSound(null, p2p.host().getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.8F);
                yield Component.translatable("message.encodedlogistics.link_card.paired");
            }
            case SAME_ENDPOINT -> Component.translatable("message.encodedlogistics.link_card.same");
            case OTHER_TYPE -> Component.translatable("message.encodedlogistics.link_card.other_type");
            case SAME_DIRECTION -> Component.translatable("message.encodedlogistics.link_card.same_direction");
        };
    }

    // Writes the address onto the card (one taken off the stack if there are several).
    private static void store(ItemStack stack, Player player, InteractionHand hand, LinkAddress address) {
        if (stack.getCount() > 1) {
            ItemStack written = stack.split(1);
            write(written, address);
            player.getInventory().placeItemBackInInventory(written, Prediction.SERVER_ONLY);
        } else {
            write(stack, address);
        }
    }

    private static void write(ItemStack stack, LinkAddress address) {
        stack.set(ModDataComponents.LINK_ADDRESS.get(), address);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
    }

    // Sneak-use in the air clears a written card.
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || address(stack) == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            stack.remove(ModDataComponents.LINK_ADDRESS.get());
            stack.remove(DataComponents.MAX_STACK_SIZE);
            player.sendOverlayMessage(Component.translatable("message.encodedlogistics.link_card.cleared"));
        }
        return InteractionResult.SUCCESS;
    }
}
