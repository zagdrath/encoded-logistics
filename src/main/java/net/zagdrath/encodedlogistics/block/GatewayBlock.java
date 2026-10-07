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
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.GatewayBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// The Gateway: the network's two-way interface with the machines next to it - it runs Processing Schematics through
// them and keeps chosen items stocked for them. Cables connect on every face. ACTIVE (its corner lights) while it moved
// items in the last 20 ticks. A device: one lane, gatewayDrain FE/t.
public class GatewayBlock extends BaseEntityBlock implements NetworkNodeBlock {
    private static final MapCodec<GatewayBlock> CODEC = simpleCodec(GatewayBlock::new);

    @Override
    protected MapCodec<GatewayBlock> codec() {
        return CODEC;
    }

    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final Set<Direction> ALL = EnumSet.allOf(Direction.class);

    public GatewayBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    public static int lightLevel(BlockState state) {
        return state.getValue(ACTIVE) ? 4 : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof GatewayBlockEntity gateway) {
            player.openMenu(gateway);
        }
        return InteractionResult.SUCCESS;
    }

    // --- Network ---

    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        return new DeviceNode(pos.immutable(), ALL, 1, Config.GATEWAY_DRAIN.getAsDouble(), List.of(), true);
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
        return new GatewayBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntityTypes.GATEWAY.get(),
                (tickLevel, pos, tickState, gateway) -> gateway.serverTick((ServerLevel) tickLevel));
    }
}
