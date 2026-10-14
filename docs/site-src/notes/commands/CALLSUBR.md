`CALLSUBR` runs a subroutine of the same program and, when the subroutine reaches its {{cmd ENDSUBR}}, carries on with
the statement after the `CALLSUBR`. To run another program instead, use {{cmd CALL}}.

- `SUBR` names a {{cmd SUBR}} in this program; one that doesn't exist is {{msg ELC0010}}. A subroutine may be
  anywhere among the subroutines, before or after the one calling it.
- `RTNVAL` names an `*INT` variable (exactly `*INT`: a `*DEC` is {{msg ELC0103}}) that receives the subroutine's
  `ENDSUBR RTNVAL()` value. Leave it out if you don't need the value.
- The subroutine shares the program's variables, so set whatever it works on before calling it, and read what it
  changed afterwards.
- Subroutines can call each other, and themselves, up to 16 deep in one program; a 17th is {{msg ELC0011}}, an escape
  message like any other.

```elcl
PGM
  DCL VAR(&DEVS) TYPE(*LIST)
  DCL VAR(&DEV)  TYPE(*CHAR) LEN(10)
  DCL VAR(&STS)  TYPE(*CHAR) LEN(10)
  DCL VAR(&BAD)  TYPE(*INT)
  DCL VAR(&RC)   TYPE(*INT)

  RTVDEVLST TYPE(*ALL) RTNLST(&DEVS)
  FOREACH VAR(&DEV) IN(&DEVS)
    CALLSUBR SUBR(CHECK) RTNVAL(&RC)
    CHGVAR VAR(&BAD) VALUE(&BAD + &RC)
  ENDFOR
  SNDPGMMSG MSG(%CHAR(&BAD) *BCAT 'devices not online')
  RETURN

  SUBR SUBR(CHECK)
    RTVDEVSTS DEV(&DEV) RTNSTS(&STS)
    IF COND(&STS *EQ '*ONLINE') THEN(CHGVAR VAR(&RC) VALUE(0))
    ELSE CMD(CHGVAR VAR(&RC) VALUE(1))
  ENDSUBR RTNVAL(&RC)
ENDPGM
```
