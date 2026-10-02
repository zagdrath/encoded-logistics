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
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;

// What's mounted on each side of a cable: nothing, a Cable Anchor (the side never connects), a Cable Facade (a panel
// covering the side, copying the look of its target block; no target is a blank facade) or a part (a terminal, port,
// tap or sensor facing out of that side; the side doesn't connect either). Immutable; one per side, by Direction
// ordinal. The parts' own state (filters, modules, settings) lives in the block entity's CableParts. A part host (a part
// on a block face, no cable) uses the same, with its part on the side toward the block it's mounted on.
public final class CableAttachments {
    public enum Kind implements StringRepresentable {
        NONE("none"),
        ANCHOR("anchor"),
        FACADE("facade"),
        PART("part");

        private final String name;

        Kind(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public record Attachment(Kind kind, @Nullable BlockState target, @Nullable PartType part) {
        public static final Attachment NONE = new Attachment(Kind.NONE, null, null);
        public static final Attachment ANCHOR = new Attachment(Kind.ANCHOR, null, null);

        // Phase 1 saved terminals as kind "terminal"; they read back as Access Terminal parts.
        private record Saved(String kind, Optional<BlockState> target, Optional<PartType> part) {}

        static final Codec<Attachment> CODEC = RecordCodecBuilder.<Saved>create(i -> i.group(
                Codec.STRING.fieldOf("kind").forGetter(Saved::kind),
                BlockState.CODEC.optionalFieldOf("target").forGetter(Saved::target),
                PartType.CODEC.optionalFieldOf("part").forGetter(Saved::part))
                .apply(i, Saved::new))
                .xmap(Attachment::fromSaved, a -> new Saved(a.kind().getSerializedName(), Optional.ofNullable(a.target()), Optional.ofNullable(a.part())));

        private static Attachment fromSaved(Saved saved) {
            return switch (saved.kind()) {
                case "anchor" -> ANCHOR;
                case "facade" -> facade(saved.target().orElse(null));
                case "terminal" -> part(PartType.ACCESS_TERMINAL);
                case "part" -> saved.part().map(Attachment::part).orElse(NONE);
                default -> NONE;
            };
        }

        public static Attachment facade(@Nullable BlockState target) {
            return new Attachment(Kind.FACADE, target, null);
        }

        public static Attachment part(PartType part) {
            return new Attachment(Kind.PART, null, part);
        }

        public boolean isAnchor() {
            return kind == Kind.ANCHOR;
        }

        public boolean isFacade() {
            return kind == Kind.FACADE;
        }

        public boolean isPart() {
            return kind == Kind.PART;
        }

        // Anchors and parts keep their side from connecting.
        public boolean blocksConnection() {
            return kind == Kind.ANCHOR || kind == Kind.PART;
        }

        // The item this attachment drops as: an anchor, a facade carrying its target, or the part's item (a part's
        // contents drop separately, from its CablePart).
        public ItemStack toItem() {
            return switch (kind) {
                case NONE -> ItemStack.EMPTY;
                case ANCHOR -> new ItemStack(ModItems.CABLE_ANCHOR.get());
                case PART -> part != null ? new ItemStack(part.item()) : ItemStack.EMPTY;
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

    // An anchor or a part anywhere: the cable sits on its junction cube.
    public boolean hasMountedPart() {
        for (Attachment attachment : sides) {
            if (attachment.blocksConnection()) {
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

    // The part on a side, or null.
    public @Nullable PartType part(Direction side) {
        return get(side).part();
    }

    public boolean hasParts() {
        for (Attachment attachment : sides) {
            if (attachment.isPart()) {
                return true;
            }
        }
        return false;
    }

    // The side doesn't connect: an anchor or a part is on it.
    public boolean blocksConnection(Direction side) {
        return get(side).blocksConnection();
    }

    // The kinds (and part types) on all six sides as one number, for caching shapes.
    public long shapeKey() {
        long key = 0;
        for (Attachment attachment : sides) {
            int code = attachment.isPart() ? 3 + attachment.part().ordinal() : attachment.kind().ordinal();
            key = key * 16 + code;
        }
        return key;
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

    // Every attachment's item (not the parts' contents).
    public List<ItemStack> drops() {
        List<ItemStack> drops = new ArrayList<>();
        for (Attachment attachment : sides) {
            if (attachment.kind() != Kind.NONE) {
                drops.add(attachment.toItem());
            }
        }
        return drops;
    }
}
