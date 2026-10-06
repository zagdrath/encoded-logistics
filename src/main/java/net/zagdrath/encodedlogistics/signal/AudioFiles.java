/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.signal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Header;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.elcl.ElclException;

// Speakers' audio (signals handoff 6). Files: <world>/encodedlogistics/audio/<SYSNAME>/ (a Speaker on no network: the
// audio folder itself), OGG Vorbis or MP3, read and checked here - found (ELC2402), within audioMaxBytes and
// audioMaxSeconds (ELC2403), decodable (ELC2406) - and kept by their content's hash, which is all clients are told:
// they ask for the bytes (AudioPayloads) and keep them by hash. Web URLs: http(s) only, allowWebAudio on (ELC2404),
// the host on webAudioHosts (ELC2405); the clients fetch those themselves, with the same limits.
public final class AudioFiles {
    public enum Format { OGG, MP3 }

    // A checked file: its name, hash (hex SHA-256) and length.
    public record Audio(String name, String hash, Format format, double seconds) {}

    // The files checked lately, by hash: where they are (read again, and their hash checked, when a client asks).
    private static final int KEPT = 256;
    private static final Map<String, Path> BY_HASH = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Path> eldest) {
            return size() > KEPT;
        }
    };

    private AudioFiles() {}

    public static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("encodedlogistics").resolve("audio");
    }

    // The folder a system's speakers play from ("": a speaker on no network).
    public static Path folder(MinecraftServer server, String system) {
        return system.isEmpty() ? root(server) : root(server).resolve(system);
    }

    public static long maxBytes() {
        return Config.AUDIO_MAX_BYTES.getAsInt() * 1024L * 1024L;
    }

    public static int maxSeconds() {
        return Config.AUDIO_MAX_SECONDS.getAsInt();
    }

    // "4 MB, 180 s" (ELC2403's limit).
    public static String limits() {
        return Config.AUDIO_MAX_BYTES.getAsInt() + " MB, " + maxSeconds() + " s";
    }

    // The .ogg and .mp3 files in a folder, by name (for the settings screen's list).
    public static List<String> list(MinecraftServer server, String system) {
        Path folder = folder(server, system);
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return names;
        }
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(Files::isRegularFile).map(path -> path.getFileName().toString()).filter(AudioFiles::audioName).sorted(String.CASE_INSENSITIVE_ORDER)
                    .limit(256).forEach(names::add);
        } catch (IOException ignored) {
            // Nothing listed.
        }
        return names;
    }

    private static boolean audioName(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return name.matches("[A-Za-z0-9_. -]+") && !name.contains("..") && (lower.endsWith(".ogg") || lower.endsWith(".mp3"));
    }

    // A file in the system's folder, read and checked.
    public static Audio check(MinecraftServer server, String system, String name) throws ElclException {
        String file = name.trim();
        if (!audioName(file)) {
            throw new ElclException(file.toLowerCase(Locale.ROOT).matches(".*\\.(ogg|mp3)") ? "ELC2402" : "ELC2406", file);
        }
        Path folder = folder(server, system), path = folder.resolve(file);
        if (!Files.isRegularFile(path)) {
            path = findIgnoringCase(folder, file);
            if (path == null) {
                throw new ElclException("ELC2402", file);
            }
        }
        byte[] bytes;
        try {
            if (Files.size(path) > maxBytes()) {
                throw new ElclException("ELC2403", file, limits());
            }
            bytes = Files.readAllBytes(path);
        } catch (IOException e) {
            throw new ElclException("ELC2402", file);
        }
        Format format = format(bytes);
        if (format == null) {
            throw new ElclException("ELC2406", file);
        }
        double seconds = seconds(bytes, format);
        if (seconds <= 0) {
            throw new ElclException("ELC2406", file);
        }
        if (seconds > maxSeconds()) {
            throw new ElclException("ELC2403", file, limits());
        }
        Audio audio = new Audio(path.getFileName().toString(), hash(bytes), format, seconds);
        synchronized (BY_HASH) {
            BY_HASH.put(audio.hash(), path);
        }
        return audio;
    }

    // A file checked lately, by its hash (what a client asks for): its bytes, if it's still there unchanged.
    public static byte @Nullable [] bytes(String hash) {
        Path path;
        synchronized (BY_HASH) {
            path = BY_HASH.get(hash);
        }
        try {
            if (path == null || Files.size(path) > maxBytes()) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(path);
            return hash(bytes).equals(hash) ? bytes : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static @Nullable Path findIgnoringCase(Path folder, String name) {
        if (!Files.isDirectory(folder)) {
            return null;
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(path -> path.getFileName().toString().equalsIgnoreCase(name)).findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    // --- Web audio ---

    public static boolean webAllowed(MinecraftServer server) {
        return switch (Config.ALLOW_WEB_AUDIO.get()) {
            case TRUE -> true;
            case FALSE -> false;
            case AUTO -> !server.isDedicatedServer();
        };
    }

    // An http(s) URL whose host is on webAudioHosts (a listed host allows its subdomains), checked.
    public static String checkUrl(MinecraftServer server, String url) throws ElclException {
        if (!webAllowed(server)) {
            throw new ElclException("ELC2404");
        }
        String host = host(url);
        if (host == null) {
            throw new ElclException("ELC2406", url);
        }
        if (!hostAllowed(host, Config.WEB_AUDIO_HOSTS.get())) {
            throw new ElclException("ELC2405", host);
        }
        return url.trim();
    }

    // The host of an http(s) URL (lower case), or null for anything else.
    public static @Nullable String host(String url) {
        try {
            URI uri = new URI(url.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https") || uri.getHost() == null || uri.getUserInfo() != null) {
                return null;
            }
            return uri.getHost().toLowerCase(Locale.ROOT);
        } catch (URISyntaxException e) {
            return null;
        }
    }

    public static boolean hostAllowed(String host, List<? extends String> allowed) {
        for (String entry : allowed) {
            String listed = entry.trim().toLowerCase(Locale.ROOT);
            if (!listed.isEmpty() && (host.equals(listed) || host.endsWith("." + listed))) {
                return true;
            }
        }
        return false;
    }

    // Whether a source is a web URL rather than a file name.
    public static boolean isUrl(String source) {
        String lower = source.trim().toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://");
    }

    // --- Formats ---

    // OGG Vorbis ("OggS", then a Vorbis identification header) or MP3 (an ID3 tag or a frame sync); else null.
    public static @Nullable Format format(byte[] bytes) {
        if (bytes.length > 64 && bytes[0] == 'O' && bytes[1] == 'g' && bytes[2] == 'g' && bytes[3] == 'S') {
            return indexOf(bytes, VORBIS, 0, Math.min(bytes.length, 512)) >= 0 ? Format.OGG : null;
        }
        if (bytes.length > 4 && (bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3' || (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xE0) == 0xE0)) {
            return Format.MP3;
        }
        return null;
    }

    private static final byte[] VORBIS = { 1, 'v', 'o', 'r', 'b', 'i', 's' };

    // Its length in seconds (0: unreadable): an OGG's last granule position over its sample rate; an MP3's frames
    // added up.
    public static double seconds(byte[] bytes, Format format) {
        return format == Format.OGG ? oggSeconds(bytes) : mp3Seconds(bytes);
    }

    static double oggSeconds(byte[] bytes) {
        int ident = indexOf(bytes, VORBIS, 0, Math.min(bytes.length, 512));
        if (ident < 0 || ident + 16 > bytes.length) {
            return 0;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int rate = buffer.getInt(ident + 12);
        // The last page's granule position: the samples before its end.
        for (int at = bytes.length - 27; at >= 0; at--) {
            if (bytes[at] == 'O' && bytes[at + 1] == 'g' && bytes[at + 2] == 'g' && bytes[at + 3] == 'S') {
                long granule = buffer.getLong(at + 6);
                return rate > 0 && granule > 0 ? (double) granule / rate : 0;
            }
        }
        return 0;
    }

    static double mp3Seconds(byte[] bytes) {
        Bitstream stream = new Bitstream(new ByteArrayInputStream(bytes));
        double millis = 0;
        int frames = 0;
        try {
            Header header;
            while ((header = stream.readFrame()) != null) {
                millis += header.ms_per_frame();
                frames++;
                stream.closeFrame();
                if (millis > 3_600_000) {
                    break;
                }
            }
        } catch (BitstreamException e) {
            // What was read so far.
        } finally {
            try {
                stream.close();
            } catch (BitstreamException ignored) {
                // Nothing held.
            }
        }
        return frames > 0 ? millis / 1000.0 : 0;
    }

    private static int indexOf(byte[] bytes, byte[] wanted, int from, int to) {
        outer:
        for (int i = from; i + wanted.length <= to; i++) {
            for (int j = 0; j < wanted.length; j++) {
                if (bytes[i + j] != wanted[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    public static String hash(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
