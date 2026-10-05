/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.zagdrath.encodedlogistics.blockentity.AccessPointBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.CableBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.ControlInterfaceBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.TerminalDeskBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.WirelessBridgeBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.WirelessPortBlockEntity;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.store.ElclStore;
import net.zagdrath.encodedlogistics.elcl.store.SystemData;
import net.zagdrath.encodedlogistics.midrange.MidrangeDevice;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.network.NodePos;
import net.zagdrath.encodedlogistics.part.CablePart;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.wireless.WirelessDevice;

// The devices scripts name (COMMANDS.md 5): CTLIF01 (Control Interfaces), ELDESK01 (Terminal Desks), rack devices by
// kind - FIREWALL01, ROUTER01, UPS01, SWITCH01, L3SWITCH01, CMPSRV01, MEMSRV01, FABSRV01, MONSRV01, NAS01, SAN01,
// RACKCON01, WLC01, TAPELIB01 - the parts on cables: INGRESS01, EGRESS01, TAP01, SENSOR01, COLLECTOR01,
// DEPLOYER01, P2P01, TERM01, FABTERM01, ENCODER01 - and wireless: AP01 (Access Points), WBRIDGE01 (Wireless Bridges),
// WINGRESS01 / WEGRESS01 (Wireless Ports: a port in every other way) - and the Midrange line: MIDRANGE01, KEYPUNCH01,
// CARDRDR01, PRT01.
//
// A device's name is stored with the device (a rack device's goes with its item; a desk's or Control Interface's with
// its block item) and given once, the first time it's on a network: its type plus the lowest number free there. It
// never changes after that unless a player renames it (RNMDEV, Work with Devices 2=Change). The system remembers which
// device holds each name, so one that arrives from another network keeps its name only if no device here has it, and
// otherwise gets the next free number. Names are given in the order the devices were always numbered in (rack by rack,
// each rack from its lowest unit up), so a network that had none yet (before names were stored) gets the same names it
// had when they were counted. Work with Devices' display order doesn't affect them.
public final class ElclDevices {
    // A named device: its name and type code, where it is, and the block entity or rack device behind it.
    public record Device(String name, String type, NodePos pos, @Nullable BlockEntity entity, @Nullable RackDevice rack, @Nullable CablePart part,
            boolean online) {}

    // A device that can carry a name, before naming: where its name is kept.
    private record Candidate(String type, NodePos pos, @Nullable BlockEntity entity, @Nullable RackDevice rack, @Nullable CablePart part, boolean online) {
        String stored() {
            if (rack != null) {
                return rack.deviceName();
            }
            if (part != null) {
                return part.deviceName();
            }
            if (entity instanceof WirelessDevice wireless) {
                return wireless.deviceName();
            }
            if (entity instanceof MidrangeDevice midrange) {
                return midrange.deviceName();
            }
            if (entity instanceof ControlInterfaceBlockEntity ci) {
                return ci.name();
            }
            return entity instanceof TerminalDeskBlockEntity desk ? desk.deviceName() : "";
        }

        void store(String name) {
            if (rack != null) {
                rack.setDeviceName(name);
            } else if (part != null) {
                part.setDeviceName(name);
            } else if (entity instanceof WirelessDevice wireless) {
                wireless.setDeviceName(name);
            } else if (entity instanceof MidrangeDevice midrange) {
                midrange.setDeviceName(name);
            } else if (entity instanceof ControlInterfaceBlockEntity ci) {
                ci.setName(name);
            } else if (entity instanceof TerminalDeskBlockEntity desk) {
                desk.setDeviceName(name);
            }
        }

        // Which device this is on its system: its block, and a rack device's unit.
        String identity() {
            return identity(pos, rack, part);
        }

        static String identity(NodePos pos, @Nullable RackDevice rack, @Nullable CablePart part) {
            return pos.dimension().identifier() + "@" + pos.pos().asLong() + (rack != null ? "/U" + rack.u() : "")
                    + (part != null ? "/" + part.side().getSerializedName() : "");
        }
    }

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

