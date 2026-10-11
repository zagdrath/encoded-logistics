/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.net;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.LongTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.CustomClickActionEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.client.ClientRenderHooks;
import net.zagdrath.encodedlogistics.item.RackElevationItem;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackElevations;
import net.zagdrath.encodedlogistics.rack.RackTemplate;

// A Rack Elevation's traffic. The editor is local to the item: each change is one small Edit (place, remove, name, a
// toggle), which the server checks against the rack's rules and writes on the held item. The materials list asks
// (MaterialsQuery) and gets the server's counts (Materials: the player's inventory and shulker boxes, and the rack
// network's storage when that's on and allowed); so does the [show] link in an apply report (CustomClickActionEvent),
// which opens the list. While a written elevation is held and a rack looked at, the client asks for the plan against it
// (PreviewQuery, every few ticks) and gets each planned device's fate and the totals (Preview) for the overlay and popup.
public final class RackElevationPayloads {
    private static final double REACH_SQR = 16 * 16;

    private RackElevationPayloads() {}

    private static final StreamCodec<RegistryFriendlyByteBuf, InteractionHand> HAND = ByteBufCodecs.BOOL.map(
            offhand -> offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, hand -> hand == InteractionHand.OFF_HAND)
            .cast();

    // The rack at pos the player may ask about (near, loaded), or null.
    private static @Nullable RackBlockEntity rack(ServerPlayer player, long pos) {
        if (pos == Long.MIN_VALUE) {
            return null;
        }
        BlockPos at = BlockPos.of(pos);
        return player.distanceToSqr(Vec3.atCenterOf(at)) <= REACH_SQR && player.level().isLoaded(at)
                && player.level().getBlockEntity(at) instanceof RackBlockEntity rack ? rack : null;
    }

    // --- Editing ---

