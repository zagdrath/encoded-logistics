/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.screen;

import net.neoforged.neoforge.common.ModConfigSpec;

// The terminals' toolbar settings (sort mode and direction, type tab, craftables shown, grid height, search mode), shared by every
// terminal and kept in the client config (encodedlogistics-client.toml), so they last across restarts. Each change is
// saved straight away. Before the config has loaded (it loads at startup) the defaults stand in.
public final class TerminalSettings {
    public enum SortMode {
        NAME, COUNT, MOD
    }

    // The grid's type tab: everything, one resource type, or the network's energy (a summary, not a grid).
    public enum TypeTab {
        ALL, ITEMS, FLUIDS, PRESSURIZED, ENERGY
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
    private static final ModConfigSpec.EnumValue<TypeTab> TYPE_TAB = BUILDER
            .comment("The grid's tab: everything, items, fluids, pressurized gases, or the energy summary.")
            .defineEnum("typeTab", TypeTab.ALL);
    private static final ModConfigSpec.BooleanValue SEARCH_SYNCED = BUILDER
            .comment("Keep the search field in step with JEI's search bar (needs JEI).")
            .define("searchSynced", false);

    // The phosphor setting's default: the system's PHOSPHOR value, until the player picks their own.
    public static final String SYSVAL = "*SYSVAL";

    private static final ModConfigSpec.ConfigValue<String> PHOSPHOR = BUILDER
            .comment("The Terminal Desk's screen colour: green, amber or white (screens/crt/phosphor.json), or *SYSVAL for the system's PHOSPHOR value.")
            .define("phosphor", SYSVAL);

    // Job toasts (JobToasts): whether to show them, for whose jobs, for which ends, how long a job must have run, and
    // the chime.
    public enum ToastJobs {
        MINE, ALL
    }

    private static final ModConfigSpec.BooleanValue TOASTS = BUILDER.pop().push("toasts")
            .comment("Show a toast when a crafting job finishes, fails or is cancelled.").define("enabled", true);
    private static final ModConfigSpec.EnumValue<ToastJobs> TOAST_JOBS = BUILDER
            .comment("Whose jobs: MINE (the ones you asked for) or ALL (every job on networks you can view).").defineEnum("jobs", ToastJobs.MINE);
    private static final ModConfigSpec.BooleanValue TOAST_COMPLETED = BUILDER.comment("Toast when a job completes.").define("completed", true);
    private static final ModConfigSpec.BooleanValue TOAST_FAILED = BUILDER.comment("Toast when a job fails.").define("failed", true);
    private static final ModConfigSpec.BooleanValue TOAST_CANCELLED = BUILDER.comment("Toast when a job is cancelled.").define("cancelled", false);
    private static final ModConfigSpec.IntValue TOAST_MIN_SECONDS = BUILDER
            .comment("Only toast for jobs that ran at least this many seconds (failures always toast).").defineInRange("minimumSeconds", 5, 0, 3_600);
    private static final ModConfigSpec.BooleanValue TOAST_SOUND = BUILDER.comment("A quiet chime with each toast (a lower tone for failures).")
            .define("sound", true);

    public static final ModConfigSpec SPEC = BUILDER.pop().build();

    private TerminalSettings() {}

    public static boolean toasts() {
        return SPEC.isLoaded() ? TOASTS.get() : TOASTS.getDefault();
    }

    public static ToastJobs toastJobs() {
        return SPEC.isLoaded() ? TOAST_JOBS.get() : TOAST_JOBS.getDefault();
    }

    public static boolean toastCompleted() {
        return SPEC.isLoaded() ? TOAST_COMPLETED.get() : TOAST_COMPLETED.getDefault();
    }

    public static boolean toastFailed() {
        return SPEC.isLoaded() ? TOAST_FAILED.get() : TOAST_FAILED.getDefault();
    }

    public static boolean toastCancelled() {
        return SPEC.isLoaded() ? TOAST_CANCELLED.get() : TOAST_CANCELLED.getDefault();
    }

    public static int toastMinimumSeconds() {
        return SPEC.isLoaded() ? TOAST_MIN_SECONDS.get() : TOAST_MIN_SECONDS.getDefault();
    }

    public static boolean toastSound() {
        return SPEC.isLoaded() ? TOAST_SOUND.get() : TOAST_SOUND.getDefault();
    }

    public static SortMode sortMode() {
        return SPEC.isLoaded() ? SORT_MODE.get() : SORT_MODE.getDefault();
    }

    public static void sortMode(SortMode mode) {
        set(SORT_MODE, mode);
    }

    public static TypeTab typeTab() {
        return SPEC.isLoaded() ? TYPE_TAB.get() : TYPE_TAB.getDefault();
    }

    public static void typeTab(TypeTab tab) {
        set(TYPE_TAB, tab);
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

    public static String phosphor() {
        return SPEC.isLoaded() ? PHOSPHOR.get() : PHOSPHOR.getDefault();
    }

    public static void phosphor(String phosphor) {
        set(PHOSPHOR, phosphor);
    }
}
