`POSDBF` moves a declared file's place for the next {{cmd RCVF}}, without reading anything. It sends no message.

- `POSITION(*START)`: the next `RCVF` reads the first record again. Use it to read a file a second time, or to start
  over after {{msg ELC2201}}.
- `POSITION(*END)`: the next `RCVF` fails with {{msg ELC2201}} at once, as if the file had been read to the end.

Either way there's no record last read any more, so an {{cmd UPDRCD}} or {{cmd DLTRCD}} straight after it is
{{msg ELC2204}}. `POSDBF` doesn't open the file, so it never fails for authority or a level check. `POSITION(*START)`
does the same for reading as {{cmd CLOF}}.

```elcl
PGM
  DCLF FILE(ELSYS/DEVICES)
  DCL  VAR(&ALL)  TYPE(*INT)
  DCL  VAR(&DOWN) TYPE(*INT)
  /* First pass: count every device */
  DOWHILE COND('1')
    RCVF
    MONMSG MSGID(ELC2201) EXEC(LEAVE)
    CHGVAR VAR(&ALL) VALUE(&ALL + 1)
  ENDDO
  /* Second pass, from the top: the ones that aren't online */
  POSDBF POSITION(*START)
  DOWHILE COND('1')
    RCVF
    MONMSG MSGID(ELC2201) EXEC(LEAVE)
    IF COND(&STATUS *NE '*ONLINE') THEN(CHGVAR VAR(&DOWN) VALUE(&DOWN + 1))
  ENDDO
  SNDPGMMSG MSG(%CHAR(&DOWN) *BCAT 'of' *BCAT %CHAR(&ALL) *BCAT 'devices not online')
ENDPGM
```
