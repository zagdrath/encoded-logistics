/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef;
import org.junit.jupiter.api.Test;

// The documentation site's Terminal OS screens (./gradlew exportDocsScreens, part of exportDocsData): every command
// that opens a screen, typed on a terminal with no server behind it as the layout tests do, with the screen's id,
// title and the grid it draws; the Main Menu's options; and the designed layouts (src/test/resources/layouts, whose
// fixed rows LayoutTest checks against the real screens) with their sample data. Writes screens.json to the folder in
// encodedlogistics.docs.export; skipped in an ordinary test run.
class DocsScreensExport {
    @Test
    void export() throws IOException {
        String folder = System.getProperty("encodedlogistics.docs.export");
        assumeTrue(folder != null, "only in exportDocsScreens");
        LayoutTest.language();
        JsonObject json = new JsonObject();
        JsonArray screens = new JsonArray();
        for (CommandDefinition command : CommandRegistry.all()) {
            if (!command.context().interactive()) {
                continue;
            }
            CrtTerminal terminal = LayoutTest.terminal();
            CrtPanel before = terminal.current();
            String line = sample(command);
            if (!ScreenCommands.open(terminal, line) || terminal.current() == before) {
                continue;
            }
            CrtPanel panel = terminal.current();
            JsonObject screen = new JsonObject();
            screen.addProperty("command", command.name());
            screen.addProperty("typed", line);
            screen.addProperty("id", panel.id());
            screen.addProperty("title", panel.title());
            // Screens that read the desk's network (its menu) draw only on a desk; the rest draw empty here.
            try {
                screen.add("grid", grid(terminal.compose()));
            } catch (RuntimeException e) {
                screen.add("grid", null);
            }
            screens.add(screen);
        }
        json.add("screens", screens);
        JsonArray menu = new JsonArray();
        for (MainMenu.Option option : MainMenu.OPTIONS) {
            JsonObject entry = new JsonObject();
            entry.addProperty("number", option.number());
            entry.addProperty("command", option.command());
            entry.addProperty("availability", option.availability().name());
            menu.add(entry);
        }
        json.add("mainMenu", menu);
        JsonObject layouts = new JsonObject();
        try (var files = Files.list(Path.of("src/test/resources/layouts"))) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString().replaceFirst("\\.txt$", "");
                layouts.add(name, grid(LayoutTest.layout(name)));
            }
        }
        json.add("layouts", layouts);
        Path out = Path.of(folder, "screens.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(json) + "\n", StandardCharsets.UTF_8);
    }

    // The command with a stand-in for each required parameter, so a screen that needs one (DSPROUTE RTR()) opens.
    private static String sample(CommandDefinition command) {
        StringBuilder line = new StringBuilder(command.name());
        for (ParamDef param : command.params()) {
            if (!param.required()) {
                continue;
            }
            String value = switch (param.kind()) {
                case QUALIFIED -> "MYLIB/SAMPLE";
                case INT, DEC -> Long.toString(Math.max(1, param.hasRange() ? param.min() : 1));
                case SPECIAL -> param.specials().isEmpty() ? "*ALL" : param.specials().getFirst();
                case TIME -> "0800";
                default -> "SAMPLE";
            };
            line.append(' ').append(param.keyword().toUpperCase(Locale.ROOT)).append('(').append(value).append(')');
        }
        return line.toString();
    }

    private static JsonArray grid(CrtGrid grid) {
        List<String> rows = new ArrayList<>();
        for (int row = 0; row < CrtGrid.ROWS; row++) {
            rows.add(new String(grid.chars[row]));
        }
        return grid(rows);
    }

    private static JsonArray grid(List<String> rows) {
        JsonArray array = new JsonArray();
        rows.forEach(row -> array.add(row.stripTrailing()));
        return array;
    }
}
