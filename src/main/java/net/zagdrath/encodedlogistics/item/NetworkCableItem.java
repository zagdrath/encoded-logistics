/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;

// A cable's item, with its channels and what it connects to in the tooltip: "Carries 8 channels", "Connects to every
// colour" or "Connects only to Red and neutral cables".
public class NetworkCableItem extends BlockItem {
    private final NetworkCableBlock cable;

    public NetworkCableItem(NetworkCableBlock cable, Item.Properties properties) {
        super(cable, properties);
        this.cable = cable;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, builder, flag);
        builder.accept(Component.translatable("tooltip.encodedlogistics.cable.channels", cable.getTier().channels()).withStyle(ChatFormatting.GRAY));
        DyeColor dye = cable.getColor().dye();
        Component connects = dye == null ? Component.translatable("tooltip.encodedlogistics.cable.neutral")
                : Component.translatable("tooltip.encodedlogistics.cable.dyed", Component.translatable("color.minecraft." + dye.getSerializedName()));
        builder.accept(connects.copy().withStyle(ChatFormatting.GRAY));
    }
}
