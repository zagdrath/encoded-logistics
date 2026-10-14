/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.docs;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.GuiItemAtlas;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.item.TrackingItemStackRenderState;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.zagdrath.encodedlogistics.EncodedLogistics;
import net.zagdrath.encodedlogistics.client.ItemInfoTooltips;
import org.jspecify.annotations.Nullable;

// The documentation site's icons (docs/site-src/PUBLISHING.md). In the docsIcons run (./gradlew renderDocsIcons) the
// client opens a small test world, renders every item the site shows - the mod's own, and every other item its
// recipes and tags name - through the game's own GUI item atlas (GuiItemAtlas, as inventories draw them: blocks
// isometric, lit as in a slot) at 64 px, writes each as <namespace>/<path>.png and quits. It also writes the
// tooltips as a client sees them (the client-side lines too) and a report of what didn't render.
@EventBusSubscriber(modid = EncodedLogistics.MODID, value = Dist.CLIENT)
public final class DocsIcons {
    public static final String PROPERTY = "encodedlogistics.docs.icons";
    private static final int SIZE = 64;
    private static final int ATLAS = 1024;

    private static @Nullable Deque<Item> queue;
    private static final List<String> failed = new ArrayList<>();
    private static int rendered;
    private static boolean waiting;
    private static int idleTicks;
    private static boolean openedWorld;
    private static final String WORLD = "docs-icons";

