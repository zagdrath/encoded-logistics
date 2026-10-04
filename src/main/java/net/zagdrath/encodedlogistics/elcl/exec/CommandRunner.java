/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.exec;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.Diagnostic;
import net.zagdrath.encodedlogistics.elcl.ElclException;
import net.zagdrath.encodedlogistics.elcl.ElclMessage;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition;
import net.zagdrath.encodedlogistics.elcl.cmd.Invocation;
import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef;
import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.elcl.parse.Expr;
import net.zagdrath.encodedlogistics.elcl.parse.Parser;
import net.zagdrath.encodedlogistics.elcl.parse.Stmt;
import net.zagdrath.encodedlogistics.rack.RackPermission;

// One ELCL command typed on a command line (an interactive job, OS.md 5): parsed and checked against its schema,
// allowed here (*INTERACTIVE), authorised (the Firewall, ELC0401), then run with its values worked out from the
// literals typed (there are no variables on a command line). What comes back: its messages, the values of its RTN*
// parameters (Command Entry shows "RTNLVL = 7"), and the escape message it failed with, if it did.
public final class CommandRunner {
    public record Result(@Nullable Stmt statement, List<ElclMessage> messages, Map<String, String> returns, @Nullable ElclMessage escape) {
        public boolean ok() {
            return escape == null;
        }
    }

    private CommandRunner() {}

    public static Result run(ElclContext context, String line) {
        Parser.Result parsed = Parser.parseCommand(line);
        if (!parsed.diagnostics().isEmpty()) {
            return failed(null, parsed.diagnostics().getFirst().message());
        }
        if (parsed.statements().isEmpty()) {
            return new Result(null, List.of(), Map.of(), null);
        }
        Stmt statement = parsed.statements().getFirst();
        CommandDefinition command = statement.definition();
        if (command == null) {
            return failed(statement, ElclMessage.of("ELC0101", statement.name()));
        }
        if (!command.context().interactive()) {
            return failed(statement, ElclMessage.of("ELC0106", command.name()));
        }
        for (Diagnostic diagnostic : Compiler.checkCommand(statement)) {
            if (diagnostic.isError()) {
                return failed(statement, diagnostic.message());
            }
        }
        RackPermission permission = permission(command.auth());
        if (permission != null && !context.allowed(permission)) {
            return failed(statement, ElclMessage.of("ELC0401", context.user(), permission.name()));
        }
        if (command.executor() == null) {
            return failed(statement, ElclMessage.of("ELC0107", command.name()));
        }
        Call call = new Call(command, statement, context);
        try {
            command.executor().run(call);
            return new Result(statement, call.messages, call.returns, null);
        } catch (ElclException e) {
            return new Result(statement, call.messages, call.returns, e.elclMessage());
        }
    }

    private static Result failed(@Nullable Stmt statement, ElclMessage escape) {
        return new Result(statement, List.of(), Map.of(), escape);
    }

    // The Firewall permission a command's Auth column stands for (configure is the Firewall's build).
    public static @Nullable RackPermission permission(CommandDefinition.Auth auth) {
        return switch (auth) {
            case NONE -> null;
            case VIEW -> RackPermission.VIEW;
            case INSERT -> RackPermission.INSERT;
            case EXTRACT -> RackPermission.EXTRACT;
            case CRAFT -> RackPermission.CRAFT;
            case CONFIGURE -> RackPermission.BUILD;
        };
    }

    // --- The invocation: literal values ---

    private static final class Call implements Invocation {
        private final CommandDefinition command;
        private final Stmt statement;
        private final ElclContext context;
        final List<ElclMessage> messages = new ArrayList<>();
        final Map<String, String> returns = new LinkedHashMap<>();

        Call(CommandDefinition command, Stmt statement, ElclContext context) {
            this.command = command;
            this.statement = statement;
            this.context = context;
        }

        @Override
        public CommandDefinition command() {
            return command;
        }

