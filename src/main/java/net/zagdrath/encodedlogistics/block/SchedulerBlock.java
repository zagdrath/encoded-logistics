/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.encodedlogistics.blockentity.SchedulerCoreBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.SchedulerStructures;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;

// A block of the Scheduler multiblock: the Scheduler Core, a Job Buffer (job memory) or a Thread Unit (parallel jobs).
// Touching scheduler blocks form one structure (SchedulerStructures) when they fill a cuboid within the size limit with
// exactly one Core; FORMED is set from it and the connected model reads it. A formed structure is one network device:
// cables connect to any of its blocks, the Core uses its one lane and drains for all of them, and the other blocks just
// pass the network through to it. Using any block of a formed structure opens the Core's screen.
public class SchedulerBlock extends Block implements NetworkNodeBlock {
    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    private static final Set<Direction> ALL = EnumSet.allOf(Direction.class);

    public SchedulerBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FORMED, false));
    }

    public static boolean isFormed(BlockState state) {
        return state.getBlock() instanceof SchedulerBlock && state.getValue(FORMED);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FORMED);
    }

    // --- Network ---

    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        if (!state.getValue(FORMED)) {
            return null;
        }
        if (level.getBlockEntity(pos) instanceof SchedulerCoreBlockEntity core) {
            return new DeviceNode(pos.immutable(), ALL, 1, core.drain(), List.of(), true);
        }
        return new DeviceNode(pos.immutable(), ALL, 0, 0, List.of(), true);
    }

    // --- Forming ---

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
            SchedulerStructures.get(serverLevel).queue(pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        SchedulerStructures.get(level).removed(pos);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos core = SchedulerStructures.get(serverLevel).coreOf(pos);
        if (core == null && level.getBlockEntity(pos) instanceof SchedulerCoreBlockEntity) {
            core = pos;
        }
        if (core != null && level.getBlockEntity(core) instanceof SchedulerCoreBlockEntity entity) {
            entity.openMenu(serverPlayer);
        } else {
            serverPlayer.sendOverlayMessage(Component.translatable("gui.encodedlogistics.scheduler.unformed").withStyle(ChatFormatting.RED));
        }
        return InteractionResult.SUCCESS;
    }
}
