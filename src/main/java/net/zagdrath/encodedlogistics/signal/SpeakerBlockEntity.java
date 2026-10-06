/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
public class SpeakerBlockEntity extends SignalBlockEntity {
    public static final String TYPE = "SPK";

    public enum Source {
        FILE, URL, NOTE;

        public Component label() {
            return Component.translatable("gui.encodedlogistics.signal.source." + name().toLowerCase(Locale.ROOT));
        }
    }

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

    // What's playing: the play's number and start tick, the file's hash or the URL, its length in ticks (0: not known
    // yet), and the limits the clients keep a URL to; or why it couldn't play.
    private boolean playing;
    private int play;
    private long started;
    private Source playingSource = Source.FILE;
    private String hash = "", playingName = "", playingUrl = "", error = "";
    private int lengthTicks, maxBytes, maxSeconds;

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

    // Plays its source now (a note: the next of its sequence); a file or URL is checked (ELC2402-2406).
    public void start() throws ElclException {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        error = "";
        playingSource = source;
        hash = "";
        playingUrl = "";
        playingName = "";
        lengthTicks = 0;
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
            }
        } catch (ElclException e) {
            fail(e.elclMessage());
            throw e;
        }
        playing = true;
        play++;
        started = serverLevel.getGameTime();
        changed();
    }

    public void stop() {
        if (playing || !error.isEmpty()) {
            playing = false;
            error = "";
            changed();
        }
    }

    private void fail(ElclMessage message) {
        playing = false;
        error = message.id() + " " + message.text();
        changed();
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
        } else if (playing && loop && source != Source.NOTE) {
            stop();
        }
    }

    @Override
    protected void serverTick() {
        if (!playing || level == null) {
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
        };
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
        lines.add(new RackDeviceInfo.InfoLine(Component.translatable("gui.encodedlogistics.signal.mode"),
                Component.translatable(loop && source != Source.NOTE ? "gui.encodedlogistics.signal.mode.loop" : "gui.encodedlogistics.signal.mode.once")));
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
    }
}
