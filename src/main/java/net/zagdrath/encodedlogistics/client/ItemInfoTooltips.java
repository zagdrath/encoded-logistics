/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import java.util.List;

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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;

// A short description of what each Encoded Logistics item does, under its name, the same way as Arcforge: "Hold
// [Shift] for info", and the description while Shift is held. Descriptions live in the lang file as
// tooltip.encodedlogistics.info.<item>; coloured items (cables) share one without the colour, e.g.
// tooltip.encodedlogistics.info.network_cable. A cable's Shift info ends with its channels and the colours it joins.
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
        String key = descriptionKey(id.getPath());
        if (key == null) {
            return;
        }
        List<Component> tooltip = event.getToolTip();
        // Right under the name, before the item's own lines.
        int at = Math.min(1, tooltip.size());
        if (!Minecraft.getInstance().hasShiftDown()) {
            tooltip.add(at, Component.translatable("tooltip.encodedlogistics.hold_shift",
                    Component.translatable("tooltip.encodedlogistics.shift").withStyle(ChatFormatting.GRAY)).withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        List<FormattedText> lines = Minecraft.getInstance().font.getSplitter()
                .splitLines(Component.translatable(key), WRAP_WIDTH, Style.EMPTY);
        for (int i = 0; i < lines.size(); i++) {
            tooltip.add(at + i, Component.literal(lines.get(i).getString()).withStyle(ChatFormatting.GRAY));
        }
        if (event.getItemStack().getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof NetworkCableBlock cable) {
            at += lines.size();
            tooltip.add(at++, Component.translatable("tooltip.encodedlogistics.cable.channels", cable.getTier().channels()).withStyle(ChatFormatting.GRAY));
            DyeColor dye = cable.getColor().dye();
            tooltip.add(at, (dye == null ? Component.translatable("tooltip.encodedlogistics.cable.neutral")
                    : Component.translatable("tooltip.encodedlogistics.cable.dyed", Component.translatable("color.minecraft." + dye.getSerializedName())))
                    .withStyle(ChatFormatting.GRAY));
        }
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
        return null;
    }
}
