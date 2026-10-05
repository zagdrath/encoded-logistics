/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.zagdrath.encodedlogistics.blockentity.RedstoneDevice;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.Invocation;
import net.zagdrath.encodedlogistics.plc.PlcContext;

// RNMDEV (Part 2): renames a device (ElclDevices.rename). COMMANDS.md 7: RTVRSIN reads what arrives on a Control Interface's face (or the highest of the six, *MAX);
// CHGRSOUT sets what a face (or every face, *ALL) gives out. A PLC's faces work the same (by name on its network, or
// DEV(*SELF) in its own program - ELC1301 anywhere else). A name no device has is ELC1301, a device that isn't a
// Control Interface or PLC ELC1303, an offline one ELC1302 (CHGRSOUT then stores nothing); a level outside 0-15 ELC0004.
public final class RedstoneCommands {
    private RedstoneCommands() {}

    static void bind() {
        CommandRegistry.bind("RNMDEV", call -> {
            ElclContext context = OsCommands.context(call);
            if (context.network() == null) {
                throw new ElclException("ELC1302", "*NETWORK");
            }
            String name = call.text("DEV").toUpperCase(Locale.ROOT), newName = call.text("NEWNAME").toUpperCase(Locale.ROOT);
            ElclDevices.rename(context.server(), context.network(), name, newName);
            call.send(ElclMessage.of("ELC1309", name, newName));
        });
        CommandRegistry.bind("RTVRSIN", call -> {
            RedstoneDevice device = redstone(call);
            String side = call.text("SIDE");
            call.returns("RTNLVL", side.equals("*MAX") ? device.maxInput() : device.input(face(side)));
        });
        CommandRegistry.bind("CHGRSOUT", call -> {
            long level = call.integer("LVL");
            RedstoneDevice device = redstone(call);
            String side = call.text("SIDE");
            device.setOutput(side.equals("*ALL") ? null : face(side), (int) level, call.line());
        });
    }

    // The device DEV() names: a PLC's own faces (*SELF), or an online Control Interface or PLC on the network.
    static RedstoneDevice redstone(Invocation call) throws ElclException {
        String name = call.text("DEV").toUpperCase(Locale.ROOT);
        if (name.equals("*SELF")) {
            PlcContext plc = call.context(PlcContext.class);
            if (plc == null) {
                throw new ElclException("ELC1301", name);
            }
            return plc.plc();
        }
        ElclContext context = call.context(ElclContext.class);
        ElclDevices.Device device = context != null ? ElclDevices.find(context.server(), context.network(), name) : null;
        if (device == null) {
            throw new ElclException("ELC1301", name);
        }
        if (!(device.entity() instanceof RedstoneDevice redstone)) {
            throw new ElclException("ELC1303", name, device.type());
        }
        if (!redstone.isOnline()) {
            throw new ElclException("ELC1302", name);
        }
        return redstone;
    }

    // *NORTH ... *DOWN as a face.
    static @Nullable Direction face(String side) {
        return Direction.byName(side.substring(1).toLowerCase(Locale.ROOT));
    }
}
