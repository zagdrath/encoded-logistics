/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.docs;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import org.slf4j.Logger;

// The documentation site's data export (docs/site-src/PUBLISHING.md). In the docsExport run (./gradlew
// exportDocsData) the server starts, writes the mod's data as JSON to the folder in encodedlogistics.docs.export and
// stops: registries, display names and tags, every recipe, worldgen and loot, the config, the ELCL command registry and
// messages, system values, device types and sounds. A failed export stops the run with exit code 1.
@EventBusSubscriber(modid = EncodedLogistics.MODID)
public final class DocsExport {
    static final Logger LOGGER = LogUtils.getLogger();
    public static final String PROPERTY = "encodedlogistics.docs.export";

    private DocsExport() {}

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        String folder = System.getProperty(PROPERTY);
        if (folder == null || System.getProperty(DocsIcons.PROPERTY) != null) {
            return;
        }
        MinecraftServer server = event.getServer();
        try {
            export(server, Path.of(folder));
            LOGGER.info("Documentation data written to {}", folder);
            server.halt(false);
        } catch (Throwable e) {
            LOGGER.error("Documentation export failed", e);
            Runtime.getRuntime().halt(1);
        }
    }

    static void export(MinecraftServer server, Path folder) throws IOException {
        Json.clean(folder, "registry", "recipes", "worldgen", "config", "elcl", "devices", "lang", "mod");
        RegistryExport registries = new RegistryExport(server);
        JsonObject recipes = RecipeExport.recipes(server);
        registries.addReferencedTags(recipes);
        Json.write(folder.resolve("mod.json"), ModInfoExport.mod(server));
        Json.write(folder.resolve("registry.json"), registries.registry());
        Json.write(folder.resolve("recipes.json"), recipes);
        Json.write(folder.resolve("worldgen.json"), RecipeExport.worldgen(server));
        Json.write(folder.resolve("config.json"), ConfigExport.config());
        Json.write(folder.resolve("elcl.json"), ElclExport.elcl());
        Json.write(folder.resolve("devices.json"), DeviceExport.devices(server));
        Json.write(folder.resolve("lang.json"), registries.lang());
    }

    static JsonArray strings(Iterable<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }

    static <V> Map<String, V> sorted(Map<String, V> map) {
        return new TreeMap<>(map);
    }

    static List<String> list(Iterable<String> values) {
        List<String> list = new ArrayList<>();
        values.forEach(list::add);
        return list;
    }
}
