/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.job;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.exec.ElclEvents;
import net.zagdrath.encodedlogistics.elcl.exec.ElclItems;
import net.zagdrath.encodedlogistics.elcl.exec.ModCommands;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.screen.JobService;
import net.zagdrath.encodedlogistics.elcl.store.ElclStore;
import net.zagdrath.encodedlogistics.elcl.store.JobData;
import net.zagdrath.encodedlogistics.elcl.store.StoredLibraryService;
import net.zagdrath.encodedlogistics.elcl.store.SystemData;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.device.UpsDevice;
import net.zagdrath.encodedlogistics.storage.NetworkStorage;

// Trigger events (COMMANDS.md 9): a trigger runs its program as a batch job, as the user who added it, with &EVENT and
// &DATA, whenever its event happens on a loaded network - edge-triggered (once per crossing) and debounced (at least a
// second between two firings of one trigger; a crossing in that second fires when it's over, if it still holds, and
// an event in it fires then once, with the last &DATA).
//  *ITMBELOW / *ITMABOVE  ITEM's count (hot and cold) drops below / rises above VALUE    &DATA: item ID and count
//  *STGFULL               storage use reaches VALUE % (100 without one)                 &DATA: % used
//  *PWRUPS / *PWRRESTORED the network goes onto UPS power / back off it                 &DATA: UPS charge %
//  *DEVFAULT / *DEVONLINE / *DEVOFFLINE  DEV (or *ANY) changes to that status          &DATA: device name
//                         (a disabled part counts as offline)
//  *CRAFTEND              a crafting job of ITEM (or *ANY) ends                         &DATA: C0042 *DONE
//  *RSCHANGE              a Control Interface's input on DEV changes (VALUE: one side)  &DATA: *NORTH 7
// The first four are looked at every half second; device changes, ended crafts and redstone come as events
// (ElclEvents). Held triggers don't fire. A job that can't be submitted (no job host: ELC0301) is reported to the
// trigger's user's message queue.
public final class Triggers {
    public static final long DEBOUNCE_MS = 1_000;
    private static final int POLL_TICKS = 10;
    // The last status of each named device per network (not saved: the first look after a restart only records them).
    private static final Map<MinecraftServer, Map<NetworkRef, Map<String, String>>> DEVICES = new WeakHashMap<>();
    private static int ticks;

    private Triggers() {}

    // --- Polled conditions ---

    public static void tick(MinecraftServer server) {
        if (++ticks % POLL_TICKS != 0) {
            return;
        }
        for (Map.Entry<NetworkRef, SystemData> entry : ElclStore.get(server).systems().entrySet()) {
            JobData data = entry.getValue().jobs;
            if (data.triggers.isEmpty() || !ControllerStructures.loaded(server, entry.getKey())) {
                continue;
            }
            ElclSystem system = new ElclSystem(server, entry.getKey());
            boolean devices = false;
            for (JobData.Trigger trigger : List.copyOf(data.triggers.values())) {
                String event = trigger.trigger.event();
                devices |= event.startsWith("*DEV");
                if (trigger.pending != null && (!trigger.trigger.status().equals("*ACTIVE") || fire(system, trigger, trigger.pending))) {
                    // Fired now its debounce is over (or held meanwhile: dropped).
                    trigger.pending = null;
                    entry.getValue().changed();
                }
                Condition condition = condition(system, trigger.trigger);
                if (condition == null) {
                    continue;
                }
                if (!trigger.primed) {
                    // Its first look: what holds now isn't a crossing.
                    trigger.primed = true;
                    trigger.wasTrue = condition.holds();
                    entry.getValue().changed();
                } else if (!condition.holds()) {
                    if (trigger.wasTrue) {
                        trigger.wasTrue = false;
                        entry.getValue().changed();
                    }
                } else if (!trigger.wasTrue && fire(system, trigger, condition.data())) {
                    trigger.wasTrue = true;
                }
            }
            if (devices) {
                devices(system);
            }
        }
    }

    private record Condition(boolean holds, String data) {}

    // Whether a polled trigger's condition holds now, and its &DATA; null for the event-driven ones.
    private static @Nullable Condition condition(ElclSystem system, JobService.Trigger trigger) {
        switch (trigger.event()) {
            case "*ITMBELOW", "*ITMABOVE" -> {
                NetworkStorage storage = ControllerStructures.sharedStorageOf(system.server(), system.network(), false);
                Item item;
                try {
                    item = ElclItems.resolve(trigger.item());
                } catch (ElclException e) {
                    return null;
                }
                if (storage == null) {
                    return null;
                }
                long count = ElclItems.count(storage, item, "*ALL"), value = number(trigger.value(), 0);
                boolean holds = trigger.event().equals("*ITMBELOW") ? count < value : count > value;
                return new Condition(holds, ElclItems.id(item) + " " + count);
            }
            case "*STGFULL" -> {
                NetworkStorage storage = ControllerStructures.sharedStorageOf(system.server(), system.network(), false);
                if (storage == null) {
                    return null;
                }
                long[] hot = storage.hotBytes();
                long used = hot[0] + StoredLibraryService.storageBytes(system);
                long percent = hot[1] <= 0 ? 0 : used * 100 / hot[1];
                return new Condition(hot[1] > 0 && percent >= number(trigger.value(), 100), Long.toString(percent));
            }
            case "*PWRUPS", "*PWRRESTORED" -> {
                boolean battery = false;
                int charge = 0, upses = 0;
                for (RackDevice device : ControllerStructures.rackDevicesServing(system.server(), system.network())) {
                    if (device instanceof UpsDevice ups && ups.isOnline()) {
                        battery |= ups.onBattery();
                        charge += ups.percent();
                        upses++;
                    }
                }
                String data = Integer.toString(upses == 0 ? 0 : charge / upses);
                return new Condition(trigger.event().equals("*PWRUPS") == battery && upses > 0, data);
            }
            default -> {
                return null;
            }
        }
    }

