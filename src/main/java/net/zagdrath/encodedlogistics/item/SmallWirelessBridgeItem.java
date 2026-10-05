/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.encodedlogistics.machine.MachineAccess;
import net.zagdrath.encodedlogistics.machine.MachineBridge;
import net.zagdrath.encodedlogistics.machine.MachineBridges;
import net.zagdrath.encodedlogistics.machine.MachineInfo;
import net.zagdrath.encodedlogistics.net.MachineBridgesPayload;

// The Small Wireless Bridge: used on a face of an Arcforge machine it goes on there (MachineBridges keeps it, by the
// block and face; one per machine - for a multiblock, one on the whole structure) as a block in front of that face (it
// needs the space free), before the machine's own screen would open. Only on a block that exposes Arcforge's machine
// control, and only one its player may use (the owner Arcforge tracks, if any). Then a Link Card from a Wireless
// Controller links it, like a Wireless Bridge.
public class SmallWirelessBridgeItem extends Item {
    public SmallWirelessBridgeItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        Direction face = context.getClickedFace();
        if (player == null) {
            return InteractionResult.PASS;
        }
        // The client can't see machine control; it takes the use on any block entity while the integration is on.
        if (!(level instanceof ServerLevel serverLevel)) {
            return MachineBridgesPayload.clientEnabled() && level.getBlockEntity(pos) != null ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        MachineAccess access = MachineBridges.access();
        if (access == null || !access.isMachine(serverLevel, pos)) {
            if (level.getBlockEntity(pos) == null) {
                return InteractionResult.PASS;
            }
            player.sendOverlayMessage(Component.translatable(access == null ? "message.encodedlogistics.small_bridge.no_integration"
                    : "message.encodedlogistics.small_bridge.not_machine"));
            return InteractionResult.SUCCESS;
        }
        MachineInfo info = access.info(serverLevel, pos);
        if (info == null) {
            return InteractionResult.PASS;
        }
        if (!MachineBridges.mayUse(player, info)) {
            player.sendOverlayMessage(Component.translatable("message.encodedlogistics.small_bridge.not_yours", info.name()));
            return InteractionResult.SUCCESS;
        }
        MachineBridges bridges = MachineBridges.get(serverLevel);
        MachineBridge existing = bridges.forMachine(info.position());
        if (existing == null) {
            existing = MachineBridges.at(serverLevel, pos);
        }
        if (existing != null) {
            player.sendOverlayMessage(Component.translatable("message.encodedlogistics.small_bridge.already", info.name()));
            return InteractionResult.SUCCESS;
        }
        // Its block goes in front of the face: the space has to be free.
        BlockState front = level.getBlockState(pos.relative(face));
        if (!front.canBeReplaced() || !front.getFluidState().isEmpty()) {
            player.sendOverlayMessage(Component.translatable("message.encodedlogistics.small_bridge.blocked"));
            return InteractionResult.SUCCESS;
        }
        bridges.attach(serverLevel, pos, face, info);
        stack.consume(1, player);
        player.sendOverlayMessage(Component.translatable("message.encodedlogistics.small_bridge.attached", info.name()));
        level.playSound(null, pos, SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.6F, 1.2F);
        return InteractionResult.SUCCESS;
    }
}
