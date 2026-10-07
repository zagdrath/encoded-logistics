/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.RelayAntennaBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;

// The Relay Antenna: wireless coverage for the Handheld Terminals linked to its network, a sphere of relayBaseRange
// blocks plus relayRangePerTransceiver for each Optical Transceiver in its four slots. A full block with a mast standing
// on top (part of its outline, not its collision). Faces the player when placed; cables connect on every face but the
// top. ONLINE (the mast tip blinks) while it works. A device: one lane, relayDrain FE/t.
public class RelayAntennaBlock extends BaseEntityBlock implements NetworkNodeBlock {
    private static final MapCodec<RelayAntennaBlock> CODEC = simpleCodec(RelayAntennaBlock::new);

    @Override
    protected MapCodec<RelayAntennaBlock> codec() {
        return CODEC;
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty ONLINE = BooleanProperty.create("online");

    private static final VoxelShape POLE = Block.box(7, 16, 7, 9, 32, 9);
    // The mast's cross arms as modelled (facing north), and turned a quarter for east and west.
    private static final VoxelShape ARMS_NS = Shapes.or(Block.box(4, 22, 7.5, 12, 23, 8.5), Block.box(7.5, 26, 5, 8.5, 27, 11));
    private static final VoxelShape ARMS_EW = Shapes.or(Block.box(7.5, 22, 4, 8.5, 23, 12), Block.box(5, 26, 7.5, 11, 27, 8.5));
    private static final VoxelShape OUTLINE_NS = Shapes.or(Shapes.block(), POLE, ARMS_NS);
    private static final VoxelShape OUTLINE_EW = Shapes.or(Shapes.block(), POLE, ARMS_EW);

    public RelayAntennaBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(ONLINE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ONLINE);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? OUTLINE_NS : OUTLINE_EW;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof RelayAntennaBlockEntity relay) {
            player.openMenu(relay);
        }
        return InteractionResult.SUCCESS;
    }

    // --- Network ---

    @Override
    public boolean connectsOn(BlockState state, Direction side) {
        return side != Direction.UP;
    }

    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        Set<Direction> sides = EnumSet.allOf(Direction.class);
        sides.remove(Direction.UP);
        return new DeviceNode(pos.immutable(), sides, 1, Config.RELAY_DRAIN.getAsDouble(), List.of(), true);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
            ControllerStructures.get(serverLevel).markTopologyChanged();
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        ControllerStructures.get(level).markTopologyChanged();
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RelayAntennaBlockEntity(pos, state);
    }
}
