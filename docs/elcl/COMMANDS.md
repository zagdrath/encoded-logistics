# ELCL Command Reference

Conventions: **Req** = required. Positional order is shown as `P1`, `P2`...
`RTN*` parameters must be variables of the stated type. "Context" is where
the command may run: `I` interactive, `B` batch, `IB` both. "Auth" is the
Firewall permission needed (OS.md §6). Every command can also raise the
general errors ELC0101–ELC0106 and ELC0401.

## 1. Program control

| Command | Parameters | Context | Notes |
|---------|-----------|---------|-------|
| `PGM` | `PARM(&v ...)` | — | Program start |
| `ENDPGM` | — | — | Program end |
| `DCL` | `VAR`(P1, Req) `TYPE`(P2, Req) `LEN` `VALUE` | — | Declaration |
| `CHGVAR` | `VAR`(P1, Req) `VALUE`(P2, Req) | IB | Assignment |
| `IF` `ELSE` `DO` `ENDDO` `DOWHILE` `DOUNTIL` `DOFOR` `FOREACH` `ENDFOR` `LEAVE` `ITERATE` `SELECT` `WHEN` `OTHERWISE` `ENDSELECT` `GOTO` `RETURN` `SUBR` `ENDSUBR` `CALLSUBR` | see spec §6 | IB | |
| `MONMSG` | `MSGID`(P1, Req, list) `CMPDTA` `EXEC` | IB | Spec §8 |
| `RCVMSG` | `MSGTYPE(*EXCP\|*LAST)` `RTNMSGID` `RTNMSG` `RTNMSGDTA` | IB | |
| `SNDPGMMSG` | `MSG` `MSGID(USRnnnn)` `MSGTYPE(*INFO\|*ESCAPE)` | IB | |
| `CALL` | `PGM`(P1, Req, qualified) `PARM`(P2, list) | IB | Spec §7 |
| `DLYJOB` | `DLY`(P1, seconds, 1–86400) or `RSMTIME(HHMMSS)` (game clock) | IB | Async |
| `RTVJOBA` | `RTNUSR` `RTNJOB` `RTNTYPE` (*CHAR: `*INTER`/`*BATCH`) `RTNHOST` | IB | Current job |

## 2. Lists [EXT]

| Command | Parameters | Notes |
|---------|-----------|-------|
| `ADDLSTE` | `LIST`(P1, Req) `VALUE`(P2, Req) `POS(*END\|n)` | Add element |
| `RMVLSTE` | `LIST`(P1, Req) `POS`(P2, Req) | Remove element n |
| `CLRLST` | `LIST`(P1, Req) | Empty the list |

## 3. Inventory

| Command | Parameters | Auth | Errors |
|---------|-----------|------|--------|
| `RTVITMCNT` | `ITEM`(P1, Req) `TIER(*ALL\|*HOT\|*COLD)` `RTNCOUNT`(*INT, Req) | view | ELC1201 (returns 0 instead if `NOTFND(*ZERO)` given), ELC1205 |
| `RTVITMLST` | `FILTER`(P1, default `*ALL`; text with `*` wildcards or `#tag`) `TIER` `MAX(*NOMAX\|n)` `SORT(*NAME\|*QTY)` `RTNLST`(*LIST, Req) | view | — |
| `MOVITM` | `ITEM`(P1, Req) `QTY`(P2, Req, n or `*ALL`) `TODEV`(P3, Req, device name or `*DESK`) `PARTIAL(*YES\|*NO)` `RTNMOVED`(*INT) | extract | ELC1201, ELC1202 (only if PARTIAL(*NO)), ELC1301, ELC1302, ELC1304 |
| `IMPITM` | `FROMDEV`(P1, Req) `ITEM(*ALL\|id)` `QTY(*ALL\|n)` `RTNMOVED`(*INT) | insert | ELC1204, ELC1301, ELC1302, ELC1304 |
| `CHGITMTIER` | `ITEM`(P1, Req) `TIER`(P2, Req: `*HOT\|*COLD\|*PIN\|*AUTO`) | configure | ELC1201, ELC1206 (no Tape Library) |
| `RTVSTGSTS` | `TIER(*ALL\|*HOT\|*COLD)` `RTNUSED` `RTNTOTAL` (*INT) `RTNPCT` (*DEC) | view | — |

