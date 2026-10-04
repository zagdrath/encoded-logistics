# ELCL and Terminal OS: implementation status

The gap check (Step 0) of the "complete the Terminal OS and ELCL" task: the code compared with HANDOFF.txt's
implementation order, every command in COMMANDS.md, every message in MESSAGES.md and every screen in OS.md §8.

**Done**: works as specified. **Partial**: exists but falls short (the gap is named). **Missing**: not there.
**Blocked**: needs hardware from the Midrange line or the Mainframe, which isn't in the mod yet. These are built
against an interface with a fake for tests (Part 7).

Paths are under `src/main/java/net/zagdrath/encodedlogistics/` unless they start with `src/test`, `docs` or `client/`
(`client/crt/`). The "Part" column says which part of the task closes the gap.

*Status as of the gap check, before any of Parts 1-8.*

## 1. HANDOFF.txt implementation order

| # | Step | Status | Where / gap | Part |
|---|---|---|---|---|
| 1 | Command registry; existing CLI ported, kept as aliases | Done | `elcl/cmd/CommandRegistry`, `BuiltinCommands`, `CommandDefinition`, `ParamDef`; `terminal/TerminalCommands` (aliases, COMMANDS.md §10) | - |
| 2 | Lexer, parser, compiler, compile listing; tests from examples | Done | `elcl/lex/Lexer`, `elcl/parse/Parser`, `elcl/compile/Compiler`, `elcl/compile/Listing`; `src/test/.../elcl/CompilerTest`, `LexerParserTest` (examples compile clean, one test per compile-time message) | - |
| 3 | VM with interactive CALL: variables, expressions, control flow, MONMSG, subroutines | Missing | No `elcl.vm`. `CompiledProgram` holds checked statements but nothing runs them; CALL answers ELC0107. `exec/CommandRunner` evaluates literal expressions for the command line only | 3 |
| 4 | Library/member storage, WRKLIB/WRKMBR, editor logic, option 14 compile | Partial | Screens and rules done (`client/WrkLibPanel`, `WrkMbrPanel`, `EditorPanel`, `EditorModel`; `elcl/screen/StubLibraryService`), but everything is kept in memory and lost on restart. Compiled programs aren't kept as runnable objects. No storage cost (ELC0207 never raised) | 1 |
| 5 | Mod commands (inventory, devices, power, storage, display, redstone); Control Interface | Partial | Control Interface and RTVRSIN/CHGRSOUT done (`block/ControlInterfaceBlock`, `blockentity/ControlInterfaceBlockEntity`, `elcl/exec/RedstoneCommands`). Every other mod command answers ELC0107. Device names come from list position (`elcl/exec/ElclDevices.list`) | 2, 3 |
| 6 | Batch jobs: SBMJOB, job hosts, tick budget, WRKACTJOB, job logs | Missing | `elcl/screen/StubJobService`: interactive jobs only, SBMJOB always ELC0301, no hosts, no budget, logs in memory | 4 |
| 7 | Job schedule entries and event triggers | Partial | Entries and triggers are stored and listed (`StubJobService`, `client/WrkJobScdePanel`, `WrkTrgEvtPanel`) but never fire and aren't saved. `elcl/exec/ElclEvents` fires only `*RSCHANGE` and nothing listens | 5 |
| 8 | Message queues, spooled output, Line Printer printing | Partial | `StubMessageService`, `StubSpoolService` work in memory. No caps, no chat notice. Printing always ELC1301 (no printer interface) | 1, 7 |
| 9 | Folder sync, SAVLIB/RSTLIB to 8" Diskette | Missing | No `elcl.sync`. SAVLIB/RSTLIB answer ELC0107 | 7, 8 |
| 10 | Sign-on, user profiles, Firewall authority everywhere | Partial | Sign-on is client-side only (`client/SignOnPanel`: shown with a Firewall unless SECLVL is 10). No server-side user profiles; the library list is a constant (`OsCommands.LIBRARY_LIST`). Command Auth checked on the command line (`CommandRunner`); the *SECOFR class is approximated by the Firewall's build permission. ELC0402 never raised | 6 |

## 2. Commands (COMMANDS.md)

