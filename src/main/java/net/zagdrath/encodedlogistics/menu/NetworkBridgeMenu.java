/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.NetworkBridgeBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// The Network Bridge's screen (screens/network_bridge.json): the link's status, where the partner is (sent when the
// screen opens) and the lanes crossing. No slots. data: status (NetworkBridgeBlockEntity.LinkStatus), lanes used, lanes
// it carries.
public class NetworkBridgeMenu extends AbstractContainerMenu {
    private final @Nullable NetworkBridgeBlockEntity bridge;
    private final @Nullable GlobalPos partner;
    private final ContainerData data;

    // Client constructor: the partner's position comes with the menu.
    public NetworkBridgeMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        super(ModMenuTypes.NETWORK_BRIDGE.get(), containerId);
        this.bridge = null;
        this.partner = ByteBufCodecs.optional(GlobalPos.STREAM_CODEC).decode(extraData).orElse(null);
        this.data = new SimpleContainerData(3);
        addDataSlots(data);
    }

    public NetworkBridgeMenu(int containerId, Inventory inventory, NetworkBridgeBlockEntity bridge) {
        super(ModMenuTypes.NETWORK_BRIDGE.get(), containerId);
        this.bridge = bridge;
        this.partner = bridge.partner();
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                if (!(bridge.getLevel() instanceof ServerLevel level)) {
                    return 0;
                }
                return switch (index) {
                    case 0 -> bridge.linkStatus(level.getServer()).ordinal();
                    case 1 -> bridge.lanesUsed(level.getServer());
                    default -> Config.BRIDGE_LANES.getAsInt();
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return 3;
            }
        };
        addDataSlots(data);
    }

    public Optional<GlobalPos> partner() {
        return Optional.ofNullable(partner);
    }

    public NetworkBridgeBlockEntity.LinkStatus status() {
        NetworkBridgeBlockEntity.LinkStatus[] values = NetworkBridgeBlockEntity.LinkStatus.values();
        return values[Math.clamp(data.get(0), 0, values.length - 1)];
    }

    public int lanesUsed() {
        return data.get(1);
    }

    public int lanes() {
        return Math.max(1, data.get(2));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return bridge == null || !bridge.isRemoved() && player.isWithinBlockInteractionRange(bridge.getBlockPos(), 4.0);
    }
}
