/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.AABB;
import net.zagdrath.encodedlogistics.blockentity.OpticalAmplifierBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.OpticalMultiplexerBlockEntity;
import net.zagdrath.encodedlogistics.blockentity.RackBlockEntity;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.Invocation;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.optical.FiberTrace;
import net.zagdrath.encodedlogistics.optical.Multiplexer;
import net.zagdrath.encodedlogistics.optical.OpticalState;
import net.zagdrath.encodedlogistics.optical.SpanLoss;
import net.zagdrath.encodedlogistics.rack.RackDevice;
import net.zagdrath.encodedlogistics.rack.RackDeviceInfo;
import net.zagdrath.encodedlogistics.rack.RackGeometry;
import net.zagdrath.encodedlogistics.rack.chassis.Chassis;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisLayout;
import net.zagdrath.encodedlogistics.rack.chassis.ChassisPart;
import net.zagdrath.encodedlogistics.rack.device.OpticalAmplifierDevice;
import net.zagdrath.encodedlogistics.rack.device.OpticalMultiplexerDevice;
import net.zagdrath.encodedlogistics.rack.device.OpticalTransportShelfDevice;

// Hardware from ELCL (the optical handoff 8): a chassis's bays (RTVCHSSTS; WRKHW's rows), an optical device's spans and
// channels (RTVOPTSTS; DSPOPT's rows) and a Transport Shelf's protection switch by hand (CHGPROT). Devices are named by
// their device names on the caller's network: chassis CSW01, BLD01, OTS01; optical MUX01 (block or rack), AMP01, OTS01.
public final class HardwareCommands {
    private HardwareCommands() {}

    static void bind() {
        CommandRegistry.bind("RTVCHSSTS", call -> {
            ElclDevices.Device device = device(call);
            if (!(device.rack() instanceof Chassis chassis)) {
                throw new ElclException("ELC2701", device.name());
            }
            List<String> list = new ArrayList<>();
            for (PartRow row : parts(device.name(), chassis, true)) {
                ChassisPart part = chassis.part(row.bay());
                list.add(row.slot() + " " + (part != null ? part.name() : "*NONE") + " " + row.code());
            }
            RackDeviceInfo.Status status = chassis.device().status();
            String sts = status == RackDeviceInfo.Status.ONLINE ? "*ONLINE" : status == RackDeviceInfo.Status.WARNING ? "*DEGRADED" : "*FAULT";
            call.returns("RTNLIST", list);
            call.returns("RTNSTS", sts);
            if (status == RackDeviceInfo.Status.WARNING) {
                call.send(ElclMessage.of("ELC2702", device.name(), chassis.device().statusText().getString()));
            }
        });
        CommandRegistry.bind("RTVOPTSTS", call -> {
            ElclDevices.Device device = device(call);
            List<SpanRow> spans = spans(device);
            if (spans == null) {
                throw new ElclException("ELC2701", device.name());
            }
            String wanted = call.text("SPAN").trim().toUpperCase(Locale.ROOT);
            SpanRow span = wanted.isEmpty() || wanted.equals("*FIRST") ? spans.stream().filter(SpanRow::active).findFirst().orElse(spans.isEmpty() ? null
                    : spans.getFirst()) : spans.stream().filter(row -> row.name().equalsIgnoreCase(wanted) && row.active()).findFirst()
                            .orElse(spans.stream().filter(row -> row.name().equalsIgnoreCase(wanted)).findFirst().orElse(null));
            if (span == null) {
                throw new ElclException("ELC2703", wanted.isEmpty() ? "*FIRST" : wanted, device.name());
            }
            String channelText = call.text("CHANNEL").trim().toUpperCase(Locale.ROOT);
            int lanes = span.up() ? span.lanes() : 0;
            if (!channelText.isEmpty() && !channelText.equals("*ALL")) {
                int channel;
                try {
                    channel = Integer.parseInt(channelText);
                } catch (NumberFormatException e) {
                    throw new ElclException("ELC0103", channelText, "CHANNEL");
                }
                ChannelRow row = channels(OsCommands.context(call).server(), device).stream().filter(c -> c.number() == channel).findFirst().orElse(null);
                if (row == null || row.network().equals("-")) {
                    throw new ElclException("ELC2704", Integer.toString(channel));
                }
                lanes = row.up() ? row.lanes() : 0;
            }
            call.returns("RTNLANES", (long) lanes);
            call.returns("RTNLOSS", (long) (span.up() ? span.loss() : 0));
            call.returns("RTNQUAL", (long) (span.up() ? span.quality() : 0));
            call.returns("RTNPROT", span.protection().equals("-") ? "*NONE" : span.protection());
        });
        CommandRegistry.bind("CHGPROT", call -> {
            ElclDevices.Device device = device(call);
            if (!(device.rack() instanceof OpticalTransportShelfDevice shelf)) {
                throw new ElclException("ELC2701", device.name());
            }
            String span = call.text("SPAN").trim().toUpperCase(Locale.ROOT);
            if (!span.equalsIgnoreCase(shelf.spanName())) {
                throw new ElclException("ELC2703", span, device.name());
            }
            OpticalTransportShelfDevice.Path path = call.text("PATH").trim().equalsIgnoreCase("*PROTECT") ? OpticalTransportShelfDevice.Path.PROTECT
                    : OpticalTransportShelfDevice.Path.WORKING;
            if (!shelf.force(path)) {
                throw new ElclException("ELC2705", span);
            }
        });
    }

