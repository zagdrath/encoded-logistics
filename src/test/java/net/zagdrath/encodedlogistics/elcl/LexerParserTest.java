/*
 * Copyright (c) 2026 Zagdrath
 * SPDX-License-Identifier: MIT
 */

package net.zagdrath.encodedlogistics.elcl;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.zagdrath.encodedlogistics.elcl.cmd.CommandDefinition;
import net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry;
import net.zagdrath.encodedlogistics.elcl.cmd.ParamDef;
import net.zagdrath.encodedlogistics.elcl.compile.Compiler;
import net.zagdrath.encodedlogistics.elcl.lex.Lexer;
import net.zagdrath.encodedlogistics.elcl.lex.Token;
import net.zagdrath.encodedlogistics.elcl.parse.Expr;
import net.zagdrath.encodedlogistics.elcl.parse.Parser;
import net.zagdrath.encodedlogistics.elcl.parse.Stmt;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LexerParserTest {
    private static List<String> texts(List<Token> tokens) {
        return tokens.stream().filter(t -> t.kind() != Token.Kind.EOF).map(t -> t.kind() == Token.Kind.EOL ? "<EOL>" : t.toString()).toList();
    }

    @Test
    void caseCommentsAndStrings() {
        assertEquals(List.of("SNDMSG", "MSG", "(", "'Can''t find it'", ")", "<EOL>"), texts(Lexer.tokens("sndmsg msg('Can''t find it') /* c */")));
    }

    @Test
    void continuation() {
        List<Token> tokens = Lexer.tokens(List.of("SNDMSG MSG('a' +   /* more */", "       *BCAT 'b')", "RETURN"));
        assertEquals(List.of("SNDMSG", "MSG", "(", "'a'", "*BCAT", "'b'", ")", "<EOL>", "RETURN", "<EOL>"), texts(tokens));
        // Inside a string: + keeps the next line as is, - drops its leading blanks.
        assertEquals("ab  c", Lexer.tokens(List.of("X 'ab+", "  c'")).get(1).text());
        assertEquals("abc", Lexer.tokens(List.of("X 'ab-", "  c'")).get(1).text());
    }

    @Test
    void negativeNumbersAndSubtraction() {
        assertEquals(List.of("(", "&A", "-5", ")", "<EOL>"), texts(Lexer.tokens("(&A -5)")));
        assertEquals(List.of("&A", "-", "5", "<EOL>"), texts(Lexer.tokens("&A - 5")));
        assertEquals(List.of("&A", "-", "5", "<EOL>"), texts(Lexer.tokens("&A-5")));
    }

    @Test
    void qualifiedNamesAndDivision() {
        Stmt call = Parser.parseCommand("CALL PGM(ZAGLIB/RESTOCK)").statements().getFirst();
        assertInstanceOf(Expr.Path.class, call.value("PGM"));
        Parser.Result divide = Parser.parse(List.of("CHGVAR &X (&A / 2)"));
        assertInstanceOf(Expr.Binary.class, ((Expr.Group) divide.statements().getFirst().value("VALUE")).inner());
    }

    @Test
    void precedence() {
        Stmt s = Parser.parseCommand("IF COND(&A + 1 * 2 *GT 3 *AND *NOT &B) THEN(RETURN)").statements().getFirst();
        assertEquals("&A + 1 * 2 *GT 3 *AND *NOT &B", s.value("COND").toString());
        Expr.Binary and = (Expr.Binary) s.value("COND");
        assertEquals("*AND", and.op());
        assertEquals("*GT", ((Expr.Binary) and.left()).op());
    }

    @Test
    void positionalGoesToTheSchema() {
        Stmt s = Parser.parseCommand("STRCRAFT LOGIC_DIE 64 WAIT(*YES)").statements().getFirst();
        assertEquals("STRCRAFT ITEM(LOGIC_DIE) QTY(64) WAIT(*YES)", s.toSource());
    }

    @Test
    void elementGroupsInLists() {
        Stmt s = Parser.parseCommand("PLYMID SPK01 'song.mid' MAP((1 SPK01) (10 SPK04) (*ALL SPK02))").statements().getFirst();
        assertEquals(List.of("1", "SPK01", "10", "SPK04", "*ALL", "SPK02"), s.param("MAP").values().stream().map(Expr::toString).toList());
        // Read back without the brackets: the same values.
        Stmt again = Parser.parseCommand(s.toSource()).statements().getFirst();
        assertEquals(s.param("MAP").values().stream().map(Expr::toString).toList(), again.param("MAP").values().stream().map(Expr::toString).toList());
        assertTrue(Compiler.checkCommand(s).stream().noneMatch(Diagnostic::isError), () -> Compiler.checkCommand(s).toString());
        // A bracket round one value is still an expression.
        Stmt notes = Parser.parseCommand("PLYNOTE SPK01 NOTE((&A + 1) 5)").statements().getFirst();
        assertInstanceOf(Expr.Group.class, notes.param("NOTE").values().getFirst());
        assertEquals(2, notes.param("NOTE").values().size());
        // A group that isn't closed.
        assertTrue(!Parser.parseCommand("PLYMID SPK01 'a.mid' MAP((1 SPK01)").diagnostics().isEmpty());
    }

    @Test
    void labels() {
        Parser.Result result = Parser.parse(List.of("LOOP: DOWHILE COND(*TRUE)", "ALONE:", "LEAVE CMDLBL(LOOP)"));
        assertEquals("LOOP", result.statements().get(0).label());
        assertEquals("ALONE", result.statements().get(1).label());
    }

    @Test
    void strcraftSchema() {
        CommandDefinition strcraft = CommandRegistry.get("STRCRAFT");
        assertNotNull(strcraft);
        assertEquals("Start Crafting (STRCRAFT)", strcraft.title());
        assertEquals(2, strcraft.params().stream().filter(ParamDef::required).count());
        assertEquals(5, strcraft.params().stream().filter(p -> !p.required()).count());
        assertEquals(List.of("Name, F4 for list", "1-999999", "*ANY, name", "*FAIL, *PARTIAL", "*NO, *YES", "*CHAR variable", "*ITEM, *FLUID, *PRES"),
                strcraft.params().stream().map(ParamDef::hint).toList());
        assertEquals(List.of(24, 10, 10, 8, 4, 10, 6), strcraft.params().stream().map(p -> Math.min(24, p.length())).toList());
    }

    @Test
    void everyCommandsSchemaIsSound() {
        for (CommandDefinition command : CommandRegistry.all()) {
            assertTrue(command.name().length() <= 10, command.name());
            assertTrue(command.positional() <= command.params().size(), command.name());
            for (ParamDef param : command.params()) {
                if (param.defaultValue() != null && param.defaultValue().startsWith("*")) {
                    assertTrue(param.acceptsSpecial(param.defaultValue()), command.name() + " " + param.keyword());
                }
            }
        }
    }
}
