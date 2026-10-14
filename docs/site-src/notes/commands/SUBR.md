`SUBR` starts a subroutine: a named block of statements, closed by {{cmd ENDSUBR}}, that the program runs with
{{cmd CALLSUBR}} from anywhere and comes back from. Use one for anything you would otherwise write out twice.

- Subroutines go after the main body, before {{cmd ENDPGM}}, one after another. Once the first `SUBR` has come, only
  more subroutines may follow: any other statement there is {{msg ELC0001}}. End the main body with {{cmd RETURN}}; if
  it runs on into the first `SUBR`, the program ends there.
- A `SUBR` inside a block (a loop, a `DO` group, a `SELECT` or another subroutine) is {{msg ELC0009}}.
- The name is up to 10 characters. Two subroutines with the same name are {{msg ELC0103}}.
- A subroutine has no parameters or variables of its own: it uses the program's variables, so set the ones it works
  on before the `CALLSUBR`. It can give back one whole number through `ENDSUBR RTNVAL()`.
- Labels inside a subroutine can't be reached by a `GOTO` from outside it ({{msg ELC0014}}), and a `LEAVE` or `ITERATE`
  in it can't act on a loop of the main body.
- A program-level {{cmd MONMSG}} covers the subroutines' commands too.

More on [subroutines](page:elcl/language#subroutines).

```elcl
PGM
  DCL VAR(&ITEM) TYPE(*CHAR) LEN(64)
  DCL VAR(&MIN)  TYPE(*INT)
  DCL VAR(&N)    TYPE(*INT)

  CHGVAR VAR(&ITEM) VALUE('minecraft:iron_ingot')
  CHGVAR VAR(&MIN) VALUE(512)
  CALLSUBR SUBR(TOPUP)
  CHGVAR VAR(&ITEM) VALUE('minecraft:copper_ingot')
  CHGVAR VAR(&MIN) VALUE(256)
  CALLSUBR SUBR(TOPUP)
  RETURN

  SUBR SUBR(TOPUP)
    RTVITMCNT ITEM(&ITEM) RTNCOUNT(&N) NOTFND(*ZERO)
    IF COND(&N *LT &MIN) THEN(STRCRAFT ITEM(&ITEM) QTY(&MIN - &N))
    MONMSG MSGID(ELC1400)
  ENDSUBR
ENDPGM
```
