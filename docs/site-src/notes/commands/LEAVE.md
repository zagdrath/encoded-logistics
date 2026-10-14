`LEAVE` ends a loop at once: the program carries on with the statement after the loop's {{cmd ENDDO}} or
{{cmd ENDFOR}}, skipping the rest of the pass and any passes still to come. It works in all four loops:
{{cmd DOWHILE}}, {{cmd DOUNTIL}}, {{cmd DOFOR}} and {{cmd FOREACH}}.

- With no `CMDLBL` (or `CMDLBL(*CURRENT)`) it leaves the innermost loop it is in. A plain {{cmd DO}} group or a
  {{cmd SELECT}} isn't a loop, so a `LEAVE` inside an `IF ... THEN(DO)` or a `WHEN` leaves the loop around them.
- `CMDLBL(name)` leaves the enclosing loop with that label, and every loop inside it. The label is the one written in
  front of the loop statement: `OUTER: DOFOR ...`. A name that isn't the label of a loop around the `LEAVE` is
  {{msg ELC0010}}.
- A `LEAVE` that isn't inside a loop is {{msg ELC0009}} ("LEAVE without matching DOWHILE"). A subroutine counts as
  outside the main body's loops: a `LEAVE` in a `SUBR` can only leave a loop in that subroutine.
- It is usually written inside `IF ... THEN(LEAVE)` or a `MONMSG ... EXEC(LEAVE)`.

To skip only the rest of one pass, use {{cmd ITERATE}}.

```elcl
DCL VAR(&DEVS) TYPE(*LIST)
DCL VAR(&DEV)  TYPE(*CHAR) LEN(10)
DCL VAR(&STS)  TYPE(*CHAR) LEN(10)
DCL VAR(&I)    TYPE(*INT)
RTVDEVLST TYPE(CMPSRV) RTNLST(&DEVS)
SCAN: DOFOR VAR(&I) FROM(1) TO(3)
  FOREACH VAR(&DEV) IN(&DEVS)
    RTVDEVSTS DEV(&DEV) RTNSTS(&STS)
    IF COND(&STS *EQ '*FAULT') THEN(DO)
      SNDMSG MSG(&DEV *BCAT 'has a fault') TOUSR(*SYSOPR)
      LEAVE CMDLBL(SCAN)
    ENDDO
  ENDFOR
  DLYJOB DLY(30)
ENDDO
```
