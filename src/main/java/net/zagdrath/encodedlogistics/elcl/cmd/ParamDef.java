/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.cmd;

import java.util.List;
import java.util.Locale;
import java.util.function.IntSupplier;

import org.jspecify.annotations.Nullable;

// One parameter of a command's schema (COMMANDS.md): its keyword, its prompter label, what it takes (kind), whether
// it's required, its default, the special values it accepts, how many values (a list: more than 1), a numeric range,
// the prompter field's length, the variable type a RTN* parameter returns and where F4 finds its values. The parser,
// the compiler, the prompter, F1 help and the command line all read it.
public record ParamDef(String keyword, String label, Kind kind, boolean required, @Nullable String defaultValue, List<String> specials, int maxValues,
        long min, long max, int length, @Nullable VarType returns, ValueList values) {

    // What a parameter takes.
    public enum Kind {
        CHAR,       // text: an expression of *CHAR (literals of any kind are taken as text)
        INT,        // a whole number (expression), maybe in a range
        DEC,        // a decimal number (expression)
        LGL,        // a logical expression (COND)
        NAME,       // a name up to 10 characters (bare, quoted, or a *CHAR variable)
        QUALIFIED,  // LIB/NAME or NAME (*LIBL), or a *CHAR variable
        ITEM,       // an item ID (bare, quoted or a variable)
        DEVICE,     // a device name (bare, quoted or a variable)
        SPECIAL,    // one of the special values (or a variable holding one)
        MSGID,      // a message ID: ELC1201, USR0001
        LABEL,      // a label in the same program
        VARIABLE,   // a variable: one that's set (RTN*, CHGVAR VAR, DOFOR VAR) or declared (DCL VAR, PGM PARM)
        COMMAND,    // a nested command (THEN, EXEC, CMD)
        VALUE,      // anything (CALL PARM, DCL VALUE)
        TIME        // a time of day: HHMM or HHMMSS
    }

    // Where F4 on the prompter finds a parameter's values.
    public enum ValueList {
        NONE, SPECIALS, ITEMS, DEVICES, LIBRARIES, MEMBERS, PROGRAMS, JOBS, SYSVALS, COMMAND
    }

    public enum VarType {
        CHAR, INT, DEC, LGL, LIST;

        public String special() {
            return "*" + name();
        }

        public static @Nullable VarType of(String special) {
            String name = special.startsWith("*") ? special.substring(1) : special;
            for (VarType type : values()) {
                if (type.name().equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return null;
        }
    }

    public boolean isList() {
        return maxValues > 1;
    }

    // A range up to the list limit (ADDLSTE / RMVLSTE POS): its top is the server's maxListSize, bound at setup.
    private static final long LIST_LIMIT = Long.MAX_VALUE - 1;
    private static volatile IntSupplier listLimit = () -> 4_096;

    public static void listLimit(IntSupplier limit) {
        listLimit = limit;
    }

    @Override
    public long max() {
        return max == LIST_LIMIT ? listLimit.getAsInt() : max;
    }

    public boolean hasRange() {
        return min != Long.MIN_VALUE || max != Long.MAX_VALUE;
    }

    public boolean acceptsSpecial(String value) {
        for (String special : specials) {
            if (special.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    // A RTN* parameter: a variable the command sets.
    public boolean isReturn() {
        return returns != null;
    }

    // The prompter's hint: "*CHAR variable", "1-999999", "*FAIL, *PARTIAL", "*ANY, name", "Name, F4 for list".
    public String hint() {
        if (returns != null) {
            return returns.special() + " variable";
        }
        String names = switch (kind) {
            case ITEM -> "Name, F4 for list";
            case DEVICE -> "Device, F4 for list";
            case QUALIFIED -> "Library/name";
            case NAME, LABEL -> "Name";
            case MSGID -> "Message ID";
            case COMMAND -> "Command, F4 to prompt";
            case LGL -> "Logical expression";
            case VARIABLE -> "Variable";
            case TIME -> "HHMM or HHMMSS";
            case INT, DEC -> hasRange() ? min + "-" + max() : kind == Kind.INT ? "Number" : "Decimal";
            case CHAR, VALUE -> "Character value";
            case SPECIAL -> "";
        };
        if (specials.isEmpty()) {
            return names;
        }
        String list = String.join(", ", specials);
        return kind == Kind.SPECIAL ? list : list + ", " + names.toLowerCase(Locale.ROOT);
    }

    public static Builder of(String keyword, String label, Kind kind) {
        return new Builder(keyword, label, kind);
    }

    public static final class Builder {
        private final String keyword, label;
        private final Kind kind;
        private boolean required;
        private @Nullable String defaultValue;
        private List<String> specials = List.of();
        private int maxValues = 1;
        private long min = Long.MIN_VALUE, max = Long.MAX_VALUE;
        private int length;
        private @Nullable VarType returns;
        private @Nullable ValueList values;

        private Builder(String keyword, String label, Kind kind) {
            this.keyword = keyword;
            this.label = label;
            this.kind = kind;
        }

        public Builder req() {
            required = true;
            return this;
        }

        public Builder dft(String value) {
            defaultValue = value;
            return this;
        }

        public Builder sv(String... specials) {
            this.specials = List.of(specials);
            return this;
        }

        public Builder list(int maxValues) {
            this.maxValues = maxValues;
            return this;
        }

        public Builder range(long min, long max) {
            this.min = min;
            this.max = max;
            return this;
        }

        // 1 to the list limit.
        public Builder listPosition() {
            return range(1, LIST_LIMIT);
        }

        public Builder len(int length) {
            this.length = length;
            return this;
        }

        public Builder returns(VarType type) {
            returns = type;
            return this;
        }

        public Builder values(ValueList values) {
            this.values = values;
            return this;
        }

        public ParamDef build() {
            int field = length > 0 ? length : switch (kind) {
                case INT, DEC, NAME, DEVICE, LABEL, MSGID, VARIABLE -> 10;
                case QUALIFIED -> 21;
                case TIME -> 6;
                case SPECIAL -> Math.max(4, specials.stream().mapToInt(String::length).max().orElse(4));
                default -> 32;
            };
            if (!specials.isEmpty() && kind != Kind.SPECIAL) {
                field = Math.max(field, specials.stream().mapToInt(String::length).max().orElse(0));
            }
            ValueList list = values != null ? values : switch (kind) {
                case ITEM -> ValueList.ITEMS;
                case DEVICE -> ValueList.DEVICES;
                case COMMAND -> ValueList.COMMAND;
                default -> specials.isEmpty() ? ValueList.NONE : ValueList.SPECIALS;
            };
            return new ParamDef(keyword, label, kind, required, defaultValue, specials, maxValues, min, max, field, returns, list);
        }
    }
}
