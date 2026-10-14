`DOWHILE` repeats the statements up to its {{cmd ENDDO}} for as long as its condition holds. The condition is tested
before each pass, the first one included, so if it is false to begin with the body never runs. (For a loop that always
runs at least once, use {{cmd DOUNTIL}}.)

- `COND` is a logical expression, as for {{cmd IF}}.
- {{cmd LEAVE}} ends the loop at once; {{cmd ITERATE}} goes straight back to the test.
- Put a label on the `DOWHILE` (`WAIT: DOWHILE ...`) to let `LEAVE` and `ITERATE` in a loop inside it name this one.

A loop that never ends doesn't hold up the server: a job runs a budget of instructions each tick and then waits for the
next (see [limits](page:elcl/language#limits)). It does use the budget on every pass, though, so a loop that watches for
something should wait between tests with {{cmd DLYJOB}} or {{cmd DLYTICK}}, which cost nothing while they wait.

```elcl
DCL VAR(&PCT) TYPE(*DEC) LEN(5 1)
RTVSTGSTS RTNPCT(&PCT)
DOWHILE COND(&PCT *LT 90)
  DLYJOB DLY(60)
  RTVSTGSTS RTNPCT(&PCT)
ENDDO
SNDMSG MSG('Storage is' *BCAT %CHAR(&PCT) *TCAT '% full') TOUSR(*SYSOPR)
```
