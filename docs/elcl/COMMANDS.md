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
| `DLYJOB` | `DLY`(P1, seconds, 1–86400) or `RSMTIME(HHMMSS)` (game clock) | IB | Async (seconds are game ticks / 20); on a command line it's done at once |
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
*(Implemented: a device for `MOVITM`/`IMPITM` is a cable part facing an inventory - an
Egress or Ingress Port, an Inventory Tap. The mod has no per-item tier, so
`CHGITMTIER` works through the Tape Libraries' lists: `*HOT` adds the item to a
library's keep-hot filter and recalls what's on tape, `*PIN` pins it, `*COLD`
archives it on the next pass whatever its age, `*AUTO` takes it off those lists;
ELC1305 when the list is full.)*

## 4. Crafting

Crafting jobs have IDs like `C0042` and are separate from script jobs. Jobs that ended stay in the network's crafting
job history (the last `CRFLOGRTN`): `RTVCRFSTS` answers `*DONE`, `*FAILED` or `*CANCELLED` for them (ELC1404 once they
have aged out), and `RTVCRFLOG` returns their IDs, newest first, filtered by item and status.

| Command | Parameters | Auth | Errors |
|---------|-----------|------|--------|
| `STRCRAFT` | `ITEM`(P1, Req) `QTY`(P2, Req) `SCHEDULER(*ANY\|name)` `MISSING(*FAIL\|*PARTIAL)` `WAIT(*NO\|*YES)` `RTNCRFJOB`(*CHAR) | craft | ELC1401, ELC1402, ELC1403 |
| `RTVCRFSTS` | `CRFJOB`(P1, Req) `RTNSTS`(*CHAR: `*QUEUED\|*ACTIVE\|*DONE\|*FAILED\|*CANCELLED`) `RTNPCT`(*DEC) | view | ELC1404 |
| `RTVCRFLOG` | `ITEM(*ALL\|item)` `STATUS(*ALL\|*DONE\|*FAILED\|*CANCELLED)` `MAX(*NOMAX\|1-999)` `RTNLST`(*LIST, Req) | view | ELC1201 |
| `ENDCRAFT` | `CRFJOB`(P1, Req) | craft | ELC1404 |

## 5. Devices

Devices are addressed by name (Label Maker or Work with Devices); defaults are
type + number: `INGRESS01`, `EGRESS02`, `NAS01`, `UPS01`, `CTLIF01`. A name is
stored with the device, given once (type + the lowest number free on the
system) and kept until renamed; it goes with the device's item. *(Implemented:
Control Interfaces, Terminal Desks, rack devices, cable parts and wireless - `AP01`, `WBRIDGE01`, `WINGRESS01`,
`WEGRESS01`, the Wireless Ports working exactly as cabled ports - Gateways (`GATEWAY01`) and Arcforge machines with a
Small Wireless Bridge on, by a prefix from their type (`ARCCRU01`); the mod has no Label Maker.)*

| Command | Parameters | Auth | Errors |
|---------|-----------|------|--------|
| `RTVDEVSTS` | `DEV`(P1, Req) `RTNSTS`(*CHAR: `*ONLINE\|*OFFLINE\|*FAULT\|*DISABLED`) `RTNTYPE`(*CHAR) | view | ELC1301 |
| `RTVDEVLST` | `TYPE(*ALL\|type)` `STATUS(*ALL\|status)` `RTNLST`(*LIST) | view | — |
| `CHGDEVSTS` | `DEV`(P1, Req) `STATUS`(P2, Req: `*ENABLE\|*DISABLE`) | configure | ELC1301, ELC1303 |
| `CHGDEVFTR` | `DEV`(P1, Req) `ACTION`(P2, Req: `*ADD\|*RMV\|*CLR`) `ITEM` (Req unless *CLR) | configure | ELC1301, ELC1303, ELC1305 (filter full) |
| `RNMDEV` **[EXT]** | `DEV`(P1, Req) `NEWNAME`(P2, Req) | configure | ELC1301, ELC0103, ELC1308 (name in use); ELC1309 on success |
| `RTVLANES` | `RTNUSED` `RTNTOTAL` (*INT) | view | — |
| `RTVMCHSTS` **[EXT]** | `MCH`(P1, Req) `RTNSTS`(*CHAR: `*IDLE\|*RUNNING\|*NOPOWER\|*NOINPUT\|*BLOCKED\|*DISABLED\|*NOTFORMED\|*FAULT\|*UNKNOWN\|*OFFLINE`) `RTNRSN`(*CHAR) `RTNPCT`(*INT, -1 when not in an operation) `RTNFE` `RTNFECAP`(*INT) `RTNRCP`(*CHAR) | view | ELC1301, ELC1318 |
| `RTVMCHSTAT` **[EXT]** | `MCH`(P1, Req) `RTNOPS` `RTNPRD` `RTNCNS` `RTNFLDPRD` `RTNFLDCNS` `RTNUPTIME`(*INT, seconds) `RTNOPM`(*DEC) | view | ELC1301, ELC1302, ELC1318 |
| `RTVMCHLST` **[EXT]** | `TYPE(*ALL\|prefix\|type id)` `STATUS(*ALL\|status)` `RTNLST`(*LIST) | view | — |
| `CHGMCHSTS` **[EXT]** | `MCH`(P1, Req) `STATUS`(P2, Req: `*ENABLE\|*DISABLE`) | configure | ELC1301, ELC1302, ELC1318, ELC1319, ELC1320 |
| `CHGMCHCFG` **[EXT]** | `MCH`(P1, Req) `RSMODE(*SAME\|*IGNORE\|*HIGH\|*LOW\|*PULSE\|*THROTTLE)` `SIDE(*SAME\|*TOP\|*BOTTOM\|*LEFT\|*RIGHT\|*BACK\|*FRONT)` `SIDEMODE(*SAME\|mode)` `AUTOEJECT(*SAME\|*YES\|*NO)` `PWRNET(*SAME\|*YES\|*NO)` `GATEWAY(*SAME\|*NONE\|gateway)` | configure | ELC1301, ELC1302, ELC1303 (not a Gateway), ELC1318, ELC1319, ELC1320, ELC1321, ELC0102 (SIDE without SIDEMODE); ELC1322 on success |

