/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.AABB;
import net.zagdrath.encodedlogistics.elcl.exec.ElclDevices;
import net.zagdrath.encodedlogistics.elcl.exec.HardwareCommands;
import net.zagdrath.encodedlogistics.elcl.exec.RoutingCommands;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.chassis.Chassis;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisLayout;
import net.zagdrath.encodedlogistics.rack.device.OpticalAmplifierDevice;
import net.zagdrath.encodedlogistics.rack.device.OpticalTransportShelfDevice;
import net.zagdrath.encodedlogistics.rack.device.RouterDevice;

// The hardware screens' lines (docs/optical/layouts): WRKHW (every chassis on the network, a row for each bay with a
// part - F11 every bay - with 5=Display and 8=Locate) and DSPOPT (the multiplexers', amplifiers' and shelves' spans; F10
// their channels). The first line is the screen's row 2 (the network and its counts).
public final class HardwareScreens {
    public static final String OPTICAL_COLUMNS = "Span          Path      Length   Lanes  Loss  Quality  Protection",
            CHANNEL_COLUMNS = "Device    Ch  Network       Far end       Lanes  Up";

    private HardwareScreens() {}

    private static String network(TerminalContext context) {
        NetworkRef network = context.network();
        return network != null ? RouterDevice.networkName(context.server(), network) : "*NONE";
    }

    // --- WRKHW ---

    public static String hardwareHead(String network, int chassis, int cards, int faults) {
        return String.format(Locale.ROOT, "Network . :  %-8s  Chassis :  %d   Cards :  %d   Faults :  %d", network, chassis, cards, faults);
    }

    // A bay: its key (device and slot, not shown), then device, slot, part, status, detail.
    public static TerminalLine hardwareRow(String device, String slot, Component part, String status, Component detail) {
        return TerminalLine.builder().text(device + " " + slot).text(device).text(slot).text(part).text(status).text(detail).build();
    }

    static TerminalOutput hardware(TerminalContext context, String device, boolean every) {
        List<HardwareCommands.PartRow> rows = HardwareCommands.allParts(context.server(), context.network(), every);
        if (!device.isBlank() && !device.equalsIgnoreCase("*ALL")) {
            rows.removeIf(row -> !row.device().equalsIgnoreCase(device));
        }
        int cards = 0, faults = 0;
        for (HardwareCommands.PartRow row : HardwareCommands.allParts(context.server(), context.network(), false)) {
            if (row.installed()) {
                cards++;
            }
            if (row.code().equals("*FAILED")) {
                faults++;
            }
        }
        TerminalOutput out = new TerminalOutput();
        out.line(hardwareHead(network(context), HardwareCommands.chassisCount(context.server(), context.network()), cards, faults));
        for (HardwareCommands.PartRow row : rows) {
            out.line(hardwareRow(row.device(), row.slot(), row.part(), row.code(), row.detail()));
        }
        return out;
    }