    // A cable part's type code.
    public static String code(CablePart part) {
        boolean wireless = part.host() instanceof WirelessPortBlockEntity;
        return switch (part.type()) {
            case INGRESS_PORT -> wireless ? "WINGRESS" : "INGRESS";
            case EGRESS_PORT -> wireless ? "WEGRESS" : "EGRESS";
            case INVENTORY_TAP -> "TAP";
            case THRESHOLD_SENSOR -> "SENSOR";
            case COLLECTOR_PLANE -> "COLLECTOR";
            case DEPLOYER_PLANE -> "DEPLOYER";
            case POINT_TO_POINT_LINK -> "P2P";
            case ACCESS_TERMINAL -> "TERM";
            case FABRICATION_TERMINAL -> "FABTERM";
            case SCHEMATIC_ENCODER -> "ENCODER";
        };
    }

    // The devices that take names, in the order they're named in: rack by rack, each rack's from its lowest unit up
    // (Work with Devices lists them top unit first).
    private static List<Candidate> candidates(MinecraftServer server, @Nullable NetworkRef network) {
        List<ControllerStructures.DeviceRow> rows = new ArrayList<>(ControllerStructures.deviceRows(server, network));
        for (int start = 0; start < rows.size(); start++) {
            int end = start;
            while (end < rows.size() && rows.get(end).rackDevice() != null) {
                end++;
            }
            if (end > start) {
                rows.subList(start, end).sort(Comparator.comparingInt(row -> row.rackDevice().u()));
                start = end - 1;
            }
        }
        List<Candidate> candidates = new ArrayList<>();
        Set<NodePos> partHosts = new HashSet<>();
        for (ControllerStructures.DeviceRow row : rows) {
            if (row.rackDevice() != null) {
                candidates.add(new Candidate(code(row.rackDevice()), row.pos(), null, row.rackDevice(), null, row.online()));
                continue;
            }
            BlockEntity entity = ControllerStructures.blockEntity(server, row.pos());
            if (entity instanceof AccessPointBlockEntity ap) {
                candidates.add(new Candidate("AP", row.pos(), ap, null, null, ap.isOnline()));
            } else if (entity instanceof WirelessBridgeBlockEntity bridge) {
                candidates.add(new Candidate("WBRIDGE", row.pos(), bridge, null, null, row.online()));
            } else if (entity instanceof MidrangeDevice midrange) {
                candidates.add(new Candidate(midrange.deviceType(), row.pos(), entity, null, null, midrange.isOnline()));
            } else if (entity instanceof ControlInterfaceBlockEntity ci) {
                candidates.add(new Candidate(ControlInterfaceBlockEntity.TYPE, row.pos(), ci, null, null, ci.isOnline()));
            } else if (entity instanceof TerminalDeskBlockEntity && row.type().equals("Terminal")) {
                candidates.add(new Candidate("DESK", row.pos(), entity, null, null, row.online()));
            } else if (entity instanceof CableBlockEntity cable && row.type().equals("Part") && partHosts.add(row.pos())) {
                // Each part on it, in side order.
                for (Direction side : Direction.values()) {
                    CablePart part = cable.part(side);
                    if (part != null) {
                        candidates.add(new Candidate(code(part), row.pos(), cable, null, part, part.isOnline()));
                    }
                }
            }
        }
        return candidates;
    }

    // The name prefix for a type code: ELDESK for desks, the code itself otherwise.
    private static String prefix(String type) {
        return type.equals("DESK") ? "ELDESK" : type;
    }

    // Every named device on the network, naming any that have no name yet (or one taken here).
    public static List<Device> list(MinecraftServer server, @Nullable NetworkRef network) {
        if (network == null) {
            return List.of();
        }
        List<Candidate> candidates = candidates(server, network);
        assign(ElclStore.get(server).system(network), candidates);
        List<Device> devices = new ArrayList<>(candidates.size());
        for (Candidate candidate : candidates) {
            devices.add(new Device(candidate.stored(), candidate.type(), candidate.pos(), candidate.entity(), candidate.rack(), candidate.part(),
                    candidate.online()));
        }
        return devices;
    }

