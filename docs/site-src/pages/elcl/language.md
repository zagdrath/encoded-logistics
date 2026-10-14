---
title: The ELCL language
nav: Language
section: elcl
order: 50
summary: ELCL's syntax, types, expressions, built-in functions, control flow, error handling and how programs run.
legacy: [language]
---
ELCL (Encoded Logistics Control Language) is a small, sandboxed control language modelled on midrange control
languages. A program is a list of commands with keyword parameters, between `PGM` and `ENDPGM`. You write it in a
source member of type `ELCLP` in the [source editor](page:elcl/libraries-and-editor#editor), compile it with
{{cmd CRTELPGM}} (or WRKMBR option 14), and run it with {{cmd CALL}}, {{cmd SBMJOB}}, a schedule entry or a trigger.

Every command a program can use has its own page in the [command reference](page:elcl/commands/index), with its
parameters, their defaults and the values they take. This page is about the language around them. For whole programs,
see {{page elcl/examples}}.

```elcl
/* HELLO - the smallest useful program */
PGM
  DCL VAR(&COUNT) TYPE(*INT)
  RTVITMCNT ITEM(IRON_INGOT) RTNCOUNT(&COUNT) NOTFND(*ZERO)
  SNDPGMMSG MSG('Iron ingots:' *BCAT %CHAR(&COUNT))
ENDPGM
```

## Lexical rules {#lexical legacy="lexical"}

- **Case**: ELCL doesn't care about case. Command names, keywords, variable names, labels, special values and built-in names
  are all folded to upper case. Text inside quotes keeps its case.

- **Comments**: `/* ... */`. A comment can span lines; comments don't nest.

- **Statements and continuation**: A statement ends at the end of its line, unless the line's last token is `+` or `-` (a comment after it is
  allowed): then the statement goes on with the next line. Inside a quoted string, `+` as the last character of a line
  carries the string on with the next line exactly as it is; `-` carries it on with the next line's leading blanks
  dropped.

- **Labels**: `NAME:` at the start of a statement, up to 10 characters (longer is {{msg ELC0001}}). A label can stand on a line of
  its own; it then belongs to the next statement.

- **Names**: Letters, digits, `_`, `@`, `#` and `$`, starting with a letter. Command names, labels and library, member and
  program names have up to 10 characters. A variable is `&` and a name, up to 32 characters in all.

- **Strings**: Single quotes, with `''` for a quote inside: `'Can''t find item'`. A string must close on its line (or be
  continued with `+` or `-`).

- **Numbers**: `42`, `-7`, `3.25`. A `-` straight before a digit, after a blank or `(` (or at the start of a statement), is a
  negative number: `PARM(&A -5)` is two values, while `&A - 5` and `&A-5` both subtract.

- **Special values**: `*` and a name: `*YES`, `*ALL`, `*HOT`. Which ones a parameter takes is part of the command (its page lists them).
  Inside a value, `*` straight before a letter is a special value or an operator word; `*` before a blank or a digit
  is multiplication. `*` followed by digits and a letter is one special value too, so the ranges `*1M`, `*10M`, `*1H`
  and `*1D` work while `&X*10` still multiplies.

- **Qualified names**: `LIB/NAME` for programs, members and files, for example `ELSYS/RESTOCK`. With no library (or `*LIBL`) the user's
  library list is searched.

- **Item IDs**: Quoted or not: `IRON_INGOT`, `minecraft:iron_ingot`, `'encodedlogistics:logic_die'`. An ID without a namespace is
  looked for in `minecraft:` first, then `encodedlogistics:`, then any mod that has it; if more than one mod does, it is
  {{msg ELC1205}}. Tags in filters start with `#`: `'#c:ingots'`. Commands that return items (such as
  {{cmd RTVITMLST}}) give a `minecraft:` item as its path in upper case (`IRON_INGOT`) and any other in full
  (`encodedlogistics:logic_die`).

- **Keyword and positional parameters**: A keyword is a name followed directly by `(`, with no blank: `LIB(ZAGLIB)`. Values without keywords go to the
  command's positional parameters in order, and only before the first keyword. The command pages list how many
  positional parameters each command has.

