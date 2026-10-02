/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block.cable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.Tags;
import net.zagdrath.encodedlogistics.block.NetworkControllerBlock;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.item.CableFacadeItem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;
import net.zagdrath.encodedlogistics.network.NetworkNodeHost;
import net.zagdrath.encodedlogistics.network.NetworkPart;
import net.zagdrath.encodedlogistics.network.RemoteLink;
import net.zagdrath.encodedlogistics.part.PartHosting;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.registry.ModBlocks;
import net.zagdrath.encodedlogistics.registry.ModDataComponents;
import net.zagdrath.encodedlogistics.registry.ModItems;

// A Network Cable, Dense Network Cable or Fiber Cable in one colour. Each side connects to a compatible cable (CABLE:
// neutral joins every colour, a dye only its own colour and neutral; the tiers all join each other) or to a network
// block that connects on that face (BLOCK: a controller, inlet, bank, isolator, devices later), or to nothing. The
// multipart blockstates draw it from those six properties, and the shape follows the same parts (CableShapes). On a
// network it's a link carrying its tier's lanes and using none itself. A dye recolours a placed cable; a water bucket
// washes a dyed one back to neutral.
//
// Its block entity (CableBlockEntity) holds the attachments: a Cable Anchor on a side stops that side connecting (for
// this cable and its neighbour alike), a Cable Facade covers it with a panel, a part (terminal, port, tap, sensor) is a
// device facing out of that side (the side doesn't connect; the cable becomes a device using the parts' lanes). Using
// any of those items on a cable mounts it on the side you're looking at; using a part opens its menu; a wrench, or
// sneak-use with an empty hand, takes it off again (with its contents); mining hits a facade or part before the cable.
// A cable carrying a Threshold Sensor sends redstone like a lever.
public class NetworkCableBlock extends Block implements SimpleWaterloggedBlock, NetworkNodeBlock, EntityBlock {
    public static final Map<Direction, EnumProperty<CableConnection>> CONNECTIONS = new EnumMap<>(Direction.class);
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    static {
        for (Direction side : Direction.values()) {
            CONNECTIONS.put(side, EnumProperty.create(side.getSerializedName(), CableConnection.class));
        }
    }

    private final CableTier tier;
    private final CableColor color;

    public NetworkCableBlock(BlockBehaviour.Properties properties, CableTier tier, CableColor color) {
        super(properties);
        this.tier = tier;
        this.color = color;
        BlockState state = stateDefinition.any().setValue(WATERLOGGED, false);
        for (EnumProperty<CableConnection> property : CONNECTIONS.values()) {
            state = state.setValue(property, CableConnection.NONE);
        }
        registerDefaultState(state);
    }

    public CableTier getTier() {
        return tier;
    }

    public CableColor getColor() {
        return color;
    }

    public static CableConnection connection(BlockState state, Direction side) {
        return state.getValue(CONNECTIONS.get(side));
    }

