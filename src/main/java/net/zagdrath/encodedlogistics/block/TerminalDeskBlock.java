/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// The Terminal Desk (2 wide x 1 deep): a CRT green-screen terminal on a steel-and-laminate desk. The master is the left
// half seen from the front (the CRT and keyboard; the whole model hangs off it), the dummy the right (the drawer
// pedestal: facing north, at x + 1). A network device on the master - cables connect on any side but the front - using
// one lane and terminalDeskDrain FE/t (+ terminalDeskScreenDrain while the screen is on). Its screen is off while it's
// offline, boots for 60 ticks as it comes online, then stays on (TerminalDeskBlockEntity).
//
// Use the master for the green-screen terminal (CrtScreen); the pedestal for the drawer (9 slots, where *DRAWER
// withdrawals go; hoppers can take from it). Sneak-use either half empty-handed to tidy or clutter the desk top.
public class TerminalDeskBlock extends BaseEntityBlock implements NetworkNodeBlock {
    private static final MapCodec<TerminalDeskBlock> CODEC = simpleCodec(TerminalDeskBlock::new);

    @Override
    protected MapCodec<TerminalDeskBlock> codec() {
        return CODEC;
    }

    public enum Part implements StringRepresentable {
        MASTER, DUMMY;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Screen implements StringRepresentable {
        OFF, BOOT, ON;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final EnumProperty<Screen> SCREEN = EnumProperty.create("screen", Screen.class);
    public static final BooleanProperty CLUTTER = BooleanProperty.create("clutter");

    // In the facing-north frame, x 0..32 across both halves (the master's 0..16).
    private static final VoxelShape[] MASTER = new VoxelShape[4], DUMMY = new VoxelShape[4];

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            int i = facing.get2DDataValue();
            MASTER[i] = rotate(Shapes.or(Block.box(0, 12, 0, 16, 13, 16), Block.box(0.5, 0, 1, 2, 12, 15), Block.box(2.5, 13, 4.5, 14.5, 23, 10.5),
                    Block.box(4, 14, 10.5, 13, 21.5, 15.5), Block.box(3, 13, 0.75, 14, 13.85, 4.25)), facing);
            DUMMY[i] = rotate(Shapes.or(Block.box(0, 12, 0, 16, 13, 16), Block.box(4, 0, 1, 15.5, 12, 15)), facing);
        }
    }

    public TerminalDeskBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.MASTER).setValue(SCREEN, Screen.OFF)
                .setValue(CLUTTER, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, SCREEN, CLUTTER);
    }

    // A shape drawn for facing north, turned to face the other way.
    private static VoxelShape rotate(VoxelShape shape, Direction facing) {
        VoxelShape[] turned = { Shapes.empty() };
        int turns = (facing.get2DDataValue() + 2) % 4;
        shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> {
            double ax0 = x0, az0 = z0, ax1 = x1, az1 = z1;
            for (int t = 0; t < turns; t++) {
                // A quarter turn clockwise seen from above: (x, z) -> (1 - z, x).
                double nx0 = 1 - az1, nz0 = ax0, nx1 = 1 - az0, nz1 = ax1;
                ax0 = nx0;
                az0 = nz0;
                ax1 = nx1;
                az1 = nz1;
            }
            turned[0] = Shapes.or(turned[0], Shapes.box(ax0, y0, az0, ax1, y1, az1));
        });
        return turned[0];
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        int i = state.getValue(FACING).get2DDataValue();
        return state.getValue(PART) == Part.MASTER ? MASTER[i] : DUMMY[i];
    }

    // --- The two halves ---

    // Where the other half is: the dummy to the master's right as seen from the front (facing north: +x).
    public static BlockPos other(BlockState state, BlockPos pos) {
        Direction side = state.getValue(FACING).getClockWise();
        return state.getValue(PART) == Part.MASTER ? pos.relative(side) : pos.relative(side.getOpposite());
    }

    public static BlockPos master(BlockState state, BlockPos pos) {
        return state.getValue(PART) == Part.MASTER ? pos : other(state, pos);
    }

    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos dummy = context.getClickedPos().relative(facing.getClockWise());
        Level level = context.getLevel();
        if (!level.getWorldBorder().isWithinBounds(dummy) || !level.getBlockState(dummy).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            level.setBlock(other(state, pos), state.setValue(PART, Part.DUMMY), Block.UPDATE_ALL);
        }
    }

    // Breaking one half breaks the other (without drops: the broken one drops the desk, the master its drawer).
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        BlockPos other = other(state, pos);
        BlockState there = level.getBlockState(other);
        if (there.is(this) && there.getValue(FACING) == state.getValue(FACING) && there.getValue(PART) != state.getValue(PART)) {
            level.destroyBlock(other, false);
        }
        if (state.getValue(PART) == Part.MASTER) {
            ControllerStructures.get(level).markTopologyChanged();
        }
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && state.getValue(PART) == Part.MASTER && level instanceof ServerLevel serverLevel) {
            ControllerStructures.get(serverLevel).markTopologyChanged();
        }
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
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // --- Using it ---

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos master = master(state, pos);
        if (!(level.getBlockEntity(master) instanceof TerminalDeskBlockEntity desk)) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            BlockState masterState = level.getBlockState(master);
            boolean clutter = !masterState.getValue(CLUTTER);
            level.setBlock(master, masterState.setValue(CLUTTER, clutter), Block.UPDATE_CLIENTS);
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(PART) == Part.DUMMY) {
            player.openMenu(desk);
        } else if (NetworkAccess.check(serverLevel(level), master, player, RackPermission.VIEW)) {
            desk.openTerminal(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    private static ServerLevel serverLevel(Level level) {
        return (ServerLevel) level;
    }

    // --- Network ---

    @Override
    public boolean connectsOn(BlockState state, Direction side) {
        return state.getValue(PART) == Part.MASTER && side != state.getValue(FACING);
    }

    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(PART) != Part.MASTER) {
            return null;
        }
        Set<Direction> sides = EnumSet.allOf(Direction.class);
        sides.remove(state.getValue(FACING));
        // The screen is on whenever the desk is online.
        double drain = Config.TERMINAL_DESK_DRAIN.getAsDouble() + Config.TERMINAL_DESK_SCREEN_DRAIN.getAsDouble();
        return new DeviceNode(pos.immutable(), sides, 1, drain, List.of(), true);
    }

    // --- Block entity ---

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MASTER ? new TerminalDeskBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(PART) != Part.MASTER) {
            return null;
        }
        return level.isClientSide() ? createTickerHelper(type, ModBlockEntityTypes.TERMINAL_DESK.get(), TerminalDeskBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntityTypes.TERMINAL_DESK.get(), TerminalDeskBlockEntity::serverTick);
    }
}