- **Lists of values**: A parameter that takes several values takes them separated by blanks: `DEV(LGT01 LGT02)`. Values that belong
  together can be grouped in brackets, as {{cmd PLYMID}}'s `MAP((1 SPK01) (10 SPK04))` does; the groups are read as if
  the brackets weren't there.

- **Characters you can type**: The terminal only takes printable ASCII, so the `¬` operator can't be typed in the editor; use `*NOT` (and `*NE` or
  `<>` for not-equal).

These two statements are the same command, written with keywords and positionally:

```elcl
DCL VAR(&L) TYPE(*INT)
RTVRSIN DEV(CTLIF01) SIDE(*NORTH) RTNLVL(&L)
RTVRSIN CTLIF01 *NORTH RTNLVL(&L)   /* positional values first, then keywords */
```

A long statement continued over lines, and a string continued with `-` (the second line's indent isn't part of it):

```elcl
DCL VAR(&MSG) TYPE(*CHAR) LEN(80)
CHGVAR VAR(&MSG) +
       VALUE('Storage is nearly full: move items to tape -
              or add a drive.')
SNDMSG MSG(&MSG) TOUSR(*SYSOPR)
```

## Program structure {#structure legacy="structure"}

```elcl
PGM PARM(&ITEM &MIN)            /* the parameter list is optional   */
  DCL VAR(&ITEM)  TYPE(*CHAR) LEN(64)
  DCL VAR(&MIN)   TYPE(*INT)
  DCL VAR(&COUNT) TYPE(*INT)    /* declarations come first          */
  MONMSG MSGID(ELC1201) EXEC(GOTO CMDLBL(NOITEM))  /* program-level */

  RTVITMCNT ITEM(&ITEM) RTNCOUNT(&COUNT)
  IF COND(&COUNT *LT &MIN) THEN(SNDMSG MSG('Low:' *BCAT &ITEM) TOUSR(*SYSOPR))
  RETURN

NOITEM:
  SNDMSG MSG('Item not found:' *BCAT &ITEM) TOUSR(*REQUESTER)
ENDPGM
```

- Every program starts with `PGM` and ends with `ENDPGM`. Anything after `ENDPGM` is {{msg ELC0001}}.
- `DCL` (and {{cmd DCLF}} for [database files](page:elcl/database-files)) come before any other command
  ({{msg ELC0016}} otherwise). The parameters named on `PGM PARM()` must be declared too.
- Program-level `MONMSG`s come straight after the declarations.
- Subroutines (`SUBR` ... `ENDSUBR`) go after the main body, before `ENDPGM`; nothing else may follow them.
- Source members have type `ELCLP` and compile to a `*PGM`.

## Grammar {#grammar legacy="grammar"}

In EBNF:

```
program     = "PGM" [ "PARM" "(" { variable } ")" ] NL
              { declaration NL }
              { monmsg NL }
              { statement NL }
              [ { subroutine NL } ]
              "ENDPGM" ;

declaration = "DCL" "VAR" "(" variable ")" "TYPE" "(" type ")"
              [ "LEN" "(" integer [ integer ] ")" ]
              [ "VALUE" "(" value ")" ]
              [ "RETAIN" "(" ( "*YES" | "*NO" ) ")" ]      (* PLC programs *)
            | "DCLF" "FILE" "(" qualified ")" [ "OPNID" "(" name ")" ] ;

type        = "*CHAR" | "*INT" | "*DEC" | "*LGL" | "*LIST" ;

statement   = [ label ":" ] command [ NL monmsg { NL monmsg } ] ;

command     = name { parameter } ;
parameter   = keyword "(" value { value } ")"   (* keyword form    *)
            | value ;                           (* positional form *)

value       = expression
            | special
            | qualified
            | "(" value { value } ")"           (* a group in a list parameter *)
            | command ;                         (* nested command, inside the parameter's
                                                   own brackets: THEN, EXEC, CMD *)

expression  = orExpr ;
orExpr      = andExpr { ("*OR" | "|") andExpr } ;
andExpr     = notExpr { ("*AND" | "&") notExpr } ;
notExpr     = ( "*NOT" | "¬" ) notExpr | relExpr ;
relExpr     = catExpr [ relop catExpr ] ;
relop       = "*EQ" | "=" | "*NE" | "<>" | "¬=" | "*GT" | ">" | "*LT" | "<"
            | "*GE" | ">=" | "*NL" | "*LE" | "<=" | "*NG" ;
catExpr     = addExpr { ("*CAT" | "||" | "*BCAT" | "|>" | "*TCAT" | "|<") addExpr } ;
addExpr     = mulExpr { ("+" | "-") mulExpr } ;
mulExpr     = unary { ("*" | "/" | "//") unary } ;   (* // = integer remainder *)
unary       = [ "-" ] primary ;
primary     = literal | variable | builtin | "(" expression ")" ;
builtin     = "%" name "(" [ expression { expression } ] ")" ;
```

