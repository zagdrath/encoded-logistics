`ENDDO` closes the innermost open {{cmd DO}} group or `DO` loop: {{cmd DOWHILE}}, {{cmd DOUNTIL}} or {{cmd DOFOR}}. At the
end of a loop it sends the program back for the next pass; at the end of a plain `DO` group the program simply carries
on after it. A {{cmd FOREACH}} closes with {{cmd ENDFOR}} and a {{cmd SELECT}} with {{cmd ENDSELECT}} instead.

An `ENDDO` whose innermost open block isn't a `DO` or a `DO` loop (nothing open, or a `FOREACH`, `SELECT` or `SUBR`) is
{{msg ELC0009}} ("ENDDO without matching DO").

- After the `ENDDO` of an `IF ... THEN(DO)` group, an {{cmd ELSE}} may follow.
- After the `ENDDO` of a `MONMSG ... EXEC(DO)` group, another {{cmd MONMSG}} for the same command may follow.
- A {{cmd MONMSG}} after any other `ENDDO` has no command to monitor ({{msg ELC0009}}).

```elcl
DCL VAR(&I)   TYPE(*INT)
DCL VAR(&LVL) TYPE(*INT)
DOFOR VAR(&I) FROM(1) TO(4)
  RTVRSIN DEV(CTLIF01) SIDE(*NORTH) RTNLVL(&LVL)
  IF COND(&LVL *GT 0) THEN(DO)
    SNDPGMMSG MSG('Signal on pass' *BCAT %CHAR(&I))
    LEAVE
  ENDDO
  DLYTICK TICKS(20)
ENDDO
```
