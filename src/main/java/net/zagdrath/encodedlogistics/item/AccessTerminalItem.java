/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.zagdrath.encodedlogistics.block.PartHostBlock;
import net.zagdrath.encodedlogistics.block.cable.CableAttachments;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.registry.ModBlocks;

// The Access Terminal. On a cable it mounts on the side you're looking at (NetworkCableBlock does that). On any other
// block's face it puts a part host in the space in front, holding the terminal against that face, screen out - before
// the block itself reacts, so a network block's own screen doesn't open instead.
public class AccessTerminalItem extends Item {
    public AccessTerminalItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        if (level.getBlockState(clicked).getBlock() instanceof NetworkCableBlock || level.getBlockState(clicked).getBlock() instanceof PartHostBlock) {
            return InteractionResult.PASS;
        }
        Direction face = context.getClickedFace();
        BlockPos pos = clicked.relative(face);
        if (!level.getBlockState(pos).canBeReplaced()) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, ModBlocks.PART_HOST.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof CableBlockEntity host) {
                host.setAttachment(face.getOpposite(), CableAttachments.Attachment.TERMINAL);
            }
            if (level instanceof ServerLevel serverLevel) {
                ControllerStructures.get(serverLevel).markTopologyChanged();
            }
            stack.consume(1, context.getPlayer());
            level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }
}