- Whether a parameter takes an expression, a special value, a qualified name or a nested command is part of the
  command. The parser gives positional values to the command's positional parameters in order.
- A nested command goes inside the parameter's own brackets: `THEN(CHGVAR &Y 1)`, `EXEC(GOTO CMDLBL(DONE))`,
  `CMD(CALL PGM(ELSYS/RESTOCK))`. These can't be nested: `PGM`, `ENDPGM`, `DCL`, `DCLF`, `ENDDO`, `ENDFOR`, `SELECT`,
  `ENDSELECT`, `SUBR`, `ENDSUBR`, `ELSE`, `WHEN`, `OTHERWISE`, `MONMSG` ({{msg ELC0103}}).
- The command that {{cmd SBMJOB}} or {{cmd ADDJOBSCDE}} runs (`CMD()`) runs on its own, so it can't be a statement
  that only works inside a program, such as `CHGVAR`. Any variables in it are replaced by their values when the job
  is submitted.
- The compiler reports each statement's first problem and skips the rest of that statement: {{msg ELC0001}} (syntax),
  {{msg ELC0101}} (unknown command), {{msg ELC0104}} (a keyword given twice), then checks the rest of the program.
  Messages of severity 20 or more mean no program is made; a warning (severity 10, such as {{msg ELC0008}}) doesn't
  stop it.

## Variables and types {#types legacy="types"}

```elcl
DCL VAR(&NAME)  TYPE(*CHAR) LEN(64) VALUE('none')
DCL VAR(&COUNT) TYPE(*INT)  VALUE(0)
DCL VAR(&PCT)   TYPE(*DEC)  LEN(5 1)
DCL VAR(&DONE)  TYPE(*LGL)  VALUE('0')
DCL VAR(&ITEMS) TYPE(*LIST)
```

| Type | Holds | Notes |
|---|---|---|
| `*CHAR` | Fixed-length text | `LEN(n)`, 1 to 1,024, default 32. A value is padded with blanks or cut to the length. A literal that is too long is a compile warning ({{msg ELC0008}}); at run time cutting is never an error. |
| `*INT` | A 64-bit whole number | Item counts can be large (tapes hold billions). Past 64 bits is {{msg ELC0007}}. |
| `*DEC` | A fixed-point decimal | `LEN(digits decimals)`, digits 1 to 31; with no `LEN` it is `LEN(15 5)`, and `LEN(7)` alone has no decimals. Values round half up to the decimals when assigned; too many digits is {{msg ELC0007}}. |
| `*LGL` | A logical | `'1'` or `'0'`; `*TRUE` and `*FALSE` work too. |
| `*LIST` | An ordered list of strings | Grows as needed up to {{config elcl.maxListSize default}} elements ({{msg ELC0015}}). Changed with {{cmd ADDLSTE}}, {{cmd RMVLSTE}} and {{cmd CLRLST}}; read with `%SIZE`, `%ELEM` and `FOREACH`; filled by commands that return lists (`RTNLST`). A list can't have a `VALUE` or be assigned with `CHGVAR`. |

A variable starts blank (`*CHAR`), zero (`*INT`, `*DEC`), `'0'` (`*LGL`) or empty (`*LIST`) unless its `DCL` gives a
`VALUE`.

Conversions:

- `*INT` and `*DEC` convert to each other by themselves (a `*DEC` going into an `*INT` loses its decimals).
- A number becomes text only through `%CHAR`, a literal number too: `CHGVAR VAR(&TEXT) VALUE(%CHAR(&COUNT))`. (A
  parameter that takes text, such as `SNDMSG MSG(500)`, takes a literal number as it is.)
