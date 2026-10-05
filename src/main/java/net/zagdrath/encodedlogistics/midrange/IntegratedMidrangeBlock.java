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
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.menu.TerminalDeskMenu;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;

// The Integrated Midrange System (tier 2, HANDOFF 2, 4): a two-panel cabinet with a diskette magazine unit and a
// built-in console, 28 x 20 px on a 3 x 2 footprint (the master bottom centre; dummies left, right, and above the
// console), one lane from any of them. STATE: its fascia (off, IPL, run, busy, attention). CONSOLE: its screen (off,
// booting, on: a session open at it). Where a click lands on the model (HANDOFF 3): the console hood or keyboard opens
// the Terminal OS; the left body or the magazine unit, the control panel; the right body and plinth, nothing.
public class IntegratedMidrangeBlock extends MidrangeHostBlock implements NetworkNodeBlock {
    private static final List<Vec3i> FOOTPRINT = List.of(new Vec3i(-1, 0, 0), Vec3i.ZERO, new Vec3i(1, 0, 0), new Vec3i(-1, 1, 0), new Vec3i(0, 1, 0));
    // The model's boxes a click zone is made of (px, from the master's corner, facing north), a little grown: a hit is on
    // a face.
    private static final double[][] CONSOLE = { { -5, 13, 9, 8, 20, 16 }, { -4, 13, 4.5, 7, 14, 8.5 } };
    private static final double[][] CONTROL_PANEL = { { 8, 1, 4, 22, 13, 16 }, { 10, 13, 6, 20, 16, 14 } };
    private static final double EDGE = 0.05;

    public enum Zone {
        CONSOLE, CONTROL_PANEL, NONE
    }

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

    // The zone a point is in (world coordinates), against the model's boxes.
    public static Zone zone(BlockPos master, Direction facing, Vec3 point) {
        double x = (point.x - master.getX()) * 16, y = (point.y - master.getY()) * 16, z = (point.z - master.getZ()) * 16;
        // Back to the model's own px (it's turned as MidrangeShapes turns its boxes).
        double mx = switch (facing) {
            case EAST -> z;
            case SOUTH -> 16 - x;
            case WEST -> 16 - z;
            default -> x;
        };
        double mz = switch (facing) {
            case EAST -> 16 - x;
            case SOUTH -> 16 - z;
            case WEST -> x;
            default -> z;
        };
        if (inside(CONSOLE, mx, y, mz)) {
            return Zone.CONSOLE;
        }
        return inside(CONTROL_PANEL, mx, y, mz) ? Zone.CONTROL_PANEL : Zone.NONE;
    }

    private static boolean inside(double[][] boxes, double x, double y, double z) {
        for (double[] b : boxes) {
            if (x >= b[0] - EDGE && x <= b[3] + EDGE && y >= b[1] - EDGE && y <= b[4] + EDGE && z >= b[2] - EDGE && z <= b[5] + EDGE) {
                return true;
            }
        }
        return false;
    }

    // The console opens the Terminal OS at it, as a Terminal Desk does; the control panel, its screen.
    @Override
    protected InteractionResult use(Level level, BlockPos master, BlockState state, Player player, BlockHitResult hit) {
        Zone zone = zone(master, state.getValue(FACING), hit.getLocation());
        if (zone == Zone.NONE) {
            return InteractionResult.PASS;
        }
        if (zone == Zone.CONTROL_PANEL) {
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
