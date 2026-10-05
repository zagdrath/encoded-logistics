# ELCL — Encoded Logistics Control Language: Language Specification

ELCL is a small, sandboxed control language modelled on midrange control
languages. Programs are made of commands with keyword parameters. Mod
extensions are marked **[EXT]**.

## 1. Program structure

```
PGM PARM(&ITEM &MIN)            /* optional parameter list          */
  DCL VAR(&COUNT) TYPE(*INT)    /* declarations come first          */
  MONMSG MSGID(ELC1201) EXEC(GOTO CMDLBL(NOITEM))  /* program-level */
  ...commands...
NOITEM:
  SNDMSG MSG('Item not found') TOUSR(*REQUESTER)
ENDPGM
```

- Every program starts with `PGM` and ends with `ENDPGM`.
- `DCL` statements must come before any other command. **[EXT]** So must `DCLF`,
  which declares a variable for each field of a database file (§11).
- Program-level `MONMSG` statements come directly after the `DCL`s.
- Source members have type `ELCLP` and compile to a `*PGM` object.

## 2. Lexical rules

- **Case-insensitive.** Keywords, command names, variable names and special
  values are folded to upper case. String literal contents keep their case.
- **Comments:** `/* ... */`, may span lines, do not nest.
- **Continuation:** a line ending with `+` continues on the next line. A `+`
  inside a string literal at line end continues the string with no space
  (`-` continues and drops leading blanks on the next line, as in real CL).
- **Statement terminator:** end of line (after continuation is resolved).
- **Labels:** `NAME:` at the start of a statement. Up to 10 characters.
- **Names:** letters, digits, `_`, `@`, `#`, `$`; must start with a letter.
  - Command names, labels, library/member/program names: up to 10 characters.
  - Variables: `&` followed by a name, up to 32 characters total.
- **String literals:** single quotes, `''` for an embedded quote:
  `'Can''t find item'`.
- **Numbers:** `42`, `-7`, `3.25`. Underscores not allowed.
- **Special values:** `*` followed by a name, e.g. `*YES`, `*ALL`, `*HOT`.
  Which special values are valid depends on the parameter (COMMANDS.md).
- **Qualified names:** `LIB/NAME` for programs and members, e.g.
  `ELGPL/RESTOCK`. `*LIBL` means search the user's library list.
- **Item IDs:** a quoted or unquoted ID: `IRON_INGOT`, `'minecraft:iron_ingot'`,
  `'encodedlogistics:logic_die'`. An unqualified ID resolves first to
  `minecraft:`, then to `encodedlogistics:`, then to any unique match; if
  more than one mod has a match, `ELC1205` (ambiguous item). Tags in filters
  use `#`: `'#c:ingots'`.

## 3. Grammar (EBNF)

```
program     = "PGM" [ "PARM" "(" { variable } ")" ] NL
              { declaration NL }
              { monmsg NL }
              { statement NL }
              "ENDPGM" ;

declaration = "DCL" "VAR" "(" variable ")" "TYPE" "(" type ")"
              [ "LEN" "(" integer [ integer ] ")" ]
              [ "VALUE" "(" value ")" ]
            | "DCLF" "FILE" "(" qualified ")" [ "OPNID" "(" name ")" ] ;   (* §11 *)

type        = "*CHAR" | "*INT" | "*DEC" | "*LGL" | "*LIST" ;

statement   = [ label ":" ] command [ NL monmsg { NL monmsg } ] ;

command     = name { parameter } ;
parameter   = keyword "(" value { value } ")"   (* keyword form   *)
            | value ;                           (* positional form *)

value       = expression
            | special
            | qualified
            | "(" command ")"                   (* nested command, only in
                                                   THEN, EXEC, CMD, ELSE CMD *)
            ;

expression  = orExpr ;
orExpr      = andExpr { ("*OR" | "|") andExpr } ;
andExpr     = notExpr { ("*AND" | "&") notExpr } ;
notExpr     = [ "*NOT" | "¬" ] relExpr ;
relExpr     = catExpr [ relop catExpr ] ;
relop       = "*EQ" | "=" | "*NE" | "<>" | "*GT" | ">" | "*LT" | "<"
            | "*GE" | ">=" | "*LE" | "<=" ;
catExpr     = addExpr { ("*CAT" | "||" | "*BCAT" | "|>" | "*TCAT" | "|<") addExpr } ;
addExpr     = mulExpr { ("+" | "-") mulExpr } ;
mulExpr     = unary { ("*" | "/" | "//") unary } ;   (* // = integer remainder *)
unary       = [ "-" ] primary ;
primary     = literal | variable | builtin | "(" expression ")" ;
builtin     = "%" name "(" [ expression { expression } ] ")" ;
```

