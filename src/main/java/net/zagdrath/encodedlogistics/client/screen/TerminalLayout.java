/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.zagdrath.encodedlogistics.EncodedLogistics;

// A terminal screen's layout, read from assets/encodedlogistics/screens/<name>.json and whatever it includes
// (screens/terminal/base_terminal.json for every terminal): later files override earlier ones key by key. Every number
// has the kit's default, so a missing or broken file still gives a usable screen.
public final class TerminalLayout {
    // The grid's height setting (the toolbar's height button, saved in TerminalSettings): a fixed number of rows
    // (rows.heights in the layout), or as many as fit the window. Shared by every terminal.
    public enum Height {
        SMALL, MEDIUM, TALL, FILL
    }

    public final int width, topHeight, rowHeight, bottomHeight;
    public final Identifier top, row, bottom;
    public final int minRows, maxRows, defaultRows;
    public final boolean fitToWindow;
    public final int smallRows, mediumRows, tallRows;
    public final int gridLeft, gridTopInRow, columns, cell;
    public final int scrollLeft, scrollTop, thumbWidth, thumbHeight;
    public final Identifier thumb, thumbHover, thumbDisabled;
    public final int searchLeft, searchTop, searchWidth, searchHeight, searchTextLeft, searchTextTop, searchMaxLength;
    public final Identifier searchSprite, searchSpriteFocused;
    public final int toolbarLeft, toolbarTop, toolbarSpacing;
    public final Identifier button, buttonHover, buttonPressed;
    // The panel tab behind the toolbar's buttons (its top part, then its bottom 3 rows, at the toolbar's height).
    public final Identifier tab;
    public final int tabWidth, tabHeight;
    // The toolbar's buttons, top to bottom: their ids (sort_mode, sort_direction, craftables) and icons by state.
    public final List<String> buttonIds;
    public final List<List<Identifier>> buttonIcons;
    public final Identifier slotHighlight;
    public final String titleKey;
    // titleMaxRight: the title is cut short (with "...") before it reaches the search field.
    public final int titleLeft, titleTop, titleMaxRight, inventoryLeft, inventoryTopInBottom;
    public final JsonObject features;
    // A section between the item rows and the bottom piece (which is then the lip-less bottom_texture): the Fabrication
    // Terminal's crafting grid (sections.crafting), the Schematic Encoder's crafting and processing sections. Every
    // section of a terminal has the same height; the screen picks which one shows. sectionHeight 0 when there's none.
    public record Section(Identifier texture, int height, Identifier bottom, JsonObject json) {}

    public final Map<String, Section> sections;
    public final int sectionHeight;
    // The first section (crafting when there is one) and its clear button.
    public final @Nullable Identifier section;
    public final int clearLeft, clearTop;
    public final Identifier clear, clearHover;

