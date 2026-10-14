`DLTRCD` deletes the record the program last read from a declared file ({{cmd RCVF}} or {{cmd CHNRCD}}). It sends no
message when the record is deleted.

- **No record last read** is {{msg ELC2204}} (nothing read yet, the end of the file reached, {{cmd POSDBF}} or
  {{cmd CLOF}} since, or that record already deleted, by this program or another job).
- After it there's no record last read, so a second `DLTRCD` or an {{cmd UPDRCD}} is {{msg ELC2204}} until you read
  again. The next {{cmd RCVF}} reads on to the record after the deleted one, so deleting as you read works.
- Its relative record number isn't used again. To delete every record at once, use {{cmd CLRPFM}}.
- It needs `*CHANGE` on the library ({{msg ELC0401}}) and can't delete from `ELSYS` files ({{msg ELC0205}}).

```elcl
PGM PARM(&BEFORE)
  DCL  VAR(&BEFORE) TYPE(*CHAR) LEN(14)   /* e.g. '00030 06:00:00' */
  DCLF FILE(ELGPL/ITEMHIST)
  DCL  VAR(&N) TYPE(*INT)
  /* Drop the history logged before a game time */
  DOWHILE COND('1')
    RCVF
    MONMSG MSGID(ELC2201) EXEC(LEAVE)
    IF COND(&LOGGED *GE &BEFORE) THEN(LEAVE)   /* key order: the rest are newer */
    DLTRCD
    CHGVAR VAR(&N) VALUE(&N + 1)
  ENDDO
  SNDPGMMSG MSG(%CHAR(&N) *BCAT 'records deleted')
ENDPGM
```
