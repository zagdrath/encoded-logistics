`ENDFOR` closes a {{cmd FOREACH}} loop: at it the loop goes on with the next element of the list, or, after the last,
the program carries on after the `ENDFOR`. Only a `FOREACH` closes with `ENDFOR`; the `DO` loops close with
{{cmd ENDDO}}. An `ENDFOR` whose innermost open block isn't a `FOREACH` is {{msg ELC0009}} ("ENDFOR without matching
FOREACH"), and so is a `FOREACH` left without one.

A {{cmd MONMSG}} straight after an `ENDFOR` has no command to monitor ({{msg ELC0009}}). Put it after the command inside
the loop, or make it a program-level monitor.

```elcl
DCL VAR(&ITEMS) TYPE(*LIST)
DCL VAR(&ITEM)  TYPE(*CHAR) LEN(64)
DCL VAR(&N)     TYPE(*INT)
RTVITMLST FILTER('*_ore') RTNLST(&ITEMS)
FOREACH VAR(&ITEM) IN(&ITEMS)
  RTVITMCNT ITEM(&ITEM) RTNCOUNT(&N)
  PRTTXT TEXT(%CHAR(&N) *BCAT &ITEM)
ENDFOR
```
