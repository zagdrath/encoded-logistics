/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.registry.ModBlockEntityTypes;

// A Speaker's settings and what it's playing (signals handoff 4): its source - an audio file from its system's audio
// folder (AudioFiles), a web URL (MP3 / OGG, behind the server's web audio settings), or a note (any note block
// instrument, pitch 0-24; ELCL's PLYNOTE may give a sequence, each rising redstone edge playing the next) - its volume,
// range (up to speakerMaxRange), loop and trigger. A rising redstone edge plays; the signal going off stops a looping
// source. Each play is numbered (play) with its start tick: the clients (SignalSounds) fetch the file by its hash or
// the URL themselves, start it where it should be by now, and report the URL's length or why it failed (an error: the
// amber LED and the popup's message). A play that isn't looping ends when its length has passed. Type SPK.
//
// A MIDI file (PLYMID, or the MIDI source: MidiFile) plays on the server, as note block notes. The speaker that starts
// it is the play's clock (its conductor): each tick it sends the notes falling on that tick - as block events, the
// velocity packed in with the instrument and note (NoteInstruments.eventA / eventB) - to the speakers its parts map
// names (MidiParts: tracks or channels to speakers; none: every part on itself), at most midiMaxNotes each per tick,
// the loudest kept. Those speakers follow it (playing, its play number) and stop when it does - STPAUD on any of them
// stops them all - or when it's gone; one given no part stays silent. Started again (a new play), one leaves the play
// and the conductor skips it.
public class SpeakerBlockEntity extends SignalBlockEntity {
    public static final String TYPE = "SPK";

    public enum Source {
        FILE, URL, NOTE, MIDI;

        public Component label() {
            return Component.translatable("gui.encodedlogistics.signal.source." + name().toLowerCase(Locale.ROOT));
        }
    }

    // What a MIDI parts map numbers: tracks, channels, or (Auto) channels in a format 0 file and tracks in format 1.
    public enum MapBy {
        AUTO, TRACK, CHANNEL;

        public Component label() {
            return Component.translatable("gui.encodedlogistics.signal.map_by." + name().toLowerCase(Locale.ROOT));
        }
    }

    // A part assigned for a playback: the number (0: all) and the speaker's position.
    private record Assigned(int number, BlockPos speaker) {}

    // A MIDI playback worked out before anything changes: the file, whether its parts are channels, who plays what,
    // and every speaker in it (the conductor first).
    private record Plan(AudioFiles.Midi file, boolean byChannel, List<Assigned> parts, List<SpeakerBlockEntity> speakers) {}

    // A MIDI note sent (for game tests): its conductor, the speaker playing it, the game tick and the note.
    public record Sent(BlockPos clock, BlockPos speaker, long tick, MidiFile.Note note) {}

    private static final List<Consumer<Sent>> LISTENERS = new ArrayList<>();

    // Notes more than this many ticks late (a pause, the chunk unloaded) are skipped rather than played at once.
    private static final int CATCH_UP = 2;

    // The instruments PLYNOTE names (NoteInstruments.NAMES), in its order: a note's block event carries the index.
    public static final NoteBlockInstrument[] INSTRUMENTS = { NoteBlockInstrument.HARP, NoteBlockInstrument.BASS, NoteBlockInstrument.SNARE,
            NoteBlockInstrument.HAT, NoteBlockInstrument.BASEDRUM, NoteBlockInstrument.BELL, NoteBlockInstrument.FLUTE, NoteBlockInstrument.CHIME,
            NoteBlockInstrument.GUITAR, NoteBlockInstrument.XYLOPHONE, NoteBlockInstrument.IRON_XYLOPHONE, NoteBlockInstrument.COW_BELL,
            NoteBlockInstrument.DIDGERIDOO, NoteBlockInstrument.BIT, NoteBlockInstrument.BANJO, NoteBlockInstrument.PLING };
    public static final int MAX_NOTES = NoteInstruments.MAX_NOTES, NOTE_TICKS = 10;

    private Source source = Source.FILE;
    private String file = "", url = "";
    private int instrument;
    private int[] notes = { 12 };
    private int nextNote;
    private int volume = 70, range = 32;
    private boolean loop;
    private Trigger trigger = Trigger.BOTH;
    // The MIDI source: its file, what its map numbers and the map (empty: every part here).
    private String midi = "";
    private MapBy mapBy = MapBy.AUTO;
    private List<MidiParts.Part> map = List.of();

