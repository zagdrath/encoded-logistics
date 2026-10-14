---
title: Database files
section: elcl
order: 40
summary: Physical files of typed records in your libraries, the network's own data as system files, and the commands, screens and queries that work with them.
legacy: [database]
---
A library can hold **physical files** as well as members and programs: tables of records, each record a row of named,
typed fields. Programs read and write them with {{cmd DCLF}} and {{cmd RCVF}}, as CL programs do; {{cmd RUNQRY}}
selects and sorts them; screens show and change them a record at a time; and four files in `ELSYS` are the network
itself (its items, devices, crafting history and jobs), live, so a program or a query can work on them like any other
file.

```term
ITEMHIST (a PF member)  --CRTPF-->  ELGPL/ITEMHIST (a *FILE)  <--WRTRCD--  a program (DCLF FILE(ELGPL/ITEMHIST))
                                         |
                                         +--RUNQRY / DSPPFM / UPDDTA / CPYTOIMPF ...
```

## Files, records and system files {#db-objects legacy="db-objects"}

- **A file** (`*FILE`, attribute `PF`) has a **record format** (up to 50 fields), an optional **key** (up to 4 fields,
  optionally `UNIQUE`) and one data member of records. A keyed file is read in key order; one without a key in the
  order its records were written. Each record has a relative record number, given when it's written and never reused.
