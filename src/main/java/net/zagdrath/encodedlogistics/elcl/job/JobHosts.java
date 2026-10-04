/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.job;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.device.DeviceSources;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.elcl.store.ElclConfig;
import net.zagdrath.encodedlogistics.rack.device.ComputeServerDevice;

// The job hosts on a system: every registered source's (the Compute Servers' is built in; the Midrange line adds its
// own). A host the system can't see (unloaded, removed) isn't listed.
public final class JobHosts {
    private static final DeviceSources<JobHost> SOURCES = new DeviceSources<>();

    static {
        SOURCES.register(JobHosts::computeServers);
    }

    private JobHosts() {}

    public static void register(DeviceSources.Source<JobHost> source) {
        SOURCES.register(source);
    }

    public static void unregister(DeviceSources.Source<JobHost> source) {
        SOURCES.unregister(source);
    }

    public static List<JobHost> all(ElclSystem system) {
        return SOURCES.all(system);
    }

    public static @Nullable JobHost find(ElclSystem system, String name) {
        for (JobHost host : all(system)) {
            if (host.name().equalsIgnoreCase(name)) {
                return host;
            }
        }
        return null;
    }

    // The network's Compute Servers, by their device names, computeServerJobs each.
    private static List<JobHost> computeServers(ElclSystem system) {
        List<JobHost> hosts = new ArrayList<>();
        int capacity = ElclConfig.computeServerJobs();
        if (capacity <= 0) {
            return hosts;
        }
        for (ElclDevices.Device device : ElclDevices.list(system.server(), system.network())) {
            if (device.rack() instanceof ComputeServerDevice server) {
                String name = device.name();
                hosts.add(new JobHost() {
                    @Override
                    public String name() {
                        return name;
                    }

                    @Override
                    public int capacity() {
                        return capacity;
                    }

                    @Override
                    public boolean resumes() {
                        return false;
                    }

                    @Override
                    public boolean online() {
                        return server.isOnline() && server.rack() != null && !server.rack().isRemoved();
                    }
                });
            }
        }
        return hosts;
    }
}
