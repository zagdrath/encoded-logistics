/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.part.ThresholdSensorPart;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// A Threshold Sensor's screen: the ghost item, the threshold (typed in), the comparison and whether it's emitting.
// Button 0: comparison. Value 0: threshold. data: 0-1 threshold, 2 comparison, 3 emitting.
public class ThresholdSensorMenu extends AbstractContainerMenu implements ValueMenu {
    public static final int ITEM_X = 26, ITEM_Y = 33, INVENTORY_Y = 84;
    public static final int BUTTON_MODE = 0, VALUE_THRESHOLD = 0;

    private final @Nullable ThresholdSensorPart sensor;
    private final SimpleContainer item = new SimpleContainer(1);
    private final ContainerData data;

    public static void open(ServerPlayer player, ThresholdSensorPart sensor) {
        PartMenus.open(player, sensor, Component.translatable(sensor.type().item().getDescriptionId()),
                (id, inventory, part) -> new ThresholdSensorMenu(id, inventory, (ThresholdSensorPart) part));
    }

    // Client constructor.
    public ThresholdSensorMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, (ThresholdSensorPart) null);
    }

    private ThresholdSensorMenu(int containerId, Inventory inventory, @Nullable ThresholdSensorPart sensor) {
        super(ModMenuTypes.THRESHOLD_SENSOR.get(), containerId);
        this.sensor = sensor;
        if (sensor != null) {
            item.setItem(0, sensor.item().copy());
            data = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case 0 -> PartMenus.low(sensor.threshold());
                        case 1 -> PartMenus.high(sensor.threshold());
                        case 2 -> sensor.mode();
                        default -> sensor.emitting() ? 1 : 0;
                    };
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return 4;
                }
            };
        } else {
            data = new SimpleContainerData(4);
        }
        addSlot(new GhostSlot(item, 0, ITEM_X, ITEM_Y));
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        addDataSlots(data);
    }

    public int threshold() {
        return PartMenus.join(data.get(0), data.get(1));
    }

    public int mode() {
        return data.get(2);
    }

    public boolean emitting() {
        return data.get(3) != 0;
    }

    private void setItem(ItemStack stack) {
        item.setItem(0, stack);
        if (sensor != null) {
            sensor.setItem(stack);
        }
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        if (slotIndex == 0) {
            if (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) {
                setItem(PartMenus.ghost(this));
            }
            return;
        }
        super.clicked(slotIndex, buttonNum, input, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (sensor == null || id != BUTTON_MODE) {
            return false;
        }
        sensor.cycleMode();
        return true;
    }

    @Override
    public void setValue(ServerPlayer player, int key, int value) {
        if (sensor != null && key == VALUE_THRESHOLD) {
            sensor.setThreshold(value);
        }
    }

    // Shift-clicking an item in the inventory makes it the sensor's item.
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index > 0 && slots.get(index).hasItem()) {
            setItem(slots.get(index).getItem().copyWithCount(1));
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return sensor == null || sensor.stillValid(player);
    }
}
