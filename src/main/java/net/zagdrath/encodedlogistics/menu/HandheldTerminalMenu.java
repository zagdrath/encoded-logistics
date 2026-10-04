/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.RelayAntennaBlockEntity;
import net.zagdrath.encodedlogistics.item.HandheldLinkState;
import net.zagdrath.encodedlogistics.item.HandheldTerminalItem;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex;
import net.zagdrath.encodedlogistics.rack.device.WirelessControllerDevice;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// A Handheld Terminal's screen: the Access Terminal's, reaching the network through the nearest of its Relay Antennas
// covering the player (picked again every CHECK_INTERVAL ticks as they move), else through the Wireless Controller it's
// linked to (full signal, anywhere). It's offline while out of range or with an empty battery. The battery pays handheldDrainPerSecond each second and handheldEnergyPerItem per item moved.
// data: 0 link state (HandheldLinkState), 1 signal bars, 2-3 energy and 4-5 capacity in 16-bit halves.
public class HandheldTerminalMenu extends AccessTerminalMenu {
    // The off hand's slot, as the menu names the terminal's place in the inventory.
    public static final int OFFHAND_SLOT = 40;
    private static final int CHECK_INTERVAL = 10, SECOND = 20, DATA = 6;

    private final int slot;
    private final NetworkIndex.@Nullable NetworkRef network;
    private final ContainerData data;
    private HandheldLinkState state = HandheldLinkState.LINKED;
    private int signal, checkTimer, secondTimer;
    // The Wireless Controller it goes through (its id), or null while it uses an antenna.
    private @Nullable UUID wireless;

    public static void open(ServerPlayer player, int slot, BlockPos relay) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new HandheldTerminalMenu(id, inventory, slot, relay),
                Component.translatable("gui.encodedlogistics.handheld_terminal")), buf -> {
                    buf.writeVarInt(slot);
                    buf.writeBlockPos(relay);
                });
    }

    // Client constructor, with the terminal's inventory slot and the antenna it opened through.
    public HandheldTerminalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        int slot = extraData.readVarInt();
        BlockPos relay = extraData.readBlockPos();
        super(ModMenuTypes.HANDHELD_TERMINAL.get(), containerId, inventory, relay, Direction.NORTH, clientRows.applyAsInt(0), 0);
        this.slot = slot;
        this.network = null;
        this.data = new SimpleContainerData(DATA);
        addDataSlots(data);
    }

    private HandheldTerminalMenu(int containerId, Inventory inventory, int slot, BlockPos relay) {
        super(ModMenuTypes.HANDHELD_TERMINAL.get(), containerId, inventory, relay, Direction.NORTH, DEFAULT_ROWS, 0);
        this.slot = slot;
        this.network = HandheldTerminalItem.network(stack());
        this.data = new ContainerData() {
            @Override
            public int get(int index) {
                int energy = HandheldTerminalItem.energy(stack()), capacity = HandheldTerminalItem.capacity();
                return switch (index) {
                    case 0 -> state.ordinal();
                    case 1 -> signal;
                    case 2 -> PartMenus.low(energy);
                    case 3 -> PartMenus.high(energy);
                    case 4 -> PartMenus.low(capacity);
                    default -> PartMenus.high(capacity);
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return DATA;
            }
        };
        addDataSlots(data);
        refresh();
    }

    private ItemStack stack() {
        return player.getInventory().getItem(slot);
    }

    // --- Server ---

    // Picks the antenna to go through, and the link state and signal it gives.
    private void refresh() {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        RelayAntennaBlockEntity relay = HandheldTerminalItem.access(level, player.position(), network);
        WirelessControllerDevice controller = relay == null ? HandheldTerminalItem.wireless(level.getServer(), player, stack()) : null;
        wireless = controller != null ? controller.id() : null;
        if (relay != null) {
            pos = relay.getBlockPos();
        } else if (controller != null && controller.rack() != null) {
            pos = controller.rack().getBlockPos();
        }
        boolean reached = relay != null || controller != null;
        state = network == null ? HandheldLinkState.UNLINKED : reached ? HandheldLinkState.LINKED : HandheldLinkState.OUT_OF_RANGE;
        signal = controller != null ? 4 : HandheldTerminalItem.signal(relay, player.position());
        ItemStack stack = stack();
        if (stack.getItem() instanceof HandheldTerminalItem && stack.get(ModDataComponents.HANDHELD_LINK_STATE.get()) != state) {
            stack.set(ModDataComponents.HANDHELD_LINK_STATE.get(), state);
        }
    }

    // Its network: the one it's linked to, reached while it's in range and charged.
    @Override
    public NetworkIndex.@Nullable NetworkRef homeNetwork() {
        return network;
    }

    @Override
    public NetworkIndex.@Nullable NetworkRef network() {
        return state == HandheldLinkState.LINKED && HandheldTerminalItem.energy(stack()) > 0 ? network : null;
    }

    public @Nullable UUID wireless() {
        return wireless;
    }

    @Override
    public void broadcastChanges() {
        if (player instanceof ServerPlayer) {
            if (++checkTimer >= CHECK_INTERVAL) {
                checkTimer = 0;
                refresh();
            }
            if (++secondTimer >= SECOND) {
                secondTimer = 0;
                pay(Config.HANDHELD_DRAIN_PER_SECOND.getAsInt());
            }
        }
        super.broadcastChanges();
    }

    @Override
    protected void moved(int items) {
        pay(items * Config.HANDHELD_ENERGY_PER_ITEM.getAsInt());
    }

    private void pay(int energy) {
        ItemStack stack = stack();
        if (energy > 0 && stack.getItem() instanceof HandheldTerminalItem) {
            HandheldTerminalItem.setEnergy(stack, HandheldTerminalItem.energy(stack) - energy);
        }
    }

    // Open while the terminal is still where it was, linked to the same network.
    @Override
    public boolean stillValid(Player player) {
        ItemStack stack = stack();
        return stack.getItem() instanceof HandheldTerminalItem && (network == null || network.equals(HandheldTerminalItem.network(stack)));
    }

    // --- Client ---

    public HandheldLinkState linkState() {
        return HandheldLinkState.byId(data.get(0));
    }

    public int signal() {
        return data.get(1);
    }

    public int energy() {
        return PartMenus.join(data.get(2), data.get(3));
    }

    public int capacity() {
        return Math.max(1, PartMenus.join(data.get(4), data.get(5)));
    }
}
