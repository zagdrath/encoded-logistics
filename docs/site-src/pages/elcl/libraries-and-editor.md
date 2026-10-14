---
title: Libraries and the editor
section: elcl
order: 30
summary: Libraries, source members and programs; the source editor and its line commands; compiling and the compile listing; storage cost, folder sync and saving to diskette.
legacy: [libraries]
---
Your programs live in **libraries**: you write their source in **members**, edit them in the source editor, and
compile them into **programs**. This page covers all of that, then how source takes network storage, how a library
can be edited outside the game, and how to carry one to another network on a diskette.

## Libraries and members {#libs-members legacy="libs-members"}

- **Library.** A named container of source members, programs and [files](page:elcl/database-files). Names are up to 10
  characters: a letter, then letters, digits, `_`, `@`, `#` or `$`. Types are `*PROD` or `*TEST` (`CRTLIB TYPE()`),
  and `*SYS` for ELSYS. A library records its owner, public authority (`*USE` or `*CHANGE`), text and when it was
  made. Make one with {{cmd CRTLIB}} (or F6 on Work with Libraries), change it with {{cmd CHGLIB}}, delete it with
  {{cmd DLTLIB}}.
- **`ELSYS`.** The read-only system library that ships with the mod. It holds the sample programs `ARCHIVE`,
  `ITEMSETUP`, `LOGITEMS`, `NOCWALL`, `RESTOCK`, `SORTDEMO` and `UPSALERT`, the file definition `ITEMHIST` (a `PF`
  member), and the live [system files](page:elcl/database-files#db-objects). The samples come compiled, so
  `CALL ELSYS/RESTOCK PARM('IRON_INGOT' 500)` works on a new system; the exception is `LOGITEMS`, which declares
  `ELGPL/ITEMHIST` and is compiled into `ELGPL` by `ITEMSETUP` (see
  [the example](page:elcl/database-files#db-example)). Browse them with 5=Display, copy them with 3=Copy, compile them
  into one of your libraries. ELSYS is rebuilt from the mod every time the system loads, and never costs storage. The
  samples are explained on {{page elcl/examples}}.
- **`ELGPL`.** The general purpose library on every system, public authority `*CHANGE`. It can't be deleted.
- **Source member.** Type `ELCLP` for a program's source, `PF` for a [file's definition](page:elcl/database-files#db-define).
  Lines of up to 80 columns, each with a sequence number in 6.2 form (`0012.00`, up to `9999.99`) and a change date
  (the game day it last changed; lines that came with a sample and never changed have none). A member's text is its
  description. A member holds at most {{config elcl.maxSourceLines default}} lines: saving, syncing, copying or
  restoring a longer one is {{msg ELC0004}}. Make one with {{cmd CRTMBR}} (`SRCTYPE(ELCLP)` or `SRCTYPE(PF)`); copy,
  rename and delete with {{cmd CPYMBR}}, {{cmd RNMMBR}} and {{cmd DLTMBR}}.
- **Program.** A compiled `*PGM`, recording its source member, the source it was compiled from and its compile date.
  It runs with {{cmd CALL}} (see {{page elcl/language}}). Changing the member afterwards doesn't change the program:
  Work with Members flags the member with `*` in the Chg column until you compile it again. {{cmd DLTPGM}} deletes
  one.
- **Library list.** Each user profile has a library list, `ELGPL ELSYS` to start with. A bare name or `*LIBL/NAME` is
  looked for in those libraries in that order; if it isn't found, the current library is used (a new member goes
  there). `*CURLIB` is the profile's current library, `ELGPL` until you choose another on the
  [Sign On](page:elcl/index#sign-on) screen, which also puts it first on the list. `CALL` looks for programs down the
  same list, whether the name is bare, `*LIBL/NAME` or `*CURLIB/NAME` ({{msg ELC0203}} naming `*LIBL` if none has it).
- **Edit locks.** One user edits a member at a time. Opening it in the editor while someone else has it gives
  {{msg ELC0208}} (`Member &1 is locked by another user.`) and the editor opens read-only; saving or deleting a member
  someone else has open gives the same message. Locks are released when you leave the editor or close the terminal,
  and aren't kept over a restart.

Libraries, members and programs are saved with the system (see [what is saved](page:elcl/index#persistence)). Who may
change what is under [library authority](page:elcl/index#security). The screens are
[Work with Libraries](page:elcl/screens#scr-wrklib) and [Work with Members](page:elcl/screens#scr-wrkmbr).

## The source editor (EDTMBR) {#editor legacy="editor"}

Open a member with WRKMBR option 2, or `EDTMBR MBR(LIB/NAME)` ({{cmd EDTMBR}}).

{{layout 05_source_editor caption="The editor after Enter found an undeclared variable on line 0009 (shown reversed in the game)."}}

- Row 1: the member (`LIB/NAME`) and its type. Row 2: the columns shown, and the cursor's line and column. Row 3: the
  column ruler, when it's on (`COLS` or F15).
- Each source row has a 7-character **margin** holding the sequence number (type a line command over it) and the
  line's **text**, 72 of its 80 columns at a time. A new line shows `'''''''` in the margin until Enter numbers every
  line typed on; the open blank line under it keeps `'''''''` until it's typed on. A new (empty) member opens on a
  page of open lines, kept full as you type. A blank line left between lines you typed is kept as a blank line of the
  member; open lines never typed on aren't saved.
- While a program has no `ENDPGM` yet, the check doesn't flag the unclosed `PGM`, `DO` or `SELECT` on the line last
  typed (compiling still does).
- Up and Down move the cursor a row, keeping its column; Tab moves from field to field.
- *Beginning of data* and *End of data* mark the ends; a run of excluded lines shows as one row,
  *N data records excluded*. The *Beginning of data* row takes the line commands `I`, `A` and `COLS`, to add lines
  at the top.
- Type over the text to change it. The Insert key switches between overwrite and insert.
- Opened read-only (WRKMBR 5=Display, a member in `ELSYS`, a library you can only read, or a member someone else is
  editing) the title is *Display Member*, every field is protected, the command line takes only `FIND`, `TOP` and
  `BOTTOM`, and the keys are *F1=Help F3=Exit F5=Refresh F12=Cancel*.
- A `PF` member (a [file's definition](page:elcl/database-files#db-define)) is edited the same way and checked as a
  definition, one line at a time; it has no prompter.
- **Pasting** (<kbd>Ctrl</kbd>+<kbd>V</kbd>) several lines puts the first at the cursor and the rest in as new lines
  after it: tabs become four blanks, other unprintable characters are dropped, and lines are cut at 80 columns
  (*N lines pasted.*, or *N lines pasted; M cut at column 80.*).

### Line commands {#editor-line-commands legacy="editor-line-commands"}

Type a line command over a line's sequence number; Enter applies every line command on the screen, top to bottom. You
only need to type the part that differs: typing `i3` over `0012.00` (leaving `i312.00`) is `I3`. Counts go up to 9999.

| Command | What it does |
|---|---|
| `I`, `In` | Insert 1 / *n* blank lines after this one. |
| `D`, `Dn` | Delete this line / *n* lines from here. |
| `DD` ... `DD` | Delete the block between the two marks (inclusive). |
| `C`, `CC` ... `CC` | Copy this line / the block; then mark the target with `A` or `B`. |
| `M`, `MM` ... `MM` | Move this line / the block; then mark the target with `A` or `B`. The target can't be inside the block (*The target is inside the lines being moved.*). |
| `A`, `B` | Target: after / before this line. |
| `R`, `Rn` | Repeat this line once / *n* times. |
| `RR` ... `RR` | Repeat the block once. |
| `X`, `Xn` | Exclude (hide) this line / *n* lines. |
| `S` or `F` | Typed on an excluded run's row: show those lines again. (FIND also reveals a line it lands on.) |
| `COLS` | Column ruler on or off. |

A copy or move waiting for its target, or a block waiting for its second mark, stays pending (*Block command
pending.*, its mark shown in the margin) across Enter and paging until it's completed; F12 or `RESET` drops it. An
unknown command gives *Line command X not valid.*

### Editor commands {#editor-commands legacy="editor-commands"}

Typed on the editor's command line:

| Command | What it does |
|---|---|
| `FIND string [NEXT\|PREV\|FIRST\|LAST\|ALL]` (or `F`) | Finds text, any case, from the cursor (`NEXT`, the default, wraps round to the top). `ALL` counts them and goes to the first: *N occurrences of X found.* Otherwise *String X found.* or *String X not found.* F16 repeats the last FIND. |
| `CHANGE from to [ALL]` (or `C`) | Changes the next occurrence (any case) from the cursor, or with `ALL` every one from the top: *N occurrence(s) of X changed.*, or *String X not found.* A change that would make a line longer than 80 columns is skipped. F17 repeats the last CHANGE, one occurrence at a time. |
| `TOP`, `BOTTOM` (or `BOT`) | Go to the first / last page. |
| `SAVE` | Saves the member: {{msg ELC0213}} (`Member &1 saved in library &2.`). |
| `FILE` | Saves and leaves the editor. |
| `CANCEL` (or `CAN`) | Leaves without saving; with changes it asks first (*Member X has changes that will be lost. Press Enter to exit without saving, or F12 to go on editing.*). |
| `RESET` (or `RES`) | Drops pending line commands and the error highlight. |
| anything else | Runs as an ELCL command (F4 prompts it). |

Strings with blanks go in quotes: `FIND 'RTNCOUNT(&COUNT'`; `''` is a quote inside one. With no string, FIND and
CHANGE say what they need (*FIND string [NEXT|PREV|FIRST|LAST|ALL]: type the string to find.*, *CHANGE from to
[ALL]: type both strings.*). Every editor command first does what Enter does: it applies the line commands on the
screen and checks the changed statements.

### Editor keys {#editor-keys legacy="editor-keys"}

| Key | What it does |
|---|---|
| Enter | Apply line commands, then check the changed statements (see [syntax check](#syntax-check)). |
| F3 | Leave. Without changes, straight out. With changes, a window: *Member X has changes. Press Enter to save them and exit, F11 to exit without saving, or F12 to go on editing.* |
| F4 | On a source line: prompt the statement that line belongs to (see below). On the command line: prompt the command typed there. |
| F5 | Reload the member from the server (only when there are no unsaved changes). |
| F10 | Cursor to the command line. |
| F11 | **Full screen**: all 80 columns, no margins, no line commands and no command line (title *Edit Member - Full screen*, keys *F3=Exit F11=Margin F12=Cancel*). F11 again returns to the margin view. Not available read-only. |
| F12 | Drop a pending block command; with none pending, leave as F3 does (asking to save if there are changes). |
| F15 | Column ruler on or off. |
| F16 / F17 | Repeat the last FIND / CHANGE. |
| F19 / F20 | Show columns 1–72 / 9–80 (a shift of 8). Not in full-screen mode, which shows all 80. |
| Page Up / Page Down | Previous / next page. |

{{layout 05b_source_editor_fullscreen caption="Full-screen editing (F11)."}}

**F4 statement rewrite.** F4 on a source line opens the [prompter](page:elcl/using-the-terminal#prompter) for the
statement on that line (variables are allowed in its values). When you press Enter there, the statement's lines are
replaced by the prompter's command, keeping the original indentation and label, wrapped with ` +` before column 72,
continuation lines indented two more blanks. A line with no statement gives *No statement on this line to prompt.*

**Numbering and saving.** Enter numbers the new lines you typed, between their neighbours (steps of up to 1.00); if
there's no room, or the numbers would pass 9999.99, the whole member is renumbered in steps of 1.00. Saving keeps
every unchanged line's sequence number and date; changed lines get today's game day.

## Syntax check {#syntax-check legacy="syntax-check"}

Each Enter checks the statements you changed (with their continuation lines) through the ELCL parser and compiler.
The first error in a changed statement is shown reversed, the cursor goes to it, and its message goes on the message
line, for example `ELC0002: Variable &CONT is not declared.` Only errors are shown; warnings (such as
{{msg ELC0008}}) wait for the compile listing. Saving is still allowed with errors; compiling is what refuses them.

## Compiling {#compiling legacy="compiling"}

Compile with WRKMBR option 14, or {{cmd CRTELPGM}} `PGM(LIB/NAME) SRCMBR(*PGM|LIB/NAME)`. `SRCMBR(*PGM)` (the default)
compiles the member with the program's own name; a bare `PGM(NAME)` goes to the library the member is found in down
your library list. `TGT(*PLC)` compiles for a [PLC](page:elcl/control-and-plcs) instead of a job. The program's
library must be one you can change (compiling into `ELSYS` is {{msg ELC0205}}), and a `PF` member is compiled with
{{cmd CRTPF}}, not `CRTELPGM` ({{msg ELC2247}}). The compiler checks:

- `PGM` first and `ENDPGM` last ({{msg ELC0009}}), nothing after `ENDPGM` and no second `PGM` ({{msg ELC0001}});
  `DCL`s and `DCLF`s before anything else ({{msg ELC0016}}); program-level `MONMSG`s straight after them; subroutines
  after the main body ({{msg ELC0001}}).
- Every command known ({{msg ELC0101}}) and every variable declared ({{msg ELC0002}}) and of a type its use takes
  ({{msg ELC0003}}). Declarations valid: no duplicates ({{msg ELC0103}}); `*CHAR` length 1–1024 (32 if not given),
  `*DEC` length 1–31 with no more decimals than the length (15 5 if not given) ({{msg ELC0004}}); `LEN` only on
  `*CHAR` and `*DEC`, and no `VALUE` on a `*LIST` ({{msg ELC0103}}).
- Every command's parameters against its schema: required ({{msg ELC0102}}), special values and kinds
  ({{msg ELC0103}}), ranges ({{msg ELC0004}}), not given twice ({{msg ELC0104}}), `RTN*` parameters given variables
  of the right type.
- Blocks matched (`DO`/`ENDDO`, `FOREACH`/`ENDFOR`, `SELECT`/`ENDSELECT`, `SUBR`/`ENDSUBR`), `ELSE` after its `IF`,
  only `WHEN` and one `OTHERWISE` directly inside a `SELECT`, `LEAVE` and `ITERATE` inside a loop ({{msg ELC0009}}).
- Every `GOTO`, `LEAVE` or `ITERATE` label and every `CALLSUBR` subroutine exists ({{msg ELC0010}}), labels are unique
  ({{msg ELC0001}}), and a `GOTO` doesn't jump into a block (a loop, `DO` group, `SELECT` or subroutine) it's outside
  of ({{msg ELC0014}}).
- At most five files declared ({{msg ELC2224}}), each one there when you compile ({{msg ELC2205}}).
- Literal problems it can see: division by a literal zero ({{msg ELC0005}}), numeric overflow ({{msg ELC0007}}), a
  literal too long for a `*CHAR` ({{msg ELC0008}}, a warning).

Any message of severity 20 or more means no program: {{msg ELC0206}} (`Program &1 was not created: compile errors.`).
Otherwise {{msg ELC0218}} (`Program &1 created in library &2.`). Either way the listing goes to Work with Output as a
spooled file named after the program, in your interactive job.

> [!TIP]
> Run a compiled program with `CALL PGM(LIB/NAME)` on any command line, or submit it as a batch job with
> `SBMJOB CMD(CALL PGM(LIB/NAME))`. See {{page elcl/language}} and {{page elcl/jobs-and-scheduling}}.

## The compile listing {#listing legacy="listing"}

Shown with [Display Spooled File](page:elcl/screens#scr-dspsplf). In order:

1. A header: `ELCL Compile Listing   LIB/NAME   Day 2 07:13   SYSNAME`.
2. The source: `SEQNBR`, the column ruler and `Date`, then each line with its sequence number and (past column 80) its
   change date. Use F20 to see the dates.
3. **Cross Reference**: each variable with its type, length (`5 1` for a `*DEC`) and the sequence numbers that use it,
   `*` where it's set. Then labels (`*LABEL`) and subroutines (`*SUBR`) with their references.
4. **Message Summary**: each message's sequence number, ID, severity and text; a totals line
   `Total n  Info n  Warning n  Error n  Severe n`; and the result, {{msg ELC0218}} or {{msg ELC0206}}.

{{layout 07_compile_listing}}

Severities: `00` information, `10` warning, `20` error, `30` severe, `40` abnormal end.

## Storage cost {#storage-cost legacy="storage-cost"}

Source members take room on the network's drives: one byte of drive space per {{default elcl.charsPerStorageByte}}
characters of source, rounded up per member ({{config elcl.charsPerStorageByte}}). `ELSYS`, compiled programs, job
logs and spooled files cost nothing. A file's records cost the same way (see
[database files](page:elcl/database-files#db-limits)).

- Saving a member, copying or restoring one, or a folder-sync import, that needs more than the drives have free fails
  with {{msg ELC0207}} (`Not enough network storage to save &1.`). A network with no drives at all can only hold empty
  members.
- The bytes the members (and files' records) take count as used hot storage on Display Network Status, in {{cmd RTVSTGSTS}} and for the
  `*STGFULL` trigger. Work with Libraries 5=Display shows a library's size in characters.
- The space is counted, not reserved. Items going into the network don't check for it, so a network can fill its
  drives with items past the point where its members fit; then only saves that make a member smaller (or delete one)
  go through until space is freed.

## Folder sync {#folder-sync legacy="folder-sync"}

A system's libraries can also live as plain files in the world save, so you can edit members in any text editor or
keep them under version control:

```text
<world>/encodedlogistics/libraries/<SYSNAME>/<LIB>/<MEMBER>.elclp   (a program's source)
<world>/encodedlogistics/libraries/<SYSNAME>/<LIB>/<MEMBER>.pf      (a file's definition)
```

- **Out:** saving a member in the editor, and creating, copying, renaming or restoring one, writes its file at once.
- **In:** while the network is loaded the folder is read every two seconds (and members never written out yet are
  written then). A new or changed file becomes the member:
  lines that didn't change keep their sequence numbers and change dates, changed and new lines are numbered between
  their neighbours and dated today (if there's no room, the member is numbered again in steps of 1.00). A new folder
  becomes a library, owned by the system (`QSYS`) with public authority `*CHANGE`.
- **Conflicts:** the system remembers what it last synced for each member, so a file and a member that both changed
  since are a conflict. The last write wins (an in-game save is always the later one), and the losing version is kept
  beside it as `<MEMBER>.elclp.bak` (or `.pf.bak`).
- **Ignored:** folders and files whose names aren't valid library or member names, or that start with `.`; and a
  `.pf` file beside a program member of the same name (or the other way round). An import that fails (no storage,
  {{msg ELC0207}}, or too many lines, {{msg ELC0004}}) is logged on the server and tried again on the next read.
- **Deletes:** deleting a file never deletes its member (for safety). Deleting a member in the game moves its file to
  `<LIB>/.deleted/`.
- `ELSYS` is never synced, in or out: it comes from the mod.
- A file's records don't sync: {{cmd CPYTOIMPF}} and {{cmd CPYFRMIMPF}} copy them as CSV to and from the system's
  folder (see [database files](page:elcl/database-files#db-copy)), only while folder sync is on.
- The folder is named after `SYSNAME`, so changing that system value starts a new folder.
- {{config elcl.allowFolderSync default}} turns it on and off: `AUTO` is on in single-player and off on dedicated
  servers; `TRUE` or `FALSE` force it. See {{page admin/folders}}.

## Saving to diskette (SAVLIB / RSTLIB) {#savlib legacy="savlib"}

`SAVLIB LIB(ZAGLIB) DEV(MIDRANGE01)` ({{cmd SAVLIB}}) writes a library (its members with their types, sequence numbers
and dates, its programs, and its files with their records) to the {{item diskette_8in}} in a diskette drive;
`RSTLIB LIB(ZAGLIB) DEV(MIDRANGE01)` ({{cmd RSTLIB}}) restores it, on this system or any other. Diskettes are items, so
a library can be carried to another network or handed to another player.

- `SAVLIB` writes to the first diskette in the drive (on a Midrange System, its default drive first; see
  {{page crafting/midrange}}), replacing a library of the same name on it: {{msg ELC0220}}
  (`Library &1 saved to &2.`, where &2 is the device and the diskette's label). A diskette holds {{config elcl.disketteBytes default}}
  bytes. What counts is each member's characters plus one a line, each program's saved source the same way again, and
  each file's records; a library that won't fit in what's free is {{msg ELC1311}}.
- `RSTLIB` reads from the first diskette holding that library ({{msg ELC0201}} if none does). It creates the library,
  owned by you with public authority `*USE`, or replaces an existing one's members, programs and files (members that
  aren't on the diskette are deleted; that needs `*CHANGE` on it: {{msg ELC0401}}). It never writes to `ELSYS`
  ({{msg ELC0205}}), and the members must fit in the network's storage ({{msg ELC0207}}). Programs come back as their
  saved source and are compiled again when first used. Done: {{msg ELC0221}}.
- No such device is {{msg ELC1301}}, an offline one {{msg ELC1302}}, a drive with no diskette {{msg ELC1310}}.

> [!NOTE]
> The diskette drives are the {{item midrange_system}} (its diskette slot, and a second with an
> {{item expansion_cabinet}}), the {{item integrated_midrange}} (every diskette in its magazine) and the
> {{item card_reader}} (one diskette). The Midrange Systems are named `MIDRANGE01` and so on, the Card Reader
> `CARDRDR01`, unless renamed; a Midrange System's drives are online only while it's running. The library is kept on
> the diskette item, so it goes wherever the diskette goes. See {{page crafting/midrange}}.
