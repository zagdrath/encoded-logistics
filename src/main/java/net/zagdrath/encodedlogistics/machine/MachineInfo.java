/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.machine;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

// A bridged machine as it is at one moment (MachineAccess#info): what it is, its status and the operation it's in (the
// outputs it'll make: its recipe), its energy, heat and tanks, its statistics and its settings. Free of the machine
// mod's own classes, so everything that shows a machine (Work with Machines, the popup, ELCL, graphs) loads without it.
// Settings are lower-case ids: redstone modes ("ignore", "high", "low", "pulse", "throttle"), sides ("top", "bottom",
// "left", "right", "back", "front") and side modes ("input", "output", "energy", ...).
public record MachineInfo(String type, Component name, boolean multiblock, boolean formed, BlockPos position, Optional<UUID> owner,
        State status, Component reason, OptionalDouble progress, OptionalInt ticksRemaining, List<ItemStack> outputs, Optional<Energy> energy,
        Optional<Heat> heat, List<Tank> tanks, Statistics statistics, Settings settings) {

    // The machine's status. UNKNOWN: one the machine mod added after this was written.
    public enum State {
        IDLE, RUNNING, NO_POWER, NO_INPUT, OUTPUT_BLOCKED, DISABLED, NOT_FORMED, FAULT, UNKNOWN;

        // As ELCL returns it: *IDLE, *RUNNING, *NOPOWER, *NOINPUT, *BLOCKED, *DISABLED, *NOTFORMED, *FAULT, *UNKNOWN.
        public String special() {
            return this == OUTPUT_BLOCKED ? "*BLOCKED" : "*" + name().replace("_", "");
        }

        public Component text() {
            return Component.translatable("gui.encodedlogistics.machine.state." + name().toLowerCase(Locale.ROOT));
        }
    }

    // FE: role "consumer", "generator" or "storage"; perTick the FE used, made or (storage) taken in last tick.
    public record Energy(String role, long stored, long capacity, long perTick) {
        public float fraction() {
            return capacity <= 0 ? 0 : Math.clamp((float) stored / capacity, 0, 1);
        }
    }

    // Heat: role "consumer" or "producer"; HU stored, °C now and at most, HU used or made last tick.
    public record Heat(String role, long stored, long capacity, int temperature, int maxTemperature, long perTick) {}

    // A tank: its role ("input", "output", "fuel", ...), what's in it (empty: nothing) and how much, in mB.
    public record Tank(String role, Component fluid, int amount, int capacity) {}

    // Totals since the machine was placed (or its multiblock formed), and its rolling rate over the last minute.
    public record Statistics(long operations, long itemsProduced, long itemsConsumed, long fluidProduced, long fluidConsumed, long uptimeTicks,
            long loadedTicks, double operationsPerMinute) {
        public static final Statistics NONE = new Statistics(0, 0, 0, 0, 0, 0, 0, 0);
    }

    // Its settings and what each may be set to: on/off; redstone mode (empty: none); side modes by side (empty: no side
    // configuration; a multiblock has ports instead); auto-eject.
    public record Settings(boolean enabled, Optional<String> redstoneMode, List<String> redstoneModes, Map<String, String> sides,
            List<String> sideModes, int ports, boolean autoEjectSupported, boolean autoEject) {}

    // What the current operation makes, as one line ("5 Bone Meal, 1 Gravel"), or "" when it isn't in one.
    public String recipe() {
        StringBuilder text = new StringBuilder();
        for (ItemStack stack : outputs) {
            if (!stack.isEmpty()) {
                text.append(text.isEmpty() ? "" : ", ").append(stack.getCount()).append(' ').append(stack.getHoverName().getString());
            }
        }
        return text.toString();
    }

    // Progress through the current operation as a whole percentage, or -1 when it isn't in one.
    public int percent() {
        return progress.isPresent() ? (int) Math.round(Math.clamp(progress.getAsDouble(), 0, 1) * 100) : -1;
    }
}
