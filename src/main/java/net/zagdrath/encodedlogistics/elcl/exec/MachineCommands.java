/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.zagdrath.encodedlogistics.blockentity.GatewayBlockEntity;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.Invocation;
import net.zagdrath.encodedlogistics.machine.MachineAccess;
import net.zagdrath.encodedlogistics.machine.MachineBridge;
import net.zagdrath.encodedlogistics.machine.MachineBridges;
import net.zagdrath.encodedlogistics.machine.MachineInfo;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;

// The machines with a Small Wireless Bridge on (Arcforge's, through MachineAccess) as ELCL and Work with Machines see
// them: by their device names (ARCCRU01). RTVMCHSTS (status, the machine's reason, progress, FE, what it's making),
// RTVMCHSTAT (its statistics), RTVMCHLST (names, by type and status), CHGMCHSTS (*ENABLE / *DISABLE: Arcforge's remote
// switch) and CHGMCHCFG (redstone mode, a side's mode, auto-eject, power from network, the Gateway that feeds it). A
// device that isn't a bridged machine is ELC1318; one whose machine is gone or unloaded, ELC1302. Settings go through
// the machine's own rules: one it lacks is ELC1320, one it refuses ELC1321, an unformed multiblock ELC1319. CHGMCHCFG
// applies its settings in the order above and stops at the first refused one.
public final class MachineCommands {
    // A bridged machine: its device name, its bridge and the machine now (null: gone or unloaded).
    public record Machine(String name, MachineBridge bridge, @Nullable MachineInfo info) {
        // *OFFLINE while it isn't on its network (or its machine is missing), else the machine's: *IDLE, *RUNNING ...
        public String status() {
            return !bridge.isOnline() || info == null ? "*OFFLINE" : info.status().special();
        }
    }

    private MachineCommands() {}

    // The network's bridged machines, in Work with Devices' order.
    public static List<Machine> list(MinecraftServer server, @Nullable NetworkRef network) {
        List<Machine> machines = new ArrayList<>();
        for (ElclDevices.Device device : ElclDevices.list(server, network)) {
            MachineBridge bridge = device.rack() == null && device.part() == null ? MachineBridges.at(server, device.pos()) : null;
            if (bridge != null) {
                machines.add(new Machine(device.name(), bridge, bridge.info(server)));
            }
        }
        return machines;
    }

    // ELC1301 for no such device; ELC1318 for one that isn't a bridged machine (or with the integration off).
    public static Machine find(MinecraftServer server, @Nullable NetworkRef network, String name) throws ElclException {
        String wanted = name.trim().toUpperCase(Locale.ROOT);
        ElclDevices.Device device = ElclDevices.find(server, network, wanted);
        if (device == null) {
            throw new ElclException("ELC1301", wanted);
        }
        MachineBridge bridge = MachineBridges.access() != null && device.rack() == null && device.part() == null ? MachineBridges.at(server, device.pos())
                : null;
        if (bridge == null) {
            throw new ElclException("ELC1318", device.name());
        }
        return new Machine(device.name(), bridge, bridge.info(server));
    }

    private static ElclContext context(Invocation call) throws ElclException {
        ElclContext context = OsCommands.context(call);
        if (context.network() == null) {
            throw new ElclException("ELC1302", "*NETWORK");
        }
        return context;
    }

    // The machine named by MCH, there to change (ELC1302 when it's gone or unloaded).
    private static Machine present(ElclContext context, Invocation call) throws ElclException {
        Machine machine = find(context.server(), context.network(), call.text("MCH"));
        if (machine.info() == null || context.server().getLevel(machine.bridge().dimension()) == null) {
            throw new ElclException("ELC1302", machine.name());
        }
        return machine;
    }

    // A setting's outcome as a message: nothing when it went through.
    static void check(Machine machine, String setting, String value, MachineAccess.Result result) throws ElclException {
        switch (result) {
            case APPLIED, UNCHANGED -> {}
            case UNSUPPORTED -> throw new ElclException("ELC1320", machine.name(), setting);
            case NOT_FORMED -> throw new ElclException("ELC1319", machine.name());
            case NO_MACHINE -> throw new ElclException("ELC1302", machine.name());
            case INVALID, REJECTED -> throw new ElclException("ELC1321", machine.name(), setting, value);
        }
    }

