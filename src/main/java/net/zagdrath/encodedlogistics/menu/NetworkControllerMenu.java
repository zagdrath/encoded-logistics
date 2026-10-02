/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.net.NetworkSnapshotPayload;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// The Network screen of a controller structure: no slots. While it's open the server sends the structure's snapshot
// (status, energy, channels, structure, devices) every SYNC_INTERVAL ticks.
public class NetworkControllerMenu extends AbstractContainerMenu {
    private static final int SYNC_INTERVAL = 10;

    private final BlockPos pos;
    private final ContainerLevelAccess access;
    private final Player player;
    private int ticksUntilSync;

    // Client constructor, with the clicked block's position written by the server.
    public NetworkControllerMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos());
    }

    public NetworkControllerMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(ModMenuTypes.NETWORK_CONTROLLER.get(), containerId);
        this.pos = pos;
        this.player = inventory.player;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
    }

    public BlockPos getPos() {
        return pos;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (--ticksUntilSync > 0 || !(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        ticksUntilSync = SYNC_INTERVAL;
        if (level.getBlockEntity(pos) instanceof NetworkControllerBlockEntity controller) {
            PacketDistributor.sendToPlayer(serverPlayer,
                    new NetworkSnapshotPayload(containerId, ControllerStructures.get(level).snapshot(controller.getStructureId())));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.NETWORK_CONTROLLER.get());
    }
}
