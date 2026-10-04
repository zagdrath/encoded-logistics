/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.compat.jade;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.block.SegmentIsolatorBlock;
import net.zagdrath.encodedlogistics.blockentity.DriveBayBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.LithographyPressBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.PowerInletBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NodePos;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// Jade for the power and cable infrastructure and the Phase 1 machines. The Capacitor Bank and the Lithography Press's
// energy need nothing extra: Jade's energy bar reads their capability.
public final class InfrastructureProviders {
    private InfrastructureProviders() {}

    // Power Inlet: "Receiving 480 FE/t" (averaged over the last second).
    public enum PowerInlet implements StreamServerDataProvider<BlockAccessor, Double> {
        INSTANCE;

        public static final Identifier UID = EncodedLogistics.id("power_inlet");

        @Override
        public @Nullable Double streamData(BlockAccessor accessor) {
            return accessor.getBlockEntity() instanceof PowerInletBlockEntity inlet ? inlet.averageRate() : null;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Double> streamCodec() {
            return ByteBufCodecs.DOUBLE.cast();
        }

        @Override
        public Identifier getUid() {
            return UID;
        }

        public enum Client implements IBlockComponentProvider {
            INSTANCE;

            @Override
            public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
                PowerInlet.INSTANCE.decodeFromData(accessor).ifPresent(rate -> tooltip.add(Component.translatable(
                        "jade.encodedlogistics.power_inlet.rate", String.format(Locale.ROOT, "%,d", Math.round(rate)))
                        .withStyle(rate > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
            }

            @Override
            public Identifier getUid() {
                return UID;
            }
        }
    }

    // Lithography Press: "Exposure 45%" while it's etching.
    public enum LithographyPress implements StreamServerDataProvider<BlockAccessor, Integer> {
        INSTANCE;

        public static final Identifier UID = EncodedLogistics.id("lithography_press");

        @Override
        public @Nullable Integer streamData(BlockAccessor accessor) {
            return accessor.getBlockEntity() instanceof LithographyPressBlockEntity press ? Math.round(press.progress() * 100) : null;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, Integer> streamCodec() {
            return ByteBufCodecs.VAR_INT.cast();
        }

        @Override
        public Identifier getUid() {
            return UID;
        }

        public enum Client implements IBlockComponentProvider {
            INSTANCE;

            @Override
            public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
                LithographyPress.INSTANCE.decodeFromData(accessor).filter(percent -> percent > 0).ifPresent(percent -> tooltip.add(
                        Component.translatable("jade.encodedlogistics.lithography_press.progress", percent).withStyle(ChatFormatting.LIGHT_PURPLE)));
            }

            @Override
            public Identifier getUid() {
                return UID;
            }
        }
    }

    // Drive Bay: "4 / 10 drives", from the drives the client already has.
    public enum DriveBay implements IBlockComponentProvider {
        INSTANCE;

        public static final Identifier UID = EncodedLogistics.id("drive_bay");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            if (accessor.getBlockEntity() instanceof DriveBayBlockEntity bay) {
                tooltip.add(Component.translatable("jade.encodedlogistics.drive_bay.drives", bay.driveCount()).withStyle(ChatFormatting.GRAY));
            }
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }

    // Network cables: "Lanes 8 / 8" - what runs through it toward the controller and what it carries; red when full
    // (whatever's beyond it is missing lanes).
    public enum Cable implements StreamServerDataProvider<BlockAccessor, int[]> {
        INSTANCE;

        public static final Identifier UID = EncodedLogistics.id("cable_lanes");

        @Override
        public int @Nullable [] streamData(BlockAccessor accessor) {
            return accessor.getLevel().getServer() != null
                    ? ControllerStructures.cableLanes(accessor.getLevel().getServer(), NodePos.of(accessor.getLevel().dimension(), accessor.getPosition()))
                    : null;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, int[]> streamCodec() {
            return ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(2)).map(list -> new int[] { list.get(0), list.get(1) },
                    lanes -> List.of(lanes[0], lanes[1])).cast();
        }

        @Override
        public Identifier getUid() {
            return UID;
        }

        public enum Client implements IBlockComponentProvider {
            INSTANCE;

            @Override
            public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
                Cable.INSTANCE.decodeFromData(accessor).ifPresent(lanes -> tooltip.add(Component.translatable("jade.encodedlogistics.cable.lanes",
                        lanes[0], lanes[1]).withStyle(lanes[0] >= lanes[1] ? ChatFormatting.RED : lanes[0] > 0 ? ChatFormatting.GREEN : ChatFormatting.GRAY)));
            }

            @Override
            public Identifier getUid() {
                return UID;
            }
        }
    }

    // Segment Isolator: "Isolating two segments" or "Idle", from its blockstate.
    public enum SegmentIsolator implements IBlockComponentProvider {
        INSTANCE;

        public static final Identifier UID = EncodedLogistics.id("segment_isolator");

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            boolean active = accessor.getBlockState().getValue(SegmentIsolatorBlock.ACTIVE);
            tooltip.add(Component.translatable(active ? "jade.encodedlogistics.segment_isolator.active" : "jade.encodedlogistics.segment_isolator.idle")
                    .withStyle(active ? ChatFormatting.GOLD : ChatFormatting.GRAY));
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