    // 5=Display: a bay's part, as its popup has it.
    static TerminalOutput part(TerminalContext context, String deviceName, String slot) {
        ElclDevices.Device device = ElclDevices.find(context.server(), context.network(), deviceName);
        ChassisLayout.Bay bay = device != null && device.rack() instanceof Chassis chassis ? chassis.layout().byLabel(slot) : null;
        if (bay == null) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.hw.gone"));
        }
        Chassis chassis = (Chassis) device.rack();
        RackDeviceInfo info = Chassis.describe(chassis, bay);
        TerminalOutput out = new TerminalOutput();
        out.line(detail("device", device.name()));
        out.line(detail("slot", bay.label()));
        out.line(detail("part", info.name().getString()));
        out.line(detail("status", chassis.partState(bay).code() + "  " + info.statusText().getString()));
        for (RackDeviceInfo.InfoLine line : info.lines()) {
            out.line(TerminalLine.builder().left(line.label().getString() + " . . :", 28).text(line.value().getString()).build());
        }
        return out;
    }

    private static TerminalLine detail(String key, String value) {
        return TerminalLine.builder().left(Component.translatable("crt.encodedlogistics.hw." + key), 28).text(value).build();
    }

    // 8=Locate: the part's faceplate, outlined for ten seconds (as Work with Devices' 8=Locate does).
    static TerminalOutput locate(TerminalContext context, String deviceName, String slot) {
        ElclDevices.Device device = ElclDevices.find(context.server(), context.network(), deviceName);
        ChassisLayout.Bay bay = device != null && device.rack() instanceof Chassis chassis ? chassis.layout().byLabel(slot) : null;
        AABB box = bay != null ? HardwareCommands.partBox((Chassis) device.rack(), bay) : null;
        if (box == null || device.rack().rack() == null) {
            return TerminalOutput.message(Component.translatable("crt.encodedlogistics.hw.gone"));
        }
        TerminalOutput out = new TerminalOutput();
        out.line(String.format(Locale.ROOT, "%.4f %.4f %.4f %.4f %.4f %.4f %s", box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ,
                device.pos().dimension().identifier()));
        var pos = device.pos().pos();
        out.setMessage(Component.translatable("crt.encodedlogistics.msg.located", device.name() + " " + bay.label(),
                pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
        return out;
    }

    // --- DSPOPT ---

    public static String opticalHead(String network, int shelves, int muxes, int amplifiers) {
        return String.format(Locale.ROOT, "Network . :  %-8s  Shelves :  %d   Muxes :  %d   Amplifiers :  %d", network, shelves, muxes, amplifiers);
    }

    public static String opticalRow(String span, String path, String length, String lanes, String loss, int quality, String protection) {
        return String.format(Locale.ROOT, "%-14s%-10s%6s%8s%6s%9s  %s", span, path, length, lanes, loss, quality + "%", protection);
    }

    public static String channelRow(String device, int channel, String network, String far, String lanes, String up) {
        return String.format(Locale.ROOT, "%-10s%2d  %-14s%-14s%5s  %s", device, channel, network, far, lanes, up);
    }

    static TerminalOutput optical(TerminalContext context, boolean channels) {
        int shelves = 0, muxes = 0, amplifiers = 0;
        List<String> rows = new ArrayList<>();
        OpticalTransportShelfDevice switched = null;
        for (ElclDevices.Device device : ElclDevices.list(context.server(), context.network())) {
            List<HardwareCommands.SpanRow> spans = HardwareCommands.spans(device);
            if (spans == null) {
                continue;
            }
            if (device.rack() instanceof OpticalTransportShelfDevice shelf) {
                shelves++;
                if (shelf.switchedAt() >= 0 && (switched == null || shelf.switchedAt() > switched.switchedAt())) {
                    switched = shelf;
                }
            } else if (HardwareCommands.multiplexer(device) != null) {
                muxes++;
            } else if (device.rack() instanceof OpticalAmplifierDevice || device.entity() != null) {
                amplifiers++;
            }
            if (channels) {
                for (HardwareCommands.ChannelRow channel : HardwareCommands.channels(context.server(), device)) {
                    rows.add(channelRow(channel.device(), channel.number(), channel.network(), channel.far(), channel.up() ? Integer.toString(channel.lanes())
                            : "-", channel.up() ? "yes" : channel.network().equals("-") ? "-" : "no"));
                }
                continue;
            }
            for (HardwareCommands.SpanRow span : spans) {
                rows.add(opticalRow(span.name(), span.path(), span.up() && span.length() > 0 ? Integer.toString(span.length()) : "-",
                        span.up() ? Integer.toString(span.lanes()) : "-", span.up() ? Integer.toString(span.loss()) : "-", span.up() ? span.quality() : 0,
                        span.protection()));
            }
        }
        TerminalOutput out = new TerminalOutput();
        out.line(opticalHead(network(context), shelves, muxes, amplifiers));
        out.line(TerminalLine.of(Component.literal(channels ? CHANNEL_COLUMNS : OPTICAL_COLUMNS), TerminalLine.BRIGHT));
        rows.forEach(out::line);
        out.line(TerminalLine.blank());
        if (!channels) {
            out.line(switched != null ? protectionNote(RoutingCommands.clock(context.server(), switched.switchedAt()), switched.switchReason(),
                    switched.revertive()) : "No protection switches.");
        }
        return out;
    }

    public static String protectionNote(String clock, String reason, boolean revertive) {
        return String.format(Locale.ROOT, "Protection switch at %s (%s), revertive: %s", clock, reason, revertive ? "yes" : "no");
    }
}
