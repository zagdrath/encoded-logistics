/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.signal;

import java.util.function.BooleanSupplier;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

// A signal device's sound: its own volume (0-1) fading to nothing at its range in blocks - not Minecraft's attenuation,
// which ties range to loudness - while alive says it's still wanted (the device there and still sounding). Positioned
// at the device, so it pans.
class DeviceSound extends AbstractTickableSoundInstance {
    private final Vec3 at;
    private final float loudness;
    private final int range;
    private final BooleanSupplier alive;

    DeviceSound(SoundEvent event, SoundSource source, Vec3 at, float loudness, int range, float pitch, BooleanSupplier alive) {
        super(event, source, SoundInstance.createUnseededRandom());
        this.at = at;
        this.loudness = loudness;
        this.range = Math.max(1, range);
        this.alive = alive;
        this.x = at.x;
        this.y = at.y;
        this.z = at.z;
        this.pitch = pitch;
        this.attenuation = SoundInstance.Attenuation.NONE;
        this.volume = gain();
    }

    private float gain() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return 0;
        }
        double distance = player.getEyePosition().distanceTo(at);
        return loudness * Mth.clamp(1.0F - (float) (distance / range), 0.0F, 1.0F);
    }

    // Stops it (the device stopped, or something else replaced it).
    void end() {
        stop();
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }

    @Override
    public void tick() {
        if (!alive.getAsBoolean()) {
            stop();
            return;
        }
        volume = gain();
    }
}
