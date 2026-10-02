/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.model;

import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemModels;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// An item model (type encodedlogistics:schematic_output) that draws an Encoded Schematic as the item it makes - its
// first output - or as the fallback model when it holds none. items/encoded_schematic_*.json use it while Shift
// (key.sneak) is held.
public record SchematicOutputModel(ItemModel fallback) implements ItemModel {
    public static final Identifier ID = EncodedLogistics.id("schematic_output");

    @Override
    public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver, ItemDisplayContext displayContext,
            @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        output.appendModelIdentityElement(this);
        Schematic schematic = item.get(ModDataComponents.SCHEMATIC.get());
        ItemStack made = schematic != null ? schematic.output() : ItemStack.EMPTY;
        if (made.isEmpty()) {
            fallback.update(output, item, resolver, displayContext, level, owner, seed);
        } else {
            resolver.appendItemLayers(output, made, displayContext, level, owner, seed);
        }
    }

    public record Unbaked(ItemModel.Unbaked fallback) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                ItemModels.CODEC.fieldOf("fallback").forGetter(Unbaked::fallback))
                .apply(i, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
            return new SchematicOutputModel(fallback.bake(context, transformation));
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            fallback.resolveDependencies(resolver);
        }
    }
}
