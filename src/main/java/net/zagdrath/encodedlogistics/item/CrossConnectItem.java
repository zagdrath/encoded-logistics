/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.encodedlogistics.block.ExchangeSwitchBlock;
import net.zagdrath.encodedlogistics.blockentity.ExchangeSwitchBlockEntity;
import net.zagdrath.encodedlogistics.network.routing.RoutingDomain;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;

// The Cross-Connect (the routing handoff 2): a patch lead from a Router to an Exchange Switch. Used on a Router's unit in
// an open rack, it records that Router (RouterDevice#useItem; BUILD on its network); then used on an Exchange Switch,
// the Router joins it there (or asks to, when the owner approves members), using up one. Sneak-use in the air clears it.
public class CrossConnectItem extends Item {
    private static final String KEY = "cross_connect";

    public CrossConnectItem(Properties properties) {
        super(properties);
    }

    public static void record(ItemStack stack, RoutingDomain.RouterId router, String name) {
        CompoundTag tag = new CompoundTag();
        tag.putString("dimension", router.dimension().identifier().toString());
        tag.putLong("rack", router.rack().asLong());
        tag.putInt("u", router.u());
        tag.putString("name", name);
        CompoundTag root = new CompoundTag();
        root.put(KEY, tag);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
    }

    public static RoutingDomain.@Nullable RouterId router(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag tag = data.copyTag().getCompoundOrEmpty(KEY);
        Identifier dimension = Identifier.tryParse(tag.getStringOr("dimension", ""));
        if (dimension == null || tag.isEmpty()) {
            return null;
        }
        return new RoutingDomain.RouterId(ResourceKey.create(Registries.DIMENSION, dimension), BlockPos.of(tag.getLongOr("rack", 0)),
                tag.getIntOr("u", 1));
    }

    private static String routerName(ItemStack stack) {
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        return data == null ? "" : data.copyTag().getCompoundOrEmpty(KEY).getStringOr("name", "");
    }

    @Override
    public Component getName(ItemStack stack) {
        String name = routerName(stack);
        return name.isEmpty() ? super.getName(stack) : Component.translatable("item.encodedlogistics.cross_connect.recorded", super.getName(stack), name);
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return router(stack) != null;
    }

    // On an Exchange Switch: the recorded Router joins it.
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        BlockState state = level.getBlockState(context.getClickedPos());
        if (!(state.getBlock() instanceof ExchangeSwitchBlock)) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel) || !(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos lower = ExchangeSwitchBlock.lower(context.getClickedPos(), state);
        if (!(level.getBlockEntity(lower) instanceof ExchangeSwitchBlockEntity exchange)) {
            return InteractionResult.SUCCESS;
        }
        RoutingDomain.RouterId id = router(stack);
        RouterDevice router = id != null ? RouterDevice.at(serverLevel.getServer(), id) : null;
        if (router == null) {
            player.sendOverlayMessage(Component.translatable(id == null ? "message.encodedlogistics.cross_connect.empty"
                    : "message.encodedlogistics.cross_connect.not_loaded"));
            return InteractionResult.SUCCESS;
        }
        if (router.rack() == null || !NetworkAccess.check(serverLevel.getServer(), router.rack().network(router), player, RackPermission.BUILD)) {
            return InteractionResult.SUCCESS;
        }
        ExchangeSwitchBlockEntity.JoinResult result = exchange.join(router, serverLevel.getServer(), player);
        player.sendOverlayMessage(Component.translatable("message.encodedlogistics.cross_connect." + result.name().toLowerCase(Locale.ROOT),
                router.deviceName(), exchange.exchangeName()));
        if ((result == ExchangeSwitchBlockEntity.JoinResult.JOINED || result == ExchangeSwitchBlockEntity.JoinResult.REQUESTED)
                && !player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResult.SUCCESS;
    }

    // Sneak-use in the air forgets the Router.
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || router(stack) == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            stack.remove(DataComponents.CUSTOM_DATA);
            player.sendOverlayMessage(Component.translatable("message.encodedlogistics.cross_connect.cleared"));
        }
        return InteractionResult.SUCCESS;
    }
}
