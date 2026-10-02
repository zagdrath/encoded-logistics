/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.encodedlogistics.blockentity.NetworkControllerBlockEntity;
import net.zagdrath.encodedlogistics.menu.NetworkControllerMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.NetworkStatus;

// The Network Controller. Touching controllers form one structure (see ControllerStructures); FORMED and STATE are
// set server-side from it, and the connected model reads them. Right-clicking any block of a valid structure opens
// the structure's Network screen.
public class NetworkControllerBlock extends BaseEntityBlock {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    public static final EnumProperty<ControllerState> STATE = EnumProperty.create("state", ControllerState.class);

    public NetworkControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false).setValue(STATE, ControllerState.OFFLINE));
    }

    public static int lightLevel(BlockState state) {
        return state.getValue(STATE) == ControllerState.ONLINE ? 4 : 0;
    }

    // A neighbour that joins this one's texture: a controller of the same formed structure (touching controllers
    // are always one structure).
    public static boolean isFormed(BlockState state) {
        return state.getBlock() instanceof NetworkControllerBlock && state.getValue(FORMED);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED, STATE);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        // Our own FORMED / STATE updates come through here too; only a newly placed controller changes the shape.
        if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
            ControllerStructures.get(serverLevel).queue(pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        ControllerStructures.get(level).queueNeighbours(pos);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation,
            boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        // Something next to the structure changed: its network may have gained or lost a node.
        if (level instanceof ServerLevel serverLevel) {
            ControllerStructures.get(serverLevel).markTopologyChanged();
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof NetworkControllerBlockEntity controller)) {
            return InteractionResult.SUCCESS;
        }
        ControllerStructures.Structure structure = ControllerStructures.get(serverLevel).get(controller.getStructureId());
        if (structure == null) {
            return InteractionResult.SUCCESS;
        }
        NetworkStatus shapeStatus = structure.shapeStatus();
        if (shapeStatus != null) {
            serverPlayer.sendOverlayMessage(shapeStatus.description().copy().withStyle(ChatFormatting.RED));
            return InteractionResult.SUCCESS;
        }
        serverPlayer.openMenu(new SimpleMenuProvider((id, inventory, p) -> new NetworkControllerMenu(id, inventory, pos),
                Component.translatable("gui.encodedlogistics.network")), buf -> buf.writeBlockPos(pos));
        return InteractionResult.SUCCESS;
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    // The structure's stored energy, 0-15.
    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos, Direction direction) {
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof NetworkControllerBlockEntity controller) {
            return ControllerStructures.get(serverLevel).comparatorSignal(controller.getStructureId());
        }
        return 0;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetworkControllerBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        // Structures tick as a whole from ControllerStructures, not block by block.
        return null;
    }
}
