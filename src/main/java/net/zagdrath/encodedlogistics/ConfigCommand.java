/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.zagdrath.encodedlogistics.multiblock.ControllerStructures;
import org.jspecify.annotations.Nullable;

// /encodedlogistics config: the mod's main config (Config.SPEC, encodedlogistics-local.toml) from in game, as Arcforge's
// /arcforge config does. Operators only; changes are saved to the file and take effect at once (every network works out
// its lanes again).
//   config get <option>                  an option's value, default and range
//   config set <option> <value>          sets one (checked against its range or allowed values)
//   config reset all|<section>|<option>  back to the defaults: every option, a section's, or one
// Options are written as section.key (lanes.resolveInterval); tab completion lists them all.
public final class ConfigCommand {
    private ConfigCommand() {}

    private static final SuggestionProvider<CommandSourceStack> OPTIONS = (context, builder) -> SharedSuggestionProvider.suggest(options(), builder);
    private static final SuggestionProvider<CommandSourceStack> TARGETS = (context, builder) -> {
        List<String> targets = new ArrayList<>();
        targets.add("all");
        targets.addAll(sections());
        targets.addAll(options());
        return SharedSuggestionProvider.suggest(targets, builder);
    };
    private static final SuggestionProvider<CommandSourceStack> VALUES = (context, builder) -> {
        ModConfigSpec.ConfigValue<?> value = value(StringArgumentType.getString(context, "option"));
        List<String> values = new ArrayList<>();
        if (value != null) {
            Object fallback = value.getDefault();
            if (fallback instanceof Boolean) {
                values.addAll(List.of("true", "false"));
            } else if (fallback instanceof Enum<?> constant) {
                Arrays.stream(constant.getDeclaringClass().getEnumConstants()).map(Enum::name).forEach(values::add);
            }
            values.add(String.valueOf(fallback));
        }
        return SharedSuggestionProvider.suggest(values, builder);
    };

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("encodedlogistics")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("config")
                        .then(Commands.literal("get")
                                .then(Commands.argument("option", StringArgumentType.word()).suggests(OPTIONS)
                                        .executes(context -> get(context.getSource(), StringArgumentType.getString(context, "option")))))
                        .then(Commands.literal("set")
                                .then(Commands.argument("option", StringArgumentType.word()).suggests(OPTIONS)
                                        .then(Commands.argument("value", StringArgumentType.greedyString()).suggests(VALUES)
                                                .executes(context -> set(context.getSource(), StringArgumentType.getString(context, "option"),
                                                        StringArgumentType.getString(context, "value"))))))
                        .then(Commands.literal("reset")
                                .then(Commands.argument("target", StringArgumentType.word()).suggests(TARGETS)
                                        .executes(context -> reset(context.getSource(), StringArgumentType.getString(context, "target")))))));
    }

    // Every option's path (section.key), in the order the file has them.
    static List<String> options() {
        List<String> paths = new ArrayList<>();
        walk(Config.SPEC.getSpec(), "", paths, false);
        return paths;
    }

    static List<String> sections() {
        List<String> paths = new ArrayList<>();
        walk(Config.SPEC.getSpec(), "", paths, true);
        return paths;
    }

    private static void walk(UnmodifiableConfig config, String prefix, List<String> out, boolean sections) {
        for (Map.Entry<String, Object> entry : config.valueMap().entrySet()) {
            String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            if (entry.getValue() instanceof UnmodifiableConfig section) {
                if (sections) {
                    out.add(path);
                }
                walk(section, path, out, sections);
            } else if (!sections) {
                out.add(path);
            }
        }
    }

    private static ModConfigSpec.@Nullable ConfigValue<?> value(String path) {
        return Config.SPEC.getValues().get(path) instanceof ModConfigSpec.ConfigValue<?> value ? value : null;
    }

    private static ModConfigSpec.@Nullable ValueSpec spec(String path) {
        return Config.SPEC.getSpec().get(path) instanceof ModConfigSpec.ValueSpec spec ? spec : null;
    }

    // "lanes.resolveInterval = 20 (default 20, 0 to 1200)".
    private static String describe(String path, ModConfigSpec.ConfigValue<?> value) {
        ModConfigSpec.ValueSpec spec = spec(path);
        String allowed = "";
        if (spec != null && spec.getRange() != null) {
            allowed = ", " + spec.getRange().getMin() + " to " + spec.getRange().getMax();
        } else if (value.getDefault() instanceof Enum<?> constant) {
            allowed = ", one of " + String.join(" ", Arrays.stream(constant.getDeclaringClass().getEnumConstants()).map(Enum::name).toList());
        }
        return path + " = " + value.get() + " (default " + value.getDefault() + allowed + ")";
    }

    private static int get(CommandSourceStack source, String path) {
        ModConfigSpec.ConfigValue<?> value = value(path);
        if (value == null) {
            source.sendFailure(Component.literal("No option " + path + " (tab lists them)"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(describe(path, value)), false);
        return 1;
    }

    @SuppressWarnings("unchecked")
    private static int set(CommandSourceStack source, String path, String text) {
        ModConfigSpec.ConfigValue<Object> value = (ModConfigSpec.ConfigValue<Object>) value(path);
        ModConfigSpec.ValueSpec spec = spec(path);
        if (value == null || spec == null) {
            source.sendFailure(Component.literal("No option " + path + " (tab lists them)"));
            return 0;
        }
        Object parsed = parse(value.getDefault(), text.trim());
        if (parsed == null || !spec.test(parsed)) {
            source.sendFailure(Component.literal("Not a value " + path + " takes: " + text + " (" + describe(path, value).replaceFirst("^.* \\(", "")));
            return 0;
        }
        value.set(parsed);
        saved(source);
        source.sendSuccess(() -> Component.literal("Set " + describe(path, value)), true);
        return 1;
    }

    private static @Nullable Object parse(Object fallback, String text) {
        try {
            return switch (fallback) {
                case Boolean b -> text.equalsIgnoreCase("true") ? Boolean.TRUE : text.equalsIgnoreCase("false") ? Boolean.FALSE : null;
                case Integer i -> Integer.parseInt(text.replace("_", "").replace(",", ""));
                case Long l -> Long.parseLong(text.replace("_", "").replace(",", ""));
                case Double d -> Double.parseDouble(text.replace("_", ""));
                case Enum<?> constant -> {
                    for (Enum<?> other : constant.getDeclaringClass().getEnumConstants()) {
                        if (other.name().equalsIgnoreCase(text)) {
                            yield other;
                        }
                    }
                    yield null;
                }
                case List<?> list -> text.isEmpty() ? List.of() : Arrays.stream(text.split(",")).map(String::trim).toList();
                case String s -> text;
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static int reset(CommandSourceStack source, String target) {
        List<String> paths;
        if (target.equalsIgnoreCase("all")) {
            paths = options();
        } else if (sections().contains(target)) {
            paths = options().stream().filter(path -> path.startsWith(target + ".")).toList();
        } else if (value(target) != null) {
            paths = List.of(target);
        } else {
            source.sendFailure(Component.literal("No option or section " + target + " (tab lists them)"));
            return 0;
        }
        List<String> changed = new ArrayList<>();
        for (String path : paths) {
            ModConfigSpec.ConfigValue<Object> value = (ModConfigSpec.ConfigValue<Object>) value(path);
            if (value != null && !value.get().equals(value.getDefault())) {
                value.set(value.getDefault());
                changed.add(path);
            }
        }
        if (!changed.isEmpty()) {
            saved(source);
        }
        source.sendSuccess(() -> Component.literal(changed.isEmpty() ? "Nothing to reset: " + paths.size() + " option(s) already at their defaults"
                : "Reset " + changed.size() + " of " + paths.size() + " option(s) to their defaults: " + String.join(", ", changed)), true);
        return changed.size();
    }

    // Written to the file, and every network works out its lanes again with the new values.
    private static void saved(CommandSourceStack source) {
        Config.SPEC.save();
        ControllerStructures.everythingChanged(source.getServer());
    }
}
