`DOFOR` counts: it sets `VAR` to `FROM`, runs the statements up to its {{cmd ENDDO}}, adds `BY` to the variable, and
goes round again until the variable has passed `TO`.

- `VAR` must be an `*INT` variable (a `*DEC` is {{msg ELC0103}}).
- `BY` is 1 unless you give it, and can be negative to count down: the loop then ends once the variable is below `TO`.
  `BY(0)` would never end, so it is {{msg ELC0004}}: when you compile if it's a literal, when it runs if it's an
  expression.
- `TO` and `BY` are worked out again on every pass, so a loop to `%SIZE(&LIST)` follows the list as it grows or
  shrinks.
- If `FROM` is already past `TO`, the body never runs.
- After the loop the variable holds the first value past `TO` (11 after `FROM(1) TO(10)`), unless {{cmd LEAVE}} ended
  it early.
- The body may change the variable itself; the next step adds `BY` to whatever it holds.
- {{cmd ITERATE}} goes to the step (add `BY`, test), {{cmd LEAVE}} out of the loop. Label the `DOFOR` to let a loop
  inside it name this one.

```elcl
DCL VAR(&I)     TYPE(*INT)
DCL VAR(&ITEMS) TYPE(*LIST)
DCL VAR(&LINE)  TYPE(*CHAR) LEN(80)
RTVITMLST SORT(*QTY) MAX(10) RTNLST(&ITEMS)
DOFOR VAR(&I) FROM(1) TO(%SIZE(&ITEMS))
  CHGVAR VAR(&LINE) VALUE(%CHAR(&I) *TCAT '.' *BCAT %ELEM(&ITEMS &I))
  PRTTXT TEXT(&LINE)
ENDDO
```

Counting down, every other number:

```elcl
DCL VAR(&N) TYPE(*INT)
DOFOR VAR(&N) FROM(10) TO(1) BY(-2)
  SNDPGMMSG MSG(%CHAR(&N))
ENDDO
```
