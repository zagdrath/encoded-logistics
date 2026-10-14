`RETURN` ends the program normally, wherever it is, just as reaching {{cmd ENDPGM}} does:

- In a program another one called with {{cmd CALL}}, control goes back to the caller, which carries on after its
  `CALL`. The parameters are copied back into the caller's variables first.
- In the job's own program, the job ends normally. Called from a command line, the terminal shows {{msg ELC0110}}.
- On a [PLC](page:elcl/control-and-plcs), it ends the current scan; the next one starts from the top on the next tick.

`RETURN` inside a subroutine ends the whole program, not just the subroutine. To leave a subroutine early, `GOTO` a
label on its {{cmd ENDSUBR}} instead.

`RETURN` is what keeps the main body out of the subroutines and the error handling at the end of a program, and the
usual end of a `MONMSG ... EXEC(DO)` group that gives up.

```elcl
DCL VAR(&N) TYPE(*INT)
MONMSG MSGID(ELC1201) EXEC(GOTO CMDLBL(NONE))
RTVITMCNT ITEM('minecraft:emerald') RTNCOUNT(&N)
SNDPGMMSG MSG('Emeralds:' *BCAT %CHAR(&N))
RETURN

NONE:
  SNDPGMMSG MSG('No emeralds in the network')
```
