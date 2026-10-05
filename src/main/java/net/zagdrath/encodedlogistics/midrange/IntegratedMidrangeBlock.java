/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.EnumSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.menu.TerminalDeskMenu;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;

// The Integrated Midrange System (tier 2, HANDOFF 4): a two-panel cabinet with a diskette magazine drive and a built-in
// console, 2 x 2 blocks (the master lower left, dummies to its +x and above), one lane from any of them. STATE: its
// fascia (off, IPL, run, busy, attention). CONSOLE: its screen (off, booting, on: a session open at it). Its console
// opens the Terminal OS; the rest of it, the control panel.
public class IntegratedMidrangeBlock extends MidrangeHostBlock implements NetworkNodeBlock {
    // Above this (in the master's block) is the console.
    private static final double CONSOLE_Y = 12.5 / 16;
    private static final List<Vec3i> FOOTPRINT = List.of(Vec3i.ZERO, new Vec3i(1, 0, 0), new Vec3i(0, 1, 0), new Vec3i(1, 1, 0));

    public IntegratedMidrangeBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.MASTER)
                .setValue(MidrangeStates.STATE, MidrangeStates.Run.OFF).setValue(MidrangeStates.CONSOLE, MidrangeStates.Console.OFF));
    }

    public static int lightLevel(BlockState state) {
        return state.getValue(PART) == Part.MASTER && state.getValue(MidrangeStates.STATE) != MidrangeStates.Run.OFF ? 4 : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MidrangeStates.STATE, MidrangeStates.CONSOLE);
    }

    @Override
    protected List<Vec3i> footprint() {
        return FOOTPRINT;
    }

    @Override
    protected String shapeKey(BlockState state) {
        return "integrated_midrange";
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    // The master is the device (a lane, its drain); the dummies pass lanes to it.
    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        boolean master = state.getValue(PART) == Part.MASTER;
        return new DeviceNode(pos.immutable(), EnumSet.allOf(Direction.class), master ? 1 : 0, master ? Config.INTEGRATED_MIDRANGE_DRAIN.getAsDouble() : 0,
                List.of(), true);
    }

    // The console (the hood and keyboard tray on the master's column: its top, and the block above) opens the Terminal
    // OS there, as a Terminal Desk does; anywhere else, the control panel.
    @Override
    protected InteractionResult use(Level level, BlockPos master, BlockState state, Player player, BlockHitResult hit) {
        BlockPos clicked = hit.getBlockPos();
        Vec3i offset = offset(level, clicked, level.getBlockState(clicked));
        double y = hit.getLocation().y - clicked.getY();
        boolean console = offset.getX() == 0 && (offset.getY() == 1 || y >= CONSOLE_Y);
        if (!console) {
            return super.use(level, master, state, player, hit);
        }
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(master) instanceof MidrangeSystemBlockEntity system) {
            if (!system.running()) {
                serverPlayer.sendOverlayMessage(Component.translatable("message.encodedlogistics.midrange.console_off"));
            } else {
                TerminalDeskMenu.open(serverPlayer, master);
            }
        }
        return InteractionResult.SUCCESS;
    }
}
