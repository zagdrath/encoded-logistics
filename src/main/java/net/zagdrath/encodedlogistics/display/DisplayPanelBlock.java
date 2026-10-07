/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.display;

import com.mojang.serialization.MapCodec;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.menu.DisplayPanelMenu;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// The Display Panel (HANDOFF 1): an 8 px slab on a wall, its screen toward FACING. Panels side by side or stacked merge
// into screens (DisplayScreens); TOP / BOTTOM / LEFT / RIGHT say an edge is joined to the next panel of the screen (no
// bezel there; left and right as seen from the front), LED that this is the screen's bottom-right block (the status
// LED), STATE the screen's state. It joins the network on its back and bottom (a cable, or the device or cable behind
// it) and to the panels beside it; a screen is one device with one lane, on its master.
public class DisplayPanelBlock extends BaseEntityBlock implements NetworkNodeBlock {
    private static final MapCodec<DisplayPanelBlock> CODEC = simpleCodec(DisplayPanelBlock::new);

    @Override
    protected MapCodec<DisplayPanelBlock> codec() {
        return CODEC;
    }

    public enum Shown implements StringRepresentable {
        OFF("off"), BOOT("boot"), ONLINE("online"), NO_SIGNAL("nosignal");

        private final String name;

        Shown(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty TOP = BooleanProperty.create("top"), BOTTOM = BooleanProperty.create("bottom"),
            LEFT = BooleanProperty.create("left"), RIGHT = BooleanProperty.create("right"), LED = BooleanProperty.create("led");
    public static final EnumProperty<Shown> STATE = EnumProperty.create("state", Shown.class);

    // The slab (shapes/collision_shapes.json, facing north): glass, body, mounting plate.
    private static final double[][] BOXES = { { 0, 0, 8, 16, 16, 8.5 }, { 0, 0, 8.5, 16, 16, 15 }, { 1, 1, 15, 15, 15, 16 } };
    private static final Map<Direction, VoxelShape> SHAPES = new EnumMap<>(Direction.class);

    static {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            VoxelShape shape = Shapes.empty();
            for (double[] box : BOXES) {
                shape = Shapes.or(shape, rotated(box, facing));
            }
            SHAPES.put(facing, shape.optimize());
        }
    }

    // A north-facing box turned to face another way.
    private static VoxelShape rotated(double[] b, Direction facing) {
        double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
        return switch (facing) {
            case SOUTH -> Block.box(16 - x1, b[1], 16 - z1, 16 - x0, b[4], 16 - z0);
            case EAST -> Block.box(16 - z1, b[1], x0, 16 - z0, b[4], x1);
            case WEST -> Block.box(z0, b[1], 16 - x1, z1, b[4], 16 - x0);
            default -> Block.box(x0, b[1], z0, x1, b[4], z1);
        };
    }

    public DisplayPanelBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TOP, false).setValue(BOTTOM, false).setValue(LEFT, false)
                .setValue(RIGHT, false).setValue(LED, true).setValue(STATE, Shown.NO_SIGNAL));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TOP, BOTTOM, LEFT, RIGHT, LED, STATE);
    }

    // On a wall: the screen faces out from it; else toward the player.
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES.get(state.getValue(FACING));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    // --- Merging ---

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
            DisplayScreens.remerge(serverLevel, List.of(pos), state.getValue(FACING));
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Direction facing = state.getValue(FACING), right = DisplayScreens.right(facing);
        List<BlockPos> around = new ArrayList<>();
        for (Direction side : new Direction[] { right, right.getOpposite(), Direction.UP, Direction.DOWN }) {
            around.add(pos.relative(side));
        }
        DisplayScreens.remerge(level, around, facing);
    }

    // --- Touch ---

    // Using the screen (HANDOFF 4, 5). With an empty hand: its configuration (the Firewall's build permission) - but once
    // it has touch triggers, only while sneaking; without sneaking that's a touch. With an item, not sneaking: a touch.
    // A touch fires *DSPTOUCH with the screen's name, the region and the canvas point (no permission needed: scripts
    // decide what a touch may do); at most four a second for each player.
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof DisplayPanelBlockEntity panel)
                || !(level.getBlockEntity(panel.masterPos()) instanceof DisplayPanelBlockEntity master)) {
            return InteractionResult.PASS;
        }
        boolean touchable = hit.getDirection() == state.getValue(FACING) && DisplayTouch.hasTriggers(serverLevel, master);
        if (touchable && !player.isSecondaryUseActive()) {
            DisplayTouch.touch(serverLevel, master, pos, hit.getLocation(), player);
        } else if (NetworkAccess.check(serverLevel, master.getBlockPos(), player, RackPermission.BUILD)) {
            DisplayPanelMenu.open(serverPlayer, master.getBlockPos());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (player.isSecondaryUseActive() || hit.getDirection() != state.getValue(FACING)) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof DisplayPanelBlockEntity panel
                && level.getBlockEntity(panel.masterPos()) instanceof DisplayPanelBlockEntity master) {
            DisplayTouch.touch(serverLevel, master, pos, hit.getLocation(), player);
        }
        return InteractionResult.SUCCESS;
    }

    // --- Network ---

    // Everything but its screen side: its back and bottom, and the panels beside it. The master is the device.
    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        Set<Direction> sides = EnumSet.allOf(Direction.class);
        sides.remove(state.getValue(FACING));
        boolean master = !(level.getBlockEntity(pos) instanceof DisplayPanelBlockEntity panel) || panel.isMaster();
        int panels = level.getBlockEntity(pos) instanceof DisplayPanelBlockEntity panel ? panel.width() * panel.height() : 1;
        return new DeviceNode(pos.immutable(), sides, master ? 1 : 0, master ? Config.DISPLAY_PANEL_DRAIN.getAsDouble() * panels : 0, List.of(), true);
    }

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DisplayPanelBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntityTypes.DISPLAY_PANEL.get(), DisplayPanelBlockEntity::serverTick);
    }
}