    public record Edit(InteractionHand hand, int op, int u, String device, String text) implements CustomPacketPayload {
        public static final int PLACE = 0, REMOVE = 1, NAME = 2, CONTENTS = 3, SETTINGS = 4, NETWORK = 5;
        public static final Type<Edit> TYPE = new Type<>(EncodedLogistics.id("rack_elevation_edit"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Edit> STREAM_CODEC = StreamCodec.composite(
                HAND, Edit::hand,
                ByteBufCodecs.VAR_INT, Edit::op,
                ByteBufCodecs.VAR_INT, Edit::u,
                ByteBufCodecs.stringUtf8(256), Edit::device,
                ByteBufCodecs.stringUtf8(RackTemplate.MAX_NAME), Edit::text,
                Edit::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        // A toggle's new value goes in u (0 off, 1 on).
        public static Edit toggle(InteractionHand hand, int op, boolean on) {
            return new Edit(hand, op, on ? 1 : 0, "", "");
        }

        static void handle(Edit edit, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            ItemStack stack = player.getItemInHand(edit.hand());
            if (!(stack.getItem() instanceof RackElevationItem)) {
                return;
            }
            RackTemplate template = apply(RackTemplate.orEmpty(stack), edit);
            if (template != null) {
                RackElevationItem.writeHeld(player, stack, template);
            }
        }

        // The plan with the edit made, or null when it breaks the rack's rules (refused).
        public static @Nullable RackTemplate apply(RackTemplate template, Edit edit) {
            boolean on = edit.u() != 0;
            return switch (edit.op()) {
                case PLACE -> {
                    Identifier id = Identifier.tryParse(edit.device());
                    RackDeviceType type = id != null ? RackDeviceType.byId(id) : null;
                    yield type != null ? template.place(type, edit.u()) : null;
                }
                case REMOVE -> template.remove(edit.u());
                case NAME -> template.withName(edit.text().strip());
                case CONTENTS -> template.withContents(on);
                case SETTINGS -> template.withSettings(on);
                case NETWORK -> template.withNetworkStorage(on);
                default -> null;
            };
        }
    }

    // --- The materials list ---

    public record Line(ItemStack item, long have, int need) {
        static final StreamCodec<RegistryFriendlyByteBuf, Line> STREAM_CODEC = StreamCodec.composite(
                ItemStack.STREAM_CODEC, Line::item,
                ByteBufCodecs.VAR_LONG, Line::have,
                ByteBufCodecs.VAR_INT, Line::need,
                Line::new);

        public boolean enough() {
            return have >= need;
        }
    }

    // The client asks for the materials of the elevation in a hand, counted against a rack (Long.MIN_VALUE: none).
    public record MaterialsQuery(InteractionHand hand, long rack) implements CustomPacketPayload {
        public static final Type<MaterialsQuery> TYPE = new Type<>(EncodedLogistics.id("rack_elevation_materials_query"));

        public static final StreamCodec<RegistryFriendlyByteBuf, MaterialsQuery> STREAM_CODEC = StreamCodec.composite(
                HAND, MaterialsQuery::hand,
                ByteBufCodecs.VAR_LONG, MaterialsQuery::rack,
                MaterialsQuery::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(MaterialsQuery query, IPayloadContext context) {
            if (context.player() instanceof ServerPlayer player && player.getItemInHand(query.hand()).getItem() instanceof RackElevationItem) {
                send(player, query.hand(), query.rack(), false);
            }
        }
    }

    private static void send(ServerPlayer player, InteractionHand hand, long rackPos, boolean open) {
        RackTemplate template = RackTemplate.orEmpty(player.getItemInHand(hand));
        RackBlockEntity rack = rack(player, rackPos);
        RackElevations.Plan plan = RackElevations.plan(player, template, rack);
        List<Line> lines = new ArrayList<>();
        for (RackElevations.Material material : plan.materials()) {
            lines.add(new Line(material.item(), material.have(), material.need()));
        }
        PacketDistributor.sendToPlayer(player, new Materials(rack != null ? rackPos : Long.MIN_VALUE, lines, plan.missing(), open));
    }

    // The [show] link in an apply report: the list, opened, counted against that rack.
    public static void onCustomClick(CustomClickActionEvent event) {
        if (!event.getIdentifier().equals(RackElevations.SHOW_MATERIALS) || event.getPlayer() == null
                || !(event.getPayload() instanceof LongTag pos)) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        for (InteractionHand hand : InteractionHand.values()) {
            if (RackTemplate.of(player.getItemInHand(hand)) != null) {
                send(player, hand, pos.longValue(), true);
                return;
            }
        }
    }

    public record Materials(long rack, List<Line> lines, long missing, boolean open) implements CustomPacketPayload {
        public static final Type<Materials> TYPE = new Type<>(EncodedLogistics.id("rack_elevation_materials"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Materials> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_LONG, Materials::rack,
                Line.STREAM_CODEC.apply(ByteBufCodecs.list(256)), Materials::lines,
                ByteBufCodecs.VAR_LONG, Materials::missing,
                ByteBufCodecs.BOOL, Materials::open,
                Materials::new);

        // Client: the latest answer.
        private static volatile @Nullable Materials latest;

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static @Nullable Materials latest() {
            return latest;
        }

        static void handle(Materials materials, IPayloadContext context) {
            latest = materials;
            if (materials.open()) {
                ClientRenderHooks.openRackElevationMaterials(materials.rack());
            }
        }
    }

    // --- The preview ---

    public record PreviewQuery(BlockPos rack, InteractionHand hand) implements CustomPacketPayload {
        public static final Type<PreviewQuery> TYPE = new Type<>(EncodedLogistics.id("rack_elevation_preview_query"));

        public static final StreamCodec<RegistryFriendlyByteBuf, PreviewQuery> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, PreviewQuery::rack,
                HAND, PreviewQuery::hand,
                PreviewQuery::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        static void handle(PreviewQuery query, IPayloadContext context) {
            if (!(context.player() instanceof ServerPlayer player)) {
                return;
            }
            RackTemplate template = RackTemplate.of(player.getItemInHand(query.hand()));
            RackBlockEntity rack = RackElevationPayloads.rack(player, query.rack().asLong());
            if (template == null || rack == null) {
                return;
            }
            RackElevations.Plan plan = RackElevations.plan(player, template, rack);
            List<Step> steps = new ArrayList<>();
            Component firstSkip = Component.empty();
            List<Component> missing = new ArrayList<>();
            for (RackElevations.Step step : plan.steps()) {
                steps.add(new Step(step.planned().u(), step.planned().size(), step.state().ordinal()));
                if (step.state() == RackElevations.State.SKIP && firstSkip.getString().isEmpty()) {
                    firstSkip = Component.translatable("message.encodedlogistics.rack_elevation.skipped", step.planned().u(),
                            step.blocker() != null ? step.blocker() : Component.empty());
                }
                RackDeviceType type = step.planned().deviceType();
                if (step.state() == RackElevations.State.MISSING && type != null) {
                    missing.add(new ItemStack(type.item()).getHoverName());
                }
            }
            PacketDistributor.sendToPlayer(player, new Preview(query.rack(), template.name(), steps, plan.count(RackElevations.State.FILL),
                    plan.count(RackElevations.State.SKIP), firstSkip, missing));
        }
    }

    public record Step(int u, int size, int state) {
        static final StreamCodec<RegistryFriendlyByteBuf, Step> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Step::u,
                ByteBufCodecs.VAR_INT, Step::size,
                ByteBufCodecs.VAR_INT, Step::state,
                Step::new);

        public RackElevations.State fate() {
            return RackElevations.State.byId(state);
        }
    }

    public record Preview(BlockPos rack, String name, List<Step> steps, int fill, int skip, Component firstSkip, List<Component> missing)
            implements CustomPacketPayload {
        public static final Type<Preview> TYPE = new Type<>(EncodedLogistics.id("rack_elevation_preview"));

        public static final StreamCodec<RegistryFriendlyByteBuf, Preview> STREAM_CODEC = StreamCodec.composite(
                BlockPos.STREAM_CODEC, Preview::rack,
                ByteBufCodecs.stringUtf8(RackTemplate.MAX_NAME), Preview::name,
                Step.STREAM_CODEC.apply(ByteBufCodecs.list(64)), Preview::steps,
                ByteBufCodecs.VAR_INT, Preview::fill,
                ByteBufCodecs.VAR_INT, Preview::skip,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Preview::firstSkip,
                ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list(64)), Preview::missing,
                Preview::new);

        // Client: the latest answers, by rack.
        private static final Map<BlockPos, Preview> ANSWERS = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75F, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<BlockPos, Preview> eldest) {
                return size() > 16;
            }
        });

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        public static @Nullable Preview forRack(BlockPos rack) {
            return ANSWERS.get(rack);
        }

        public static void clear() {
            ANSWERS.clear();
        }

        static void handle(Preview preview, IPayloadContext context) {
            ANSWERS.put(preview.rack(), preview);
        }
    }
}
