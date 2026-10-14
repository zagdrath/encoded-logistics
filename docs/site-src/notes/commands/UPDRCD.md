`UPDRCD` changes the record the program last read from a declared file ({{cmd RCVF}} or {{cmd CHNRCD}}) to the
current values of the file's field variables. Read the record, change the variables you want to, then `UPDRCD`. It
sends no message when the record is changed.

- **No record last read** is {{msg ELC2204}}: nothing read yet, the end of the file reached, a {{cmd POSDBF}} or
  {{cmd CLOF}} since, or the record deleted with {{cmd DLTRCD}}. It's {{msg ELC2204}} too when another job deleted the
  record after you read it.
- The record keeps its relative record number and stays the record last read, so you can change it again or delete it.
  Its values come back into the variables (a `T` field written blank gets the time it was changed).
- A changed key moves the record to its new place in key order; with a `UNIQUE` key, a key another record has is
  {{msg ELC2203}}. A value that doesn't fit its field is {{msg ELC2209}}.
- Like {{cmd WRTRCD}}, it needs `*CHANGE` on the library ({{msg ELC0401}}) and can't change `ELSYS` files
  ({{msg ELC0205}}).

```elcl
PGM
  DCLF FILE(ELGPL/ITEMHIST)
  DCL  VAR(&FIXED) TYPE(*INT)
  /* Counts below zero can't happen: put any there are to zero */
  DOWHILE COND('1')
    RCVF
    MONMSG MSGID(ELC2201) EXEC(LEAVE)
    IF COND(&COUNT *LT 0) THEN(DO)
      CHGVAR VAR(&COUNT) VALUE(0)
      UPDRCD
      CHGVAR VAR(&FIXED) VALUE(&FIXED + 1)
    ENDDO
  ENDDO
  SNDPGMMSG MSG(%CHAR(&FIXED) *BCAT 'records fixed')
ENDPGM
```