- Text becomes a number only through `%INT` or `%DEC`: `CHGVAR VAR(&COUNT) VALUE(%INT(&TEXT))`. Assigning text to a
  numeric variable directly is {{msg ELC0003}} when it compiles, and text that isn't a number is {{msg ELC0003}} when
  `%INT` or `%DEC` runs.
- Comparing text ignores trailing blanks.

Assign with {{cmd CHGVAR}}, by keyword or positionally:

```elcl
DCL VAR(&X) TYPE(*INT)
DCL VAR(&TEXT) TYPE(*CHAR) LEN(20)
CHGVAR VAR(&X) VALUE(&X + 1)
CHGVAR &X 10
CHGVAR VAR(&TEXT) VALUE('X is' *BCAT %CHAR(&X))
CHGVAR VAR(&X) VALUE(%INT('42') * 2)      /* text into a number goes through %INT */
```

## Expressions and operators {#expressions legacy="expressions"}

Precedence, highest first: unary minus; `* / //`; `+ -`; `*CAT *BCAT *TCAT`; the relational operators; `*NOT`;
`*AND`; `*OR`. Use brackets when in doubt.

| Word | Symbol | Meaning |
|---|---|---|
| | `+ - * /` | Arithmetic. `*INT / *INT` gives a whole number, cut towards zero; a `*DEC` on either side gives a `*DEC`. Dividing by zero is {{msg ELC0005}}. |
| | `//` | Remainder of whole numbers: `17 // 5` is 2. |
| `*CAT` | `\|\|` | Join as it is (trailing blanks kept). |
| `*BCAT` | `\|>` | Trim trailing blanks from the left side, add one blank, join. |
| `*TCAT` | `\|<` | Trim trailing blanks from the left side, join. |
| `*EQ` | `=` | Equal |
| `*NE` | `<>` (also `¬=`) | Not equal |
| `*GT` | `>` | Greater than |
| `*LT` | `<` | Less than |
| `*GE`, `*NL` | `>=` | Greater than or equal (not less) |
| `*LE`, `*NG` | `<=` | Less than or equal (not greater) |
| `*NOT` | `¬` | Logical not |
| `*AND` | `&` | Logical and (the `&` needs a blank after it, or it starts a variable name) |
| `*OR` | `\|` | Logical or |

Relational operators compare numbers with numbers and text with text; a logical compares with a logical or with
`'1'` / `'0'`. Mixing them (a number with text) is {{msg ELC0003}} when the compiler can see it. Joining a numeric or
logical variable to text is {{msg ELC0003}} too: put it through `%CHAR` first.

A `COND()` is a logical expression:

```elcl
DCL VAR(&COUNT) TYPE(*INT)
DCL VAR(&MIN)   TYPE(*INT) VALUE(64)
DCL VAR(&DONE)  TYPE(*LGL)
DCL VAR(&TYPE)  TYPE(*CHAR) LEN(10)
IF COND(&COUNT *LT &MIN) THEN(CHGVAR &DONE '1')
IF COND(&DONE) THEN(RETURN)
IF COND(*NOT &DONE *AND (&TYPE *EQ 'NAS' *OR &TYPE *EQ 'SAN')) THEN(RETURN)
IF COND(&COUNT // 2 = 0) THEN(SNDPGMMSG MSG('Even'))
```

## Built-in functions {#builtins legacy="builtins"}

Built-in functions start with `%`. Their arguments are separated by blanks, as everywhere in ELCL: `%SST(&S 1 3)`. An
argument can be any expression.

{{table builtins}}

An unknown built-in, or one with the wrong number of arguments, is {{msg ELC0001}}. On a command line, where there are
no variables, the text and number built-ins work, but `%SIZE`, `%ELEM` and `%NAME` don't ({{msg ELC0001}}).

```elcl
DCL VAR(&DATA) TYPE(*CHAR) LEN(256) VALUE('RTR01 NOFUEL 10 COAL')
DCL VAR(&DEV)  TYPE(*CHAR) LEN(10)
DCL VAR(&REST) TYPE(*CHAR) LEN(256)
DCL VAR(&AT)   TYPE(*INT)
CHGVAR VAR(&AT)   VALUE(%SCAN(' ' &DATA))               /* 6 */
CHGVAR VAR(&DEV)  VALUE(%SST(&DATA 1 &AT - 1))           /* RTR01 */
CHGVAR VAR(&REST) VALUE(%TRIM(%SST(&DATA &AT %LEN(&DATA) - &AT + 1)))
SNDPGMMSG MSG(%UPPER(%NAME('minecraft:coal')) *BCAT 'denied on' *BCAT &DEV)
```