"Compiles" means the schema is registered and the compiler checks it; "runs" means it has an executor.

### §1 Program control

| Command | Status | Where / gap | Part |
|---|---|---|---|
| PGM, ENDPGM, DCL, CHGVAR | Partial | Compiles (`Compiler`); no VM to run them | 3 |
| IF, ELSE, DO, ENDDO, DOWHILE, DOUNTIL, DOFOR, FOREACH, ENDFOR, LEAVE, ITERATE, SELECT, WHEN, OTHERWISE, ENDSELECT, GOTO, RETURN | Partial | Compiles with block matching and GOTO rules; no VM | 3 |
| SUBR, ENDSUBR, CALLSUBR | Partial | Compiles; no VM | 3 |
| MONMSG (command and program level, range IDs) | Partial | Compiles (placement, up to 50 IDs); no VM to monitor anything | 3 |
| RCVMSG | Partial | Compiles; no VM | 3 |
| SNDPGMMSG | Partial | Runs on the command line (`OsCommands`); programs need the VM | 3 |
| CALL | Missing | ELC0107 | 3 |
| DLYJOB | Missing | ELC0107 | 3 |
| RTVJOBA | Partial | Runs for the interactive job only (`OsCommands`) | 4 |

### §2 Lists

| Command | Status | Where / gap | Part |
|---|---|---|---|
| ADDLSTE, RMVLSTE, CLRLST | Partial | Compile; program-only and no VM | 3 |

### §3-6 Inventory, crafting, devices, power

| Command | Status | Where / gap | Part |
|---|---|---|---|
| RTVITMCNT, RTVITMLST, MOVITM, IMPITM, CHGITMTIER, RTVSTGSTS | Missing | ELC0107 | 3 |
| STRCRAFT, RTVCRFSTS, ENDCRAFT | Missing | ELC0107 | 3 |
| RTVDEVSTS, RTVDEVLST, CHGDEVSTS, CHGDEVFTR, RTVLANES | Missing | ELC0107 | 3 |
| RTVPWRSTS | Missing | ELC0107 | 3 |

### §7 Redstone

| Command | Status | Where / gap | Part |
|---|---|---|---|
| RTVRSIN, CHGRSOUT | Done | `elcl/exec/RedstoneCommands`, game test `control_interface`. Device lookup moves to stored names in Part 2 | 2 |

### §8 Messages, displays and output

| Command | Status | Where / gap | Part |
|---|---|---|---|
| SNDMSG | Partial | `OsCommands`; in memory, TOTRM ignored, no chat notice | 1 |
| DSPMSG | Done | Screen `client/DspMsgPanel` (data in memory until Part 1) | 1 |
| SNDDSPTXT | Missing | ELC0107 (Status Display, NOC Video Wall, Rack Console) | 3 |
| PRTTXT | Partial | `OsCommands`; spooled file in memory | 1 |
| PRTRPT | Missing / Blocked | ELC0107; needs the Line Printer (PrinterDevice interface) | 3, 7 |

### §9 OS commands

| Command | Status | Where / gap | Part |
|---|---|---|---|
| WRKLIB, CRTLIB, CHGLIB, DLTLIB | Partial | `OsCommands`, `StubLibraryService`; library authority done; not persisted | 1 |
| WRKMBR, EDTMBR | Done | Screens (data in memory until Part 1) | 1 |
| CRTMBR, CPYMBR, RNMMBR, DLTMBR | Partial | Not persisted; no storage cost; no folder sync | 1, 8 |
| CRTELPGM, DLTPGM | Partial | Listing spooled; program kept only as a name (nothing runnable, not persisted) | 1, 3 |
| SBMJOB | Missing | Always ELC0301 | 4 |
| WRKACTJOB, WRKJOB, DSPJOBLOG | Partial | Interactive jobs only; hosts shown 0/0; call stack stubbed | 4 |
| HLDJOB, RLSJOB, ENDJOB, CHGJOB | Partial | Flip a status flag only; nothing runs | 4 |
| ADDJOBSCDE, RMVJOBSCDE, WRKJOBSCDE (+ HLDJOBSCDE, RLSJOBSCDE) | Partial | Stored and listed; never fire; not persisted; 10=Submit now gives ELC0301 | 5 |
| ADDTRGEVT, RMVTRGEVT, WRKTRGEVT (+ HLDTRGEVT, RLSTRGEVT) | Partial | Stored and listed; never fire; not persisted; only `*RSCHANGE` is detected | 5 |
| WRKSYSVAL, RTVSYSVAL, CHGSYSVAL | Partial | `StubSysvalService`; rules right; not persisted | 1, 6 |
| SAVLIB, RSTLIB | Missing / Blocked | ELC0107; needs the 8" Diskette (DisketteDevice interface) | 7 |
| WRKDEV, WRKINV, DSPNETSTS, WRKCRFJOB | Done | Existing screens | - |
| SIGNOFF, GO, CLEAR | Done | Client `ScreenCommands` | - |

