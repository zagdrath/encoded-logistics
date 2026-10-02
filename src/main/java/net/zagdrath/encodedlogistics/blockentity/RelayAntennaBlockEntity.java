/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.block.RelayAntennaBlock;
import net.zagdrath.encodedlogistics.item.HandheldTerminalItem;
import net.zagdrath.encodedlogistics.menu.RelayAntennaMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModItems;

// A Relay Antenna's four Optical Transceiver slots and its coverage: a sphere of relayBaseRange blocks (plus
// relayRangePerTransceiver per transceiver) around its centre, while it's online. A Handheld Terminal of its network is
// in range when any of the network's antennas covers it.
public class RelayAntennaBlockEntity extends BaseContainerBlockEntity implements NetworkDevice {
    public static final int SLOTS = 4;

    // A player near the antenna carrying a Handheld Terminal linked to its network, and how far away they are.
    public record Linked(String name, int distance) {}

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private boolean online;

    // For the screen: the range.
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return range();
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return 1;
        }
    };

    public RelayAntennaBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.RELAY_ANTENNA.get(), pos, state);
    }

    @Override
    public void setNetworkOnline(boolean online) {
        this.online = online;
        BlockState state = getBlockState();
        if (level != null && state.getBlock() instanceof RelayAntennaBlock && state.getValue(RelayAntennaBlock.ONLINE) != online) {
            level.setBlock(worldPosition, state.setValue(RelayAntennaBlock.ONLINE, online), Block.UPDATE_CLIENTS);
        }
    }

    public boolean isOnline() {
        return online;
    }

    public int transceivers() {
        int count = 0;
        for (ItemStack stack : items) {
            if (stack.is(ModItems.OPTICAL_TRANSCEIVER.get())) {
                count++;
            }
        }
        return count;
    }

    // Blocks it covers, from its centre.
    public int range() {
        return Config.RELAY_BASE_RANGE.getAsInt() + Config.RELAY_RANGE_PER_TRANSCEIVER.getAsInt() * transceivers();
    }

    public double distanceTo(Vec3 point) {
        return Math.sqrt(Vec3.atCenterOf(worldPosition).distanceToSqr(point));
    }

    public boolean covers(Vec3 point) {
        return distanceTo(point) <= range();
    }

    // The players in range carrying a Handheld Terminal linked to this antenna's network, nearest first.
    public List<Linked> linkedTerminals(ServerLevel level) {
        NetworkIndex.NetworkRef network = ControllerStructures.networkOf(level, worldPosition);
        List<Linked> linked = new ArrayList<>();
        if (network == null) {
            return linked;
        }
        for (ServerPlayer player : level.players()) {
            if (!covers(player.position())) {
                continue;
            }
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
                if (network.equals(HandheldTerminalItem.network(inventory.getItem(slot)))) {
                    linked.add(new Linked(player.getName().getString(), (int) Math.round(distanceTo(player.position()))));
                    break;
                }
            }
        }
        linked.sort(Comparator.comparingInt(Linked::distance).thenComparing(Linked::name));
        return linked;
    }

    // --- Container ---

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.encodedlogistics.relay_antenna");
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public int getContainerSize() {
        return SLOTS;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return stack.is(ModItems.OPTICAL_TRANSCEIVER.get());
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new RelayAntennaMenu(containerId, inventory, this, data, worldPosition);
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
    }
}
