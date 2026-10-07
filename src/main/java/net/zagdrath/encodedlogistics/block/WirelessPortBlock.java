/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.block;

import com.mojang.serialization.MapCodec;
import java.util.EnumSet;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.blockentity.WirelessPortBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;
import net.zagdrath.encodedlogistics.network.NetworkPart;
import net.zagdrath.encodedlogistics.network.RemoteLink;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.part.PortPart;
import net.zagdrath.encodedlogistics.rack.NetworkAccess;
import net.zagdrath.encodedlogistics.rack.RackPermission;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;
import net.zagdrath.encodedlogistics.wireless.WirelessShapes;
import net.zagdrath.encodedlogistics.wireless.WirelessState;

// The Wireless Ingress and Egress Ports: a panel mounted on the inventory it faces (FACING: toward it, placed against the
// face clicked), pulling from it into its Wireless Controller's network or pushing into it, with no cable. It's a cabled
// port in all but its link (WirelessPortBlockEntity holds an Ingress or Egress Port part on that side: the same
// filters, modules, redstone modes and screen). One lane over its link to the controller's rack, one of the controller's
// clients, wirelessEnergyMultiplier times a cabled port's drain. STATE: off, linking, online, fault.
public class WirelessPortBlock extends BaseEntityBlock implements NetworkNodeBlock {
    // 26.1 requires a block codec. Nothing decodes this block type, and its constructor arguments aren't
    // data, so the codec stands for this instance.
    @Override
    protected MapCodec<WirelessPortBlock> codec() {
        return MapCodec.unit(this);
    }

    public static final EnumProperty<Direction> FACING = BlockStateProperties.FACING;
    public static final EnumProperty<WirelessState> STATE = WirelessState.STATE;

    private final boolean ingress;

    public WirelessPortBlock(boolean ingress, BlockBehaviour.Properties properties) {
        super(properties);
        this.ingress = ingress;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATE, WirelessState.OFF));
    }

    public boolean ingress() {
        return ingress;
    }

    // The part it holds.
    public PartType partType() {
        return ingress ? PartType.INGRESS_PORT : PartType.EGRESS_PORT;
    }

    public static int lightLevel(BlockState state) {
        return state.getValue(STATE) == WirelessState.OFF ? 0 : 3;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, STATE);
    }

    // Against the face clicked: facing the block it's on.
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getClickedFace().getOpposite());
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
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return WirelessShapes.PORT.get(state.getValue(FACING));
    }

    // Opens the port's screen (the network's Firewall: build permission, as for a cabled port).
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof WirelessPortBlockEntity port && port.port() instanceof PortPart part
                && NetworkAccess.check((ServerLevel) level, pos, player, RackPermission.BUILD)) {
            part.openMenu(serverPlayer);
        }
        return InteractionResult.SUCCESS;
    }

    // --- Network ---

    @Override
    public boolean connectsOn(BlockState state, Direction side) {
        return false;
    }

    // A device with no cable: one lane, and the link to its controller's rack.
    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        double drain = partType().drain() * Config.WIRELESS_ENERGY_MULTIPLIER.getAsDouble();
        List<RemoteLink> links = level.getBlockEntity(pos) instanceof WirelessPortBlockEntity port && port.link() != null
                ? List.of(new RemoteLink(port.link().rackNode(), 1, true)) : List.of();
        return new DeviceNode(pos.immutable(), EnumSet.noneOf(Direction.class), partType().lanes(), drain, List.of(new NetworkPart(asItem(), drain)), false,
                links);
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
        return new WirelessPortBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() ? null : createTickerHelper(type, ModBlockEntityTypes.WIRELESS_PORT.get(),
                (tickLevel, pos, tickState, port) -> port.serverTick((ServerLevel) tickLevel));
    }
}
