`ENDSELECT` closes a {{cmd SELECT}}. Whichever branch ran (or none), the program carries on with the statement after
it. An `ENDSELECT` whose innermost open block isn't a `SELECT` is {{msg ELC0009}} ("ENDSELECT without matching
SELECT"), and so is a `SELECT` left without one.

A {{cmd MONMSG}} straight after an `ENDSELECT` has no command to monitor ({{msg ELC0009}}).

```elcl
DCL VAR(&N) TYPE(*INT)
RTVITMCNT ITEM('minecraft:diamond') RTNCOUNT(&N) NOTFND(*ZERO)
SELECT
  WHEN COND(&N *EQ 0) THEN(SNDPGMMSG MSG('No diamonds'))
  WHEN COND(&N *LT 64) THEN(SNDPGMMSG MSG('Under a stack of diamonds'))
  OTHERWISE CMD(SNDPGMMSG MSG('Plenty of diamonds'))
ENDSELECT
PRTTXT TEXT('Diamonds:' *BCAT %CHAR(&N))
```
