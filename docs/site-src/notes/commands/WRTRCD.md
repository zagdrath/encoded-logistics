`WRTRCD` adds a new record to a declared file, made from the current values of the file's field variables. Set the
variables (with `CHGVAR`, or by reading a record first) and write. It sends no message when the record is written.

- **Values:** text longer than an `A` field is cut, as a `*CHAR` assignment is; a number too big for its field is
  {{msg ELC2209}}. Fields added to the file since the program was compiled are written blank.
- **Timestamps:** a `T` field written blank takes the game clock's time at that moment, and the variables get the
  record back as it was written, so the variable holds that time afterwards. A program that writes several records
  with the same time can write the first blank and use the time it got for the rest (the shipped `LOGITEMS` does).
- **Where it goes** doesn't matter to you: a keyed file keeps its records in key order, a file without a key in the
  order written. The record gets the next relative record number. Writing doesn't change the place {{cmd RCVF}} has
  got to, or the record last read.

Failures:

| Message | When |
|---|---|
| {{msg ELC2203}} | The file's key is `UNIQUE` and a record with that key is there already. |
| {{msg ELC2208}} | The file holds {{config elcl.maxRecordsPerFile}} records already. |
| {{msg ELC0207}} | The network's drives have no room for another record. |
| {{msg ELC0205}} | The file is in `ELSYS` (its system files are read-only). |
| {{msg ELC0401}} | The job's user lacks `*CHANGE` on the file's library (they aren't its owner or `*SECOFR`, and it isn't a `*CHANGE` library). |

See [storage, limits and authority](page:elcl/database-files#db-limits).

```elcl
PGM
  DCLF FILE(ELSYS/INVITEMS) OPNID(INV)
  DCLF FILE(ELGPL/ITEMHIST) OPNID(HIST)
  DOWHILE COND('1')
    RCVF OPNID(INV)
    MONMSG MSGID(ELC2201) EXEC(LEAVE)
    CHGVAR VAR(&HIST_LOGGED) VALUE(' ')        /* the time it's written */
    CHGVAR VAR(&HIST_ITEM)   VALUE(&INV_ITEM)
    CHGVAR VAR(&HIST_COUNT)  VALUE(&INV_HOT + &INV_COLD)
    WRTRCD OPNID(HIST)
  ENDDO
ENDPGM
```
