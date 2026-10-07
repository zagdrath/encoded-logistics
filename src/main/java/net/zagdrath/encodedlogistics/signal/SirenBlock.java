/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

import com.mojang.serialization.MapCodec;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// An Alarm Strobe (signals handoff 3): a beacon light on its siren sounder, in green, amber or red - its lens the lamps'
// steel whites times the dye (lime, orange, red), so strobes and Cage Lights match. LIGHT: off, or the lens lit solid
// or flashing slow, medium or fast (the lens textures' animations); the siren is the block entity's (SirenBlockEntity).
public class SirenBlock extends SignalBlock {
    // 26.1 requires a block codec. Nothing decodes this block type, and its constructor arguments aren't
    // data, so the codec stands for this instance.
    @Override
    protected MapCodec<SirenBlock> codec() {
        return MapCodec.unit(this);
    }

    public enum Colour {
        GREEN(DyeColor.LIME), AMBER(DyeColor.ORANGE), RED(DyeColor.RED);

        private final DyeColor dye;

        Colour(DyeColor dye) {
            this.dye = dye;
        }

        public DyeColor dye() {
            return dye;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        public Component label() {
            return Component.translatable("gui.encodedlogistics.signal.colour." + id());
        }
    }

    public enum Light implements StringRepresentable {
        OFF, SOLID, SLOW, MEDIUM, FAST;

        public Component label() {
            return Component.translatable("gui.encodedlogistics.signal.light." + getSerializedName());
        }

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<Light> LIGHT = EnumProperty.create("light", Light.class);

    private final Colour colour;

    public SirenBlock(Colour colour, BlockBehaviour.Properties properties) {
        // The round sounder, its mount and stem, the black base and the lens with its cap.
        super(properties, new double[] { 3.5, 0, 3.5, 12.5, 3, 12.5 }, new double[] { 6.5, 3, 6.5, 9.5, 5.5, 9.5 },
                new double[] { 4.5, 5.5, 4.5, 11.5, 8.5, 11.5 }, new double[] { 5, 8.5, 5, 11, 16, 11 });
        this.colour = colour;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(LIGHT, Light.OFF));
    }

    public Colour colour() {
        return colour;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIGHT);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SirenBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends SignalBlockEntity> blockEntityType() {
        return ModBlockEntityTypes.SIREN.get();
    }
}
