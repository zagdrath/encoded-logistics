/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl.parse;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import net.zagdrath.encodedlogistics.elcl.Diagnostic;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef;
import net.zagdrath.encodedlogistics.elcl.lex.Lexer;
import net.zagdrath.encodedlogistics.elcl.lex.Token;
import net.zagdrath.encodedlogistics.elcl.lex.Token.Kind;

// Tokens to statements (ELCL_SPEC.md 3), with the command registry's schemas: a keyword is a name straight before
// "(" (no blank); values without keywords go to the schema's positional parameters in order, and only before the
// first keyword. A COMMAND parameter's parentheses hold a nested command, parsed against its own schema. Each
// statement's first problem is a diagnostic (ELC0001 syntax, ELC0101 unknown command, ELC0104 a keyword twice); the
// rest of that statement is skipped and parsing goes on with the next one.
public final class Parser {
    public record Result(List<Stmt> statements, List<Diagnostic> diagnostics) {
        public boolean ok() {
            return diagnostics.isEmpty();
        }
    }

    private static final class SyntaxError extends RuntimeException {
        final Diagnostic diagnostic;

        SyntaxError(Diagnostic diagnostic) {
            super(null, null, false, false);
            this.diagnostic = diagnostic;
        }
    }

    // Operators' *WORD forms, and how tightly each binds (higher first).
    private static final Map<String, String> SYMBOLS = Map.ofEntries(Map.entry("=", "*EQ"), Map.entry("<>", "*NE"), Map.entry(">", "*GT"),
            Map.entry("<", "*LT"), Map.entry(">=", "*GE"), Map.entry("<=", "*LE"), Map.entry("||", "*CAT"), Map.entry("|>", "*BCAT"),
            Map.entry("|<", "*TCAT"), Map.entry("&", "*AND"), Map.entry("|", "*OR"), Map.entry("¬", "*NOT"), Map.entry("*NG", "*LE"),
            Map.entry("*NL", "*GE"));
    private static final Set<String> RELATIONAL = Set.of("*EQ", "*NE", "*GT", "*LT", "*GE", "*LE");
    private static final Set<String> CONCAT = Set.of("*CAT", "*BCAT", "*TCAT");
    public static final Set<String> OPERATOR_WORDS = Set.of("*EQ", "*NE", "*GT", "*LT", "*GE", "*LE", "*NG", "*NL", "*CAT", "*BCAT", "*TCAT", "*AND",
            "*OR", "*NOT");

    private final List<Token> tokens;
    private int pos;
    private final List<Diagnostic> diagnostics = new ArrayList<>();

    private Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    // A whole member: every statement.
    public static Result parse(List<String> lines) {
        Parser parser = new Parser(Lexer.tokens(lines));
        List<Stmt> statements = new ArrayList<>();
        // Labels alone on their lines, waiting for the statement they belong to.
        List<String> labels = new ArrayList<>();
        int labelLine = 0;
        while (parser.peek().kind() != Kind.EOF) {
            if (parser.peek().kind() == Kind.EOL) {
                parser.pos++;
                continue;
            }
            // A label alone on its line belongs to the next statement.
            if (parser.labelOnly()) {
                Token name = parser.next();
                parser.next();
                if (labels.isEmpty()) {
                    labelLine = name.line();
                }
                labels.add(name.text());
                parser.checkLabel(name);
                continue;
            }
            Stmt statement = parser.statement();
            if (!labels.isEmpty()) {
                // The first is its label (unless it has its own); the rest are aliases.
                if (statement.label() != null) {
                    labels.addFirst(statement.label());
                }
                statement = new Stmt(labels.getFirst(), statement.name(), statement.params(), labelLine, statement.lastLine(), statement.definition(),
                        statement.broken(), List.copyOf(labels.subList(1, labels.size())));
                labels.clear();
            }
            statements.add(statement);
        }
        return new Result(statements, parser.diagnostics);
    }

    // One command (a command line, the prompter's input): null when there's none; diagnostics as for a member.
    public static Result parseCommand(String text) {
        return parse(List.of(text));
    }

    private Token peek() {
        return tokens.get(pos);
    }

    private Token peek(int ahead) {
        return tokens.get(Math.min(tokens.size() - 1, pos + ahead));
    }

    private Token next() {
        Token token = tokens.get(pos);
        if (token.kind() != Kind.EOF) {
            pos++;
        }
        return token;
    }

    private static String shown(Token token) {
        return token.kind() == Kind.EOL || token.kind() == Kind.EOF ? "end of statement" : token.toString();
    }

    private SyntaxError error(Token token) {
        return new SyntaxError(Diagnostic.of(token.line(), "ELC0001", shown(token)));
    }

    private boolean labelOnly() {
        return peek().kind() == Kind.NAME && peek(1).kind() == Kind.COLON && !peek(1).spaced() && peek(2).kind() == Kind.EOL;
    }

    private void checkLabel(Token name) {
        if (name.text().length() > 10) {
            diagnostics.add(Diagnostic.of(name.line(), "ELC0001", name.text()));
        }
    }

    private void skipStatement() {
        while (peek().kind() != Kind.EOL && peek().kind() != Kind.EOF) {
            pos++;
        }
    }

