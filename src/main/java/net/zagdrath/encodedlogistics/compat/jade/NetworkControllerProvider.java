/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.compat.jade;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkSnapshot;
import net.zagdrath.encodedlogistics.network.NetworkStatus;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.StreamServerDataProvider;
import snownee.jade.api.config.IPluginConfig;

// Jade for the Network Controller: "Online", "0 / 32 channels", "3x3x3 frame, 20 blocks".
public enum NetworkControllerProvider implements StreamServerDataProvider<BlockAccessor, NetworkControllerProvider.Data> {
    INSTANCE;

    public static final Identifier UID = EncodedLogistics.id("network_controller");

    public record Data(int status, int channelsUsed, int channelCapacity, int sizeX, int sizeY, int sizeZ, int blocks) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Data> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT, Data::status,
                ByteBufCodecs.VAR_INT, Data::channelsUsed,
                ByteBufCodecs.VAR_INT, Data::channelCapacity,
                ByteBufCodecs.VAR_INT, Data::sizeX,
                ByteBufCodecs.VAR_INT, Data::sizeY,
                ByteBufCodecs.VAR_INT, Data::sizeZ,
                ByteBufCodecs.VAR_INT, Data::blocks,
                Data::new);
    }

    @Override
    public @Nullable Data streamData(BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof NetworkControllerBlockEntity controller) || !(accessor.getLevel() instanceof ServerLevel level)) {
            return null;
        }
        NetworkSnapshot snapshot = ControllerStructures.get(level).snapshot(controller.getStructureId());
        return new Data(snapshot.status().ordinal(), snapshot.channelsUsed(), snapshot.channelCapacity(), snapshot.sizeX(), snapshot.sizeY(),
                snapshot.sizeZ(), snapshot.blocks());
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, Data> streamCodec() {
        return Data.STREAM_CODEC;
    }

    @Override
    public Identifier getUid() {
        return UID;
    }

    public enum Client implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            NetworkControllerProvider.INSTANCE.decodeFromData(accessor).ifPresent(data -> {
                NetworkStatus status = NetworkStatus.byId(data.status());
                ChatFormatting color = status.isError() ? ChatFormatting.RED : status == NetworkStatus.ONLINE ? ChatFormatting.GREEN : ChatFormatting.GOLD;
                tooltip.add(status.description().copy().withStyle(color));
                tooltip.add(Component.translatable("gui.encodedlogistics.channels", data.channelsUsed(), data.channelCapacity())
                        .withStyle(ChatFormatting.GRAY));
                if (status != NetworkStatus.INVALID_SHAPE && status != NetworkStatus.TOO_LARGE) {
                    tooltip.add((data.blocks() == 1 ? Component.translatable("gui.encodedlogistics.structure.single")
                            : Component.translatable("gui.encodedlogistics.structure.value", data.sizeX(), data.sizeY(), data.sizeZ(), data.blocks()))
                            .withStyle(ChatFormatting.GRAY));
                }
            });
        }

        @Override
        public Identifier getUid() {
            return UID;
        }
    }
}
