/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.docs;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;

// The registries: the mod's items and blocks (names, tooltips, tags, block properties, creative tabs), the names of
// every item and block in the game (for recipes and tags that use other mods' and vanilla's), the tags the mod's things
// are in or its recipes use, and the mod's block entity, menu, entity, sound, data component and recipe registrations.
final class RegistryExport {
    private final MinecraftServer server;
    // Item and block tags to export: every tag holding one of the mod's, every tag in the mod's namespace, and (added
    // by addReferencedTags) every tag a recipe uses.
    private final Set<TagKey<Item>> itemTags = new LinkedHashSet<>();
    private final Set<TagKey<Block>> blockTags = new LinkedHashSet<>();

    RegistryExport(MinecraftServer server) {
        this.server = server;
        for (Item item : BuiltInRegistries.ITEM) {
            if (ours(BuiltInRegistries.ITEM.getKey(item))) {
                item.builtInRegistryHolder().tags().forEach(itemTags::add);
            }
        }
        BuiltInRegistries.ITEM.getTags().map(HolderSet.Named::key).filter(tag -> ours(tag.location())).forEach(itemTags::add);
        for (Block block : BuiltInRegistries.BLOCK) {
            if (ours(BuiltInRegistries.BLOCK.getKey(block))) {
                block.builtInRegistryHolder().tags().forEach(blockTags::add);
            }
        }
        BuiltInRegistries.BLOCK.getTags().map(HolderSet.Named::key).filter(tag -> ours(tag.location())).forEach(blockTags::add);
    }

    static boolean ours(Identifier id) {
        return id.getNamespace().equals(EncodedLogistics.MODID);
    }

    // Every "#namespace:path" string anywhere in the recipes is an item tag the site has to resolve.
    void addReferencedTags(JsonElement recipes) {
        if (recipes.isJsonObject()) {
            recipes.getAsJsonObject().entrySet().forEach(entry -> addReferencedTags(entry.getValue()));
        } else if (recipes.isJsonArray()) {
            recipes.getAsJsonArray().forEach(this::addReferencedTags);
        } else if (recipes instanceof JsonPrimitive primitive && primitive.isString()) {
            String text = primitive.getAsString();
            if (text.startsWith("#") && Identifier.tryParse(text.substring(1)) instanceof Identifier id) {
                itemTags.add(TagKey.create(BuiltInRegistries.ITEM.key(), id));
            }
        }
    }

    JsonObject registry() {
        JsonObject json = new JsonObject();
        json.add("items", items());
        json.add("blocks", blocks());
        json.add("names", names());
        json.add("itemTags", tags(BuiltInRegistries.ITEM, itemTags));
        json.add("blockTags", tags(BuiltInRegistries.BLOCK, blockTags));
        json.add("creativeTabs", creativeTabs());
        json.add("blockEntityTypes", blockEntityTypes());
        json.add("menuTypes", ids(BuiltInRegistries.MENU));
        json.add("entityTypes", entityTypes());
        json.add("soundEvents", sounds());
        json.add("dataComponents", dataComponents());
        json.add("recipeTypes", ids(BuiltInRegistries.RECIPE_TYPE));
        json.add("recipeSerializers", ids(BuiltInRegistries.RECIPE_SERIALIZER));
        return json;
    }