    private Stmt statement() {
        Token first = peek();
        String label = null;
        if (first.kind() == Kind.NAME && peek(1).kind() == Kind.COLON && !peek(1).spaced()) {
            label = next().text();
            next();
            checkLabel(first);
        }
        Token name = peek();
        if (name.kind() != Kind.NAME) {
            diagnostics.add(Diagnostic.of(name.line(), "ELC0001", shown(name)));
            skipStatement();
            return new Stmt(label, "", List.of(), first.line(), name.line(), null, true);
        }
        List<Stmt.Param> params = new ArrayList<>();
        CommandDefinition definition = CommandRegistry.get(name.text());
        try {
            next();
            if (definition == null || name.text().length() > 10) {
                throw new SyntaxError(Diagnostic.of(name.line(), "ELC0101", name.text()));
            }
            params(definition, params, false);
            return new Stmt(label, name.text(), List.copyOf(params), first.line(), lastLine(), definition, false);
        } catch (SyntaxError e) {
            diagnostics.add(e.diagnostic);
            skipStatement();
            return new Stmt(label, name.text(), List.copyOf(params), first.line(), lastLine(), definition, true);
        }
    }

    // The line of the last token read (the statement's last line).
    private int lastLine() {
        for (int i = pos - 1; i >= 0; i--) {
            if (tokens.get(i).kind() != Kind.EOL) {
                return tokens.get(i).line();
            }
        }
        return 0;
    }

    // A command's parameters, up to the end of the statement (or, nested, its closing parenthesis).
    private void params(CommandDefinition definition, List<Stmt.Param> params, boolean nested) {
        Set<String> seen = new HashSet<>();
        int positional = 0;
        boolean keywords = false;
        while (true) {
            Token token = peek();
            if (token.kind() == Kind.EOL || token.kind() == Kind.EOF || nested && token.kind() == Kind.RPAREN) {
                if (nested && token.kind() != Kind.RPAREN) {
                    throw error(token);
                }
                return;
            }
            if (token.kind() == Kind.NAME && peek(1).kind() == Kind.LPAREN && !peek(1).spaced()) {
                // Keyword form.
                ParamDef param = definition.param(token.text());
                if (param == null) {
                    throw error(token);
                }
                if (!seen.add(param.keyword())) {
                    throw new SyntaxError(Diagnostic.of(token.line(), "ELC0104", param.keyword()));
                }
                next();
                next();
                List<Expr> values = values(param);
                if (next().kind() != Kind.RPAREN) {
                    throw error(tokens.get(pos - 1));
                }
                params.add(new Stmt.Param(param.keyword(), List.copyOf(values), token.line()));
                keywords = true;
                continue;
            }
            // Positional form: before any keyword, in the schema's order.
            ParamDef param = definition.positional(positional++);
            if (keywords || param == null) {
                throw error(token);
            }
            if (!seen.add(param.keyword())) {
                throw new SyntaxError(Diagnostic.of(token.line(), "ELC0104", param.keyword()));
            }
            Expr value;
            if (param.kind() == ParamDef.Kind.COMMAND) {
                if (next().kind() != Kind.LPAREN) {
                    throw error(token);
                }
                value = nested(token.line());
                if (next().kind() != Kind.RPAREN) {
                    throw error(tokens.get(pos - 1));
                }
            } else {
                value = expression();
            }
            params.add(new Stmt.Param(param.keyword(), List.of(value), token.line()));
        }
    }

    // A parameter's values up to its ")". A list's values may come in element groups - MAP((1 SPK01) (10 SPK04)) - each
    // group's values taken in turn, as if listed without the brackets; a bracket round one value is an expression.
    private List<Expr> values(ParamDef param) {
        List<Expr> values = new ArrayList<>();
        if (param.kind() == ParamDef.Kind.COMMAND) {
            if (peek().kind() != Kind.RPAREN) {
                values.add(nested(peek().line()));
            }
            return values;
        }
        while (peek().kind() != Kind.RPAREN) {
            if (peek().kind() == Kind.EOL || peek().kind() == Kind.EOF) {
                throw error(peek());
            }
            if (param.isList() && peek().kind() == Kind.LPAREN && group(values)) {
                continue;
            }
            values.add(expression());
        }
        return values;
    }

    // An element group at "(": its values added, true; or, with only one value in it, nothing read and false.
    private boolean group(List<Expr> values) {
        int from = pos;
        next();
        List<Expr> group = new ArrayList<>();
        group.add(expression());
        if (peek().kind() == Kind.RPAREN) {
            pos = from;
            return false;
        }
        while (peek().kind() != Kind.RPAREN) {
            if (peek().kind() == Kind.EOL || peek().kind() == Kind.EOF) {
                throw error(peek());
            }
            group.add(expression());
        }
        next();
        values.addAll(group);
        return true;
    }

