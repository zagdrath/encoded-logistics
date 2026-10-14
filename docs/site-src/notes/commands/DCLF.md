`DCLF` tells a program which database file it works with, and gives it a variable for every field of the file's record
format. It's a declaration: it goes with the `DCL`s, before any other command ({{msg ELC0016}}), and does nothing when
the program runs. The file operations ({{cmd RCVF}}, {{cmd CHNRCD}}, {{cmd WRTRCD}}, {{cmd UPDRCD}}, {{cmd DLTRCD}},
{{cmd POSDBF}}, {{cmd CLOF}}) then name it by its open ID.

## The variables

Each field becomes a variable of the same name: `&ITEM`, `&HOT`, `&COLD`... With an open ID, the ID and an underscore
go in front: `DCLF FILE(ELSYS/INVITEMS) OPNID(INV)` gives `&INV_ITEM`, `&INV_HOT`, `&INV_COLD`. Don't `DCL` them
yourself: a variable already declared with one of those names is {{msg ELC0103}}.

| Field type | Its variable |
|---|---|
| `A` (character) | `*CHAR` of the field's length |
| `S` (integer) | `*INT` |
| `P` (decimal) | `*DEC` of the field's digits and decimals |
| `L` (logical) | `*LGL` |
| `T` (game timestamp) | `*CHAR` 14, such as `00012 06:30:15` |

## Rules

- **The file must exist when the program is compiled**, so the compiler can read its record format: otherwise
  {{msg ELC2205}}. The shipped files you can always declare are `ELSYS/INVITEMS`, `ELSYS/DEVICES`, `ELSYS/CRFHIST` and
  `ELSYS/JOBS` (see [system files](page:elcl/database-files#db-objects)).
- **Up to five files** a program ({{msg ELC2224}} past that), each open ID once ({{msg ELC0103}}). `OPNID(*NONE)`, the
  default, is an ID too, so only one file can be declared without one.
- **An unqualified name** (`FILE(STOCK)`) is looked for down the job user's library list each time the program uses
  the file. Qualify it when the same name could be in more than one library.
- **Level check.** The program keeps the record format it was compiled with. Each file operation opens the file as it
  is at that moment: if a field the program declares has gone or changed type since ({{cmd CHGPF}}), the operation
  fails with {{msg ELC2207}} and you compile the program again. Fields added since don't matter (they're written blank).

`ELGPL/ITEMHIST` in the example below is the file `CALL ELSYS/ITEMSETUP` makes
([the worked example](page:elcl/database-files#db-example)). `DCLF` runs only in programs. See [Files in programs](page:elcl/database-files#db-programs) for more.

```elcl
PGM
  DCLF FILE(ELSYS/DEVICES)                  /* &NAME &TYPE &LOCATION &LANES &STATUS */
  DCLF FILE(ELGPL/ITEMHIST) OPNID(HIST)     /* &HIST_LOGGED &HIST_ITEM &HIST_COUNT  */
  DCL  VAR(&DOWN) TYPE(*INT)
  DOWHILE COND('1')
    RCVF
    MONMSG MSGID(ELC2201) EXEC(LEAVE)
    IF COND(&STATUS *NE '*ONLINE') THEN(CHGVAR VAR(&DOWN) VALUE(&DOWN + 1))
  ENDDO
  SNDPGMMSG MSG(%CHAR(&DOWN) *BCAT 'devices not online')
ENDPGM
```
