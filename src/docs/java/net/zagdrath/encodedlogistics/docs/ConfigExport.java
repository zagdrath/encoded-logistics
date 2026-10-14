/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.docs;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.locale.Language;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.zagdrath.encodedlogistics.Config;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.client.screen.TerminalSettings;

// Both config specs, walked as NeoForge builds them: each section with its options in order, and each option's key,
// type, default, range or allowed values, comment and config-screen label (from the lang file, by key, as NeoForge's
// config screen finds it).
final class ConfigExport {
    private ConfigExport() {}

    static JsonObject config() {
        JsonArray files = new JsonArray();
        files.add(file(EncodedLogistics.localConfigType(), Config.SPEC));
        files.add(file(ModConfig.Type.CLIENT, TerminalSettings.SPEC));
        JsonObject json = new JsonObject();
        json.add("files", files);
        return json;
    }

    private static JsonObject file(ModConfig.Type type, ModConfigSpec spec) {
        String name = EncodedLogistics.MODID + "-" + type.extension() + ".toml";
        JsonObject json = new JsonObject();
        json.addProperty("type", type.name());
        json.addProperty("fileName", name);
        // LOCAL and CLIENT files live in the game folder's config/ folder; the run created this one if it exists.
        json.addProperty("foundInRun", Files.exists(FMLPaths.CONFIGDIR.get().resolve(name)));
        JsonArray sections = new JsonArray();
        walk(spec, spec.getSpec(), new ArrayList<>(), sections, null);
        json.add("sections", sections);
        return json;
    }

    private static void walk(ModConfigSpec spec, UnmodifiableConfig config, List<String> path, JsonArray sections, JsonArray options) {
        for (Map.Entry<String, Object> entry : config.valueMap().entrySet()) {
            List<String> child = new ArrayList<>(path);
            child.add(entry.getKey());
            if (entry.getValue() instanceof UnmodifiableConfig section) {
                JsonObject json = new JsonObject();
                json.addProperty("path", String.join(".", child));
                json.addProperty("label", label(entry.getKey()));
                json.addProperty("comment", spec.getLevelComment(child));
                JsonArray inner = new JsonArray();
                json.add("options", inner);
                sections.add(json);
                walk(spec, section, child, sections, inner);
            } else if (entry.getValue() instanceof ModConfigSpec.ValueSpec value && options != null) {
                options.add(option(entry.getKey(), child, value));
            }
        }
    }

    private static JsonObject option(String key, List<String> path, ModConfigSpec.ValueSpec value) {
        JsonObject json = new JsonObject();
        Object fallback = value.getDefault();
        json.addProperty("key", key);
        json.addProperty("path", String.join(".", path));
        json.addProperty("type", type(fallback));
        json.add("default", element(fallback));
        ModConfigSpec.Range<?> range = value.getRange();
        if (range != null) {
            json.add("min", element(range.getMin()));
            json.add("max", element(range.getMax()));
        }
        if (fallback instanceof Enum<?> constant) {
            JsonArray allowed = new JsonArray();
            for (Object other : constant.getDeclaringClass().getEnumConstants()) {
                if (value.test(other) || value.test(((Enum<?>) other).name())) {
                    allowed.add(((Enum<?>) other).name());
                }
            }
            json.add("allowed", allowed);
        }
        json.addProperty("comment", value.getComment());
        json.addProperty("label", label(key));
        json.addProperty("restart", value.restartType().name());
        return json;
    }

    // The config screen's label for a key or section, or null when the lang file has none.
    private static String label(String key) {
        String translation = EncodedLogistics.MODID + ".configuration." + key;
        return Language.getInstance().has(translation) ? Language.getInstance().getOrDefault(translation) : null;
    }

    private static String type(Object value) {
        return switch (value) {
            case Boolean b -> "boolean";
            case Integer i -> "int";
            case Long l -> "long";
            case Double d -> "double";
            case Enum<?> e -> "enum";
            case List<?> l -> "list";
            case String s -> "string";
            default -> value.getClass().getSimpleName();
        };
    }

    private static JsonElement element(Object value) {
        return switch (value) {
            case null -> JsonNull.INSTANCE;
            case Boolean b -> new JsonPrimitive(b);
            case Number n -> new JsonPrimitive(n);
            case Enum<?> e -> new JsonPrimitive(e.name());
            case List<?> list -> {
                JsonArray array = new JsonArray();
                list.forEach(item -> array.add(element(item)));
                yield array;
            }
            default -> new JsonPrimitive(value.toString());
        };
    }
}
