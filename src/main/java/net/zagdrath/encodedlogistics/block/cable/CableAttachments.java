/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block.cable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;

// What's mounted on each side of a cable: nothing, a Cable Anchor (the side never connects) or a Cable Facade (a panel
// covering the side, copying the look of its target block; no target is a blank facade). Immutable; one per side, by
// Direction ordinal.
public final class CableAttachments {
    public enum Kind implements StringRepresentable {
        NONE("none"),
        ANCHOR("anchor"),
        FACADE("facade");

        public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public record Attachment(Kind kind, @Nullable BlockState target) {
        public static final Attachment NONE = new Attachment(Kind.NONE, null);
        public static final Attachment ANCHOR = new Attachment(Kind.ANCHOR, null);

        static final Codec<Attachment> CODEC = RecordCodecBuilder.create(i -> i.group(
                Kind.CODEC.fieldOf("kind").forGetter(Attachment::kind),
                BlockState.CODEC.optionalFieldOf("target").forGetter(a -> Optional.ofNullable(a.target())))
                .apply(i, (kind, target) -> new Attachment(kind, target.orElse(null))));

        public static Attachment facade(@Nullable BlockState target) {
            return new Attachment(Kind.FACADE, target);
        }

        public boolean isAnchor() {
            return kind == Kind.ANCHOR;
        }

        public boolean isFacade() {
            return kind == Kind.FACADE;
        }

        // The item this attachment drops as: an anchor, or a facade carrying its target.
        public ItemStack toItem() {
            return switch (kind) {
                case NONE -> ItemStack.EMPTY;
                case ANCHOR -> new ItemStack(ModItems.CABLE_ANCHOR.get());
                case FACADE -> {
                    ItemStack stack = new ItemStack(ModItems.CABLE_FACADE.get());
                    if (target != null) {
                        stack.set(ModDataComponents.FACADE_TARGET.get(), target);
                    }
                    yield stack;
                }
            };
        }
    }

    public static final CableAttachments EMPTY = new CableAttachments(new Attachment[] {
            Attachment.NONE, Attachment.NONE, Attachment.NONE, Attachment.NONE, Attachment.NONE, Attachment.NONE });

    // Saved as six entries in Direction order; anything else reads as no attachments.
    public static final Codec<CableAttachments> CODEC = Attachment.CODEC.listOf()
            .xmap(list -> list.size() == 6 ? new CableAttachments(list.toArray(Attachment[]::new)) : EMPTY, CableAttachments::list);

    private final Attachment[] sides;

    private CableAttachments(Attachment[] sides) {
        this.sides = sides;
    }

    public Attachment get(Direction side) {
        return sides[side.ordinal()];
    }

    public CableAttachments with(Direction side, Attachment attachment) {
        Attachment[] copy = sides.clone();
        copy[side.ordinal()] = attachment;
        return new CableAttachments(copy);
    }

    public boolean isEmpty() {
        for (Attachment attachment : sides) {
            if (attachment.kind() != Kind.NONE) {
                return false;
            }
        }
        return true;
    }

    public boolean hasAnchor() {
        for (Attachment attachment : sides) {
            if (attachment.isAnchor()) {
                return true;
            }
        }
        return false;
    }

    public boolean anchored(Direction side) {
        return get(side).isAnchor();
    }

    public boolean facade(Direction side) {
        return get(side).isFacade();
    }

    // The kinds on all six sides as one number (0 to 728), for caching shapes.
    public int shapeKey() {
        int key = 0;
        for (Attachment attachment : sides) {
            key = key * 3 + attachment.kind().ordinal();
        }
        return key;
    }

    public List<ItemStack> drops() {
        List<ItemStack> drops = new ArrayList<>();
        for (Attachment attachment : sides) {
            if (attachment.kind() != Kind.NONE) {
                drops.add(attachment.toItem());
            }
        }
        return drops;
    }

    private List<Attachment> list() {
        return List.of(sides);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CableAttachments attachments && Arrays.equals(sides, attachments.sides);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(sides);
    }
}