    private static ElclDevices.Device device(Invocation call) throws ElclException {
        ElclContext context = OsCommands.context(call);
        String name = call.text("DEV").trim().toUpperCase(Locale.ROOT);
        ElclDevices.Device device = ElclDevices.find(context.server(), context.network(), name);
        if (device == null) {
            throw new ElclException("ELC2701", name);
        }
        return device;
    }

    // --- Chassis bays (RTVCHSSTS, WRKHW) ---

    // A bay as WRKHW shows it: device, slot, part, status, detail.
    public record PartRow(String device, String slot, Component part, String code, Component detail, ChassisLayout.Bay bay, boolean installed) {}

    public static List<PartRow> parts(String device, Chassis chassis, boolean empty) {
        List<PartRow> rows = new ArrayList<>();
        for (ChassisLayout.Bay bay : chassis.layout().bays()) {
            ChassisPart part = chassis.part(bay);
            Chassis.PartState state = chassis.partState(bay);
            if (part == null && !empty && !state.code().equals("*FAILED")) {
                continue;
            }
            Component name = part != null ? Component.translatable("crt.encodedlogistics.hw.part." + part.id())
                    : state.code().equals("*FAILED") ? Component.translatable("crt.encodedlogistics.hw.part." + kindPart(bay.kind()))
                            : Component.translatable("crt.encodedlogistics.hw.part.empty");
            rows.add(new PartRow(device, bay.label(), name, state.code(), state.detail(), bay, part != null));
        }
        return rows;
    }

    // What usually sits in a kind of bay (a failed part's name, once it's gone).
    private static String kindPart(ChassisPart.Kind kind) {
        return switch (kind) {
            case SUPERVISOR -> ChassisPart.SUPERVISOR_CARD.id();
            case SHELF_CONTROLLER -> ChassisPart.CONTROLLER_CARD.id();
            case CHASSIS_PSU -> ChassisPart.CHASSIS_PSU.id();
            case BLADE_PSU -> ChassisPart.BLADE_PSU.id();
            case INTERCONNECT -> ChassisPart.INTERCONNECT_MODULE.id();
            case MANAGEMENT -> ChassisPart.MANAGEMENT_MODULE.id();
            default -> "empty";
        };
    }

    // Every chassis on the network with its bays (empty ones too, when asked).
    public static List<PartRow> allParts(MinecraftServer server, @Nullable NetworkRef network, boolean empty) {
        List<PartRow> rows = new ArrayList<>();
        for (ElclDevices.Device device : ElclDevices.list(server, network)) {
            if (device.rack() instanceof Chassis chassis) {
                rows.addAll(parts(device.name(), chassis, empty));
            }
        }
        return rows;
    }

    public static int chassisCount(MinecraftServer server, @Nullable NetworkRef network) {
        return (int) ElclDevices.list(server, network).stream().filter(device -> device.rack() instanceof Chassis).count();
    }

    // A part's faceplate in the world (8=Locate), or null when the chassis or bay isn't there.
    public static @Nullable AABB partBox(Chassis chassis, ChassisLayout.Bay bay) {
        RackDevice device = chassis.device();
        RackBlockEntity rack = device.rack();
        if (rack == null) {
            return null;
        }
        double top = RackGeometry.unitBottom(device.u()) + device.size();
        double y0 = top - (bay.y() + bay.h()) / 8.0, y1 = top - bay.y() / 8.0;
        AABB local = bay.rear() ? new AABB(1.55 + bay.x() / 8.0, y0, 29.25, 1.55 + (bay.x() + bay.w()) / 8.0, y1, 29.5)
                : new AABB(14.45 - (bay.x() + bay.w()) / 8.0, y0, 1.5, 14.45 - bay.x() / 8.0, y1, 1.75);
        return RackGeometry.toWorld(local, rack.getBlockPos(), rack.facing());
    }

