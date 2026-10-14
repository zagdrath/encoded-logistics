/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.elcl.compile.FileResolver;
import net.zagdrath.encodedlogistics.elcl.db.Dds;
import net.zagdrath.encodedlogistics.elcl.db.RecordFormat;
import org.junit.jupiter.api.Test;

// Every ELCL program and snippet in the documentation site compiles (./gradlew docsExamplesTest, part of buildDocs).
// tools/docs/build.py snippets writes them to build/docs/snippets with a manifest: each program's page, its compile
// target (a job or a PLC), whether it's a fragment to wrap in PGM / ENDPGM, and whether a warning is expected; and the
// physical files (DDS) the pages define, which the programs may declare alongside the shipped ones.
class DocsExamplesTest {
    static final Path SNIPPETS = Path.of(System.getProperty("encodedlogistics.docs.snippets", "build/docs/snippets"));

    @Test
    void everyDocsExampleCompiles() throws IOException {
        Path manifest = SNIPPETS.resolve("snippets.json");
        assumeTrue(Files.exists(manifest), "no snippets: run tools/docs/build.py snippets");
        JsonObject json = JsonParser.parseString(Files.readString(manifest, StandardCharsets.UTF_8)).getAsJsonObject();
        Map<String, RecordFormat> formats = new HashMap<>(CompilerTest.shippedFormats());
        List<String> problems = new ArrayList<>();
        for (JsonElement element : json.getAsJsonArray("files")) {
            JsonObject file = element.getAsJsonObject();
            String name = file.get("name").getAsString().toUpperCase(Locale.ROOT);
            Dds.Result result = Dds.compile(CompilerTest.read(SNIPPETS.resolve(file.get("file").getAsString())));
            if (!result.ok()) {
                problems.add(file.get("page").getAsString() + ": file " + name + ": " + result.diagnostics());
            } else {
                formats.put(name, result.format());
            }
        }
        FileResolver files = FileResolver.of(formats);
        int compiled = 0;
        for (JsonElement element : json.getAsJsonArray("programs")) {
            JsonObject program = element.getAsJsonObject();
            List<String> lines = new ArrayList<>(CompilerTest.read(SNIPPETS.resolve(program.get("file").getAsString())));
            if (program.get("wrap").getAsBoolean()) {
                lines.addFirst("PGM");
                lines.add("ENDPGM");
            }
            Compiler.Target target = Compiler.Target.valueOf(program.get("target").getAsString());
            Compiler.Result result = Compiler.compileTexts(lines, files, target);
            boolean warningsAllowed = program.get("warnings").getAsBoolean();
            boolean good = warningsAllowed ? result.ok() : result.diagnostics().isEmpty();
            if (!good) {
                StringBuilder detail = new StringBuilder();
                result.diagnostics().forEach(diagnostic -> detail.append("\n    line ").append(diagnostic.line() + 1).append(": ")
                        .append(diagnostic.message()));
                problems.add(program.get("page").getAsString() + " (" + program.get("name").getAsString() + ")" + detail);
            }
            compiled++;
        }
        if (!problems.isEmpty()) {
            fail(problems.size() + " documentation example(s) don't compile:\n" + String.join("\n", problems));
        }
        assertTrue(compiled > 0, "the manifest lists no programs");
    }
}
