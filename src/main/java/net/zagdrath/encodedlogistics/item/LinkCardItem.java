/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.item;

import java.util.Locale;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.block.PartHostBlock;
import net.zagdrath.encodedlogistics.block.SegmentIsolatorBlock;
import net.zagdrath.encodedlogistics.block.ServerRackBlock;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkBridgeBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.machine.MachineBridge;
import net.zagdrath.encodedlogistics.machine.MachineBridges;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.net.MachineBridgesPayload;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;
import net.zagdrath.encodedlogistics.network.NetworkNodeHost;
import net.zagdrath.encodedlogistics.part.PointToPointPart;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.rack.RackTargeting;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;
import net.zagdrath.encodedlogistics.rack.device.SwitchDevice;
import net.zagdrath.encodedlogistics.rack.device.WirelessControllerDevice;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.wireless.Wireless;
import net.zagdrath.encodedlogistics.wireless.WirelessClient;

// The Link Card pairs Network Bridges and Point-to-Point Link endpoints. Sneak-use it on one to store its address (a
// card taken off a stack of blank ones gets it); use it on the partner to pair the two - Bridge with Bridge, or a
// Point-to-Point Link input with an output carrying the same thing. The card keeps the address, so more outputs can be
// paired to the same input. Sneak-use in the air clears it. Blank cards stack; written ones don't.
//
// In a Server Rack: sneak-use it on a Segment Isolator to store the segment on the half clicked, then use it on a switch's
// unit in an open rack to let the rack's devices serve that segment (again to unlink it); sneak-use it on any other block
// of a network to store that network, then use it on a Router's unit to link the Router to it.
//
// Wireless: use it on a Wireless Controller's unit in an open rack to take the controller, then on a Wireless Bridge or
// Wireless Port to link it to that controller. A card from another controller relinks it there.
//
// Permissions: the Firewall's build permission (rack access, for a device in a Server Rack) on the network at each end -
// checked when the card is written (the end clicked) and when it's applied (both ends; adopting a Wireless Bridge or Port
// needs build on the controller's network). Denied, nothing is written or linked and the player is told "Access denied:
// SYSNAME Firewall". Links made before stay as they are.
public class LinkCardItem extends Item {
    public LinkCardItem(Item.Properties properties) {
        super(properties);
    }

    public static @Nullable LinkAddress address(ItemStack stack) {
        return stack.get(ModDataComponents.LINK_ADDRESS.get());
    }

    // What a use landed on: a Bridge, or the Point-to-Point Link on the side looked at.
    private record Target(LinkAddress address, @Nullable NetworkBridgeBlockEntity bridge, @Nullable PointToPointPart p2p) {}

    private static @Nullable Target target(Level level, BlockPos pos, Vec3 hit) {
        if (level.getBlockEntity(pos) instanceof NetworkBridgeBlockEntity bridge) {
            return new Target(LinkAddress.bridge(GlobalPos.of(level.dimension(), pos)), bridge, null);
        }
        if (level.getBlockEntity(pos) instanceof CableBlockEntity host) {
            Direction side = level.getBlockState(pos).getBlock() instanceof PartHostBlock ? PartHostBlock.mount(host.getAttachments())
                    : NetworkCableBlock.sideAt(hit, pos);
            if (side != null && host.part(side) instanceof PointToPointPart p2p) {
                return new Target(LinkAddress.p2p(GlobalPos.of(level.dimension(), pos), side, p2p.linkType()), null, p2p);
            }
        }
        return null;
    }

