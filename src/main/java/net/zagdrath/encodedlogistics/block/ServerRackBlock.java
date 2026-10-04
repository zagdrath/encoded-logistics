/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.BlockNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.RackTargeting;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// The Server Rack: a 42U enclosed rack, 1 wide, 3 tall and 2 deep (RackGeometry). Six blocks of this one block: the
// master (the middle of the front column) holds the block entity and draws the whole frame; the other five are
// invisible dummies that know where they are (PART_INDEX). Placed with its front toward the player, from the targeted
// block up and back; breaking any of it breaks all of it.
//
// Right-click the front to open or close the front door, the back for both rear doors; sneak-right-click the front for
// the rack's screen. With the front door open, using a rack device on the front mounts it at the unit looked at.
//
// On the network: cables join it on the back face of the back blocks and on the top of the top blocks. Its blocks pass
// lanes through to each other; the master is the network device, using a lane for each device in it (RackBlockEntity).
public class ServerRackBlock extends BaseEntityBlock implements NetworkNodeBlock {
    public enum Part implements StringRepresentable {
        MASTER, DUMMY;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final EnumProperty<Part> PART = EnumProperty.create("part", Part.class);
    public static final IntegerProperty PART_INDEX = IntegerProperty.create("index", 0, RackGeometry.PARTS - 1);

