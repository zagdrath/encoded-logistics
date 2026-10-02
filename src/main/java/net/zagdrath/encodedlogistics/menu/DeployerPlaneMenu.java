/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.part.DeployerPlanePart;
import net.zagdrath.encodedlogistics.part.PartFilter;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// The Deployer Plane's screen (screens/deployer_plane.json): the place / drop mode button (button 0), the 3x3 ghost filter
// of what it deploys, and the player's inventory. data: 0 drop mode.
public class DeployerPlaneMenu extends AbstractContainerMenu {
    public static final int FILTER_X = 62, FILTER_Y = 18, MODE_X = 8, MODE_Y = 18, INVENTORY_Y = 84;
    public static final int BUTTON_MODE = 0;
    private static final int INVENTORY = PartFilter.SIZE;

    private final @Nullable DeployerPlanePart plane;
    private final Container filter;
    private final ContainerData data;

    public static void open(ServerPlayer player, DeployerPlanePart plane) {
        PartMenus.open(player, plane, Component.translatable(plane.type().item().getDescriptionId()),
                (id, inventory, part) -> new DeployerPlaneMenu(id, inventory, (DeployerPlanePart) part));
    }

    public DeployerPlaneMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, (DeployerPlanePart) null);
    }

    private DeployerPlaneMenu(int containerId, Inventory inventory, @Nullable DeployerPlanePart plane) {
        super(ModMenuTypes.DEPLOYER_PLANE.get(), containerId);
        this.plane = plane;
        if (plane != null) {
            filter = new ListContainer(plane.filter().entries(), plane::settingsChanged);
            data = new ContainerData() {
                @Override
                public int get(int index) {
                    return plane.dropMode() ? 1 : 0;
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return 1;
                }
            };
        } else {
            filter = new SimpleContainer(PartFilter.SIZE);
            data = new SimpleContainerData(1);
        }
        for (int i = 0; i < PartFilter.SIZE; i++) {
            addSlot(new GhostSlot(filter, i, FILTER_X + (i % 3) * 18, FILTER_Y + (i / 3) * 18));
        }
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        addDataSlots(data);
    }

    public boolean dropMode() {
        return data.get(0) != 0;
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        if (slotIndex >= 0 && slotIndex < PartFilter.SIZE) {
            if (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) {
                filter.setItem(slotIndex, PartMenus.ghost(this));
            }
            return;
        }
        super.clicked(slotIndex, buttonNum, input, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (plane != null && id == BUTTON_MODE) {
            plane.toggleMode();
            return true;
        }
        return false;
    }

    // Shift-clicking an inventory stack sets it in the first free filter entry.
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index >= INVENTORY && slot.hasItem()) {
            for (int i = 0; i < PartFilter.SIZE; i++) {
                if (filter.getItem(i).isEmpty()) {
                    filter.setItem(i, slot.getItem().copyWithCount(1));
                    break;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return plane == null || plane.stillValid(player);
    }
}