    // Before the block reacts, so a Bridge or Point-to-Point Link doesn't open its screen instead.
    @Override
    public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        InteractionResult wireless = wireless(stack, context);
        if (wireless != null) {
            return wireless;
        }
        InteractionResult segment = segment(stack, context);
        if (segment != null) {
            return segment;
        }
        Target target = target(level, context.getClickedPos(), context.getClickLocation());
        if (target == null && player != null && player.isSecondaryUseActive() && onNetwork(level, context.getClickedPos())) {
            if (level instanceof ServerLevel serverLevel && NetworkAccess.guard(serverLevel, context.getClickedPos(), player, RackPermission.BUILD)) {
                store(stack, player, context.getHand(), LinkAddress.network(GlobalPos.of(level.dimension(), context.getClickedPos())));
                player.sendOverlayMessage(Component.translatable("message.encodedlogistics.link_card.network_stored"));
                level.playSound(null, context.getClickedPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.4F);
            }
            return InteractionResult.SUCCESS;
        }
        if (target == null || player == null) {
            return InteractionResult.PASS;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResult.SUCCESS;
        }
        if (!NetworkAccess.guard(serverLevel, context.getClickedPos(), player, RackPermission.BUILD)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            store(stack, player, context.getHand(), target.address());
            player.sendOverlayMessage(Component.translatable("message.encodedlogistics.link_card.stored"));
            level.playSound(null, context.getClickedPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.4F);
            return InteractionResult.SUCCESS;
        }
        LinkAddress stored = address(stack);
        if (stored == null) {
            player.sendOverlayMessage(Component.translatable("tooltip.encodedlogistics.link_card.hint"));
            return InteractionResult.SUCCESS;
        }
        // The other end's network too.
        if (!NetworkAccess.guard(serverLevel.getServer(), stored.pos(), player, RackPermission.BUILD)) {
            return InteractionResult.SUCCESS;
        }
        Component message = pair(serverLevel, stored, target);
        player.sendOverlayMessage(message);
        return InteractionResult.SUCCESS;
    }

    // Whether a block is part of a network (a controller, or a node on one); the client can't tell, so it says yes.
    private static boolean onNetwork(Level level, BlockPos pos) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return level.getBlockEntity(pos) instanceof NetworkControllerBlockEntity || level.getBlockState(pos).getBlock() instanceof NetworkNodeBlock
                    || level.getBlockEntity(pos) instanceof NetworkNodeHost;
        }
        return level.getBlockEntity(pos) instanceof NetworkControllerBlockEntity || ControllerStructures.networkOf(serverLevel, pos) != null;
    }

    // A Wireless Controller's unit in an open rack (take the controller), or a Wireless Bridge or Port, or a machine with
    // a Small Wireless Bridge on (link it to the controller on the card). Null when the use is about neither.
    private static @Nullable InteractionResult wireless(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        if (player == null) {
            return null;
        }
        if (!(level instanceof ServerLevel) && MachineBridgesPayload.has(pos)) {
            return InteractionResult.SUCCESS;
        }
        if (Wireless.clientAt(level, pos) instanceof WirelessClient client) {
            if (!(level instanceof ServerLevel serverLevel)) {
                return InteractionResult.SUCCESS;
            }
            LinkAddress stored = address(stack);
            if (stored == null || stored.kind() != LinkAddress.Kind.WIRELESS || stored.controller().isEmpty()) {
                player.sendOverlayMessage(Component.translatable("message.encodedlogistics.link_card.wireless_hint"));
                return InteractionResult.SUCCESS;
            }
            WirelessControllerDevice controller = wirelessController(serverLevel.getServer(), stored);
            if (controller == null) {
                player.sendOverlayMessage(Component.translatable("message.encodedlogistics.link_card.gone"));
                return InteractionResult.SUCCESS;
            }
            if (!NetworkAccess.guard(serverLevel.getServer(), controller.network(), player, RackPermission.BUILD)
                    || !NetworkAccess.guard(serverLevel, pos, player, RackPermission.BUILD)
                    || client instanceof MachineBridge bridge && !MachineBridges.mayLink(player, bridge)) {
                return InteractionResult.SUCCESS;
            }
            if (!controller.link(client)) {
                player.sendOverlayMessage(Component.translatable("message.encodedlogistics.handheld.no_network"));
                return InteractionResult.SUCCESS;
            }
            player.sendOverlayMessage(Component.translatable("message.encodedlogistics.link_card.wireless_linked", Wireless.name(controller)));
            level.playSound(null, pos, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5F, 1.8F);
            return InteractionResult.SUCCESS;
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof ServerRackBlock)) {
            return null;
        }
        RackBlockEntity rack = ServerRackBlock.rack(level, pos, state);
        if (rack == null || RackGeometry.face(context.getClickedFace(), state.getValue(ServerRackBlock.FACING)) != RackGeometry.Face.FRONT) {
            return null;
        }
        RackTargeting.Target target = RackTargeting.pick(rack, context.getClickedFace(), player.getEyePosition(), player.getViewVector(1.0F));
        if (target == null || !(target.device() instanceof WirelessControllerDevice controller)) {
            return null;
        }
        if (level instanceof ServerLevel serverLevel && NetworkAccess.guard(serverLevel, rack.getBlockPos(), player, RackPermission.RACK)) {
            store(stack, player, context.getHand(), LinkAddress.wireless(GlobalPos.of(level.dimension(), rack.getBlockPos()), controller.id()));
            player.sendOverlayMessage(Component.translatable("message.encodedlogistics.link_card.wireless_stored"));
            level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.4F);
        }
        return InteractionResult.SUCCESS;
    }

    // The controller a wireless card names: in the rack it was taken from (loading it), else anywhere on its network.
    private static @Nullable WirelessControllerDevice wirelessController(MinecraftServer server, LinkAddress address) {
        UUID id = address.controller().orElse(null);
        ServerLevel there = server.getLevel(address.pos().dimension());
        if (id == null || there == null) {
            return null;
        }
        there.getChunkAt(address.pos().pos());
        if (there.getBlockEntity(address.pos().pos()) instanceof RackBlockEntity rack) {
            for (RackDevice device : rack.devices()) {
                if (device instanceof WirelessControllerDevice controller && controller.id().equals(id)) {
                    return controller;
                }
            }
        }
        return null;
    }

    // A Segment Isolator (sneak-use: store the segment on that half), or a switch or Router in an open rack (use with a
    // stored segment or network: link it). Null when the use is about neither.
    private static @Nullable InteractionResult segment(ItemStack stack, UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (player == null) {
            return null;
        }
        if (state.getBlock() instanceof SegmentIsolatorBlock && player.isSecondaryUseActive()) {
            // The segment on the half clicked: its ends are usually against cables or blocks, so anywhere on the housing
            // will do, the side of its middle the click was on deciding which end.
            Direction.Axis axis = state.getValue(SegmentIsolatorBlock.AXIS);
            double along = context.getClickLocation().get(axis) - pos.get(axis) - 0.5;
            Direction side = context.getClickedFace().getAxis() == axis ? context.getClickedFace()
                    : Direction.fromAxisAndDirection(axis, along >= 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
            if (level instanceof ServerLevel serverLevel && NetworkAccess.guard(serverLevel, pos.relative(side), player, RackPermission.BUILD)) {
                store(stack, player, context.getHand(), LinkAddress.segment(GlobalPos.of(level.dimension(), pos.relative(side)), side));
                player.sendOverlayMessage(Component.translatable("message.encodedlogistics.link_card.segment_stored"));
                level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.4F);
            }
            return InteractionResult.SUCCESS;
        }
        LinkAddress stored = address(stack);
        if (!(state.getBlock() instanceof ServerRackBlock) || stored == null
                || stored.kind() != LinkAddress.Kind.SEGMENT && stored.kind() != LinkAddress.Kind.NETWORK) {
            return null;
        }
        RackBlockEntity rack = ServerRackBlock.rack(level, pos, state);
        Direction facing = state.getValue(ServerRackBlock.FACING);
        if (rack == null || RackGeometry.face(context.getClickedFace(), facing) != RackGeometry.Face.FRONT) {
            return null;
        }
        RackTargeting.Target target = RackTargeting.pick(rack, context.getClickedFace(), player.getEyePosition(), player.getViewVector(1.0F));
        if (target == null) {
            return null;
        }
        RackDevice device = target.device();
        if (device instanceof SwitchDevice && stored.kind() == LinkAddress.Kind.SEGMENT) {
            if (level instanceof ServerLevel serverLevel && NetworkAccess.guard(serverLevel, rack.getBlockPos(), player, RackPermission.RACK)
                    && NetworkAccess.guard(serverLevel.getServer(), stored.pos(), player, RackPermission.BUILD)) {
                RackBlockEntity.SegmentResult result = rack.toggleSegment(stored.pos());
                player.sendOverlayMessage(Component.translatable("message.encodedlogistics.link_card.segment_" + result.name().toLowerCase(Locale.ROOT)));
                level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.8F);
            }
            return InteractionResult.SUCCESS;
        }
        if (!(device instanceof RouterDevice router)) {
            if (level instanceof ServerLevel) {
                player.sendOverlayMessage(Component.translatable(stored.kind() == LinkAddress.Kind.SEGMENT
                        ? "message.encodedlogistics.link_card.not_switch" : "message.encodedlogistics.link_card.not_router"));
            }
            return InteractionResult.SUCCESS;
        }
        if (level instanceof ServerLevel serverLevel && NetworkAccess.guard(serverLevel, rack.getBlockPos(), player, RackPermission.RACK)
                && NetworkAccess.guard(serverLevel.getServer(), stored.pos(), player, RackPermission.BUILD)) {
            String result = switch (router.link(stored.pos())) {
                case LINKED -> "router_linked";
                case ALREADY -> "router_already";
                case FULL -> "router_full";
            };
            player.sendOverlayMessage(Component.translatable("message.encodedlogistics.link_card." + result));
            if (result.equals("router_linked")) {
                level.playSound(null, pos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.8F);
            }
        }
        return InteractionResult.SUCCESS;
    }

    private static Component pair(ServerLevel level, LinkAddress stored, Target target) {
        if (stored.kind() != target.address().kind() || stored.kind() == LinkAddress.Kind.SEGMENT || stored.kind() == LinkAddress.Kind.NETWORK) {
            return Component.translatable("message.encodedlogistics.link_card.mismatch");
        }
        if (target.bridge() != null) {
            NetworkBridgeBlockEntity other = NetworkBridgeBlockEntity.at(level.getServer(), stored.pos(), true);
            if (other == null) {
                return Component.translatable("message.encodedlogistics.link_card.gone");
            }
            if (other == target.bridge()) {
                return Component.translatable("message.encodedlogistics.link_card.same");
            }
            if (!NetworkBridgeBlockEntity.pair(other, target.bridge())) {
                return Component.translatable("message.encodedlogistics.link_card.dimension");
            }
            level.playSound(null, target.bridge().getBlockPos(), SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 0.5F, 1.6F);
            return Component.translatable("message.encodedlogistics.link_card.paired");
        }
        PointToPointPart p2p = target.p2p();
        if (!stored.pos().dimension().equals(level.dimension())) {
            return Component.translatable("message.encodedlogistics.link_card.p2p_dimension");
        }
        level.getChunkAt(stored.pos().pos());
        PointToPointPart other = stored.side().map(side -> PointToPointPart.at(level, stored.pos().pos(), side)).orElse(null);
        if (other == null) {
            return Component.translatable("message.encodedlogistics.link_card.gone");
        }
        return switch (PointToPointPart.pair(other, p2p)) {
            case PAIRED -> {
                level.playSound(null, p2p.host().getBlockPos(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.4F, 1.8F);
                yield Component.translatable("message.encodedlogistics.link_card.paired");
            }
            case SAME_ENDPOINT -> Component.translatable("message.encodedlogistics.link_card.same");
            case OTHER_TYPE -> Component.translatable("message.encodedlogistics.link_card.other_type");
            case SAME_DIRECTION -> Component.translatable("message.encodedlogistics.link_card.same_direction");
        };
    }

    // Writes the address onto the card (one taken off the stack if there are several).
    private static void store(ItemStack stack, Player player, InteractionHand hand, LinkAddress address) {
        if (stack.getCount() > 1) {
            ItemStack written = stack.split(1);
            write(written, address);
            player.getInventory().placeItemBackInInventory(written, Prediction.SERVER_ONLY);
        } else {
            write(stack, address);
        }
    }

    private static void write(ItemStack stack, LinkAddress address) {
        stack.set(ModDataComponents.LINK_ADDRESS.get(), address);
        stack.set(DataComponents.MAX_STACK_SIZE, 1);
    }

    // Sneak-use in the air clears a written card.
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!player.isSecondaryUseActive() || address(stack) == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide()) {
            stack.remove(ModDataComponents.LINK_ADDRESS.get());
            stack.remove(DataComponents.MAX_STACK_SIZE);
            player.sendOverlayMessage(Component.translatable("message.encodedlogistics.link_card.cleared"));
        }
        return InteractionResult.SUCCESS;
    }
}
