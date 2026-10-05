/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.wireless;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.zagdrath.encodedlogistics.machine.MachineBridges;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.device.WirelessControllerDevice;

// Wireless's lookups: a client's controller, a network's controllers and online Access Points, and what stops a client
// working (Problem) - with the status and blockstate each problem shows.
public final class Wireless {
    // Why a Wireless Bridge, Port or Small Wireless Bridge isn't on its controller's network (NONE: it is).
    public enum Problem {
        NONE, NOT_LINKED, NO_CONTROLLER, NO_ACCESS_POINTS, OVER_CAPACITY;

        public Component text() {
            return Component.translatable("hud.encodedlogistics.wireless.problem." + name().toLowerCase(Locale.ROOT));
        }

        public RackDeviceInfo.Status status() {
            return switch (this) {
                case NONE -> RackDeviceInfo.Status.ONLINE;
                case NOT_LINKED, NO_CONTROLLER -> RackDeviceInfo.Status.WARNING;
                case NO_ACCESS_POINTS, OVER_CAPACITY -> RackDeviceInfo.Status.FAULT;
            };
        }

        public WirelessState state() {
            return switch (this) {
                case NONE -> WirelessState.ONLINE;
                case NOT_LINKED, NO_CONTROLLER -> WirelessState.LINKING;
                case NO_ACCESS_POINTS, OVER_CAPACITY -> WirelessState.FAULT;
            };
        }
    }

    private Wireless() {}

    // The Wireless Controller a client is linked to, while it's loaded on its network.
    public static @Nullable WirelessControllerDevice controller(MinecraftServer server, @Nullable WirelessLink link) {
        if (link == null) {
            return null;
        }
        for (RackDevice device : ControllerStructures.rackDevicesServing(server, link.network())) {
            if (device instanceof WirelessControllerDevice controller && controller.id().equals(link.controller())) {
                return controller;
            }
        }
        return null;
    }

    // Whether a network has a Wireless Controller that's online.
    public static boolean hasController(MinecraftServer server, @Nullable NetworkRef network) {
        for (RackDevice device : ControllerStructures.rackDevicesServing(server, network)) {
            if (device instanceof WirelessControllerDevice controller && controller.isOnline()) {
                return true;
            }
        }
        return false;
    }

    // A network's first online Wireless Controller, or null.
    public static @Nullable WirelessControllerDevice firstController(MinecraftServer server, @Nullable NetworkRef network) {
        for (RackDevice device : ControllerStructures.rackDevicesServing(server, network)) {
            if (device instanceof WirelessControllerDevice controller && controller.isOnline()) {
                return controller;
            }
        }
        return null;
    }

    // The wireless client at a block: a Wireless Bridge or Port, or the Small Wireless Bridge on a machine there (on the
    // server; a client-side level knows only the block entities). Null for none.
    public static @Nullable WirelessClient clientAt(Level level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof WirelessClient client) {
            return client;
        }
        return level instanceof ServerLevel serverLevel ? MachineBridges.at(serverLevel, pos) : null;
    }

    // The same in any dimension, if it's loaded.
    public static @Nullable WirelessClient clientAt(MinecraftServer server, NodePos pos) {
        ServerLevel level = server.getLevel(pos.dimension());
        return level != null && level.isLoaded(pos.pos()) ? clientAt(level, pos.pos()) : null;
    }

    // The wireless device at a node (an Access Point, Wireless Bridge or Port, or a bridged machine), if it's loaded.
    public static @Nullable WirelessDevice deviceAt(MinecraftServer server, NodePos pos) {
        if (ControllerStructures.blockEntity(server, pos) instanceof WirelessDevice device) {
            return device;
        }
        ServerLevel level = server.getLevel(pos.dimension());
        return level != null && level.isLoaded(pos.pos()) ? MachineBridges.at(level, pos.pos()) : null;
    }

    // What stops a client working: not linked, its controller gone or offline, no Access Points, or no slot for it.
    public static Problem problem(MinecraftServer server, WirelessClient client) {
        WirelessLink link = client.link();
        if (link == null) {
            return Problem.NOT_LINKED;
        }
        WirelessControllerDevice controller = controller(server, link);
        if (controller == null || !controller.isOnline()) {
            return Problem.NO_CONTROLLER;
        }
        if (!controller.lists(client.self())) {
            return Problem.NOT_LINKED;
        }
        if (controller.accessPointsOnline() <= 0) {
            return Problem.NO_ACCESS_POINTS;
        }
        return controller.admits(client.self()) ? Problem.NONE : Problem.OVER_CAPACITY;
    }

    // A client's regular check: its controller (if loaded) still lists it - else it's unlinked - and its rack hasn't moved
    // (the controller taken to another rack: the link follows it).
    public static void check(MinecraftServer server, WirelessClient client) {
        WirelessLink link = client.link();
        WirelessControllerDevice controller = controller(server, link);
        if (link == null || controller == null) {
            return;
        }
        if (!controller.lists(client.self())) {
            client.setLink(null);
            return;
        }
        GlobalPos rack = controller.rackPos();
        NetworkRef network = controller.network();
        if (rack != null && network != null && (!rack.equals(link.rack()) || !network.equals(link.network()))) {
            client.setLink(new WirelessLink(link.controller(), network, rack));
        }
    }

    // A client broken: its controller (if loaded) forgets it.
    public static void removed(MinecraftServer server, WirelessClient client) {
        WirelessControllerDevice controller = controller(server, client.link());
        if (controller != null) {
            controller.forget(client.self());
        }
    }

    // A controller's name as wireless blocks show it: its device name (WLC01), or its kind.
    public static String name(WirelessControllerDevice controller) {
        return controller.deviceName().isEmpty() ? controller.name().getString() : controller.deviceName();
    }
}
