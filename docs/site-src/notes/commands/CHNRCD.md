`CHNRCD` reads one record of a keyed file by its key, straight into the file's field variables, without reading the
records before it.

- **`KEY`** gives a value for each key field, in the key's order: all of them, or only the first ones (a partial key).
  `CHNRCD` reads the first record, in key order, whose leading key fields are those values. Each value is taken as its
  key field's type, so a value that isn't one (text for an `S` field) is {{msg ELC2209}}. Text compares exactly, upper
  and lower case apart, trailing blanks aside.
- **No such record:** {{msg ELC2202}}, naming the key. Nothing changes: the variables and the place in the file stay as
  they were.
- **Found**, the record becomes the record last read ({{cmd UPDRCD}} and {{cmd DLTRCD}} work on it), and the next
  {{cmd RCVF}} reads on from it in key order, even after the end of the file had been reached.
- **The compiler checks the key:** `CHNRCD` on a file without a key, or with more values than the key has fields, is
  {{msg ELC0103}}. `ELSYS/INVITEMS` is keyed on `ITEM`, `DEVICES` on `NAME`, `CRFHIST` and `JOBS` on `NUMBER`.

It sends no message when it finds the record.

```elcl
PGM
  DCLF FILE(ELSYS/DEVICES)
  CHNRCD KEY('UPS01')
  MONMSG MSGID(ELC2202) EXEC(DO)
    SNDPGMMSG MSG('UPS01 is not on this network')
    RETURN
  ENDDO
  IF COND(&STATUS *NE '*ONLINE') THEN(SNDPGMMSG MSG('UPS01 is' *BCAT &STATUS))
ENDPGM
```

With a partial key, `CHNRCD` then {{cmd RCVF}} walks every record that starts with it. `ELGPL/ITEMHIST` is keyed on
`LOGGED` then `ITEM`, so this reads one logging run's records:

```elcl
PGM PARM(&WHEN)
  DCL  VAR(&WHEN) TYPE(*CHAR) LEN(14)
  DCLF FILE(ELGPL/ITEMHIST)
  DCL  VAR(&N) TYPE(*INT)
  CHNRCD KEY(&WHEN)
  MONMSG MSGID(ELC2202) EXEC(RETURN)
  DOWHILE COND(&LOGGED *EQ &WHEN)
    CHGVAR VAR(&N) VALUE(&N + 1)
    RCVF
    MONMSG MSGID(ELC2201) EXEC(LEAVE)
  ENDDO
  SNDPGMMSG MSG(%CHAR(&N) *BCAT 'items logged at' *BCAT &WHEN)
ENDPGM
```
