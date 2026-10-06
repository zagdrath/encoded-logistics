/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.Invocation;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.signal.AudioFiles;
import net.zagdrath.encodedlogistics.signal.CageLightBlockEntity;
import net.zagdrath.encodedlogistics.signal.NoteInstruments;
import net.zagdrath.encodedlogistics.signal.SignalBlockEntity;
import net.zagdrath.encodedlogistics.signal.SirenBlock;
import net.zagdrath.encodedlogistics.signal.SirenBlockEntity;
import net.zagdrath.encodedlogistics.signal.SirenSound;
import net.zagdrath.encodedlogistics.signal.SpeakerBlockEntity;

// The signal devices' commands (docs/signals, COMMANDS.md 7b). DEV takes a list of names, or *ALL for every device of
// the type on the network; each is checked before any is changed - not found ELC1301, offline ELC1302, another type
// ELC1303, an Alarm Strobe or Speaker whose trigger is Redstone ELC2408 - and the values likewise (ELC2401, ELC2407).
//   CHGLGT   a Cage Light's network state (*ON, *OFF, *TOGGLE; it adds to its redstone's) and light level.
//   STRSRN   an Alarm Strobe on, with its tone and light mode (*SAME: as set); ENDSRN off.
//   PLYAUD   a Speaker plays a file from the audio folder or a web URL (ELC2402-2406), its volume and loop; STPAUD stops.
//   PLYNOTE  a Speaker plays a note (an instrument, 0-24); more than one note: a sequence, the rest one per rising
//            redstone edge.
public final class SignalCommands {
    private SignalCommands() {}

    static void bind() {
        CommandRegistry.bind("CHGLGT", call -> {
            String status = call.text("STATUS");
            int level = 0;
            if (call.given("LVL") && !call.text("LVL").equals("*SAME")) {
                long wanted = call.integer("LVL");
                if (wanted < 1 || wanted > 15) {
                    throw new ElclException("ELC2401", Long.toString(wanted));
                }
                level = (int) wanted;
            }
            for (CageLightBlockEntity light : devices(call, CageLightBlockEntity.class, CageLightBlockEntity.TYPE, false)) {
                light.command(switch (status) {
                    case "*ON" -> Boolean.TRUE;
                    case "*OFF" -> Boolean.FALSE;
                    default -> null;
                }, status.equals("*TOGGLE"), level);
            }
        });
        CommandRegistry.bind("STRSRN", call -> {
            String sound = call.text("SOUND"), mode = call.text("MODE");
            SirenSound tone = sound.equals("*SAME") ? null : SirenSound.of(sound);
            SirenBlock.Light light = mode.equals("*SAME") ? null : SirenBlock.Light.valueOf(mode.substring(1).toUpperCase(Locale.ROOT));
            for (SirenBlockEntity siren : devices(call, SirenBlockEntity.class, SirenBlockEntity.TYPE, true)) {
                siren.start(tone, light);
            }
        });
        CommandRegistry.bind("ENDSRN", call -> {
            for (SirenBlockEntity siren : devices(call, SirenBlockEntity.class, SirenBlockEntity.TYPE, true)) {
                siren.end();
            }
        });
        CommandRegistry.bind("PLYAUD", call -> {
            String source = call.text("SRC").trim(), loop = call.text("LOOP");
            int volume = call.given("VOL") && !call.text("VOL").equals("*SAME") ? (int) call.integer("VOL") : -1;
            List<SpeakerBlockEntity> speakers = devices(call, SpeakerBlockEntity.class, SpeakerBlockEntity.TYPE, true);
            // The source checked once, before any speaker starts.
            ElclContext context = call.context(ElclContext.class);
            if (AudioFiles.isUrl(source)) {
                AudioFiles.checkUrl(context.server(), source);
            } else {
                AudioFiles.check(context.server(), new ElclSystem(context.server(), context.network()).name(), source);
            }
            for (SpeakerBlockEntity speaker : speakers) {
                speaker.playAudio(source, volume, loop.equals("*SAME") ? null : loop.equals("*YES"));
            }
        });
        CommandRegistry.bind("PLYNOTE", call -> {
            int instrument = Math.max(0, NoteInstruments.of(call.text("INST")));
            List<String> given = call.list("NOTE");
            int[] notes = new int[given.size()];
            for (int i = 0; i < notes.length; i++) {
                long note;
                try {
                    note = Long.parseLong(given.get(i).trim());
                } catch (NumberFormatException e) {
                    throw new ElclException("ELC2407", given.get(i));
                }
                if (note < 0 || note > 24) {
                    throw new ElclException("ELC2407", Long.toString(note));
                }
                notes[i] = (int) note;
            }
            for (SpeakerBlockEntity speaker : devices(call, SpeakerBlockEntity.class, SpeakerBlockEntity.TYPE, true)) {
                speaker.playNotes(instrument, notes);
            }
        });
        CommandRegistry.bind("STPAUD", call -> {
            for (SpeakerBlockEntity speaker : devices(call, SpeakerBlockEntity.class, SpeakerBlockEntity.TYPE, true)) {
                speaker.stop();
            }
        });
    }

    // The devices DEV names (*ALL: every one of the type on the network, online ones), each checked first.
    // triggered: the device has a trigger, which must take network commands.
    static <T extends SignalBlockEntity> List<T> devices(Invocation call, Class<T> kind, String type, boolean triggered) throws ElclException {
        ElclContext context = call.context(ElclContext.class);
        if (context == null || context.network() == null) {
            throw new ElclException("ELC1302", "*NETWORK");
        }
        List<ElclDevices.Device> all = ElclDevices.list(context.server(), context.network());
        Set<String> names = new LinkedHashSet<>();
        for (String name : call.list("DEV")) {
            names.add(name.trim().toUpperCase(Locale.ROOT));
        }
        List<T> found = new ArrayList<>();
        for (String name : names) {
            if (name.equals("*ALL")) {
                // Those that take network commands.
                for (ElclDevices.Device device : all) {
                    if (kind.isInstance(device.entity()) && device.online() && !found.contains(kind.cast(device.entity()))
                            && (!triggered || takesNetwork(kind.cast(device.entity())))) {
                        found.add(kind.cast(device.entity()));
                    }
                }
                continue;
            }
            ElclDevices.Device device = null;
            for (ElclDevices.Device candidate : all) {
                if (candidate.name().equalsIgnoreCase(name)) {
                    device = candidate;
                }
            }
            if (device == null) {
                throw new ElclException("ELC1301", name);
            }
            if (!kind.isInstance(device.entity())) {
                throw new ElclException("ELC1303", name, device.type().isEmpty() ? type : device.type());
            }
            T signal = kind.cast(device.entity());
            if (!signal.isOnline()) {
                throw new ElclException("ELC1302", name);
            }
            if (triggered && !takesNetwork(signal)) {
                throw new ElclException("ELC2408", name);
            }
            if (!found.contains(signal)) {
                found.add(signal);
            }
        }
        return found;
    }

    private static boolean takesNetwork(SignalBlockEntity signal) {
        SignalBlockEntity.Trigger trigger = signal instanceof SirenBlockEntity siren ? siren.trigger()
                : signal instanceof SpeakerBlockEntity speaker ? speaker.trigger() : SignalBlockEntity.Trigger.BOTH;
        return trigger.network();
    }
}
