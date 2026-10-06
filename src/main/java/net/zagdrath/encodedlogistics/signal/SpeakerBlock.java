/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// The Speaker (signals handoff 4): one block for ceilings, walls and floors - a flat 14 x 14 x 2 panel on whichever
// face it's placed - with an LED in its corner (STATE: idle, playing green, error amber). What it plays is its block
// entity's (SpeakerBlockEntity); a note it's told to play reaches clients as a block event (instrument, note and a MIDI
// note's velocity, packed: NoteInstruments.eventA / eventB).
public class SpeakerBlock extends SignalBlock {
    public enum State implements StringRepresentable {
        IDLE, PLAYING, ERROR;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<State> STATE = EnumProperty.create("state", State.class);

    public SpeakerBlock(BlockBehaviour.Properties properties) {
        super(properties, new double[] { 1, 0, 1, 15, 2, 15 });
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(STATE, State.IDLE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(STATE);
    }

    // A note (PLYNOTE, or a MIDI file's): played on the clients near it.
    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int a, int b) {
        if (level.isClientSide() && level.getBlockEntity(pos) instanceof SpeakerBlockEntity speaker) {
            SignalClientHooks.get().note(speaker, NoteInstruments.eventInstrument(a), NoteInstruments.eventNote(b),
                    NoteInstruments.eventVelocity(a, b) / 127.0F);
        }
        return true;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SpeakerBlockEntity(pos, state);
    }

    @Override
    protected BlockEntityType<? extends SignalBlockEntity> blockEntityType() {
        return ModBlockEntityTypes.SPEAKER.get();
    }
}