    private TerminalLayout(JsonObject json) {
        JsonObject pieces = object(json, "pieces");
        width = integer(json, "width", 195);
        topHeight = integer(object(pieces, "top"), "height", 19);
        rowHeight = integer(object(pieces, "row"), "height", 18);
        bottomHeight = integer(object(pieces, "bottom"), "height", 99);
        top = texture(string(object(pieces, "top"), "texture", "gui/terminal/top.png"));
        row = texture(string(object(pieces, "row"), "texture", "gui/terminal/row.png"));
        Identifier plainBottom = texture(string(object(pieces, "bottom"), "texture", "gui/terminal/bottom.png"));
        JsonObject rows = object(json, "rows");
        minRows = integer(rows, "min", 3);
        maxRows = integer(rows, "max", 12);
        defaultRows = integer(rows, "default", 6);
        fitToWindow = rows.has("fit_to_window") ? rows.get("fit_to_window").getAsBoolean() : true;
        JsonObject heights = object(rows, "heights");
        smallRows = integer(heights, "small", 3);
        mediumRows = integer(heights, "medium", 6);
        tallRows = integer(heights, "tall", 9);
        JsonObject grid = object(json, "grid");
        gridLeft = integer(grid, "left", 9);
        gridTopInRow = integer(grid, "top_in_row", 1);
        columns = integer(grid, "columns", 9);
        cell = integer(grid, "cell", 18);
        JsonObject scrollbar = object(json, "scrollbar");
        scrollLeft = integer(scrollbar, "left", 176);
        scrollTop = integer(scrollbar, "top", 19);
        JsonArray thumbSize = scrollbar.has("thumb_size") ? scrollbar.getAsJsonArray("thumb_size") : null;
        thumbWidth = thumbSize != null ? thumbSize.get(0).getAsInt() : 6;
        thumbHeight = thumbSize != null ? thumbSize.get(1).getAsInt() : 15;
        thumb = sprite(string(scrollbar, "thumb", "controller/scroll_thumb"));
        thumbHover = sprite(string(scrollbar, "thumb_hover", "controller/scroll_thumb_hover"));
        thumbDisabled = sprite(string(scrollbar, "thumb_disabled", "controller/scroll_thumb_disabled"));
        JsonObject search = object(json, "search");
        searchLeft = integer(search, "left", 119);
        searchTop = integer(search, "top", 4);
        searchWidth = integer(search, "width", 64);
        searchHeight = integer(search, "height", 12);
        searchTextLeft = integer(search, "text_left", 4);
        searchTextTop = integer(search, "text_top", 2);
        searchMaxLength = integer(search, "max_length", 64);
        searchSprite = sprite(string(search, "sprite", "terminal/search_field"));
        searchSpriteFocused = sprite(string(search, "sprite_focused", "terminal/search_field_focused"));
        JsonObject toolbar = object(json, "toolbar");
        toolbarLeft = integer(toolbar, "left", -21);
        toolbarTop = integer(toolbar, "top", 19);
        toolbarSpacing = integer(toolbar, "spacing", 20);
        button = sprite(string(toolbar, "button", "terminal/button"));
        buttonHover = sprite(string(toolbar, "button_hover", "terminal/button_hover"));
        buttonPressed = sprite(string(toolbar, "button_pressed", "terminal/button_pressed"));
        tab = sprite(string(toolbar, "tab", "terminal/toolbar"));
        JsonArray tabSize = toolbar.has("tab_size") ? toolbar.getAsJsonArray("tab_size") : null;
        tabWidth = tabSize != null ? tabSize.get(0).getAsInt() : 24;
        tabHeight = tabSize != null ? tabSize.get(1).getAsInt() : 128;
        List<String> ids = new ArrayList<>();
        List<List<Identifier>> icons = new ArrayList<>();
        if (toolbar.has("buttons")) {
            for (JsonElement entry : toolbar.getAsJsonArray("buttons")) {
                List<Identifier> states = new ArrayList<>();
                for (JsonElement icon : entry.getAsJsonObject().getAsJsonArray("icons")) {
                    states.add(sprite(icon.getAsString()));
                }
                ids.add(string(entry.getAsJsonObject(), "id", ""));
                icons.add(List.copyOf(states));
            }
        } else {
            ids.addAll(List.of("sort_mode", "sort_direction", "craftables", "height"));
            icons.add(List.of(sprite("terminal/icon_sort_name"), sprite("terminal/icon_sort_count"), sprite("terminal/icon_sort_mod")));
            icons.add(List.of(sprite("terminal/icon_dir_asc"), sprite("terminal/icon_dir_desc")));
            icons.add(List.of(sprite("terminal/icon_craftable_on"), sprite("terminal/icon_craftable_off")));
            icons.add(List.of(sprite("terminal/icon_height_small"), sprite("terminal/icon_height_medium"), sprite("terminal/icon_height_tall"),
                    sprite("terminal/icon_height_fill")));
        }
        buttonIds = List.copyOf(ids);
        buttonIcons = List.copyOf(icons);
        slotHighlight = sprite(string(json, "slot_highlight", "terminal/slot_highlight"));
        JsonObject text = object(json, "text");
        JsonObject title = object(text, "title");
        titleKey = string(title, "key", "gui.encodedlogistics.access_terminal");
        titleLeft = integer(title, "left", 8);
        titleTop = integer(title, "top", 5);
        titleMaxRight = integer(title, "max_right", searchLeft - 4);
        JsonObject inventory = object(text, "inventory");
        inventoryLeft = integer(inventory, "left", 9);
        inventoryTopInBottom = integer(inventory, "top_in_bottom", 6);
        features = object(json, "features");
        Map<String, Section> found = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : object(json, "sections").entrySet()) {
            JsonObject piece = read(entry.getValue().getAsString().replaceFirst("\\.json$", ""), 1);
            if (piece.has("texture")) {
                found.put(entry.getKey(), new Section(texture(string(piece, "texture", "gui/terminal/crafting.png")), integer(piece, "height", 76),
                        texture(string(piece, "bottom_texture", "gui/terminal/bottom_plain.png")), piece));
            }
        }
        sections = Map.copyOf(found);
        Section first = found.containsKey("crafting") ? found.get("crafting") : found.values().stream().findFirst().orElse(null);
        JsonObject crafting = first != null ? first.json() : new JsonObject();
        sectionHeight = first != null ? first.height() : 0;
        section = first != null ? first.texture() : null;
        bottom = first != null ? first.bottom() : plainBottom;
        JsonObject clearButton = object(crafting, "clear");
        clearLeft = integer(clearButton, "left", 84);
        clearTop = integer(clearButton, "top", 8);
        clear = sprite(string(clearButton, "sprite", "terminal/clear_grid"));
        clearHover = sprite(string(clearButton, "hover", "terminal/clear_grid_hover"));
    }

    // The layout of screens/<name>.json with its includes.
    public static TerminalLayout load(String name) {
        return new TerminalLayout(read(name, 0));
    }

    public @Nullable Section section(String name) {
        return sections.get(name);
    }

    public boolean feature(String name) {
        return features.has(name) && features.get(name).getAsBoolean();
    }

    // Grid rows for a window this many GUI pixels tall (with a little margin, and room for any section): the height
    // setting's rows, never more than fit, within min / max.
    public int rowsFor(int screenHeight) {
        int fit = defaultRows;
        if (fitToWindow) {
            // A margin, and room above for the type tabs (AbstractTerminalScreen.TAB_STRIP).
            int rows = (screenHeight - topHeight - sectionHeight - bottomHeight - 16 - AbstractTerminalScreen.TAB_STRIP) / rowHeight;
            fit = Math.max(minRows, Math.min(maxRows, rows));
        }
        int wanted = switch (TerminalSettings.height()) {
            case SMALL -> smallRows;
            case MEDIUM -> mediumRows;
            case TALL -> tallRows;
            case FILL -> fit;
        };
        return Math.max(minRows, Math.min(wanted, fit));
    }

    public int height(int rows) {
        return topHeight + rows * rowHeight + sectionHeight + bottomHeight;
    }

    // --- Reading ---

    private static JsonObject read(String name, int depth) {
        JsonObject merged = new JsonObject();
        Identifier id = EncodedLogistics.id("screens/" + name + ".json");
        Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
        if (resource.isEmpty() || depth > 4) {
            return merged;
        }
        JsonObject json;
        try (Reader reader = resource.get().openAsReader()) {
            json = JsonParser.parseReader(reader).getAsJsonObject();
        } catch (Exception e) {
            EncodedLogistics.LOGGER.warn("Couldn't read terminal layout {}", id, e);
            return merged;
        }
        if (json.has("includes")) {
            for (JsonElement include : json.getAsJsonArray("includes")) {
                merge(merged, read(include.getAsString().replaceFirst("\\.json$", ""), depth + 1));
            }
        }
        merge(merged, json);
        return merged;
    }

    private static void merge(JsonObject into, JsonObject from) {
        for (Map.Entry<String, JsonElement> entry : from.entrySet()) {
            JsonElement existing = into.get(entry.getKey());
            if (existing != null && existing.isJsonObject() && entry.getValue().isJsonObject()) {
                merge(existing.getAsJsonObject(), entry.getValue().getAsJsonObject());
            } else {
                into.add(entry.getKey(), entry.getValue().deepCopy());
            }
        }
    }

    private static JsonObject object(JsonObject json, String key) {
        return json.has(key) && json.get(key).isJsonObject() ? json.getAsJsonObject(key) : new JsonObject();
    }

    private static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }

    private static String string(JsonObject json, String key, String fallback) {
        return json.has(key) ? json.get(key).getAsString() : fallback;
    }

    private static Identifier texture(String path) {
        return EncodedLogistics.id("textures/" + path);
    }

    private static Identifier sprite(String path) {
        return EncodedLogistics.id(path);
    }
}
