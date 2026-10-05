# ELCL and Terminal OS: implementation status

The final status of every item in the ELCL handoff: HANDOFF.txt's implementation order, every command in
COMMANDS.md, every message in MESSAGES.md and every screen in OS.md §8. The gap check (Step 0) found most of it
partial or missing. The task's Parts 1-8 closed those gaps; the table by part, at the end, says how.

**Done**: works as specified, or as the notes in COMMANDS.md / OS.md say where the mod differs. **Blocked**: works
against an interface (docs/elcl/INTERFACES.md), tested with a fake. The real device comes with the Mainframe or the
display blocks, which aren't in the mod yet. The Midrange line is in the mod (Part 9), so what waited on it is Done.

Paths are under `src/main/java/net/zagdrath/encodedlogistics/` unless they start with `src/test` or `docs`; `client/`
means `client/crt/`.

## 1. HANDOFF.txt implementation order

| # | Step | Status | Where |
|---|---|---|---|
| 1 | Command registry; existing CLI ported, kept as aliases | Done | `elcl/cmd/CommandRegistry`, `BuiltinCommands`, `CommandDefinition`, `ParamDef`; `terminal/TerminalCommands` |
| 2 | Lexer, parser, compiler, compile listing; tests from examples | Done | `elcl/lex`, `elcl/parse`, `elcl/compile` (`Compiler`, `Listing`); `CompilerTest`, `LexerParserTest` |
| 3 | VM: variables, expressions, control flow, MONMSG, subroutines, CALL | Done | `elcl/vm/Vm`, `Values`, `Lowerer`, `VmProgram`; `VmTest` |
| 4 | Libraries/members storage, WRKLIB/WRKMBR, editor, option 14 | Done | `elcl/store/StoredLibraryService`, `SystemData`, `ElclStore`; `client/WrkLibPanel`, `WrkMbrPanel`, `EditorPanel` |
| 5 | Mod commands; the Control Interface | Done | `elcl/exec/ModCommands`, `RedstoneCommands`, `ElclDevices`, `ElclItems`; `block/ControlInterfaceBlock` |
| 6 | Batch jobs: SBMJOB, job hosts, tick budget, WRKACTJOB, job logs | Done | `elcl/job/StoredJobService`, `JobManager`, `JobHost`, `JobHosts`, `BatchContext` |
| 7 | Job schedule entries and event triggers | Done | `elcl/job/Schedules`, `Triggers`, `RealTime`; `elcl/exec/ElclEvents` |
| 8 | Message queues, spooled output, Line Printer printing | Done | `elcl/store/StoredMessageService`, `StoredSpoolService`; printing through `elcl/device/PrinterDevice` on the Line Printer (`midrange/LinePrinterBlockEntity`), as Printouts |
| 9 | Folder sync; SAVLIB/RSTLIB to 8" Diskette | Done | `elcl/sync/FolderSync`, `Resequence`; SAVLIB/RSTLIB through `elcl/device/DisketteDevice` on the Midrange Systems and the Card Reader |
| 10 | Sign-on, user profiles, Firewall authority everywhere | Done | `elcl/store/StoredUserService`, `elcl/exec/Authority`, `client/SignOnPanel`, `menu/TerminalDeskMenu` |

## 2. Commands (COMMANDS.md)

Every command has its schema in `elcl/cmd/BuiltinCommands` and an executor. The language statements run in the VM;
the rest are bound in `elcl/exec/OsCommands`, `ModCommands` and `RedstoneCommands`. The only ELC0107 left is a screen
command (WRKLIB, DSPMSG...) run inside a program, which has no screen to open.

