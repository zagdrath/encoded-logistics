`CHGVAR` works out the expression in `VALUE` and stores it in the variable `VAR`, converted to the variable's type. Both parameters are positional, so `CHGVAR &N (&N + 1)` is the short form.

What the compiler accepts depends on the variable's type (each mismatch is {{msg ELC0003}}):

| Variable | Takes |
|---|---|
| `*CHAR` | Text. A number only through `%CHAR`, even a literal one: `VALUE(%CHAR(&COUNT))`. The result is padded with blanks or cut to the variable's length; a text literal that is too long is the warning {{msg ELC0008}}, and at run time cutting is never an error. |
| `*INT` | A number. A `*DEC` value loses its decimals (it is cut towards zero, not rounded). Text is accepted and converted when it runs: text that isn't a number is {{msg ELC0003}} then. |
| `*DEC` | A number, rounded half up to the variable's decimals. A value with more whole digits than the variable holds is {{msg ELC0007}}. Text as for `*INT`. |
| `*LGL` | A condition (`&A *GT 5`, `*NOT &DONE`), another logical, `'1'`, `'0'`, `*TRUE` or `*FALSE`. |
| `*LIST` | Nothing: a list can't be assigned ({{msg ELC0103}}). Use {{cmd ADDLSTE}}, {{cmd RMVLSTE}}, {{cmd CLRLST}} or a command's `RTNLST`. |

An expression of literals alone that is already too big for the variable is reported when you compile
({{msg ELC0007}}). Anything else that goes wrong while the expression is worked out (a division by zero, `%SST` past the
end, an `*INT` past 64 bits) is an escape message you can catch with {{cmd MONMSG}}. CHGVAR sends no message when it
succeeds. Operators and built-in functions are on [the language page](page:elcl/language#expressions).

```elcl
DCL VAR(&USED) TYPE(*INT)
DCL VAR(&TOT)  TYPE(*INT)
DCL VAR(&PCT)  TYPE(*DEC) LEN(5 1)
DCL VAR(&FULL) TYPE(*LGL)
DCL VAR(&MSG)  TYPE(*CHAR) LEN(64)
RTVSTGSTS RTNUSED(&USED) RTNTOTAL(&TOT)
IF COND(&TOT *GT 0) THEN(CHGVAR VAR(&PCT) VALUE(&USED * 100.0 / &TOT))
CHGVAR VAR(&FULL) VALUE(&PCT *GE 90)
CHGVAR VAR(&MSG) VALUE('Storage' *BCAT %CHAR(&PCT) *TCAT '% full')
CHGVAR &TOT (&TOT - &USED)
```
