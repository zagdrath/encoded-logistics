/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.plc;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Prediction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
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
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.menu.PlcMenu;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// The Programmable Logic Controller (docs/plc HANDOFF 1): wall-mounted, facing away from the wall - a steel backplate, a
// DIN rail, the CPU module (LCD, RUN / ERROR LEDs, an I/O LED per face) and four module slots beside it. Each of its six
// faces reads redstone in and drives redstone out as a Control Interface's do (weak power, its own output never read).
// State: facing, the mode (off without power, stop, run, fault; the LEDs and LCD), what's in each slot.
// Using a sensor module on it puts it in the first free slot; sneaking with an empty hand takes the last one out; any
// other use opens its screen (PLCSTS) - on a network, with the Firewall's build permission; without one, anyone (or
// only whoever placed it: config plcOpenToAnyone). Cabled to a network on any face but its front, it's a device
// there (PLC01...) with one lane, powered by the network; otherwise it runs on FE from any face.
public class PlcBlock extends BaseEntityBlock implements NetworkNodeBlock {
    public enum Mode implements StringRepresentable {
        OFF("off"), STOP("stop"), RUN("run"), FAULT("fault");

        private final String name;

        Mode(String name) {
            this.name = name;
        }

        @Override
        public String getSerializedName() {
            return name;
        }
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Mode> STATE = EnumProperty.create("state", Mode.class);
    public static final List<EnumProperty<PlcModule>> SLOTS = List.of(EnumProperty.create("slot1", PlcModule.class),
            EnumProperty.create("slot2", PlcModule.class), EnumProperty.create("slot3", PlcModule.class), EnumProperty.create("slot4", PlcModule.class));

    // The model's boxes (shapes/collision_shapes.json, facing north): backplate, DIN rail, CPU and its terminal strips,
    // the four module bodies and their terminal strips.
    private static final double[][] BOXES = { { 1, 2, 15, 15, 14, 16 }, { 1, 7, 14.2, 15, 9, 15 }, { 9.5, 3, 10, 14.5, 13, 14.2 },
            { 9.5, 13, 11, 14.5, 14, 14.2 }, { 9.5, 2, 11, 14.5, 3, 14.2 }, { 1.5, 3.5, 10.5, 9.4, 12.5, 14.2 }, { 1.5, 12.5, 11.5, 9.4, 13.5, 14.2 } };
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

    public PlcBlock(BlockBehaviour.Properties properties) {
        super(properties);
        BlockState state = stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATE, Mode.OFF);
        for (EnumProperty<PlcModule> slot : SLOTS) {
            state = state.setValue(slot, PlcModule.EMPTY);
        }
        registerDefaultState(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATE);
        SLOTS.forEach(builder::add);
    }

    // The LEDs and LCD glow a little.
    public static int lightLevel(BlockState state) {
        return state.getValue(STATE) == Mode.OFF ? 0 : 3;
    }

    // On a wall: facing out from it; else toward the player.
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing = face.getAxis().isHorizontal() ? face : context.getHorizontalDirection().getOpposite();
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer instanceof Player player && level.getBlockEntity(pos) instanceof PlcBlockEntity plc) {
            plc.setOwner(player.getUUID());
        }
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

    // --- Redstone (as the Control Interface: weak power only) ---

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    // direction: from the block asking toward this one, so the face it touches is the opposite.
    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return level.getBlockEntity(pos) instanceof PlcBlockEntity plc ? plc.emitted(direction.getOpposite()) : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 0;
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, block, orientation, movedByPiston);
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof PlcBlockEntity plc) {
            plc.inputsChanged();
        }
    }

    // --- Use ---

    // A sensor module goes in the first free slot; anything else (an EEPROM Cartridge too: it's used from the screen,
    // held) opens the screen.
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        if (!(stack.getItem() instanceof PlcModuleItem) || player.isSecondaryUseActive()) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel serverLevel && level.getBlockEntity(pos) instanceof PlcBlockEntity plc) {
            if (!PlcMenu.mayOpen(serverLevel, plc, player)) {
                return InteractionResult.SUCCESS;
            }
            if (plc.insert(stack)) {
                stack.consume(1, player);
                level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.5F, 1.6F);
            } else if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.sendOverlayMessage(Component.translatable("message.encodedlogistics.plc.slots_full"));
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)
                || !(level.getBlockEntity(pos) instanceof PlcBlockEntity plc)) {
            return InteractionResult.SUCCESS;
        }
        if (!PlcMenu.mayOpen(serverLevel, plc, player)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            ItemStack out = plc.removeLast();
            if (out.isEmpty()) {
                return InteractionResult.PASS;
            }
            player.getInventory().placeItemBackInInventory(out, Prediction.SERVER_ONLY);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 0.5F, 1.6F);
            return InteractionResult.SUCCESS;
        }
        PlcMenu.open(serverPlayer, plc);
        return InteractionResult.SUCCESS;
    }

    // --- Network ---

    // Every side but its front; one lane, its upkeep the network's while it's on one.
    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        Set<Direction> sides = EnumSet.allOf(Direction.class);
        sides.remove(state.getValue(FACING));
        return new DeviceNode(pos.immutable(), sides, 1, Config.PLC_ENERGY.getAsInt(), List.of(), true);
    }

    @Override
    public boolean connectsOn(BlockState state, Direction side) {
        return side != state.getValue(FACING);
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
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PlcBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntityTypes.PLC.get(), PlcBlockEntity::serverTick);
    }
}
