/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.signal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.jspecify.annotations.Nullable;

import net.minecraft.util.Util;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.zagdrath.encodedlogistics.net.AudioPayloads;
import net.zagdrath.encodedlogistics.signal.AudioFiles;

// The client's Speaker audio: files the server sent (asked for by hash, assembled from its chunks, kept by hash up to
// BUDGET bytes, least recently played dropped first), and web URLs this game fetched itself (no redirects - they could
// leave the server's allowed hosts - at most maxBytes, the last few kept). Cleared on leaving the world.
final class AudioCache {
    private static final long BUDGET = 64L * 1024 * 1024;
    private static final int URLS_KEPT = 8;
    private static final long REQUEST_TIMEOUT_MS = 30_000;

    private record Assembly(byte[][] parts, int[] received, long since) {}

    private static final LinkedHashMap<String, byte[]> FILES = new LinkedHashMap<>(16, 0.75F, true);
    private static final Map<String, Assembly> PENDING = new HashMap<>();
    private static final Set<String> MISSING = new HashSet<>();
    private static final Map<String, CompletableFuture<byte[]>> URLS = new LinkedHashMap<>(16, 0.75F, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, CompletableFuture<byte[]>> eldest) {
            return size() > URLS_KEPT;
        }
    };
    private static long size;

    private AudioCache() {}

    // --- Files from the server ---

    static synchronized byte @Nullable [] file(String hash) {
        return FILES.get(hash);
    }

    // Whether the server said it hasn't got the file.
    static synchronized boolean missing(String hash) {
        return MISSING.contains(hash);
    }

    // Asks the server for a file (once, again only if the last ask went unanswered for a while).
    static synchronized void request(String hash) {
        if (FILES.containsKey(hash) || MISSING.contains(hash)) {
            return;
        }
        Assembly pending = PENDING.get(hash);
        if (pending != null && Util.getMillis() - pending.since() < REQUEST_TIMEOUT_MS) {
            return;
        }
        PENDING.put(hash, new Assembly(new byte[0][], new int[1], Util.getMillis()));
        ClientPacketDistributor.sendToServer(new AudioPayloads.Request(hash));
    }

    static synchronized void chunk(String hash, int index, int total, byte[] bytes) {
        if (total <= 0) {
            PENDING.remove(hash);
            MISSING.add(hash);
            return;
        }
        Assembly assembly = PENDING.get(hash);
        if (assembly == null || index < 0 || index >= total) {
            return;
        }
        if (assembly.parts().length != total) {
            assembly = new Assembly(new byte[total][], new int[1], assembly.since());
            PENDING.put(hash, assembly);
        }
        if (assembly.parts()[index] == null) {
            assembly.parts()[index] = bytes;
            assembly.received()[0]++;
        }
        if (assembly.received()[0] < total) {
            return;
        }
        PENDING.remove(hash);
        int length = 0;
        for (byte[] part : assembly.parts()) {
            length += part.length;
        }
        byte[] whole = new byte[length];
        int at = 0;
        for (byte[] part : assembly.parts()) {
            System.arraycopy(part, 0, whole, at, part.length);
            at += part.length;
        }
        if (!AudioFiles.hash(whole).equals(hash)) {
            MISSING.add(hash);
            return;
        }
        FILES.put(hash, whole);
        size += whole.length;
        var eldest = FILES.entrySet().iterator();
        while (size > BUDGET && FILES.size() > 1 && eldest.hasNext()) {
            size -= eldest.next().getValue().length;
            eldest.remove();
        }
    }

    // --- Web audio ---

    // The URL's bytes (fetched once, kept for the next few plays); fails with a message for the popup.
    static synchronized CompletableFuture<byte[]> url(String url, int maxBytes) {
        CompletableFuture<byte[]> known = URLS.get(url);
        if (known != null && !known.isCompletedExceptionally()) {
            return known;
        }
        CompletableFuture<byte[]> fetch = CompletableFuture.supplyAsync(() -> fetch(url, maxBytes), Util.nonCriticalIoPool());
        URLS.put(url, fetch);
        return fetch;
    }

    private static byte[] fetch(String url, int maxBytes) {
        try {
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(10)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(30)).header("User-Agent", "EncodedLogistics-Speaker")
                    .GET().build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new CompletionException(new IOException("web: HTTP " + response.statusCode()));
                }
                byte[] bytes = body.readNBytes(maxBytes + 1);
                if (bytes.length > maxBytes) {
                    throw new CompletionException(new IOException("web: too large"));
                }
                return bytes;
            }
        } catch (IOException | IllegalArgumentException e) {
            throw new CompletionException(new IOException(e.getMessage() != null && e.getMessage().startsWith("web:") ? e.getMessage() : "web: no answer", e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CompletionException(new IOException("web: no answer", e));
        }
    }

    static synchronized void clear() {
        FILES.clear();
        PENDING.clear();
        MISSING.clear();
        URLS.clear();
        size = 0;
    }
}
