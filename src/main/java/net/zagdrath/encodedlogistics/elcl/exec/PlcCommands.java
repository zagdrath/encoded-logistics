/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.SourceLine;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.Invocation;
import net.zagdrath.encodedlogistics.elcl.screen.ElclServices;
import net.zagdrath.encodedlogistics.elcl.screen.ElclSystem;
import net.zagdrath.encodedlogistics.plc.PlcBlockEntity;
import net.zagdrath.encodedlogistics.plc.PlcContext;
import net.zagdrath.encodedlogistics.plc.PlcModule;
import net.zagdrath.encodedlogistics.plc.PlcProgram;
import net.zagdrath.encodedlogistics.plc.PlcSensors;

// The PLC commands (docs/plc HANDOFF 4):
//   RTVSNSVAL  a sensor module's reading - in a PLC's own program (DEV(*SELF), the default), or a networked PLC's by name.
//              With RTNSTS given, a missing module or one without a target comes back as *NOMODULE / *NOTARGET; without
//              it they're ELC1501 / ELC1503. TYPE() other than *ANY: the module must be that kind (ELC1501).
//   SNDPLCPGM  loads a program compiled with CRTELPGM TGT(*PLC) into a PLC on the network (ELC1504 for any other); it
//              stops first, then runs from the top with RUN(*YES). The loader's the user running it (their authority).
//              The same program loaded again keeps its retained variables.
//   STRPLC     RUN, clearing a fault (ELC1505 without a program); ENDPLC  STOP.
public final class PlcCommands {
    private PlcCommands() {}

    static void bind() {
        CommandRegistry.bind("RTVSNSVAL", call -> {
            PlcBlockEntity plc = plc(call, call.text("DEV"));
            int slot = (int) call.integer("MODULE");
            String type = call.text("TYPE");
            PlcModule wanted = type.isEmpty() || type.equals("*ANY") ? null : PlcModule.bySpecial(type);
            PlcModule module = plc.module(slot - 1);
            boolean status = call.given("RTNSTS");
            if (module == PlcModule.EMPTY || wanted != null && module != wanted) {
                if (!status) {
                    throw new ElclException("ELC1501", slot, (wanted != null ? wanted : PlcModule.EMPTY).label());
                }
                returns(call, PlcSensors.NO_MODULE);
                return;
            }
            PlcSensors.Reading reading = plc.read(slot - 1);
            if (!reading.ok() && !status) {
                throw new ElclException("ELC1503", slot, reading.aux());
            }
            returns(call, reading);
        });
        CommandRegistry.bind("SNDPLCPGM", call -> {
            ElclSystem system = OsCommands.system(call);
            String user = OsCommands.user(call);
            String[] pgm = program(system, user, call.text("PGM"));
            if (!ElclServices.libraries().programTarget(system, pgm[0], pgm[1]).equals("*PLC")) {
                throw new ElclException("ELC1504", pgm[1]);
            }
            var source = SourceLine.texts(ElclServices.libraries().programSource(system, pgm[0], pgm[1]));
            Map<String, String> files = ElclServices.libraries().programFiles(system, pgm[0], pgm[1]);
            PlcBlockEntity plc = plc(call, call.text("DEV"));
            UUID player = OsCommands.player(call);
            PlcProgram old = plc.program();
            var retained = old != null && old.name().equals(pgm[1]) ? old.retained() : Map.<String, java.util.List<String>>of();
            PlcProgram loaded = new PlcProgram(pgm[1], source, files, user.toUpperCase(Locale.ROOT), java.util.Optional.ofNullable(player),
                    plc.authority(null, player), system.nowShort(), retained);
            plc.load(loaded, call.text("RUN").equals("*YES"));
            call.send(ElclMessage.of("ELC1509", pgm[1], name(plc)));
        });
        CommandRegistry.bind("STRPLC", call -> {
            PlcBlockEntity plc = plc(call, call.text("DEV"));
            if (plc.program() == null) {
                throw new ElclException("ELC1505", name(plc));
            }
            plc.start();
            call.send(ElclMessage.of("ELC1507", name(plc)));
        });
        CommandRegistry.bind("ENDPLC", call -> {
            PlcBlockEntity plc = plc(call, call.text("DEV"));
            plc.stop();
            call.send(ElclMessage.of("ELC1508", name(plc)));
        });
    }

    private static void returns(Invocation call, PlcSensors.Reading reading) {
        call.returns("RTNVAL", reading.value());
        call.returns("RTNSTS", reading.status());
        call.returns("RTNAUX", reading.aux());
    }

    private static String name(PlcBlockEntity plc) {
        return plc.deviceName().isEmpty() ? PlcBlockEntity.TYPE : plc.deviceName();
    }

    // The PLC DEV() names: this one (*SELF, in a PLC's program), or one on the network.
    static PlcBlockEntity plc(Invocation call, String dev) throws ElclException {
        String name = dev.toUpperCase(Locale.ROOT);
        if (name.isEmpty() || name.equals("*SELF")) {
            PlcContext context = call.context(PlcContext.class);
            if (context == null) {
                throw new ElclException("ELC1301", "*SELF");
            }
            return context.plc();
        }
        ElclContext context = OsCommands.context(call);
        ElclDevices.Device device = ElclDevices.find(context.server(), context.network(), name);
        if (device == null) {
            throw new ElclException("ELC1301", name);
        }
        if (!(device.entity() instanceof PlcBlockEntity plc)) {
            throw new ElclException("ELC1303", name, device.type());
        }
        return plc;
    }

    // PGM(LIB/NAME) as {library, name}: a bare name (or *LIBL) down the user's library list, the first with it.
    private static String[] program(ElclSystem system, String user, String text) throws ElclException {
        String value = text.toUpperCase(Locale.ROOT);
        int slash = value.indexOf('/');
        String library = slash >= 0 ? value.substring(0, slash) : "*LIBL", name = slash >= 0 ? value.substring(slash + 1) : value;
        if (library.equals("*CURLIB")) {
            return new String[] { ElclServices.users().profile(system, user, null).currentLibrary(), name };
        }
        if (!library.equals("*LIBL")) {
            return new String[] { library, name };
        }
        @Nullable String found = null;
        for (String candidate : OsCommands.libraryList(system, user)) {
            try {
                if (ElclServices.libraries().programs(system, candidate).contains(name)) {
                    found = candidate;
                    break;
                }
            } catch (ElclException ignored) {
                // Not there.
            }
        }
        if (found == null) {
            throw new ElclException("ELC0203", name, "*LIBL");
        }
        return new String[] { found, name };
    }
}
