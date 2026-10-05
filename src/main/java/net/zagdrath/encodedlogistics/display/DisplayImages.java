/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.display;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import javax.imageio.ImageIO;

import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelResource;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.elcl.ElclException;

// Images on Display Panels (HANDOFF 3): PNGs in <world>/encodedlogistics/images/<SYSNAME>/, scaled to their region.
// Server config: allowImages (AUTO: on in single-player, off on dedicated servers), displayMaxImageSize (source px each
// way), displayMaxImageBytes and displayImageColors (the most a player may pick). Colour modes: 16 = the fixed panel
// palette; 64 / 256 = an adaptive palette for the image (median cut); FULL = 24-bit. The palettes use an ordered 4 x 4
// Bayer dither (strength 48 / 24 / 12), stable between frames; FULL and SCALE(*NEAREST) aren't dithered. Decoded off the
// server thread straight to the region's size and cached per file, its change time, region size and mode; clients get
// the rendered region, never the file.
public final class DisplayImages {
    public enum Colors {
        C16(16, 48), C64(64, 24), C256(256, 12), FULL(0, 0);

        final int count, strength;

        Colors(int count, int strength) {
            this.count = count;
            this.strength = strength;
        }

        public static Colors of(String value) {
            return switch (value.trim().toUpperCase(Locale.ROOT)) {
                case "16" -> C16;
                case "64" -> C64;
                case "*FULL", "FULL" -> FULL;
                default -> C256;
            };
        }

        public String label() {
            return this == FULL ? "*FULL" : Integer.toString(count);
        }
    }

    // The fixed palette (textures/display/palette.json).
    static final int[] PALETTE_16 = { 0x1F2228, 0x373C44, 0x555B65, 0x79808A, 0xA3A9B1, 0xD3D7DB, 0x2678A0, 0x50C2EC, 0x89F9FF, 0xC65217, 0xFF9B44,
            0xECC138, 0x3CE05A, 0x22A03C, 0xBA3B37, 0x6A4A2A };
    private static final int[][] BAYER = { { 0, 8, 2, 10 }, { 12, 4, 14, 6 }, { 3, 11, 1, 9 }, { 15, 7, 13, 5 } };
    private static final int CACHED = 32;

    private record Key(Path file, long modified, int w, int h, boolean nearest, Colors colors) {}

