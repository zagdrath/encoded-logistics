`CLOF` forgets where a program has got to in a declared file: the next {{cmd RCVF}} reads it again from the first
record, and there's no record last read for {{cmd UPDRCD}} or {{cmd DLTRCD}} ({{msg ELC2204}}). It sends no message.

The file stays declared, so you can go on using it after `CLOF`: there's nothing to open again. You don't need `CLOF`
at the end of a program, and nothing is held open between file operations (each one opens the file as it is at that
moment). For reading, `CLOF` does the same as {{cmd POSDBF}} `POSITION(*START)`.

```elcl
PGM
  DCLF FILE(ELSYS/JOBS)
  DCL  VAR(&N) TYPE(*INT)
  RCVF
  MONMSG MSGID(ELC2201) EXEC(SNDPGMMSG MSG('No jobs'))
  CLOF
  /* Read again from the top */
  DOWHILE COND('1')
    RCVF
    MONMSG MSGID(ELC2201) EXEC(LEAVE)
    CHGVAR VAR(&N) VALUE(&N + 1)
  ENDDO
  SNDPGMMSG MSG(%CHAR(&N) *BCAT 'jobs')
ENDPGM
```
