/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.storage;

import java.util.Objects;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.transfer.fluid.FluidResource;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.item.ResourceEntryItem;

// One kind of thing as network storage counts it, without an amount: an item (with its components), a fluid, or a
// pressurized gas or chemical from another mod (PressurizedSource), each of a ResourceType. Two keys are equal when they
// are the same resource: items that would stack together, the same fluid with the same components, the same gas of the
// same source.
//
// Item keys save and sync exactly as they always have (an item stack template), so worlds with item-only storage load
// unchanged; the other types save as {resource_type, fluid} or {resource_type, source, gas}. A gas whose mod is missing
// keeps its key (and its amount in storage) until the mod is back.
//
// Wherever an ItemStack has to stand for a key (ghost filter slots, schematics, the encoder), a fluid or gas key is a
// Resource Entry (ResourceEntryItem) carrying it: stack() gives one, and entry() reads one back. Item keys are the item.
public final class StorageKey {
    private static final Codec<StorageKey> ITEM_CODEC = ItemStackTemplate.CODEC.xmap(template -> StorageKey.of(template.create()),
            key -> ItemStackTemplate.fromNonEmptyStack(key.stack()));

    private record Typed(ResourceType type, Optional<FluidResource> fluid, Optional<String> source, Optional<Identifier> gas) {
        static final Codec<Typed> CODEC = RecordCodecBuilder.create(i -> i.group(
                ResourceType.CODEC.fieldOf("resource_type").forGetter(Typed::type),
                FluidResource.CODEC.optionalFieldOf("fluid").forGetter(Typed::fluid),
                Codec.STRING.optionalFieldOf("source").forGetter(Typed::source),
                Identifier.CODEC.optionalFieldOf("gas").forGetter(Typed::gas))
                .apply(i, Typed::new));