    // --- Optical spans and channels (RTVOPTSTS, DSPOPT) ---

    // A span (a multiplexer's line, a shelf's working or protect path, an amplifier's): its device, name, path, whether
    // it's up, length, lanes delivered, lanes lost, quality, and protection state (*ACTIVE, *STANDBY, *FAILED, or "-").
    public record SpanRow(String device, String name, String path, boolean up, int length, int lanes, int loss, int quality, String protection,
            boolean active) {}

    // A device's spans, or null when it isn't optical.
    public static @Nullable List<SpanRow> spans(ElclDevices.Device device) {
        Multiplexer mux = multiplexer(device);
        if (mux != null) {
            FiberTrace.Result line = mux.trace();
            String name = device.name() + "-" + (mux.partnerName().isEmpty() ? "?" : mux.partnerName());
            return List.of(line != null ? new SpanRow(device.name(), name, "line", true, line.length(), line.lanes(), line.lost(), line.quality(), "-", true)
                    : new SpanRow(device.name(), name, "line", false, 0, 0, 0, 0, "-", true));
        }
        if (device.rack() instanceof OpticalTransportShelfDevice shelf) {
            List<SpanRow> rows = new ArrayList<>();
            for (OpticalTransportShelfDevice.Path path : OpticalTransportShelfDevice.Path.values()) {
                FiberTrace.Result trace = shelf.path(path);
                boolean active = shelf.activePath() == path;
                if (path == OpticalTransportShelfDevice.Path.PROTECT && !shelf.protection() && trace == null) {
                    continue;
                }
                String protection = !shelf.protection() ? "-" : trace == null ? "*FAILED" : active ? "*ACTIVE" : "*STANDBY";
                rows.add(new SpanRow(device.name(), shelf.spanName(), path.name().toLowerCase(Locale.ROOT), trace != null, trace != null ? trace.length() : 0,
                        shelf.lanes(trace), shelf.lost(trace), shelf.quality(trace), protection, active));
            }
            return rows;
        }
        if (device.entity() instanceof OpticalAmplifierBlockEntity amplifier) {
            boolean on = amplifier.state() == OpticalState.ON;
            return List.of(new SpanRow(device.name(), device.name(), "amplify", on, 0, on ? SpanLoss.fiberLanes() : 0, 0, on ? 100 : 0, "-", true));
        }
        if (device.rack() instanceof OpticalAmplifierDevice amplifier) {
            boolean on = amplifier.status() == RackDeviceInfo.Status.ONLINE;
            return List.of(new SpanRow(device.name(), device.name(), "patch", on, 0, on ? SpanLoss.fiberLanes() : 0, 0, on ? 100 : 0, "-", true));
        }
        return null;
    }

    public static @Nullable Multiplexer multiplexer(ElclDevices.Device device) {
        if (device.entity() instanceof OpticalMultiplexerBlockEntity block) {
            return block.multiplexer();
        }
        return device.rack() instanceof OpticalMultiplexerDevice rack ? rack.multiplexer() : null;
    }

    // A channel: its device, number, network here and there, whether it's up and its lanes.
    public record ChannelRow(String device, int number, String network, String far, boolean up, int lanes) {}

    public static List<ChannelRow> channels(MinecraftServer server, ElclDevices.Device device) {
        List<ChannelRow> rows = new ArrayList<>();
        Multiplexer mux = multiplexer(device);
        if (mux != null) {
            for (int i = 0; i < mux.channelCount(); i++) {
                rows.add(new ChannelRow(device.name(), i + 1, mux.channel(i) != null ? Multiplexer.networkName(server, mux.channel(i)) : "-",
                        mux.partnerName().isEmpty() ? "-" : mux.partnerName(), mux.up(i), mux.up(i) ? mux.channelLanes() : 0));
            }
        } else if (device.rack() instanceof OpticalTransportShelfDevice shelf) {
            for (OpticalTransportShelfDevice.Channel channel : shelf.channels()) {
                rows.add(new ChannelRow(device.name(), channel.number(), channel.network() != null ? Multiplexer.networkName(server, channel.network()) : "-",
                        channel.far() != null ? Multiplexer.networkName(server, channel.far()) : "-", channel.up(),
                        channel.up() ? shelf.lanes(shelf.activeTrace()) : 0));
            }
        }
        return rows;
    }
}