*(Implemented: device types for `TYPE()` and `RTNTYPE` are the name prefixes -
CTLIF, DESK, the rack devices' (UPS, NAS, TAPELIB, ...) and the cable parts'
(INGRESS, EGRESS, TAP, SENSOR, COLLECTOR, DEPLOYER, P2P, TERM, FABTERM,
ENCODER). Only cable parts can be enabled / disabled (a disabled part works as
if offline) and only ports, taps and planes have filters; anything else is
ELC1303.)*

*(Machine commands **[EXT]**: Arcforge machines with a linked Small Wireless Bridge on them, by their device names
(`MCH`). A device that isn't one - or any, with the Arcforge integration off - is ELC1318; a machine gone, unloaded or
off the network ELC1302. Settings go through the machine's own rules: one it doesn't have is ELC1320, a value it
refuses ELC1321, an unformed multiblock ELC1319. `CHGMCHCFG` applies its settings in order and stops at the first
refused. `PWRNET(*YES)`: power from the network (`machinePowerRate`, `machinePowerEfficiency`, `machinePowerReserve`);
`GATEWAY`: the Gateway that feeds the machine and takes its outputs over the air for Processing Schematic jobs.)*

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
| `SNDDSPTXT` | `DEV`(P1, Req) `TEXT`(P2, Req) `LINE(*NEXT\|n)` `CLEAR(*NO\|*YES)` | IB | A Display Panel screen (DSP01). ELC1301, ELC1303. *(Implemented against `elcl.device.DisplayDevice`: a screen has a line per 10 canvas px and a character per 6; longer text is cut, *NEXT past the bottom rolls the lines up)* |
| `CLRDSP` **[EXT]** | `DEV`(P1, Req) `RGN(*ALL\|name)` | IB | Clears a region's widget, or (*ALL) the whole screen: its regions, text and images. ELC1301, ELC1303, ELC1314. |
| `CHGDSPRGN` **[EXT]** | `DEV`(P1, Req) `RGN`(P2, Req, name) `X(*SAME\|n)` `Y(*SAME\|n)` `W(*SAME\|n)` `H(*SAME\|n)` `BG(*SAME\|*DFT\|colour)` | IB | Defines or changes a region in canvas px (a new one: from 0, 0 to the screen's edge by default). ELC1301, ELC1303, ELC1314 (outside the screen or overlapping). |
| `SNDDSPWDG` **[EXT]** | `DEV`(P1, Req) `RGN`(P2, Req) `WDG`(P3, Req: `*STORAGE *COLD *ENERGY *LANES *JOBS *ITEM *CLOCK *DEVICES *UPS *TEXT *NONE`) `ITEM(*NONE\|id)` `DEVTYPE(*ALL\|type)` `COLOR(*DFT\|colour)` | IB | Places a dashboard widget. ELC1301, ELC1303, ELC1314 (no such region), ELC1316 (bad data source). |
| `SNDDSPGPH` **[EXT]** | `DEV`(P1, Req) `RGN`(P2, Req) `STAT`(P3, Req: `*ITEMFLOW *ENERGY *LANES *STORAGE *CRAFTING *ITEM *MCHOPS *MCHFE`) `ITEM(*NONE\|id\|machine)` `RANGE(*1M\|*10M\|*1H\|*1D)` `TYPE(*LINE\|*BAR)` `COLOR(*DFT\|colour)` | IB | Draws a graph (a Monitoring Server's series when there is one; else the screen's own history). `*MCHOPS` / `*MCHFE`: an Arcforge machine's operations a minute / FE stored, `ITEM` its device name. ELC1301, ELC1303, ELC1314, ELC1316, ELC1318. |
| `SNDDSPIMG` **[EXT]** | `DEV`(P1, Req) `RGN`(P2, Req) `FILE`(P3, Req, quoted: `FILE('logo.png')`) `SCALE(*DITHER\|*NEAREST)` `COLORS(*DFT\|16\|64\|256\|*FULL)` | IB | Shows a PNG from `<world>/encodedlogistics/images/<SYSNAME>/`. ELC1301, ELC1303, ELC1312, ELC1313, ELC1314, ELC1315; ELC1317 (a diagnostic) when the colours are above the server's limit. |
| `RTVDSPSIZ` **[EXT]** | `DEV`(P1, Req) `RTNW`(*INT) `RTNH`(*INT) `RTNPXW`(*INT) `RTNPXH`(*INT) | IB | Size in panels and canvas px. ELC1301, ELC1303. |
| `PRTTXT` | `TEXT`(P1, Req) `SPLF(*JOB\|name)` | IB | Writes a line to a spooled file |
| `PRTRPT` | `RPT`(P1, Req: `*INV\|*DEV\|*JOBLOG\|*SPLF`) `SPLF(name)` `DEV(*DFT\|printer)` | IB | Line Printer: a Printout (`elcl/exec/Reports`: 56 columns; *INV item, quantity, tier, location; *DEV name, type, status, lanes, location; *JOBLOG time, job, event). ELC1301, ELC1306 (out of paper) |

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
| `SAVLIB LIB() DEV()` / `RSTLIB LIB() DEV()` | 8" Diskette in a Midrange System or Card Reader. *(Implemented against `DisketteDevice`, docs/elcl/INTERFACES.md: ELC1301 no device, ELC1310 no diskette, ELC1311 too big; RSTLIB makes the library or replaces its members and programs)* | IB |
| `WRKDEV` / `WRKINV` / `DSPNETSTS` | existing screens | I |
| `WRKMCH` **[EXT]** | Work with Machines: 2=Change (`CHGMCHCFG`), 5=Display, 7=Enable/Disable (`CHGMCHSTS`) | I |
| `SIGNOFF` | | I |

**Display colours** (`COLOR`, `BG`): `*DFT`, a panel palette name (`*DARK *STEEL *GREY *SILVER *LIGHT *WHITE *NAVY
*BLUE *CYAN *RUST *ORANGE *YELLOW *GREEN *FOREST *RED *BROWN *MINT`) or `#RRGGBB`. `RANGE`'s `*1M`, `*10M`, `*1H` and
`*1D` are special values (the lexer reads `*` with digits and a letter as one).

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
| `*DSPTOUCH` **[EXT]** | `DEV` (or `*ANY`) | screen, region and canvas point, e.g. `DSP01 A 40 12` |
| `*MCHIDLE` / `*MCHFAULT` / `*MCHNOPWR` **[EXT]** | `DEV` (or `*ANY`): an Arcforge machine goes idle, faults, runs short of power | machine name and the machine's words, e.g. `ARCCRU01 No power` |
| `*MCHDONE` **[EXT]** | `DEV` (or `*ANY`), `ITEM` (or `*ANY`): an Arcforge machine finishes an operation | machine name, count and first thing made, e.g. `ARCCRU01 5 BONE_MEAL` |

Triggers are edge-triggered (fire once per crossing) and debounced
(minimum 1 second between firings of the same trigger).

*(Implemented: the triggered program runs as a batch job, as the trigger's
user - so it needs a job host; without one ELC0301 goes to that user's message
queue, as it does for a schedule entry. Item counts, storage use and power are
looked at every half second; a crossing during the debounce fires when it's
over, if it still holds. `*STGFULL` without a VALUE is 100%. A disabled part
counts as offline for `*DEVOFFLINE`. With `&EVENT` declared `*CHAR 10` as
above, the longer event names arrive cut to 10 characters: `*DEVOFFLIN`,
`*PWRRESTOR`. Schedule entries: `*ONCE` is removed once it has run; a `*DAILY`
entry missed while its network was unloaded runs once when it's loaded again.)*

## 10. The existing CLI (removed)

The Terminal Desk's original words (`help`, `show`, `withdraw`, `craft`, `cancel job`, `clear`) are gone: every command
line is ELCL. Use F1 / `GO HELP`, `WRKINV`, `WRKDEV`, `DSPNETSTS`, `WRKCRFJOB`, `MOVITM ITEM() QTY() TODEV(*DESK)`,
`STRCRAFT`, `ENDCRAFT` and `CLEAR` instead.
