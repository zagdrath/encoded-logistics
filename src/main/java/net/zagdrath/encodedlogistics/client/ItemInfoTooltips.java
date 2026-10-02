/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.crafting.Schematic;
import net.zagdrath.encodedlogistics.item.CableFacadeItem;
import net.zagdrath.encodedlogistics.item.PartItem;
import net.zagdrath.encodedlogistics.item.SchematicItem;
import net.zagdrath.encodedlogistics.item.StorageDriveItem;
import net.zagdrath.encodedlogistics.item.StorageTierItem;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.DriveStats;
import net.zagdrath.encodedlogistics.storage.ItemKey;
import net.zagdrath.encodedlogistics.storage.StorageTier;

// A short description of what each Encoded Logistics item does, under its name, the same way as Arcforge: "Hold
// [Shift] for info", and the description while Shift is held. Descriptions live in the lang file as
// tooltip.encodedlogistics.info.<item>; coloured items (cables) share one without the colour, e.g.
// tooltip.encodedlogistics.info.network_cable. A cable's Shift info ends with its lanes and the colours it joins; a
// facade always shows the block it copies.
@EventBusSubscriber(modid = EncodedLogistics.MODID, value = Dist.CLIENT)
public final class ItemInfoTooltips {
    private static final String PREFIX = "tooltip.encodedlogistics.info.";
    // Descriptions wrap at this width (the vanilla tooltip font is 9 px tall; ~40 characters a line).
    private static final int WRAP_WIDTH = 200;

    private ItemInfoTooltips() {}

    @SubscribeEvent
    static void onTooltip(ItemTooltipEvent event) {
        Identifier id = BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem());
        if (!EncodedLogistics.MODID.equals(id.getNamespace())) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        String key = descriptionKey(id.getPath());
        // Right under the name, before the item's own lines.
        int at = Math.min(1, tooltip.size());
        // Drives, dies and photomasks always say their tier, fill or that they're reusable.
        ItemStack stack = event.getItemStack();
        if (stack.getItem() instanceof StorageDriveItem) {
            DriveStats stats = StorageDriveItem.stats(stack);
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.drive.bytes", String.format(Locale.ROOT, "%,d", stats.bytesUsed()),
                    String.format(Locale.ROOT, "%,d", stats.bytesTotal())).withStyle(ChatFormatting.GRAY));
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.drive.types", stats.typesUsed(), Config.DRIVE_TYPE_LIMIT.getAsInt())
                    .withStyle(ChatFormatting.GRAY));
        } else if (stack.getItem() instanceof StorageTierItem die) {
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.die.tier",
                    Component.literal(die.getTier().label()).withColor(die.getTier().light())).withStyle(ChatFormatting.GRAY));
        } else if (stack.is(ModItems.FILTER_MODULE.get()) || stack.is(ModItems.THROUGHPUT_MODULE.get())) {
            tooltip.add(at++, Component.translatable(stack.is(ModItems.FILTER_MODULE.get()) ? "tooltip.encodedlogistics.module.filter"
                    : "tooltip.encodedlogistics.module.throughput").withStyle(ChatFormatting.GRAY));
        } else if (stack.getItem() instanceof PartItem part && !part.getPartType().isTerminal()) {
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.part.lane").withStyle(ChatFormatting.DARK_GRAY));
        } else if (SchematicItem.schematic(stack) != null) {
            // An encoded schematic: what it makes and takes.
            Schematic schematic = SchematicItem.schematic(stack);
            ItemStack output = schematic.output();
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.schematic.output",
                    Component.literal(output.getCount() + " x ").append(output.getHoverName())).withStyle(ChatFormatting.GRAY));
            for (Map.Entry<ItemKey, Long> input : schematic.inputTotals().entrySet()) {
                tooltip.add(at++, Component.literal("  " + input.getValue() + " x ").append(input.getKey().stack().getHoverName())
                        .withStyle(ChatFormatting.DARK_GRAY));
            }
            if (!Minecraft.getInstance().hasShiftDown()) {
                tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.schematic.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
            }
        } else if (stack.is(ModItems.LOGIC_PHOTOMASK.get()) || stack.is(ModItems.STORAGE_PHOTOMASK.get()) || stack.is(ModItems.MEMORY_PHOTOMASK.get())
                || stack.is(ModItems.PROCESSOR_PHOTOMASK.get())) {
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.photomask.reusable").withStyle(ChatFormatting.GRAY));
        }
        // A facade always says what it looks like.
        if (event.getItemStack().getItem() instanceof CableFacadeItem) {
            BlockState target = CableFacadeItem.target(event.getItemStack());
            tooltip.add(at++, (target != null
                    ? Component.translatable("tooltip.encodedlogistics.cable_facade.target", target.getBlock().getName())
                    : Component.translatable("tooltip.encodedlogistics.cable_facade.blank")).withStyle(ChatFormatting.GRAY));
        }
        if (key == null) {
            return;
        }
        if (!Minecraft.getInstance().hasShiftDown()) {
            tooltip.add(at, Component.translatable("tooltip.encodedlogistics.hold_shift",
                    Component.translatable("tooltip.encodedlogistics.shift").withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        List<FormattedText> lines = Minecraft.getInstance().font.getSplitter()
                .splitLines(Component.translatable(key, descriptionArguments(id.getPath())), WRAP_WIDTH, Style.EMPTY);
        for (int i = 0; i < lines.size(); i++) {
            tooltip.add(at + i, Component.literal(lines.get(i).getString()).withStyle(ChatFormatting.GRAY));
        }
        if (event.getItemStack().getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof NetworkCableBlock cable) {
            at += lines.size();
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.cable.lanes", cable.getTier().lanes()).withStyle(ChatFormatting.GRAY));
            DyeColor dye = cable.getColor().dye();
            tooltip.add(at, (dye == null ? Component.translatable("tooltip.encodedlogistics.cable.neutral")
                    : Component.translatable("tooltip.encodedlogistics.cable.dyed", Component.translatable("color.minecraft." + dye.getSerializedName())))
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // Numbers a description shows that come from the config.
    private static Object[] descriptionArguments(String path) {
        if (path.equals("capacitor_bank")) {
            return new Object[] { String.format(Locale.ROOT, "%,d", Config.CAPACITOR_CAPACITY.getAsInt()) };
        }
        return new Object[0];
    }

    // The item's description key, or the shared one without its colour, or null if it has none.
    private static @Nullable String descriptionKey(String path) {
        Language language = Language.getInstance();
        if (language.has(PREFIX + path)) {
            return PREFIX + path;
        }
        for (DyeColor dye : DyeColor.values()) {
            String colour = dye.getSerializedName() + "_";
            if (path.startsWith(colour) && language.has(PREFIX + path.substring(colour.length()))) {
                return PREFIX + path.substring(colour.length());
            }
        }
        for (StorageTier tier : StorageTier.values()) {
            String suffix = "_" + tier.id();
            if (path.endsWith(suffix) && language.has(PREFIX + path.substring(0, path.length() - suffix.length()))) {
                return PREFIX + path.substring(0, path.length() - suffix.length());
            }
        }
        return null;
    }
}