## Control flow {#control-flow legacy="control-flow"}

`IF` runs one command, or a `DO` group, when its condition holds; `ELSE` straight after it runs one otherwise:

```elcl
DCL VAR(&A) TYPE(*INT)
DCL VAR(&Y) TYPE(*INT)
IF COND(&A *GT 10) THEN(DO)
  CHGVAR VAR(&Y) VALUE(1)
  SNDPGMMSG MSG('Big')
ENDDO
ELSE CMD(DO)
  CHGVAR VAR(&Y) VALUE(2)
ENDDO

IF COND(&A *EQ 0) THEN(CHGVAR &Y 1)
ELSE CMD(CHGVAR &Y 2)
```

Loops:

```elcl
DCL VAR(&I)    TYPE(*INT)
DCL VAR(&DONE) TYPE(*LGL)
DCL VAR(&E)    TYPE(*CHAR) LEN(64)
DCL VAR(&LIST) TYPE(*LIST)

DOWHILE COND(&I *LT 10)          /* while it holds; maybe never       */
  CHGVAR &I (&I + 1)
ENDDO

DOUNTIL COND(&DONE)              /* the body runs at least once       */
  CHGVAR &DONE '1'
ENDDO

DOFOR VAR(&I) FROM(10) TO(1) BY(-1)
  IF COND(&I *EQ 5) THEN(ITERATE)       /* on to the next pass        */
  ADDLSTE LIST(&LIST) VALUE(%CHAR(&I))
ENDDO

FOREACH VAR(&E) IN(&LIST)        /* each element in turn              */
  IF COND(&E *EQ '3') THEN(LEAVE)       /* out of the loop            */
ENDFOR
```

`SELECT` picks the first `WHEN` that holds, or the `OTHERWISE`:

```elcl
DCL VAR(&T) TYPE(*CHAR) LEN(16) VALUE('SAN')
SELECT
  WHEN COND(&T *EQ 'NAS') THEN(SNDPGMMSG MSG('A NAS'))
  WHEN COND(&T *EQ 'SAN') THEN(DO)
    SNDPGMMSG MSG('A SAN')
  ENDDO
  OTHERWISE CMD(SNDPGMMSG MSG('Something else'))
ENDSELECT
```

`GOTO` jumps to a label, and `RETURN` ends the program (back to its caller):

```elcl
DCL VAR(&N) TYPE(*INT)
AGAIN:
  CHGVAR &N (&N + 1)
  IF COND(&N *LT 3) THEN(GOTO CMDLBL(AGAIN))
  RETURN
```

The rules:

