/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block.cable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.encodedlogistics.block.NetworkControllerBlock;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;
import net.zagdrath.encodedlogistics.network.NetworkNodeHost;
import net.zagdrath.encodedlogistics.registry.ModBlocks;

// A Network Cable or Dense Network Cable in one colour. Each side connects to a compatible cable (CABLE: neutral joins
// every colour, a dye only its own colour and neutral; normal and dense join each other) or to a network block
// (BLOCK: a controller now, devices later), or to nothing. The multipart blockstates draw it from those six
// properties, and the shape follows the same parts (CableShapes). On a network it's a link carrying its tier's
// channels and using none itself. A dye recolours a placed cable; a water bucket washes a dyed one back to neutral.
public class NetworkCableBlock extends Block implements SimpleWaterloggedBlock, NetworkNodeBlock {
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

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        CONNECTIONS.values().forEach(builder::add);
        builder.add(WATERLOGGED);
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

    // The state with every side's connection worked out from the neighbours.
    public BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
        for (Direction side : Direction.values()) {
            state = state.setValue(CONNECTIONS.get(side), connectionTo(level, pos, side));
        }
        return state;
    }

    private CableConnection connectionTo(BlockGetter level, BlockPos pos, Direction side) {
        BlockPos neighbourPos = pos.relative(side);
        BlockState neighbour = level.getBlockState(neighbourPos);
        if (neighbour.getBlock() instanceof NetworkCableBlock other) {
            return color.connectsTo(other.color) ? CableConnection.CABLE : CableConnection.NONE;
        }
        if (neighbour.getBlock() instanceof NetworkControllerBlock || neighbour.getBlock() instanceof NetworkNodeBlock
                || level.getBlockEntity(neighbourPos) instanceof NetworkNodeHost) {
            return CableConnection.BLOCK;
        }
        return CableConnection.NONE;
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

    public record CableNode(BlockPos pos, Set<Direction> connections, int channelCapacity, double passiveDrain) implements NetworkNode {
        @Override
        public int channelCost() {
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
        return new CableNode(pos.immutable(), connections, tier.channels(), tier.passiveDrain());
    }

    // --- Dyeing and washing ---

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        CableColor recolour = stack.is(Items.WATER_BUCKET) ? CableColor.NEUTRAL : CableColor.of(stack.get(DataComponents.DYE));
        // A water bucket on a neutral cable waterlogs it as usual.
        if (recolour == null || recolour == color) {
            return super.useItemOn(stack, state, level, pos, player, hand, hit);
        }
        if (!level.isClientSide()) {
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

    // --- Shape and fluid ---

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        CableConnection[] connections = new CableConnection[6];
        for (Direction side : Direction.values()) {
            connections[side.ordinal()] = connection(state, side);
        }
        return CableShapes.get(tier, connections);
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }
}
