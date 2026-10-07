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
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.WirelessBridgeBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;
import net.zagdrath.encodedlogistics.network.RemoteLink;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.wireless.WirelessShapes;
import net.zagdrath.encodedlogistics.wireless.WirelessState;

// The Wireless Bridge: a remote segment of its Wireless Controller's network. Linked to a controller (Link Card), what's
// cabled to it (on its sides and bottom; its mast is on top) is on that network, anywhere in range, as if a
// wirelessBridgeLanes-lane cable ran from the controller's rack. It uses no lanes itself, drains wirelessEnergyMultiplier
// times a Network Bridge's, and counts as one of the controller's clients. STATE: off, linking (not linked or no
// controller), online, active (lanes crossing: the window pulses), fault (no access points, over capacity).
public class WirelessBridgeBlock extends BaseEntityBlock implements NetworkNodeBlock {
    private static final MapCodec<WirelessBridgeBlock> CODEC = simpleCodec(WirelessBridgeBlock::new);

    @Override
    protected MapCodec<WirelessBridgeBlock> codec() {
        return CODEC;
    }

    public static final EnumProperty<WirelessState> STATE = WirelessState.BRIDGE_STATE;

    public WirelessBridgeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(STATE, WirelessState.OFF));
    }

    public static int lightLevel(BlockState state) {
        return state.getValue(STATE) == WirelessState.OFF ? 0 : 5;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(STATE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return WirelessShapes.BRIDGE;
    }

    // --- Network ---

    // A Wireless Bridge on the network: no lanes of its own, linked on every face but the top, and its link to its
    // controller's rack (the rack links back while the controller admits it).
    public record WirelessBridgeNode(BlockPos pos, Set<Direction> connections, double passiveDrain, List<RemoteLink> remoteLinks) implements NetworkNode {
        @Override
        public int laneCost() {
            return 0;
        }
    }

    @Override
    public boolean connectsOn(BlockState state, Direction side) {
        return side != Direction.UP;
    }

    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        Set<Direction> sides = EnumSet.allOf(Direction.class);
        sides.remove(Direction.UP);
        List<RemoteLink> links = level.getBlockEntity(pos) instanceof WirelessBridgeBlockEntity bridge && bridge.link() != null
                ? List.of(new RemoteLink(bridge.link().rackNode(), Config.WIRELESS_BRIDGE_LANES.getAsInt(), true)) : List.of();
        return new WirelessBridgeNode(pos.immutable(), sides, Config.BRIDGE_DRAIN.getAsDouble() * Config.WIRELESS_ENERGY_MULTIPLIER.getAsDouble(), links);
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
        return new WirelessBridgeBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntityTypes.WIRELESS_BRIDGE.get(),
                (tickLevel, pos, tickState, bridge) -> bridge.serverTick((ServerLevel) tickLevel));
    }
}
