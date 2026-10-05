/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.zagdrath.encodedlogistics.midrange.MidrangeStates;
import net.zagdrath.encodedlogistics.midrange.MidrangeSystemBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModSounds;

// A Midrange System's fans on the client (SOUNDS.txt): looping while it's on (any state but off).
public final class MidrangeSounds {
    private static final Map<BlockPos, Fan> RUNNING = new HashMap<>();

    private MidrangeSounds() {}

    public static void tick(MidrangeSystemBlockEntity system) {
        if (!on(system)) {
            return;
        }
        Fan fan = RUNNING.get(system.getBlockPos());
        if (fan == null || fan.isStopped()) {
            fan = new Fan(system);
            RUNNING.put(system.getBlockPos().immutable(), fan);
            Minecraft.getInstance().getSoundManager().play(fan);
        }
    }

    private static boolean on(MidrangeSystemBlockEntity system) {
        return system.getBlockState().hasProperty(MidrangeStates.STATE) && system.getBlockState().getValue(MidrangeStates.STATE) != MidrangeStates.Run.OFF;
    }

    private static final class Fan extends AbstractTickableSoundInstance {
        private final MidrangeSystemBlockEntity system;

        Fan(MidrangeSystemBlockEntity system) {
            super(ModSounds.MIDRANGE_FAN.value(), SoundSource.BLOCKS, RandomSource.create());
            this.system = system;
            looping = true;
            delay = 0;
            volume = 0.25F;
            BlockPos pos = system.getBlockPos();
            x = pos.getX() + 0.5;
            y = pos.getY() + 0.5;
            z = pos.getZ() + 0.5;
        }

        @Override
        public void tick() {
            if (system.isRemoved() || !on(system)) {
                RUNNING.remove(system.getBlockPos());
                stop();
            }
        }
    }
}
