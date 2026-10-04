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
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;

// Trigger events from the game (COMMANDS.md 9): what happened, on which network, its device and &DATA. Each event is
// handed to the listeners; the trigger service (WRKTRGEVT's entries, debounced) is one of them.
public final class ElclEvents {
    public record Event(NetworkRef network, String event, String device, String data) {}

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

    // *RSCHANGE: a Control Interface's input on a face changed. &DATA = "*NORTH 7".
    public static void redstoneChanged(MinecraftServer server, NetworkRef network, String device, Direction side, int level) {
        fire(server, new Event(network, "*RSCHANGE", device, side(side) + " " + level));
    }

    // A face as ELCL names it: *NORTH ... *UP, *DOWN.
    public static String side(Direction side) {
        return "*" + side.getSerializedName().toUpperCase(Locale.ROOT);
    }
}
