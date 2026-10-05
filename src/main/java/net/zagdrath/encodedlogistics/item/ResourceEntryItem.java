/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;
import net.zagdrath.encodedlogistics.storage.StorageKey;

// A fluid or gas standing in an item's place: in a ghost filter slot, a Processing schematic's inputs and outputs, the
// Gateway's stock row. It carries the resource and an amount (mB, or the gas's own unit) in its resource_entry
// component; the stack's count is always 1. Not obtainable and never stored: anything holding one reads it with
// StorageKey.entry() / amount().
public class ResourceEntryItem extends Item {
    public record Entry(StorageKey key, long amount) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                StorageKey.TYPED_CODEC.fieldOf("resource").forGetter(Entry::key),
                Codec.LONG.optionalFieldOf("amount", 0L).forGetter(Entry::amount))
                .apply(i, Entry::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                StorageKey.STREAM_CODEC, Entry::key,
                ByteBufCodecs.VAR_LONG, Entry::amount,
                Entry::new);
    }

    public ResourceEntryItem(Item.Properties properties) {
        super(properties);
    }

    public static ItemStack of(StorageKey key, long amount) {
        ItemStack stack = new ItemStack(ModItems.RESOURCE_ENTRY.get());
        stack.set(ModDataComponents.RESOURCE_ENTRY.get(), new Entry(key, Math.max(0, amount)));
        return stack;
    }

    public static @Nullable Entry entry(ItemStack stack) {
        return stack.is(ModItems.RESOURCE_ENTRY.get()) ? stack.get(ModDataComponents.RESOURCE_ENTRY.get()) : null;
    }

    // The fluid or gas a Resource Entry stands for; null for any other stack.
    public static @Nullable StorageKey key(ItemStack stack) {
        Entry entry = entry(stack);
        return entry != null ? entry.key() : null;
    }

    // How much of its key a stack stands for, as an entry: a Resource Entry's amount, or the stack's count.
    public static long amount(ItemStack stack) {
        Entry entry = entry(stack);
        return entry != null ? entry.amount() : stack.getCount();
    }

    // The same entry with another amount: a Resource Entry's amount, or a copy of the stack with that count.
    public static ItemStack withAmount(ItemStack stack, long amount) {
        Entry entry = entry(stack);
        return entry != null ? of(entry.key(), amount) : stack.copyWithCount((int) Math.max(1, Math.min(amount, Integer.MAX_VALUE)));
    }

    @Override
    public Component getName(ItemStack stack) {
        Entry entry = entry(stack);
        return entry != null ? entry.key().displayName() : super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> tooltip, TooltipFlag flag) {
        Entry entry = entry(stack);
        if (entry != null) {
            if (entry.amount() > 0) {
                tooltip.accept(Component.literal(entry.key().format(entry.amount())).withStyle(ChatFormatting.GRAY));
            }
            tooltip.accept(Component.translatable("tooltip.encodedlogistics.resource." + entry.key().type().getSerializedName())
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