Notes:
- Whether a parameter accepts an expression, a special value, a qualified
  name or a nested command is defined by its schema in COMMANDS.md. The
  parser resolves positional parameters using the schema's positional order.
- Inside parameter values, `*` immediately followed by a letter is a special
  value or operator; `*` followed by a space or digit is multiplication.

## 4. Types

| Type    | Meaning | Notes |
|---------|---------|-------|
| `*CHAR` | Fixed-length string | `LEN(n)`, 1–1024, default 32. Assigned values are padded with blanks or truncated (truncation is a compile warning when detectable, never a runtime error). |
| `*INT`  | 64-bit signed integer | Item counts can exceed 2³¹ (LTO-10 tapes). Overflow → `ELC0007`. |
| `*DEC`  | Fixed-point decimal | `LEN(total decimals)`, default `LEN(15 5)`. Rounds half-up on assignment. |
| `*LGL`  | Logical | Literal values `'1'` / `'0'`. **[EXT]** `*TRUE` / `*FALSE` also accepted. |
| `*LIST` | **[EXT]** Ordered list of strings | Grows as needed up to the list limit (§9). |

Conversions: `*INT` ↔ `*DEC` implicit. Numbers → `*CHAR` only via `%CHAR`.
`*CHAR` → numbers only via `%INT` / `%DEC` (invalid text → `ELC0003`).
Relational comparison of `*CHAR` values ignores trailing blanks.

## 5. Expressions and built-in functions

Precedence (highest first): unary minus; `* / //`; `+ -`; `*CAT *BCAT *TCAT`;
relational; `*NOT`; `*AND`; `*OR`.

- `*CAT` joins as-is; `*BCAT` trims trailing blanks from the left operand and
  adds one blank; `*TCAT` trims trailing blanks from the left operand.
- Division of `*INT` by `*INT` truncates toward zero. Divide by zero → `ELC0005`.

Built-ins:

| Function | Returns |
|----------|---------|
| `%SST(s start len)` | Substring (1-based). Out of range → `ELC0004`. |
| `%TRIM(s)`, `%TRIML(s)`, `%TRIMR(s)` | Trimmed string |
| `%LEN(s)` | Length after trailing blanks are trimmed |
| `%UPPER(s)`, `%LOWER(s)` | Case-converted string |
| `%SCAN(find s [start])` | 1-based position or 0 |
| `%CHAR(n)` | Number as text |
| `%INT(x)`, `%DEC(x [len dec])` | Conversion |
| `%ABS(n)`, `%MIN(a b)`, `%MAX(a b)` | Numeric helpers |
| `%SIZE(list)` **[EXT]** | Number of elements |
| `%ELEM(list n)` **[EXT]** | Element n (1-based). Out of range → `ELC0006`. |
| `%NAME(item)` **[EXT]** | Display name of an item ID |

List mutation uses commands: `ADDLSTE`, `RMVLSTE`, `CLRLST` (COMMANDS.md §2).

## 6. Control flow

```
IF COND(&A *GT 10) THEN(DO)
  ...
ENDDO
ELSE CMD(DO)
  ...
ENDDO

IF COND(&X *EQ 0) THEN(CHGVAR &Y 1)
ELSE CMD(CHGVAR &Y 2)

DOWHILE COND(&I *LT 10)  ... ENDDO
DOUNTIL COND(&DONE)      ... ENDDO      /* body runs at least once */
DOFOR VAR(&I) FROM(1) TO(10) BY(1)  ... ENDDO
FOREACH VAR(&E) IN(&LIST)  ... ENDFOR    /* [EXT] */
LEAVE [CMDLBL(label)]      /* exit innermost or labelled loop */
ITERATE [CMDLBL(label)]    /* next iteration */

SELECT
  WHEN COND(&T *EQ 'NAS') THEN(...)
  WHEN COND(&T *EQ 'SAN') THEN(...)
  OTHERWISE CMD(...)
ENDSELECT

GOTO CMDLBL(label)         /* only to labels in the same program, not
                              into a loop or DO group from outside      */
RETURN                     /* end this program, back to caller          */

SUBR SUBR(NAME) ... ENDSUBR [RTNVAL(&I)]
CALLSUBR SUBR(NAME) [RTNVAL(&I)]
```

- `ELSE` must immediately follow the `IF` it belongs to (or its `ENDDO`).
- `SUBR` blocks go after the main body, before `ENDPGM`. Subroutines share
  the program's variables. Max subroutine nesting: 16.

## 7. Calling programs

`CALL PGM(LIB/NAME) PARM(&A 'text' 5)` — parameters are passed **by
reference** for variables and by value for literals/expressions, as in real
CL. The called program's `PGM PARM(...)` must have the same number of
parameters (`ELC0012` otherwise). Max call depth: 16 (`ELC0011`).

## 8. Error handling

Every failing command raises an **escape message** with an ID from
MESSAGES.md (`ELCnnnn`) and message data.