    public static CableConnection[] connections(BlockState state) {
        CableConnection[] connections = new CableConnection[6];
        for (Direction side : Direction.values()) {
            connections[side.ordinal()] = connection(state, side);
        }
        return connections;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CONNECTIONS.values().forEach(builder::add);
        builder.add(WATERLOGGED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CableBlockEntity(pos, state);
    }

    // Swapping one cable for another (dyeing, washing) keeps the block entity with its attachments.
    @Override
    protected boolean shouldChangedStateKeepBlockEntity(BlockState oldState) {
        return oldState.getBlock() instanceof NetworkCableBlock;
    }

    public static CableAttachments attachments(BlockGetter level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CableBlockEntity cable ? cable.getAttachments() : CableAttachments.EMPTY;
    }

    // --- Connections ---

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        return withConnections(defaultBlockState(), level, pos)
                .setValue(WATERLOGGED, level.getFluidState(pos).getType() == Fluids.WATER);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction side,
            BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        if (state.getValue(WATERLOGGED)) {
            ticks.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return state.setValue(CONNECTIONS.get(side), connectionTo(level, pos, side));
    }

    // The state with every side's connection worked out from the neighbours (and this cable's anchors).
    public BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            state = state.setValue(CONNECTIONS.get(side), connectionTo(level, pos, side));
        }
        return state;
    }

    private CableConnection connectionTo(BlockGetter level, BlockPos pos, Direction side) {
        if (attachments(level, pos).blocksConnection(side)) {
            return CableConnection.NONE;
        }
        BlockPos neighbourPos = pos.relative(side);
        BlockState neighbour = level.getBlockState(neighbourPos);
        if (neighbour.getBlock() instanceof NetworkCableBlock other) {
            return color.connectsTo(other.color) && !attachments(level, neighbourPos).blocksConnection(side.getOpposite()) ? CableConnection.CABLE
                    : CableConnection.NONE;
        }
        if (neighbour.getBlock() instanceof NetworkNodeBlock block) {
            return block.connectsOn(neighbour, side.getOpposite()) ? CableConnection.BLOCK : CableConnection.NONE;
        }
        if (neighbour.getBlock() instanceof NetworkControllerBlock || level.getBlockEntity(neighbourPos) instanceof NetworkNodeHost) {
            return CableConnection.BLOCK;
        }
        return CableConnection.NONE;
    }

    // After an anchor came or went: this cable and the neighbour on that side work out their connections again.
    private static void refreshConnections(Level level, BlockPos pos, Direction side) {
        refreshConnections(level, pos);
        refreshConnections(level, pos.relative(side));
        if (level instanceof ServerLevel serverLevel) {
            ControllerStructures.get(serverLevel).markTopologyChanged();
        }
    }

    private static void refreshConnections(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof NetworkCableBlock cable) {
            BlockState updated = cable.withConnections(state, level, pos);
            if (updated != state) {
                level.setBlock(pos, updated, Block.UPDATE_ALL);
            }
        }
    }

    // Any change to a cable (placed, reconnected, removed) changes the networks it's on.
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel serverLevel) {
            ControllerStructures.get(serverLevel).markTopologyChanged();
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        ControllerStructures.get(level).markTopologyChanged();
    }

    // --- Network ---

    public record CableNode(BlockPos pos, Set<Direction> connections, int laneCapacity, double passiveDrain) implements NetworkNode {
        @Override
        public int laneCost() {
            return 0;
        }
    }

    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        Set<Direction> connections = EnumSet.noneOf(Direction.class);
        for (Direction side : Direction.values()) {
            if (connection(state, side).connected()) {
                connections.add(side);
            }
        }
        CableAttachments attachments = attachments(level, pos);
        if (!attachments.hasParts()) {
            return new CableNode(pos.immutable(), connections, tier.lanes(), tier.passiveDrain());
        }
        // With parts on it the cable is a device: it needs their lanes, and drains for them too.
        List<RemoteLink> links = level.getBlockEntity(pos) instanceof CableBlockEntity cable ? cable.remoteLinks() : List.of();
        return new CableDeviceNode(pos.immutable(), connections, tier.lanes(), PartHosting.lanes(attachments),
                tier.passiveDrain() + PartHosting.drain(attachments), PartHosting.networkParts(attachments), links);
    }

    // A cable carrying parts: still a link of its tier's lanes, and a device needing the parts' lanes; remoteLinks:
    // those of its lanes Point-to-Point Links.
    public record CableDeviceNode(BlockPos pos, Set<Direction> connections, int laneCapacity, int laneCost, double passiveDrain,
            List<NetworkPart> parts, List<RemoteLink> remoteLinks) implements NetworkNode {}

    // --- Using items: attachments, dyeing and washing ---

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (stack.is(ModItems.CABLE_ANCHOR.get()) || stack.is(ModItems.CABLE_FACADE.get()) || PartType.byItem(stack.getItem()) != null) {
            return attach(stack, level, pos, player, sideAt(hit.getLocation(), pos));
        }
        if (stack.is(Tags.Items.TOOLS_WRENCH)) {
            return detach(level, pos, player, sideAt(hit.getLocation(), pos)) ? InteractionResult.SUCCESS
                    : super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        CableColor recolour = stack.is(Items.WATER_BUCKET) ? CableColor.NEUTRAL : CableColor.of(stack.get(DataComponents.DYE));
        // A water bucket on a neutral cable waterlogs it as usual.
        if (recolour == null || recolour == color) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!level.isClientSide()) {
            // The block entity, and so the attachments, carry over (shouldChangedStateKeepBlockEntity).
            NetworkCableBlock target = ModBlocks.cable(tier, recolour).get();
            BlockState recoloured = target.withConnections(target.defaultBlockState(), level, pos).setValue(WATERLOGGED, state.getValue(WATERLOGGED));
            level.setBlock(pos, recoloured, Block.UPDATE_ALL);
            if (recolour != CableColor.NEUTRAL) {
                stack.consume(1, player);
            }
            level.playSound(null, pos, recolour == CableColor.NEUTRAL ? SoundEvents.BUCKET_EMPTY : SoundEvents.DYE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    // Sneak-use with an empty hand takes the attachment off the side you're looking at; using a part opens its menu.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        Direction side = sideAt(hit.getLocation(), pos);
        if (player.isSecondaryUseActive() && detach(level, pos, player, side)) {
            return InteractionResult.SUCCESS;
        }
        if (!player.isSecondaryUseActive() && attachments(level, pos).part(side) != null) {
            if (player instanceof ServerPlayer serverPlayer) {
                PartHosting.open(level, pos, side, serverPlayer);
            }
            return InteractionResult.SUCCESS;
        }
        return super.useWithoutItem(state, level, pos, player, hit);
    }

    private InteractionResult attach(ItemStack stack, Level level, BlockPos pos, Player player, Direction side) {
        if (!(level.getBlockEntity(pos) instanceof CableBlockEntity cable) || cable.getAttachments().get(side).kind() != CableAttachments.Kind.NONE) {
            return InteractionResult.FAIL;
        }
        CableAttachments.Attachment attachment;
        if (stack.is(ModItems.CABLE_ANCHOR.get())) {
            attachment = CableAttachments.Attachment.ANCHOR;
        } else if (PartType.byItem(stack.getItem()) != null) {
            attachment = CableAttachments.Attachment.part(PartType.byItem(stack.getItem()));
        } else {
            BlockState target = stack.get(ModDataComponents.FACADE_TARGET.get());
            if (target != null && !CableFacadeItem.canCopy(target)) {
                return InteractionResult.FAIL;
            }
            attachment = CableAttachments.Attachment.facade(target);
        }
        if (!level.isClientSide()) {
            cable.setAttachment(side, attachment);
            stack.consume(1, player);
            refreshConnections(level, pos, side);
            level.playSound(null, pos, SoundEvents.METAL_PLACE, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    // Takes the attachment off a side and drops it; false when there's nothing there.
    private static boolean detach(Level level, BlockPos pos, @Nullable Player player, Direction side) {
        if (!(level.getBlockEntity(pos) instanceof CableBlockEntity cable)) {
            return false;
        }
        CableAttachments.Attachment attachment = cable.getAttachments().get(side);
        if (attachment.kind() == CableAttachments.Kind.NONE) {
            return false;
        }
        if (!level.isClientSide()) {
            if (player == null || !player.isCreative()) {
                PartHosting.dropFrom(level, pos, side, cable);
            }
            cable.setAttachment(side, CableAttachments.Attachment.NONE);
            refreshConnections(level, pos, side);
            level.playSound(null, pos, SoundEvents.METAL_BREAK, SoundSource.BLOCKS, 1.0F, 1.0F);
        }
        return true;
    }

    // The side a point on the cable (a hit) belongs to: whichever way it lies furthest from the middle. A hit on an
    // arm, its flange or the facade over it all count as that arm's side.
    public static Direction sideAt(Vec3 hit, BlockPos pos) {
        return Direction.getApproximateNearest(hit.subtract(Vec3.atCenterOf(pos)));
    }

    // Mining a cable takes off the facade or part you're looking at first; the cable itself goes once none is in the way.
    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level level, BlockPos pos, Player player, ItemStack toolStack, boolean willHarvest,
            FluidState fluid) {
        Direction part = partLookedAt(level, pos, player);
        if (part != null) {
            detach(level, pos, player, part);
            return false;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, toolStack, willHarvest, fluid);
    }

    private static @Nullable Direction partLookedAt(Level level, BlockPos pos, Player player) {
        CableAttachments attachments = attachments(level, pos);
        if (attachments.isEmpty()) {
            return null;
        }
        HitResult hit = player.pick(player.blockInteractionRange(), 1.0F, false);
        if (!(hit instanceof BlockHitResult blockHit) || !blockHit.getBlockPos().equals(pos)) {
            return null;
        }
        Vec3 local = blockHit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
        for (Direction side : Direction.values()) {
            PartType part = attachments.part(side);
            if (attachments.facade(side) && CableShapes.facade(side).bounds().inflate(1.0E-4).contains(local)
                    || part != null && CableShapes.part(part, side).bounds().inflate(1.0E-4).contains(local)) {
                return side;
            }
        }
        return null;
    }

    // A facade of an opaque block hides the neighbour's face behind it, like a full block would.
    @Override
    public boolean hidesNeighborFace(BlockGetter level, BlockPos pos, BlockState state, BlockState neighborState, Direction dir) {
        CableAttachments attachments = level.getModelData(pos).get(CableBlockEntity.ATTACHMENTS);
        if (attachments == null || !attachments.facade(dir)) {
            return false;
        }
        BlockState target = attachments.get(dir).target();
        return target == null || target.canOcclude();
    }

    // --- Redstone (Threshold Sensors) ---

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return PartHosting.weakSignal(level, pos);
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return PartHosting.strongSignal(level, pos, direction);
    }

    // Redstone dust only joins cables that carry a sensor.
    @Override
    protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null && PartHosting.hasSensor(level, pos);
    }

    // --- Shape and fluid ---

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CableShapes.get(tier, connections(state), attachments(level, pos));
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
}
