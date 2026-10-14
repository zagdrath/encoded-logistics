/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.docs;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jspecify.annotations.Nullable;

// Reading and writing the export's JSON: pretty-printed, keys in the order they were added, LF line ends.
final class Json {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeNulls().create();

    private Json() {}

    static void write(Path file, JsonElement json) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, GSON.toJson(json).replace("\r\n", "\n") + "\n", StandardCharsets.UTF_8);
    }

    // Removes the export's own files from earlier runs (name.json), so a renamed one doesn't linger.
    static void clean(Path folder, String... names) throws IOException {
        for (String name : names) {
            Files.deleteIfExists(folder.resolve(name + ".json"));
        }
    }

    // A JSON resource on the classpath (the mod's or the game's), or null.
    static @Nullable JsonObject resource(String path) {
        try (InputStream in = Json.class.getResourceAsStream(path)) {
            if (in == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        } catch (IOException | RuntimeException e) {
            DocsExport.LOGGER.warn("Couldn't read {}", path, e);
            return null;
        }
    }
}
