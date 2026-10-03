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
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.registry.ModBlocks;

// A cable part's item (terminals, ports, the tap, the sensor, links, planes). On a cable it mounts on the face you click,
// sneaking or not. On any other block's face it goes on the cable in front of that face, if there is one,
// facing the block; otherwise it puts a part host in the space in front, holding the part against that face - before
// the block itself reacts, so a network block's own screen doesn't open instead.
public class PartItem extends Item {
    private final PartType type;

    public PartItem(Item.Properties properties, PartType type) {
        super(properties);
        this.type = type;
    }

    public PartType getPartType() {
        return type;
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        // On a cable: the face clicked. Done here rather than in the cable's useItemOn, which the game skips while
        // sneaking - and sneaking is how a part gets placed by a chest or machine without opening it.
        if (level.getBlockState(clicked).getBlock() instanceof NetworkCableBlock && context.getPlayer() != null) {
            return NetworkCableBlock.attach(stack, level, clicked, context.getPlayer(), context.getClickedFace());
        }
        if (level.getBlockState(clicked).getBlock() instanceof PartHostBlock) {
            return InteractionResult.PASS;
        }
        Direction face = context.getClickedFace();
        BlockPos pos = clicked.relative(face);
        // A cable in front of the face: the part goes on the cable's side toward the block, as AE2's buses do.
        if (level.getBlockState(pos).getBlock() instanceof NetworkCableBlock && context.getPlayer() != null) {
            return NetworkCableBlock.attach(stack, level, pos, context.getPlayer(), face.getOpposite());
        }
        if (!level.getBlockState(pos).canBeReplaced()) {
            return InteractionResult.FAIL;
        }
        if (!level.isClientSide()) {
            level.setBlock(pos, ModBlocks.PART_HOST.get().defaultBlockState(), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof CableBlockEntity host) {
                host.setAttachment(face.getOpposite(), CableAttachments.Attachment.part(type));
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