        @Override
        public boolean given(String keyword) {
            return statement.param(keyword) != null;
        }

        @Override
        public String text(String keyword) throws ElclException {
            List<String> values = list(keyword);
            return values.isEmpty() ? "" : values.getFirst();
        }

        @Override
        public long integer(String keyword) throws ElclException {
            String text = text(keyword);
            long value;
            try {
                value = new BigDecimal(text).longValueExact();
            } catch (NumberFormatException | ArithmeticException e) {
                throw new ElclException("ELC0003", text, "*INT");
            }
            ParamDef param = command.param(keyword);
            if (param != null && param.hasRange() && (value < param.min() || value > param.max())) {
                throw new ElclException("ELC0004", text);
            }
            return value;
        }

        // A value that fails (%SST out of range, %SIZE here...) is that escape message.
        @Override
        public List<String> list(String keyword) throws ElclException {
            Stmt.Param param = statement.param(keyword);
            if (param == null) {
                ParamDef def = command.param(keyword);
                return def != null && def.defaultValue() != null ? List.of(def.defaultValue()) : List.of();
            }
            List<String> values = new ArrayList<>();
            for (Expr value : param.values()) {
                values.add(evaluate(value));
            }
            return values;
        }

        @Override
        public void returns(String keyword, Object value) {
            returns.put(keyword.toUpperCase(Locale.ROOT), String.valueOf(value));
        }

        @Override
        public void send(ElclMessage message) {
            messages.add(message);
        }

        @Override
        public boolean interactive() {
            return true;
        }

        @Override
        public <T> @Nullable T context(Class<T> type) {
            return type.isInstance(context) ? type.cast(context) : null;
        }
    }

    // A value worked out from literals: text, numbers, names and special values (upper case), and the operators and
    // built-ins on them.
    static String evaluate(Expr expr) throws ElclException {
        return switch (expr) {
            case Expr.Str str -> str.value();
            case Expr.Num num -> num.text();
            case Expr.Name name -> name.name();
            case Expr.Special special -> special.name();
            case Expr.Path path -> path.toString();
            case Expr.Group group -> evaluate(group.inner());
            case Expr.Nested nested -> nested.command().toSource();
            case Expr.Var var -> throw new ElclException("ELC0002", var.name());
            case Expr.Unary unary -> unary.op().equals("-") ? number(unary.operand()).negate().toPlainString()
                    : flag(!truth(evaluate(unary.operand())));
            case Expr.Binary binary -> binary(binary);
            case Expr.Builtin builtin -> builtin(builtin);
        };
    }

