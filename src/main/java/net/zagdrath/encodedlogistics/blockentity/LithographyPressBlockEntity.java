/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.item.WorldlyContainerWrapper;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.block.LithographyPressBlock;
import net.zagdrath.encodedlogistics.menu.LithographyPressMenu;
import net.zagdrath.encodedlogistics.recipe.LithographyInput;
import net.zagdrath.encodedlogistics.recipe.LithographyRecipe;
import net.zagdrath.encodedlogistics.recipe.LithographyRecipes;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// The Lithography Press: photomask (slot 0, never used up), wafer (1), additive (2), output (3). While a recipe matches
// and the output has room it spends the recipe's energy evenly over its time, pausing (keeping its progress) when the
// buffer runs dry; at the end it uses up one wafer and one additive. Takes FE on every face. Automation: the top feeds
// the photomask slot, the sides the wafer and additive slots (whichever the item is for), the bottom takes the output.
public class LithographyPressBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer {
    public static final int PHOTOMASK = 0, WAFER = 1, ADDITIVE = 2, OUTPUT = 3;
    private static final int[] TOP = { PHOTOMASK }, SIDES = { WAFER, ADDITIVE }, BOTTOM = { OUTPUT };

    private NonNullList<ItemStack> items = NonNullList.withSize(4, ItemStack.EMPTY);
    private final Buffer energy = new Buffer();
    private int progress, time;
    private @Nullable ResourceKey<Recipe<?>> recipe;
    private int comparator = -1;

    // For the screen: 0 progress, 1 time, 2-3 energy (low, high 16 bits), 4-5 capacity (low, high).
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> progress;
                case 1 -> time;
                case 2 -> energy.getAmountAsInt() & 0xFFFF;
                case 3 -> energy.getAmountAsInt() >>> 16;
                case 4 -> energy.capacity() & 0xFFFF;
                case 5 -> energy.capacity() >>> 16;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return 6;
        }
    };

    public LithographyPressBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.LITHOGRAPHY_PRESS.get(), pos, state);
    }

    public EnergyHandler getEnergyHandler(@Nullable Direction side) {
        return energy;
    }

    public ResourceHandler<ItemResource> getItemHandler(@Nullable Direction side) {
        return new WorldlyContainerWrapper(this, side);
    }

    // 0-1 through the current etch.
    public float progress() {
        return time <= 0 ? 0 : (float) progress / time;
    }

    public int comparatorSignal() {
        return time <= 0 ? 0 : progress * 15 / time;
    }

    // --- Working ---

    public static void serverTick(Level level, BlockPos pos, BlockState state, LithographyPressBlockEntity press) {
        press.energy.refreshLimits();
        boolean active = false;
        LithographyInput input = new LithographyInput(press.items.get(WAFER), press.items.get(ADDITIVE), press.items.get(PHOTOMASK));
        RecipeHolder<LithographyRecipe> holder = LithographyRecipes.find(level, input).orElse(null);
        if (holder == null || !press.canOutput(holder.value().result().create())) {
            press.progress = 0;
            press.recipe = null;
            press.time = 0;
        } else {
            LithographyRecipe current = holder.value();
            if (!holder.id().equals(press.recipe)) {
                press.recipe = holder.id();
                press.progress = 0;
            }
            press.time = current.time();
            int cost = current.energyOnTick(press.progress);
            if (press.energy.getAmountAsInt() >= cost) {
                press.energy.set(press.energy.getAmountAsInt() - cost);
                press.progress++;
                active = true;
                if (press.progress >= current.time()) {
                    press.finish(current.assemble(input));
                }
            }
            press.setChanged();
        }
        if (state.getValue(LithographyPressBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(LithographyPressBlock.ACTIVE, active), Block.UPDATE_ALL);
        }
        int signal = press.comparatorSignal();
        if (signal != press.comparator) {
            press.comparator = signal;
            level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    private boolean canOutput(ItemStack result) {
        ItemStack output = items.get(OUTPUT);
        return output.isEmpty() || ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= Math.min(output.getMaxStackSize(), getMaxStackSize());
    }

    private void finish(ItemStack result) {
        items.get(WAFER).shrink(1);
        items.get(ADDITIVE).shrink(1);
        ItemStack output = items.get(OUTPUT);
        if (output.isEmpty()) {
            items.set(OUTPUT, result);
        } else {
            output.grow(result.getCount());
        }
        progress = 0;
    }

    // --- Container ---

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.encodedlogistics.lithography_press");
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
        return 4;
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new LithographyPressMenu(containerId, inventory, this, data);
    }

    // What may go in each slot, by hand or by automation.
    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        if (level == null) {
            return false;
        }
        return switch (slot) {
            case PHOTOMASK -> LithographyRecipes.isPhotomask(level, stack);
            case WAFER -> LithographyRecipes.isWafer(level, stack);
            case ADDITIVE -> LithographyRecipes.isAdditive(level, stack);
            default -> false;
        };
    }

    @Override
    public int[] getSlotsForFace(Direction side) {
        return side == Direction.UP ? TOP : side == Direction.DOWN ? BOTTOM : SIDES;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable Direction side) {
        if (slot == WAFER && side != null && side.getAxis().isHorizontal()) {
            return canPlaceItem(WAFER, stack);
        }
        if (slot == ADDITIVE && side != null && side.getAxis().isHorizontal()) {
            // Wafers go to the wafer slot first.
            return canPlaceItem(ADDITIVE, stack) && !canPlaceItem(WAFER, stack);
        }
        return slot == PHOTOMASK && side == Direction.UP && canPlaceItem(PHOTOMASK, stack);
    }

    // Only the output comes out, from below; the photomask never does.
    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, Direction side) {
        return slot == OUTPUT && side == Direction.DOWN;
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(4, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        energy.deserialize(input);
        progress = input.getIntOr("progress", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        energy.serialize(output);
        output.putInt("progress", progress);
    }

    private final class Buffer extends SimpleEnergyHandler {
        Buffer() {
            super(Config.PRESS_CAPACITY.getDefault(), Config.PRESS_MAX_INPUT.getDefault(), 0);
        }

        // Limits come from the config, which may not be loaded when the block entity is created.
        void refreshLimits() {
            if (Config.SPEC.isLoaded()) {
                capacity = Config.PRESS_CAPACITY.getAsInt();
                maxInsert = Config.PRESS_MAX_INPUT.getAsInt();
            }
        }

        int capacity() {
            return capacity;
        }

        @Override
        protected void onEnergyChanged(int previousAmount) {
            setChanged();
        }
    }
}
