/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.screen;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Function;

import net.minecraft.server.MinecraftServer;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;

// The services behind the Terminal OS screens. Each starts as a stub (in memory, per system: gone when the server
// stops) and is replaced by the ELCL package that implements it when that lands (see docs/elcl/SCREEN_INVENTORY.md).
public final class ElclServices {
    private static LibraryService libraries = new StubLibraryService();
    private static JobService jobs = new StubJobService();
    private static MessageService messages = new StubMessageService();
    private static SpoolService spool = new StubSpoolService();
    private static SysvalService sysvals = new StubSysvalService();

    private ElclServices() {}

    public static LibraryService libraries() {
        return libraries;
    }

    public static JobService jobs() {
        return jobs;
    }

    public static MessageService messages() {
        return messages;
    }

    public static SpoolService spool() {
        return spool;
    }

    public static SysvalService sysvals() {
        return sysvals;
    }

    public static void setLibraries(LibraryService service) {
        libraries = service;
    }

    public static void setJobs(JobService service) {
        jobs = service;
    }

    public static void setMessages(MessageService service) {
        messages = service;
    }

    public static void setSpool(SpoolService service) {
        spool = service;
    }

    public static void setSysvals(SysvalService service) {
        sysvals = service;
    }

    // A stub's data per system, for as long as its server runs.
    static final class Store<T> {
        private final Map<MinecraftServer, Map<NetworkRef, T>> data = new WeakHashMap<>();
        private final Function<ElclSystem, T> create;

        Store(Function<ElclSystem, T> create) {
            this.create = create;
        }

        synchronized T of(ElclSystem system) {
            Map<NetworkRef, T> systems = data.computeIfAbsent(system.server(), server -> new HashMap<>());
            T state = systems.get(system.network());
            if (state == null) {
                state = create.apply(system);
                systems.put(system.network(), state);
            }
            return state;
        }
    }
}
