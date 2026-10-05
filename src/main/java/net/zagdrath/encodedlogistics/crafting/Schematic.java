/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.crafting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.CraftingInput;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// What an Encoded Schematic holds (the encodedlogistics:schematic component), written by the Schematic Encoder.
// Crafting: up to nine grid slots of one item each and the recipe's result; a Fabricator crafts it. Processing: up to
// nine input stacks and up to three output stacks; a Gateway feeds the inputs to a machine and waits for the outputs.
public record Schematic(Kind kind, List<Input> inputs, List<ItemStackTemplate> outputs) {
    public static final int INPUTS = 9, PROCESSING_OUTPUTS = 3;

    public enum Kind implements StringRepresentable {
        CRAFTING("crafting"), PROCESSING("processing");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);
        public static final StreamCodec<ByteBuf, Kind> STREAM_CODEC = ByteBufCodecs.idMapper(i -> values()[i], Kind::ordinal);

        private final String id;

        Kind(String id) {
            this.id = id;
        }

        @Override
        public String getSerializedName() {
            return id;
        }
    }

    // An input and its slot in the encoder's grid (0-8, row by row).
    public record Input(int slot, ItemStackTemplate item) {
        public static final Codec<Input> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.intRange(0, INPUTS - 1).fieldOf("slot").forGetter(Input::slot),
                ItemStackTemplate.CODEC.fieldOf("item").forGetter(Input::item))
                .apply(i, Input::new));
        public static final StreamCodec<RegistryFriendlyByteBuf, Input> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Input::slot,
                ItemStackTemplate.STREAM_CODEC, Input::item,
                Input::new);
    }

    public static final Codec<Schematic> CODEC = RecordCodecBuilder.create(i -> i.group(
            Kind.CODEC.fieldOf("type").forGetter(Schematic::kind),
            Input.CODEC.listOf(0, INPUTS).fieldOf("inputs").forGetter(Schematic::inputs),
            ItemStackTemplate.CODEC.listOf(1, PROCESSING_OUTPUTS).fieldOf("outputs").forGetter(Schematic::outputs))
            .apply(i, Schematic::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Schematic> STREAM_CODEC = StreamCodec.composite(
            Kind.STREAM_CODEC, Schematic::kind,
            Input.STREAM_CODEC.apply(ByteBufCodecs.list(INPUTS)), Schematic::inputs,
            ItemStackTemplate.STREAM_CODEC.apply(ByteBufCodecs.list(PROCESSING_OUTPUTS)), Schematic::outputs,
            Schematic::new);

    public Schematic {
        inputs = List.copyOf(inputs);
        outputs = List.copyOf(outputs);
    }

    // From the encoder's slots: nine inputs (empty where there's none) and the outputs (empties left out).
    public static Schematic of(Kind kind, List<ItemStack> grid, List<ItemStack> outputs) {
        List<Input> inputs = new ArrayList<>();
        for (int slot = 0; slot < Math.min(INPUTS, grid.size()); slot++) {
            ItemStack stack = grid.get(slot);
            if (!stack.isEmpty()) {
                inputs.add(new Input(slot, ItemStackTemplate.fromNonEmptyStack(kind == Kind.CRAFTING ? stack.copyWithCount(1) : stack)));
            }
        }
        List<ItemStackTemplate> results = new ArrayList<>();
        for (ItemStack stack : outputs) {
            if (!stack.isEmpty() && results.size() < PROCESSING_OUTPUTS) {
                results.add(ItemStackTemplate.fromNonEmptyStack(stack));
            }
        }
        return new Schematic(kind, inputs, results);
    }

    // The first output: what the schematic makes, as shown on the card and in terminals.
    public ItemStack output() {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.getFirst().create();
    }

    // The nine grid slots (empty where there's no input).
    public List<ItemStack> grid() {
        List<ItemStack> grid = new ArrayList<>(INPUTS);
        for (int slot = 0; slot < INPUTS; slot++) {
            grid.add(ItemStack.EMPTY);
        }
        for (Input input : inputs) {
            grid.set(input.slot(), input.item().create());
        }
        return grid;
    }

    // A crafting schematic's grid as the recipe sees it.
    public CraftingInput craftingInput() {
        return CraftingInput.of(3, 3, grid());
    }

    // What one craft (or one processing run) takes, added up by item.
    public Map<StorageKey, Long> inputTotals() {
        Map<StorageKey, Long> totals = new LinkedHashMap<>();
        for (Input input : inputs) {
            ItemStack stack = input.item().create();
            totals.merge(StorageKey.of(stack), (long) stack.getCount(), Long::sum);
        }
        return totals;
    }

    // What one craft gives, added up by item.
    public Map<StorageKey, Long> outputTotals() {
        Map<StorageKey, Long> totals = new LinkedHashMap<>();
        for (ItemStackTemplate output : outputs) {
            ItemStack stack = output.create();
            totals.merge(StorageKey.of(stack), (long) stack.getCount(), Long::sum);
        }
        return totals;
    }

    public long outputCount(StorageKey key) {
        return outputTotals().getOrDefault(key, 0L);
    }
}
