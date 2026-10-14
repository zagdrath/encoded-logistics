/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.docs;

import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.part.PartType;
import net.zagdrath.encodedlogistics.rack.RackDeviceType;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisPart;
import net.zagdrath.encodedlogistics.registry.ModItems;

// The devices a network names (Work with Devices, ELCL's DEV parameters): each kind's type code and the name prefix
// its devices are numbered from (UPS01, ELDESK01), as ElclDevices gives them; the rack device types with their sizes;
// and the chassis parts.
final class DeviceExport {
    private DeviceExport() {}

    static JsonObject devices(MinecraftServer server) {
        JsonObject json = new JsonObject();
        JsonArray types = new JsonArray();
        for (RackDeviceType type : RackDeviceType.all()) {
            String code = ElclDevices.code(type);
            types.add(device("rack", BuiltInRegistries.ITEM.getKey(type.item()).toString(), code, type.id().toString(), type.size()));
        }
        for (PartType part : PartType.values()) {
            String item = BuiltInRegistries.ITEM.getKey(ModItems.part(part).get()).toString();
            types.add(device("part", item, ElclDevices.code(part, false), part.getSerializedName(), 0));
            String wireless = ElclDevices.code(part, true);
            if (!wireless.equals(ElclDevices.code(part, false))) {
                types.add(device("wirelessPort", "encodedlogistics:wireless_" + part.getSerializedName(), wireless, part.getSerializedName(), 0));
            }
        }
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
            if (!RegistryExport.ours(id)) {
                continue;
            }
            Set<String> blocks = new TreeSet<>();
            String code = null;
            for (Block block : type.getValidBlocks()) {
                blocks.add(BuiltInRegistries.BLOCK.getKey(block).toString());
                BlockEntity entity = type.create(BlockPos.ZERO, block.defaultBlockState());
                if (code == null && entity != null) {
                    code = ElclDevices.code(entity);
                }
            }
            if (code != null) {
                for (String block : blocks) {
                    types.add(device("block", block, code, id.toString(), 0));
                }
            }
        }
        json.add("types", types);
        JsonArray parts = new JsonArray();
        for (ChassisPart part : ChassisPart.values()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("item", "encodedlogistics:" + part.id());
            entry.addProperty("kind", part.kind().name());
            parts.add(entry);
        }
        json.add("chassisParts", parts);
        return json;
    }

    private static JsonObject device(String kind, String item, String code, String source, int units) {
        JsonObject json = new JsonObject();
        json.addProperty("kind", kind);
        json.addProperty("item", item);
        json.addProperty("type", code);
        json.addProperty("prefix", ElclDevices.prefix(code));
        json.addProperty("example", ElclDevices.prefix(code) + "01");
        json.addProperty("source", source);
        if (units > 0) {
            json.addProperty("units", units);
        }
        return json;
    }
}