### §9 Trigger events

| Event | Status | Part |
|---|---|---|
| `*RSCHANGE` | Partial: fired by the Control Interface (`ElclEvents.redstoneChanged`); nothing listens | 5 |
| `*ITMBELOW`, `*ITMABOVE`, `*STGFULL`, `*DEVFAULT`, `*DEVONLINE`, `*DEVOFFLINE`, `*PWRUPS`, `*PWRRESTORED`, `*CRAFTEND` | Missing: not detected | 5 |

### §10 Aliases

All Done (`terminal/TerminalCommands`).

## 3. Messages (MESSAGES.md)

"Raised" means some code path throws or sends it with the spec's meaning.

| IDs | Status | Where / gap | Part |
|---|---|---|---|
| ELC0001-0005, 0007-0010, 0014, 0016 | Done | Compiler / command line; one negative test each (`CompilerTest`) | - |
| ELC0006 (list index), 0011 (call depth), 0012 (parm count), 0013 (called program failed), 0015 (list limit) | Missing | Runtime errors: need the VM | 3 |
| ELC0000 (monitor all) | Missing | Monitoring: needs the VM | 3 |
| ELC0101-0104, 0106 | Done | `CommandRunner`, `Compiler` | - |
| ELC0105 (not allowed in batch) | Missing | No batch context | 4 |
| ELC0201-0206, 0208 | Done | `StubLibraryService` (in memory) | 1 |
| ELC0207 (storage full) | Missing | No storage cost | 1 |
| ELC0210-0219, 0222 | Done | `OsCommands` completion messages | - |
| ELC0220, 0221 (library saved / restored) | Missing / Blocked | SAVLIB/RSTLIB | 7 |
| ELC0301 | Partial | Always raised (no hosts) | 4 |
| ELC0302, 0304-0309, 0311-0315 | Partial | Raised by the stub; ELC0304 never sent (nothing is ever submitted) | 4, 5 |
| ELC0303 (ended by operator), 0310 (host unloaded / lost power) | Missing | No running jobs | 4 |
| ELC0401 | Partial | Command line (Auth column), library authority, CHGSYSVAL; not on programs, jobs, schedule entries or triggers | 6 |
| ELC0402 (sign-on failed) | Missing | Sign-on is client-side; a wrong name only shows a message | 6 |
| ELC1201-1206 | Missing | Inventory commands | 3 |
| ELC1301-1303 | Partial | Redstone commands only | 2, 3 |
| ELC1304, 1305 | Missing | MOVITM / IMPITM / CHGDEVFTR | 3 |
| ELC1306 (out of paper) | Missing / Blocked | PrinterDevice | 7 |
| ELC1401-1404 | Missing | Crafting commands | 3 |
| USRnnnn | Done | SNDPGMMSG (command line) | 3 |
| ELC0107 (not available yet; not in MESSAGES.md) | Temporary | Every command without an executor; should end up unused | 3 |

## 4. Screens (OS.md §8)