    public static void bind() {
        CommandRegistry.bind("RTVMCHSTS", call -> {
            ElclContext context = context(call);
            Machine machine = find(context.server(), context.network(), call.text("MCH"));
            MachineInfo info = machine.info();
            call.returns("RTNSTS", machine.status());
            call.returns("RTNRSN", info == null ? "" : info.reason().getString());
            call.returns("RTNPCT", (long) (info == null ? -1 : info.percent()));
            call.returns("RTNFE", info == null ? 0L : info.energy().map(MachineInfo.Energy::stored).orElse(0L));
            call.returns("RTNFECAP", info == null ? 0L : info.energy().map(MachineInfo.Energy::capacity).orElse(0L));
            call.returns("RTNRCP", info == null ? "" : info.recipe());
        });
        CommandRegistry.bind("RTVMCHSTAT", call -> {
            ElclContext context = context(call);
            Machine machine = find(context.server(), context.network(), call.text("MCH"));
            if (machine.info() == null) {
                throw new ElclException("ELC1302", machine.name());
            }
            MachineInfo.Statistics stats = machine.info().statistics();
            call.returns("RTNOPS", stats.operations());
            call.returns("RTNPRD", stats.itemsProduced());
            call.returns("RTNCNS", stats.itemsConsumed());
            call.returns("RTNFLDPRD", stats.fluidProduced());
            call.returns("RTNFLDCNS", stats.fluidConsumed());
            call.returns("RTNUPTIME", stats.uptimeTicks() / 20);
            call.returns("RTNOPM", BigDecimal.valueOf(stats.operationsPerMinute()).setScale(2, RoundingMode.HALF_UP));
        });
        CommandRegistry.bind("RTVMCHLST", call -> {
            ElclContext context = context(call);
            String type = call.text("TYPE").toUpperCase(Locale.ROOT), status = call.text("STATUS");
            List<String> names = new ArrayList<>();
            for (Machine machine : list(context.server(), context.network())) {
                if ((type.equals("*ALL") || machine.bridge().typeCode().equals(type) || machine.bridge().type().equalsIgnoreCase(type))
                        && (status.equals("*ALL") || machine.status().equals(status))) {
                    names.add(machine.name());
                }
            }
            call.returns("RTNLST", names);
        });
        CommandRegistry.bind("CHGMCHSTS", call -> {
            ElclContext context = context(call);
            Machine machine = present(context, call);
            ServerLevel level = context.server().getLevel(machine.bridge().dimension());
            String status = call.text("STATUS");
            check(machine, "STATUS", status, MachineBridges.access().setEnabled(level, machine.bridge().pos(), status.equals("*ENABLE")));
        });
        CommandRegistry.bind("CHGMCHCFG", call -> {
            ElclContext context = context(call);
            Machine machine = present(context, call);
            MachineBridge bridge = machine.bridge();
            ServerLevel level = context.server().getLevel(bridge.dimension());
            MachineAccess access = MachineBridges.access();
            String redstone = call.text("RSMODE");
            if (!redstone.equals("*SAME")) {
                check(machine, "RSMODE", redstone, access.setRedstoneMode(level, bridge.pos(), redstone.substring(1)));
            }
            String side = call.text("SIDE"), sideMode = call.text("SIDEMODE");
            if (!side.equals("*SAME") || !sideMode.equals("*SAME")) {
                if (side.equals("*SAME") || sideMode.equals("*SAME")) {
                    throw new ElclException("ELC0102", side.equals("*SAME") ? "SIDE" : "SIDEMODE");
                }
                if (machine.info().settings().sides().isEmpty()) {
                    throw new ElclException("ELC1320", machine.name(), "SIDE");
                }
                check(machine, "SIDEMODE", side + " " + sideMode, access.setSideMode(level, bridge.pos(), side.substring(1), sideMode.toLowerCase(Locale.ROOT)));
            }
            String eject = call.text("AUTOEJECT");
            if (!eject.equals("*SAME")) {
                check(machine, "AUTOEJECT", eject, access.setAutoEject(level, bridge.pos(), eject.equals("*YES")));
            }
            String power = call.text("PWRNET");
            if (!power.equals("*SAME")) {
                if (power.equals("*YES") && MachineBridges.energy(level, bridge) == null) {
                    throw new ElclException("ELC1320", machine.name(), "PWRNET");
                }
                bridge.setPowerFromNetwork(power.equals("*YES"));
            }
            String gateway = call.text("GATEWAY");
            if (gateway.equals("*NONE")) {
                bridge.setGateway(null);
            } else if (!gateway.equals("*SAME")) {
                ElclDevices.Device device = ElclDevices.find(context.server(), context.network(), gateway);
                if (device == null) {
                    throw new ElclException("ELC1301", gateway);
                }
                if (!(device.entity() instanceof GatewayBlockEntity)) {
                    throw new ElclException("ELC1303", device.name(), device.type());
                }
                bridge.setGateway(GlobalPos.of(device.pos().dimension(), device.pos().pos()));
            }
            call.send(ElclMessage.of("ELC1322", machine.name()));
        });
    }
}
