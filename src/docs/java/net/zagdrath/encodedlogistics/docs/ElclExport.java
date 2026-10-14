/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.docs;

import java.util.Map;
import java.util.TreeMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.zagdrath.encodedlogistics.elcl.ElclMessages;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef;
import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.elcl.screen.SysvalService;
import net.zagdrath.encodedlogistics.elcl.store.StoredSysvalService;

// ELCL as the code defines it: every command in the registry with its full parameter schema (what the parser, the
// compiler, the F4 prompter and F1 help read), every message, the system values and the built-in functions.
final class ElclExport {
    private ElclExport() {}

    static JsonObject elcl() {
        JsonObject json = new JsonObject();
        json.add("commands", commands());
        json.add("messages", messages());
        json.add("systemValues", systemValues());
        json.add("builtins", builtins());
        return json;
    }

    private static JsonArray commands() {
        JsonArray array = new JsonArray();
        for (CommandDefinition command : CommandRegistry.all()) {
            JsonObject json = new JsonObject();
            json.addProperty("name", command.name());
            json.addProperty("description", command.description());
            json.addProperty("title", command.title());
            json.addProperty("context", command.context().name());
            json.addProperty("auth", command.auth().name());
            json.addProperty("positional", command.positional());
            json.addProperty("plcLocal", Compiler.plcLocal(command.name(), null));
            JsonArray params = new JsonArray();
            for (ParamDef param : command.params()) {
                params.add(param(param));
            }
            json.add("params", params);
            array.add(json);
        }
        return array;
    }

    private static JsonObject param(ParamDef param) {
        JsonObject json = new JsonObject();
        json.addProperty("keyword", param.keyword());
        json.addProperty("label", param.label());
        json.addProperty("kind", param.kind().name());
        json.addProperty("required", param.required());
        json.addProperty("default", param.defaultValue());
        json.add("specials", DocsExport.strings(param.specials()));
        json.addProperty("maxValues", param.maxValues());
        if (param.hasRange()) {
            json.addProperty("min", param.min());
            json.addProperty("max", param.max());
        }
        json.addProperty("length", param.length());
        json.addProperty("returns", param.returns() == null ? null : param.returns().special());
        json.addProperty("valueList", param.values().name());
        json.addProperty("hint", param.hint());
        return json;
    }

    private static JsonArray messages() {
        JsonArray array = new JsonArray();
        for (ElclMessages.Definition definition : ElclMessages.all().values()) {
            JsonObject json = new JsonObject();
            json.addProperty("id", definition.id());
            json.addProperty("severity", definition.severity());
            json.addProperty("text", definition.text());
            array.add(json);
        }
        return array;
    }

    private static JsonArray systemValues() {
        JsonArray array = new JsonArray();
        // SYSNAME's default is the system's own name: ELNET01 for the first network.
        for (SysvalService.Sysval value : StoredSysvalService.definitions("ELNET01")) {
            JsonObject json = new JsonObject();
            json.addProperty("name", value.name());
            json.addProperty("description", value.description());
            json.addProperty("default", value.defaultValue());
            json.add("allowed", DocsExport.strings(value.allowed()));
            array.add(json);
        }
        return array;
    }

    private static JsonArray builtins() {
        JsonArray array = new JsonArray();
        Map<String, Compiler.Builtin> sorted = new TreeMap<>(Compiler.builtins());
        sorted.forEach((name, builtin) -> {
            JsonObject json = new JsonObject();
            json.addProperty("name", name);
            json.addProperty("minArgs", builtin.min());
            json.addProperty("maxArgs", builtin.max());
            json.addProperty("result", builtin.result() == null ? null : builtin.result().special());
            array.add(json);
        });
        return array;
    }
}
