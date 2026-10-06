/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.zagdrath.encodedlogistics.registry.ModSounds;

// An Alarm Strobe's tones (signals handoff 7: sounds/block/siren/*.ogg, synthesised for the mod), each with its
// sample's length in ticks - the siren plays it again at the end of each, so it loops and stops cleanly - or none
// (light only).
public enum SirenSound {
    WAIL(80), YELP(40), KLAXON(32), BELL(40), HORN(40), BEEP(24), NONE(0);

    private final int ticks;

    SirenSound(int ticks) {
        this.ticks = ticks;
    }

    public int ticks() {
        return ticks;
    }

    public @Nullable Holder<SoundEvent> event() {
        return switch (this) {
            case WAIL -> ModSounds.SIREN_WAIL;
            case YELP -> ModSounds.SIREN_YELP;
            case KLAXON -> ModSounds.SIREN_KLAXON;
            case BELL -> ModSounds.SIREN_BELL;
            case HORN -> ModSounds.SIREN_HORN;
            case BEEP -> ModSounds.SIREN_BEEP;
            case NONE -> null;
        };
    }

    public Component label() {
        return Component.translatable("gui.encodedlogistics.signal.sound." + name().toLowerCase(Locale.ROOT));
    }

    // *WAIL ... *NONE (STRSRN SOUND).
    public static @Nullable SirenSound of(String special) {
        for (SirenSound sound : values()) {
            if (special.equalsIgnoreCase("*" + sound.name())) {
                return sound;
            }
        }
        return null;
    }
}