    private static BigDecimal number(Expr expr) throws ElclException {
        String text = evaluate(expr).trim();
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            throw new ElclException("ELC0003", text, "*DEC");
        }
    }

    // Toward zero, as *INT takes it.
    private static BigDecimal whole(BigDecimal value) {
        return value.setScale(0, RoundingMode.DOWN);
    }

    private static boolean truth(String text) {
        return text.equals("1") || text.equalsIgnoreCase("*TRUE") || text.equalsIgnoreCase("*YES");
    }

    private static String flag(boolean value) {
        return value ? "1" : "0";
    }

    private static String binary(Expr.Binary binary) throws ElclException {
        switch (binary.op()) {
            case "+", "-", "*", "/", "//" -> {
                BigDecimal left = number(binary.left()), right = number(binary.right());
                BigDecimal result = switch (binary.op()) {
                    case "+" -> left.add(right);
                    case "-" -> left.subtract(right);
                    case "*" -> left.multiply(right);
                    default -> {
                        if ((binary.op().equals("//") ? whole(right) : right).signum() == 0) {
                            throw new ElclException("ELC0005");
                        }
                        yield binary.op().equals("//") ? whole(left).remainder(whole(right))
                                : left.scale() <= 0 && right.scale() <= 0 ? left.divide(right, 0, RoundingMode.DOWN) : left.divide(right, 10, RoundingMode.HALF_UP);
                    }
                };
                return result.stripTrailingZeros().toPlainString();
            }
            case "*CAT" -> {
                return evaluate(binary.left()) + evaluate(binary.right());
            }
            case "*BCAT" -> {
                return evaluate(binary.left()).stripTrailing() + " " + evaluate(binary.right());
            }
            case "*TCAT" -> {
                return evaluate(binary.left()).stripTrailing() + evaluate(binary.right());
            }
            case "*AND" -> {
                return flag(truth(evaluate(binary.left())) && truth(evaluate(binary.right())));
            }
            case "*OR" -> {
                return flag(truth(evaluate(binary.left())) || truth(evaluate(binary.right())));
            }
            default -> {
                String left = evaluate(binary.left()), right = evaluate(binary.right());
                int compare;
                try {
                    compare = new BigDecimal(left.trim()).compareTo(new BigDecimal(right.trim()));
                } catch (NumberFormatException e) {
                    compare = left.stripTrailing().compareTo(right.stripTrailing());
                }
                return flag(switch (binary.op()) {
                    case "*EQ" -> compare == 0;
                    case "*NE" -> compare != 0;
                    case "*GT" -> compare > 0;
                    case "*LT" -> compare < 0;
                    case "*GE" -> compare >= 0;
                    default -> compare <= 0;
                });
            }
        }
    }

    private static String builtin(Expr.Builtin builtin) throws ElclException {
        List<Expr> args = builtin.args();
        return switch (builtin.function()) {
            case "%TRIM" -> evaluate(args.getFirst()).strip();
            case "%TRIML" -> evaluate(args.getFirst()).stripLeading();
            case "%TRIMR" -> evaluate(args.getFirst()).stripTrailing();
            case "%UPPER" -> evaluate(args.getFirst()).toUpperCase(Locale.ROOT);
            case "%LOWER" -> evaluate(args.getFirst()).toLowerCase(Locale.ROOT);
            case "%LEN" -> Integer.toString(evaluate(args.getFirst()).stripTrailing().length());
            case "%CHAR" -> number(args.getFirst()).toPlainString();
            case "%DEC" -> {
                // LEN(15 5) unless given; a length alone has no decimals.
                int digits = args.size() > 1 ? number(args.get(1)).intValue() : 15, decimals = args.size() > 2 ? number(args.get(2)).intValue() : args.size() > 1 ? 0 : 5;
                BigDecimal value = number(args.getFirst()).setScale(decimals, RoundingMode.HALF_UP);
                if (value.signum() != 0 && value.precision() - value.scale() > digits - decimals) {
                    throw new ElclException("ELC0007");
                }
                yield value.toPlainString();
            }
            case "%INT" -> whole(number(args.getFirst())).toPlainString();
            case "%ABS" -> number(args.getFirst()).abs().toPlainString();
            case "%MIN" -> number(args.get(0)).min(number(args.get(1))).toPlainString();
            case "%MAX" -> number(args.get(0)).max(number(args.get(1))).toPlainString();
            case "%SCAN" -> {
                String find = evaluate(args.get(0)).stripTrailing(), in = evaluate(args.get(1));
                int start = args.size() > 2 ? number(args.get(2)).intValue() : 1;
                if (start < 1) {
                    throw new ElclException("ELC0004", start);
                }
                yield find.isEmpty() || start > in.length() ? "0" : Integer.toString(in.indexOf(find, start - 1) + 1);
            }
            case "%SST" -> {
                String text = evaluate(args.get(0));
                int start = number(args.get(1)).intValue(), length = number(args.get(2)).intValue();
                if (start < 1 || length < 0 || start - 1 + length > text.length()) {
                    throw new ElclException("ELC0004", start + " " + length);
                }
                yield text.substring(start - 1, start - 1 + length);
            }
            default -> throw new ElclException("ELC0001", builtin.function());
        };
    }
}