    private JsonArray items() {
        JsonArray array = new JsonArray();
        Item.TooltipContext context = Item.TooltipContext.of(server.overworld());
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (!ours(id)) {
                continue;
            }
            ItemStack stack = new ItemStack(item);
            JsonObject json = new JsonObject();
            json.addProperty("id", id.toString());
            json.addProperty("name", stack.getHoverName().getString());
            json.addProperty("translationKey", item.getDescriptionId());
            json.addProperty("maxStackSize", stack.getMaxStackSize());
            json.addProperty("rarity", stack.getRarity().getSerializedName());
            if (item instanceof BlockItem blockItem) {
                json.addProperty("block", BuiltInRegistries.BLOCK.getKey(blockItem.getBlock()).toString());
            }
            json.add("tooltip", tooltip(stack, context));
            json.add("tags", tagNames(item.builtInRegistryHolder().tags()));
            json.addProperty("class", item.getClass().getSimpleName());
            array.add(json);
        }
        return array;
    }

    // The tooltip a player sees below the name (server side: what the item and common tooltip events add; the icons
    // run adds the client-side lines).
    private static JsonArray tooltip(ItemStack stack, Item.TooltipContext context) {
        JsonArray lines = new JsonArray();
        try {
            List<Component> components = stack.getTooltipLines(context, null, TooltipFlag.NORMAL);
            for (int i = 1; i < components.size(); i++) {
                lines.add(components.get(i).getString());
            }
        } catch (RuntimeException e) {
            DocsExport.LOGGER.warn("No tooltip for {}", stack, e);
        }
        return lines;
    }

    private JsonArray blocks() {
        JsonArray array = new JsonArray();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            if (!ours(id)) {
                continue;
            }
            BlockState state = block.defaultBlockState();
            JsonObject json = new JsonObject();
            json.addProperty("id", id.toString());
            json.addProperty("name", block.getName().getString());
            json.addProperty("translationKey", block.getDescriptionId());
            Item item = block.asItem();
            json.addProperty("item", item == net.minecraft.world.item.Items.AIR ? null : BuiltInRegistries.ITEM.getKey(item).toString());
            json.addProperty("hardness", block.defaultDestroyTime());
            json.addProperty("blastResistance", block.getExplosionResistance());
            json.addProperty("requiresTool", state.requiresCorrectToolForDrops());
            json.addProperty("lightEmission", state.getLightEmission());
            json.add("tags", tagNames(block.builtInRegistryHolder().tags()));
            json.addProperty("class", block.getClass().getSimpleName());
            array.add(json);
        }
        return array;
    }

    // Every item's and block's display name in the game, by id (recipes and tags name vanilla's and other mods').
    private static JsonObject names() {
        JsonObject json = new JsonObject();
        JsonObject items = new JsonObject();
        for (Item item : BuiltInRegistries.ITEM) {
            items.addProperty(BuiltInRegistries.ITEM.getKey(item).toString(), new ItemStack(item).getHoverName().getString());
        }
        JsonObject blocks = new JsonObject();
        for (Block block : BuiltInRegistries.BLOCK) {
            blocks.addProperty(BuiltInRegistries.BLOCK.getKey(block).toString(), block.getName().getString());
        }
        json.add("items", items);
        json.add("blocks", blocks);
        return json;
    }

    private static JsonArray tagNames(java.util.stream.Stream<? extends TagKey<?>> tags) {
        TreeSet<String> names = new TreeSet<>();
        tags.forEach(tag -> names.add(tag.location().toString()));
        return DocsExport.strings(names);
    }

    private static <T> JsonObject tags(Registry<T> registry, Collection<TagKey<T>> keys) {
        Map<String, JsonArray> sorted = new java.util.TreeMap<>();
        for (TagKey<T> key : keys) {
            JsonArray members = new JsonArray();
            registry.get(key).ifPresent(set -> {
                for (Holder<T> holder : set) {
                    members.add(registry.getKey(holder.value()).toString());
                }
            });
            sorted.put(key.location().toString(), members);
        }
        JsonObject json = new JsonObject();
        sorted.forEach(json::add);
        return json;
    }

    private JsonArray creativeTabs() {
        JsonArray array = new JsonArray();
        CreativeModeTabs.tryRebuildTabContents(server.getWorldData().enabledFeatures(), true, server.registryAccess());
        for (CreativeModeTab tab : BuiltInRegistries.CREATIVE_MODE_TAB) {
            Identifier id = BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab);
            if (!ours(id)) {
                continue;
            }
            JsonObject json = new JsonObject();
            json.addProperty("id", id.toString());
            json.addProperty("title", tab.getDisplayName().getString());
            json.addProperty("icon", BuiltInRegistries.ITEM.getKey(tab.getIconItem().getItem()).toString());
            Set<String> contents = new LinkedHashSet<>();
            tab.getDisplayItems().forEach(stack -> contents.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
            json.add("items", DocsExport.strings(contents));
            array.add(json);
        }
        return array;
    }

    // Block entity types with their blocks, and the device type code a network gives each (ElclDevices).
    private JsonArray blockEntityTypes() {
        JsonArray array = new JsonArray();
        for (BlockEntityType<?> type : BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            Identifier id = BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(type);
            if (!ours(id)) {
                continue;
            }
            JsonObject json = new JsonObject();
            json.addProperty("id", id.toString());
            Set<String> blocks = new TreeSet<>();
            String code = null;
            for (Block block : type.getValidBlocks()) {
                blocks.add(BuiltInRegistries.BLOCK.getKey(block).toString());
                if (code == null) {
                    code = deviceCode(type, block);
                }
            }
            json.add("blocks", DocsExport.strings(blocks));
            json.addProperty("deviceType", code);
            array.add(json);
        }
        return array;
    }

    private static String deviceCode(BlockEntityType<?> type, Block block) {
        try {
            BlockEntity entity = type.create(BlockPos.ZERO, block.defaultBlockState());
            return entity == null ? null : ElclDevices.code(entity);
        } catch (RuntimeException e) {
            DocsExport.LOGGER.warn("Couldn't create {} for its device type", type, e);
            return null;
        }
    }

    private static JsonArray entityTypes() {
        JsonArray array = new JsonArray();
        BuiltInRegistries.ENTITY_TYPE.forEach(type -> {
            Identifier id = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            if (ours(id)) {
                JsonObject json = new JsonObject();
                json.addProperty("id", id.toString());
                json.addProperty("name", type.getDescription().getString());
                array.add(json);
            }
        });
        return array;
    }

    // Sound events with their subtitles and files (assets/encodedlogistics/sounds.json).
    private static JsonArray sounds() {
        JsonObject definitions = Json.resource("/assets/" + EncodedLogistics.MODID + "/sounds.json");
        JsonArray array = new JsonArray();
        BuiltInRegistries.SOUND_EVENT.forEach(sound -> {
            Identifier id = BuiltInRegistries.SOUND_EVENT.getKey(sound);
            if (!ours(id)) {
                return;
            }
            JsonObject json = new JsonObject();
            json.addProperty("id", id.toString());
            JsonObject definition = definitions != null && definitions.has(id.getPath()) ? definitions.getAsJsonObject(id.getPath()) : null;
            if (definition != null && definition.has("subtitle")) {
                String key = definition.get("subtitle").getAsString();
                json.addProperty("subtitleKey", key);
                json.addProperty("subtitle", Language.getInstance().getOrDefault(key));
            }
            if (definition != null && definition.has("sounds")) {
                json.add("sounds", definition.get("sounds"));
            }
            array.add(json);
        });
        return array;
    }

    private static JsonArray dataComponents() {
        JsonArray array = new JsonArray();
        BuiltInRegistries.DATA_COMPONENT_TYPE.forEach(type -> {
            Identifier id = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type);
            if (ours(id)) {
                JsonObject json = new JsonObject();
                json.addProperty("id", id.toString());
                json.addProperty("persistent", !type.isTransient());
                array.add(json);
            }
        });
        return array;
    }

    private static <T> JsonArray ids(Registry<T> registry) {
        JsonArray array = new JsonArray();
        registry.keySet().stream().filter(RegistryExport::ours).map(Identifier::toString).sorted().forEach(array::add);
        return array;
    }

    // The mod's en_us language file as it ships (screen help, GUI wording and the rest the pages quote).
    JsonObject lang() {
        JsonObject json = Json.resource("/assets/" + EncodedLogistics.MODID + "/lang/en_us.json");
        if (json == null) {
            throw new IllegalStateException("The mod's en_us.json isn't on the classpath");
        }
        Map<String, JsonElement> sorted = new LinkedHashMap<>();
        json.entrySet().forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        JsonObject result = new JsonObject();
        sorted.forEach(result::add);
        return result;
    }
}
