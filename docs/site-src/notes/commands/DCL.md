`DCL` declares a variable: its name, its type and, for text and decimals, its length. Every variable a program uses
must be declared ({{msg ELC0002}} otherwise), including the parameters named on {{cmd PGM}}. The types themselves are
described on [the language page](page:elcl/language#types).

All the `DCL`s (and any {{cmd DCLF}}s) come straight after `PGM`, before every other statement: a `DCL` after the first
command is {{msg ELC0016}}. Declaring the same name twice, or a name a `DCLF` already declared for a field, is
{{msg ELC0103}}.

## Length and starting value

| `TYPE` | `LEN` | Starts as |
|---|---|---|
| `*CHAR` | `LEN(n)`, 1 to 1,024; 32 if you leave it out | blanks |
| `*DEC` | `LEN(digits decimals)`, digits 1 to 31, decimals 0 up to the digits; `LEN(15 5)` if you leave it out, and `LEN(7)` alone means 7 digits, no decimals | 0 at its decimals |
| `*INT` | none (a 64-bit whole number) | 0 |
| `*LGL` | none | `'0'` |
| `*LIST` | none (up to {{config elcl.maxListSize default}} elements) | empty |

A length out of those ranges is {{msg ELC0004}}; a `LEN` on an `*INT`, `*LGL` or `*LIST`, or two values on a `*CHAR`, is
{{msg ELC0103}}.

`VALUE` sets a different starting value, checked against the type as {{cmd CHGVAR}} checks an assignment: text for a
`*CHAR` (a number only through `%CHAR`), a whole number for an `*INT` (`VALUE(2.5)` is {{msg ELC0003}}), `'1'`, `'0'`,
`*TRUE` or `*FALSE` for a `*LGL`, and a number that fits for a `*DEC` ({{msg ELC0007}} if it doesn't). A text literal
longer than its `*CHAR` is cut, with the warning {{msg ELC0008}}. A `*LIST` can't have a `VALUE` ({{msg ELC0103}}): start
it empty and fill it with {{cmd ADDLSTE}} or a command's `RTNLST`. Give `VALUE` a literal; the starting values aren't set
in any particular order, so one variable's `VALUE` can't rely on another's.

## RETAIN (PLC programs)

On a [PLC](page:elcl/control-and-plcs) a program's variables carry on from one scan to the next anyway; `VALUE` is
applied again only when the PLC goes from STOP to RUN or powers up. `RETAIN(*YES)` keeps the variable's value through
those too: STOP and RUN, a power loss, a world reload and a program cartridge. In a program compiled for a job,
`RETAIN(*YES)` has no effect and the compiler says so with the warning {{msg ELC1506}}.

```elcl
DCL VAR(&ITEM)  TYPE(*CHAR) LEN(64) VALUE('minecraft:iron_ingot')
DCL VAR(&MIN)   TYPE(*INT)  VALUE(500)
DCL VAR(&PCT)   TYPE(*DEC)  LEN(5 1)
DCL VAR(&LOW)   TYPE(*LGL)
DCL VAR(&ITEMS) TYPE(*LIST)
DCL VAR(&COUNT) TYPE(*INT)
RTVITMCNT ITEM(&ITEM) RTNCOUNT(&COUNT) NOTFND(*ZERO)
CHGVAR VAR(&LOW) VALUE(&COUNT *LT &MIN)
```

A PLC counter that survives a restart:

```elcl target=PLC_LOCAL
DCL VAR(&PULSES) TYPE(*INT) RETAIN(*YES)
DCL VAR(&LVL)    TYPE(*INT)
RTVRSIN DEV(*SELF) SIDE(*NORTH) RTNLVL(&LVL)
IF COND(&LVL *GT 0) THEN(CHGVAR VAR(&PULSES) VALUE(&PULSES + 1))
```
