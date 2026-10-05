/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.zagdrath.encodedlogistics.crafting.CraftingJob;
import net.zagdrath.encodedlogistics.crafting.JobEvents;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;

// Trigger events from the game (COMMANDS.md 9): what happened, on which network, its device or item and &DATA. Each
// event is handed to the listeners; the trigger service (WRKTRGEVT's entries, debounced) is one of them.
public final class ElclEvents {
    public record Event(NetworkRef network, String event, String device, String item, String data) {
        public Event(NetworkRef network, String event, String device, String data) {
            this(network, event, device, "", data);
        }
    }

    public interface Listener {
        void fired(MinecraftServer server, Event event);
    }

    private static final List<Listener> LISTENERS = new ArrayList<>();
    // The last events fired, newest last (the gametests read it).
    private static final List<Event> RECENT = new ArrayList<>();
    private static final int KEPT = 64;

    private ElclEvents() {}

    public static synchronized void listen(Listener listener) {
        LISTENERS.add(listener);
    }

    public static synchronized List<Event> recent() {
        return List.copyOf(RECENT);
    }

    public static synchronized void fire(MinecraftServer server, Event event) {
        RECENT.add(event);
        while (RECENT.size() > KEPT) {
            RECENT.removeFirst();
        }
        for (Listener listener : List.copyOf(LISTENERS)) {
            listener.fired(server, event);
        }
    }

    // *DSPTOUCH: a Display Panel screen was touched. &DATA = "DSP01 A 40 12" (its name, the region, canvas px from the
    // top left).
    public static void displayTouched(MinecraftServer server, NetworkRef network, String device, String region, int x, int y) {
        fire(server, new Event(network, "*DSPTOUCH", device, device + " " + region + " " + x + " " + y));
    }

    // *RSCHANGE: a Control Interface's input on a face changed. &DATA = "*NORTH 7".
    public static void redstoneChanged(MinecraftServer server, NetworkRef network, String device, Direction side, int level) {
        fire(server, new Event(network, "*RSCHANGE", device, side(side) + " " + level));
    }

    // *CRAFTEND: a crafting job ended. &DATA = "C0042 *DONE" (*DONE, *FAILED or *CANCELLED); its item for ITEM().
    public static void craftEnded(MinecraftServer server, NetworkRef network, CraftingJob job, JobEvents.Outcome outcome) {
        String status = switch (outcome) {
            case COMPLETED -> "*DONE";
            case FAILED -> "*FAILED";
            case CANCELLED -> "*CANCELLED";
        };
        String id = String.format(Locale.ROOT, "C%04d", ControllerStructures.jobNumber(server, network, job.id));
        fire(server, new Event(network, "*CRAFTEND", "", ElclItems.id(job.target.stack().getItem()), id + " " + status));
    }

    // *MCHIDLE, *MCHFAULT, *MCHNOPWR: a machine with a Small Wireless Bridge on went idle, faulted or ran short of power.
    // &DATA = "ARCCRU01 Too hot" (its name, then the machine's own words).
    public static void machineStatus(MinecraftServer server, NetworkRef network, String event, String device, String reason) {
        fire(server, new Event(network, event, device, (device + " " + reason).trim()));
    }

    // *MCHDONE: a bridged machine finished an operation. &DATA = "ARCCRU01 5 minecraft:bone_meal" (its name, then what it
    // made first); that item for ITEM().
    public static void machineDone(MinecraftServer server, NetworkRef network, String device, List<ItemStack> produced) {
        ItemStack first = produced.stream().filter(stack -> !stack.isEmpty()).findFirst().orElse(ItemStack.EMPTY);
        String item = first.isEmpty() ? "" : ElclItems.id(first.getItem());
        fire(server, new Event(network, "*MCHDONE", device, item, first.isEmpty() ? device : device + " " + first.getCount() + " " + item));
    }

    // A face as ELCL names it: *NORTH ... *UP, *DOWN.
    public static String side(Direction side) {
        return "*" + side.getSerializedName().toUpperCase(Locale.ROOT);
    }
}
