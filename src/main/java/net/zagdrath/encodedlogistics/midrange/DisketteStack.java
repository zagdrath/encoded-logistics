/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.elcl.device.Diskette;
import net.zagdrath.encodedlogistics.elcl.device.LibraryImage;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// An 8" Diskette in a drive slot as ELCL sees it (SAVLIB / RSTLIB): its label and libraries from its DisketteData; a
// write replaces the library of the same name and stores the stack back (changed: the drive's setChanged).
public final class DisketteStack implements Diskette {
    public static final String DEFAULT_LABEL = "RECIPES";

    private final Supplier<ItemStack> stack;
    private final Consumer<ItemStack> changed;

    public DisketteStack(Supplier<ItemStack> stack, Consumer<ItemStack> changed) {
        this.stack = stack;
        this.changed = changed;
    }

    public static DisketteData data(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.DISKETTE_RECIPES.get(), new DisketteData("", List.of(), List.of()));
    }

    @Override
    public String label() {
        String label = data(stack.get()).label();
        return label.isEmpty() ? DEFAULT_LABEL : label;
    }

    @Override
    public List<LibraryImage> libraries() {
        List<LibraryImage> images = new ArrayList<>();
        for (CompoundTag tag : data(stack.get()).libraries()) {
            images.add(LibraryImage.load(tag));
        }
        return images;
    }

    @Override
    public void write(LibraryImage image) {
        ItemStack item = stack.get();
        DisketteData data = data(item);
        List<CompoundTag> libraries = new ArrayList<>();
        for (CompoundTag tag : data.libraries()) {
            if (!LibraryImage.load(tag).library().equalsIgnoreCase(image.library())) {
                libraries.add(tag);
            }
        }
        libraries.add(image.save());
        item.set(ModDataComponents.DISKETTE_RECIPES.get(), new DisketteData(data.label().isEmpty() ? DEFAULT_LABEL : data.label(), data.recipes(), libraries));
        changed.accept(item);
    }
}
