/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.menu.GhostSlot;

// Client to server: an item dragged from JEI onto a ghost slot of an open menu. The menu handles it as a left click
// with one of the item in hand (its own ghost logic: PartMenus.ghost and the like), the player's real carried stack set
// aside meanwhile; only ghost slots, which never take or give a real item, are reachable this way.
public record GhostSlotPayload(int containerId, int slot, ItemStack stack) implements CustomPacketPayload {
    public static final Type<GhostSlotPayload> TYPE = new Type<>(EncodedLogistics.id("ghost_slot"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GhostSlotPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, GhostSlotPayload::containerId,
            ByteBufCodecs.VAR_INT, GhostSlotPayload::slot,
            ItemStack.STREAM_CODEC, GhostSlotPayload::stack,
            GhostSlotPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    static void handle(GhostSlotPayload payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            apply(player, payload.containerId(), payload.slot(), payload.stack());
        }
    }

    // Sets the ghost slot at index of the player's open menu to one of stack; false when that isn't an active ghost slot.
    public static boolean apply(Player player, int containerId, int index, ItemStack stack) {
        AbstractContainerMenu menu = player.containerMenu;
        if (menu.containerId != containerId || !menu.stillValid(player) || index < 0 || index >= menu.slots.size() || stack.isEmpty()) {
            return false;
        }
        Slot slot = menu.getSlot(index);
        if (!(slot instanceof GhostSlot) || !slot.isActive()) {
            return false;
        }
        ItemStack carried = menu.getCarried();
        menu.setCarried(stack.copyWithCount(1));
        try {
            menu.clicked(index, 0, ContainerInput.PICKUP, player);
        } finally {
            menu.setCarried(carried);
        }
        return true;
    }
}