        DataResult<StorageKey> key() {
            return switch (type) {
                case FLUID -> fluid.map(StorageKey::fluid).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "A fluid key needs a fluid"));
                case PRESSURIZED -> source.isPresent() && gas.isPresent() ? DataResult.success(pressurized(source.get(), gas.get()))
                        : DataResult.error(() -> "A pressurized key needs a source and a gas");
                default -> DataResult.error(() -> "Not a typed resource: " + type.getSerializedName());
            };
        }

        static Typed of(StorageKey key) {
            return key.type == ResourceType.FLUID ? new Typed(key.type, Optional.of(key.fluid), Optional.empty(), Optional.empty())
                    : new Typed(key.type, Optional.empty(), Optional.of(key.source), Optional.of(key.gas));
        }
    }

    // Fluid and gas keys only (a Resource Entry's).
    public static final Codec<StorageKey> TYPED_CODEC = Typed.CODEC.comapFlatMap(Typed::key, Typed::of);

    public static final Codec<StorageKey> CODEC = Codec.either(TYPED_CODEC, ITEM_CODEC).xmap(either -> either.map(key -> key, key -> key),
            key -> key.isItem() ? Either.right(key) : Either.left(key));

    public static final StreamCodec<RegistryFriendlyByteBuf, StorageKey> STREAM_CODEC = StreamCodec.of(StorageKey::write, StorageKey::read);

    // The network's energy as terminals list it (an entry beside the items, as in AE2): display only - never stored,
    // saved or sent, and nothing to click.
    public static final StorageKey ENERGY = new StorageKey(ResourceType.ENERGY, null, null, EncodedLogistics.MODID, EncodedLogistics.id("energy"));

    private final ResourceType type;
    // ITEM: the item, count 1. Others: a Resource Entry for the key, made when first asked for.
    private @Nullable ItemStack stack;
    private final @Nullable FluidResource fluid;
    private final @Nullable String source;
    private final @Nullable Identifier gas;
    private final int hash;

    private StorageKey(ResourceType type, @Nullable ItemStack stack, @Nullable FluidResource fluid, @Nullable String source, @Nullable Identifier gas) {
        this.type = type;
        this.stack = stack;
        this.fluid = fluid;
        this.source = source;
        this.gas = gas;
        this.hash = switch (type) {
            case ITEM -> ItemStack.hashItemAndComponents(Objects.requireNonNull(stack));
            case FLUID -> 31 + Objects.requireNonNull(fluid).hashCode();
            default -> 31 * (62 + Objects.requireNonNull(source).hashCode()) + Objects.requireNonNull(gas).hashCode();
        };
    }

    public static StorageKey of(ItemStack stack) {
        return new StorageKey(ResourceType.ITEM, stack.copyWithCount(1), null, null, null);
    }

    // A fluid: kept as PRESSURIZED when a PressurizedSource says it's one of its gases (an Arcforge gas), else FLUID.
    public static StorageKey fluid(FluidResource fluid) {
        Fluid source = fluid.getFluid();
        Optional<PressurizedSource> gasSource = PressurizedSources.forFluid(source);
        if (gasSource.isPresent()) {
            return pressurized(gasSource.get().id(), BuiltInRegistries.FLUID.getKey(source));
        }
        return new StorageKey(ResourceType.FLUID, null, fluid, null, null);
    }

    public static StorageKey fluid(Fluid fluid) {
        return fluid(FluidResource.of(fluid));
    }

    public static StorageKey pressurized(String source, Identifier gas) {
        return new StorageKey(ResourceType.PRESSURIZED, null, null, source, gas);
    }

    // The key an ItemStack stands for where one is used as an entry (ghost slots, schematics): a Resource Entry's
    // resource, or else the item itself.
    public static StorageKey entry(ItemStack stack) {
        StorageKey key = ResourceEntryItem.key(stack);
        return key != null ? key : of(stack);
    }

    public ResourceType type() {
        return type;
    }

    public boolean isItem() {
        return type == ResourceType.ITEM;
    }

    public boolean is(ResourceType type) {
        return this.type == type;
    }

    // An item key's item, count 1; for a fluid or gas, a Resource Entry standing for it. Don't change it.
    public ItemStack stack() {
        if (stack == null) {
            stack = ResourceEntryItem.of(this, 0);
        }
        return stack;
    }

    // An item key: that many of the item. A fluid or gas: a Resource Entry for that amount.
    public ItemStack toStack(int count) {
        return isItem() ? stack().copyWithCount(count) : ResourceEntryItem.of(this, count);
    }

    // Items: a stack's size. Fluids and gases come out by amount, not stacks: 1.
    public int maxStackSize() {
        return isItem() ? stack().getMaxStackSize() : 1;
    }

    // A fluid key's fluid; a pressurized key's, for a source whose gases are fluids and is present. Null otherwise.
    public @Nullable FluidResource fluid() {
        if (type == ResourceType.FLUID) {
            return fluid;
        }
        if (type == ResourceType.PRESSURIZED) {
            PressurizedSource from = PressurizedSources.get(Objects.requireNonNull(source));
            Optional<Fluid> found = from != null ? from.fluid(Objects.requireNonNull(gas)) : Optional.empty();
            return found.filter(f -> f != Fluids.EMPTY).map(FluidResource::of).orElse(null);
        }
        return null;
    }

    // A pressurized key's source ("arcforge"), else null.
    public @Nullable String source() {
        return source;
    }

    // A pressurized key's gas id, else null.
    public @Nullable Identifier gas() {
        return gas;
    }

    // The resource's registry id: the item's, the fluid's, or the gas's own (e.g. arcforge:hydrogen).
    public Identifier id() {
        return switch (type) {
            case ITEM -> BuiltInRegistries.ITEM.getKey(stack().getItem());
            case FLUID -> BuiltInRegistries.FLUID.getKey(Objects.requireNonNull(fluid).getFluid());
            default -> Objects.requireNonNull(gas);
        };
    }

    // The mod it comes from, for @mod searches and tooltips.
    public String namespace() {
        return id().getNamespace();
    }

    public Component displayName() {
        if (type == ResourceType.ENERGY) {
            return Component.translatable("gui.encodedlogistics.terminal.energy");
        }
        return switch (type) {
            case ITEM -> stack().getHoverName();
            case FLUID -> Objects.requireNonNull(fluid).getHoverName();
            default -> {
                PressurizedSource from = PressurizedSources.get(Objects.requireNonNull(source));
                yield from != null ? from.name(Objects.requireNonNull(gas)) : Component.literal(Objects.requireNonNull(gas).toString());
            }
        };
    }

    // An amount of this resource in its unit: 1,234 / 1.5 B / 250 mB.
    public String format(long amount) {
        return type.format(amount);
    }

    private static void write(RegistryFriendlyByteBuf buf, StorageKey key) {
        ResourceType.STREAM_CODEC.encode(buf, key.type);
        switch (key.type) {
            case ITEM -> ItemStack.STREAM_CODEC.encode(buf, key.stack());
            case FLUID -> FluidResource.STREAM_CODEC.encode(buf, Objects.requireNonNull(key.fluid));
            default -> {
                ByteBufCodecs.STRING_UTF8.encode(buf, Objects.requireNonNull(key.source));
                Identifier.STREAM_CODEC.encode(buf, Objects.requireNonNull(key.gas));
            }
        }
    }

    private static StorageKey read(RegistryFriendlyByteBuf buf) {
        ResourceType type = ResourceType.STREAM_CODEC.decode(buf);
        return switch (type) {
            case ITEM -> of(ItemStack.STREAM_CODEC.decode(buf));
            case FLUID -> new StorageKey(ResourceType.FLUID, null, FluidResource.STREAM_CODEC.decode(buf), null, null);
            default -> pressurized(ByteBufCodecs.STRING_UTF8.decode(buf), Identifier.STREAM_CODEC.decode(buf));
        };
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof StorageKey key) || hash != key.hash || type != key.type) {
            return false;
        }
        return switch (type) {
            case ITEM -> ItemStack.isSameItemSameComponents(stack(), key.stack());
            case FLUID -> Objects.requireNonNull(fluid).equals(key.fluid);
            default -> Objects.equals(source, key.source) && Objects.equals(gas, key.gas);
        };
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return switch (type) {
            case ITEM -> stack().toString();
            case FLUID -> "fluid " + Objects.requireNonNull(fluid);
            default -> source + " gas " + gas;
        };
    }
}