    public ServerRackBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.DUMMY)
                .setValue(PART_INDEX, RackGeometry.BOTTOM_FRONT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART, PART_INDEX);
    }

    public static boolean isMaster(BlockState state) {
        return state.getValue(PART) == Part.MASTER;
    }

    public static BlockPos masterPos(BlockState state, BlockPos pos) {
        return RackGeometry.masterPos(pos, state.getValue(FACING), state.getValue(PART_INDEX));
    }

    public static @Nullable RackBlockEntity rack(BlockGetter level, BlockPos pos, BlockState state) {
        return level.getBlockEntity(masterPos(state, pos)) instanceof RackBlockEntity rack ? rack : null;
    }

    private BlockState part(BlockState state, int index) {
        return state.setValue(PART_INDEX, index).setValue(PART, index == RackGeometry.MASTER ? Part.MASTER : Part.DUMMY);
    }

    // --- Placing and breaking ---

    // The targeted block becomes the bottom front; it fails, with a message, when any of the six isn't free.
    @Override
    public @Nullable BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        BlockPos bottom = context.getClickedPos();
        BlockPos master = RackGeometry.masterPos(bottom, facing, RackGeometry.BOTTOM_FRONT);
        for (int index = 0; index < RackGeometry.PARTS; index++) {
            if (index == RackGeometry.BOTTOM_FRONT) {
                continue;
            }
            BlockPos pos = RackGeometry.partPos(master, facing, index);
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.getBlockState(pos).canBeReplaced(context)) {
                if (context.getPlayer() instanceof ServerPlayer player) {
                    player.sendOverlayMessage(Component.translatable("message.encodedlogistics.rack.no_room"));
                }
                return null;
            }
        }
        return part(defaultBlockState().setValue(FACING, facing), RackGeometry.BOTTOM_FRONT);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity by, ItemStack stack) {
        super.setPlacedBy(level, pos, state, by, stack);
        if (level.isClientSide()) {
            return;
        }
        Direction facing = state.getValue(FACING);
        BlockPos master = masterPos(state, pos);
        for (int index = 0; index < RackGeometry.PARTS; index++) {
            if (index != state.getValue(PART_INDEX)) {
                level.setBlock(RackGeometry.partPos(master, facing, index), part(state, index), Block.UPDATE_ALL);
            }
        }
    }

    // Breaking one block breaks the rest (without drops: the broken block drops the rack, the master's block entity its
    // devices).
    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        Direction facing = state.getValue(FACING);
        BlockPos master = masterPos(state, pos);
        for (int index = 0; index < RackGeometry.PARTS; index++) {
            BlockPos other = RackGeometry.partPos(master, facing, index);
            BlockState there = level.getBlockState(other);
            if (!other.equals(pos) && there.is(this) && there.getValue(FACING) == facing && there.getValue(PART_INDEX) == index) {
                level.destroyBlock(other, false);
            }
        }
        ControllerStructures.get(level).markTopologyChanged();
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this) && level instanceof ServerLevel serverLevel) {
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
        return isMaster(state) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    // --- Using it ---

    // A rack device used on the open front mounts at the unit looked at (RackTargeting). Any other item goes to the
    // device at that unit (RackDevice#useItem), then to the others in the rack (a Handheld Terminal used anywhere on the
    // rack links to its Wireless Controller).
    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hit) {
        RackDeviceType type = RackDeviceType.of(stack);
        RackBlockEntity rack = rack(level, pos, state);
        if (rack == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        boolean front = RackGeometry.face(hit.getDirection(), state.getValue(FACING)) == RackGeometry.Face.FRONT;
        if (type == null && !stack.isEmpty()) {
            return useItemOnDevice(stack, rack, level, player, hit, front);
        }
        if (type == null || !front) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        RackTargeting.Target target = RackTargeting.pick(rack, hit.getDirection(), player.getEyePosition(), player.getViewVector(1.0F));
        if (target == null) {
            return InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (level instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
            rack.installFromHand(serverPlayer, stack, target.u());
        }
        return InteractionResult.SUCCESS;
    }

    private static InteractionResult useItemOnDevice(ItemStack stack, RackBlockEntity rack, Level level, Player player, BlockHitResult hit,
            boolean front) {
        RackDevice target = front ? targeted(rack, hit, player) : null;
        if (!(level instanceof ServerLevel) || !(player instanceof ServerPlayer serverPlayer)) {
            // The server decides; the client just doesn't open the door.
            return target != null || rack.devices().stream().anyMatch(device -> device.type() == RackDeviceType.WIRELESS_CONTROLLER)
                    ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
        }
        if (target != null && target.useItem(serverPlayer, stack, true)) {
            return InteractionResult.SUCCESS;
        }
        for (RackDevice device : List.copyOf(rack.devices())) {
            if (device != target && device.useItem(serverPlayer, stack, false)) {
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    // The device at the unit looked at through the open front, or null.
    private static @Nullable RackDevice targeted(RackBlockEntity rack, BlockHitResult hit, Player player) {
        RackTargeting.Target target = RackTargeting.pick(rack, hit.getDirection(), player.getEyePosition(), player.getViewVector(1.0F));
        return target != null ? rack.deviceAt(target.u()) : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        RackBlockEntity rack = rack(level, pos, state);
        if (rack == null) {
            return InteractionResult.PASS;
        }
        RackGeometry.Face face = RackGeometry.face(hit.getDirection(), state.getValue(FACING));
        if (face == RackGeometry.Face.OTHER) {
            return InteractionResult.PASS;
        }
        // A device used through the open front (the Rack Console's drawer).
        RackDevice device = face == RackGeometry.Face.FRONT ? targeted(rack, hit, player) : null;
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (device != null && device.use(serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (face == RackGeometry.Face.FRONT && player.isSecondaryUseActive()) {
            rack.openMenu(serverPlayer);
        } else if (face == RackGeometry.Face.FRONT) {
            rack.setFrontOpen(!rack.isFrontOpen());
        } else {
            rack.setRearOpen(!rack.isRearOpen());
        }
        return InteractionResult.SUCCESS;
    }

    // --- Network ---

    @Override
    public boolean connectsOn(BlockState state, Direction side) {
        return RackGeometry.connectsOn(state.getValue(PART_INDEX), state.getValue(FACING), side);
    }

    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        int index = state.getValue(PART_INDEX);
        Direction facing = state.getValue(FACING);
        Set<Direction> sides = EnumSet.noneOf(Direction.class);
        for (Direction side : RackGeometry.internalSides(index, facing)) {
            sides.add(side);
        }
        for (Direction side : Direction.values()) {
            if (RackGeometry.connectsOn(index, facing, side)) {
                sides.add(side);
            }
        }
        if (isMaster(state) && level.getBlockEntity(pos) instanceof RackBlockEntity rack) {
            return rack.networkNode(sides);
        }
        return new BlockNode(pos.immutable(), sides, 0, true);
    }

    // --- Block entity ---

    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return isMaster(state) ? new RackBlockEntity(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (!isMaster(state)) {
            return null;
        }
        return level.isClientSide() ? createTickerHelper(type, ModBlockEntityTypes.SERVER_RACK.get(), RackBlockEntity::clientTick)
                : createTickerHelper(type, ModBlockEntityTypes.SERVER_RACK.get(), RackBlockEntity::serverTick);
    }
}
