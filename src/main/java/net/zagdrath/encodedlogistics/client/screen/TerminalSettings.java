/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import net.neoforged.neoforge.common.ModConfigSpec;

// The terminals' toolbar settings (sort mode and direction, craftables shown, grid height, search mode), shared by every
// terminal and kept in the client config (encodedlogistics-client.toml), so they last across restarts. Each change is
// saved straight away. Before the config has loaded (it loads at startup) the defaults stand in.
public final class TerminalSettings {
    public enum SortMode {
        NAME, COUNT, MOD
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder().push("terminal");

    private static final ModConfigSpec.EnumValue<SortMode> SORT_MODE = BUILDER.comment("Sort the grid by name, count or mod.")
            .defineEnum("sortMode", SortMode.NAME);
    private static final ModConfigSpec.BooleanValue DESCENDING = BUILDER.comment("Sort in descending order.").define("descending", false);
    private static final ModConfigSpec.BooleanValue CRAFTABLES_ALWAYS = BUILDER
            .comment("Show what the network can craft but has none of always (true) or only while searching (false).")
            .define("craftablesAlways", true);
    private static final ModConfigSpec.EnumValue<TerminalLayout.Height> HEIGHT = BUILDER
            .comment("The grid's height: a fixed size, or as many rows as fit the window.")
            .defineEnum("height", TerminalLayout.Height.FILL);
    private static final ModConfigSpec.BooleanValue SEARCH_SYNCED = BUILDER
            .comment("Keep the search field in step with JEI's search bar (needs JEI).")
            .define("searchSynced", false);

    public static final ModConfigSpec SPEC = BUILDER.pop().build();

    private TerminalSettings() {}

    public static SortMode sortMode() {
        return SPEC.isLoaded() ? SORT_MODE.get() : SORT_MODE.getDefault();
    }

    public static void sortMode(SortMode mode) {
        set(SORT_MODE, mode);
    }

    public static boolean descending() {
        return SPEC.isLoaded() ? DESCENDING.get() : DESCENDING.getDefault();
    }

    public static void descending(boolean descending) {
        set(DESCENDING, descending);
    }

    public static boolean craftablesAlways() {
        return SPEC.isLoaded() ? CRAFTABLES_ALWAYS.get() : CRAFTABLES_ALWAYS.getDefault();
    }

    public static void craftablesAlways(boolean always) {
        set(CRAFTABLES_ALWAYS, always);
    }

    public static TerminalLayout.Height height() {
        return SPEC.isLoaded() ? HEIGHT.get() : HEIGHT.getDefault();
    }

    public static void height(TerminalLayout.Height height) {
        set(HEIGHT, height);
    }

    public static boolean searchSynced() {
        return SPEC.isLoaded() ? SEARCH_SYNCED.get() : SEARCH_SYNCED.getDefault();
    }

    public static void searchSynced(boolean synced) {
        set(SEARCH_SYNCED, synced);
    }

    private static <T> void set(ModConfigSpec.ConfigValue<T> value, T to) {
        if (SPEC.isLoaded()) {
            value.set(to);
            value.save();
        }
    }
}
