/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.zagdrath.encodedlogistics.block.TerminalDeskBlock;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;
import net.zagdrath.encodedlogistics.registry.ModSounds;

// The Terminal Desk's sounds on the client (SOUNDS.txt): the CRT's hum, looping while its screen is lit, and the keys
// clacking as the player types at it (at most one every 2 ticks).
public final class DeskSounds {
    private static final Map<BlockPos, Hum> HUMMING = new HashMap<>();
    private static long lastClack;

    private DeskSounds() {}

    public static void tick(TerminalDeskBlockEntity desk) {
        if (desk.screen() == TerminalDeskBlock.Screen.OFF) {
            return;
        }
        Hum hum = HUMMING.get(desk.getBlockPos());
        if (hum == null || hum.isStopped()) {
            hum = new Hum(desk);
            HUMMING.put(desk.getBlockPos().immutable(), hum);
            Minecraft.getInstance().getSoundManager().play(hum);
        }
    }

    public static void keyClack() {
        Minecraft minecraft = Minecraft.getInstance();
        long now = minecraft.level != null ? minecraft.level.getGameTime() : 0;
        if (now - lastClack < 2) {
            return;
        }
        lastClack = now;
        minecraft.getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.DESK_KEY_CLACK.value(), 0.9F + RandomSource.create().nextFloat() * 0.2F,
                0.5F));
    }

    private static final class Hum extends AbstractTickableSoundInstance {
        private final TerminalDeskBlockEntity desk;

        Hum(TerminalDeskBlockEntity desk) {
            super(ModSounds.DESK_HUM.value(), SoundSource.BLOCKS, RandomSource.create());
            this.desk = desk;
            looping = true;
            delay = 0;
            volume = 0.2F;
            BlockPos pos = desk.getBlockPos();
            x = pos.getX() + 0.5;
            y = pos.getY() + 1.0;
            z = pos.getZ() + 0.5;
        }

        @Override
        public void tick() {
            if (desk.isRemoved() || desk.screen() == TerminalDeskBlock.Screen.OFF) {
                HUMMING.remove(desk.getBlockPos());
                stop();
            }
        }
    }
}
