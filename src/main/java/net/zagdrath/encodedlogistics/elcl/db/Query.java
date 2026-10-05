/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.db;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.parse.Expr;
import net.zagdrath.encodedlogistics.elcl.parse.Parser;
import net.zagdrath.encodedlogistics.elcl.parse.Stmt;
import net.zagdrath.encodedlogistics.elcl.vm.Values;

// RUNQRY's record selection and sort (and a Display Panel table's): QRYSLT is an ELCL expression over the file's field
// names - 'QTY *LT 100 *AND ACTIVE', '%SST(ITEM 1 4) *EQ "IRON"' - with a literal in double quotes or doubled single
// ones, and a field written bare or as &FIELD; a name that isn't a field is ELC2242. *ALL (or blank) takes every record.
// SORT names up to 4 fields, each followed by *DESCEND if it should go highest first (*ASCEND, the default, also
// allowed); *NONE keeps the file's order (key order, else arrival). Records the sort can't tell apart keep that order.
public final class Query {
    public static final int MAX_SORT = 4;

    // A selection: its expression, field names made variables (null: every record).
    public record Selection(@Nullable Expr expr, RecordFormat format) {
        public boolean selects(Object[] values) throws ElclException {
            if (expr == null) {
                return true;
            }
            return Values.truth(Values.eval(expr, new Values.Scope() {
                @Override
                public Object get(String variable) throws ElclException {
                    int index = format.index(variable.substring(1));
                    if (index < 0) {
                        throw new ElclException("ELC0002", variable);
                    }
                    return values[index];
                }

                @Override
                public String itemName(String item) {
                    return item;
                }
            }));
        }
    }

    public record SortKey(int index, boolean descending) {}

    private Query() {}

    public static Selection selection(String qryslt, RecordFormat format, String file) throws ElclException {
        String text = qryslt.strip();
        if (text.isEmpty() || text.equalsIgnoreCase("*ALL")) {
            return new Selection(null, format);
        }
        Parser.Result parsed = Parser.parseCommand("IF COND(" + singleQuoted(text) + ")");
        if (!parsed.diagnostics().isEmpty()) {
            throw new ElclException(parsed.diagnostics().getFirst().message());
        }
        Stmt statement = parsed.statements().isEmpty() ? null : parsed.statements().getFirst();
        Expr condition = statement != null ? statement.value("COND") : null;
        if (condition == null || statement.params().size() != 1) {
            throw new ElclException("ELC0001", text);
        }
        return new Selection(fields(condition, format, file), format);
    }

    // "..." literals as '...' ones (a single quote in one doubled), the way midrange QRYSLTs write them.
    static String singleQuoted(String text) {
        StringBuilder out = new StringBuilder();
        boolean single = false, dbl = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (dbl) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') {
                    out.append('"');
                    i++;
                } else if (c == '"') {
                    out.append('\'');
                    dbl = false;
                } else {
                    out.append(c == '\'' ? "''" : String.valueOf(c));
                }
            } else if (c == '"' && !single) {
                out.append('\'');
                dbl = true;
            } else {
                if (c == '\'') {
                    single = !single;
                }
                out.append(c);
            }
        }
        return out.toString();
    }

    // Names and &variables that are fields become the variables Selection reads; any other is ELC2242.
    private static Expr fields(Expr expr, RecordFormat format, String file) throws ElclException {
        return switch (expr) {
            case Expr.Name name -> field(name.name(), name.line(), format, file);
            case Expr.Var var -> field(var.name().substring(1), var.line(), format, file);
            case Expr.Group group -> new Expr.Group(fields(group.inner(), format, file), group.line());
            case Expr.Unary unary -> new Expr.Unary(unary.op(), fields(unary.operand(), format, file), unary.line());
            case Expr.Binary binary -> new Expr.Binary(binary.op(), fields(binary.left(), format, file), fields(binary.right(), format, file), binary.line());
            case Expr.Builtin builtin -> {
                List<Expr> args = new ArrayList<>();
                for (Expr arg : builtin.args()) {
                    args.add(fields(arg, format, file));
                }
                yield new Expr.Builtin(builtin.function(), List.copyOf(args), builtin.line());
            }
            case Expr.Path path -> throw new ElclException("ELC2242", path.toString(), file);
            case Expr.Nested nested -> throw new ElclException("ELC0001", nested.command().name());
            default -> expr;
        };
    }

    private static Expr field(String name, int line, RecordFormat format, String file) throws ElclException {
        String upper = name.toUpperCase(Locale.ROOT);
        if (format.index(upper) < 0) {
            throw new ElclException("ELC2242", upper, file);
        }
        return new Expr.Var("&" + upper, line);
    }

    // SORT(field [*DESCEND] ...): ELC2242 for a name that isn't a field, ELC2224 past MAX_SORT fields.
    public static List<SortKey> sort(List<String> spec, RecordFormat format, String file) throws ElclException {
        List<SortKey> keys = new ArrayList<>();
        for (String word : spec) {
            String upper = word.strip().toUpperCase(Locale.ROOT);
            if (upper.isEmpty() || upper.equals("*NONE") || upper.equals("*ASCEND")) {
                continue;
            }
            if (upper.equals("*DESCEND")) {
                if (keys.isEmpty()) {
                    throw new ElclException("ELC0103", upper, "SORT");
                }
                keys.set(keys.size() - 1, new SortKey(keys.getLast().index(), true));
                continue;
            }
            int index = format.index(upper);
            if (index < 0) {
                throw new ElclException("ELC2242", upper, file);
            }
            keys.add(new SortKey(index, false));
        }
        if (keys.size() > MAX_SORT) {
            throw new ElclException("ELC2224", "sort fields", MAX_SORT);
        }
        return keys;
    }

    // The records the selection takes, sorted.
    public static List<DbRecord> run(List<DbRecord> records, Selection selection, List<SortKey> sort) throws ElclException {
        List<DbRecord> selected = new ArrayList<>();
        Object[] values;
        for (DbRecord record : records) {
            values = record.values();
            if (selection.selects(values)) {
                selected.add(record);
            }
        }
        if (!sort.isEmpty()) {
            RecordFormat format = selection.format();
            Comparator<DbRecord> order = (a, b) -> {
                for (SortKey key : sort) {
                    FieldDef field = format.fields().get(key.index());
                    int compare = field.compare(a.value(key.index()), b.value(key.index()));
                    if (compare != 0) {
                        return key.descending() ? -compare : compare;
                    }
                }
                return 0;
            };
            selected.sort(order);
        }
        return selected;
    }
}
