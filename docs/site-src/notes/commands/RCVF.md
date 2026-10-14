`RCVF` reads the next record of a file the program declared with {{cmd DCLF}} into that file's field variables. A
keyed file is read in key order; a file without a key in the order its records were written. The first `RCVF` reads
the first record, and each one after reads the record after the last one read. It sends no message when it reads a
record.

- **At the end** it fails with {{msg ELC2201}}, and keeps failing with it on every `RCVF` until you go back with
  {{cmd POSDBF}} `POSITION(*START)` or {{cmd CLOF}}. Monitor for it to end a read loop:
  `MONMSG MSGID(ELC2201) EXEC(LEAVE)`. On an empty file the first `RCVF` gets it.
- **The record it reads** becomes the one {{cmd UPDRCD}} and {{cmd DLTRCD}} work on. At the end there's none.
- **Reading on** goes by key: records other jobs write or delete between your reads are seen (or skipped) as their
  keys fall after or before where you've got to.
- **System files** (`ELSYS/INVITEMS`, `DEVICES`, `CRFHIST`, `JOBS`) are made from the network as they're read. A read
  from the top sees the network as it is now; reads on from there see the same picture for the rest of that game
  tick. Reading them needs the Firewall's view permission ({{msg ELC0401}}).
- An open ID no `DCLF` declares is {{msg ELC2206}} when the program is compiled; a field changed since then is
  {{msg ELC2207}} (see {{cmd DCLF}}).

To find one record by its key instead, use {{cmd CHNRCD}}.

```elcl
PGM
  DCLF FILE(ELSYS/INVITEMS)
  DCL  VAR(&TOTAL) TYPE(*INT)
  DCL  VAR(&KINDS) TYPE(*INT)
  DOWHILE COND('1')
    RCVF
    MONMSG MSGID(ELC2201) EXEC(LEAVE)   /* end of file */
    IF COND(&TYPE *EQ 'ITEM') THEN(DO)
      CHGVAR VAR(&TOTAL) VALUE(&TOTAL + &HOT + &COLD)
      CHGVAR VAR(&KINDS) VALUE(&KINDS + 1)
    ENDDO
  ENDDO
  SNDPGMMSG MSG(%CHAR(&KINDS) *BCAT 'kinds of item,' *BCAT %CHAR(&TOTAL) *BCAT 'in all')
ENDPGM
```
