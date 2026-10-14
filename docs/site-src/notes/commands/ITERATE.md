`ITERATE` skips the rest of the current pass of a loop and goes on with the next one. Where "the next one" starts
depends on the loop:

| Loop | `ITERATE` goes to |
|---|---|
| {{cmd DOWHILE}} | the condition, tested again before the next pass |
| {{cmd DOUNTIL}} | the condition at the end of the pass, so the loop can end there |
| {{cmd DOFOR}} | the step: `BY` is added to the variable, then it is tested against `TO` |
| {{cmd FOREACH}} | the next element (or out, after the last) |

- With no `CMDLBL` (or `CMDLBL(*CURRENT)`) it acts on the innermost loop it is in. A plain {{cmd DO}} group or a
  {{cmd SELECT}} isn't a loop, so an `ITERATE` inside them acts on the loop around them.
- `CMDLBL(name)` goes on with the next pass of the enclosing loop with that label (`OUTER: DOFOR ...`), ending any loops
  inside it. A name that isn't the label of a loop around the `ITERATE` is {{msg ELC0010}}.
- An `ITERATE` that isn't inside a loop is {{msg ELC0009}}; a loop of the main body doesn't count from inside a
  subroutine.

To end the loop instead, use {{cmd LEAVE}}.

```elcl
DCL VAR(&ITEMS) TYPE(*LIST)
DCL VAR(&ITEM)  TYPE(*CHAR) LEN(64)
DCL VAR(&N)     TYPE(*INT)
RTVITMLST RTNLST(&ITEMS)
FOREACH VAR(&ITEM) IN(&ITEMS)
  IF COND(%SCAN('minecraft:' &ITEM) *EQ 1) THEN(ITERATE)
  RTVITMCNT ITEM(&ITEM) RTNCOUNT(&N)
  IF COND(&N *LT 64) THEN(PRTTXT TEXT('Low:' *BCAT &ITEM))
ENDFOR
```