`*DESK` = the drawer of the Terminal Desk running the job (interactive only).
Cold items requested by `MOVITM` trigger a recall; the job waits (async).

## 4. Crafting

Crafting jobs have IDs like `C0042` and are separate from script jobs.

| Command | Parameters | Auth | Errors |
|---------|-----------|------|--------|
| `STRCRAFT` | `ITEM`(P1, Req) `QTY`(P2, Req) `SCHEDULER(*ANY\|name)` `MISSING(*FAIL\|*PARTIAL)` `WAIT(*NO\|*YES)` `RTNCRFJOB`(*CHAR) | craft | ELC1401, ELC1402, ELC1403 |
| `RTVCRFSTS` | `CRFJOB`(P1, Req) `RTNSTS`(*CHAR: `*QUEUED\|*ACTIVE\|*DONE\|*FAILED\|*CANCELLED`) `RTNPCT`(*DEC) | view | ELC1404 |
| `ENDCRAFT` | `CRFJOB`(P1, Req) | craft | ELC1404 |

## 5. Devices

Devices are addressed by name (Label Maker or Work with Devices); defaults are
type + number: `INGRESS01`, `EGRESS02`, `NAS01`, `UPS01`, `CTLIF01`. A name is
stored with the device, given once (type + the lowest number free on the
system) and kept until renamed; it goes with the device's item. *(Implemented:
Control Interfaces, Terminal Desks and rack devices; the mod has no Label Maker.)*

| Command | Parameters | Auth | Errors |
|---------|-----------|------|--------|
| `RTVDEVSTS` | `DEV`(P1, Req) `RTNSTS`(*CHAR: `*ONLINE\|*OFFLINE\|*FAULT\|*DISABLED`) `RTNTYPE`(*CHAR) | view | ELC1301 |
| `RTVDEVLST` | `TYPE(*ALL\|type)` `STATUS(*ALL\|status)` `RTNLST`(*LIST) | view | — |
| `CHGDEVSTS` | `DEV`(P1, Req) `STATUS`(P2, Req: `*ENABLE\|*DISABLE`) | configure | ELC1301, ELC1303 |
| `CHGDEVFTR` | `DEV`(P1, Req) `ACTION`(P2, Req: `*ADD\|*RMV\|*CLR`) `ITEM` (Req unless *CLR) | configure | ELC1301, ELC1303, ELC1305 (filter full) |
| `RNMDEV` **[EXT]** | `DEV`(P1, Req) `NEWNAME`(P2, Req) | configure | ELC1301, ELC0103, ELC1308 (name in use); ELC1309 on success |
| `RTVLANES` | `RTNUSED` `RTNTOTAL` (*INT) | view | — |

## 6. Power

| Command | Parameters | Auth |
|---------|-----------|------|
| `RTVPWRSTS` | `RTNSRC`(*CHAR: `*NETWORK\|*UPS\|*NONE`) `RTNCHG`(*DEC, UPS %) `RTNLOAD`(*INT, FE/t) `RTNSTORED`(*INT, FE) | view |

## 7. Redstone (Control Interface block)

| Command | Parameters | Auth | Errors |
|---------|-----------|------|--------|
| `RTVRSIN` | `DEV`(P1, Req) `SIDE`(P2, Req: `*NORTH\|*SOUTH\|*EAST\|*WEST\|*UP\|*DOWN\|*MAX`) `RTNLVL`(*INT, 0–15) | view | ELC1301, ELC1302, ELC1303 |
| `CHGRSOUT` | `DEV`(P1, Req) `SIDE`(P2, Req, or `*ALL`) `LVL`(P3, Req, 0–15) | configure | ELC1301, ELC1302, ELC1303, ELC0004 |

## 8. Messages, displays and output

| Command | Parameters | Context | Notes |
|---------|-----------|---------|-------|
| `SNDMSG` | `MSG`(P1, Req) `TOUSR(*REQUESTER\|*SYSOPR\|*ALL\|user)` `TOTRM(name)` | IB | `*SYSOPR` = network owner. Also shows a chat notice to online recipients (config). |
| `DSPMSG` | `USR(*CURRENT\|user)` | I | Opens Display Messages screen |
| `SNDDSPTXT` | `DEV`(P1, Req) `TEXT`(P2, Req) `LINE(*NEXT\|n)` `CLEAR(*NO\|*YES)` | IB | Status Display, NOC Video Wall, Rack Console. ELC1301, ELC1303 |
| `PRTTXT` | `TEXT`(P1, Req) `SPLF(*JOB\|name)` | IB | Writes a line to a spooled file |
| `PRTRPT` | `RPT`(P1, Req: `*INV\|*DEV\|*JOBLOG\|*SPLF`) `SPLF(name)` `DEV(*DFT\|printer)` | IB | Line Printer. ELC1301, ELC1306 (out of paper) |

