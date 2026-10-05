/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.menu.KeypunchMenu;
import net.zagdrath.encodedlogistics.menu.PeripheralMenu;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.registry.ModSounds;

// The Keypunch (HANDOFF 3, 5): a crafting recipe typed into its 3 x 3 grid by item name (nothing is used up) is punched
// onto a blank Punch Card - one card per Punch (F6), into its stacker (cards of the same recipe stack there). Blank
// cards go in its hopper when used on it; a sneak-use with an empty hand takes the punched cards (then the blank ones).
// It works while a Midrange System on its network is online.
public class KeypunchBlockEntity extends PeripheralBlockEntity {
    public static final String TYPE = "KEYPUNCH";
    public static final int BLANK = 0, PUNCHED = 1;
    private static final int ACTIVE_TICKS = 16;

    // Its recipe (a change saves it and works out the output again) and what it crafts.
    private final SimpleContainer grid = new SimpleContainer(9) {
        @Override
        public void setChanged() {
            super.setChanged();
            KeypunchBlockEntity.this.setChanged();
            updateResult();
        }
    };
    private final SimpleContainer result = new SimpleContainer(1);

    public KeypunchBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.KEYPUNCH.get(), pos, state, 2);
    }

    @Override
    public String deviceType() {
        return TYPE;
    }

    public SimpleContainer grid() {
        return grid;
    }

    public SimpleContainer result() {
        return result;
    }

    public static boolean blank(ItemStack stack) {
        return stack.is(ModItems.PUNCH_CARD.get()) && !stack.has(ModDataComponents.PUNCHED_RECIPE.get());
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        return slot == BLANK && blank(stack);
    }

    // What the grid crafts (empty for nothing).
    public void updateResult() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
        boolean any = false;
        for (int i = 0; i < 9; i++) {
            ItemStack stack = grid.getItem(i);
            items.set(i, stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1));
            any |= !stack.isEmpty();
        }
        ItemStack output = ItemStack.EMPTY;
        if (any) {
            CraftingInput input = CraftingInput.of(3, 3, items);
            Optional<RecipeHolder<CraftingRecipe>> recipe = serverLevel.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, serverLevel);
            if (recipe.isPresent()) {
                output = recipe.get().value().assemble(input);
            }
        }
        result.setItem(0, output);
    }

    // The recipe on the grid, or null when it crafts nothing.
    public @Nullable Schematic schematic() {
        ItemStack output = result.getItem(0);
        if (output.isEmpty()) {
            return null;
        }
        List<ItemStack> inputs = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            inputs.add(grid.getItem(i).copy());
        }
        return Schematic.of(Schematic.Kind.CRAFTING, inputs, List.of(output.copy()));
    }

    // Punches one card; the message line says how it went.
    public Component punch() {
        if (!isOnline()) {
            return Component.translatable("crt.encodedlogistics.machine.no_host");
        }
        Schematic schematic = schematic();
        if (schematic == null) {
            return Component.translatable("crt.encodedlogistics.keypunch.no_recipe");
        }
        ItemStack blank = getItem(BLANK);
        if (!blank(blank)) {
            return Component.translatable("crt.encodedlogistics.keypunch.no_blank");
        }
        ItemStack card = new ItemStack(ModItems.PUNCH_CARD.get());
        card.set(ModDataComponents.PUNCHED_RECIPE.get(), schematic);
        ItemStack punched = getItem(PUNCHED);
        if (punched.isEmpty()) {
            setItem(PUNCHED, card);
        } else if (ItemStack.isSameItemSameComponents(punched, card) && punched.getCount() < punched.getMaxStackSize()) {
            punched.grow(1);
        } else {
            return Component.translatable("crt.encodedlogistics.keypunch.full");
        }
        blank.shrink(1);
        setChanged();
        activate(ACTIVE_TICKS, ModSounds.KEYPUNCH_CLATTER);
        ItemStack output = schematic.output();
        return Component.translatable("crt.encodedlogistics.keypunch.punched", output.getHoverName(), output.getCount());
    }

    public void clearGrid() {
        grid.clearContent();
    }

    // An item from its name as typed: "logic_die", "minecraft:iron_ingot" (this mod's, then Minecraft's, then any mod's
    // with that path); null when there's none.
    public static @Nullable Item item(String typed) {
        String name = typed.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (name.isEmpty()) {
            return null;
        }
        Identifier id = Identifier.tryParse(name);
        if (name.contains(":")) {
            return id != null ? BuiltInRegistries.ITEM.getOptional(id).filter(item -> item != Items.AIR).orElse(null) : null;
        }
        for (String namespace : List.of(EncodedLogistics.MODID, Identifier.DEFAULT_NAMESPACE)) {
            Identifier in = Identifier.tryBuild(namespace, name);
            Item found = in != null ? BuiltInRegistries.ITEM.getOptional(in).orElse(null) : null;
            if (found != null && found != Items.AIR) {
                return found;
            }
        }
        for (Identifier key : BuiltInRegistries.ITEM.keySet()) {
            if (key.getPath().equals(name)) {
                return BuiltInRegistries.ITEM.getValue(key);
            }
        }
        return null;
    }

    // An item's name as the grid shows it: its path for this mod's and Minecraft's, else namespace:path.
    public static String shortName(ItemStack stack) {
        if (stack.isEmpty()) {
            return "";
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return id.getNamespace().equals(EncodedLogistics.MODID) || id.getNamespace().equals(Identifier.DEFAULT_NAMESPACE) ? id.getPath() : id.toString();
    }

    // A grid cell typed on the screen: the item, or cleared when blank; the message when there's no such item.
    public @Nullable Component setCell(int cell, String typed) {
        if (cell < 0 || cell >= 9) {
            return null;
        }
        if (typed.isBlank()) {
            grid.setItem(cell, ItemStack.EMPTY);
            return null;
        }
        Item item = item(typed);
        if (item == null) {
            return Component.translatable("crt.encodedlogistics.keypunch.no_item", typed.trim());
        }
        grid.setItem(cell, new ItemStack(item));
        return null;
    }

    // Blank cards used on it go in its hopper (as many as fit).
    @Override
    public boolean insert(ItemStack stack) {
        if (!blank(stack)) {
            return false;
        }
        ItemStack hopper = getItem(BLANK);
        int room = hopper.isEmpty() ? stack.getMaxStackSize() : ItemStack.isSameItemSameComponents(hopper, stack) ? hopper.getMaxStackSize() - hopper.getCount() : 0;
        int moved = Math.min(room, stack.getCount());
        if (moved <= 0) {
            return false;
        }
        if (hopper.isEmpty()) {
            setItem(BLANK, stack.split(moved));
        } else {
            hopper.grow(moved);
            stack.shrink(moved);
        }
        setChanged();
        return true;
    }

    // A sneak-use with an empty hand: the punched cards, else the blank ones.
    @Override
    public List<ItemStack> eject() {
        for (int slot : new int[] { PUNCHED, BLANK }) {
            if (!getItem(slot).isEmpty()) {
                ItemStack out = removeItemNoUpdate(slot);
                setChanged();
                return List.of(out);
            }
        }
        return List.of();
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        updateResult();
        return new KeypunchMenu(containerId, inventory, this, this, PeripheralMenu.Opening.SERVER);
    }

    // --- Saving: the grid too ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        List<ItemStack> saved = input.read("grid", ItemStack.OPTIONAL_CODEC.listOf()).orElse(List.of());
        for (int i = 0; i < 9; i++) {
            grid.setItem(i, i < saved.size() ? saved.get(i) : ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.store("grid", ItemStack.OPTIONAL_CODEC.listOf(), grid.getItems());
    }
}
