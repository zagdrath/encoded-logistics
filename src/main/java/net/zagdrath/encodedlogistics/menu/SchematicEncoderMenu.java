/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.item.ResourceEntryItem;
import net.zagdrath.encodedlogistics.item.SchematicItem;
import net.zagdrath.encodedlogistics.part.SchematicEncoderPart;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.registry.ModMenuTypes;

// The Schematic Encoder: the Access Terminal's grid of network items, then the encoding section, then the player's
// inventory. The section's slots: the 3x3 ghost grid, three ghost processing outputs (processing mode only), the crafting
// result (crafting mode only, read-only: whatever recipe the grid matches), the blank-card slot and the encoded-card
// slot. In processing mode ghost items have amounts (1-64; the screen changes them with MenuValuePayload). Encode writes
// the grid onto a blank card into the encoded slot (or rewrites a card already there); a card put into the encoded slot
// loads its schematic. Buttons: 0 mode, 1 clear, 2 encode.
public class SchematicEncoderMenu extends AccessTerminalMenu implements ValueMenu {
    public static final int SECTION = 76, GRID_X = 31, GRID_Y = 9, OUTPUT_X = 117, RESULT_X = 119, RESULT_Y = 26, CARD_X = 161, BLANK_Y = 9,
            ENCODED_Y = 47, MAX_AMOUNT = 64;
    // A fluid or gas entry's amount (mB, or its mod's unit) in a Processing Schematic: up to this, a bucket to start with.
    public static final int MAX_RESOURCE_AMOUNT = 1_000_000, DEFAULT_RESOURCE_AMOUNT = 1_000;
    public static final int[] OUTPUT_Y = { 9, 27, 45 };
    public static final int BUTTON_MODE = 0, BUTTON_CLEAR = 1, BUTTON_ENCODE = 2;
    public static final int GRID = INVENTORY_SLOTS, OUTPUTS = GRID + 9, RESULT = OUTPUTS + 3, BLANK = RESULT + 1, ENCODED = BLANK + 1;
    private static final Identifier GHOST_CARD = EncodedLogistics.id("encoder/ghost_card");

    private final Container grid, outputs, cards;
    private final SimpleContainer result = new SimpleContainer(1);
    private final DataSlot mode;
    private final @Nullable SchematicEncoderPart part;