- **Command-level:** a `MONMSG` directly after a command applies to that
  command only.
- **Program-level:** `MONMSG` statements after the `DCL`s apply to every
  command in the program.
- `MONMSG MSGID(id ...) [CMPDTA('text')] [EXEC(command)]`
  - Up to 50 IDs. `ELC1200` (ending in `00`) monitors the whole `ELC12xx`
    range; `ELC0000` monitors everything.
  - With no `EXEC`, execution continues with the next command.
  - `EXEC` may be `GOTO`, `DO`...`ENDDO`, `RETURN`, or any single command.
- `RCVMSG MSGTYPE(*EXCP) RTNMSGID(&ID) RTNMSG(&TEXT)` reads the last escape
  message after it was monitored.
- `SNDPGMMSG MSG('text') MSGTYPE(*ESCAPE)` raises your own escape message
  with ID `USR0001` (or `MSGID(USRnnnn)` to choose one).
- **Unmonitored escape message:** the program ends, the message is written to
  the job log and sent to the job user's message queue. Interactive jobs show
  it on the message line. The caller sees `ELC0013` (called program failed),
  which it may monitor.

## 9. Runtime model

- Programs are compiled before running. Running source directly is not
  supported (keeps the VM simple and errors front-loaded).
- **Instruction budget:** each running job executes at most N VM instructions
  per tick, then yields. Proposed defaults (server config):
  - Interactive jobs: 200 / tick.
  - Batch jobs: 100 / tick each, plus a global cap of 2,000 / tick across
    all jobs on a server.
- **Async commands** (DLYJOB, MOVITM of large quantities, STRCRAFT with
  WAIT(*YES), recalls from tape) put the job into a wait state; the VM
  resumes it when the operation completes. Waiting costs no budget.
- **Persistence:** VM state must serialize to NBT. Behaviour on unload/
  restart depends on the job host (OS.md §5).
- **Limits** (server config): max source lines per member 5,000;
  max variables per program 256; max list size 4,096 elements;
  max `*CHAR` length 1,024; call depth 16; subroutine depth 16.

## 10. Compile listing

Option 14 / `CRTELPGM` produces a listing (shown on screen and stored as a
spooled file) containing: source with sequence numbers, a cross-reference of
variables and labels, and messages with sequence number, ID, severity and
text. Severity: 00 info, 10 warning, 20 error, 30 severe. Any 20+ message
means no program object is created.

## 11. Database files **[EXT]**

A program works on a physical file (COMMANDS.md 11) through the variables `DCLF` declares, one for each field of the
file's record format: `&FIELD`, or `&OPNID_FIELD` when the file has an open ID (`DCLF FILE(ELGPL/STOCK) OPNID(STK)`
gives `&STK_ITEM`, `&STK_QTY`...). Their types come from the fields: A `*CHAR` of its length, S `*INT`, P `*DEC` of its
length and decimals, L `*LGL`, T (a game timestamp, `00012 06:30:15`) `*CHAR 14`. A program declares up to 5 files.

```
PGM
  DCLF FILE(ELSYS/INVITEMS)            /* &ITEM &NAME &HOT &COLD &MOD */
  DCL  VAR(&TOTAL) TYPE(*INT)
READ:
  RCVF
  MONMSG MSGID(ELC2201) EXEC(GOTO CMDLBL(DONE))   /* end of file */
  CHGVAR VAR(&TOTAL) VALUE(&TOTAL + &HOT + &COLD)
  GOTO CMDLBL(READ)
DONE:
  SNDPGMMSG MSG('Items:' *BCAT %CHAR(&TOTAL))
ENDPGM
```

- **Compile time:** the file must exist (`ELC2205`); `*LIBL` is the compiling user's library list. The program keeps
  the record formats it was compiled with (saved with it), so it compiles again the same on load and in a saved job.
  A file operation naming an open ID no `DCLF` declares is `ELC2206`.
- **Run time:** each operation opens the file as it is now (`*LIBL`: the job user's library list) and checks it against
  the format the program was compiled with: a field the program declares that is gone, or of another type, is a level
  check (`ELC2207`). Fields added since don't matter (a write leaves them blank).
- **Reading:** `RCVF` reads in key order (arrival order without a key); `CHNRCD` reads by key and `RCVF` carries on
  after it. At the end, `RCVF` raises `ELC2201` - again on every `RCVF` until `POSDBF` or `CLOF`. Where a file's reads
  have got to, and the record last read (what `UPDRCD` and `DLTRCD` change), are part of the job's saved state.
- **Writing:** `WRTRCD` and `UPDRCD` write the variables as the file's fields hold them (a value that doesn't fit is
  `ELC2209`; text is cut to its length as for a `*CHAR`). A timestamp written blank (or `*NOW`) takes the game time,
  and the variable gets it back.
