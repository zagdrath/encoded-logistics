/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.plc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef.VarType;
import net.zagdrath.encodedlogistics.elcl.compile.VarDecl;
import net.zagdrath.encodedlogistics.elcl.vm.Values;

// A PLC's program (component encodedlogistics:plc_program): kept in the PLC, on its item when it's broken, and on an
// EEPROM Cartridge. Its name, its source (compiled again from it, as a job's program is - the same compiler, target PLC)
// and the record formats of any files it declares, who loaded it (name, UUID - whose Firewall authority it runs with on
// a network - and that authority when it was loaded), when it was saved, and its retained variables (RETAIN(*YES)) by
// name, each as text: one value, or a *LIST's elements.
public record PlcProgram(String name, List<String> source, Map<String, String> files, String loadedBy, Optional<UUID> loaderId, String authority,
        String saved, Map<String, List<String>> retained) {
    public static final Codec<PlcProgram> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("name").forGetter(PlcProgram::name),
            Codec.STRING.listOf().fieldOf("source").forGetter(PlcProgram::source),
            Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("files", Map.of()).forGetter(PlcProgram::files),
            Codec.STRING.optionalFieldOf("loaded_by", "").forGetter(PlcProgram::loadedBy),
            UUIDUtil.CODEC.optionalFieldOf("loader").forGetter(PlcProgram::loaderId),
            Codec.STRING.optionalFieldOf("authority", "").forGetter(PlcProgram::authority),
            Codec.STRING.optionalFieldOf("saved", "").forGetter(PlcProgram::saved),
            Codec.unboundedMap(Codec.STRING, Codec.STRING.listOf()).optionalFieldOf("retained", Map.of()).forGetter(PlcProgram::retained))
            .apply(i, PlcProgram::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, PlcProgram> STREAM_CODEC = ByteBufCodecs.fromCodecWithRegistries(CODEC);

    public PlcProgram {
        source = List.copyOf(source);
        files = Map.copyOf(files);
        retained = Map.copyOf(retained);
    }

    // Its size as the screens and tooltips give it: the source's characters, a byte each, and a line end per line.
    public int size() {
        int bytes = 0;
        for (String line : source) {
            bytes += line.stripTrailing().length() + 1;
        }
        return bytes;
    }

    public PlcProgram withRetained(Map<String, List<String>> values) {
        return new PlcProgram(name, source, files, loadedBy, loaderId, authority, saved, values);
    }

    public PlcProgram withSource(List<String> lines, Map<String, String> formats, String by, @Nullable UUID id, String auth, String when) {
        return new PlcProgram(name, lines, formats, by, Optional.ofNullable(id), auth, when, retained);
    }

    public PlcProgram loadedBy(String by, @Nullable UUID id, String auth) {
        return new PlcProgram(name, source, files, by, Optional.ofNullable(id), auth, saved, retained);
    }

    // --- Retained values as text ---

    // A variable's value as it's kept.
    public static List<String> encode(Object value) {
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            list.forEach(element -> out.add(String.valueOf(element)));
            return out;
        }
        if (value instanceof BigDecimal decimal) {
            return List.of(decimal.toPlainString());
        }
        if (value instanceof Boolean flag) {
            return List.of(flag ? "1" : "0");
        }
        return List.of(String.valueOf(value));
    }

    // A kept value back, for its declaration (null when it no longer reads as that type).
    public static @Nullable Object decode(VarDecl decl, List<String> kept) {
        String first = kept.isEmpty() ? "" : kept.getFirst();
        try {
            return switch (decl.type()) {
                case INT -> Long.parseLong(first.trim());
                case DEC -> new BigDecimal(first.trim());
                case LGL -> first.trim().equals("1") || first.trim().equalsIgnoreCase("*ON") || first.trim().equalsIgnoreCase("TRUE");
                case CHAR -> Values.fit(first, decl.length());
                case LIST -> new ArrayList<>(kept);
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // A kept value as the Display Retained Variables screen shows it.
    public static String shown(VarType type, List<String> kept) {
        if (type == VarType.LIST) {
            return "(" + kept.size() + ") " + String.join(", ", kept);
        }
        String first = kept.isEmpty() ? "" : kept.getFirst();
        return type == VarType.LGL ? (first.equals("1") ? "'1'" : "'0'") : type == VarType.CHAR ? "'" + first.stripTrailing() + "'"
                : first.toUpperCase(Locale.ROOT);
    }
}