    private DocsIcons() {}

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        String folder = System.getProperty(PROPERTY);
        Minecraft minecraft = Minecraft.getInstance();
        if (folder == null || waiting || minecraft.gui.overlay() instanceof LoadingOverlay) {
            return;
        }
        // Items' components are bound only once a world's registries load, so the icons are drawn in a world: a
        // small test world, made the first time (run/docsIcons/saves/docs-icons) and opened from the title screen.
        if (minecraft.level == null || minecraft.player == null) {
            if (minecraft.gui.screen() instanceof TitleScreen && !openedWorld) {
                openedWorld = true;
                openWorld(minecraft);
            }
            return;
        }
        // A few ticks in the world first, so every model and atlas has settled.
        if (idleTicks++ < 40) {
            return;
        }
        Path out = Path.of(folder);
        try {
            if (queue == null) {
                queue = new ArrayDeque<>(items());
                DocsExport.LOGGER.info("Rendering {} icons to {}", queue.size(), out);
                writeTooltips();
            }
            if (queue.isEmpty()) {
                finish(out);
                minecraft.stop();
                return;
            }
            renderBatch(minecraft, out);
        } catch (Throwable e) {
            DocsExport.LOGGER.error("Icon rendering failed", e);
            Runtime.getRuntime().halt(1);
        }
    }

    private static void openWorld(Minecraft minecraft) {
        if (minecraft.getLevelSource().levelExists(WORLD)) {
            minecraft.createWorldOpenFlows().openWorld(WORLD, () -> minecraft.gui.setScreen(new TitleScreen()));
        } else {
            LevelSettings settings = new LevelSettings(WORLD, GameType.SPECTATOR, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
            minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, WorldOptions.testWorldWithRandomSeed(), WorldPresets::createTestWorldDimensions,
                    new TitleScreen());
        }
    }

    // The mod's items, then every item in the tags and recipes the export wrote (registry.json, recipes.json).
    private static List<Item> items() throws IOException {
        Set<Item> items = new LinkedHashSet<>();
        for (Item item : BuiltInRegistries.ITEM) {
            if (RegistryExport.ours(BuiltInRegistries.ITEM.getKey(item))) {
                items.add(item);
            }
        }
        Path data = Path.of(System.getProperty(DocsExport.PROPERTY));
        Set<String> ids = new LinkedHashSet<>();
        if (Files.exists(data.resolve("registry.json"))) {
            JsonObject registry = JsonParser.parseString(Files.readString(data.resolve("registry.json"))).getAsJsonObject();
            registry.getAsJsonObject("itemTags").entrySet().forEach(entry -> entry.getValue().getAsJsonArray().forEach(id -> ids.add(id.getAsString())));
        }
        if (Files.exists(data.resolve("recipes.json"))) {
            collect(JsonParser.parseString(Files.readString(data.resolve("recipes.json"))), ids);
        }
        for (String id : ids) {
            Identifier key = Identifier.tryParse(id);
            if (key != null) {
                BuiltInRegistries.ITEM.getOptional(key).ifPresent(items::add);
            }
        }
        items.remove(net.minecraft.world.item.Items.AIR);
        return new ArrayList<>(items);
    }

    // Every "namespace:path" string in the recipes that names an item.
    private static void collect(JsonElement json, Set<String> ids) {
        if (json.isJsonObject()) {
            json.getAsJsonObject().entrySet().forEach(entry -> collect(entry.getValue(), ids));
        } else if (json.isJsonArray()) {
            json.getAsJsonArray().forEach(element -> collect(element, ids));
        } else if (json.isJsonPrimitive() && json.getAsJsonPrimitive().isString()) {
            String text = json.getAsString();
            if (!text.startsWith("#") && text.indexOf(':') > 0) {
                ids.add(text);
            }
        }
    }

    // One atlas of icons: draw them, copy the atlas back from the GPU, and write each slot when the copy lands.
    private static void renderBatch(Minecraft minecraft, Path out) {
        List<Item> batch = new ArrayList<>();
        List<TrackingItemStackRenderState> states = new ArrayList<>();
        int capacity = (ATLAS / SIZE) * (ATLAS / SIZE);
        while (!queue.isEmpty() && batch.size() < capacity) {
            Item item = queue.poll();
            TrackingItemStackRenderState state = new TrackingItemStackRenderState();
            minecraft.getItemModelResolver().updateForTopItem(state, new ItemStack(item), ItemDisplayContext.GUI, null, null, 0);
            if (state.isEmpty()) {
                failed.add(BuiltInRegistries.ITEM.getKey(item) + " (no model)");
                continue;
            }
            batch.add(item);
            states.add(state);
        }
        GuiItemAtlas atlas = new GuiItemAtlas(minecraft.gameRenderer.featureRenderDispatcher(), ATLAS, SIZE);
        Set<Object> models = new HashSet<>();
        states.forEach(state -> models.add(state.getModelIdentity()));
        atlas.tryPrepareFor(models);
        List<GuiItemAtlas.SlotView> slots = new ArrayList<>();
        GpuTexture texture = null;
        for (TrackingItemStackRenderState state : states) {
            GuiItemAtlas.SlotView slot = atlas.getOrUpdate(state);
            slots.add(slot);
            if (slot != null) {
                texture = slot.textureView().texture();
            }
        }
        if (texture == null) {
            batch.forEach(item -> failed.add(BuiltInRegistries.ITEM.getKey(item) + " (not drawn)"));
            atlas.close();
            return;
        }
        GpuTexture source = texture;
        int pixel = source.getFormat().blockSize();
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Docs icon readback", 9, (long) ATLAS * ATLAS * pixel);
        waiting = true;
        RenderSystem.getDevice().createCommandEncoder().copyTextureToBuffer(source, buffer, 0L, () -> {
            try (GpuBufferSlice.MappedView read = buffer.map(true, false)) {
                for (int i = 0; i < batch.size(); i++) {
                    GuiItemAtlas.SlotView slot = slots.get(i);
                    Identifier id = BuiltInRegistries.ITEM.getKey(batch.get(i));
                    if (slot == null) {
                        failed.add(id + " (no atlas slot)");
                        continue;
                    }
                    // The slot's top-left in texture rows counted from the top (the atlas is drawn bottom-up).
                    int left = Math.round(slot.u0() * ATLAS);
                    int top = Math.round((1.0F - slot.v0()) * ATLAS);
                    try (NativeImage image = new NativeImage(SIZE, SIZE, true)) {
                        boolean any = false;
                        for (int y = 0; y < SIZE; y++) {
                            for (int x = 0; x < SIZE; x++) {
                                int row = ATLAS - 1 - (top + y);
                                int abgr = read.data().getInt(((left + x) + row * ATLAS) * pixel);
                                image.setPixelABGR(x, y, abgr);
                                any |= (abgr >>> 24) != 0;
                            }
                        }
                        if (!any) {
                            failed.add(id + " (rendered empty)");
                            continue;
                        }
                        Path file = out.resolve(id.getNamespace()).resolve(id.getPath() + ".png");
                        Files.createDirectories(file.getParent());
                        image.writeToFile(file);
                        rendered++;
                    }
                }
            } catch (IOException e) {
                DocsExport.LOGGER.error("Couldn't write icons", e);
                Runtime.getRuntime().halt(1);
            } finally {
                buffer.close();
                atlas.close();
                waiting = false;
            }
        }, 0);
    }

    // Tooltips as a client shows them: the item's own lines and every tooltip event's, the client-side ones included,
    // and the description Shift shows.
    private static void writeTooltips() throws IOException {
        JsonObject json = new JsonObject();
        Item.TooltipContext context = Item.TooltipContext.of(Minecraft.getInstance().level);
        for (Item item : BuiltInRegistries.ITEM) {
            Identifier id = BuiltInRegistries.ITEM.getKey(item);
            if (!RegistryExport.ours(id)) {
                continue;
            }
            JsonArray lines = new JsonArray();
            try {
                List<Component> components = new ItemStack(item).getTooltipLines(context, null, TooltipFlag.NORMAL);
                for (int i = 1; i < components.size(); i++) {
                    lines.add(components.get(i).getString());
                }
            } catch (RuntimeException e) {
                DocsExport.LOGGER.warn("No client tooltip for {}", id, e);
            }
            JsonObject entry = new JsonObject();
            entry.add("lines", lines);
            // The description the tooltip shows while Shift is held.
            Component info = ItemInfoTooltips.description(new ItemStack(item));
            entry.addProperty("shift", info == null ? null : info.getString());
            json.add(id.toString(), entry);
        }
        Json.write(Path.of(System.getProperty(DocsExport.PROPERTY)).resolve("tooltips-client.json"), json);
    }

    private static void finish(Path out) throws IOException {
        JsonObject report = new JsonObject();
        report.addProperty("size", SIZE);
        report.addProperty("rendered", rendered);
        report.add("failed", DocsExport.strings(failed));
        Json.write(out.resolve("icons.json"), report);
        Files.writeString(out.resolve("README.txt"), "Rendered by ./gradlew renderDocsIcons (src/docs/java/.../DocsIcons.java). Don't edit.\n",
                StandardCharsets.UTF_8);
        DocsExport.LOGGER.info("Rendered {} icons; {} failed", rendered, failed.size());
    }
}