    // What's playing: the play's number and start tick, the file's hash or the URL, its length in ticks (0: not known
    // yet), and the limits the clients keep a URL to; or why it couldn't play.
    private boolean playing;
    private int play;
    private long started;
    private Source playingSource = Source.FILE;
    private String hash = "", playingName = "", playingUrl = "", error = "";
    private int lengthTicks, maxBytes, maxSeconds;
    // A MIDI play: the conductor (itself, if it is) and its play number, and the parts this one plays (0: all; none:
    // silent) - tracks, or channels. The conductor's: the parts it hands out, and (not saved) the notes and where
    // it's got to.
    private @Nullable BlockPos conductor;
    private int conductorPlay;
    private int[] playingParts = new int[0];
    private boolean playingByChannel;
    private List<Assigned> assigned = List.of();
    private MidiFile.@Nullable Song song;
    private int cursor;
    private long cycle = -1;

    public SpeakerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntityTypes.SPEAKER.get(), pos, state);
    }

    @Override
    public String deviceType() {
        return TYPE;
    }

    // --- What the clients read ---

    public boolean playing() {
        return playing;
    }

    public Source playingSource() {
        return playingSource;
    }

    public int play() {
        return play;
    }

    public long started() {
        return started;
    }

    public String hash() {
        return hash;
    }

    public String playingName() {
        return playingName;
    }

    public String playingUrl() {
        return playingUrl;
    }

    public boolean loop() {
        return loop;
    }

    public int volume() {
        return volume;
    }

    public int range() {
        return Math.min(range, Config.SPEAKER_MAX_RANGE.getAsInt());
    }

    public int lengthTicks() {
        return lengthTicks;
    }

    public int maxBytes() {
        return maxBytes;
    }

    public int maxSeconds() {
        return maxSeconds;
    }

    public Trigger trigger() {
        return trigger;
    }

    public String error() {
        return error;
    }

    public int[] playingParts() {
        return playingParts.clone();
    }

    // Whether it's playing as part of a conductor's MIDI play.
    public boolean follows(BlockPos clock, int clockPlay) {
        return playing && playingSource == Source.MIDI && clock.equals(conductor) && conductorPlay == clockPlay;
    }

    // Whether it's the clock of a MIDI play.
    public boolean conducting() {
        return playing && playingSource == Source.MIDI && worldPosition.equals(conductor);
    }

    // --- Playing ---

    // The system folder's name ("" on no network).
    private String system() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return "";
        }
        NetworkRef network = ControllerStructures.networkOf(serverLevel, worldPosition);
        return network != null ? new ElclSystem(serverLevel.getServer(), network).name() : "";
    }

    private MinecraftServer server() {
        return ((ServerLevel) level).getServer();
    }

    // PLYAUD: a file name or a URL (checked: ELC2402-2406), with a volume (-1: unchanged) and loop (null: unchanged).
    public void playAudio(String sourceText, int newVolume, @Nullable Boolean newLoop) throws ElclException {
        if (AudioFiles.isUrl(sourceText)) {
            AudioFiles.checkUrl(server(), sourceText);
            source = Source.URL;
            url = sourceText.trim();
        } else {
            source = Source.FILE;
            file = sourceText.trim();
        }
        if (newVolume >= 0) {
            volume = Math.clamp(newVolume, 0, 100);
        }
        if (newLoop != null) {
            loop = newLoop;
        }
        start();
    }

    // PLYNOTE: an instrument (an index into INSTRUMENTS) and a note or a sequence of them (0-24), the first played now.
    public void playNotes(int newInstrument, int[] newNotes) throws ElclException {
        source = Source.NOTE;
        instrument = Math.clamp(newInstrument, 0, INSTRUMENTS.length - 1);
        notes = newNotes.length == 0 ? new int[] { 12 } : Arrays.copyOf(newNotes, Math.min(newNotes.length, MAX_NOTES));
        nextNote = 0;
        start();
    }

    // PLYMID: a MIDI file, what its map numbers and the map (empty: every part here), a volume for every speaker in the
    // play (-1: each its own) and loop. The file, its parts and the map's speakers are all checked before anything
    // changes.
    public void playMidi(String file, MapBy by, List<MidiParts.Part> parts, int newVolume, boolean newLoop) throws ElclException {
        Plan plan = plan(file.trim(), by, parts);
        source = Source.MIDI;
        midi = plan.file().name();
        mapBy = by;
        map = List.copyOf(parts);
        loop = newLoop;
        begin(plan, newVolume < 0 ? -1 : Math.clamp(newVolume, 0, 100));
    }

    // Plays its source now (a note: the next of its sequence); a file or URL is checked (ELC2402-2406), a MIDI file
    // and its parts map as PLYMID checks them.
    public void start() throws ElclException {
        if (!(level instanceof ServerLevel)) {
            return;
        }
        Plan plan = null;
        if (source == Source.MIDI) {
            try {
                plan = plan(midi, mapBy, map);
            } catch (ElclException e) {
                fail(e.elclMessage());
                throw e;
            }
        }
        begin(plan, -1);
    }

    private void begin(@Nullable Plan plan, int memberVolume) throws ElclException {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        // A MIDI play it was the clock of ends here.
        leave();
        error = "";
        playingSource = source;
        hash = "";
        playingUrl = "";
        playingName = "";
        lengthTicks = 0;
        conductor = null;
        playingParts = new int[0];
        assigned = List.of();
        song = null;
        try {
            switch (source) {
                case FILE -> {
                    if (file.isEmpty()) {
                        throw new ElclException("ELC2402", "*NONE");
                    }
                    AudioFiles.Audio audio = AudioFiles.check(serverLevel.getServer(), system(), file);
                    hash = audio.hash();
                    playingName = audio.name();
                    lengthTicks = Math.max(1, (int) Math.ceil(audio.seconds() * 20));
                }
                case URL -> {
                    playingUrl = AudioFiles.checkUrl(serverLevel.getServer(), url);
                    playingName = playingUrl;
                    maxBytes = (int) Math.min(Integer.MAX_VALUE, AudioFiles.maxBytes());
                    maxSeconds = AudioFiles.maxSeconds();
                }
                case NOTE -> {
                    int note = notes[Math.floorMod(nextNote, notes.length)];
                    nextNote = (nextNote + 1) % notes.length;
                    lengthTicks = NOTE_TICKS;
                    serverLevel.blockEvent(worldPosition, getBlockState().getBlock(), instrument, Math.clamp(note, 0, 24));
                }
                case MIDI -> {
                    if (plan == null) {
                        throw new ElclException("ELC2402", "*NONE");
                    }
                    hash = plan.file().hash();
                    playingName = plan.file().name();
                    song = plan.file().song();
                    lengthTicks = plan.file().song().lengthTicks();
                }
            }
        } catch (ElclException e) {
            fail(e.elclMessage());
            throw e;
        }
        playing = true;
        play++;
        started = serverLevel.getGameTime();
        if (plan != null) {
            conductor = worldPosition;
            conductorPlay = play;
            playingByChannel = plan.byChannel();
            assigned = plan.parts();
            playingParts = partsOf(worldPosition, assigned);
            cursor = 0;
            cycle = -1;
            if (memberVolume >= 0) {
                volume = memberVolume;
            }
        }
        changed();
        if (plan != null) {
            for (SpeakerBlockEntity member : plan.speakers()) {
                if (member != this) {
                    member.follow(this, partsOf(member.worldPosition, assigned), memberVolume);
                }
            }
            // The notes on its first tick, now.
            midiTick(serverLevel);
        }
    }

    // Stops what it's playing; a MIDI play stops on every speaker in it (from its clock).
    public void stop() {
        if (playing && playingSource == Source.MIDI && !worldPosition.equals(conductor) && level instanceof ServerLevel serverLevel) {
            SpeakerBlockEntity clock = clock(serverLevel);
            if (clock != null) {
                clock.stop();
            }
        }
        leave();
        halt();
    }

    // Stops this one alone.
    private void halt() {
        song = null;
        if (playing || !error.isEmpty()) {
            playing = false;
            error = "";
            changed();
        }
    }

    private void fail(ElclMessage message) {
        leave();
        playing = false;
        song = null;
        error = message.id() + " " + message.text();
        changed();
    }

    // --- MIDI ---

    // A MIDI play worked out (nothing changed): the file read and checked, its map's parts in it (by track: ELC2411; by
    // channel: ELC2412), and its map's speakers on this one's network.
    private Plan plan(String file, MapBy by, List<MidiParts.Part> parts) throws ElclException {
        if (file.isEmpty()) {
            throw new ElclException("ELC2402", "*NONE");
        }
        ServerLevel serverLevel = (ServerLevel) level;
        AudioFiles.Midi read = AudioFiles.midi(serverLevel.getServer(), system(), file);
        MidiFile.Song tune = read.song();
        boolean byChannel = by == MapBy.CHANNEL || by == MapBy.AUTO && tune.format() == 0;
        List<Assigned> given = new ArrayList<>();
        List<SpeakerBlockEntity> speakers = new ArrayList<>(List.of(this));
        if (parts.isEmpty()) {
            given.add(new Assigned(0, worldPosition));
        } else {
            List<ElclDevices.Device> devices = ElclDevices.list(serverLevel.getServer(), ControllerStructures.networkOf(serverLevel, worldPosition));
            for (MidiParts.Part part : parts) {
                if (part.number() != 0 && byChannel && !tune.hasChannel(part.number())) {
                    throw new ElclException("ELC2412", Integer.toString(part.number()), read.name());
                }
                if (part.number() != 0 && !byChannel && part.number() > tune.tracks()) {
                    throw new ElclException("ELC2411", Integer.toString(part.number()), read.name());
                }
                SpeakerBlockEntity speaker = speaker(devices, part.device());
                given.add(new Assigned(part.number(), speaker.worldPosition));
                if (!speakers.contains(speaker)) {
                    speakers.add(speaker);
                }
            }
        }
        return new Plan(read, byChannel, List.copyOf(given), List.copyOf(speakers));
    }

    // A speaker the parts map names, on this one's network: there (ELC1301), a speaker in this level (ELC1303), online
    // (ELC1302) and taking network commands (ELC2408) - this one aside, whatever started it.
    private SpeakerBlockEntity speaker(List<ElclDevices.Device> devices, String name) throws ElclException {
        for (ElclDevices.Device device : devices) {
            if (!device.name().equalsIgnoreCase(name)) {
                continue;
            }
            if (!(device.entity() instanceof SpeakerBlockEntity speaker) || speaker.getLevel() != level) {
                throw new ElclException("ELC1303", name, device.type());
            }
            if (speaker != this && !speaker.isOnline()) {
                throw new ElclException("ELC1302", name);
            }
            if (speaker != this && !speaker.trigger().network()) {
                throw new ElclException("ELC2408", name);
            }
            return speaker;
        }
        throw new ElclException("ELC1301", name);
    }

    // The parts a speaker plays in a play: 0 alone for all of them; none: silent.
    private static int[] partsOf(BlockPos speaker, List<Assigned> parts) {
        Set<Integer> numbers = new LinkedHashSet<>();
        for (Assigned part : parts) {
            if (part.speaker().equals(speaker)) {
                if (part.number() == 0) {
                    return new int[] { 0 };
                }
                numbers.add(part.number());
            }
        }
        return numbers.stream().mapToInt(Integer::intValue).sorted().toArray();
    }

    // Joins a conductor's MIDI play, playing the given parts (none: silent), at a volume (-1: its own).
    private void follow(SpeakerBlockEntity clock, int[] parts, int newVolume) {
        leave();
        error = "";
        playing = true;
        play++;
        playingSource = Source.MIDI;
        started = clock.started;
        hash = clock.hash;
        playingName = clock.playingName;
        playingUrl = "";
        lengthTicks = 0;
        conductor = clock.worldPosition;
        conductorPlay = clock.play;
        playingParts = parts;
        playingByChannel = clock.playingByChannel;
        assigned = List.of();
        song = null;
        if (newVolume >= 0) {
            volume = newVolume;
        }
        changed();
    }

    // Ends the MIDI play it's the clock of, on every speaker following it.
    private void leave() {
        if (!conducting() || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        for (Assigned part : assigned) {
            BlockPos at = part.speaker();
            if (!at.equals(worldPosition) && serverLevel.isLoaded(at) && serverLevel.getBlockEntity(at) instanceof SpeakerBlockEntity speaker
                    && speaker.follows(worldPosition, play)) {
                speaker.halt();
            }
        }
    }

    // The conductor a follower's play is from, if it's loaded and still playing it.
    private @Nullable SpeakerBlockEntity clock(ServerLevel serverLevel) {
        return conductor != null && serverLevel.isLoaded(conductor) && serverLevel.getBlockEntity(conductor) instanceof SpeakerBlockEntity clock
                && clock.conducting() && clock.play == conductorPlay ? clock : null;
    }

    // The conductor's notes (read again after a reload; gone, or now past its limits: an error, and the play ends).
    private MidiFile.@Nullable Song song() {
        if (song == null) {
            try {
                song = AudioFiles.midi(server(), system(), playingName).song();
                lengthTicks = song.lengthTicks();
            } catch (ElclException e) {
                fail(e.elclMessage());
                return null;
            }
        }
        return song;
    }

    public static void listen(Consumer<Sent> listener) {
        LISTENERS.add(listener);
    }

    public static void unlisten(Consumer<Sent> listener) {
        LISTENERS.remove(listener);
    }

    // A conductor's tick: the notes falling on it (a few ticks late at most), to the speakers playing their parts,
    // each at most midiMaxNotes (the loudest); looped, the cycle starts over; else the play ends after its length.
    private void midiTick(ServerLevel serverLevel) {
        MidiFile.Song tune = song();
        if (tune == null) {
            return;
        }
        long elapsed = serverLevel.getGameTime() - started;
        int length = tune.lengthTicks();
        if (elapsed < 0) {
            return;
        }
        if (!loop && elapsed >= length) {
            leave();
            halt();
            return;
        }
        long now = elapsed / length;
        int position = (int) (elapsed % length);
        if (now != cycle) {
            cycle = now;
            cursor = 0;
        }
        List<MidiFile.Note> notes = tune.notes();
        while (cursor < notes.size() && notes.get(cursor).tick() < position - CATCH_UP) {
            cursor++;
        }
        Map<BlockPos, List<MidiFile.Note>> sounding = new LinkedHashMap<>();
        for (; cursor < notes.size() && notes.get(cursor).tick() <= position; cursor++) {
            MidiFile.Note note = notes.get(cursor);
            int number = playingByChannel ? note.channel() : note.track();
            for (Assigned part : assigned) {
                if (part.number() == 0 || part.number() == number) {
                    List<MidiFile.Note> heard = sounding.computeIfAbsent(part.speaker(), at -> new ArrayList<>());
                    if (!heard.contains(note)) {
                        heard.add(note);
                    }
                }
            }
        }
        int most = Config.MIDI_MAX_NOTES.getAsInt();
        for (Map.Entry<BlockPos, List<MidiFile.Note>> entry : sounding.entrySet()) {
            BlockPos at = entry.getKey();
            if (!serverLevel.isLoaded(at) || !(serverLevel.getBlockEntity(at) instanceof SpeakerBlockEntity speaker) || !speaker.follows(worldPosition, play)) {
                continue;
            }
            for (MidiFile.Note note : MidiParts.loudest(entry.getValue(), most)) {
                serverLevel.blockEvent(at, speaker.getBlockState().getBlock(), NoteInstruments.eventA(note.instrument(), note.velocity()),
                        NoteInstruments.eventB(note.pitch(), note.velocity()));
                for (Consumer<Sent> listener : List.copyOf(LISTENERS)) {
                    listener.accept(new Sent(worldPosition, at, serverLevel.getGameTime(), note));
                }
            }
        }
    }

    // A client's report on a play (AudioPayloads.Report): the URL's length, or why it couldn't play it.
    public void report(ServerPlayer player, int reportedPlay, float seconds, String problem) {
        if (reportedPlay != play || !playing || player.distanceToSqr(Vec3.atCenterOf(worldPosition)) > (range() + 16.0) * (range() + 16.0)) {
            return;
        }
        if (!problem.isEmpty()) {
            playing = false;
            error = problem.length() > 120 ? problem.substring(0, 120) : problem;
            changed();
        } else if (lengthTicks == 0 && seconds > 0) {
            lengthTicks = Math.max(1, (int) Math.ceil(seconds * 20));
            changed();
        }
    }

    @Override
    protected void redstone(boolean now) {
        if (!trigger.redstone()) {
            return;
        }
        if (now) {
            try {
                start();
            } catch (ElclException ignored) {
                // Shown as its error.
            }
        } else if (playing && loop && playingSource != Source.NOTE && (playingSource != Source.MIDI || conducting())) {
            stop();
        }
    }

    @Override
    protected void serverTick() {
        if (!playing || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (playingSource == Source.MIDI) {
            if (worldPosition.equals(conductor)) {
                midiTick(serverLevel);
            } else if (serverLevel.getGameTime() % 20 == 0 && clock(serverLevel) == null) {
                // Its clock stopped, went, or started something else.
                halt();
            }
            return;
        }
        long elapsed = level.getGameTime() - started;
        boolean once = !loop || playingSource == Source.NOTE;
        // A URL whose length no client has told: at most the longest allowed.
        int length = lengthTicks > 0 ? lengthTicks : playingSource == Source.URL ? maxSeconds * 20 : 0;
        if (once && length > 0 && elapsed >= length) {
            playing = false;
            changed();
        }
    }

    @Override
    protected void clientTick() {
        SignalClientHooks.get().tick(this);
    }

    private void changed() {
        sync();
        BlockState state = getBlockState();
        if (state.getBlock() instanceof SpeakerBlock) {
            showState(state.setValue(SpeakerBlock.STATE, !error.isEmpty() ? SpeakerBlock.State.ERROR
                    : playing ? SpeakerBlock.State.PLAYING : SpeakerBlock.State.IDLE));
        }
    }

    // --- Screen and popup ---

    @Override
    public Component statusText() {
        return Component.translatable(!error.isEmpty() ? "gui.encodedlogistics.signal.status.error"
                : playing ? "gui.encodedlogistics.signal.status.playing" : "gui.encodedlogistics.signal.status.idle");
    }

    @Override
    public RackDeviceInfo.Status status() {
        return !error.isEmpty() ? RackDeviceInfo.Status.FAULT : playing ? RackDeviceInfo.Status.ONLINE : RackDeviceInfo.Status.OFFLINE;
    }

    private Component sourceValue() {
        return switch (source) {
            case FILE -> file.isEmpty() ? Component.translatable("gui.encodedlogistics.signal.none") : Component.literal(file);
            case URL -> url.isEmpty() ? Component.translatable("gui.encodedlogistics.signal.none") : Component.literal(url);
            case NOTE -> Component.translatable("gui.encodedlogistics.signal.note_value", instrumentLabel(instrument), notesText());
            case MIDI -> midi.isEmpty() ? Component.translatable("gui.encodedlogistics.signal.none") : Component.literal(midi);
        };
    }

    // The parts a speaker in a MIDI play plays: all, none (silent), or its tracks or channels.
    private Component partsValue() {
        if (playingParts.length == 0) {
            return Component.translatable("gui.encodedlogistics.signal.plays.nothing");
        }
        if (playingParts[0] == 0) {
            return Component.translatable("gui.encodedlogistics.signal.plays.all");
        }
        StringBuilder numbers = new StringBuilder();
        for (int part : playingParts) {
            numbers.append(numbers.isEmpty() ? "" : ", ").append(part);
        }
        return Component.translatable(playingByChannel ? "gui.encodedlogistics.signal.plays.channels" : "gui.encodedlogistics.signal.plays.tracks",
                numbers.toString());
    }

    public static Component instrumentLabel(int index) {
        return Component.translatable("gui.encodedlogistics.signal.instrument." + INSTRUMENTS[Math.clamp(index, 0, INSTRUMENTS.length - 1)].getSerializedName());
    }

    private String notesText() {
        StringBuilder text = new StringBuilder();
        for (int note : notes) {
            text.append(text.isEmpty() ? "" : " ").append(note);
        }
        return text.toString();
    }

    @Override
    public List<Row> rows() {
        List<Row> rows = new ArrayList<>();
        rows.add(Row.cycled("source", Component.translatable("gui.encodedlogistics.signal.source"), source.label()));
        switch (source) {
            case FILE -> rows.add(Row.cycled("file", Component.translatable("gui.encodedlogistics.signal.file"),
                    file.isEmpty() ? Component.translatable("gui.encodedlogistics.signal.none") : Component.literal(file)));
            case URL -> rows.add(Row.edited("url", Component.translatable("gui.encodedlogistics.signal.url"),
                    url.isEmpty() ? Component.translatable("gui.encodedlogistics.signal.none") : Component.literal(url)));
            case NOTE -> {
                rows.add(Row.cycled("instrument", Component.translatable("gui.encodedlogistics.signal.instrument"), instrumentLabel(instrument)));
                rows.add(Row.edited("note", Component.translatable("gui.encodedlogistics.signal.note"), Component.literal(notesText())));
            }
            case MIDI -> {
                rows.add(Row.cycled("midi", Component.translatable("gui.encodedlogistics.signal.file"),
                        midi.isEmpty() ? Component.translatable("gui.encodedlogistics.signal.none") : Component.literal(midi)));
                rows.add(Row.cycled("map_by", Component.translatable("gui.encodedlogistics.signal.map_by"), mapBy.label()));
                rows.add(Row.edited("map", Component.translatable("gui.encodedlogistics.signal.map"),
                        map.isEmpty() ? Component.translatable("gui.encodedlogistics.signal.map.here") : Component.literal(MidiParts.text(map))));
            }
        }
        rows.add(Row.cycled("volume", Component.translatable("gui.encodedlogistics.signal.volume"), Component.literal(volume + "%")));
        rows.add(Row.cycled("range", Component.translatable("gui.encodedlogistics.signal.range"),
                Component.translatable("gui.encodedlogistics.signal.blocks", range())));
        rows.add(Row.cycled("loop", Component.translatable("gui.encodedlogistics.signal.loop"),
                Component.translatable(loop ? "gui.encodedlogistics.signal.yes" : "gui.encodedlogistics.signal.no")));
        rows.add(Row.cycled("trigger", Component.translatable("gui.encodedlogistics.signal.trigger"), trigger.label()));
        rows.add(deviceRow());
        return rows;
    }

    @Override
    public String text(String key) {
        return switch (key) {
            case "url" -> url;
            case "note" -> notesText();
            case "map" -> MidiParts.text(map);
            default -> super.text(key);
        };
    }

    @Override
    protected boolean setting(ServerPlayer player, String key, int step, @Nullable String text) throws ElclException {
        switch (key) {
            case "source" -> source = cycle(source, step);
            case "file" -> {
                List<String> files = AudioFiles.list(server(), system());
                if (files.isEmpty()) {
                    throw new ElclException("ELC2402", "*NONE");
                }
                int at = files.indexOf(file);
                file = files.get(at < 0 ? 0 : Math.floorMod(at + Integer.signum(step), files.size()));
            }
            case "url" -> {
                if (text == null) {
                    return false;
                }
                url = text.isBlank() ? "" : AudioFiles.checkUrl(server(), text);
            }
            case "midi" -> {
                List<String> files = AudioFiles.listMidi(server(), system());
                if (files.isEmpty()) {
                    throw new ElclException("ELC2402", "*NONE");
                }
                int at = files.indexOf(midi);
                midi = files.get(at < 0 ? 0 : Math.floorMod(at + Integer.signum(step), files.size()));
            }
            case "map_by" -> mapBy = cycle(mapBy, step);
            case "map" -> {
                if (text == null) {
                    return false;
                }
                map = MidiParts.parse(text, "MAP");
            }
            case "instrument" -> instrument = Math.floorMod(instrument + Integer.signum(step), INSTRUMENTS.length);
            case "note" -> {
                if (text != null) {
                    notes = parseNotes(text);
                    nextNote = 0;
                } else {
                    notes = new int[] { Math.clamp(notes[0] + Integer.signum(step), 0, 24) };
                }
            }
            case "volume" -> volume = Math.clamp(volume + (Math.abs(step) >= 10 ? Integer.signum(step) : step * 10), 0, 100);
            case "range" -> range = Math.clamp(range() + (Math.abs(step) >= 10 ? Integer.signum(step) : step * 8), 8, Config.SPEAKER_MAX_RANGE.getAsInt());
            case "loop" -> loop = !loop;
            case "trigger" -> trigger = cycle(trigger, step);
            default -> {
                return false;
            }
        }
        return true;
    }

    // "0 5 7 12" (spaces or commas): notes 0-24 (ELC2407 otherwise), at most MAX_NOTES.
    public static int[] parseNotes(String text) throws ElclException {
        String[] parts = text.trim().split("[\\s,]+");
        List<Integer> parsed = new ArrayList<>();
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            int note;
            try {
                note = Integer.parseInt(part);
            } catch (NumberFormatException e) {
                throw new ElclException("ELC2407", part);
            }
            if (note < 0 || note > 24) {
                throw new ElclException("ELC2407", part);
            }
            if (parsed.size() < MAX_NOTES) {
                parsed.add(note);
            }
        }
        return parsed.isEmpty() ? new int[] { 12 } : parsed.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public Component action() {
        return Component.translatable(playing ? "gui.encodedlogistics.signal.stop" : "gui.encodedlogistics.signal.play");
    }

    @Override
    protected void act(ServerPlayer player) {
        if (playing) {
            stop();
            return;
        }
        try {
            start();
        } catch (ElclException e) {
            player.sendOverlayMessage(Component.literal(e.elclMessage().id() + "  " + e.elclMessage().text()));
        }
    }

    @Override
    protected List<RackDeviceInfo.InfoLine> hudLines() {
        List<RackDeviceInfo.InfoLine> lines = new ArrayList<>();
        if (!error.isEmpty()) {
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("gui.encodedlogistics.signal.source"), Component.literal(error)));
            return lines;
        }
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("gui.encodedlogistics.signal.source"),
                playing && playingSource != Source.NOTE ? Component.literal(shortName(playingName)) : sourceValue()));
        boolean midiPlay = playing && playingSource == Source.MIDI;
        if (midiPlay) {
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("gui.encodedlogistics.signal.plays"), partsValue()));
        }
        // A speaker following another's MIDI play loops (or not) as that one does.
        if (!midiPlay || conducting()) {
            lines.add(new RackDeviceInfo.InfoLine(Component.translatable("gui.encodedlogistics.signal.mode"),
                    Component.translatable(loop && source != Source.NOTE ? "gui.encodedlogistics.signal.mode.loop" : "gui.encodedlogistics.signal.mode.once")));
        }
        return lines;
    }

    // A URL's last path part (or host), for the popup.
    private static String shortName(String name) {
        if (!AudioFiles.isUrl(name)) {
            return name;
        }
        String trimmed = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
        return trimmed.substring(trimmed.lastIndexOf('/') + 1);
    }

    // --- Saving ---

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        source = load(input, "source", Source.FILE);
        file = input.getStringOr("file", "");
        url = input.getStringOr("url", "");
        instrument = Math.clamp(input.getIntOr("instrument", 0), 0, INSTRUMENTS.length - 1);
        notes = input.getIntArray("notes").filter(saved -> saved.length > 0).orElse(new int[] { 12 });
        nextNote = input.getIntOr("next_note", 0);
        volume = Math.clamp(input.getIntOr("volume", 70), 0, 100);
        range = Math.clamp(input.getIntOr("range", 32), 1, 256);
        loop = input.getBooleanOr("loop", false);
        trigger = load(input, "trigger", Trigger.BOTH);
        midi = input.getStringOr("midi", "");
        mapBy = load(input, "map_by", MapBy.AUTO);
        try {
            map = MidiParts.parse(input.getStringOr("map", ""), "MAP");
        } catch (ElclException e) {
            map = List.of();
        }
        playing = input.getBooleanOr("playing", false);
        play = input.getIntOr("play", 0);
        started = input.getLongOr("started", 0L);
        playingSource = load(input, "playing_source", Source.FILE);
        hash = input.getStringOr("hash", "");
        playingName = input.getStringOr("playing_name", "");
        playingUrl = input.getStringOr("playing_url", "");
        error = input.getStringOr("error", "");
        lengthTicks = input.getIntOr("length", 0);
        maxBytes = input.getIntOr("max_bytes", 0);
        maxSeconds = input.getIntOr("max_seconds", 0);
        conductor = input.getIntArray("conductor").filter(at -> at.length == 3).map(at -> new BlockPos(at[0], at[1], at[2])).orElse(null);
        conductorPlay = input.getIntOr("conductor_play", 0);
        playingParts = input.getIntArray("playing_parts").orElse(new int[0]);
        playingByChannel = input.getBooleanOr("by_channel", false);
        // number, x, y, z for each part handed out.
        int[] saved = input.getIntArray("assigned").orElse(new int[0]);
        List<Assigned> parts = new ArrayList<>();
        for (int i = 0; i + 3 < saved.length; i += 4) {
            parts.add(new Assigned(saved[i], new BlockPos(saved[i + 1], saved[i + 2], saved[i + 3])));
        }
        assigned = List.copyOf(parts);
        song = null;
        cycle = -1;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putString("source", source.name());
        output.putString("file", file);
        output.putString("url", url);
        output.putInt("instrument", instrument);
        output.putIntArray("notes", notes);
        output.putInt("next_note", nextNote);
        output.putInt("volume", volume);
        output.putInt("range", range);
        output.putBoolean("loop", loop);
        output.putString("trigger", trigger.name());
        output.putString("midi", midi);
        output.putString("map_by", mapBy.name());
        output.putString("map", MidiParts.text(map));
        output.putBoolean("playing", playing);
        output.putInt("play", play);
        output.putLong("started", started);
        output.putString("playing_source", playingSource.name());
        output.putString("hash", hash);
        output.putString("playing_name", playingName);
        output.putString("playing_url", playingUrl);
        output.putString("error", error);
        output.putInt("length", lengthTicks);
        output.putInt("max_bytes", maxBytes);
        output.putInt("max_seconds", maxSeconds);
        if (conductor != null) {
            output.putIntArray("conductor", new int[] { conductor.getX(), conductor.getY(), conductor.getZ() });
        }
        output.putInt("conductor_play", conductorPlay);
        output.putIntArray("playing_parts", playingParts);
        output.putBoolean("by_channel", playingByChannel);
        int[] parts = new int[assigned.size() * 4];
        for (int i = 0; i < assigned.size(); i++) {
            Assigned part = assigned.get(i);
            parts[i * 4] = part.number();
            parts[i * 4 + 1] = part.speaker().getX();
            parts[i * 4 + 2] = part.speaker().getY();
            parts[i * 4 + 3] = part.speaker().getZ();
        }
        output.putIntArray("assigned", parts);
    }
}
