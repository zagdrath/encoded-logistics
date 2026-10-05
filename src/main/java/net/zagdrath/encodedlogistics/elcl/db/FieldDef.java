/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.db;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef.VarType;
import net.zagdrath.encodedlogistics.elcl.vm.Values;

// One field of a physical file's record format (DDS: name, length, type, decimals, TEXT, COLHDG). What a record holds
// for each type: A a String (trailing blanks dropped, cut to its length), S a Long of up to `length` digits, P a
// BigDecimal at `decimals` places (half up) of up to `length` digits, L a Boolean, T a game timestamp as text
// ("00012 06:30:15": the day, then the time on the game clock; it sorts and compares as text). A program sees each
// field as a variable of the matching type (*CHAR, *INT, *DEC, *LGL; T as *CHAR 14).
public record FieldDef(String name, Type type, int length, int decimals, String text, List<String> headings) {
    public enum Type {
        CHAR('A'), INT('S'), DEC('P'), LGL('L'), TIME('T');

        public final char code;

        Type(char code) {
            this.code = code;
        }

        public static Type of(char code) {
            for (Type type : values()) {
                if (type.code == Character.toUpperCase(code)) {
                    return type;
                }
            }
            return null;
        }
    }

    // The longest each type may be: A as a *CHAR, S within 64 bits, P as a *DEC.
    public static final int MAX_CHAR = 1_024, MAX_INT = 18, MAX_DEC = 31, TIME_LENGTH = 14;

    public FieldDef {
        headings = List.copyOf(headings);
    }

    public VarType varType() {
        return switch (type) {
            case CHAR, TIME -> VarType.CHAR;
            case INT -> VarType.INT;
            case DEC -> VarType.DEC;
            case LGL -> VarType.LGL;
        };
    }

    // The length its variable is declared with (*CHAR's length, *DEC's digits).
    public int varLength() {
        return switch (type) {
            case CHAR -> length;
            case TIME -> TIME_LENGTH;
            case DEC -> length;
            default -> 0;
        };
    }

    // Characters it takes in a record (what storage counts): its length; a timestamp 14, a logical 1.
    public int size() {
        return switch (type) {
            case TIME -> TIME_LENGTH;
            case LGL -> 1;
            default -> length;
        };
    }

    // Columns a value takes on a report: text its length, numbers with a sign (and point), a timestamp 14.
    public int width() {
        return switch (type) {
            case CHAR -> length;
            case INT -> length + 1;
            case DEC -> length + (decimals > 0 ? 2 : 1);
            case LGL -> 1;
            case TIME -> TIME_LENGTH;
        };
    }

    public boolean numeric() {
        return type == Type.INT || type == Type.DEC;
    }

    // Its column heading: COLHDG's lines, else the field's name.
    public List<String> heading() {
        return headings.isEmpty() ? List.of(name) : headings;
    }

    // "64A", "11S 0", "9P 2", "1L", "T".
    public String definition() {
        return switch (type) {
            case CHAR, LGL -> length + "" + type.code;
            case INT, DEC -> length + "" + type.code + " " + decimals;
            case TIME -> "T";
        };
    }

    // An empty record's value: blanks, zero, false; a timestamp blank (the time it's written, when it is).
    public Object blank() {
        return switch (type) {
            case CHAR, TIME -> "";
            case INT -> 0L;
            case DEC -> BigDecimal.ZERO.setScale(decimals);
            case LGL -> Boolean.FALSE;
        };
    }

    // Any value (a variable's, a value of another field, text) as this field holds it. Text too long for an A field is
    // cut, as a *CHAR assignment is; a number too big, text that isn't a number, a logical that isn't '1' / '0' or a
    // timestamp that isn't one are ELC2209. A timestamp blank or *NOW stays blank: the time it's written fills it.
    public Object convert(Object value) throws ElclException {
        try {
            return switch (type) {
                case CHAR -> {
                    // Control characters never go into a record (they separate its saved values).
                    String text = Values.text(value).replaceAll("\\p{Cntrl}", " ").stripTrailing();
                    yield text.length() > length ? text.substring(0, length).stripTrailing() : text;
                }
                case INT -> {
                    // A whole number: typed text with a fraction isn't one.
                    if (value instanceof String s && !s.isBlank() && Values.number(s.trim()).stripTrailingZeros().scale() > 0) {
                        throw new ElclException("ELC2209", s.strip(), name);
                    }
                    long number = Values.integer(value instanceof String s ? s.isBlank() ? "0" : s.trim() : value);
                    if (number != 0 && Long.toString(Math.abs(number)).length() > length) {
                        throw new ElclException("ELC2209", Values.text(value).strip(), name);
                    }
                    yield number;
                }
                case DEC -> Values.decimal(value instanceof String s ? s.isBlank() ? "0" : s.trim() : value, length, decimals);
                case LGL -> value instanceof String s && s.isBlank() ? Boolean.FALSE : Values.logical(value);
                case TIME -> Timestamps.normalize(Values.text(value));
            };
        } catch (ElclException e) {
            if (e.elclMessage().id().equals("ELC2209")) {
                throw e;
            }
            throw new ElclException("ELC2209", Values.text(value).strip(), name);
        }
    }

    // A stored value as text: as a CSV cell, on a screen, for a key in a saved position.
    public String text(Object value) {
        return switch (type) {
            case CHAR, TIME -> String.valueOf(value);
            case INT -> Long.toString((Long) value);
            case DEC -> ((BigDecimal) value).setScale(decimals, RoundingMode.HALF_UP).toPlainString();
            case LGL -> (Boolean) value ? "1" : "0";
        };
    }

    // A value laid out in its report column: text left, numbers right.
    public String column(Object value) {
        String text = text(value);
        int width = width();
        if (numeric()) {
            return text.length() >= width ? text : " ".repeat(width - text.length()) + text;
        }
        return text.length() >= width ? text.substring(0, width) : text + " ".repeat(width - text.length());
    }

    public int compare(Object a, Object b) {
        return switch (type) {
            case CHAR, TIME -> ((String) a).stripTrailing().compareTo(((String) b).stripTrailing());
            case INT -> Long.compare((Long) a, (Long) b);
            case DEC -> ((BigDecimal) a).compareTo((BigDecimal) b);
            case LGL -> Boolean.compare((Boolean) a, (Boolean) b);
        };
    }

    // The same type, as CHGPF keeps a field's values by.
    public boolean sameType(FieldDef other) {
        return type == other.type;
    }

    static String upper(String name) {
        return name.trim().toUpperCase(Locale.ROOT);
    }
}
