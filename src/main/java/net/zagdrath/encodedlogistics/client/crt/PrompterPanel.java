/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.client.crt;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.Diagnostic;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef;
import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.elcl.parse.Expr;
import net.zagdrath.encodedlogistics.elcl.parse.Parser;
import net.zagdrath.encodedlogistics.elcl.parse.Stmt;
import net.zagdrath.encodedlogistics.net.CrtResponsePayload;
import net.zagdrath.encodedlogistics.terminal.TerminalLine;

// PROMPT (screen 6, F4): a command's parameters from its CommandRegistry definition - one row each from row 4, the
// label with its dot leaders (bright when required), the keyword (dim), the field (the schema's length, at most 24)
// and the hint (dim; long ones go on to the next row). Required parameters first; the others under "Additional
// Parameters", shown with F10 or once any of them has a value. A list parameter takes its values with blanks between
// (+ in it opens a window for them). F4 on a field: its value list (special values here; items, devices, libraries,
// members, programs, jobs and system values from the server) or, on a command parameter (SBMJOB CMD), the prompter
// for that command. Enter checks the values (ELC0102 / ELC0103 / ELC0004, the cursor to the field) and hands the
// command back - keywords in schema order, defaults left out. F5 resets to the defaults; F3 and F12 go back unchanged.
final class PrompterPanel extends CrtPanel {
    private static final int FIRST = 4, LAST = 19, LABEL = 1, KEYWORD = 24, FIELD = 35, HINT = 61, HINT_WIDTH = 18, MAX_FIELD = 24;

    private final CommandDefinition definition;
    private final boolean variables;
    private final Consumer<String> done;
    private final Map<String, String> values = new LinkedHashMap<>();
    private final Map<String, CrtField> byKeyword = new LinkedHashMap<>();
    private boolean additional;
    private int top;
    private int lines;
    private @Nullable ValueListWindow valueList;

    // A layout line: a parameter's row (or a hint's continuation), the heading, or a blank.
    private record Line(@Nullable ParamDef param, String hint, boolean heading) {}

    private final List<Line> layout = new ArrayList<>();

    PrompterPanel(CrtTerminal screen, CommandDefinition definition, String text, boolean variables, Consumer<String> done) {
        super(screen);
        this.definition = definition;
        this.variables = variables;
        this.done = done;
        Parser.Result parsed = Parser.parseCommand(text);
        Stmt given = parsed.statements().isEmpty() ? null : parsed.statements().getFirst();
        for (ParamDef param : definition.params()) {
            Stmt.Param value = given != null && definition.name().equals(given.name()) ? given.param(param.keyword()) : null;
            if (value != null) {
                values.put(param.keyword(), shown(value));
                if (!param.required()) {
                    additional = true;
                }
            } else {
                values.put(param.keyword(), param.defaultValue() != null ? param.defaultValue() : "");
            }
        }
        rebuild();
    }

    // The command a text starts, or null.
    static @Nullable CommandDefinition definition(String text) {
        String word = text.trim().split("[\\s(]", 2)[0];
        return word.isEmpty() ? null : CommandRegistry.get(word);
    }

    // A parameter's values as the field shows them: strings without their quotes when that's all there is.
    private static String shown(Stmt.Param param) {
        if (param.values().size() == 1 && param.values().getFirst() instanceof Expr.Str str) {
            return str.value();
        }
        List<String> parts = new ArrayList<>();
        for (Expr value : param.values()) {
            parts.add(value.toString());
        }
        return String.join(" ", parts);
    }

    // A label with dot leaders: the dots on every other column (the odd ones) up to `end`, from two past the label.
    static String leaders(String label, int start, int end) {
        char[] out = new char[end - start + 1];
        Arrays.fill(out, ' ');
        for (int i = 0; i < label.length() && i < out.length; i++) {
            out[i] = label.charAt(i);
        }
        for (int i = label.length() + 1; i < out.length; i++) {
            if ((start + i) % 2 == 1) {
                out[i] = '.';
            }
        }
        return new String(out);
    }

    @Override
    String id() {
        return "PROMPT";
    }

