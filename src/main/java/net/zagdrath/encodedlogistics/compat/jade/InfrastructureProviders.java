/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.compat.jade;

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
import net.zagdrath.encodedlogistics.blockentity.PowerInletBlockEntity;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// Jade for the power and cable infrastructure. The Capacitor Bank needs nothing extra: Jade's energy bar reads its
// capability.
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
