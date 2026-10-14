`ENDSUBR` closes a subroutine. When the program reaches it, it goes back to the statement after the {{cmd CALLSUBR}}
that ran the subroutine, and the value of `RTNVAL` goes into that `CALLSUBR`'s `RTNVAL` variable, if it named one.

- `RTNVAL` is a whole number: a literal, an `*INT` variable or an expression (0 if you leave it out). A `*DEC` value
  loses its decimals. Use it as a return code, for instance 0 for success and something else for a problem.
- An `ENDSUBR` with no open {{cmd SUBR}} is {{msg ELC0009}}, and so is a `SUBR` left without one.
- A label on `ENDSUBR` is the way to leave the subroutine early: `GOTO` to it from inside. ({{cmd RETURN}} in a
  subroutine ends the whole program.)

```elcl
PGM
  DCL VAR(&DEV) TYPE(*CHAR) LEN(10)
  DCL VAR(&STS) TYPE(*CHAR) LEN(10)
  DCL VAR(&RC)  TYPE(*INT)

  CHGVAR VAR(&DEV) VALUE('UPS01')
  CALLSUBR SUBR(CHECK) RTNVAL(&RC)
  IF COND(&RC *NE 0) THEN(SNDMSG MSG(&DEV *BCAT 'needs attention') TOUSR(*SYSOPR))
  RETURN

  SUBR SUBR(CHECK)
    CHGVAR VAR(&RC) VALUE(0)
    RTVDEVSTS DEV(&DEV) RTNSTS(&STS)
    MONMSG MSGID(ELC1300) EXEC(DO)
      CHGVAR VAR(&RC) VALUE(2)
      GOTO CMDLBL(DONE)
    ENDDO
    IF COND(&STS *NE '*ONLINE') THEN(CHGVAR VAR(&RC) VALUE(1))
  DONE: ENDSUBR RTNVAL(&RC)
ENDPGM
```
