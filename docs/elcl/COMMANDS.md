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
| `DCL` | `VAR`(P1, Req) `TYPE`(P2, Req) `LEN` `VALUE` `RETAIN(*NO\|*YES)` **[EXT]** | — | Declaration. `RETAIN(*YES)`: a PLC keeps the value across STOP / RUN, power loss and reloads (§7a); anywhere else ELC1506 (a warning) and it's ignored |
| `CHGVAR` | `VAR`(P1, Req) `VALUE`(P2, Req) | IB | Assignment |
| `IF` `ELSE` `DO` `ENDDO` `DOWHILE` `DOUNTIL` `DOFOR` `FOREACH` `ENDFOR` `LEAVE` `ITERATE` `SELECT` `WHEN` `OTHERWISE` `ENDSELECT` `GOTO` `RETURN` `SUBR` `ENDSUBR` `CALLSUBR` | see spec §6 | IB | |
| `MONMSG` | `MSGID`(P1, Req, list) `CMPDTA` `EXEC` | IB | Spec §8 |
| `RCVMSG` | `MSGTYPE(*EXCP\|*LAST)` `RTNMSGID` `RTNMSG` `RTNMSGDTA` | IB | |
| `SNDPGMMSG` | `MSG` `MSGID(USRnnnn)` `MSGTYPE(*INFO\|*ESCAPE)` | IB | |
| `CALL` | `PGM`(P1, Req, qualified) `PARM`(P2, list) | IB | Spec §7 |
| `DLYJOB` | `DLY`(P1, seconds, 1–86400) or `RSMTIME(HHMMSS)` (game clock) | IB | Async (seconds are game ticks / 20); on a command line it's done at once |
| `DLYTICK` **[EXT]** | `TICKS`(P1, Req, 1–1200) | IB | Delays the program that many game ticks (a PLC's outputs hold meanwhile); on a command line it's done at once. ELC0004 |
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
| `RTVITMCNT` | `ITEM`(P1, Req) `TIER(*ALL\|*HOT\|*COLD)` `RTNCOUNT`(*INT, Req) `TYPE` | view | ELC1201 (returns 0 instead if `NOTFND(*ZERO)` given), ELC1205, ELC1207 |
| `RTVITMLST` | `FILTER`(P1, default `*ALL`; text with `*` wildcards or `#tag`) `TIER` `MAX(*NOMAX\|n)` `SORT(*NAME\|*QTY)` `RTNLST`(*LIST, Req) `TYPE` | view | — |
| `MOVITM` | `ITEM`(P1, Req) `QTY`(P2, Req, n or `*ALL`) `TODEV`(P3, Req, device name or `*DESK`) `PARTIAL(*YES\|*NO)` `RTNMOVED`(*INT) `TYPE` | extract | ELC1201, ELC1202 (only if PARTIAL(*NO)), ELC1207, ELC1208, ELC1301, ELC1302, ELC1304 |
| `IMPITM` | `FROMDEV`(P1, Req) `ITEM(*ALL\|id)` `QTY(*ALL\|n)` `RTNMOVED`(*INT) `TYPE` | insert | ELC1204, ELC1207, ELC1208, ELC1301, ELC1302, ELC1304 |
| `CHGITMTIER` | `ITEM`(P1, Req) `TIER`(P2, Req: `*HOT\|*COLD\|*PIN\|*AUTO`) | configure | ELC1201, ELC1206 (no Tape Library) |
| `RTVSTGSTS` | `TIER(*ALL\|*HOT\|*COLD)` `RTNUSED` `RTNTOTAL` (*INT) `RTNPCT` (*DEC) `TYPE` | view | — |

`*DESK` = the drawer of the Terminal Desk running the job (interactive only).

**Resource types.** `TYPE(*ITEM|*FLUID|*PRES|*ALL)` (default `*ITEM`, so programs written before types behave
the same) picks what the inventory commands work on: items, fluids (amounts in mB), pressurized gases and chemicals
from another mod (Arcforge's gases; their own unit, mB for Arcforge), or all of them. A fluid or gas is named by its
full ID, `'minecraft:water'` or `'arcforge:hydrogen'` (an unqualified ID is taken as minecraft's). `RTVITMLST` returns
fluids and gases by those IDs. `MOVITM`/`IMPITM` move them through the device's faced block's tanks (ELC1208 when it
has none for that type), and `MOVITM ... TODEV(*DESK)` into empty containers (buckets, tanks, gas cartridges) in the
desk's drawer. `RTVSTGSTS TYPE(*FLUID)` sizes the Fluid Storage Drives (bytes; a byte holds 1,000 mB). Fluids and gases
are never on tape. ELC1207 when the ID names a resource of another type (a gas asked for with `TYPE(*FLUID)`).

Work with Inventory (`WRKINV`) lists items, fluids and gases with a Type column (`ITEM`, `FLUID`, `PRES`) and a Type
filter (`*ALL` default); 1=Withdraw of a fluid or gas fills containers in the drawer or your inventory. Display Network
Status shows fluid and pressurized drives, and the energy in Energy Storage Drives, on their own lines when there are any.
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
| `STRCRAFT` | `ITEM`(P1, Req) `QTY`(P2, Req) `SCHEDULER(*ANY\|name)` `MISSING(*FAIL\|*PARTIAL)` `WAIT(*NO\|*YES)` `RTNCRFJOB`(*CHAR) `TYPE(*ITEM\|*FLUID\|*PRES)` | craft | ELC1207, ELC1401, ELC1402, ELC1403 |
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
Small Wireless Bridge on, by a prefix from their type (`ARCCRU01`), PLCs (`PLC01`, type PLC), and Cage Lights, Alarm Strobes
and Speakers (`LGT01`, `SRN01`, `SPK01`; types LGT, SRN, SPK); the mod has no Label Maker.)*

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
| `RTVPWRSTS` | `RTNSRC`(*CHAR: `*NETWORK\|*UPS\|*NONE`) `RTNCHG`(*DEC, UPS %) `RTNLOAD`(*INT, FE/t) `RTNSTORED`(*INT, FE) `RTNCAP`(*INT, FE) `RTNDRVSTO`(*INT, FE) `RTNDRVCAP`(*INT, FE) | view |

`RTNSTORED` / `RTNCAP` are the whole energy pool (controllers, Capacitor Banks, Energy Storage Drives);
`RTNDRVSTO` / `RTNDRVCAP` the part of it in Energy Storage Drives.

## 7. Redstone (Control Interface block, PLC)

| Command | Parameters | Auth | Errors |
|---------|-----------|------|--------|
| `RTVRSIN` | `DEV`(P1, Req, or `*SELF`) `SIDE`(P2, Req: `*NORTH\|*SOUTH\|*EAST\|*WEST\|*UP\|*DOWN\|*MAX`) `RTNLVL`(*INT, 0–15) | view | ELC1301, ELC1302, ELC1303 |
| `CHGRSOUT` | `DEV`(P1, Req, or `*SELF`) `SIDE`(P2, Req, or `*ALL`) `LVL`(P3, Req, 0–15) | configure | ELC1301, ELC1302, ELC1303, ELC0004 |

*(Added: a PLC's faces work exactly as a Control Interface's - by its name (`PLC01`) on its network, or `DEV(*SELF)` in
the PLC's own program, which needs no network. `*SELF` anywhere else is ELC1301.)*

## 7a. PLCs **[EXT]**

A Programmable Logic Controller (docs/plc) runs one compiled program continuously, like a PLC scan: on ENDPGM the next
pass starts from the top (next tick), its variables carried on - a `VALUE` is set again only on STOP -> RUN and
power-up; `RETAIN(*YES)` variables are kept in the program itself. `plcInstructionsPerTick` instructions a tick (a pass
that needs more carries on next tick). Without a network it runs only the language statements, the list commands,
`DLYJOB`, `DLYTICK`, `RTVSNSVAL` and `RTVRSIN` / `CHGRSOUT DEV(*SELF)`; anything else is ELC1502, when it's compiled
for that PLC (the editor's save) and at run time. Cabled to a network, everything works, with the Firewall authority of
the player who last loaded the program. An unmonitored escape puts it in FAULT (outputs hold; `plcFaultOutputs`).

| Command | Parameters | Context | Auth | Notes |
|---------|-----------|---------|------|-------|
| `RTVSNSVAL` | `MODULE`(P1, Req, 1–4) `TYPE(*ANY\|*PRESENCE\|*INVENTORY\|*FLUID\|*LIGHT\|*TIMER)` `DEV(*SELF\|name)` `RTNVAL`(*DEC) `RTNSTS`(*CHAR 10: `*OK\|*NOMODULE\|*NOTARGET`) `RTNAUX`(*CHAR 32) | IB | view | A sensor module's reading (below). With `RTNSTS` given, a missing module (or one not of `TYPE`) or one with nothing to read comes back as `*NOMODULE` / `*NOTARGET`; without it they're ELC1501 / ELC1503. `DEV` (added): a networked PLC's modules from a batch job; `*SELF` (the default) outside a PLC is ELC1301 |
| `SNDPLCPGM` | `PGM`(P1, Req, lib/name) `DEV`(P2, Req) `RUN(*YES\|*NO)` | IB | configure | Loads a program compiled with `CRTELPGM TGT(*PLC)` into a PLC on the network (it stops first; `RUN(*YES)` then runs it from the top). The loader is the user running it. The same program again keeps its retained variables. ELC1301, ELC1303, ELC1504, ELC0401; ELC1509 on success |
| `STRPLC` | `DEV`(P1, Req) | IB | configure | RUN, clearing a fault, from the top. ELC1301, ELC1303, ELC1505; ELC1507 |
| `ENDPLC` | `DEV`(P1, Req) | IB | configure | STOP (its outputs off). ELC1301, ELC1303; ELC1508 |

Sensor modules, as `RTVSNSVAL` reads them (`RTNVAL`, `RTNAUX`):

| Module | Setting (PLCMOD 2) | RTNVAL | RTNAUX |
|--------|-------------------|--------|--------|
| Presence Sensor | radius 1–16, `*PLAYERS\|*MOBS\|*ALL` | how many within the radius | the nearest one's name |
| Inventory Sensor | face (default: behind the PLC) | how full, percent (by slot, as a comparator) | the item count |
| Fluid Sensor | face (default: behind the PLC) | how full, percent | the amount, mB |
| Light Sensor | — | the light level at the PLC, 0–15 | `*DAY` / `*NIGHT` |
| Timer Module | — | game ticks | the day and time (`Day 2 14:32`) |

## 7b. Signals: Cage Lights, Alarm Strobes, Speakers **[EXT]**

Cage Lights (`LGT`), Alarm Strobes (`SRN`) and Speakers (`SPK`) (docs/signals) work from the redstone at their block
on their own; cabled to a network they're devices there too. `DEV` takes a list (`DEV(LGT01 LGT02)`) or `*ALL` for
every online device of the type; every named device is checked before any is changed. An Alarm Strobe or Speaker
whose trigger is Redstone takes no network commands: named, it's ELC2408; `*ALL` leaves it out.

| Command | Parameters | Context | Auth | Notes |
|---------|-----------|---------|------|-------|
| `CHGLGT` | `DEV`(P1, Req, list) `STATUS(*SAME\|*ON\|*OFF\|*TOGGLE)` `LVL(*SAME\|1–15)` | IB | configure | The light's network state (it adds to its redstone's: either turns it on; Always on ignores it) and light level. ELC1301, ELC1302, ELC1303, ELC2401 |
| `STRSRN` | `DEV`(P1, Req, list) `SOUND(*SAME\|*WAIL\|*YELP\|*KLAXON\|*BELL\|*HORN\|*BEEP\|*NONE)` `MODE(*SAME\|*SOLID\|*SLOW\|*MEDIUM\|*FAST)` | IB | configure | Turns the strobe on, its tone and light mode set when given (`*NONE`: light only). Strobes on one network with the same tone share a start, so they sound together. ELC1301, ELC1302, ELC1303, ELC2408 |
| `ENDSRN` | `DEV`(P1, Req, list) | IB | configure | Stops the sound and light (a redstone signal on a Both-trigger strobe keeps it on). ELC1301, ELC1302, ELC1303, ELC2408 |
| `PLYAUD` | `DEV`(P1, Req, list) `SRC`(P2, Req: 'file name' or 'URL') `VOL(*SAME\|0–100)` `LOOP(*SAME\|*NO\|*YES)` | IB | configure | Plays an OGG Vorbis or MP3 file from `<world>/encodedlogistics/audio/<SYSNAME>/`, or a web URL (http/https, `allowWebAudio`, a host on `webAudioHosts`; each listening player's game fetches it). Checked once before any speaker starts. ELC1301, ELC1302, ELC1303, ELC2402–ELC2406, ELC2408 |
| `PLYNOTE` | `DEV`(P1, Req, list) `INST(*HARP\|*BASS\|*SNARE\|*HAT\|*BASSDRUM\|*BELL\|*FLUTE\|*CHIME\|*GUITAR\|*XYLOPHONE\|*IRONXYLO\|*COWBELL\|*DIDGERIDOO\|*BIT\|*BANJO\|*PLING)` `NOTE`(P3, Req, 0–24, list of up to 32) | IB | configure | Plays the first note now; more than one note is a sequence, the next played on each rising redstone edge. ELC1301, ELC1302, ELC1303, ELC2407, ELC2408 |
| `STPAUD` | `DEV`(P1, Req, list) | IB | configure | Stops what the speaker is playing (and clears an error). ELC1301, ELC1302, ELC1303, ELC2408 |

Server config (`signals`): `audioMaxBytes` (MB, 4), `audioMaxSeconds` (180), `allowWebAudio` (AUTO: on in single-player,
off on dedicated servers), `webAudioHosts` (a listed host allows its subdomains; empty: none), `sirenMaxRange` (96),
`speakerMaxRange` (64), `signalDeviceDrain` (FE/t). Client config: `neverPlayWebAudio`.

## 8. Messages, displays and output

| Command | Parameters | Context | Notes |
|---------|-----------|---------|-------|
| `SNDMSG` | `MSG`(P1, Req) `TOUSR(*REQUESTER\|*SYSOPR\|*ALL\|user)` `TOTRM(name)` | IB | `*SYSOPR` = network owner. Also shows a chat notice to online recipients (config). |
| `DSPMSG` | `USR(*CURRENT\|user)` | I | Opens Display Messages screen |
| `SNDDSPTXT` | `DEV`(P1, Req) `TEXT`(P2, Req) `LINE(*NEXT\|n)` `CLEAR(*NO\|*YES)` `SIZE(*SAME\|*SMALL\|*NORMAL\|*LARGE\|*HUGE)` [EXT] `COLOR(*SAME\|*DFT\|name\|#RRGGBB)` [EXT] `ALIGN(*SAME\|*LEFT\|*CENTER\|*RIGHT)` [EXT] | IB | A Display Panel screen (DSP01). ELC1301, ELC1303, ELC0103 (a colour name it doesn't know), ELC0003 (a bad #RRGGBB). *(Implemented against `elcl.device.DisplayDevice`: text stands 3 canvas px in from the edges; a character is 4 x 6 canvas px at `*SMALL`, 6 x 10 at `*NORMAL`, 2x and 3x that at `*LARGE` / `*HUGE`; longer text is cut, *NEXT past the bottom rolls the lines up; `*SAME` keeps a line's own size, colour and alignment)* |
| `CLRDSP` **[EXT]** | `DEV`(P1, Req) `RGN(*ALL\|name)` | IB | Clears a region's widget, or (*ALL) the whole screen: its regions, text and images. ELC1301, ELC1303, ELC1314. |
| `CHGDSPRGN` **[EXT]** | `DEV`(P1, Req) `RGN`(P2, Req, name) `X(*SAME\|n)` `Y(*SAME\|n)` `W(*SAME\|n)` `H(*SAME\|n)` `BG(*SAME\|*DFT\|colour)` | IB | Defines or changes a region in canvas px (a new one: from 0, 0 to the screen's edge by default). ELC1301, ELC1303, ELC1314 (outside the screen or overlapping). |
| `SNDDSPWDG` **[EXT]** | `DEV`(P1, Req) `RGN`(P2, Req) `WDG`(P3, Req: `*STORAGE *COLD *ENERGY *LANES *JOBS *ITEM *CLOCK *DEVICES *UPS *TEXT *TABLE *NONE`) `ITEM(*NONE\|id)` `DEVTYPE(*ALL\|type)` `COLOR(*DFT\|colour)` `FILE(*NONE\|LIB/NAME)` `QRYSLT(*ALL\|expression)` `SORT(*NONE\|field [*DESCEND] ...)` | IB | Places a dashboard widget. ELC1301, ELC1303, ELC1314 (no such region), ELC1316 (bad data source). `*TABLE` (added): a file's records in columns under their headings, selected and sorted as `RUNQRY` does (11), refreshed every second; the first 63 records; `FILE` required (ELC1316), checked with its `QRYSLT` and `SORT` when placed (ELC2205, ELC2242). The configuration screen's Widgets tab offers it too (a file; selection and sort from the command). |
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
| `CRTMBR MBR() TEXT() SRCTYPE(ELCLP\|PF)` / `CPYMBR FROM() TO()` / `RNMMBR MBR() NEWNAME()` / `DLTMBR MBR()` | `SRCTYPE(PF)`: a physical file's definition (11) | IB |
| `CRTELPGM PGM(LIB/NAME) SRCMBR(*PGM\|LIB/NAME) TGT(*JOB\|*PLC)` / `DLTPGM PGM()` | From an `ELCLP` member (ELC2247 otherwise). *(Added: `TGT(*PLC)` compiles it for a PLC - `RETAIN` without ELC1506 - and only those `SNDPLCPGM` loads; SAVLIB / RSTLIB keep the target)* | IB |
| `SBMJOB CMD(command) JOB(name) HOST(*ANY\|device)` | Batch job; ELC0301 if no host | IB |
| `WRKACTJOB` / `WRKJOB JOB()` / `DSPJOBLOG JOB(*\|id)` | | I |
| `HLDJOB JOB()` / `RLSJOB JOB()` / `ENDJOB JOB() OPTION(*CNTRLD\|*IMMED)` | | IB |
| `ADDJOBSCDE JOB() CMD() FRQ(*ONCE\|*INTERVAL\|*DAILY) TIME(HHMM) INTERVAL(seconds)` / `RMVJOBSCDE JOB()` / `WRKJOBSCDE` | game-clock TIME, real-time INTERVAL (see HANDOFF open question 2) | IB / IB / I |
| `ADDTRGEVT TRG() EVENT() PGM() ITEM() DEV() VALUE() TYPE()` / `RMVTRGEVT TRG()` / `WRKTRGEVT` | events below | IB / IB / I |
| `WRKSYSVAL` / `RTVSYSVAL SYSVAL() RTNVAR()` / `CHGSYSVAL SYSVAL() VALUE()` | OS.md §7 | I / IB / IB |
| `SAVLIB LIB() DEV()` / `RSTLIB LIB() DEV()` | 8" Diskette in a Midrange System or Card Reader. *(Implemented against `DisketteDevice`, docs/elcl/INTERFACES.md: ELC1301 no device, ELC1310 no diskette, ELC1311 too big; RSTLIB makes the library or replaces its members and programs)* *(Added: and its files with their records, and its members' types; the records count against the diskette's capacity)* | IB |
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
| `*ITMBELOW` / `*ITMABOVE` | `ITEM`, `VALUE`, `TYPE(*ITEM\|*FLUID\|*PRES)` | item id (a fluid's or gas's full ID) and new count (mB for fluids) |
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

## 11. Database files

Physical files (`*FILE`, attribute `PF`) live in libraries beside members and programs: a record format (its fields)
compiled from a `PF` source member, an optional key, and one data member of records. A file named without a library
(`FILE(ITEMHIST)`) is looked for down the user's library list; one being made goes in their current library. Reading a
file needs its library (`*USE`, which every library gives); writing it `*CHANGE` on the library (its owner, `*SECOFR`, or
a `*CHANGE` library: ELC0401; never ELSYS: ELC0205). Records take network storage as source does (each its record
length in characters; ELC0207 when the drives are full) and a file holds at most `maxRecordsPerFile` (config, 10,000:
ELC2208). Files and their records save with the system.

ELSYS holds four read-only **system files**, made from the network as they're read (a read from the top sees it as it
is; reading on, the same picture for the rest of the tick). They need the Firewall's view permission (ELC0401).

| File | Fields (key first) |
|------|--------------------|
| `ELSYS/INVITEMS` | `ITEM` 64A (the ID as scripts write it; a fluid's or gas's full ID), `NAME` 48A, `HOT` 18S (mB for fluids and gases), `COLD` 18S, `MOD` 32A (a gas's source mod), `TYPE` 5A (`ITEM`, `FLUID`, `PRES`) |
| `ELSYS/DEVICES` | `NAME` 10A, `TYPE` 10A, `LOCATION` 40A (`x,y,z dimension`), `LANES` 9S, `STATUS` 10A |
| `ELSYS/CRFHIST` | `NUMBER` 9S, `JOBID` 8A (`C0042`), `ITEM` 64A, `REQUESTED` 18S, `PRODUCED` 18S, `STATUS` 10A, `USER` 10A, `STARTED` T, `ENDED` T |
| `ELSYS/JOBS` | `NUMBER` 6A, `JOB` 10A, `USER` 10A, `TYPE` 3A, `HOST` 10A, `STATUS` 7A, `PRIORITY` 1S, `BUDGET` 3S |

**Definitions.** `CRTMBR MBR(LIB/NAME) SRCTYPE(PF)` makes a definition member (`SRCTYPE(ELCLP)`, the default, a
program's). Each line starts with `A` (a line without one is read the same), in the editor's columns or anywhere;
`A*` or `*` starts a comment; a line with only keywords goes with the line before it:

```
     A                                      UNIQUE
     A          R ITEMREC                   TEXT('Item counts')
     A            ITEM          64A         COLHDG('Item')
     A            QTY           11S 0
     A            PRICE          9P 2       TEXT('Unit price')
     A            ACTIVE         1L
     A            UPDATED         T
     A          K ITEM
```

`R` names the record format (one a file); `K` a key field (up to 4, in order; none: arrival order); anything else a
field (up to 50): its name (up to 10 characters), length and type, keywords.

| Type | Meaning | Length | In a program |
|------|---------|--------|--------------|
| `A` | Character | 1-1,024 | `*CHAR` of its length |
| `S` | Integer | 1-18 digits, 0 decimals | `*INT` |
| `P` | Decimal | 1-31 digits and its decimals (`9P 2`, `9P2`) | `*DEC` of its length and decimals |
| `L` | Logical | 1 | `*LGL` |
| `T` | Game timestamp: `00012 06:30:15` (the day, then the time on the game clock; it sorts as text) | ignored | `*CHAR 14` |

Keywords: `UNIQUE` (before the `R`: the key is unique), `TEXT('...')` on the `R` (the file's text, when its member has
none) or a field, `COLHDG('...' ['...' ['...']])` on a field (its column heading, up to three lines). A timestamp typed,
imported or assigned may be `Day 12 06:30`, `12 06:30:15` or `00012 06:30:15`; blank or `*NOW` is the moment it's
written. The editor checks a definition as it does a program; CRTPF's listing (Work with Output) shows the source with
sequence numbers, the record format, and each message with its sequence number (ELC2220-ELC2228).

### In programs

| Command | Parameters | Notes |
|---------|-----------|-------|
| `DCLF` | `FILE`(P1, Req, qualified) `OPNID`(P2, `*NONE`\|name) | With the `DCL`s (ELC0016 after other commands): a variable for each field, `&FIELD`, or `&OPNID_FIELD` with an open ID, of its type above. Up to 5 a program, each open ID once (ELC0103). The file must be there when the program is compiled (ELC2205); the program keeps the formats it was compiled with, so a field it declares that is gone or of another type when it runs is a level check, ELC2207 (fields added don't matter) |
| `RCVF` | `OPNID`(P1) | The next record (key order; arrival order without a key) into the variables; at the end ELC2201, and again until `POSDBF` or `CLOF` |
| `POSDBF` | `OPNID`(P1) `POSITION`(P2, Req: `*START\|*END`) | Back to the start, or on to the end |
| `CLOF` | `OPNID`(P1) | Closes it: the next `RCVF` reads from the top |
| `CHNRCD` **[EXT]** | `OPNID`(P1) `KEY`(P2, Req, 1-4 values) | The first record whose leading key fields are the values (all of the key or the first of its fields); ELC2202 when there's none. `RCVF` reads on after it |
| `WRTRCD` **[EXT]** | `OPNID`(P1) | A new record from the variables (fields the program doesn't declare blank). ELC2203 for a duplicate unique key, ELC2208 when the file is full, ELC0207 when storage is, ELC2209 for a value that doesn't fit its field. A timestamp written blank takes the time, and its variable gets it |
| `UPDRCD` **[EXT]** | `OPNID`(P1) | The record last read (`RCVF` or `CHNRCD`) from the variables; ELC2204 with none |
| `DLTRCD` **[EXT]** | `OPNID`(P1) | Deletes the record last read; ELC2204 with none |

The variables are the program's own: `RCVF` overwrites them, `WRTRCD` and `UPDRCD` write what they hold. ELC2201 is
monitored like any escape message:

```
DCLF FILE(ELSYS/INVITEMS)
...
READ: RCVF
MONMSG MSGID(ELC2201) EXEC(GOTO CMDLBL(DONE))
```

### The files

| Command | Parameters | Context | Notes |
|---------|-----------|---------|-------|
| `CRTPF` | `FILE`(P1, Req) `SRCMBR`(P2, `*FILE`\|LIB/NAME) `TEXT(*SRCMBRTXT\|*BLANK\|text)` | IB | From a `PF` member (`*FILE`: the member of the file's name in its library). ELC2230; ELC2239 with definition errors (the listing in Work with Output), ELC2247 not a `PF` member, ELC0204 already there |
| `CHGPF` | `FILE`(P1, Req) `SRCMBR`(P2, `*FILE`: the member it was made from) `TEXT(*SAME\|*BLANK\|text)` | IB | Made again from its definition; each record keeps its values where a field of the same name and type is still there, the other fields' values are dropped (ELC2232 for each). ELC2231; ELC2246 with definition errors; a new unique key over duplicates is ELC2203 and nothing changes |
| `DLTF` | `FILE`(P1, Req) | IB | ELC2233 |
| `CLRPFM` | `FILE`(P1, Req) | IB | Every record gone: ELC2234 |
| `CPYF` | `FROMFILE`(P1, Req) `TOFILE`(P2, Req) `MBROPT(*ADD\|*REPLACE)` `CRTFILE(*NO\|*YES)` | IB | Each field of the file copied to from the field of the same name (ELC2244 when there's none; ELC2243 for a value that doesn't fit); `CRTFILE(*YES)` makes a file there isn't with the copied file's format. All or nothing. ELC2235 |
| `CPYTOIMPF` | `FILE`(P1, Req) `TOSTMF`(P2, Req: `'name.csv'`) | IB | The records as CSV (a heading row of field names; values as text) in the system's folder-sync folder, `<world>/encodedlogistics/libraries/<SYSNAME>/name.csv`. Only where folder sync is on (`allowFolderSync`): ELC2241. A plain name ending `.csv`: ELC2245. ELC2236 |
| `CPYFRMIMPF` | `FROMSTMF`(P1, Req) `FILE`(P2, Req) `MBROPT(*ADD\|*REPLACE)` | IB | From that folder: a heading row naming the fields maps columns by name (any order; fields left out blank), else columns are the fields in order. ELC2240 no such file, ELC2243 a value that doesn't fit its field (and its row). All or nothing. ELC2237 |
| `RUNQRY` | `FILE`(P1, Req) `QRYSLT(*ALL\|expression)` `SORT(*NONE\|field [*DESCEND] ...)` `OUTPUT(*DISPLAY\|*PRINT\|*OUTFILE)` `OUTFILE(LIB/NAME)` | IB | Below. ELC2238 |
| `WRKF LIB(*CURLIB\|lib)` / `DSPPFM FILE()` / `DSPFD FILE()` / `UPDDTA FILE()` | screens (OS.md 8) | I |

**RUNQRY.** `QRYSLT` is an ELCL expression over the file's field names - `'QTY *LT 100 *AND ACTIVE'`,
`'%SST(ITEM 1 4) *EQ "IRON"'` - a field written bare or as `&FIELD`, a literal in double quotes (or doubled single ones,
as in any quoted value); a name that isn't a field is ELC2242. `SORT` names up to 4 fields, each followed by `*DESCEND`
to go highest first (records it can't tell apart keep the file's order). `*DISPLAY` shows the result on the terminal
(the Display Physical File Member layout); in a program or a batch job, where there's no screen, it's printed.
`*PRINT` writes the report (headings, the records, the count) to the spooled file `QPQUPRFIL`. `*OUTFILE` makes - or
replaces - `OUTFILE` with the records selected, in the query's order (a file without a key).