| Section | Commands | Status | Where / notes |
|---|---|---|---|
| §1 Program control | PGM, ENDPGM, DCL, CHGVAR, IF, ELSE, DO, ENDDO, DOWHILE, DOUNTIL, DOFOR, FOREACH, ENDFOR, LEAVE, ITERATE, SELECT, WHEN, OTHERWISE, ENDSELECT, GOTO, RETURN, SUBR, ENDSUBR, CALLSUBR, MONMSG, RCVMSG, SNDPGMMSG, CALL, DLYJOB | Done | `elcl/vm`. CALL on a command line runs in the interactive job (`elcl/job/InteractiveCalls`); DLYJOB typed there completes at once |
| §1 | RTVJOBA | Done | The job the command runs in (interactive or batch) |
| §2 Lists | ADDLSTE, RMVLSTE, CLRLST | Done | `elcl/vm/Vm` |
| §3 Inventory | RTVITMCNT, RTVITMLST, MOVITM, IMPITM, CHGITMTIER, RTVSTGSTS | Done | `ModCommands`. MOVITM / IMPITM work on a cable part's faced inventory or `*DESK`; CHGITMTIER through the Tape Libraries' keep-hot and pinned lists |
| §4 Crafting | STRCRAFT, RTVCRFSTS, RTVCRFLOG, ENDCRAFT | Done | `ModCommands`; ended jobs from the saved history `crafting/CraftLog` (CRFLOGRTN) |
| §5 Devices | RTVDEVSTS, RTVDEVLST, CHGDEVSTS, CHGDEVFTR, RTVLANES; RNMDEV (added) | Done | `ModCommands`, `RedstoneCommands`. Cable parts can be disabled and have filters; other devices answer ELC1303 |
| §5 Machines (added) | RTVMCHSTS, RTVMCHSTAT, RTVMCHLST, CHGMCHSTS, CHGMCHCFG | Done | `MachineCommands`: Arcforge machines with a Small Wireless Bridge on (`machine/`, `compat/arcforge/`) |
| §6 Power | RTVPWRSTS | Done | `ModCommands` |
| §7 Redstone | RTVRSIN, CHGRSOUT | Done | `RedstoneCommands`, on any `blockentity/RedstoneDevice` (Control Interfaces and PLCs; `DEV(*SELF)` in a PLC's program) |
| §7a PLCs (added) | RTVSNSVAL, SNDPLCPGM, STRPLC, ENDPLC; DLYTICK; DCL RETAIN; CRTELPGM TGT | Done | `elcl/exec/PlcCommands`; DLYTICK in `elcl/vm/Vm`; RETAIN and the PLC compile targets in `elcl/compile/Compiler` (`Target`); `plc/` |
| §8 Messages, displays, output | SNDMSG, DSPMSG, PRTTXT | Done | SNDMSG's TOTRM is accepted but not used |
| §8 | SNDDSPTXT | Done | `elcl/device/DisplayDevice` on the Display Panel (`display/DisplayPanelBlockEntity`) |
| §8 | CLRDSP, CHGDSPRGN, SNDDSPWDG, SNDDSPGPH, SNDDSPIMG, RTVDSPSIZ (added) | Done | `elcl/exec/DisplayCommands` |
| §8 | PRTRPT | Done | `ModCommands` with `elcl/exec/Reports` (shared with the Line Printer's own screen); `midrange/LinePrinterBlockEntity` |
| §9 OS | WRKLIB, CRTLIB, CHGLIB, DLTLIB, WRKMBR, EDTMBR, CRTMBR, CPYMBR, RNMMBR, DLTMBR, CRTELPGM, DLTPGM | Done | `OsCommands`, `StoredLibraryService` |
| §9 | SBMJOB, WRKACTJOB, WRKJOB, DSPJOBLOG, HLDJOB, RLSJOB, ENDJOB, CHGJOB | Done | `StoredJobService` |
| §9 | ADDJOBSCDE, RMVJOBSCDE, WRKJOBSCDE, HLDJOBSCDE, RLSJOBSCDE | Done | `StoredJobService`, `Schedules` |
| §9 | ADDTRGEVT, RMVTRGEVT, WRKTRGEVT, HLDTRGEVT, RLSTRGEVT; all fifteen events (`*MCHIDLE`, `*MCHDONE`, `*MCHFAULT`, `*MCHNOPWR` added) | Done | `StoredJobService`, `Triggers`; machine events from Arcforge's listeners (`MachineBridge.events`) |
| §9 | WRKSYSVAL, RTVSYSVAL, CHGSYSVAL | Done | `StoredSysvalService` (SYSNAME, DATFMT, SECLVL, QMAXJOB, LOGRTN, PHOSPHOR all take effect; DATFMT in the dates the system writes, not the screens' header clock) |
| §9 | SAVLIB, RSTLIB | Done | `midrange/MidrangeSystemBlockEntity` (slot A, B with an Expansion Cabinet; the Integrated system's magazine; the default library's diskette first), `CardReaderBlockEntity`, `DisketteStack` |
| §9 | WRKDEV, WRKINV, DSPNETSTS, WRKCRFJOB, WRKMCH (added), SIGNOFF, GO, CLEAR | Done | Client screens (`client/ScreenCommands`) |
| §10 | The old CLI's aliases | Done | `terminal/TerminalCommands` |
| §11 Database files (added) | DCLF, RCVF, POSDBF, CLOF, CHNRCD, WRTRCD, UPDRCD, DLTRCD | Done | `elcl/compile/Compiler` (DCLF's variables from a `FileResolver`; the formats kept with the program), `elcl/vm/Vm` (file state in the frame, saved with the job; a level check each operation), `elcl/db/FileAccess` |
| §11 | CRTPF, CHGPF, DLTF, CLRPFM, CPYF, CPYTOIMPF, CPYFRMIMPF, RUNQRY; CRTMBR SRCTYPE(PF) | Done | `elcl/exec/DbCommands`, `elcl/store/StoredFileService`, `elcl/db` (`Dds`, `DbFile`, `Query`, `Csv`, `Report`); ELSYS's INVITEMS, DEVICES, CRFHIST and JOBS from `elcl/exec/LiveFiles` |
| §11 | WRKF, DSPPFM, DSPFD, UPDDTA; RUNQRY OUTPUT(*DISPLAY) | Done | Client screens (`client/WrkFPanel`, `DspPfmPanel`, `DspFdPanel`, `UpdDtaPanel`) on `elcl/screen/FileQueries` |

## 3. Messages (MESSAGES.md)

| IDs | Status | Raised by |
|---|---|---|
| ELC0000 (monitor everything) | Done | `VmProgram.matches` |
| ELC0001-0005, 0007-0010, 0014, 0016 | Done | Compiler, command line, VM |
| ELC0006, 0011, 0012, 0013, 0015 | Done | VM (list index, call depth, parameter count, called program failed, list limit) |
| ELC0101-0106 | Done | Command line and VM (0105: interactive-only commands in a batch job) |
| ELC0107-0110 (added) | Done | ELC0107 only for a screen command inside a program; 0108-0110 are an interactive CALL's |
| ELC0201-0208, 0210-0219, 0222 | Done | Library service, OS commands; ELC0207 when members don't fit the network's storage |
| ELC0220, 0221 | Done | SAVLIB / RSTLIB |
| ELC0301-0315 | Done | Job service: no host (0301), not found (0302), ended by operator (0303), submitted (0304), host lost (0310), schedule entries and triggers |
| ELC0401 | Done | Every Auth check (command line, VM, screens), library authority, CHGSYSVAL, others' jobs / entries / triggers |
| ELC0402 | Done | Sign-on (`StoredUserService.signOn`, the desk refusing an unsigned session) |
| ELC1201-1206 | Done | Inventory commands (ELC1203 when a recall starts) |
| ELC1301-1305 | Done | Device commands |
| ELC1306, 1307 (added) | Done | Printing |
| ELC1308, 1309 (added) | Done | RNMDEV |
| ELC1310, 1311 (added) | Done | SAVLIB / RSTLIB |
| ELC1312-1317 (added) | Done | Display Panels (images, regions, data sources) |
| ELC1318-1322 (added) | Done | Machine commands (Arcforge) |
| ELC1401-1404 | Done | Crafting commands |
| ELC1501-1509 (added) | Done | PLCs (`plc/`, `elcl/exec/PlcCommands`, `Compiler.Target.PLC_LOCAL`) |
| ELC2201-2209, 2220-2228, 2230-2247 (added) | Done | Database files: the VM's file operations and the file service (2201-2209), the definition compiler `elcl/db/Dds` (2220-2228), the database commands (2230-2247) |
| USRnnnn | Done | SNDPGMMSG |

## 4. Screens (OS.md §8)

| # | Screen | Status | Where |
|---|---|---|---|
| 1 | Sign On | Done | `client/SignOnPanel`, signing on through the server |
| 2 | Main Menu | Done | `client/MainMenu`, `MainMenuPanel` |
| 3 | Work with Libraries | Done | `client/WrkLibPanel` |
| 4 | Work with Members | Done | `client/WrkMbrPanel` |
| 5 | Source Editor | Done | `client/EditorPanel`, `EditorModel` |
| 6 | Command Prompter | Done | `client/PrompterPanel`, `ValueListWindow` |
| 7 | Compile Listing | Done | `elcl/compile/Listing`, `client/DspSplfPanel` |
| 8 | Work with Active Jobs | Done | `client/WrkActJobPanel`: interactive and batch jobs, host, *WAIT, budget, hosts busy / total |
| 9 | Work with Job / Display Job Log | Done | `client/WrkJobPanel`, `DspJobLogPanel`; the VM's call stack; ended jobs' logs |
| 10 | Work with Job Schedule Entries | Done | `client/WrkJobScdePanel` |
| 11 | Work with Trigger Events | Done | `client/WrkTrgEvtPanel` |
| 12 | Display Messages | Done | `client/DspMsgPanel`, MW indicator |
| 13 | Work with Output | Done (6=Print Blocked: printer) | `client/WrkSplfPanel`, `DspSplfPanel` |
| 14 | Work with System Values | Done | `client/WrkSysvalPanel` |
| 15 | Help panels (F1) | Done | `CrtTerminal.help()` |
| 16 | Command Entry | Done | `client/CommandEntryPanel` |
| - | Work with Devices (names, 2=Change, 8=Locate) | Done | `client/DevicesPanel`, `client/CrtLocate` |
| - | Control Interface block | Done | `block/ControlInterfaceBlock` |
| 17 | Work with Files (added) | Done | `client/WrkFPanel`; Work with Members lists files and PF members (`client/WrkMbrPanel`) |
| 18 | Display Physical File Member / Display Report (added) | Done | `client/DspPfmPanel` |
| 19 | Display File Description (added) | Done | `client/DspFdPanel` |
| 20 | Update Data (added) | Done | `client/UpdDtaPanel` |

## 5. Blocked on hardware not in the mod

| Hardware | What waits on it | Interface |
|---|---|---|
| Mainframe | Batch jobs that resume after a restart | `JobHost` (`resumes()` true) |
| Status Display, NOC Video Wall, Rack Console screen | More displays for SNDDSPTXT (the Display Panel is one already) | `DisplayDevice` |
| Label Maker (not in the mod) | Renaming devices with it | none needed: `ElclDevices.rename` |

Compute Servers (4 jobs each) and the Midrange Systems (1, 2 with an Expansion Cabinet, 4 on the Integrated system)
are batch job hosts. The Midrange line's Card Reader, Line Printer and 8" Diskettes are in the mod too (Parts 9 and 12).

## 6. Known limits and decisions

- **Storage:** members count against free drive space and block saves when it's full (ELC0207), but don't stop items
  going into the drives.
- **No per-item tier:** the mod has none, so CHGITMTIER works through the Tape Libraries' lists.
- **Crafting history:** crafting jobs leave their Scheduler as soon as they end. Their records are kept in the
  network's history (`CraftLog`, saved with the system's data, the last CRFLOGRTN) for RTVCRFSTS, RTVCRFLOG, Work with Jobs' history and the
  Scheduler and server panels; it survives a restart.
- **Sign-on:** needed at SECLVL 30 only on a network with a Firewall; without one everyone has full authority anyway.
  SECLVL 10 also stops the OS asking the Firewall (it still guards the blocks).
- **Job control:** holding, ending or changing another user's job, schedule entry or trigger needs *SECOFR or full
  authority (ELC0401 *JOBCTL).
- **Triggers need a host:** triggered programs and schedule entries run as batch jobs, so they need a job host;
  without one the creator gets ELC0301 in their message queue.
- **Edge state:** triggers' edge state is saved, but device status changes are first recorded (not fired) after a
  restart.
- **Item names:** `%NAME` and device type names come from the server's language, so mod items may show translation keys
  on a dedicated server.
- **Real time in tests:** INTERVAL() and the triggers' debounce are real time (`RealTime`); the game tests count ticks
  at 50 ms instead.

## By part

| Part | Status | Where |
|---|---|---|
| 1. Persistence | Done | `elcl/store/`: `ElclStore` (saved data, one `SystemData` per network), `StoredLibraryService`, `StoredMessageService`, `StoredSpoolService`, `StoredSysvalService`, `StoredUserService` (*SECOFR = the Firewall's owner), `ElclConfig`. ELSYS rebuilt and its samples compiled on every load; programs keep their source, compile date and source version and compile again on load; members cost a byte per 64 characters of drive space (DSPNETSTS counts them; ELC0207); caps of 200 spooled files and 500 messages per user (config `elcl`); SNDMSG chat notice. Tests: `SystemDataTest`; game tests `elcl_persistence`, `elcl_storage_full`, `elcl_retention`, `elcl_library_authority`. Members count against free drive space but don't stop items going in |
| 2. Stable device names | Done | Names stored on the device (`RackDevice.deviceName`, carried by `RackDeviceItem`; `TerminalDeskBlockEntity.deviceName`, `ControlInterfaceBlockEntity.name`, both carried on the block item by the `encodedlogistics:device_name` component), given by `elcl/exec/ElclDevices` after every network solve (`ControllerStructures`) in the old counting order, so unnamed worlds keep their counted names; the system remembers each name's holder (`SystemData.deviceNames`). `RNMDEV` (new, configure) and WRKDEV 2=Change rename; ELC1308 on a duplicate, ELC1309 done. No Label Maker in the mod. Tests: game tests `device_names_migration`, `device_names_stable`, `device_names_moved` |
| 3. Language and VM | Done | `elcl/vm/`: `Lowerer` (every control-flow form, subroutines, monitors as instruction ranges), `Vm` (budgeted, resumable; frames, subroutine stack, loop counters, the async wait, all to NBT), `Values` (types, *DEC rounding, overflow, built-ins). CALL with PARM by reference (copied back on return), MONMSG command and program level with ranges and CMPDTA, RCVMSG, SNDPGMMSG, ELC0006/0011/0012/0013/0015 at run time. `elcl/job/JobManager` runs programs each server tick (budgets 200 / 100, global 2,000; config `elcl`); CALL on a command line runs in the interactive job (`InteractiveCalls`, ELC0108-0110); ENDJOB stops it. Mod commands in `elcl/exec/ModCommands` (inventory, crafting, devices, power, SNDDSPTXT, PRTRPT), async waits (`Waits`: RECALL, CRAFT). Cable parts are named devices (INGRESS01...) and can be disabled. SAVLIB/RSTLIB wait on Part 7. Tests: `VmTest` (13), game tests `elcl_mod_commands`, `elcl_interactive_call`, `elcl_examples` (all five examples run) |
| 4. Batch jobs | Done | `elcl/job/StoredJobService` replaces the stub: interactive jobs (in memory, per session) and batch jobs (saved with the system in `elcl/store/JobData`, a running one with its VM state). `JobHost` (name, capacity, resumes, online) and `JobHosts` (registered sources; Compute Servers built in, computeServerJobs = 4, not resuming). SBMJOB (ELC0301 with no host, or an unknown HOST(); ELC0304), the job queue (*JOBQ; priority order; QMAXJOB), HLDJOB / RLSJOB (queued or running), ENDJOB *CNTRLD / *IMMED (ELC0303 logged and sent to the user), ELC0310 when a host is unloaded or loses power (sent to the user too), resumed after a restart on a resuming host, ended on any other. Job logs (LOG(*YES) logs commands), the last LOGRTN ended jobs' logs kept and saved. WRKACTJOB / WRKJOB / DSPJOBLOG show batch jobs (host, *WAIT, budget %), the call stack from the VM, hosts busy / total. Batch jobs run as their submitter with that player's Firewall permissions (`BatchContext`). Tests: game tests `batch_compute_server`, `batch_queue_and_hosts` (fake Midrange System, Expansion Cabinet, Integrated Midrange System), `batch_restart` (fake Mainframe resumes, fake Midrange ends with ELC0310), `batch_budget`, `batch_logs` |
| 5. Scheduling and triggers | Done | `elcl/job/Schedules` (each tick: due entries on loaded networks submit their command as a batch job as their creator; *ONCE removed after, *DAILY at TIME() on the game clock, *INTERVAL every INTERVAL() real seconds; held entries wait), `elcl/job/Triggers` (every event in COMMANDS.md 9: item counts, storage use and UPS power polled every half second with edge state saved; device status changes, ended crafts (`JobEvents.ended`) and redstone (`ElclEvents`) as events; 1-second debounce; &EVENT and &DATA passed to the program; held triggers don't fire). Both persist in `JobData` with the creator's player id and run as them; a failure to submit (ELC0301) goes to the creator's message queue. WRKJOBSCDE (2=Change, 3=Hold/Release, 4=Remove, 10=Submit now) and WRKTRGEVT (2, 3, 4) run working commands. `ControllerStructures.loaded` says whether a network is loaded. Tests: game tests `schedule_entries`, `trigger_items`, `trigger_events`, `trigger_power`, `trigger_storage` (real seconds are ticks at 50 ms there: `RealTime`) |
| 6. Security and sign-on | Done | `elcl/screen/UserService` + `elcl/store/StoredUserService`: profiles saved with the system (library list ELGPL ELSYS, current library first on it, made at first use or sign-on), *SECOFR = the Firewall's owner and server operators, `signOn` (ELC0402 for another player's name, ELC0201 for a library that isn't there). Sign-on through the server (`signon` screen query; `SignOnPanel` waits for its answer); `TerminalDeskMenu` refuses anything but info and the sign-on until then, at SECLVL 30 with a Firewall. `elcl/exec/Authority` is the one check for terminals and batch jobs: the Firewall's permissions (by player, or by the submitter's id), full authority with no Firewall or at SECLVL 10. ELC0401 on screens too, and when managing another user's jobs, schedule entries or triggers (`mayManage`). CHGSYSVAL: *SECOFR or no Firewall. *LIBL and *CURLIB use the profile. DATFMT changes the date shown. Tests: game tests `security_authority`, `security_signon` |
| 7. Interfaces for the Midrange line | Done | `docs/elcl/INTERFACES.md` says what each Midrange device and the Mainframe implement. `elcl/job/JobHost` (Part 4); `elcl/device/DisketteDevice`, `Diskette`, `LibraryImage` (NBT), `Diskettes` - SAVLIB / RSTLIB (ELC0220 / ELC0221; ELC1301, ELC1302, ELC1310, ELC1311 added, ELC0201, ELC0205), with the size limit (disketteBytes, 64 KB); `elcl/device/PrinterDevice`, `Printers` - PRTRPT and WRKSPLF 6=Print (ELC1301, ELC1306, ELC1307); `crafting/RecipeLibrarySource`, `RecipeLibraries` - a library's recipes count as the network's (`CraftRequests.schematics`), steps run on a provider that `accepts` them (`CraftingProvider.accepts`, used by `JobRunner` and the Fabricator, Gateway and Fabrication Server); `elcl/device/DisplayDevice` too. Tests: game tests `diskette_save_restore`, `printer_print`, `recipe_library` (fakes) |
| 9. The Midrange line | Done | `midrange/`: the Midrange System (1 thread, max job 64, 1 batch job; 2 / 128 / 2 with an Expansion Cabinet) and Integrated Midrange System (4 / 512 / 4, a Diskette Magazine) in `MidrangeSystemBlockEntity`. Each is a batch `JobHost`, a `DisketteDevice`, a `RecipeLibrarySource`, a `CraftingProvider` crafting its diskettes' recipes (40 / 25 ticks a step) and a crafting job host (a Craft Plan choice, `ControllerStructures.schedulersOf`, `crafting/JobHost.at`). Its states: an IPL on coming online (config `midrangeIplTicks`; its batch jobs end, ELC0310), run, busy, attention; Hold / Release its queue. The Card Reader (`CardReaderBlockEntity`, a `DisketteDevice`), Line Printer (`LinePrinterBlockEntity`, a `PrinterDevice`: Printouts, one paper a page) and Keypunch (`KeypunchBlockEntity`) work for a Midrange System on their network (`Midranges.host`), use no lanes and are listed on Work with Devices (`network/ListedDevice`). Names MIDRANGE01, KEYPUNCH01, CARDRDR01, PRT01 (`MidrangeDevice`, carried by the item). Green screens with the desk's CRT (`client/crt/CrtDisplay`, `CrtMachineScreen`): the three peripherals' and the control panels (`MidrangePanelScreen`). The Integrated system's console opens the Terminal Desk's session (`TerminalDeskMenu` at its master). Tests: game tests `midrange_footprints`, `midrange_expansion_cabinet`, `midrange_peripherals`, `midrange_printer_pages`, `midrange_system_crafts`, `midrange_tiers` |
| 12. The Midrange line v4 | Done | `docs/midrange/HANDOFF.txt` (v4). Art: 1 texel a model px, the v4 models, blockstates and shapes (`data/encodedlogistics/shapes/midrange.json`). The Midrange System is one block (`expansion` shows the second diskette slot; an Expansion Cabinet beside it attaches); the Integrated system 3 x 2 with click zones on its model (`IntegratedMidrangeBlock.zone`: console, control panel, nothing); the Line Printer 1 x 2. Green screens text only (`CrtMachineScreen`; `docs/midrange/layouts`): option fields, the command line (run as at a Terminal Desk), F4 lists; MRCTL / IMCTL drive options 4 / 5 / 8 and job options 3 / 4 / 6 (`crafting/JobRunner` holds single jobs); KEYPUNCH item-name fields; CARDRDR's read plan; PRINTER's spooled-file list; items go in on the blocks and out with a sneak-use (`Midranges.use`); the HUD hidden under green screens. Both Midrange systems are their networks' controllers (`network/SourceNode`: a lane source with its own budget, `ControllerStructures.Structure.midrange`, `ControllerBuffer`; another controller is a conflict, E8; config `midrangeLaneFaces`, `midrangeEnergyBlocks`, `integratedMidrange*`). The Disk Drive (`DiskDriveBlockEntity`: a Storage Drive as hot storage, `storage/DriveHolder`, spin-up and spin-down, DISK01, DSKDRV) and Tape Drive (`TapeDriveBlockEntity`: a Tape Reel as cold storage, `rack/TapeSource`, archive and recall, TAPE01, TAPDRV), the Tape Reel (`item/TapeReelItem`). Printouts (`midrange/Printout`, `item/PrintoutItem`): pages drawn on the client (`client/printout/PrintoutPages`), the reader (`PrintoutReaderScreen`), held and framed (`PrintoutRendering`); the reports in 56 columns with inks (`elcl/exec/Reports`); a report out of paper part-way waits. Tests: game tests `midrange_integrated_zones`, `midrange_screens`, `midrange_controller`, `midrange_storage_drives` and the updated Midrange ones |
| 10. Display Panels | Done | `display/`: panels merge into screens of up to `displayMaxWidth` x `displayMaxHeight` (8 x 6) panels (`DisplayScreens`: the largest rectangles, the master its bottom-left block, bezels and the LED from the rectangle, content kept by the master), 32 x 32 canvas px a panel drawn on the client as a dynamic texture (`client/display/DisplayCanvases`, `CanvasPainter`; reference `tools/dsp_canvas.py`); states off / boot / online / no signal; one lane a screen; `DSP01` names. Content (`DisplayContent`): Text / Dashboard / Script-controlled modes, styled text lines, regions with widgets (storage, cold, energy, lanes, jobs, item, clock, devices, UPS, text, graph, image); live data a second (`DisplayData`, `DisplayFramePayload`), graphs from a Monitoring Server or the screen's own history (`DisplayHistory`, `displayHistorySeconds`); images from `<world>/encodedlogistics/images/<SYSNAME>/` (`DisplayImages`: off-thread, 16 / 64 / 256 / FULL colours, Bayer dither; `allowImages`, `displayMaxImageSize`, `displayMaxImageBytes`, `displayImageColors`). SNDDSPTXT and the six new commands (`elcl/exec/DisplayCommands`), ELC1312-1317, `*DSPTOUCH` (`DisplayTouch`). The configuration screen (`client/screen/DisplayPanelScreen`, `menu/DisplayPanelMenu`; build permission). Tests: `DisplayImagesTest`; game tests `display_merging`, `display_on_network`, `display_commands`, `display_configuration` |
| 13. Arcforge machines | Done | The Small Wireless Bridge (optional Arcforge, API 1.x; `compat/arcforge/ArcforgeCompat` checks the version, `ArcforgeMachines` is the only class using Arcforge's API): attached to a machine's face (`machine/MachineBridges`, saved per level), linked with a Link Card through a Wireless Controller (build permission, the machine's owner), a one-lane wireless node; named by type (`ARCCRU01`); Work with Devices, the popups; Gateways (`GATEWAY01`) feeding it over the air for Processing Schematics; power from network; WRKMCH; the machine commands, ELC1318-1322, the `*MCH*` triggers; Monitoring Server and Display Panel graphs (`*MCHOPS`, `*MCHFE`). Tests: game tests `arcforge_*` (run with `-Parcforge_jar`), unit tests `ArcforgeCompatTest`, `WrkMchTest` |
| 14. Database files | Done | Physical files (`*FILE`, attribute PF) from PF source members (`elcl/db/Dds`: types A, S, P, L, T; UNIQUE, TEXT, COLHDG; 50 fields, a key of 4), records by relative record number with a key index (`DbFile`), saved with the system (`SystemData`), costing storage as source (`charsPerStorageByte`) and limited by `maxRecordsPerFile` (10,000). CRTPF with its listing, CHGPF keeping records field by field (ELC2232 for each dropped), DLTF, CLRPFM, CPYF, CSV through the folder-sync folder (CPYTOIMPF / CPYFRMIMPF; `allowFolderSync`), RUNQRY (QRYSLT as an ELCL expression, SORT, *DISPLAY / *PRINT / *OUTFILE). DCLF and the file operations in programs, the formats a program was compiled with kept with it (level check ELC2207). ELSYS's live system files (INVITEMS, DEVICES, CRFHIST, JOBS; the Firewall's view permission). Reading needs the library, writing *CHANGE on it. Screens: Work with Files, Display Physical File Member, Display File Description, Update Data, RUNQRY's Display Report; PF members in Work with Members and the editor (DDS check; DCLF checked against the files' formats). PF members sync as `.pf`; SAVLIB / RSTLIB carry files and records; a Display Panel `*TABLE` widget (SNDDSPWDG FILE() QRYSLT() SORT(), `display/DisplayData`, `client/display/CanvasPainter`). The ELSYS sample ITEMSETUP / LOGITEMS / ITEMHIST. Tests: `elcl.db.DdsTest`, `DbFileTest`, `QueryCsvTest`, `elcl.DbVmTest`, `SystemDataTest`, `client.crt.DbScreensTest` (layouts 17-22); game tests `db_files`, `db_programs`, `db_query`, `db_system_files`, `db_copies`, `db_persistence`, `db_limits`, `db_authority`, `db_example`, `db_screens`, `db_save_restore`, `db_display_table` |
| 15. PLCs | Done | `docs/plc/HANDOFF.txt`. The block (`plc/PlcBlock`: wall-mounted, facing, state off / stop / run / fault, slot1-4; shape from `docs/plc/shapes`), `PlcBlockEntity` (six redstone channels shared with the Control Interface through `blockentity/RedstoneChannels`; FE buffer `plcBuffer`, `plcEnergy` a tick, or the network's power; the scan - `plcInstructionsPerTick`, a pass's variables carried into the next by `Vm.start(host, program, carried)`, RETAIN(*YES) kept in the program; FAULT with the message and line, `plcFaultOutputs`; `plcMaxPerChunk` / `plcMaxPerServer`; a reload is a power-up), `PlcVmHost` (the same VM a batch job uses; without a network it refuses all but `Compiler.plcLocal` - ELC1502), `PlcContext` (the loader's Firewall authority), `PlcSensors` (presence, inventory, fluid, light, timer), `PlcProgram` (component `plc_program`: on the item, on the EEPROM Cartridge). Device `PLC01` (NamedDevice; type PLC in Work with Devices). Screens: `menu/PlcMenu` and `client/crt/PlcScreen` (PLCSTS, PLCIO, PLCMOD and its setting prompter, retained variables and the log), the source editor `client/crt/PlcEditorScreen` (EditorPanel with a compile target; PlcMenu answers its source / save requests as a `menu/CrtHost`, compiling on save). I/O LEDs per face: `client/PlcRenderer`. Art: `tools/plc.py`, `blockbench/plc_with_modules.bbmodel`. Open questions settled: the per-tick budget and limits as proposed; outputs hold on FAULT (config); ladder logic later; cartridges are written from the PLC's screen only (no CRTPLCCTG); anyone may open a PLC off a network (`plcOpenToAnyone`, else only its placer and operators). Tests: unit `PlcProgramTest`; game tests `plc_standalone`, `plc_networked` |
| 11. Rack access and linking | Done | The Firewall's rack access permission (`RackPermission.RACK`, inheriting build) guards Server Racks' doors, screen, devices and breaking (`rackBreakProtection`); Link Cards need build (rack access at a rack device) on the network at each end, when written and when applied (`NetworkAccess.guard`). Tests: game tests `rack_access`, `rack_access_open_and_offline`, `link_card_permissions` |
| 8. Folder sync | Done | `elcl/sync/FolderSync`: `<world>/encodedlogistics/libraries/<SYSNAME>/<LIB>/<MEMBER>.elclp`. Out: the library service's listener writes a member's file on every save, create, copy, rename and restore. In: polled every 2 s on loaded networks; changed files become members (`elcl/sync/Resequence`: unchanged lines keep their sequence numbers and dates), new folders libraries (owner QSYS, *CHANGE). Conflicts: last write wins, the loser kept as `.bak` (`SystemData.syncHashes` remembers what was last synced). Deleting a file keeps the member; deleting a member moves its file to `.deleted/`. ELSYS never synced. `allowFolderSync` AUTO (on in single-player, off on dedicated servers), TRUE, FALSE. Tests: `ResequenceTest`, game test `folder_sync` |
