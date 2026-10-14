/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.docs;

import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.zagdrath.encodedlogistics.EncodedLogistics;

// Recipes, worldgen and loot as the server's data packs hold them (the mod's files, generated or written by hand),
// each recipe marked with whether it loaded: the Arc Crusher's need Arcforge, and their conditions are kept.
final class RecipeExport {
    private RecipeExport() {}

    // Every recipe that is the mod's or that uses or makes one of its items, by id.
    static JsonObject recipes(MinecraftServer server) throws IOException {
        Map<String, JsonObject> sorted = new TreeMap<>();
        for (Map.Entry<Identifier, JsonObject> entry : read(server, "recipe").entrySet()) {
            Identifier id = entry.getKey();
            JsonObject recipe = entry.getValue();
            if (!RegistryExport.ours(id) && !recipe.toString().contains(EncodedLogistics.MODID + ":")) {
                continue;
            }
            JsonObject json = new JsonObject();
            json.addProperty("id", id.toString());
            json.addProperty("loaded", server.getRecipeManager().byKey(ResourceKey.create(Registries.RECIPE, id)).isPresent());
            json.add("data", recipe);
            sorted.put(id.toString(), json);
        }
        JsonObject json = new JsonObject();
        sorted.forEach(json::add);
        return json;
    }

    // The mod's ore features, their placement and biome modifiers, and its blocks' loot tables.
    static JsonObject worldgen(MinecraftServer server) throws IOException {
        JsonObject json = new JsonObject();
        json.add("configuredFeatures", ours(read(server, "worldgen/feature")));
        json.add("placedFeatures", ours(read(server, "worldgen/placed_feature")));
        json.add("biomeModifiers", ours(read(server, "neoforge/biome_modifier")));
        json.add("blockLoot", ours(read(server, "loot_table/blocks")));
        return json;
    }

    private static JsonObject ours(Map<Identifier, JsonObject> files) {
        JsonObject json = new JsonObject();
        files.forEach((id, file) -> {
            if (RegistryExport.ours(id)) {
                json.add(id.toString(), file);
            }
        });
        return json;
    }

    // The JSON files under data/<namespace>/<folder>/, by id (the path below the folder, without .json).
    private static Map<Identifier, JsonObject> read(MinecraftServer server, String folder) throws IOException {
        Map<Identifier, JsonObject> files = new TreeMap<>();
        for (Map.Entry<Identifier, Resource> entry : server.getResourceManager()
                .listResources(folder, id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier file = entry.getKey();
            String path = file.getPath().substring(folder.length() + 1, file.getPath().length() - ".json".length());
            try (Reader reader = entry.getValue().openAsReader()) {
                JsonElement json = JsonParser.parseReader(reader);
                if (json.isJsonObject()) {
                    files.put(Identifier.fromNamespaceAndPath(file.getNamespace(), path), json.getAsJsonObject());
                }
            }
        }
        return files;
    }
}