    // Client constructor.
    public SchematicEncoderMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf extraData) {
        this(containerId, inventory, extraData.readBlockPos(), extraData.readEnum(Direction.class), clientRows.applyAsInt(SECTION));
    }

    // Server constructor.
    public SchematicEncoderMenu(int containerId, Inventory inventory, BlockPos pos, Direction side) {
        this(containerId, inventory, pos, side, DEFAULT_ROWS);
    }

    private SchematicEncoderMenu(int containerId, Inventory inventory, BlockPos pos, Direction side, int rows) {
        super(ModMenuTypes.SCHEMATIC_ENCODER.get(), containerId, inventory, pos, side, rows, SECTION);
        part = !inventory.player.level().isClientSide() && inventory.player.level().getBlockEntity(pos) instanceof CableBlockEntity host
                && host.part(side) instanceof SchematicEncoderPart encoder ? encoder : null;
        if (part != null) {
            grid = new ListContainer(part.grid(), this::gridChanged);
            outputs = new ListContainer(part.outputs(), part::contentsChanged);
            cards = new ListContainer(part.cards(), part::contentsChanged);
            mode = new DataSlot() {
                @Override
                public int get() {
                    return part.mode();
                }

                @Override
                public void set(int value) {}
            };
        } else {
            grid = new SimpleContainer(9);
            outputs = new SimpleContainer(3);
            cards = new SimpleContainer(2);
            mode = DataSlot.standalone();
        }
        int top = TOP + rows * ROW;
        for (int i = 0; i < 9; i++) {
            addSlot(new AmountSlot(grid, i, GRID_X + (i % 3) * 18, top + GRID_Y + (i / 3) * 18, false));
        }
        for (int i = 0; i < 3; i++) {
            addSlot(new AmountSlot(outputs, i, OUTPUT_X, top + OUTPUT_Y[i], true));
        }
        addSlot(new GhostSlot(result, 0, RESULT_X, top + RESULT_Y) {
            @Override
            public boolean isActive() {
                return !processing();
            }
        });
        addSlot(new Slot(cards, SchematicEncoderPart.BLANK, CARD_X, top + BLANK_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModItems.SCHEMATIC_CARD.get());
            }

            @Override
            public Identifier getNoItemIcon() {
                return GHOST_CARD;
            }
        });
        addSlot(new Slot(cards, SchematicEncoderPart.ENCODED, CARD_X, top + ENCODED_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof SchematicItem;
            }

            @Override
            public int getMaxStackSize() {
                return 1;
            }

            @Override
            public void setByPlayer(ItemStack stack, ItemStack previous) {
                super.setByPlayer(stack, previous);
                load(SchematicItem.schematic(stack));
            }
        });
        addDataSlot(mode);
        updateResult();
    }

    public static void open(ServerPlayer player, BlockPos pos, Direction side) {
        player.openMenu(new SimpleMenuProvider((id, inventory, p) -> new SchematicEncoderMenu(id, inventory, pos, side),
                Component.translatable("gui.encodedlogistics.schematic_encoder")), buf -> {
                    buf.writeBlockPos(pos);
                    buf.writeEnum(side);
                });
    }

    public boolean processing() {
        return mode.get() == SchematicEncoderPart.PROCESSING;
    }

    // A ghost grid or output slot; outputs only show in processing mode.
    private final class AmountSlot extends GhostSlot {
        private final boolean output;

        AmountSlot(Container container, int slot, int x, int y, boolean output) {
            super(container, slot, x, y);
            this.output = output;
        }

        @Override
        public boolean isActive() {
            return !output || processing();
        }

        @Override
        public int getMaxStackSize() {
            return MAX_AMOUNT;
        }
    }

    // --- Ghost slots ---

    // Grid and output slots: an item in hand sets it (left: the stack's count in processing mode, else one), an empty
    // hand clears it (left click; the screen turns a right click on a set slot into an amount change). In processing
    // mode a filled bucket, tank or gas container (or a picked or JEI-dragged fluid or gas) sets a fluid or gas entry of a
    // bucket's worth instead; crafting takes items only.
    @Override
    public void clicked(int slotIndex, int buttonNum, ContainerInput input, Player player) {
        if (slotIndex >= GRID && slotIndex < BLANK) {
            if (slotIndex < RESULT && (input == ContainerInput.PICKUP || input == ContainerInput.QUICK_MOVE)) {
                Slot slot = slots.get(slotIndex);
                if (slot.isActive()) {
                    ItemStack carried = getCarried();
                    if (!carried.isEmpty() || buttonNum == 0) {
                        slot.container.setItem(slot.getContainerSlot(), ghost(carried, buttonNum == 1));
                    }
                }
            }
            return;
        }
        super.clicked(slotIndex, buttonNum, input, player);
    }

    private ItemStack ghost(ItemStack carried, boolean one) {
        if (processing()) {
            ItemStack entry = PartMenus.ghost(carried, null);
            ResourceEntryItem.Entry resource = ResourceEntryItem.entry(entry);
            if (resource != null) {
                ResourceEntryItem.Entry given = ResourceEntryItem.entry(carried);
                long amount = given != null && given.amount() > 0 ? given.amount() : DEFAULT_RESOURCE_AMOUNT;
                return ResourceEntryItem.of(resource.key(), Math.min(amount, MAX_RESOURCE_AMOUNT));
            }
        } else if (ResourceEntryItem.entry(carried) != null) {
            return ItemStack.EMPTY;
        }
        return GatewayMenu.ghost(carried, one || !processing());
    }

    // An amount changed on the screen: key is the slot (0-8 grid, 9-11 outputs).
    @Override
    public void setValue(ServerPlayer player, int key, int value) {
        if (!processing() || key < 0 || key >= 12) {
            return;
        }
        Container container = key < 9 ? grid : outputs;
        int index = key < 9 ? key : key - 9;
        ItemStack stack = container.getItem(index);
        if (ResourceEntryItem.entry(stack) != null) {
            container.setItem(index, ResourceEntryItem.withAmount(stack, Math.clamp(value, 1, MAX_RESOURCE_AMOUNT)));
        } else if (!stack.isEmpty()) {
            container.setItem(index, stack.copyWithCount(Math.clamp(value, 1, MAX_AMOUNT)));
        }
    }

    private void gridChanged() {
        if (part != null) {
            part.contentsChanged();
        }
        updateResult();
    }

    // Crafting mode: the result of the recipe the grid matches.
    private void updateResult() {
        if (!(player.level() instanceof ServerLevel level) || part == null) {
            return;
        }
        ItemStack output = ItemStack.EMPTY;
        if (!processing()) {
            NonNullList<ItemStack> items = NonNullList.withSize(9, ItemStack.EMPTY);
            for (int i = 0; i < 9; i++) {
                items.set(i, grid.getItem(i).isEmpty() ? ItemStack.EMPTY : grid.getItem(i).copyWithCount(1));
            }
            CraftingInput input = CraftingInput.of(3, 3, items);
            Optional<RecipeHolder<CraftingRecipe>> recipe = level.getServer().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, level);
            if (recipe.isPresent()) {
                output = recipe.get().value().assemble(input);
            }
        }
        result.setItem(0, output);
    }

    // --- Buttons ---

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (part == null) {
            return false;
        }
        switch (id) {
            case BUTTON_MODE -> {
                part.setMode(processing() ? SchematicEncoderPart.CRAFTING : SchematicEncoderPart.PROCESSING);
                if (!processing()) {
                    // Crafting takes one of each, and items only.
                    for (int i = 0; i < 9; i++) {
                        if (ResourceEntryItem.entry(grid.getItem(i)) != null) {
                            grid.setItem(i, ItemStack.EMPTY);
                        } else if (grid.getItem(i).getCount() > 1) {
                            grid.setItem(i, grid.getItem(i).copyWithCount(1));
                        }
                    }
                }
                updateResult();
            }
            case BUTTON_CLEAR -> clear();
            case BUTTON_ENCODE -> encode();
            default -> {
                return false;
            }
        }
        return true;
    }

    private void clear() {
        for (int i = 0; i < 9; i++) {
            grid.setItem(i, ItemStack.EMPTY);
        }
        for (int i = 0; i < 3; i++) {
            outputs.setItem(i, ItemStack.EMPTY);
        }
        updateResult();
    }

    // The schematic the section describes, or null when there's nothing to encode.
    private @Nullable Schematic schematic() {
        List<ItemStack> inputs = new ArrayList<>();
        boolean any = false;
        for (int i = 0; i < 9; i++) {
            inputs.add(grid.getItem(i).copy());
            any |= !grid.getItem(i).isEmpty();
        }
        if (!any) {
            return null;
        }
        if (!processing()) {
            ItemStack output = result.getItem(0);
            return output.isEmpty() ? null : Schematic.of(Schematic.Kind.CRAFTING, inputs, List.of(output.copy()));
        }
        List<ItemStack> results = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            results.add(outputs.getItem(i).copy());
        }
        return results.stream().allMatch(ItemStack::isEmpty) ? null : Schematic.of(Schematic.Kind.PROCESSING, inputs, results);
    }

    // Writes the schematic onto a blank card (or over the encoded card already in the slot).
    private void encode() {
        Schematic schematic = schematic();
        if (schematic == null) {
            return;
        }
        ItemStack encoded = cards.getItem(SchematicEncoderPart.ENCODED);
        ItemStack blank = cards.getItem(SchematicEncoderPart.BLANK);
        if (encoded.isEmpty()) {
            if (blank.isEmpty()) {
                return;
            }
            blank.shrink(1);
            cards.setItem(SchematicEncoderPart.BLANK, blank);
        } else if (!(encoded.getItem() instanceof SchematicItem)) {
            return;
        }
        ItemStack card = new ItemStack(schematic.kind() == Schematic.Kind.CRAFTING ? ModItems.ENCODED_SCHEMATIC_CRAFTING.get()
                : ModItems.ENCODED_SCHEMATIC_PROCESSING.get());
        card.set(ModDataComponents.SCHEMATIC.get(), schematic);
        cards.setItem(SchematicEncoderPart.ENCODED, card);
    }

    // An encoded card was put in: its schematic goes into the grid to look at or change.
    private void load(@Nullable Schematic schematic) {
        if (schematic == null || part == null) {
            return;
        }
        part.setMode(schematic.kind() == Schematic.Kind.CRAFTING ? SchematicEncoderPart.CRAFTING : SchematicEncoderPart.PROCESSING);
        List<ItemStack> inputs = schematic.grid();
        for (int i = 0; i < 9; i++) {
            grid.setItem(i, inputs.get(i));
        }
        for (int i = 0; i < 3; i++) {
            outputs.setItem(i, schematic.kind() == Schematic.Kind.PROCESSING && i < schematic.outputs().size() ? schematic.outputs().get(i).create()
                    : ItemStack.EMPTY);
        }
        updateResult();
    }

    // JEI: a recipe's inputs (nine slots, row by row) and outputs into the ghost slots.
    public void setRecipe(boolean crafting, List<ItemStack> inputs, List<ItemStack> results) {
        if (part == null) {
            return;
        }
        part.setMode(crafting ? SchematicEncoderPart.CRAFTING : SchematicEncoderPart.PROCESSING);
        for (int i = 0; i < 9; i++) {
            ItemStack stack = i < inputs.size() ? inputs.get(i) : ItemStack.EMPTY;
            grid.setItem(i, stack.isEmpty() || crafting && ResourceEntryItem.entry(stack) != null ? ItemStack.EMPTY
                    : ResourceEntryItem.entry(stack) != null ? stack.copy() : stack.copyWithCount(crafting ? 1 : Math.clamp(stack.getCount(), 1, MAX_AMOUNT)));
        }
        for (int i = 0; i < 3; i++) {
            ItemStack stack = !crafting && i < results.size() ? results.get(i) : ItemStack.EMPTY;
            outputs.setItem(i, stack.isEmpty() ? ItemStack.EMPTY
                    : ResourceEntryItem.entry(stack) != null ? stack.copy() : stack.copyWithCount(Math.clamp(stack.getCount(), 1, MAX_AMOUNT)));
        }
        updateResult();
    }

    // --- Shift-clicks ---

    // From the inventory: blank cards to their slot, encoded cards to theirs, anything else into the network. From the
    // card slots: back to the inventory.
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem() || index >= GRID && index < BLANK) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        if (index == BLANK || index == ENCODED) {
            ItemStack original = stack.copy();
            if (!moveItemStackTo(stack, 0, INVENTORY_SLOTS, true)) {
                return ItemStack.EMPTY;
            }
            slot.setChanged();
            return original;
        }
        if (stack.is(ModItems.SCHEMATIC_CARD.get())) {
            moveItemStackTo(stack, BLANK, BLANK + 1, false);
            slot.setChanged();
            return ItemStack.EMPTY;
        }
        if (stack.getItem() instanceof SchematicItem && !slots.get(ENCODED).hasItem()) {
            ItemStack one = stack.split(1);
            slots.get(ENCODED).setByPlayer(one, ItemStack.EMPTY);
            slot.setChanged();
            return ItemStack.EMPTY;
        }
        return super.quickMoveStack(player, index);
    }
}