    @Override
    String title() {
        return definition.title();
    }

    @Override
    String prompt() {
        return "";
    }

    @Override
    String keys() {
        return tr("crt.encodedlogistics.fkeys.prompt");
    }

    @Override
    void shown() {
        rebuild();
    }

    private boolean anyOptional() {
        return definition.params().stream().anyMatch(param -> !param.required());
    }

    private String hint(ParamDef param) {
        return param.isList() ? tr("crt.encodedlogistics.prompt.more_values") : param.hint();
    }

    // The lines (required parameters, then the heading and the others when shown) and this page's fields.
    private void rebuild() {
        keep();
        layout.clear();
        for (boolean required : new boolean[] { true, false }) {
            if (!required) {
                if (!additional || !anyOptional()) {
                    break;
                }
                layout.add(new Line(null, "", false));
                layout.add(new Line(null, "", true));
                layout.add(new Line(null, "", false));
            }
            for (ParamDef param : definition.params()) {
                if (param.required() == required) {
                    List<String> hints = CrtWindow.wrap(hint(param), HINT_WIDTH);
                    layout.add(new Line(param, hints.getFirst(), false));
                    for (String more : hints.subList(1, hints.size())) {
                        layout.add(new Line(null, more, false));
                    }
                }
            }
        }
        lines = LAST - FIRST + 1;
        top = Math.max(0, Math.min(top, (layout.size() - 1) / lines * lines));
        CrtField focused = screen.focused();
        String focusedKeyword = null;
        for (Map.Entry<String, CrtField> entry : byKeyword.entrySet()) {
            if (entry.getValue() == focused) {
                focusedKeyword = entry.getKey();
            }
        }
        fields.clear();
        byKeyword.clear();
        for (int i = 0; i < lines && top + i < layout.size(); i++) {
            ParamDef param = layout.get(top + i).param();
            if (param != null) {
                // Up to 24 shown; a list's values (or a long value) scroll in it.
                int capacity = param.isList() || param.kind() == ParamDef.Kind.COMMAND ? 512 : Math.max(1, param.length());
                CrtField field = new CrtField(FIRST + i, FIELD, Math.min(MAX_FIELD, Math.max(1, param.length())), capacity, values.get(param.keyword()));
                if (param.required()) {
                    field.required();
                }
                fields.add(field);
                byKeyword.put(param.keyword(), field);
            }
        }
        CrtField again = focusedKeyword != null ? byKeyword.get(focusedKeyword) : null;
        if (again != null) {
            screen.focus(again);
        } else if (!fields.isEmpty() && (focused == null || !fields.contains(focused))) {
            screen.focus(fields.getFirst());
        }
    }

    // The fields' values back into the map (before a new page or layout).
    private void keep() {
        byKeyword.forEach((keyword, field) -> values.put(keyword, field.value.trim()));
    }

    @Override
    void draw(CrtGrid grid) {
        grid.put(2, 1, tr("crt.encodedlogistics.type_choices"));
        for (int i = 0; i < lines && top + i < layout.size(); i++) {
            Line line = layout.get(top + i);
            int row = FIRST + i;
            if (line.heading()) {
                grid.put(row, LABEL, tr("crt.encodedlogistics.prompt.additional"), CrtGrid.BRIGHT);
            } else if (line.param() != null) {
                ParamDef param = line.param();
                grid.put(row, LABEL, leaders(param.label(), LABEL, KEYWORD - 3), param.required() ? CrtGrid.BRIGHT : CrtGrid.NORMAL);
                grid.put(row, KEYWORD, param.keyword(), CrtGrid.DIM);
            }
            if (!line.hint().isEmpty()) {
                grid.put(row, HINT, line.hint(), CrtGrid.DIM);
            }
        }
        if (!layout.isEmpty()) {
            grid.right(20, tr(top + lines < layout.size() ? "crt.encodedlogistics.more" : "crt.encodedlogistics.bottom"), CrtGrid.NORMAL);
        }
    }

    @Override
    void page(int direction) {
        keep();
        int next = top + direction * lines;
        if (next >= 0 && next < layout.size()) {
            top = next;
            rebuild();
        }
    }

