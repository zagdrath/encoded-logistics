/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.vm;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.compile.VarDecl;
import net.zagdrath.encodedlogistics.elcl.parse.Expr;

// ELCL values at run time (ELCL_SPEC.md 4-5) and the expressions that make them. A value is a String (*CHAR), a Long
// (*INT), a BigDecimal (*DEC), a Boolean (*LGL) or a List<String> (*LIST); a variable holds its declared type's:
// *CHAR padded or cut to its length, *DEC at its decimals (rounded half up), *INT within 64 bits (ELC0007 past them).
public final class Values {
    // Where an expression finds its variables, and the game's item names (%NAME).
    public interface Scope {
        Object get(String variable) throws ElclException;

        String itemName(String item);
    }

    private static final MathContext DIVISION = new MathContext(34, RoundingMode.HALF_UP);

    private Values() {}

    // --- A variable's starting value and assignment ---

    public static Object initial(VarDecl decl) {
        return switch (decl.type()) {
            case CHAR -> " ".repeat(decl.length());
            case INT -> 0L;
            case DEC -> BigDecimal.ZERO.setScale(decl.decimals());
            case LGL -> Boolean.FALSE;
            case LIST -> new ArrayList<String>();
        };
    }

    // A value as the variable's type stores it. A list limit (maxList) bounds *LIST values (ELC0015).
    @SuppressWarnings("unchecked")
    public static Object convert(VarDecl decl, Object value, int maxList) throws ElclException {
        return switch (decl.type()) {
            case CHAR -> fit(text(value), decl.length());
            case INT -> integer(value);
            case DEC -> decimal(value, decl.length(), decl.decimals());
            case LGL -> truth(value);
            case LIST -> {
                List<String> list = value instanceof List<?> l ? new ArrayList<>((List<String>) l) : new ArrayList<>(List.of(text(value).stripTrailing()));
                if (list.size() > maxList) {
                    throw new ElclException("ELC0015", maxList);
                }
                yield list;
            }
        };
    }

    // Padded with blanks or cut to the length (never an error at run time).
    public static String fit(String text, int length) {
        return text.length() >= length ? text.substring(0, length) : text + " ".repeat(length - text.length());
    }

    // --- Conversions ---

    // A value as text: numbers plain, logicals '1' / '0', a list its elements joined by blanks.
    public static String text(Object value) {
        return switch (value) {
            case String s -> s;
            case Long l -> Long.toString(l);
            case BigDecimal d -> d.toPlainString();
            case Boolean b -> b ? "1" : "0";
            case List<?> list -> String.join(" ", list.stream().map(String::valueOf).map(String::stripTrailing).toList());
            default -> String.valueOf(value);
        };
    }

    public static long integer(Object value) throws ElclException {
        return switch (value) {
            case Long l -> l;
            case BigDecimal d -> {
                try {
                    yield d.setScale(0, RoundingMode.DOWN).longValueExact();
                } catch (ArithmeticException e) {
                    throw new ElclException("ELC0007");
                }
            }
            case Boolean b -> b ? 1L : 0L;
            default -> {
                String text = text(value).trim();
                try {
                    yield new BigDecimal(text).setScale(0, RoundingMode.DOWN).longValueExact();
                } catch (NumberFormatException e) {
                    throw new ElclException("ELC0003", text, "*INT");
                } catch (ArithmeticException e) {
                    throw new ElclException("ELC0007");
                }
            }
        };
    }

    public static BigDecimal number(Object value) throws ElclException {
        return switch (value) {
            case Long l -> BigDecimal.valueOf(l);
            case BigDecimal d -> d;
            case Boolean b -> b ? BigDecimal.ONE : BigDecimal.ZERO;
            default -> {
                String text = text(value).trim();
                try {
                    yield new BigDecimal(text);
                } catch (NumberFormatException e) {
                    throw new ElclException("ELC0003", text, "*DEC");
                }
            }
        };
    }

