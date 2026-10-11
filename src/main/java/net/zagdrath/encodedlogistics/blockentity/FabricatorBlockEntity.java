/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.blockentity;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.block.FabricatorBlock;
import net.zagdrath.encodedlogistics.crafting.CraftQueue;
import net.zagdrath.encodedlogistics.crafting.CraftTask;
import net.zagdrath.encodedlogistics.crafting.CraftingProvider;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.item.SchematicItem;
import net.zagdrath.encodedlogistics.menu.FabricatorMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkDevice;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModItems;

// A Fabricator's nine Crafting Schematic slots and two Throughput Module slots, and the crafts it's working on. A
// Scheduler offers it crafts (with the inputs) while it's online, as many at once as the jobs' threads allow (CraftQueue);
// it pays fabricatorEnergyPerCraft FE each, works each for fabricatorCraftTicks[modules] ticks (pausing while offline),
// then crafts the recipe the schematic's grid matches and sends the result and any remainders (buckets) back to the job.
// Broken mid-craft, it gives the inputs back to the jobs.
public class FabricatorBlockEntity extends BaseContainerBlockEntity implements NetworkDevice, CraftingProvider {
    public static final int SCHEMATIC_SLOTS = 9, MODULE_SLOTS = 2, SLOTS = SCHEMATIC_SLOTS + MODULE_SLOTS;
    // Stays lit this long after a craft, so back-to-back crafts don't flicker.
    private static final int LINGER = 10;

    private NonNullList<ItemStack> items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
    private final CraftQueue crafts = new CraftQueue();
    private int idle;
    private double energyCredit;
    private boolean online;

    // For the screen: the progress and duration of the craft nearest done.
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == 0 ? crafts.leadProgress() : crafts.leadDuration();
        }

        @Override
        public void set(int index, int value) {}

        @Override
        public int getCount() {
            return 2;
        }
    };

    public FabricatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.FABRICATOR.get(), pos, state);
    }

    @Override
    public void setNetworkOnline(boolean online) {
        this.online = online;
    }

    public boolean isWorking() {
        return !crafts.isEmpty();
    }

    // How many crafts it's working on at once.
    public int craftsRunning() {
        return crafts.size();
    }

    public int throughputModules() {
        int count = 0;
        for (int slot = SCHEMATIC_SLOTS; slot < SLOTS; slot++) {
            if (items.get(slot).is(ModItems.THROUGHPUT_MODULE.get())) {
                count++;
            }
        }
        return count;
    }

    private int craftTicks() {
        List<? extends Integer> ticks = Config.FABRICATOR_CRAFT_TICKS.get();
        return ticks.isEmpty() ? 20 : Math.max(1, ticks.get(Math.min(throughputModules(), ticks.size() - 1)));
    }

    // --- Crafting ---

    @Override
    public List<Schematic> schematics() {
        List<Schematic> schematics = new ArrayList<>();
        for (int slot = 0; slot < SCHEMATIC_SLOTS; slot++) {
            Schematic schematic = SchematicItem.schematic(items.get(slot));
            if (schematic != null && schematic.kind() == Schematic.Kind.CRAFTING) {
                schematics.add(schematic);
            }
        }
        return schematics;
    }

    @Override
    public boolean offer(ServerLevel level, CraftTask offered) {
        if (!online || offered.schematic().kind() != Schematic.Kind.CRAFTING || !accepts(offered.schematic())) {
            return false;
        }
        int cost = Config.FABRICATOR_ENERGY_PER_CRAFT.getAsInt();
        if (energyCredit < cost) {
            energyCredit += ControllerStructures.get(level).drawEnergy(level, worldPosition, (int) Math.ceil(cost - energyCredit));
            if (energyCredit < cost) {
                return false;
            }
        }
        energyCredit -= cost;
        crafts.add(offered, craftTicks());
        setActive(true);
        setChanged();
        return true;
    }

    public void serverTick(ServerLevel level) {
        if (crafts.isEmpty()) {
            if (idle > 0 && --idle == 0) {
                setActive(false);
            }
            return;
        }
        if (!online) {
            return;
        }
        List<CraftTask> done = crafts.tick();
        for (CraftTask task : done) {
            SchedulerCoreBlockEntity.deliver(level, task, craft(level, task), true, worldPosition);
        }
        if (!done.isEmpty()) {
            idle = LINGER;
            setChanged();
        }
    }

    // The recipe's result and remainders, or the inputs back when the grid no longer matches a recipe.
    public static List<ItemStack> craft(ServerLevel level, CraftTask task) {
        CraftingInput input = task.schematic().craftingInput();
        Optional<RecipeHolder<CraftingRecipe>> recipe = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
        if (recipe.isEmpty()) {
            return task.inputs();
        }
        List<ItemStack> made = new ArrayList<>();
        made.add(recipe.get().value().assemble(input));
        for (ItemStack remainder : recipe.get().value().getRemainingItems(input)) {
            if (!remainder.isEmpty()) {
                made.add(remainder);
            }
        }
        return made;
    }

    private void setActive(boolean active) {
        if (level != null && getBlockState().getValue(FabricatorBlock.ACTIVE) != active) {
            level.setBlock(worldPosition, getBlockState().setValue(FabricatorBlock.ACTIVE, active), Block.UPDATE_ALL);
        }
    }

    // --- Container ---

    @Override
    protected Component getDefaultName() {
        return Component.translatable("block.encodedlogistics.fabricator");
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
        return accepts(slot, stack);
    }

    // Schematic slots take Crafting Schematics, module slots Throughput Modules.
    public static boolean accepts(int slot, ItemStack stack) {
        return slot < SCHEMATIC_SLOTS ? stack.is(ModItems.ENCODED_SCHEMATIC_CRAFTING.get()) : stack.is(ModItems.THROUGHPUT_MODULE.get());
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return new FabricatorMenu(containerId, inventory, this, data);
    }

    // Breaking it drops its schematics and modules, and gives the crafts in progress back to their jobs.
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (level instanceof ServerLevel serverLevel) {
            for (CraftTask task : crafts.clear()) {
                SchedulerCoreBlockEntity.refund(serverLevel, task, task.inputs(), pos);
            }
        }
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        items = NonNullList.withSize(SLOTS, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(input, items);
        crafts.load(input);
        energyCredit = input.getDoubleOr("energy", 0);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, items);
        crafts.save(output);
        output.putDouble("energy", energyCredit);
    }
}
