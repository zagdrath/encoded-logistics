/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.Direction;
import net.zagdrath.encodedlogistics.blockentity.ControlInterfaceBlockEntity;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.Invocation;

// COMMANDS.md 7: RTVRSIN reads what arrives on a Control Interface's face (or the highest of the six, *MAX);
// CHGRSOUT sets what a face (or every face, *ALL) gives out. A name no device has is ELC1301, a device that isn't a
// Control Interface ELC1303, an offline one ELC1302 (CHGRSOUT then stores nothing); a level outside 0-15 ELC0004.
public final class RedstoneCommands {
    private RedstoneCommands() {}

    static void bind() {
        CommandRegistry.bind("RTVRSIN", call -> {
            ControlInterfaceBlockEntity ci = controlInterface(call);
            String side = call.text("SIDE");
            call.returns("RTNLVL", side.equals("*MAX") ? ci.maxInput() : ci.input(face(side)));
        });
        CommandRegistry.bind("CHGRSOUT", call -> {
            long level = call.integer("LVL");
            ControlInterfaceBlockEntity ci = controlInterface(call);
            String side = call.text("SIDE");
            ci.setOutput(side.equals("*ALL") ? null : face(side), (int) level);
        });
    }

    // The online Control Interface DEV() names.
    static ControlInterfaceBlockEntity controlInterface(Invocation call) throws ElclException {
        ElclContext context = call.context(ElclContext.class);
        String name = call.text("DEV").toUpperCase(Locale.ROOT);
        ElclDevices.Device device = context != null ? ElclDevices.find(context.server(), context.network(), name) : null;
        if (device == null) {
            throw new ElclException("ELC1301", name);
        }
        if (!(device.entity() instanceof ControlInterfaceBlockEntity ci)) {
            throw new ElclException("ELC1303", name, device.type());
        }
        if (!ci.isOnline()) {
            throw new ElclException("ELC1302", name);
        }
        return ci;
    }

    // *NORTH ... *DOWN as a face.
    static @Nullable Direction face(String side) {
        return Direction.byName(side.substring(1).toLowerCase(Locale.ROOT));
    }
}