| # | Screen | Status | Where / gap | Part |
|---|---|---|---|---|
| 1 | Sign On | Partial | `client/SignOnPanel`; client-side only, no user profile, no library list, ELC0402 missing | 6 |
| 2 | Main Menu | Done | `client/MainMenu`, `MainMenuPanel` | - |
| 3 | Work with Libraries | Partial | `client/WrkLibPanel`; data not persisted | 1 |
| 4 | Work with Members | Partial | `client/WrkMbrPanel`; data not persisted | 1 |
| 5 | Source Editor | Done | `client/EditorPanel`, `EditorModel` (saves go to the stub until Part 1) | 1 |
| 6 | Command Prompter | Done | `client/PrompterPanel`, `ValueListWindow` | - |
| 7 | Compile Listing | Done | `elcl/compile/Listing`, `client/DspSplfPanel` | - |
| 8 | Work with Active Jobs | Partial | `client/WrkActJobPanel`; no batch jobs, hosts 0/0, budget not measured | 4 |
| 9 | Work with Job / Display Job Log | Partial | `client/WrkJobPanel`, `DspJobLogPanel`; call stack stubbed, logs in memory | 3, 4 |
| 10 | Work with Job Schedule Entries | Partial | `client/WrkJobScdePanel`; entries never fire, 10=Submit now gives ELC0301 | 5 |
| 11 | Work with Trigger Events | Partial | `client/WrkTrgEvtPanel`; triggers never fire | 5 |
| 12 | Display Messages | Partial | `client/DspMsgPanel`, MW indicator done; queues not persisted | 1 |
| 13 | Work with Output | Partial | `client/WrkSplfPanel`, `DspSplfPanel`; not persisted; 6=Print always ELC1301 | 1, 7 |
| 14 | Work with System Values | Partial | `client/WrkSysvalPanel`; values not persisted | 1 |
| 15 | Help panels (F1) | Done | `CrtTerminal.help()`, lang `crt.encodedlogistics.help.*` | - |
| 16 | Command Entry | Done | `client/CommandEntryPanel` | - |
| - | Control Interface block | Done | `block/ControlInterfaceBlock` | - |

## 5. The task's parts against what exists

| Part | Item | Status |
|---|---|---|
| 1 | Persistence (`elcl.store`): libraries, members, programs, message queues, spooled files, system values | Missing: all five services are in-memory stubs (`elcl/screen/Stub*`) |
| 1 | Storage cost, ELC0207 | Missing |
| 1 | ELSYS rebuilt from the bundled examples on load | Partial: built from `data/encodedlogistics/elcl/ELSYS/` when a system is first touched |
| 1 | Retention caps (spooled files, messages per user) | Missing |
| 1 | Library authority (owner; others read-only unless *CHANGE) | Done (stub rules) |
| 2 | Stable device names, migration, renaming, uniqueness, moving networks | Missing: names come from list position (`ElclDevices.list`). There's no Label Maker in the mod, so renaming is through Work with Devices only |
| 3 | VM (budgets, async waits, NBT state) | Missing |
| 3 | Mod commands §2-8 | Missing, apart from §7 and parts of §8 |
| 3 | Examples compile clean | Done; running them needs the VM and mod commands |
| 4 | JobHost, JobManager, Compute Server host, SBMJOB, job queue, logs, persistence, global budget | Missing. The crafting system's `crafting/JobHost` is unrelated; the ELCL one goes in `elcl.job` |
| 5 | Schedule entries and triggers firing, persisted, run as creator | Missing (only stored, in memory) |
| 6 | Server-side sign-on and profiles, library list, *SECOFR owner, SECLVL, ELC0401 everywhere | Partial (see step 10) |
| 7 | JobHost, DisketteDevice, PrinterDevice, RecipeLibrarySource interfaces + fakes + INTERFACES.md | Missing |
| 8 | Folder sync (`elcl.sync`) and allowFolderSync | Missing |

## 6. Hardware not in the mod

Built against interfaces (Part 7) with fakes in tests. Real implementations come with the Midrange line task:

- Midrange System (1 batch job, +1 with an Expansion Cabinet), Integrated Midrange System (4): JobHost.
- Mainframe (resumes jobs): JobHost.
- Line Printer: PrinterDevice (PRTRPT, WRKSPLF 6=Print).
- 8" Diskette in a Midrange System or Card Reader: DisketteDevice (SAVLIB / RSTLIB), RecipeLibrarySource.
- Keypunch: nothing in ELCL depends on it.
