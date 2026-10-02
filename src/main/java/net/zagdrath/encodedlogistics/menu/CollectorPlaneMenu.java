/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.part.CollectorPlanePart;
import net.zagdrath.encodedlogistics.part.PartFilter;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// The Collector Plane's screen (screens/collector_plane.json): its module slot (#encodedlogistics:plane_modules: a
// Filter or a Fuzzy Match Module), the 3x3 ghost filter that applies while one is in, and the player's inventory. With a
// Filter Module, buttons 0-2 toggle deny / tags / components; with a Fuzzy Match Module, an entry's fuzzy setting comes
// as a value (key: the entry, value: the code). data: 0 flags (PortMenu's FLAG_*), 1-9 the entries' fuzzy codes.
public class CollectorPlaneMenu extends AbstractContainerMenu implements ValueMenu {
    public static final TagKey<Item> PLANE_MODULES = TagKey.create(Registries.ITEM, EncodedLogistics.id("plane_modules"));
    public static final int MODULE_X = 8, MODULE_Y = 18, FILTER_X = 62, FILTER_Y = 18, OPTIONS_X = 134, OPTIONS_Y = 18, OPTIONS_STEP = 20,
            INVENTORY_Y = 84;
    public static final int BUTTON_DENY = 0, BUTTON_TAGS = 1, BUTTON_COMPONENTS = 2;
    private static final int FILTER = 0, MODULE = PartFilter.SIZE, INVENTORY = MODULE + 1, DATA = 1 + PartFilter.SIZE;

    private final @Nullable CollectorPlanePart plane;
    private final Container filter, module;
    private final ContainerData data;

    public static void open(ServerPlayer player, CollectorPlanePart plane) {
        PartMenus.open(player, plane, Component.translatable(plane.type().item().getDescriptionId()),
                (id, inventory, part) -> new CollectorPlaneMenu(id, inventory, (CollectorPlanePart) part));
    }

    public CollectorPlaneMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, (CollectorPlanePart) null);
    }

    private CollectorPlaneMenu(int containerId, Inventory inventory, @Nullable CollectorPlanePart plane) {
        super(ModMenuTypes.COLLECTOR_PLANE.get(), containerId);
        this.plane = plane;
        if (plane != null) {
            filter = new ListContainer(plane.filter().entries(), plane::settingsChanged);
            module = new ListContainer(plane.module(), plane::settingsChanged);
            data = new ContainerData() {
                @Override
                public int get(int index) {
                    PartFilter f = plane.filter();
                    if (index >= 1) {
                        return f.fuzzy(index - 1);
                    }
                    return (plane.hasFilterModule() ? PortMenu.FLAG_FILTER_MODULE : 0) | (f.deny() ? PortMenu.FLAG_DENY : 0)
                            | (f.tags() ? PortMenu.FLAG_TAGS : 0) | (f.components() ? PortMenu.FLAG_COMPONENTS : 0)
                            | (plane.hasFuzzyModule() ? PortMenu.FLAG_FUZZY_MODULE : 0);
                }

                @Override
                public void set(int index, int value) {}

                @Override
                public int getCount() {
                    return DATA;
                }
            };
        } else {
            filter = new SimpleContainer(PartFilter.SIZE);
            module = new SimpleContainer(1);
            data = new SimpleContainerData(DATA);
        }
        for (int i = 0; i < PartFilter.SIZE; i++) {
            addSlot(new GhostSlot(filter, i, FILTER_X + (i % 3) * 18, FILTER_Y + (i / 3) * 18));
        }
        addSlot(new Slot(module, 0, MODULE_X, MODULE_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(PLANE_MODULES);
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }
        });
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        addDataSlots(data);
    }

    public boolean flag(int flag) {
        return (data.get(0) & flag) != 0;
    }

    public int fuzzy(int entry) {
        return data.get(1 + entry);
    }

    @Override
    public void setValue(ServerPlayer player, int key, int value) {
        if (plane != null && plane.hasFuzzyModule() && key >= 0 && key < PartFilter.SIZE) {
            plane.filter().setFuzzy(key, value);
            plane.settingsChanged();
        }
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        if (slotIndex >= FILTER && slotIndex < MODULE) {
            if (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) {
                filter.setItem(slotIndex - FILTER, PartMenus.ghost(this));
                if (plane != null) {
                    plane.filter().clearFuzzy(slotIndex - FILTER);
                }
            }
            return;
        }
        super.clicked(slotIndex, buttonNum, input, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (plane == null || !plane.hasFilterModule()) {
            return false;
        }
        switch (id) {
            case BUTTON_DENY -> plane.filter().toggleDeny();
            case BUTTON_TAGS -> plane.filter().toggleTags();
            case BUTTON_COMPONENTS -> plane.filter().toggleComponents();
            default -> {
                return false;
            }
        }
        plane.settingsChanged();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index < MODULE || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < INVENTORY) {
            if (!moveItemStackTo(stack, INVENTORY, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!stack.is(PLANE_MODULES) || !moveItemStackTo(stack, MODULE, INVENTORY, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return plane == null || plane.stillValid(player);
    }
}
