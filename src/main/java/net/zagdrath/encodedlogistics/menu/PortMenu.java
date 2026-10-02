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
import net.zagdrath.encodedlogistics.part.PartFilter;
import net.zagdrath.encodedlogistics.part.PortPart;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// An Ingress or Egress Port's screen: the 3x3 ghost filter, four module slots (#encodedlogistics:port_modules; one
// Filter Module, up to three Throughput Modules, one Fuzzy Match Module, one Redstone Control Module) and the player's
// inventory. Buttons: 0 redstone mode (needs a Redstone Control Module), 1-3 the Filter Module's deny / tag / component
// options. With a Fuzzy Match Module, a filter entry's fuzzy setting comes as a value (key: the entry, value: the code).
// data: 0 redstone mode, 1 flags (1 filter module, 2 deny, 4 tags, 8 components, 16 ingress, 32 fuzzy module,
// 64 redstone module), 2-10 the entries' fuzzy codes (PartFilter.fuzzy).
public class PortMenu extends AbstractContainerMenu implements ValueMenu {
    public static final TagKey<Item> PORT_MODULES = TagKey.create(Registries.ITEM, EncodedLogistics.id("port_modules"));
    public static final int FILTER_X = 62, FILTER_Y = 19, MODULE_X = 152, INVENTORY_Y = 94;
    public static final int[] MODULE_Y = { 19, 37, 55, 73 };
    public static final int BUTTON_REDSTONE = 0, BUTTON_DENY = 1, BUTTON_TAGS = 2, BUTTON_COMPONENTS = 3;
    public static final int FLAG_FILTER_MODULE = 1, FLAG_DENY = 2, FLAG_TAGS = 4, FLAG_COMPONENTS = 8, FLAG_INGRESS = 16, FLAG_FUZZY_MODULE = 32,
            FLAG_REDSTONE_MODULE = 64;
    private static final int DATA = 2 + PartFilter.SIZE;
    private static final int FILTER = 0, MODULES = PartFilter.SIZE, INVENTORY = MODULES + PortPart.MODULE_SLOTS;

    private final @Nullable PortPart port;
    private final Container filter, modules;
    private final ContainerData data;

    public static void open(ServerPlayer player, PortPart port) {
        PartMenus.open(player, port, Component.translatable(port.type().item().getDescriptionId()),
                (id, inventory, part) -> new PortMenu(id, inventory, (PortPart) part));
    }

    // Client constructor (the menu data names the part; the client works from the synced slots and data).
    public PortMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, (PortPart) null);
    }

    private PortMenu(int containerId, Inventory inventory, @Nullable PortPart port) {
        super(ModMenuTypes.PORT.get(), containerId);
        this.port = port;
        if (port != null) {
            filter = new ListContainer(port.filter().entries(), port::settingsChanged);
            modules = new ListContainer(port.modules(), port::settingsChanged);
            data = new ContainerData() {
                @Override
                public int get(int index) {
                    if (index == 0) {
                        return port.redstoneMode();
                    }
                    PartFilter f = port.filter();
                    if (index >= 2) {
                        return f.fuzzy(index - 2);
                    }
                    return (port.hasFilterModule() ? FLAG_FILTER_MODULE : 0) | (f.deny() ? FLAG_DENY : 0) | (f.tags() ? FLAG_TAGS : 0)
                            | (f.components() ? FLAG_COMPONENTS : 0) | (port.ingress() ? FLAG_INGRESS : 0)
                            | (port.hasFuzzyModule() ? FLAG_FUZZY_MODULE : 0) | (port.hasRedstoneModule() ? FLAG_REDSTONE_MODULE : 0);
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
            modules = new SimpleContainer(PortPart.MODULE_SLOTS);
            data = new SimpleContainerData(DATA);
        }
        for (int i = 0; i < PartFilter.SIZE; i++) {
            addSlot(new GhostSlot(filter, i, FILTER_X + (i % 3) * 18, FILTER_Y + (i / 3) * 18));
        }
        for (int i = 0; i < PortPart.MODULE_SLOTS; i++) {
            addSlot(new Slot(modules, i, MODULE_X, MODULE_Y[i]) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return accepts(stack, getContainerSlot());
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
        addStandardInventorySlots(inventory, 8, INVENTORY_Y);
        addDataSlots(data);
    }

    // A module slot takes a port module: one Filter Module per port, up to three Throughput Modules.
    private boolean accepts(ItemStack stack, int slot) {
        if (!stack.is(PORT_MODULES)) {
            return false;
        }
        int same = 0;
        for (int i = 0; i < modules.getContainerSize(); i++) {
            if (i != slot && modules.getItem(i).is(stack.getItem())) {
                same++;
            }
        }
        int limit = stack.is(ModItems.FILTER_MODULE.get()) ? 1 : stack.is(ModItems.THROUGHPUT_MODULE.get()) ? 3 : 1;
        return same < limit;
    }

    public int redstoneMode() {
        return data.get(0);
    }

    public boolean flag(int flag) {
        return (data.get(1) & flag) != 0;
    }

    // A filter entry's fuzzy setting (PartFilter.fuzzy).
    public int fuzzy(int entry) {
        return data.get(2 + entry);
    }

    @Override
    public void setValue(ServerPlayer player, int key, int value) {
        if (port != null && port.hasFuzzyModule() && key >= 0 && key < PartFilter.SIZE) {
            port.filter().setFuzzy(key, value);
            port.settingsChanged();
        }
    }

    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        if (slotIndex >= FILTER && slotIndex < MODULES) {
            if (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE) {
                filter.setItem(slotIndex - FILTER, PartMenus.ghost(this));
                if (port != null) {
                    port.filter().clearFuzzy(slotIndex - FILTER);
                }
            }
            return;
        }
        super.clicked(slotIndex, buttonNum, input, player);
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (port == null) {
            return false;
        }
        boolean options = port.hasFilterModule();
        switch (id) {
            case BUTTON_REDSTONE -> {
                // The modes need a Redstone Control Module.
                if (!port.hasRedstoneModule()) {
                    return false;
                }
                port.cycleRedstoneMode();
                return true;
            }
            case BUTTON_DENY -> {
                if (options) {
                    port.filter().toggleDeny();
                }
            }
            case BUTTON_TAGS -> {
                if (options) {
                    port.filter().toggleTags();
                }
            }
            case BUTTON_COMPONENTS -> {
                if (options) {
                    port.filter().toggleComponents();
                }
            }
            default -> {
                return false;
            }
        }
        port.settingsChanged();
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index < MODULES || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < INVENTORY) {
            if (!moveItemStackTo(stack, INVENTORY, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!stack.is(PORT_MODULES) || !moveItemStackTo(stack, MODULES, INVENTORY, false)) {
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
        return port == null || port.stillValid(player);
    }
}
