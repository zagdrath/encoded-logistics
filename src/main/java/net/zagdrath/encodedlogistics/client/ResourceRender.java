/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import org.jspecify.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.item.ResourceEntryItem;
import net.zagdrath.encodedlogistics.storage.PressurizedSource;
import net.zagdrath.encodedlogistics.storage.PressurizedSources;
import net.zagdrath.encodedlogistics.storage.ResourceType;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// Drawing a StorageKey in a GUI square: an item as itself; a fluid (and an Arcforge gas, which is a fluid) as its still
// texture in its tint; a gas with no fluid as a square of its source's colour. Slots holding a Resource Entry (filters,
// schematics) draw the resource it stands for, with its amount when it has one.
public final class ResourceRender {
    private static final int TEXT = 0xFFF0F0F0;
    private static final Identifier ENERGY = EncodedLogistics.id("terminal/energy");

    private ResourceRender() {}

    public static void icon(GuiGraphicsExtractor graphics, StorageKey key, int x, int y) {
        if (key.isItem()) {
            graphics.item(key.stack(), x, y);
            return;
        }
        if (key.is(ResourceType.ENERGY)) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ENERGY, x, y, 16, 16);
            return;
        }
        FluidResource fluid = key.fluid();
        if (fluid != null && fluid(graphics, fluid.getFluid(), x, y)) {
            return;
        }
        graphics.fill(x + 1, y + 1, x + 15, y + 15, 0xFF000000 | color(key));
    }

    // The resource's colour as an RGB (for lists and the Type column): a gas source's colour, a fluid's tint, else grey.
    public static int color(StorageKey key) {
        if (key.source() != null && key.gas() != null) {
            PressurizedSource source = PressurizedSources.get(key.source());
            if (source != null) {
                return source.color(key.gas()) & 0xFFFFFF;
            }
        }
        return 0x8A8A8A;
    }

    private static boolean fluid(GuiGraphicsExtractor graphics, Fluid fluid, int x, int y) {
        if (fluid == Fluids.EMPTY) {
            return false;
        }
        try {
            var model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState());
            TextureAtlasSprite still = model.stillMaterial().sprite();
            int tint = (model.fluidTintSource() != null ? model.fluidTintSource().colorAsStack(new FluidStack(fluid, 1)) : -1) | 0xFF000000;
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, still, x, y, 16, 16, tint);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    // A slot's stack, if it's a Resource Entry: the resource and its amount (short, in its unit). False for any other
    // stack (the screen draws it as usual).
    public static boolean entrySlot(GuiGraphicsExtractor graphics, Font font, Slot slot) {
        return entry(graphics, font, slot.getItem(), slot.x, slot.y);
    }

    public static boolean entry(GuiGraphicsExtractor graphics, Font font, ItemStack stack, int x, int y) {
        ResourceEntryItem.Entry entry = ResourceEntryItem.entry(stack);
        if (entry == null) {
            return false;
        }
        icon(graphics, entry.key(), x, y);
        if (entry.amount() > 0) {
            amount(graphics, font, entry.key().type().abbreviate(entry.amount()), x, y);
        }
        return true;
    }

    // Text at half size in a square's bottom right corner, as counts are drawn.
    public static void amount(GuiGraphicsExtractor graphics, Font font, String text, int x, int y) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x + 16 - font.width(text) * 0.5F, y + 16 - 4.5F);
        graphics.pose().scale(0.5F, 0.5F);
        graphics.text(font, text, 0, 0, TEXT, true);
        graphics.pose().popMatrix();
    }

    // The display name of the mod a resource comes from ("Arcforge"), for tooltips.
    public static String modName(@Nullable String namespace) {
        if (namespace == null) {
            return "";
        }
        return ModList.get().getModContainerById(namespace).map(container -> container.getModInfo().getDisplayName()).orElse(namespace);
    }
}
