/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.docs;

import com.google.gson.JsonObject;
import net.minecraft.SharedConstants;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.common.NeoForgeVersion;
import net.zagdrath.encodedlogistics.EncodedLogistics;

// The versions the documentation was built from (each page's footer).
final class ModInfoExport {
    private ModInfoExport() {}

    static JsonObject mod(MinecraftServer server) {
        JsonObject json = new JsonObject();
        json.addProperty("id", EncodedLogistics.MODID);
        ModList.get().getModContainerById(EncodedLogistics.MODID).ifPresent(container -> {
            json.addProperty("name", container.getModInfo().getDisplayName());
            json.addProperty("version", container.getModInfo().getVersion().toString());
        });
        json.addProperty("minecraft", SharedConstants.getCurrentVersion().name());
        json.addProperty("neoforge", NeoForgeVersion.getVersion());
        json.addProperty("arcforgeLoaded", ModList.get().isLoaded("arcforge"));
        return json;
    }
}
