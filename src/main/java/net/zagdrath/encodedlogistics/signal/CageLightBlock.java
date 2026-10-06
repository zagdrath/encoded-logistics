/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// An Industrial Cage Light (signals handoff 2), one block per dye colour sharing one model pair (caged_light_off / _on)
// whose bulb is tinted with the dye (SignalTints). LIT picks the model; LEVEL is the light it gives while lit (1-15, set
// on its screen or by CHGLGT LVL).
public class CageLightBlock extends SignalBlock {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    public static final IntegerProperty LEVEL = IntegerProperty.create("level", 1, 15);

    private final DyeColor color;

    public CageLightBlock(DyeColor color, BlockBehaviour.Properties properties) {
        // The cage, and the base under it.
        super(properties, new double[] { 5, 1, 5, 11, 9, 11 }, new double[] { 4, 0, 4, 12, 1, 12 });
        this.color = color;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(LIT, false).setValue(LEVEL, 15));
    }

    public DyeColor color() {
        return color;
    }

    public static int lightLevel(BlockState state) {
        return state.getValue(LIT) ? state.getValue(LEVEL) : 0;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT, LEVEL);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CageLightBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends SignalBlockEntity> blockEntityType() {
        return ModBlockEntityTypes.CAGE_LIGHT.get();
    }
}
