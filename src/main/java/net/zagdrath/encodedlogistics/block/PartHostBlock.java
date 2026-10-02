/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block;

import java.util.EnumSet;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.encodedlogistics.block.cable.CableAttachments;
import net.zagdrath.encodedlogistics.block.cable.CableShapes;
import net.zagdrath.encodedlogistics.block.cable.NetworkCableBlock;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;
import net.zagdrath.encodedlogistics.part.PartHosting;

// A part host: holds a part mounted on the face of a block that isn't a cable. No cable, no item of its own - just a
// CableBlockEntity whose part sits on the side toward the block it's mounted on (terminals and sensors facing out, ports
// and taps facing the block). It's on that block's network when the block is a network block (a Drive Bay, a
// controller...), as a device using the part's lanes; otherwise the part is offline. Using it opens the part's menu;
// mining it (or sneak-using it with an empty hand) drops the part and its contents, and the host goes with it.
public class PartHostBlock extends Block implements EntityBlock, NetworkNodeBlock {
    public PartHostBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CableBlockEntity(pos, state);
    }

    // The side the part is on: toward the block it's mounted on.
    public static @Nullable Direction mount(CableAttachments attachments) {
        for (Direction side : Direction.values()) {
            if (attachments.part(side) != null) {
                return side;
            }
        }
        return null;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return CableShapes.partHost(NetworkCableBlock.attachments(level, pos));
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.isSecondaryUseActive()) {
            if (!level.isClientSide()) {
                level.destroyBlock(pos, false, player);
            }
            return InteractionResult.SUCCESS;
        }
        Direction mount = mount(NetworkCableBlock.attachments(level, pos));
        if (mount != null && player instanceof ServerPlayer serverPlayer) {
            PartHosting.open(level, pos, mount, serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    // --- Network ---

    @Override
    public boolean connectsOn(BlockState state, Direction side) {
        return false;
    }

    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        CableAttachments attachments = NetworkCableBlock.attachments(level, pos);
        Direction mount = mount(attachments);
        if (mount == null) {
            return null;
        }
        return new DeviceNode(pos.immutable(), EnumSet.of(mount), PartHosting.lanes(attachments), PartHosting.drain(attachments),
                PartHosting.networkParts(attachments), false);
    }

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

    // --- Redstone (a Threshold Sensor on a block face) ---

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

    @Override
    protected boolean shouldRedstoneWireConnectTo(BlockState state, BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        return direction != null && PartHosting.hasSensor(level, pos);
    }
}