    // Gives names: a device the system already knows under its name keeps it; one arriving with a name nobody here has
    // keeps it too; the rest get their type and the lowest number free.
    private static void assign(SystemData system, List<Candidate> candidates) {
        Map<String, String> holders = system.deviceNames;
        Map<String, String> before = new HashMap<>(holders);
        Map<String, String> carried = new HashMap<>();
        candidates.forEach(candidate -> carried.put(candidate.identity(), candidate.stored()));
        // A name is held only while the device there still carries it: one gone (or moved, or swapped with another)
        // frees it.
        holders.entrySet().removeIf(entry -> !entry.getKey().equals(carried.get(entry.getValue())));
        Set<String> taken = new HashSet<>();
        List<Candidate> unnamed = new ArrayList<>();
        // Known here under the name they carry.
        for (Candidate candidate : candidates) {
            String name = candidate.stored();
            if (!name.isEmpty() && candidate.identity().equals(holders.get(name)) && taken.add(name)) {
                continue;
            }
            unnamed.add(candidate);
        }
        // Arriving with a name: kept if it's free.
        List<Candidate> rest = new ArrayList<>();
        for (Candidate candidate : unnamed) {
            String name = candidate.stored();
            if (!name.isEmpty() && !holders.containsKey(name) && taken.add(name)) {
                holders.values().remove(candidate.identity());
                holders.put(name, candidate.identity());
                continue;
            }
            rest.add(candidate);
        }
        for (Candidate candidate : rest) {
            String name = free(prefix(candidate.type()), taken, holders);
            taken.add(name);
            holders.values().remove(candidate.identity());
            holders.put(name, candidate.identity());
            candidate.store(name);
        }
        if (!holders.equals(before)) {
            system.changed();
        }
    }

    private static String free(String prefix, Set<String> taken, Map<String, String> holders) {
        for (int number = 1;; number++) {
            String name = String.format(Locale.ROOT, "%s%02d", prefix, number);
            if (!taken.contains(name) && !holders.containsKey(name)) {
                return name;
            }
        }
    }

    public static @Nullable Device find(MinecraftServer server, @Nullable NetworkRef network, String name) {
        for (Device device : list(server, network)) {
            if (device.name().equalsIgnoreCase(name)) {
                return device;
            }
        }
        return null;
    }

    // The name of the device at a block (and rack unit), or "" for none; a part's (side given) by its side.
    public static String nameAt(List<Device> devices, NodePos pos, @Nullable RackDevice rack) {
        return nameAt(devices, pos, rack, null);
    }

    public static String nameAt(List<Device> devices, NodePos pos, @Nullable RackDevice rack, @Nullable Direction side) {
        for (Device device : devices) {
            if (device.pos().equals(pos) && device.rack() == rack && (device.part() == null ? side == null : device.part().side() == side)) {
                return device.name();
            }
        }
        return "";
    }

    // Renames a device (RNMDEV, Work with Devices 2=Change): ELC1301 when there's no such device, ELC0103 for a name
    // that isn't one, ELC1308 when another device on the system has it.
    public static void rename(MinecraftServer server, NetworkRef network, String name, String newName) throws ElclException {
        String wanted = newName.trim().toUpperCase(Locale.ROOT);
        if (!wanted.matches("[A-Z][A-Z0-9_@#$]{0,9}")) {
            throw new ElclException("ELC0103", newName, "NEWNAME");
        }
        Device device = find(server, network, name);
        if (device == null) {
            throw new ElclException("ELC1301", name.toUpperCase(Locale.ROOT));
        }
        if (device.name().equals(wanted)) {
            return;
        }
        SystemData system = ElclStore.get(server).system(network);
        String identity = Candidate.identity(device.pos(), device.rack(), device.part());
        String holder = system.deviceNames.get(wanted);
        if (holder != null && !holder.equals(identity) || find(server, network, wanted) != null) {
            throw new ElclException("ELC1308", wanted, system.sysvals.getOrDefault("SYSNAME", "the system"));
        }
        system.deviceNames.values().remove(identity);
        system.deviceNames.put(wanted, identity);
        system.changed();
        new Candidate(device.type(), device.pos(), device.entity(), device.rack(), device.part(), device.online()).store(wanted);
    }
}