    private static long number(String text, long fallback) {
        try {
            return new BigDecimal(text.trim()).longValue();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    // Device status changes, as events: *DEVFAULT, *DEVONLINE, *DEVOFFLINE (a disabled part: offline).
    private static void devices(ElclSystem system) {
        Map<String, String> last = DEVICES.computeIfAbsent(system.server(), s -> new HashMap<>()).computeIfAbsent(system.network(), n -> new HashMap<>());
        boolean first = last.isEmpty();
        for (ElclDevices.Device device : ElclDevices.list(system.server(), system.network())) {
            String status = ModCommands.status(device);
            String before = last.put(device.name(), status);
            if (first || before == null || before.equals(status)) {
                continue;
            }
            String event = switch (status) {
                case "*FAULT" -> "*DEVFAULT";
                case "*ONLINE" -> "*DEVONLINE";
                default -> before.equals("*OFFLINE") || before.equals("*DISABLED") ? null : "*DEVOFFLINE";
            };
            if (event != null) {
                ElclEvents.fire(system.server(), new ElclEvents.Event(system.network(), event, device.name(), device.name()));
            }
        }
        if (first) {
            last.put("", "");
        }
    }

    // --- Events (devices, crafts, redstone) ---

    public static void fired(MinecraftServer server, ElclEvents.Event event) {
        SystemData data = ElclStore.get(server).systems().get(event.network());
        if (data == null || data.jobs.triggers.isEmpty()) {
            return;
        }
        ElclSystem system = new ElclSystem(server, event.network());
        for (JobData.Trigger trigger : List.copyOf(data.jobs.triggers.values())) {
            if (matches(trigger.trigger, event) && !fire(system, trigger, event.data()) && trigger.trigger.status().equals("*ACTIVE")) {
                // Within the debounce: it fires when that's over.
                trigger.pending = event.data();
                data.changed();
            }
        }
    }

    private static boolean matches(JobService.Trigger trigger, ElclEvents.Event event) {
        if (!trigger.event().equals(event.event())) {
            return false;
        }
        String device = trigger.device().trim().toUpperCase(Locale.ROOT), item = trigger.item().trim();
        return switch (event.event()) {
            case "*DEVFAULT", "*DEVONLINE", "*DEVOFFLINE" -> device.isEmpty() || device.equals("*ANY") || device.equals(event.device());
            case "*RSCHANGE" -> (device.isEmpty() || device.equals("*ANY") || device.equals(event.device()))
                    && (trigger.value().isBlank() || trigger.value().equalsIgnoreCase("*NONE") || event.data().startsWith(trigger.value().trim().toUpperCase(Locale.ROOT) + " "));
            case "*CRAFTEND" -> item.isEmpty() || item.equalsIgnoreCase("*ANY") || sameItem(item, event.item());
            default -> false;
        };
    }

    private static boolean sameItem(String a, String b) {
        try {
            return ElclItems.resolve(a) == ElclItems.resolve(b);
        } catch (ElclException e) {
            return a.equalsIgnoreCase(b);
        }
    }

    // --- Firing ---

    // Submits the trigger's program as a batch job (as its user): CALL PGM(...) PARM('*EVENT' '&DATA'). False when it
    // didn't fire (held, or within the debounce).
    static boolean fire(ElclSystem system, JobData.Trigger trigger, String data) {
        JobService.Trigger t = trigger.trigger;
        long now = RealTime.millis();
        if (!t.status().equals("*ACTIVE") || now - trigger.lastFired < DEBOUNCE_MS) {
            return false;
        }
        trigger.lastFired = now;
        ElclStore.of(system).changed();
        String command = "CALL PGM(" + t.program() + ") PARM(" + quote(t.event()) + " " + quote(data) + ")";
        try {
            ElclServices.jobs().submit(system, t.user(), trigger.player, command, t.name(), "*ANY", false);
        } catch (ElclException e) {
            report(system, t.user(), "Trigger " + t.name(), e.elclMessage());
        }
        return true;
    }

    static String quote(String text) {
        return "'" + text.replace("'", "''") + "'";
    }

    // Why a trigger or schedule entry couldn't submit its job, to its user's message queue.
    static void report(ElclSystem system, String user, String what, ElclMessage message) {
        ElclServices.messages().send(system, "QSYS", user, message.id(), message.severity(), what + ": " + message.text());
    }
}
