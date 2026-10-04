/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import net.minecraft.server.MinecraftServer;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef;
import net.zagdrath.encodedlogistics.multiblock.NetworkIndex.NetworkRef;
import net.zagdrath.encodedlogistics.rack.RackPermission;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

// A command typed on a command line (only literals there): its values worked out as a program would, the built-ins
// that need a program (%SIZE, %ELEM, %NAME) ELC0001, and a value that fails the command's escape message.
class CommandRunnerTest {
    static {
        if (CommandRegistry.get("TSTLINE") == null) {
            CommandRegistry.register(CommandDefinition.of("TSTLINE", "Test line").positional(1)
                    .p(ParamDef.of("MSG", "Message", ParamDef.Kind.CHAR)).build());
            CommandRegistry.bind("TSTLINE", call -> call.send(ElclMessage.user("USR0001", 0, call.text("MSG"))));
        }
    }

    private static final ElclContext CONTEXT = new ElclContext() {
        @Override
        public MinecraftServer server() {
            throw new UnsupportedOperationException();
        }

        @Override
        public @Nullable NetworkRef network() {
            return null;
        }

        @Override
        public String user() {
            return "TESTER";
        }

        @Override
        public boolean allowed(RackPermission permission) {
            return true;
        }
    };

    // What TSTLINE says with that MSG(), or the escape it fails with.
    private static String say(String msg) {
        CommandRunner.Result result = CommandRunner.run(CONTEXT, "TSTLINE MSG(" + msg + ")");
        if (result.escape() != null) {
            return result.escape().id();
        }
        assertNotNull(result.statement());
        return result.messages().getFirst().text();
    }

    @Test
    void builtIns() {
        assertEquals("4", say("%CHAR(%SCAN('a' 'banana' 3))"));
        assertEquals("0", say("%CHAR(%SCAN('x' 'banana'))"));
        assertEquals("1.23", say("%CHAR(%DEC('1.23456' 5 2))"));
        assertEquals("1.23456", say("%CHAR(%DEC('1.234561'))"));
        assertEquals("ELC0007", say("%CHAR(%DEC('12345' 5 2))"));
        assertEquals("1", say("%CHAR(7.5 // 2)"));
    }

    @Test
    void aValueThatFailsIsItsEscape() {
        assertEquals("ELC0004", say("%SST('abc' 2 9)"));
        assertEquals("ELC0005", say("%CHAR(1 / 0)"));
        assertEquals("ELC0001", say("%NAME(IRON_INGOT)"));
        assertEquals("ELC0001", say("%CHAR(%SIZE('a'))"));
    }
}