    // A decimal at `decimals` places (half up), no more than `digits` digits in all (ELC0007).
    public static BigDecimal decimal(Object value, int digits, int decimals) throws ElclException {
        BigDecimal d = number(value).setScale(decimals, RoundingMode.HALF_UP);
        if (d.precision() - d.scale() > digits - decimals && d.signum() != 0) {
            throw new ElclException("ELC0007");
        }
        return d;
    }

    public static boolean truth(Object value) {
        return switch (value) {
            case Boolean b -> b;
            case Long l -> l != 0;
            case BigDecimal d -> d.signum() != 0;
            default -> {
                String text = text(value).trim().toUpperCase(Locale.ROOT);
                yield text.equals("1") || text.equals("*TRUE") || text.equals("*YES") || text.equals("*ON");
            }
        };
    }

    private static boolean numeric(Object value) {
        return value instanceof Long || value instanceof BigDecimal;
    }

    // --- Expressions ---

    public static Object eval(Expr expr, Scope scope) throws ElclException {
        return switch (expr) {
            case Expr.Str str -> str.value();
            case Expr.Num num -> num.value().scale() <= 0 && num.value().compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0
                    && num.value().compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) >= 0 ? num.value().longValueExact() : num.value();
            case Expr.Name name -> name.name();
            case Expr.Special special -> special.name().equals("*TRUE") ? Boolean.TRUE : special.name().equals("*FALSE") ? Boolean.FALSE : special.name();
            case Expr.Path path -> path.toString();
            case Expr.Group group -> eval(group.inner(), scope);
            case Expr.Nested nested -> nested.command().toSource();
            case Expr.Var var -> scope.get(var.name());
            case Expr.Unary unary -> unary.op().equals("-") ? negate(eval(unary.operand(), scope)) : !truth(eval(unary.operand(), scope));
            case Expr.Binary binary -> binary(binary, scope);
            case Expr.Builtin builtin -> builtin(builtin, scope);
        };
    }

    private static Object negate(Object value) throws ElclException {
        if (value instanceof Long l) {
            try {
                return Math.negateExact(l);
            } catch (ArithmeticException e) {
                throw new ElclException("ELC0007");
            }
        }
        return number(value).negate();
    }

    private static Object binary(Expr.Binary binary, Scope scope) throws ElclException {
        String op = binary.op();
        switch (op) {
            case "*AND" -> {
                return truth(eval(binary.left(), scope)) && truth(eval(binary.right(), scope));
            }
            case "*OR" -> {
                return truth(eval(binary.left(), scope)) || truth(eval(binary.right(), scope));
            }
            default -> {}
        }
        Object left = eval(binary.left(), scope), right = eval(binary.right(), scope);
        return switch (op) {
            case "+", "-", "*", "/", "//" -> arithmetic(op, left, right);
            case "*CAT" -> text(left) + text(right);
            case "*BCAT" -> text(left).stripTrailing() + " " + text(right);
            case "*TCAT" -> text(left).stripTrailing() + text(right);
            default -> {
                int compare = compare(left, right);
                yield switch (op) {
                    case "*EQ" -> compare == 0;
                    case "*NE" -> compare != 0;
                    case "*GT" -> compare > 0;
                    case "*LT" -> compare < 0;
                    case "*GE", "*NL" -> compare >= 0;
                    default -> compare <= 0;
                };
            }
        };
    }

    // Numbers by value; logicals with logicals ('1' / '0' text among them); text ignoring trailing blanks.
    public static int compare(Object left, Object right) throws ElclException {
        if (numeric(left) && numeric(right) || numeric(left) && looksNumeric(right) || numeric(right) && looksNumeric(left)) {
            return number(left).compareTo(number(right));
        }
        if (left instanceof Boolean || right instanceof Boolean) {
            return Boolean.compare(truth(left), truth(right));
        }
        return text(left).stripTrailing().compareTo(text(right).stripTrailing());
    }

    private static boolean looksNumeric(Object value) {
        if (numeric(value)) {
            return true;
        }
        try {
            new BigDecimal(text(value).trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static Object arithmetic(String op, Object left, Object right) throws ElclException {
        if ((op.equals("/") || op.equals("//")) && number(right).signum() == 0) {
            throw new ElclException("ELC0005");
        }
        if (left instanceof Long a && right instanceof Long b) {
            try {
                return switch (op) {
                    case "+" -> Math.addExact(a, b);
                    case "-" -> Math.subtractExact(a, b);
                    case "*" -> Math.multiplyExact(a, b);
                    // Toward zero.
                    case "/" -> {
                        if (a == Long.MIN_VALUE && b == -1) {
                            throw new ArithmeticException();
                        }
                        yield a / b;
                    }
                    default -> a % b;
                };
            } catch (ArithmeticException e) {
                throw new ElclException("ELC0007");
            }
        }
        BigDecimal a = number(left), b = number(right);
        return switch (op) {
            case "+" -> a.add(b);
            case "-" -> a.subtract(b);
            case "*" -> a.multiply(b);
            case "/" -> a.divide(b, DIVISION);
            default -> a.remainder(b);
        };
    }

    @SuppressWarnings("unchecked")
    private static Object builtin(Expr.Builtin builtin, Scope scope) throws ElclException {
        List<Expr> args = builtin.args();
        List<Object> values = new ArrayList<>(args.size());
        for (Expr arg : args) {
            values.add(eval(arg, scope));
        }
        return switch (builtin.function()) {
            case "%TRIM" -> text(values.get(0)).strip();
            case "%TRIML" -> text(values.get(0)).stripLeading();
            case "%TRIMR" -> text(values.get(0)).stripTrailing();
            case "%UPPER" -> text(values.get(0)).toUpperCase(Locale.ROOT);
            case "%LOWER" -> text(values.get(0)).toLowerCase(Locale.ROOT);
            case "%LEN" -> (long) text(values.get(0)).stripTrailing().length();
            case "%CHAR" -> numeric(values.get(0)) ? text(values.get(0)) : text(values.get(0)).stripTrailing();
            case "%INT" -> integer(values.get(0));
            case "%DEC" -> values.size() >= 3 ? decimal(values.get(0), (int) integer(values.get(1)), (int) integer(values.get(2))) : number(values.get(0));
            case "%ABS" -> values.get(0) instanceof Long l ? (Object) Math.abs(l) : number(values.get(0)).abs();
            case "%MIN" -> compare(values.get(0), values.get(1)) <= 0 ? values.get(0) : values.get(1);
            case "%MAX" -> compare(values.get(0), values.get(1)) >= 0 ? values.get(0) : values.get(1);
            case "%SCAN" -> {
                String find = text(values.get(0)).stripTrailing(), in = text(values.get(1));
                int start = values.size() > 2 ? (int) integer(values.get(2)) : 1;
                if (start < 1) {
                    throw new ElclException("ELC0004", start);
                }
                yield find.isEmpty() || start > in.length() ? 0L : (long) (in.indexOf(find, start - 1) + 1);
            }
            case "%SST" -> {
                String text = text(values.get(0));
                long start = integer(values.get(1)), length = integer(values.get(2));
                if (start < 1 || length < 0 || start - 1 + length > text.length()) {
                    throw new ElclException("ELC0004", start + " " + length);
                }
                yield text.substring((int) start - 1, (int) (start - 1 + length));
            }
            case "%SIZE" -> values.get(0) instanceof List<?> list ? (long) list.size() : 1L;
            case "%ELEM" -> {
                List<String> list = values.get(0) instanceof List<?> l ? (List<String>) l : List.of(text(values.get(0)));
                long n = integer(values.get(1));
                if (n < 1 || n > list.size()) {
                    throw new ElclException("ELC0006", n, list.size());
                }
                yield list.get((int) n - 1);
            }
            case "%NAME" -> scope.itemName(text(values.get(0)).strip());
            default -> throw new ElclException("ELC0001", builtin.function());
        };
    }
}
