/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.zagdrath.encodedlogistics.blockentity.ControlInterfaceBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.rack.RackDevice;

// The devices scripts name (COMMANDS.md 5): type + number, numbered in the network's device order - CTLIF01 (a Control
// Interface keeps the name it was given), ELDESK01 (Terminal Desks), and rack devices by kind: FIREWALL01, ROUTER01,
// UPS01, SWITCH01, L3SWITCH01, CMPSRV01, MEMSRV01, FABSRV01, MONSRV01, NAS01, SAN01, RACKCON01, WLC01, TAPELIB01.
public final class ElclDevices {
    // A named device: its name and type code, where it is, and the block entity or rack device behind it.
    public record Device(String name, String type, NodePos pos, @Nullable BlockEntity entity, @Nullable RackDevice rack, boolean online) {}

    private ElclDevices() {}

    // A rack device kind's type code.
    public static String code(RackDevice device) {
        String id = device.type().id().getPath();
        return switch (id) {
            case "l2_switch_24", "l2_switch_48" -> "SWITCH";
            case "l3_switch" -> "L3SWITCH";
            case "compute_server" -> "CMPSRV";
            case "memory_server" -> "MEMSRV";
            case "fabrication_server" -> "FABSRV";
            case "monitoring_server" -> "MONSRV";
            case "rack_console" -> "RACKCON";
            case "wireless_controller" -> "WLC";
            case "tape_library_4u", "tape_library_6u" -> "TAPELIB";
            default -> id.toUpperCase(Locale.ROOT);
        };
    }

    public static List<Device> list(MinecraftServer server, @Nullable NetworkRef network) {
        List<Device> devices = new ArrayList<>();
        Map<String, Integer> numbers = new HashMap<>();
        for (ControllerStructures.DeviceRow row : ControllerStructures.deviceRows(server, network)) {
            if (row.rackDevice() != null) {
                String code = code(row.rackDevice());
                devices.add(new Device(numbered(code, numbers), code, row.pos(), null, row.rackDevice(), row.online()));
                continue;
            }
            BlockEntity entity = ControllerStructures.blockEntity(server, row.pos());
            if (entity instanceof ControlInterfaceBlockEntity ci && !ci.name().isEmpty()) {
                devices.add(new Device(ci.name(), ControlInterfaceBlockEntity.TYPE, row.pos(), ci, null, ci.isOnline()));
            } else if (entity instanceof TerminalDeskBlockEntity && row.type().equals("Terminal")) {
                devices.add(new Device(numbered("ELDESK", numbers), "DESK", row.pos(), entity, null, row.online()));
            }
        }
        return devices;
    }

    private static String numbered(String code, Map<String, Integer> numbers) {
        int number = numbers.merge(code, 1, Integer::sum);
        return String.format(Locale.ROOT, "%s%02d", code, number);
    }

    public static @Nullable Device find(MinecraftServer server, @Nullable NetworkRef network, String name) {
        for (Device device : list(server, network)) {
            if (device.name().equalsIgnoreCase(name)) {
                return device;
            }
        }
        return null;
    }

    // The lowest type + number no device on the network has yet.
    public static String freeName(MinecraftServer server, NetworkRef network, String type) {
        Set<String> taken = new HashSet<>();
        for (Device device : list(server, network)) {
            taken.add(device.name());
        }
        for (int number = 1;; number++) {
            String name = String.format(Locale.ROOT, "%s%02d", type, number);
            if (!taken.contains(name)) {
                return name;
            }
        }
    }
}