    // A nested command inside THEN() / EXEC() / CMD().
    private Expr nested(int line) {
        Token name = peek();
        if (name.kind() != Kind.NAME) {
            throw error(name);
        }
        CommandDefinition definition = CommandRegistry.get(name.text());
        if (definition == null) {
            throw new SyntaxError(Diagnostic.of(name.line(), "ELC0101", name.text()));
        }
        next();
        List<Stmt.Param> params = new ArrayList<>();
        params(definition, params, true);
        return new Expr.Nested(new Stmt(null, name.text(), List.copyOf(params), name.line(), lastLine(), definition, false), line);
    }

    // --- Expressions, loosest first ---

    private @Nullable String operator(Token token) {
        if (token.kind() == Kind.OPERATOR) {
            return SYMBOLS.getOrDefault(token.text(), token.text());
        }
        if (token.kind() == Kind.SLASH) {
            return "/";
        }
        if (token.kind() == Kind.SPECIAL && OPERATOR_WORDS.contains(token.text())) {
            return SYMBOLS.getOrDefault(token.text(), token.text());
        }
        return null;
    }

    private boolean at(Set<String> ops) {
        String op = operator(peek());
        return op != null && ops.contains(op);
    }

    private Expr expression() {
        Expr left = and();
        while (at(Set.of("*OR"))) {
            Token op = next();
            left = new Expr.Binary("*OR", left, and(), op.line());
        }
        return left;
    }

    private Expr and() {
        Expr left = not();
        while (at(Set.of("*AND"))) {
            Token op = next();
            left = new Expr.Binary("*AND", left, not(), op.line());
        }
        return left;
    }

    private Expr not() {
        if (at(Set.of("*NOT"))) {
            Token op = next();
            return new Expr.Unary("*NOT", not(), op.line());
        }
        return relational();
    }

    private Expr relational() {
        Expr left = concat();
        if (at(RELATIONAL)) {
            Token op = next();
            left = new Expr.Binary(operator(op), left, concat(), op.line());
        }
        return left;
    }

    private Expr concat() {
        Expr left = additive();
        while (at(CONCAT)) {
            Token op = next();
            left = new Expr.Binary(operator(op), left, additive(), op.line());
        }
        return left;
    }

    private Expr additive() {
        Expr left = multiplicative();
        while (at(Set.of("+", "-"))) {
            Token op = next();
            left = new Expr.Binary(op.text(), left, multiplicative(), op.line());
        }
        return left;
    }

    private Expr multiplicative() {
        Expr left = unary();
        while (at(Set.of("*", "/", "//"))) {
            Token op = next();
            left = new Expr.Binary(operator(op), left, unary(), op.line());
        }
        return left;
    }

    private Expr unary() {
        if (peek().is(Kind.OPERATOR, "-")) {
            Token op = next();
            return new Expr.Unary("-", unary(), op.line());
        }
        return primary();
    }

    private Expr primary() {
        Token token = peek();
        switch (token.kind()) {
            case STRING -> {
                next();
                return new Expr.Str(token.text(), token.line());
            }
            case VARIABLE -> {
                next();
                if (token.text().length() > 32) {
                    throw error(token);
                }
                return new Expr.Var(token.text(), token.line());
            }
            case NUMBER, NAME -> {
                return name();
            }
            case SPECIAL -> {
                if (OPERATOR_WORDS.contains(token.text())) {
                    throw error(token);
                }
                return name();
            }
            case BUILTIN -> {
                next();
                if (next().kind() != Kind.LPAREN) {
                    throw error(token);
                }
                List<Expr> args = new ArrayList<>();
                while (peek().kind() != Kind.RPAREN) {
                    if (peek().kind() == Kind.EOL || peek().kind() == Kind.EOF) {
                        throw error(peek());
                    }
                    args.add(expression());
                }
                next();
                return new Expr.Builtin(token.text(), List.copyOf(args), token.line());
            }
            case LPAREN -> {
                next();
                Expr inner = expression();
                if (next().kind() != Kind.RPAREN) {
                    throw error(tokens.get(pos - 1));
                }
                return new Expr.Group(inner, token.line());
            }
            default -> throw error(token);
        }
    }

    // A name, number or special value, joined to what follows by "/" (LIB/NAME, 000123/USER/JOB) or ":" (an item's
    // namespace) when nothing comes between them.
    private Expr name() {
        Token first = next();
        List<String> parts = new ArrayList<>();
        parts.add(first.text());
        if (first.kind() == Kind.NAME && peek().kind() == Kind.COLON && !peek().spaced() && peek(1).kind() == Kind.NAME && !peek(1).spaced()) {
            next();
            return new Expr.Name(first.text() + ":" + next().text(), first.line());
        }
        while (peek().kind() == Kind.SLASH && !peek().spaced() && peek(1).kind() == Kind.NAME && !peek(1).spaced()) {
            next();
            parts.add(next().text());
        }
        if (parts.size() > 1) {
            return new Expr.Path(List.copyOf(parts), first.line());
        }
        return switch (first.kind()) {
            case NUMBER -> {
                try {
                    yield new Expr.Num(new BigDecimal(first.text()), first.text(), first.line());
                } catch (NumberFormatException e) {
                    throw error(first);
                }
            }
            case SPECIAL -> new Expr.Special(first.text(), first.line());
            default -> new Expr.Name(first.text(), first.line());
        };
    }
}
