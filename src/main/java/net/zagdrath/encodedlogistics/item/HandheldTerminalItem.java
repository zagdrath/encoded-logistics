/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.CapacitorBankBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RelayAntennaBlockEntity;
import net.zagdrath.encodedlogistics.menu.HandheldTerminalMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;

// The Handheld Terminal: an Access Terminal that works anywhere one of its network's Relay Antennas covers. Use it on a
// Relay Antenna or a Network Controller to link it to that network; use it in the air to open the terminal (linked, in
// range and charged). Its battery (handheldCapacity FE) pays handheldDrainPerSecond while the screen is open and
// handheldEnergyPerItem per item moved; it charges in any FE charger, or held against a Capacitor Bank (use and hold) at
// handheldChargeRate FE/t. Its icon shows whether it's in range (HANDHELD_LINK_STATE, updated every second in an
// inventory).
public class HandheldTerminalItem extends Item {
    private static final int STATE_INTERVAL = 20, CHARGE_DURATION = 72_000;

    public HandheldTerminalItem(Item.Properties properties) {
        super(properties);
    }

    // The network a Handheld Terminal is linked to, or null (also for anything else).
    public static NetworkIndex.@Nullable NetworkRef network(ItemStack stack) {
        return stack.getItem() instanceof HandheldTerminalItem ? stack.get(ModDataComponents.HANDHELD_NETWORK.get()) : null;
    }

    public static int energy(ItemStack stack) {
        return stack.getOrDefault(ModDataComponents.ENERGY.get(), 0);
    }

    public static int capacity() {
        return Config.HANDHELD_CAPACITY.getAsInt();
    }

    public static void setEnergy(ItemStack stack, int energy) {
        stack.set(ModDataComponents.ENERGY.get(), Math.clamp(energy, 0, capacity()));
    }

    // The nearest (relative to its range) online Relay Antenna of the network covering a point in a level, or null.
    public static @Nullable RelayAntennaBlockEntity access(ServerLevel level, Vec3 point, NetworkIndex.@Nullable NetworkRef network) {
        RelayAntennaBlockEntity best = null;
        double bestRatio = Double.MAX_VALUE;
        for (RelayAntennaBlockEntity relay : ControllerStructures.relays(level.getServer(), network)) {
            if (relay.getLevel() != level || !relay.covers(point)) {
                continue;
            }
            double ratio = relay.distanceTo(point) / Math.max(1, relay.range());
            if (ratio < bestRatio) {
                bestRatio = ratio;
                best = relay;
            }
        }
        return best;
    }

    // Signal bars (0-4) for a point covered by an antenna: four near it, one at the edge of its range.
    public static int signal(@Nullable RelayAntennaBlockEntity relay, Vec3 point) {
        if (relay == null) {
            return 0;
        }
        double ratio = relay.distanceTo(point) / Math.max(1, relay.range());
        return ratio <= 0.25 ? 4 : ratio <= 0.5 ? 3 : ratio <= 0.75 ? 2 : 1;
    }

    public static HandheldLinkState state(ServerLevel level, Vec3 point, ItemStack stack) {
        NetworkIndex.NetworkRef network = network(stack);
        if (network == null) {
            return HandheldLinkState.UNLINKED;
        }
        return access(level, point, network) != null ? HandheldLinkState.LINKED : HandheldLinkState.OUT_OF_RANGE;
    }

    // --- Linking and charging ---

    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        var blockEntity = level.getBlockEntity(context.getClickedPos());
        if (player == null || !(blockEntity instanceof RelayAntennaBlockEntity || blockEntity instanceof NetworkControllerBlockEntity
                || blockEntity instanceof CapacitorBankBlockEntity)) {
            return InteractionResult.PASS;
        }
        if (blockEntity instanceof CapacitorBankBlockEntity) {
            if (energy(stack) >= capacity()) {
                return InteractionResult.PASS;
            }
            player.startUsingItem(context.getHand());
            return InteractionResult.CONSUME;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        NetworkIndex.NetworkRef network = blockEntity instanceof NetworkControllerBlockEntity controller
                ? new NetworkIndex.NetworkRef(serverLevel.dimension(), controller.getStructureId())
                : ControllerStructures.networkOf(serverLevel, context.getClickedPos());
        if (network == null || network.id() <= 0) {
            player.sendOverlayMessage(Component.translatable("message.encodedlogistics.handheld.no_network"));
            return InteractionResult.SUCCESS;
        }
        stack.set(ModDataComponents.HANDHELD_NETWORK.get(), network);
        stack.set(ModDataComponents.HANDHELD_LINK_STATE.get(), state(serverLevel, player.position(), stack));
        player.sendOverlayMessage(Component.translatable("message.encodedlogistics.handheld.linked", network.id()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity user) {
        return CHARGE_DURATION;
    }

    // Held against a Capacitor Bank: takes up to handheldChargeRate FE a tick from it, until full or looking away.
    @Override
    public void onUseTick(Level level, LivingEntity user, ItemStack stack, int ticksRemaining) {
        if (level.isClientSide() || !(user instanceof Player player)) {
            return;
        }
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        int room = capacity() - energy(stack);
        if (room <= 0 || !(hit instanceof BlockHitResult blockHit)
                || !(level.getBlockEntity(blockHit.getBlockPos()) instanceof CapacitorBankBlockEntity bank)) {
            user.stopUsingItem();
            return;
        }
        int got = bank.drain(Math.min(room, Config.HANDHELD_CHARGE_RATE.getAsInt()));
        if (got > 0) {
            setEnergy(stack, energy(stack) + got);
        }
    }

    // --- Opening ---

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer serverPlayer) || !(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        NetworkIndex.NetworkRef network = network(stack);
        if (network == null) {
            player.sendOverlayMessage(Component.translatable("gui.encodedlogistics.handheld.unlinked"));
            return InteractionResult.FAIL;
        }
        if (energy(stack) <= 0) {
            player.sendOverlayMessage(Component.translatable("message.encodedlogistics.handheld.empty"));
            return InteractionResult.FAIL;
        }
        RelayAntennaBlockEntity relay = access(serverLevel, player.position(), network);
        stack.set(ModDataComponents.HANDHELD_LINK_STATE.get(), relay != null ? HandheldLinkState.LINKED : HandheldLinkState.OUT_OF_RANGE);
        if (relay == null) {
            player.sendOverlayMessage(Component.translatable("gui.encodedlogistics.handheld.out_of_range"));
            return InteractionResult.FAIL;
        }
        int slot = hand == InteractionHand.OFF_HAND ? HandheldTerminalMenu.OFFHAND_SLOT : player.getInventory().getSelectedSlot();
        HandheldTerminalMenu.open(serverPlayer, slot, relay.getBlockPos());
        return InteractionResult.SUCCESS;
    }

    // Keeps the icon's link state current while it's carried.
    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity owner, @Nullable EquipmentSlot slot) {
        if (level.getGameTime() % STATE_INTERVAL != 0) {
            return;
        }
        HandheldLinkState state = state(level, owner.position(), stack);
        if (stack.get(ModDataComponents.HANDHELD_LINK_STATE.get()) != state) {
            stack.set(ModDataComponents.HANDHELD_LINK_STATE.get(), state);
        }
    }

    // --- Battery bar ---

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return energy(stack) < capacity();
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * energy(stack) / capacity());
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb(0.43F, 0.85F, 0.85F);
    }
}
