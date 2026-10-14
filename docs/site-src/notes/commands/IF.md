`IF` works out its condition and, when it holds, runs the command in `THEN`. When it doesn't, the program goes on with
the next statement, or with the {{cmd ELSE}} if one follows straight after.

- `COND` must be a logical expression: a comparison (`&COUNT *LT 100`), `*AND` / `*OR` / `*NOT` of them, a `*LGL`
  variable, or `'1'` / `'0'`. A `*CHAR` or numeric variable on its own isn't a condition ({{msg ELC0103}}): compare it
  with something.
- `THEN` holds one command. For several, write `THEN(DO)` and the commands on the following lines, closed by
  {{cmd ENDDO}}. `THEN` can hold another `IF`, a loop that opens on the following lines, or a jump (`GOTO`, `LEAVE`,
  `ITERATE`, `RETURN`, `CALLSUBR`). It can't hold the structural statements (`DCL`, `ELSE`, `WHEN`, `SELECT`, `ENDDO`,
  `MONMSG`, `SUBR` and the like: {{msg ELC0103}}).
- `THEN` is optional; an `IF` without one does nothing, but an `ELSE` after it still runs when the condition fails.
- Text compares ignoring trailing blanks, so a `*CHAR(10)` holding `'ABC'` equals `'ABC'`.

A {{cmd MONMSG}} straight after an `IF` whose `THEN` is a single command (or after its `ELSE`) monitors what that `IF`
runs. After a `THEN(DO)` group's `ENDDO` there is no command to monitor ({{msg ELC0009}}): put the `MONMSG` after the
command inside the group instead. See
[control flow](page:elcl/language#control-flow) for the rules all the blocks share.

```elcl
DCL VAR(&COUNT) TYPE(*INT)
DCL VAR(&ITEM)  TYPE(*CHAR) LEN(64) VALUE('minecraft:redstone')
RTVITMCNT ITEM(&ITEM) RTNCOUNT(&COUNT) NOTFND(*ZERO)
IF COND(&COUNT *EQ 0) THEN(SNDMSG MSG('Out of' *BCAT &ITEM) TOUSR(*SYSOPR))
IF COND(&COUNT *GT 0 *AND &COUNT *LT 256) THEN(DO)
  SNDPGMMSG MSG('Running low:' *BCAT %CHAR(&COUNT))
  STRCRAFT ITEM(&ITEM) QTY(256)
ENDDO
```