- **Its definition** is a source member of type `PF`, edited in the [source editor](page:elcl/libraries-and-editor#editor)
  like a program's; {{cmd CRTPF}} compiles it into the file. A file and a member (or a program) may share a name: they
  are different objects.
- **Records are saved with the system**, cost network storage as source does (see [limits](#db-limits)) and go to a
  diskette with {{cmd SAVLIB}}.

The **system files** in `ELSYS` have no records of their own: they're made from the network each time they're read (a
read from the top sees the network as it is now; a program reading on sees the same picture for the rest of that
tick). They're read-only, and need the Firewall's **view** permission, however they're read (a program,
{{cmd RUNQRY}}, {{cmd CPYF}}, {{cmd CPYTOIMPF}} or a screen). `INVITEMS` lists only what's actually stored (fluids and
gases always have `COLD` 0), and `DEVICES`' `LOCATION` gives the dimension by its path (`overworld`).

| File | Fields (the key first) | What's in it |
|---|---|---|
| `ELSYS/INVITEMS` | `ITEM` 64A, `NAME` 48A, `HOT` 18S, `COLD` 18S, `MOD` 32A, `TYPE` 5A | Every item, fluid and gas in storage: its ID as scripts write it (`IRON_INGOT`, `encodedlogistics:logic_die`, `minecraft:water`), display name, amount on the drives (mB for fluids and gases) and on tape, mod (a gas's source mod), and type (`ITEM`, `FLUID`, `PRES`). |
| `ELSYS/DEVICES` | `NAME` 10A, `TYPE` 10A, `LOCATION` 40A, `LANES` 9S, `STATUS` 10A | Every named device: type, `x,y,z dimension`, lanes it uses, `*ONLINE` / `*OFFLINE` / `*FAULT` / `*DISABLED`. |
| `ELSYS/CRFHIST` | `NUMBER` 9S, `JOBID` 8A, `ITEM` 64A, `REQUESTED` 18S, `PRODUCED` 18S, `STATUS` 10A, `USER` 10A, `STARTED` T, `ENDED` T | The [crafting job history](page:crafting/jobs). |
| `ELSYS/JOBS` | `NUMBER` 6A, `JOB` 10A, `USER` 10A, `TYPE` 3A, `HOST` 10A, `STATUS` 7A, `PRIORITY` 1S, `BUDGET` 3S | The script jobs, as [Work with Active Jobs](page:elcl/screens#scr-wrkactjob) shows them. |

## Defining a file {#db-define legacy="db-define"}

Make a definition member with `CRTMBR MBR(ZAGLIB/STOCK) SRCTYPE(PF)` (or F6 on Work with Members), then edit it.
Lines start with `A`, as in a midrange definition (a line without one is read the same); type in the midrange
editor's columns or anywhere on the line. `A*` (or `*`) starts a comment; a line with only keywords goes with the line
before it. The member's text comes from a first comment of the form `A* NAME - text`.

```dds file=ZAGLIB/STOCK
     A* STOCK - stock levels
     A                                      UNIQUE
     A          R STOCKREC                  TEXT('Stock record')
     A            ITEM          20A         COLHDG('Item')
     A            QTY            9S 0       COLHDG('Quantity')
     A            COST           7P 2       TEXT('Unit cost')
     A            HOT            1L
     A            SEEN            T         TEXT('Last counted')
     A          K ITEM
```

`R` names the record format (one to a file). `K` names a key field, in order. Every other line is a field: its name
(a letter, then up to 9 letters, digits, `_`, `@`, `#` or `$`), its length and type, then keywords. A definition needs
at least one field.

| Type | Meaning | Length | Its variable in a program |
|---|---|---|---|
| `A` | Character | 1–1,024 (`64A`) | `*CHAR` of that length |
| `S` | Integer | 1–18 digits, 0 decimals (`11S 0`) | `*INT` |
| `P` | Decimal | 1–31 digits and its decimals (`9P 2` or `9P2`) | `*DEC` of that length and decimals |
| `L` | Logical | 1 (or blank) | `*LGL` |
| `T` | Game timestamp: the day and the time on the game clock, `00012 06:30:15`; it sorts and compares as text | any (ignored) | `*CHAR` 14 |

- **Keywords.** `UNIQUE` on a line of its own before the `R` (or on the `R` line): no two records may have the same
  key ({{msg ELC2203}}); it needs a `K` field. `TEXT('...')` on the `R` (the file's text when its member has none) or
  on a field. `COLHDG('...' ['...' ['...']])` on a field: its column heading on screens and reports, up to three lines
  (otherwise the field's name). Anything else, or a keyword on a `K` line, is {{msg ELC2226}}.
- **Timestamps.** Typed, imported or assigned, a `T` value may be `Day 12 06:30`, `12 06:30:15` or `00012 06:30:15`.
  Written blank (or `*NOW`) it takes the moment it's written; a program's variable gets that time back after
  {{cmd WRTRCD}} or {{cmd UPDRCD}}.
- **Checking.** The editor checks a definition on Enter as it does a program: the bad line reversed, its message on the
  message line ({{msg ELC2220}} to {{msg ELC2228}}). There's no prompter for definition lines.

## Creating, changing and deleting files {#db-create legacy="db-create"}

- `CRTPF FILE(ZAGLIB/STOCK)` compiles the member of the same name in the same library (`SRCMBR(LIB/NAME)` for another)
  and makes the file: {{msg ELC2230}}. The listing goes to Work with Output (a spooled file of the file's name): the
  source with sequence numbers, the record format (fields, record length, key), and each message with the sequence
  number of its line. With errors there's no file: {{msg ELC2239}}. Option **14=Compile** on a `PF` member in Work
  with Members does the same. `TEXT()` gives the file a text of its own (the default, `*SRCMBRTXT`, takes the
  member's).
- `CHGPF FILE(ZAGLIB/STOCK)` makes the file again from its (changed) definition, **keeping its records**: each keeps its
  value in every field whose name and type are still there, and the other fields' values are dropped, with a warning
  for each ({{msg ELC2232}}). A text field that's shorter now is cut; a number that no longer fits stops it
  ({{msg ELC2209}}), as does a key made `UNIQUE` over duplicates ({{msg ELC2203}}): then nothing changes. Done, it's
  {{msg ELC2231}}; a definition with errors changes nothing ({{msg ELC2246}}), and a bigger format that no longer fits
  the network's storage is {{msg ELC0207}}. 14=Compile on a `PF` member whose file is there already runs
  {{cmd CHGPF}}. A file made by a query or a copy has no definition, so it can't be changed ({{msg ELC2247}}). Work
  with Members and Work with Files mark a file whose definition changed since with `*` in Chg.
- {{cmd DLTF}} deletes a file and its records ({{msg ELC2233}}); {{cmd CLRPFM}} deletes every record
  ({{msg ELC2234}}).

## Files in programs {#db-programs legacy="db-programs"}

{{cmd DCLF}} goes with the `DCL`s and declares a variable for every field: `&ITEM`, `&QTY`..., or with an open ID
`&STK_ITEM`, `&STK_QTY`... (`DCLF FILE(ZAGLIB/STOCK) OPNID(STK)`). A program may declare up to five files, each open ID
once. The file operations name the file by its open ID (none: the file declared without one).

This program reads every item on the network, adds up how many there are, and writes the ones with fewer than 64 into
a file of its own, `ZAGLIB/LOW` (which has to exist before the program is compiled):

```dds file=ZAGLIB/LOW
     A* LOW - items running low
     A                                      UNIQUE
     A          R LOWREC
     A            ITEM          64A         COLHDG('Item')
     A            QTY           18S 0       COLHDG('Quantity')
     A          K ITEM
```

```elcl
PGM
  DCLF FILE(ELSYS/INVITEMS)                   /* &ITEM &NAME &HOT &COLD &MOD &TYPE */
  DCLF FILE(ZAGLIB/LOW) OPNID(LOW)            /* &LOW_ITEM &LOW_QTY                */
  DCL  VAR(&TOTAL) TYPE(*INT)
READ:
  RCVF
  MONMSG MSGID(ELC2201) EXEC(GOTO CMDLBL(DONE))  /* end of file */
  CHGVAR VAR(&TOTAL) VALUE(&TOTAL + &HOT + &COLD)
  IF COND(&HOT + &COLD *LT 64) THEN(DO)
    CHGVAR VAR(&LOW_ITEM) VALUE(&ITEM)
    CHGVAR VAR(&LOW_QTY)  VALUE(&HOT + &COLD)
    WRTRCD OPNID(LOW)
    MONMSG MSGID(ELC2203)                     /* already listed */
  ENDDO
  GOTO CMDLBL(READ)
DONE:
  SNDPGMMSG MSG('Items stored:' *BCAT %CHAR(&TOTAL))
ENDPGM
```

| Command | What it does |
|---|---|
| {{cmd RCVF}} | Reads the next record into the variables (key order; arrival order without a key). At the end: {{msg ELC2201}}, which you can monitor, and again on every `RCVF` until the file is repositioned, chained or closed. |
| {{cmd POSDBF}} | `POSITION(*START)` goes back to the start (the next `RCVF` reads the first record); `*END` goes on to the end. Give `OPNID()` and `POSITION()` by keyword: the first positional value is the open ID. |
| {{cmd CLOF}} | Closes the file: the next `RCVF` reads it again from the top. |
| {{cmd CHNRCD}} | `KEY(value ...)` reads the first record whose leading key fields are these values (all of the key, or its first fields): {{msg ELC2202}} when there's none. `RCVF` carries on after it. A file without a key, or more values than it has key fields, is {{msg ELC0103}}. |
| {{cmd WRTRCD}} | Writes a new record from the variables (fields the program doesn't declare are blank): {{msg ELC2203}} for a duplicate unique key, {{msg ELC2208}} when the file is full, {{msg ELC0207}} when storage is. |
| {{cmd UPDRCD}} | Changes the record last read (`RCVF` or `CHNRCD`) to the variables' values: {{msg ELC2204}} when none has been read. |
| {{cmd DLTRCD}} | Deletes the record last read ({{msg ELC2204}} with none); after it there's no record last read until the next read. |

- **The file must exist when the program is compiled** ({{msg ELC2205}}); an unqualified name is looked for down the
  compiling user's library list. The program keeps the record formats it was compiled with, so it compiles again the
  same after a restart. The variables a `DCLF` makes mustn't clash with your own or another file's ({{msg ELC0103}}):
  give one of the files an open ID.
- **Level check.** Each operation opens the file as it is now (an unqualified name: down the job user's library list).
  If a field the program declares has gone or changed type since it was compiled ({{cmd CHGPF}}), that's
  {{msg ELC2207}}: compile it again. Fields added since don't matter.
- A value that doesn't fit its field (a number too big for `9S 0`) is {{msg ELC2209}}; text too long for an `A` field
  is cut, as a `*CHAR` assignment is.
- A file operation naming an open ID no `DCLF` declares is a compile error, {{msg ELC2206}}. The file commands are
  program-only: typed on a command line they're {{msg ELC0106}}.
- The editor checks `DCLF` against the file's real format, which it asks the server for when it meets one; until the
  answer comes, that file's variables aren't checked.

## The screens {#db-screens legacy="db-screens"}

`WRKF LIB(ZAGLIB)` (Main Menu option 10; with no library, your current library) works with a library's files. Work
with Members lists them too, after the library's members, with Type `*FILE`.

{{layout 17_wrkf}}

Options: **2=Change data** (Update Data), **4=Delete** (asks first; {{cmd DLTF}}), **5=Display data**,
**8=Display description**. F6 prompts {{cmd CRTPF}}. ELSYS's files can be shown (5 and 8), not changed
({{msg ELC0205}}).

`DSPPFM FILE(ZAGLIB/STOCK)`, **Display Physical File Member**, shows the records in columns under their headings.
PageUp / PageDown roll it; **Position to** takes a key on a keyed file (its fields' values, separated by blanks; the
first fields alone will do: the first record at or after it comes to the top), or on a file without a key a line
number in the list; **F19 / F20** shift 40 columns for records wider than the screen.

{{layout 18_dsppfm}}

`DSPFD FILE()`, **Display File Description**: the file's text, record format, source member (and whether it changed
since), when it was made, records and the limit, record length, storage, key, and each field's type, length,
decimals, place in the key and text.

{{layout 20_dspfd}}

`UPDDTA FILE()`, **Update Data**, changes records one at a time, as a midrange data file utility does:

{{layout 21_upddta}}

- **Change mode** shows a record. Change its values and press Enter to change it; Enter with nothing changed goes on to
  the next record. **F7 / F8** show the record before or after (PageUp / PageDown too, when the fields fit one page);
  **F11** deletes it (asks first); on a keyed file, a key in Position to and Enter shows the first record at or after
  it.
- **Entry mode** (F6; an empty file opens in it): type a new record's values and press Enter to add it; the fields
  clear for the next. **F5** clears them, **F10** goes back to change mode.
- Values are checked by type before anything is sent (a whole number for `S`, a number for `P`, `1` or `0` for `L`, a
  timestamp or blank for `T`), and again on the server: an error that names a field ({{msg ELC2209}}) puts the cursor
  on it; a duplicate key ({{msg ELC2203}}) is shown on the message line. A file with more than 12 fields has more
  pages of them. A record too long to send from the screen has to be changed with a program; `ELSYS`'s files can't be
  changed at all ({{msg ELC0205}}).

## Queries (RUNQRY) {#db-query legacy="db-query"}

```elcl nocompile="commands typed on a command line"
RUNQRY FILE(ZAGLIB/STOCK) QRYSLT('QTY *LT 100 *AND HOT') SORT(QTY *DESCEND)
RUNQRY FILE(ELSYS/INVITEMS) QRYSLT('%SST(ITEM 1 4) *EQ "IRON"') SORT(ITEM) OUTPUT(*PRINT)
RUNQRY FILE(ELSYS/DEVICES) QRYSLT('STATUS *NE "*ONLINE"') OUTPUT(*OUTFILE) OUTFILE(ZAGLIB/DOWN)
```

- **`QRYSLT`** is an ELCL expression over the file's field names: every operator and built-in function of the
  [language](page:elcl/language); a field written bare or as `&FIELD`; a literal in double quotes (or doubled single
  quotes, as in any quoted value). A name that isn't a field is {{msg ELC2242}}. `*ALL` (the default) takes every
  record.
- **`SORT`** names up to four fields ({{msg ELC2224}} past that), each optionally followed by `*DESCEND` to go highest
  first (or `*ASCEND`, the default); records it can't tell apart keep the file's order. `*NONE` (the default): the
  file's order.
- **`OUTPUT(*DISPLAY)`** (the default) shows the result on the terminal, laid out as Display Physical File Member
  (titled **Display Report**; Position to takes a line number). In a program or a batch job, where there's no screen,
  it's printed instead. **`*PRINT`** writes the report (title, selection, headings, records, the count) to the spooled
  file `QPQUPRFIL` in Work with Output. **`*OUTFILE`** makes or replaces `OUTFILE()` (needed with it; a bare name goes
  in your current library) with the records selected, in the query's order: a file without a key, with the text
  *Query of LIB/FILE*. It replaces any file of that name, so choose the name with care.
- Each run in a program says {{msg ELC2238}} (`Query selected &1 of &2 records.`); typed at a terminal, the Display
  Report screen shows *Selected n of m* instead.

{{layout 19_runqry}}

## Copying, export and import {#db-copy legacy="db-copy"}

- {{cmd CPYF}} `FROMFILE() TOFILE() MBROPT(*ADD|*REPLACE) CRTFILE(*NO|*YES)` copies records, each field of the file
  copied to from the field of the same name ({{msg ELC2244}} when they share none; others left blank).
  `CRTFILE(*YES)` makes a file that isn't there with the copied file's format and key. It's all or nothing: a value
  that doesn't fit ({{msg ELC2243}}, with its row) or a duplicate key ({{msg ELC2203}}) copies nothing. Done:
  {{msg ELC2235}}.
- {{cmd CPYTOIMPF}} `FILE() TOSTMF('stock.csv')` writes the records as CSV (a heading row of field names, then a row a
  record; numbers plain, logicals `1`/`0`, timestamps as text) to the system's folder:
  `<world>/encodedlogistics/libraries/<SYSNAME>/stock.csv`. {{msg ELC2236}}.
- {{cmd CPYFRMIMPF}} `FROMSTMF('stock.csv') FILE() MBROPT(*ADD|*REPLACE)` reads one back. A heading row naming the
  fields maps columns by name (any order; fields left out are blank); without one the columns are the fields in order.
  All or nothing: a cell that doesn't fit its field is {{msg ELC2243}}. Done: {{msg ELC2237}}; {{msg ELC2240}} when
  there's no such file.
- Both need [folder sync](page:elcl/libraries-and-editor#folder-sync) on ({{config elcl.allowFolderSync}}), else
  {{msg ELC2241}}; the name must be a plain name ending `.csv` ({{msg ELC2245}}). See also {{page admin/folders}}.

## Storage, limits and authority {#db-limits legacy="db-limits"}

- **Storage.** A file's records cost network storage as source does: each record its record length in characters (A
  its length, S and P their digits, L 1, T 14), a byte of drive space per {{default elcl.charsPerStorageByte}}
  characters ({{config elcl.charsPerStorageByte}}), rounded up per file. Writing records past the drives' room is
  {{msg ELC0207}}. ELSYS's files cost nothing.
- **Records per file.** At most {{config elcl.maxRecordsPerFile default}}: past it, {{msg ELC2208}}. The limit and the
  storage check apply to every way records go in: {{cmd WRTRCD}}, {{cmd CPYF}}, {{cmd CPYFRMIMPF}} and
  `RUNQRY OUTPUT(*OUTFILE)`.
- **Authority.** Reading a file needs its library (every library gives `*USE`); writing it (records, {{cmd CRTPF}},
  {{cmd CHGPF}}, {{cmd DLTF}}, {{cmd CLRPFM}}, copies and imports into it) needs `*CHANGE` on the library: its owner,
  `*SECOFR`, or a `*CHANGE` library ({{msg ELC0401}}); ELSYS never ({{msg ELC0205}}). ELSYS's system files need the
  Firewall's view permission ({{msg ELC0401}}).
- **Saving.** Files and their records are saved with the system. {{cmd SAVLIB}} writes a library's files and records to
  the diskette with its members (their types kept) and programs, and {{cmd RSTLIB}} puts them back; each record counts
  its record length plus one against the diskette's {{config elcl.disketteBytes}}.
  [Folder sync](page:elcl/libraries-and-editor#folder-sync)
  writes `PF` members as `<MEMBER>.pf`; records don't sync (use {{cmd CPYTOIMPF}}).
- **Display Panels** can show a file as a table: `SNDDSPWDG WDG(*TABLE) FILE() QRYSLT() SORT()` (see
  {{page control/display-panels}} and {{cmd SNDDSPWDG}}).

## Example: logging item counts (ITEMSETUP, LOGITEMS) {#db-example legacy="db-example"}

`ELSYS` ships a worked example: `CALL ELSYS/ITEMSETUP` sets it all up. It makes `ELGPL/ITEMHIST` from the definition
`ELSYS/ITEMHIST` (keeping its records if it's there already), compiles `ELSYS/LOGITEMS` into `ELGPL`, and adds a
schedule entry that runs it every 10 minutes as a batch job (which needs a
[job host](page:elcl/jobs-and-scheduling)). Copy the members to a library of your own to change them. If
`ELGPL/ITEMHIST` is there already, running `ITEMSETUP` again leaves it as it is: use {{cmd CHGPF}} to bring in a
changed definition.

{{example ITEMHIST.pf}}

{{example ITEMSETUP.elclp}}

{{example LOGITEMS.elclp}}

Each run writes one record an item, all with the same `LOGGED` time (the key keeps them apart), then prints that run's
items under a stack, fewest first, to Work with Output. To see the history: `RUNQRY FILE(ELGPL/ITEMHIST)`, or for one
item `RUNQRY ELGPL/ITEMHIST QRYSLT('ITEM *EQ "IRON_INGOT"')`; `CPYTOIMPF FILE(ELGPL/ITEMHIST) TOSTMF('itemhist.csv')`
takes it to a spreadsheet. `RMVJOBSCDE JOB(LOGITEMS)` stops the logging.