## 9. OS commands

| Command | Parameters | Context |
|---------|-----------|---------|
| `WRKLIB` / `CRTLIB LIB() TEXT()` / `DLTLIB LIB()` | | I / IB / IB |
| `WRKMBR LIB()` / `EDTMBR MBR(LIB/NAME)` | | I |
| `CPYMBR FROM() TO()` / `RNMMBR MBR() NEWNAME()` / `DLTMBR MBR()` | | IB |
| `CRTELPGM PGM(LIB/NAME) SRCMBR(*PGM\|LIB/NAME)` / `DLTPGM PGM()` | | IB |
| `SBMJOB CMD(command) JOB(name) HOST(*ANY\|device)` | Batch job; ELC0301 if no host | IB |
| `WRKACTJOB` / `WRKJOB JOB()` / `DSPJOBLOG JOB(*\|id)` | | I |
| `HLDJOB JOB()` / `RLSJOB JOB()` / `ENDJOB JOB() OPTION(*CNTRLD\|*IMMED)` | | IB |
| `ADDJOBSCDE JOB() CMD() FRQ(*ONCE\|*INTERVAL\|*DAILY) TIME(HHMM) INTERVAL(seconds)` / `RMVJOBSCDE JOB()` / `WRKJOBSCDE` | game-clock TIME, real-time INTERVAL (see HANDOFF open question 2) | IB / IB / I |
| `ADDTRGEVT TRG() EVENT() PGM() ITEM() DEV() VALUE()` / `RMVTRGEVT TRG()` / `WRKTRGEVT` | events below | IB / IB / I |
| `WRKSYSVAL` / `RTVSYSVAL SYSVAL() RTNVAR()` / `CHGSYSVAL SYSVAL() VALUE()` | OS.md §7 | I / IB / IB |
| `SAVLIB LIB() DEV()` / `RSTLIB LIB() DEV()` | 8" Diskette in a Midrange System or Card Reader | IB |
| `WRKDEV` / `WRKINV` / `DSPNETSTS` | existing screens | I |
| `SIGNOFF` | | I |

**Trigger events** (`ADDTRGEVT EVENT(...)`). The triggered program receives
two parameters: `&EVENT` (*CHAR 10) and `&DATA` (*CHAR 256).

| Event | Uses | `&DATA` |
|-------|------|---------|
| `*ITMBELOW` / `*ITMABOVE` | `ITEM`, `VALUE` | item id and new count |
| `*STGFULL` | `VALUE` (percent) | percent used |
| `*DEVFAULT` / `*DEVONLINE` / `*DEVOFFLINE` | `DEV` (or `*ANY`) | device name |
| `*PWRUPS` / `*PWRRESTORED` | — | UPS charge % |
| `*CRAFTEND` | `ITEM` (or `*ANY`) | craft job id and status |
| `*RSCHANGE` | `DEV`, optional `VALUE` (side) | side and new level |

Triggers are edge-triggered (fire once per crossing) and debounced
(minimum 1 second between firings of the same trigger).

## 10. Aliases for the existing CLI

The existing Terminal Desk CLI commands stay as aliases:

| Existing | ELCL |
|----------|------|
| `help` | `F1` help / `GO HELP` |
| `show inventory [f]` | `WRKINV` (with filter) |
| `show drives` | `WRKDEV` filtered to storage |
| `show lanes` | `DSPNETSTS` lanes section |
| `show jobs` | `WRKACTJOB` |
| `show devices` | `WRKDEV` |
| `show power` | `DSPNETSTS` power section |
| `withdraw <item> <n>` | `MOVITM ITEM() QTY() TODEV(*DESK)` |
| `craft <item> <n>` | `STRCRAFT` |
| `cancel job <id>` | `ENDCRAFT` |
| `clear` | `CLEAR` (clears command entry history) |
