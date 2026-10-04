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

// The mod's sounds (sounds.json): the Server Rack's doors, the Rack Console's drawer and screen, the Tape Library's
// picker, the Terminal Desk's CRT and keys.
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS = DeferredRegister.create(Registries.SOUND_EVENT, EncodedLogistics.MODID);

    public static final Holder<SoundEvent> RACK_DOOR_OPEN = SOUND_EVENTS.register("block.rack.door_open",
            SoundEvent::createVariableRangeEvent);
    public static final Holder<SoundEvent> RACK_DOOR_CLOSE = SOUND_EVENTS.register("block.rack.door_close",
            SoundEvent::createVariableRangeEvent);

    // A UPS on battery: four beeps every 30 s, a rapid beep when low, one beep when power's back.
    public static final Holder<SoundEvent> UPS_ALARM = SOUND_EVENTS.register("block.rack.ups_alarm", SoundEvent::createVariableRangeEvent);
    public static final Holder<SoundEvent> UPS_ALARM_LOW = SOUND_EVENTS.register("block.rack.ups_alarm_low", SoundEvent::createVariableRangeEvent);
    public static final Holder<SoundEvent> UPS_BEEP = SOUND_EVENTS.register("block.rack.ups_beep", SoundEvent::createVariableRangeEvent);

    public static final Holder<SoundEvent> RACK_CONSOLE_SLIDE_OUT = SOUND_EVENTS.register("block.rack.console_slide_out",
            SoundEvent::createVariableRangeEvent);
    public static final Holder<SoundEvent> RACK_CONSOLE_SLIDE_IN = SOUND_EVENTS.register("block.rack.console_slide_in",
            SoundEvent::createVariableRangeEvent);
    public static final Holder<SoundEvent> RACK_CONSOLE_HINGE = SOUND_EVENTS.register("block.rack.console_hinge",
            SoundEvent::createVariableRangeEvent);
    public static final Holder<SoundEvent> RACK_PICKER_MOVE = SOUND_EVENTS.register("block.rack.picker_move",
            SoundEvent::createVariableRangeEvent);
    public static final Holder<SoundEvent> RACK_TAPE_LOAD = SOUND_EVENTS.register("block.rack.tape_load",
            SoundEvent::createVariableRangeEvent);

    public static final Holder<SoundEvent> DESK_HUM = SOUND_EVENTS.register("block.terminal_desk.crt_hum", SoundEvent::createVariableRangeEvent);
    public static final Holder<SoundEvent> DESK_DEGAUSS = SOUND_EVENTS.register("block.terminal_desk.degauss", SoundEvent::createVariableRangeEvent);
    public static final Holder<SoundEvent> DESK_KEY_CLACK = SOUND_EVENTS.register("block.terminal_desk.key_clack",
            SoundEvent::createVariableRangeEvent);

    private ModSounds() {}
}
