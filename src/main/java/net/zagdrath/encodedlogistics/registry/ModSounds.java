/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.registry;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.zagdrath.encodedlogistics.EncodedLogistics;

// The mod's sounds (sounds.json): the Server Rack's doors.
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, EncodedLogistics.MODID);

    public static final Holder<SoundEvent> RACK_DOOR_OPEN = SOUND_EVENTS.register("block.rack.door_open",
            SoundEvent::createVariableRangeEvent);
    public static final Holder<SoundEvent> RACK_DOOR_CLOSE = SOUND_EVENTS.register("block.rack.door_close",
            SoundEvent::createVariableRangeEvent);

    private ModSounds() {}
}