- `ELSE` must follow straight after its `IF` (or that `IF`'s `ENDDO`); anywhere else it is {{msg ELC0009}}.
- `THEN(DO)`, `CMD(DO)` and `EXEC(DO)` open a `DO` group, closed by `ENDDO`. `DO`, `DOWHILE`, `DOUNTIL` and `DOFOR`
  close with `ENDDO`, `FOREACH` with `ENDFOR`, `SELECT` with `ENDSELECT`. A block left open, or a closer with no
  opener, is {{msg ELC0009}}.
- `DOFOR`: `VAR` is an `*INT` variable; `FROM` and `TO` are needed, `BY` is 1 unless given and can be negative; `BY(0)`
  is {{msg ELC0004}}. The loop ends when the variable passes `TO`.
- `FOREACH`: `VAR` is a `*CHAR` variable, `IN` a `*LIST`.
- `LEAVE` and `ITERATE` must be inside a loop ({{msg ELC0009}} otherwise). They act on the innermost loop; give
  `CMDLBL` the label of an enclosing loop to leave or continue that one instead:
  `OUTER: DOFOR VAR(&I) FROM(1) TO(9)` ... `LEAVE CMDLBL(OUTER)`. A label that isn't an enclosing loop is
  {{msg ELC0010}}. A subroutine can't leave a loop of the main body.
- `WHEN` and `OTHERWISE` must be inside a `SELECT`, and only they may be: a `SELECT` holds `WHEN`s, then at most one
  `OTHERWISE`.
- A `GOTO`'s label must exist ({{msg ELC0010}}) and must not be inside a loop or `DO` group the `GOTO` is outside of
  ({{msg ELC0014}}). Jumping out of a block is fine.

```elcl
DCL VAR(&I) TYPE(*INT)
DCL VAR(&J) TYPE(*INT)
OUTER: DOFOR VAR(&I) FROM(1) TO(9)
  DOFOR VAR(&J) FROM(1) TO(9)
    IF COND(&I * &J *GT 20) THEN(LEAVE CMDLBL(OUTER))
    IF COND(&J *GT &I) THEN(ITERATE CMDLBL(OUTER))
  ENDDO
ENDDO
```

## Subroutines {#subroutines legacy="subroutines"}

```elcl
PGM
  DCL VAR(&RC)  TYPE(*INT)
  DCL VAR(&MSG) TYPE(*CHAR) LEN(64)

  CHGVAR VAR(&MSG) VALUE('First')
  CALLSUBR SUBR(REPORT) RTNVAL(&RC)
  CHGVAR VAR(&MSG) VALUE('Second')
  CALLSUBR SUBR(REPORT)

  SUBR SUBR(REPORT)
    PRTTXT TEXT(&MSG)
  ENDSUBR RTNVAL(0)
ENDPGM
```

- `SUBR` blocks go after the main body, before `ENDPGM`. Two with the same name are {{msg ELC0103}}.
- Subroutines share the program's variables. One can call another, up to 16 deep ({{msg ELC0011}}).
- `ENDSUBR RTNVAL()` takes a whole number or an expression (0 unless given); `CALLSUBR RTNVAL()` names an `*INT`
  variable that receives it.
- A `CALLSUBR` to a subroutine that doesn't exist is {{msg ELC0010}}.
- The main body ends where the first `SUBR` begins: the program ends there as if it reached a `RETURN`, so it never
  runs on into a subroutine.

## MONMSG and error handling {#errors legacy="errors"}

Every command that fails raises an *escape message*, with an ID from the [message reference](page:elcl/messages)
(`ELCnnnn`) and its message data. {{cmd MONMSG}} catches them.

- **Command level**: A `MONMSG` straight after a command applies to that command only. Several can follow one command, also after
  another `MONMSG`'s `EXEC(DO)` ... `ENDDO`.

- **Program level**: `MONMSG`s straight after the declarations apply to every command in the program. A `MONMSG` anywhere else that
  doesn't follow a command is {{msg ELC0009}}.

- **`MONMSG MSGID(id ...) [CMPDTA('text')] [EXEC(command)]`**: Up to 50 IDs. An ID ending in `00` catches its whole range: `ELC1200` catches `ELC1201` to `ELC1299`, and
  `ELC0000` catches everything. With no `EXEC`, the program carries on with the next command. `EXEC` can be `GOTO`,
  `DO` ... `ENDDO`, `RETURN`, `LEAVE`, `ITERATE` or any single command. The command-level monitors are tried first
  (the innermost), then the program-level ones.

- **{{cmd RCVMSG}}**: `RCVMSG MSGTYPE(*EXCP) RTNMSGID(&ID) RTNMSG(&TEXT) RTNMSGDTA(&DATA)` reads the last escape message caught;
  `MSGTYPE(*LAST)` reads the last message of any kind.

- **{{cmd SNDPGMMSG}}**: `SNDPGMMSG MSG('text') MSGTYPE(*ESCAPE)` raises an escape message of your own, ID `USR0001`, or the one you give
  with `MSGID(USRnnnn)` (three letters and four digits). Without `MSGTYPE(*ESCAPE)` it is an informational message to
  the job log (and, at a terminal, Command Entry).

An escape message nothing catches ends the program: the message goes to the job log and to the job user's message
queue, and an interactive job shows it on the message line. A program that called this one gets {{msg ELC0013}}
(called program ended abnormally), which it can catch in turn.

```elcl
DCL VAR(&ITEM)  TYPE(*CHAR) LEN(64) VALUE('LOGIC_DIE')
DCL VAR(&JOB)   TYPE(*CHAR) LEN(10)
DCL VAR(&ID)    TYPE(*CHAR) LEN(7)
DCL VAR(&TEXT)  TYPE(*CHAR) LEN(132)

STRCRAFT ITEM(&ITEM) QTY(16) RTNCRFJOB(&JOB)
MONMSG MSGID(ELC1400) EXEC(DO)          /* any crafting error, ELC1401-ELC1499 */
  RCVMSG RTNMSGID(&ID) RTNMSG(&TEXT)
  SNDMSG MSG('Cannot craft' *BCAT &ITEM *BCAT '-' *BCAT &ID *BCAT &TEXT) TOUSR(*SYSOPR)
  RETURN
ENDDO
MONMSG MSGID(ELC1200)                   /* storage errors: carry on       */
```

Raising and catching your own:

```elcl
DCL VAR(&FREE) TYPE(*INT)
DCL VAR(&USED) TYPE(*INT)
DCL VAR(&TOT)  TYPE(*INT)
MONMSG MSGID(USR0100) EXEC(GOTO CMDLBL(FULL))

RTVSTGSTS RTNUSED(&USED) RTNTOTAL(&TOT)
CHGVAR VAR(&FREE) VALUE(&TOT - &USED)
IF COND(&FREE *LT 1000) THEN(SNDPGMMSG MSG('Storage full') MSGID(USR0100) MSGTYPE(*ESCAPE))
RETURN

FULL:
  SNDMSG MSG('Less than 1000 items of room left') TOUSR(*SYSOPR)
```

## CALL and parameters {#calling legacy="calling"}

`CALL PGM(LIB/NAME) PARM(&A 'text' 5)` runs another compiled program and carries on when it returns.

- Variables are passed by reference: when the called program returns, the values of its parameters are copied back
  into the caller's variables. Literals and expressions are passed by value.
- The called program's `PGM PARM(...)` must have the same number of parameters ({{msg ELC0012}} otherwise). Each value
  is converted to the type the called program declares.
- A program with no library (or `*LIBL`) is looked for down the user's library list; none found is {{msg ELC0203}}.
- Calls nest up to 16 deep ({{msg ELC0011}}).
- A called program that ends on an escape message nothing caught gives the caller {{msg ELC0013}}.

```elcl
DCL VAR(&COUNT) TYPE(*INT)
CALL PGM(ELSYS/RESTOCK) PARM('IRON_INGOT' 500)
CALL PGM(COUNTER) PARM(&COUNT)        /* COUNTER can change &COUNT */
MONMSG MSGID(ELC0013 ELC0203)
```

## Running a program {#running legacy="running"}

A program always runs in a [job](page:elcl/jobs-and-scheduling#job-basics), a budget of VM instructions each server
tick at a time, so a long or endless program never holds up the server. There are three ways to start one:

- **On a command line: `CALL PGM(LIB/NAME) PARM(...)`**: The program runs in your session's interactive job, {{config elcl.interactiveBudget default}} instructions a tick.
  The command line answers at once with {{msg ELC0108}} and you can go on using the terminal. When the program ends,
  the messages it sent appear on Command Entry and the message line says how it ended: {{msg ELC0110}}, or the escape
  message that ended it. An interactive job runs one program at a time: a second `CALL` while one runs is
  {{msg ELC0109}}. Closing the terminal ends the program with its job, and so does {{cmd ENDJOB}}. The Sign On
  screen's Program/procedure field starts a program the same way.

- **As a batch job: `SBMJOB CMD(CALL PGM(LIB/NAME)) JOB(NAME)`**: The program runs on a job host, {{config elcl.batchBudget default}} instructions a tick, with nobody at a terminal,
  as the user who submitted it. See [jobs](page:elcl/jobs-and-scheduling#job-basics).

- **From a schedule entry or a trigger**: Each submits a batch job when it fires, as the user who added it. See
  [schedule entries](page:elcl/jobs-and-scheduling#schedules) and [triggers](page:elcl/jobs-and-scheduling#triggers).

Inside a program every command works as it does on a command line, with the same authority checks (the job user's
Firewall permissions; see [the permissions](page:admin/permissions)), plus the language statements. The command pages
say where each command runs:

- Commands marked *Programs only* (`CHGVAR`, `MONMSG`, `RCVMSG`, the control flow, the list and file commands) are
  {{msg ELC0106}} typed on a command line.
- Commands marked *Command line only* (the screen commands such as {{cmd WRKLIB}}) are {{msg ELC0105}} in a batch job.
  In a program called from a terminal they don't open their screen and answer {{msg ELC0107}}.
- `MOVITM TODEV(*DESK)` needs the Terminal Desk of an interactive job; in a batch job it is {{msg ELC1301}} naming
  `*DESK`.

A program compiled for a [PLC](page:elcl/control-and-plcs#plc) runs on the PLC itself instead, a scan at a time.

## RTN* return parameters {#return-params legacy="return-params"}

Parameters whose keyword starts with `RTN` return a value: you give them a variable, and the command sets it. The
compiler checks the variable is declared and of the right type: an `*INT` or `*DEC` for a number, a `*CHAR` for text,
a `*LIST` for a list.

```elcl
DCL VAR(&LVL)  TYPE(*INT)
DCL VAR(&USER) TYPE(*CHAR) LEN(10)
DCL VAR(&TYPE) TYPE(*CHAR) LEN(10)
DCL VAR(&DEVS) TYPE(*LIST)
RTVRSIN DEV(CTLIF01) SIDE(*NORTH) RTNLVL(&LVL)
RTVJOBA RTNUSR(&USER) RTNTYPE(&TYPE)
RTVDEVLST TYPE(CMPSRV) RTNLST(&DEVS)
```

On a command line there are no variables, so you leave the `RTN*` parameters out (even required ones), and Command
Entry shows what they would have returned:

```elcl nocompile="typed at the ===> prompt, where RTN* parameters are left out"
RTVRSIN CTLIF01 *MAX
```

shows `RTNLVL = 9`.

## Limits and runtime model {#limits legacy="limits"}

- Programs are compiled before they run; source doesn't run directly. A program runs the source it was compiled from,
  whatever has happened to the member since.
- Limits: {{config elcl.maxSourceLines default}} lines a source member; {{config elcl.maxListSize default}} list
  elements ({{msg ELC0015}}); `*CHAR` length 1,024; `*DEC` 31 digits; call depth 16 ({{msg ELC0011}}), and subroutine
  depth 16; 50 message IDs on one `MONMSG`; 5 files declared with `DCLF`.
- Instruction budget: each tick, each running job runs at most its budget of VM instructions, then waits for the next
  tick: {{config elcl.interactiveBudget default}} for an interactive job, {{config elcl.batchBudget default}} for each
  batch job, and at most {{config elcl.globalBudget default}} a tick across every job on the server. Jobs take turns
  going first, so the global budget is shared out fairly. Work with Active Jobs shows what each job used.
- Waits: some commands put the job into a wait (status `*WAIT`) that costs no budget, and the command carries on when
  the wait is over:
  - `DLYJOB DLY(n)`: *n* seconds of game time (20 ticks each, so it stretches if the server lags), 1 to 86,400.
  - `DLYJOB RSMTIME(HHMMSS)`: until the game clock next comes round to that time (the game day starts at 06:00).
  - `DLYTICK TICKS(n)`: *n* game ticks, 1 to 1,200.
  - {{cmd MOVITM}} of items that are partly on tape: it recalls them, sends {{msg ELC1203}} and waits for them.
  - `STRCRAFT WAIT(*YES)`: until its crafting job has ended.
- On a command line none of these waits: `DLYJOB` and `DLYTICK` complete at once, and `MOVITM` and `STRCRAFT` start
  the recall or the craft and say so.
- Runtime errors are escape messages like any other and can be caught: a list index out of range ({{msg ELC0006}}),
  division by zero ({{msg ELC0005}}), overflow ({{msg ELC0007}}), a value of the wrong type ({{msg ELC0003}}), a value
  out of range ({{msg ELC0004}}, such as `%SST` past the end), the list limit ({{msg ELC0015}}), call depth
  ({{msg ELC0011}}), the parameter count ({{msg ELC0012}}) and a called program's failure ({{msg ELC0013}}).
- What is saved: a batch job's whole VM state (each program's place, variables, subroutine stack, loop counters,
  monitors, last escape message, the call stack and the wait it is in) is saved with the system. A running batch job
  still ends when its host goes down or the server stops (see [jobs](page:elcl/jobs-and-scheduling#job-basics));
  interactive jobs aren't saved at all.