    private @Nullable ParamDef paramOf(@Nullable CrtField field) {
        for (Map.Entry<String, CrtField> entry : byKeyword.entrySet()) {
            if (entry.getValue() == field) {
                return definition.param(entry.getKey());
            }
        }
        return null;
    }

    @Override
    @Nullable String helpField(@Nullable CrtField field) {
        ParamDef param = paramOf(field);
        return param != null ? param.keyword() : null;
    }

    // F1: from the definition - the command's, or the parameter's (its keyword, what it takes, its special values).
    @Override
    String help(@Nullable String field) {
        ParamDef param = field != null ? definition.param(field) : null;
        if (param == null) {
            StringBuilder text = new StringBuilder(tr("crt.encodedlogistics.prompt.help", definition.title())).append("\n");
            for (ParamDef each : definition.params()) {
                text.append("\n  ").append(CrtGrid.pad(each.keyword(), 10)).append(" ").append(each.label()).append(each.required() ? " *" : "");
            }
            return text.toString();
        }
        StringBuilder text = new StringBuilder(param.label() + " (" + param.keyword() + ")\n\n");
        text.append(tr("crt.encodedlogistics.prompt.help_type", hint(param))).append("\n");
        if (!param.specials().isEmpty()) {
            text.append(tr("crt.encodedlogistics.prompt.help_specials", String.join(", ", param.specials()))).append("\n");
        }
        if (param.defaultValue() != null) {
            text.append(tr("crt.encodedlogistics.prompt.help_default", param.defaultValue())).append("\n");
        }
        if (param.required()) {
            text.append(tr("crt.encodedlogistics.prompt.help_required"));
        }
        return text.toString();
    }

