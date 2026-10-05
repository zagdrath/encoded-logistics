/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.midrange;

import java.util.EnumSet;
import java.util.List;
import java.util.function.BiFunction;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.zagdrath.encodedlogistics.network.DeviceNode;
import net.zagdrath.encodedlogistics.network.NetworkNode;
import net.zagdrath.encodedlogistics.network.NetworkNodeBlock;

// A Midrange peripheral that takes more than a block (HANDOFF 2): the Keypunch (1 x 2: a desk with its raised card unit)
// and the Line Printer (1 x 2: the paper leans back above it). It works beside a Midrange System, Expansion Cabinet or Integrated Midrange System, or
// cabled to the same network; it uses no lanes (I/O for the Midrange, not a network device) and doesn't carry the
// network on through it. ACTIVE: while it punches or prints.
public class MidrangePeripheralBlock extends FootprintBlock implements NetworkNodeBlock {
    private final String key;
    private final List<Vec3i> footprint;
    private final BiFunction<BlockPos, BlockState, ? extends PeripheralBlockEntity> entity;

    public MidrangePeripheralBlock(String key, List<Vec3i> footprint, BiFunction<BlockPos, BlockState, ? extends PeripheralBlockEntity> entity,
            BlockBehaviour.Properties properties) {
        super(properties);
        this.key = key;
        this.footprint = List.copyOf(footprint);
        this.entity = entity;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, Part.MASTER).setValue(MidrangeStates.ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(MidrangeStates.ACTIVE);
    }

    @Override
    protected List<Vec3i> footprint() {
        return footprint;
    }

    @Override
    protected String shapeKey(BlockState state) {
        return key;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public @Nullable NetworkNode getNetworkNode(Level level, BlockPos pos, BlockState state) {
        return new DeviceNode(pos.immutable(), EnumSet.allOf(Direction.class), 0, 0, List.of(), false);
    }

    // On the master only.
    @Override
    public @Nullable BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == Part.MASTER ? entity.apply(pos, state) : null;
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide() || state.getValue(PART) != Part.MASTER ? null
                : (tickLevel, pos, tickState, blockEntity) -> {
                    if (blockEntity instanceof PeripheralBlockEntity peripheral) {
                        PeripheralBlockEntity.serverTick(tickLevel, pos, tickState, peripheral);
                    }
                };
    }

    // Its green screen; sneaking, what comes out of it (Midranges.use).
    @Override
    protected InteractionResult use(Level level, BlockPos master, BlockState state, Player player, BlockHitResult hit) {
        return level.getBlockEntity(master) instanceof PeripheralBlockEntity peripheral ? Midranges.use(peripheral, player) : InteractionResult.PASS;
    }

    // Cards, paper: into it.
    @Override
    protected InteractionResult useItem(ItemStack stack, Level level, BlockPos master, BlockState state, Player player, InteractionHand hand,
            BlockHitResult hit) {
        return level.getBlockEntity(master) instanceof PeripheralBlockEntity peripheral ? Midranges.useItem(peripheral, stack)
                : InteractionResult.TRY_WITH_EMPTY_HAND;
    }
}
