/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.part.FabricationTerminalPart;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// The Fabrication Terminal: the Access Terminal's grid of network items, then a 3x3 crafting grid (the part's own items)
// and its result, then the player's inventory. Taking the result crafts; every grid slot that runs out is refilled
// from the network with the same item. Shift-clicking the result crafts as many as fit in the inventory. The clear
// button returns the grid to the network (or the player when it's full); JEI fills it from the network, then the player.
public class FabricationTerminalMenu extends AccessTerminalMenu {
    public static final int SECTION = 76, GRID_X = 31, GRID_Y = 9, RESULT_X = 127, RESULT_Y = 26;
    public static final int BUTTON_CLEAR = 0;
    private static final int GRID = INVENTORY_SLOTS, RESULT = GRID + 9;

    private final TransientCraftingContainer craftSlots;
    private final ResultContainer resultSlots = new ResultContainer();
    private boolean loading;

    // Client constructor.
    public FabricationTerminalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), extraData.readEnum(Direction.class), clientRows.applyAsInt(SECTION));
    }

    // Server constructor (open() uses it; gametests too).
    public FabricationTerminalMenu(int containerId, Inventory inventory, BlockPos pos, Direction side, int rows) {
        super(ModMenuTypes.FABRICATION_TERMINAL.get(), containerId, inventory, pos, side, rows, SECTION);
        craftSlots = new TransientCraftingContainer(this, 3, 3);
        int top = TOP + rows * ROW;
        for (int i = 0; i < 9; i++) {
            addSlot(new Slot(craftSlots, i, GRID_X + (i % 3) * 18, top + GRID_Y + (i / 3) * 18));
        }
        addSlot(new RefillingResultSlot(inventory.player, craftSlots, resultSlots, top + RESULT_Y));
        FabricationTerminalPart part = part();
        if (part != null) {
            loading = true;
            for (int i = 0; i < 9; i++) {
                craftSlots.setItem(i, part.grid().get(i).copy());
            }
            loading = false;
            slotsChanged(craftSlots);
        }
    }

    public static void open(ServerPlayer player, BlockPos pos, Direction side) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new FabricationTerminalMenu(id, inventory, pos, side, DEFAULT_ROWS),
                Component.translatable("gui.encodedlogistics.fabrication_terminal")), buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeEnum(side);
                });
    }

    private @Nullable FabricationTerminalPart part() {
        return !player.level().isClientSide() && player.level().getBlockEntity(pos) instanceof CableBlockEntity host
                && host.part(side) instanceof FabricationTerminalPart part ? part : null;
    }

    // --- Crafting ---

    @Override
    public void slotsChanged(Container container) {
        if (container != craftSlots || loading || !(player.level() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        FabricationTerminalPart part = part();
        if (part != null) {
            for (int i = 0; i < 9; i++) {
                part.grid().set(i, craftSlots.getItem(i).copy());
            }
            part.gridChanged();
        }
        CraftingInput input = craftSlots.asCraftInput();
        ItemStack result = ItemStack.EMPTY;
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (recipe.isPresent() && resultSlots.setRecipeUsed(serverPlayer, recipe.get())) {
            ItemStack assembled = recipe.get().value().assemble(input);
            if (assembled.isItemEnabled(level.enabledFeatures())) {
                result = assembled;
            }
        }
        resultSlots.setItem(0, result);
        setRemoteSlot(RESULT, result);
        serverPlayer.connection.send(new ClientboundContainerSetSlotPacket(containerId, incrementStateId(), RESULT, result));
    }

    // Takes the result like a crafting table, then refills the grid slots that ran out from the network.
    private final class RefillingResultSlot extends ResultSlot {
        RefillingResultSlot(Player player, TransientCraftingContainer grid, ResultContainer result, int y) {
            super(player, grid, result, 0, RESULT_X, y);
        }

        @Override
        public void onTake(Player player, ItemStack carried) {
            ItemStack[] before = new ItemStack[9];
            for (int i = 0; i < 9; i++) {
                before[i] = craftSlots.getItem(i).copy();
            }
            super.onTake(player, carried);
            refill(before);
        }
    }

    private void refill(ItemStack[] before) {
        NetworkStorage storage = storage();
        if (storage == null) {
            return;
        }
        for (int i = 0; i < 9; i++) {
            if (craftSlots.getItem(i).isEmpty() && !before[i].isEmpty()) {
                ItemKey key = ItemKey.of(before[i]);
                if (storage.extract(key, 1, false) > 0) {
                    craftSlots.setItem(i, key.toStack(1));
                }
            }
        }
    }

    // Shift-clicking the result crafts as many as fit in the inventory; shift-clicking a grid slot sends it to the
    // network (or the inventory); the rest is the Access Terminal's.
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index == RESULT) {
            Slot slot = slots.get(RESULT);
            ItemStack first = slot.getItem().copy();
            for (int crafts = 0; crafts < 64 && slot.hasItem(); crafts++) {
                ItemStack result = slot.getItem().copy();
                if (!ItemStack.isSameItemSameComponents(result, first) || !fits(result)) {
                    break;
                }
                slot.onTake(player, result);
                moveItemStackTo(result, 0, INVENTORY_SLOTS, true);
                slotsChanged(craftSlots);
            }
            return ItemStack.EMPTY;
        }
        if (index >= GRID && index < RESULT) {
            Slot slot = slots.get(index);
            ItemStack stack = slot.getItem();
            NetworkStorage storage = storage();
            if (storage != null && !stack.isEmpty()) {
                stack.shrink((int) storage.insert(ItemKey.of(stack), stack.getCount(), false));
            }
            if (!stack.isEmpty()) {
                moveItemStackTo(stack, 0, INVENTORY_SLOTS, true);
            }
            slot.setChanged();
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, index);
    }

    // A double-click while carrying the result's item collects matching stacks - but never from the result, as on a
    // crafting table: clicking it again quickly would otherwise count as that double-click and take nothing.
    @Override
    public boolean canTakeItemForPickAll(ItemStack carried, Slot target) {
        return target.container != resultSlots && super.canTakeItemForPickAll(carried, target);
    }

    private boolean fits(ItemStack stack) {
        int room = 0;
        for (int i = 0; i < INVENTORY_SLOTS; i++) {
            ItemStack slot = slots.get(i).getItem();
            if (slot.isEmpty()) {
                room += stack.getMaxStackSize();
            } else if (ItemStack.isSameItemSameComponents(slot, stack)) {
                room += slot.getMaxStackSize() - slot.getCount();
            }
            if (room >= stack.getCount()) {
                return true;
            }
        }
        return false;
    }

    // The clear button.
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_CLEAR) {
            return false;
        }
        clearGrid();
        return true;
    }

    // Every grid item back to the network; what doesn't fit to the player.
    private void clearGrid() {
        NetworkStorage storage = storage();
        for (int i = 0; i < 9; i++) {
            ItemStack stack = craftSlots.getItem(i);
            if (stack.isEmpty()) {
                continue;
            }
            if (storage != null) {
                stack.shrink((int) storage.insert(ItemKey.of(stack), stack.getCount(), false));
            }
            if (!stack.isEmpty() && !player.getInventory().add(stack)) {
                Block.popResource(player.level(), player.blockPosition(), stack);
            }
            craftSlots.setItem(i, ItemStack.EMPTY);
        }
        slotsChanged(craftSlots);
    }

    // JEI: fills the grid for a recipe - each slot from the first of its options the network has, else the player's
    // inventory has. inputs: the nine slots' options, row by row.
    public void fillGrid(List<List<ItemStack>> inputs) {
        clearGrid();
        NetworkStorage storage = storage();
        loading = true;
        for (int i = 0; i < Math.min(9, inputs.size()); i++) {
            for (ItemStack option : inputs.get(i)) {
                if (option.isEmpty()) {
                    continue;
                }
                ItemKey key = ItemKey.of(option);
                if (storage != null && storage.extract(key, 1, false) > 0) {
                    craftSlots.setItem(i, key.toStack(1));
                    break;
                }
                int slot = player.getInventory().findSlotMatchingItem(option);
                if (slot >= 0) {
                    craftSlots.setItem(i, player.getInventory().removeItem(slot, 1));
                    break;
                }
            }
        }
        loading = false;
        slotsChanged(craftSlots);
    }
}
