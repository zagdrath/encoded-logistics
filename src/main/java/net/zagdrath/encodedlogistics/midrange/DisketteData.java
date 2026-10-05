/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.ArrayList;
import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.crafting.Schematic;

// What's written on an 8" Diskette (ModDataComponents.DISKETTE_RECIPES; a blank one has none): its label (the job
// library's name), the recipes a Card Reader read onto it (up to MAX_RECIPES; a Midrange System crafts from them), and
// the ELCL libraries SAVLIB saved on it (LibraryImage NBT).
public record DisketteData(String label, List<Schematic> recipes, List<CompoundTag> libraries) {
    public static final int MAX_RECIPES = 8;

    public static final Codec<DisketteData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.optionalFieldOf("label", "").forGetter(DisketteData::label),
            Schematic.CODEC.listOf().optionalFieldOf("recipes", List.of()).forGetter(DisketteData::recipes),
            CompoundTag.CODEC.listOf().optionalFieldOf("libraries", List.of()).forGetter(DisketteData::libraries))
            .apply(i, DisketteData::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DisketteData> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DisketteData::label,
            Schematic.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_RECIPES)), DisketteData::recipes,
            ByteBufCodecs.COMPOUND_TAG.apply(ByteBufCodecs.list()), DisketteData::libraries,
            DisketteData::new);

    public DisketteData {
        recipes = List.copyOf(recipes);
        libraries = List.copyOf(libraries);
    }

    // The same with a recipe read in: one making the same thing is replaced, else it's added (while there's room).
    public DisketteData withRecipe(Schematic recipe) {
        List<Schematic> next = new ArrayList<>(recipes);
        for (int i = 0; i < next.size(); i++) {
            if (ItemStack.isSameItemSameComponents(next.get(i).output(), recipe.output())) {
                next.set(i, recipe);
                return new DisketteData(label, next, libraries);
            }
        }
        if (next.size() < MAX_RECIPES) {
            next.add(recipe);
        }
        return new DisketteData(label, next, libraries);
    }

    public DisketteData withLibraries(List<CompoundTag> libraries) {
        return new DisketteData(label, recipes, libraries);
    }
}
