/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.blockentity.CapacitorBankBlockEntity;
import net.zagdrath.encodedlogistics.net.CapacitorBankPayload;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// The Capacitor Bank screen: no slots. While it's open the server sends the bank's figures every SYNC_INTERVAL ticks.
public class CapacitorBankMenu extends AbstractContainerMenu {
    private static final int SYNC_INTERVAL = 10;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final Player player;
    private int ticksUntilSync;

    // Client constructor, with the bank's position written by the server.
    public CapacitorBankMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos());
    }

    public CapacitorBankMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenuTypes.CAPACITOR_BANK.get(), containerId);
        this.pos = pos;
        this.player = inventory.player;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (--ticksUntilSync > 0 || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        ticksUntilSync = SYNC_INTERVAL;
        if (player.level().getBlockEntity(pos) instanceof CapacitorBankBlockEntity bank) {
            PacketDistributor.sendToPlayer(serverPlayer, new CapacitorBankPayload(containerId, bank.getStored(), bank.getCapacity(),
                    bank.averageInput(), bank.averageOutput()));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.CAPACITOR_BANK.get());
    }
}