    @Override
    boolean functionKey(int f) {
        switch (f) {
            case 3 -> {
                screen.back();
                return true;
            }
            case 4 -> {
                prompt4(screen.focused());
                return true;
            }
            case 5 -> {
                for (ParamDef param : definition.params()) {
                    values.put(param.keyword(), param.defaultValue() != null ? param.defaultValue() : "");
                }
                byKeyword.clear();
                rebuild();
                return true;
            }
            case 10 -> {
                keep();
                additional = !additional || !anyOptional();
                rebuild();
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    // F4 on a field: the nested command's prompter, or the value list.
    private void prompt4(@Nullable CrtField field) {
        ParamDef param = paramOf(field);
        if (param == null || field == null) {
            return;
        }
        keep();
        if (param.kind() == ParamDef.Kind.COMMAND) {
            String inner = field.trimmed();
            Consumer<String> back = text -> {
                values.put(param.keyword(), text);
                byKeyword.clear();
                rebuild();
            };
            if (inner.isEmpty() || !screen.prompter(inner, variables, back)) {
                screen.message(tr("crt.encodedlogistics.prompt.type_command"));
            }
            return;
        }
        String kind = switch (param.values()) {
            case ITEMS -> "items";
            case DEVICES -> "devices";
            case LIBRARIES -> "libraries";
            case MEMBERS -> "members";
            case PROGRAMS -> "programs";
            case JOBS -> "jobs";
            case SYSVALS -> "sysvals";
            default -> null;
        };
        valueList = new ValueListWindow(screen, param.label(), value -> {
            values.put(param.keyword(), value);
            byKeyword.clear();
            rebuild();
        });
        for (String special : param.specials()) {
            valueList.add(special, "");
        }
        if (kind != null) {
            String filter = param.values() == ParamDef.ValueList.ITEMS ? field.trimmed() : "";
            screen.query("values " + kind + (filter.isEmpty() ? "" : " \"" + filter + "\""));
        } else if (param.specials().isEmpty()) {
            valueList = null;
            screen.message(hint(param));
            return;
        }
        screen.openWindow(valueList);
    }

    @Override
    void receive(CrtResponsePayload response) {
        if (valueList != null && answers(response, "values")) {
            for (TerminalLine line : response.lines()) {
                valueList.add(cell(line, 0), cell(line, 1));
            }
            response.message().ifPresent(screen::message);
        }
    }

    // A list field typed as "+": its values one to a field, in a window.
    @Override
    boolean enter() {
        keep();
        for (ParamDef param : definition.params()) {
            if (param.isList() && values.get(param.keyword()).equals("+")) {
                values.put(param.keyword(), "");
                FormWindow form = new FormWindow(screen, param.label() + " (" + param.keyword() + ")", tr("crt.encodedlogistics.prompt.values"), list -> {
                    values.put(param.keyword(), String.join(" ", list.stream().filter(v -> !v.isEmpty()).toList()));
                    byKeyword.clear();
                    rebuild();
                });
                for (int i = 0; i < 4; i++) {
                    form.field(tr("crt.encodedlogistics.prompt.value", i + 1), Math.min(MAX_FIELD, param.length()), "", "");
                }
                screen.openWindow(form);
                return true;
            }
        }
        String command = command();
        ElclMessage problem = check(command);
        if (problem != null) {
            screen.message(problem.toString());
            CrtField at = fieldFor(problem);
            if (at != null) {
                screen.focus(at);
            }
            return true;
        }
        screen.back();
        done.accept(command);
        return true;
    }

    // The command: keywords in schema order, values as typed (names in upper case, text quoted when it has to be),
    // defaults and blanks left out.
    String command() {
        keep();
        StringBuilder out = new StringBuilder(definition.name());
        for (ParamDef param : definition.params()) {
            String value = values.getOrDefault(param.keyword(), "").trim();
            if (value.isEmpty() || param.defaultValue() != null && value.equalsIgnoreCase(param.defaultValue())) {
                continue;
            }
            out.append(' ').append(param.keyword()).append('(').append(format(param, value)).append(')');
        }
        return out.toString();
    }

    static String format(ParamDef param, String value) {
        if (value.startsWith("'") || value.startsWith("&") || param.kind() == ParamDef.Kind.COMMAND) {
            return value;
        }
        switch (param.kind()) {
            case CHAR, VALUE -> {
                boolean plain = value.startsWith("*") && !value.contains(" ") || value.matches("-?[0-9]+(\\.[0-9]+)?") || value.startsWith("%")
                        || value.contains("*CAT") || value.contains("*BCAT") || value.contains("*TCAT") || value.startsWith("(");
                if (param.isList() || plain) {
                    return value;
                }
                return "'" + value.replace("'", "''") + "'";
            }
            case ITEM -> {
                return value.contains(":") || value.contains("#") ? "'" + value + "'" : value.toUpperCase(Locale.ROOT);
            }
            case LGL -> {
                return value;
            }
            default -> {
                return value.toUpperCase(Locale.ROOT);
            }
        }
    }

    // The command's first problem, or null: parse, then the schema's checks (variables allowed when it's for the editor).
    @Nullable ElclMessage check(String command) {
        Parser.Result parsed = Parser.parseCommand(command);
        if (!parsed.diagnostics().isEmpty()) {
            return parsed.diagnostics().getFirst().message();
        }
        for (Diagnostic diagnostic : Compiler.checkCommand(parsed.statements().getFirst())) {
            if (variables && diagnostic.message().id().equals("ELC0002")) {
                continue;
            }
            if (diagnostic.isError()) {
                return diagnostic.message();
            }
        }
        return null;
    }

    // The field a problem is about: ELC0102 names its keyword first, ELC0103 second; otherwise the field holding the
    // value it quotes.
    private @Nullable CrtField fieldFor(ElclMessage problem) {
        List<String> data = problem.data();
        String keyword = problem.id().equals("ELC0102") && !data.isEmpty() ? data.get(0) : problem.id().equals("ELC0103") && data.size() > 1 ? data.get(1) : null;
        if (keyword != null) {
            if (!byKeyword.containsKey(keyword) && definition.param(keyword) != null && !definition.param(keyword).required()) {
                additional = true;
                rebuild();
            }
            return byKeyword.get(keyword);
        }
        if (!data.isEmpty()) {
            for (Map.Entry<String, String> entry : values.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(data.getFirst())) {
                    return byKeyword.get(entry.getKey());
                }
            }
        }
        return null;
    }
}