    private static final Map<Key, int[]> CACHE = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, int[]> eldest) {
            return size() > CACHED;
        }
    };

    private DisplayImages() {}

    public static Path folder(MinecraftServer server, String system) {
        return server.getWorldPath(LevelResource.ROOT).resolve("encodedlogistics").resolve("images").resolve(system);
    }

    public static boolean allowed(MinecraftServer server) {
        return switch (Config.ALLOW_IMAGES.get()) {
            case TRUE -> true;
            case FALSE -> false;
            case AUTO -> !server.isDedicatedServer();
        };
    }

    // The most colours the server allows.
    public static Colors cap() {
        return Colors.of(Config.DISPLAY_IMAGE_COLORS.get());
    }

    // A mode capped by the server's limit.
    public static Colors capped(Colors wanted) {
        Colors cap = cap();
        return order(wanted) > order(cap) ? cap : wanted;
    }

    private static int order(Colors colors) {
        return colors == Colors.FULL ? 1_000 : colors.count;
    }

    // The file an image name means in the system's folder, checked: images allowed (ELC1315), a plain .png name that's
    // there (ELC1312), not too big (ELC1313).
    public static Path check(MinecraftServer server, String system, String name) throws ElclException {
        if (!allowed(server)) {
            throw new ElclException("ELC1315");
        }
        String file = name.trim();
        if (!file.matches("[A-Za-z0-9_.-]+") || !file.toLowerCase(Locale.ROOT).endsWith(".png") || file.contains("..")) {
            throw new ElclException("ELC1312", file);
        }
        Path path = folder(server, system).resolve(file);
        if (!Files.isRegularFile(path)) {
            // Names are found whatever their case.
            path = findIgnoringCase(folder(server, system), file);
            if (path == null) {
                throw new ElclException("ELC1312", file);
            }
        }
        int maxSize = Config.DISPLAY_MAX_IMAGE_SIZE.getAsInt();
        long maxBytes = Config.DISPLAY_MAX_IMAGE_BYTES.getAsInt() * 1024L * 1024L;
        try {
            int[] size = pngSize(path);
            if (Files.size(path) > maxBytes || size[0] > maxSize || size[1] > maxSize) {
                throw new ElclException("ELC1313", file, maxSize + " x " + maxSize + " px, " + Config.DISPLAY_MAX_IMAGE_BYTES.getAsInt() + " MB");
            }
        } catch (IOException e) {
            throw new ElclException("ELC1312", file);
        }
        return path;
    }

    private static Path findIgnoringCase(Path folder, String name) {
        try (var files = Files.list(folder)) {
            return files.filter(p -> p.getFileName().toString().equalsIgnoreCase(name)).findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    // A PNG's width and height from its header.
    private static int[] pngSize(Path path) throws IOException {
        try (InputStream in = Files.newInputStream(path); DataInputStream data = new DataInputStream(in)) {
            byte[] signature = data.readNBytes(8);
            if (signature.length < 8 || signature[1] != 'P' || signature[2] != 'N' || signature[3] != 'G') {
                throw new IOException("not a PNG");
            }
            data.readInt();
            data.readInt();
            return new int[] { data.readInt(), data.readInt() };
        }
    }

    // The image at the region's size (ARGB), off the server thread; cached.
    public static CompletableFuture<int[]> render(Path path, int w, int h, boolean nearest, Colors colors) {
        long modified;
        try {
            modified = Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return CompletableFuture.failedFuture(e);
        }
        Key key = new Key(path, modified, w, h, nearest, colors);
        synchronized (CACHE) {
            int[] cached = CACHE.get(key);
            if (cached != null) {
                return CompletableFuture.completedFuture(cached);
            }
        }
        return CompletableFuture.supplyAsync(() -> {
            try {
                BufferedImage source = ImageIO.read(path.toFile());
                if (source == null) {
                    throw new IOException("unreadable");
                }
                int[] pixels = convert(source, w, h, nearest, colors);
                synchronized (CACHE) {
                    CACHE.put(key, pixels);
                }
                return pixels;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, Util.backgroundExecutor());
    }

    static int[] convert(BufferedImage source, int w, int h, boolean nearest, Colors colors) {
        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        Image resized = source.getScaledInstance(w, h, nearest ? Image.SCALE_FAST : Image.SCALE_AREA_AVERAGING);
        g.drawImage(resized, 0, 0, null);
        g.dispose();
        int[] pixels = scaled.getRGB(0, 0, w, h, null, 0, w);
        if (nearest || colors == Colors.FULL) {
            for (int i = 0; i < pixels.length; i++) {
                pixels[i] |= 0xFF000000;
            }
            return pixels;
        }
        int[] palette = colors == Colors.C16 ? PALETTE_16 : medianCut(pixels, colors.count);
        Map<Integer, Integer> nearestColor = new HashMap<>();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int rgb = pixels[y * w + x];
                float t = (BAYER[y % 4][x % 4] / 16F - 0.5F) * colors.strength;
                int r = clamp((rgb >> 16 & 0xFF) + t) >> 2, gr = clamp((rgb >> 8 & 0xFF) + t) >> 2, b = clamp((rgb & 0xFF) + t) >> 2;
                int key = r << 12 | gr << 6 | b;
                int chosen = nearestColor.computeIfAbsent(key, k -> closest(palette, r << 2, gr << 2, b << 2));
                pixels[y * w + x] = 0xFF000000 | chosen;
            }
        }
        return pixels;
    }

    private static int clamp(float v) {
        return Math.clamp(Math.round(v), 0, 255);
    }

    private static int closest(int[] palette, int r, int g, int b) {
        int best = palette[0];
        long bestDistance = Long.MAX_VALUE;
        for (int color : palette) {
            int dr = (color >> 16 & 0xFF) - r, dg = (color >> 8 & 0xFF) - g, db = (color & 0xFF) - b;
            long distance = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = color;
            }
        }
        return best;
    }

    // An adaptive palette of up to n colours: boxes of the image's colours split at the median of their widest channel,
    // each box's average a colour.
    static int[] medianCut(int[] pixels, int n) {
        List<int[]> boxes = new ArrayList<>();
        boxes.add(pixels.clone());
        while (boxes.size() < n) {
            int widest = -1, channel = 0, range = 0;
            for (int i = 0; i < boxes.size(); i++) {
                int[] box = boxes.get(i);
                if (box.length < 2) {
                    continue;
                }
                for (int c = 0; c < 3; c++) {
                    int shift = 16 - 8 * c, lo = 255, hi = 0;
                    for (int rgb : box) {
                        int v = rgb >> shift & 0xFF;
                        lo = Math.min(lo, v);
                        hi = Math.max(hi, v);
                    }
                    if (hi - lo > range) {
                        range = hi - lo;
                        widest = i;
                        channel = c;
                    }
                }
            }
            if (widest < 0 || range == 0) {
                break;
            }
            int[] box = boxes.remove(widest);
            int shift = 16 - 8 * channel;
            Integer[] sorted = new Integer[box.length];
            for (int i = 0; i < box.length; i++) {
                sorted[i] = box[i];
            }
            Arrays.sort(sorted, Comparator.comparingInt(rgb -> rgb >> shift & 0xFF));
            int half = sorted.length / 2;
            int[] a = new int[half], b = new int[sorted.length - half];
            for (int i = 0; i < sorted.length; i++) {
                if (i < half) {
                    a[i] = sorted[i];
                } else {
                    b[i - half] = sorted[i];
                }
            }
            boxes.add(a);
            boxes.add(b);
        }
        int[] palette = new int[boxes.size()];
        for (int i = 0; i < boxes.size(); i++) {
            long r = 0, g = 0, b = 0;
            int[] box = boxes.get(i);
            for (int rgb : box) {
                r += rgb >> 16 & 0xFF;
                g += rgb >> 8 & 0xFF;
                b += rgb & 0xFF;
            }
            int count = Math.max(1, box.length);
            palette[i] = (int) (r / count) << 16 | (int) (g / count) << 8 | (int) (b / count);
        }
        return palette;
    }
}
