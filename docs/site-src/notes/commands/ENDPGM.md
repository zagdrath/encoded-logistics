`ENDPGM` is the last statement of every program. Reaching it ends the program normally, just as {{cmd RETURN}} does:
a called program goes back to its caller (copying its parameters back), and the job's own program ends the job. On a
[PLC](page:elcl/control-and-plcs) it ends one scan; the next scan starts from the top on the next tick, with the
variables as this one left them.

- Nothing may come after it: any statement there is {{msg ELC0001}}.
- A program without one is {{msg ELC0009}} ("PGM without matching ENDPGM").
- A block still open when `ENDPGM` comes (a `DO`, a loop, a `SELECT`, a `SUBR`) is {{msg ELC0009}} on the line that
  opened it.
- Subroutines go before it, after the main body. The main body never runs on into them, so a program whose main body
  reaches the first `SUBR` ends there.

A label on `ENDPGM` marks the end of the main body, so `GOTO` to it ends the program like `RETURN`:

```elcl
PGM
  DCL VAR(&COUNT) TYPE(*INT)
  RTVITMCNT ITEM('minecraft:iron_ingot') RTNCOUNT(&COUNT) NOTFND(*ZERO)
  IF COND(&COUNT *GE 1000) THEN(GOTO CMDLBL(DONE))
  SNDMSG MSG('Iron is running low') TOUSR(*SYSOPR)
DONE: ENDPGM
```
