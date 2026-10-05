# Terminal OS screens: inventory

Status of everything in the Control Interface / Terminal OS screens handoff (section A), after implementation.
**EXISTS**: left alone. **EXTENDED**: existing code with the handoff's additions. **NEW**: added.
Client classes are in `client/crt/` unless a path is given; server classes under `net.zagdrath.encodedlogistics`.

## Common elements

| Element | Status | Implemented by |
|---|---|---|
| Frame (id, title, system, clock, prompt, command line, message line, keys) | EXTENDED | `CrtTerminal.compose()` (logic moved out of `CrtScreen`, which now only draws and passes the game's input to it) |
| Character grid 80x24, attributes, underline, reverse | EXTENDED | `CrtGrid`: the 12 extra glyphs (`EXTRA`, `glyph()`), `box()`, `ruler()`, `columnRuler()` (the editor's and listing's `*...+... 1` form) |
| Glyph drawing, frame (no glow or scanlines) | EXTENDED | `CrtDisplay.draw()`: glyph index map, 96x70 / 160x98 sheets; the GUI-kit panel, title and screen well (docs/crt/HANDOFF.txt) - the CRT bezel and vignette are retired |
| Font sheets | EXTENDED | `textures/font/terminal.png`, `terminal_glow.png`, `font/terminal.json` (replaced from the zip; rows 0-5 byte-identical) |
| Input field | EXTENDED | `CrtField`: overwrite / Insert, Field Exit (Ctrl+Enter, keypad Enter), Field Advance, protected / numeric / uppercase / required, capacity beyond the shown length (scrolls) |
| Cursor | EXTENDED | `CrtScreen` (draws at `CrtField.cursorColumn()`) |
| Inverse / highlight | EXISTS | `CrtGrid.reverse` |
| "Work with" list | EXTENDED | `ListPanel` (+ `invalid()`, Opt help); `OsListPanel` (NEW: the OS screens' header, legend, option queue, delete confirmation) |
| Display panel | EXISTS | `TextPanel`; `InfoPanel` (NEW: label / value display built on the client) |
| Phosphor | EXTENDED | `TerminalSettings` (default `*SYSVAL`), `CrtTerminal.phosphor()`, system value PHOSPHOR from the `info` query, `MoreKeysPanel` (choice 4 = `*SYSVAL`) |
| F-keys | EXTENDED | `CrtTerminal.functionKey()`: F1 help, F4 prompter, each screen's own first (`CrtPanel.functionKey()`); Tab hook (`CrtPanel.tab()`) |
| F4 field hint | EXISTS | `CrtPanel.prompt(CrtField)` (fallback when the field isn't a command) |
| Pop-up window | NEW | `CrtWindow`; `FormWindow` (labelled fields), `ValueListWindow` (F4 lists), `CrtTerminal.confirm()` |
| "MW" messages waiting | NEW | `CrtResponsePayload.unread`, `TerminalDeskMenu.handle()`, `CrtTerminal.compose()` |
| Help panels (F1) | NEW | `CrtTerminal.help()`, `CrtPanel.help()` / `helpField()`, lang keys `crt.encodedlogistics.help.<screen>[.<field>]`; the prompter's from the registry |
| Command system | EXTENDED | `terminal/TerminalCommands.execute()`: the desk's words stay (with an alias note); anything else goes to `elcl/exec/ElclCommandLine` → `CommandRunner` → `elcl/cmd/CommandRegistry` |
| Screen data | EXTENDED | `CrtTerminal.query()` → `TerminalService.SCREEN` → `elcl/screen/ScreenQueries` |
| Control Interface block | NEW | `block/ControlInterfaceBlock`, `blockentity/ControlInterfaceBlockEntity`, `elcl/exec/RedstoneCommands`, `elcl/exec/ElclDevices`, `elcl/exec/ElclEvents` |

## Screens

| # | Screen | Status | Class |
|---|---|---|---|
| 1 | Sign On | EXTENDED | `SignOnPanel`, signing on through the server (`ScreenQueries` signon, `UserService.signOn`; the desk's menu refuses everything else until then, `TerminalDeskMenu.refused`) |
| 2 | Main Menu | EXTENDED | `MainMenuPanel`, from the option definition `MainMenu` (see below) |
| 3 | Work with Libraries | NEW | `WrkLibPanel` (+ `InfoPanel` for 5=Display) |
| 4 | Work with Members | NEW | `WrkMbrPanel` |
| 5 | Source Editor (+ full screen) | NEW | `EditorPanel`, `EditorModel` |
| 6 | Command Prompter | NEW | `PrompterPanel`, `ValueListWindow` |
| 7 | Compile Listing | NEW | `elcl/compile/Listing` (built on the server, spooled), shown in `DspSplfPanel` |
| 8 | Work with Active Jobs | NEW | `WrkActJobPanel` |
| 9 | Work with Job / Display Job Log | NEW | `WrkJobPanel`, `DspJobLogPanel` |
| 10 | Work with Job Schedule Entries | NEW | `WrkJobScdePanel` |
| 11 | Work with Trigger Events | NEW | `WrkTrgEvtPanel` |
| 12 | Display Messages | NEW | `DspMsgPanel` |
| 13 | Work with Output / Display Spooled File | NEW | `WrkSplfPanel`, `DspSplfPanel` |
| 14 | Work with System Values | NEW | `WrkSysvalPanel` |
| 15 | Help panel | NEW | `CrtTerminal.help()` (a `CrtWindow`) |
| 16 | Command Entry | EXTENDED | `CommandEntryPanel` (F4 prompts, Tab completes) |
| - | Work with Jobs (crafting) | EXTENDED | `JobsPanel`: id and command WRKCRFJOB (amendment 1) |
| - | WRKINV, WRKDEV, DSPNETSTS, Withdraw, Craft, More keys | EXISTS | `InventoryPanel`, `DevicesPanel`, `StatusPanel`, `WithdrawPanel`, `CraftPanel`, `MoreKeysPanel` (phosphor choice extended) |
| - | Deposit Items | NEW | `DepositPanel` (WRKINV F6): the player's inventory; 1=Deposit a stack, F6 everything outside the hotbar (`TerminalActions.deposit`, the Firewall's insert permission) |
| - | Work with Devices names | EXTENDED | `DevicesPanel` 2=Change (RNMDEV), the Device column starts with the device's stored name; `elcl/exec/ElclDevices` (names stored on the device, `SystemData.deviceNames`) |
| - | Work with Devices topology | EXTENDED | `ControllerStructures.deviceRows`: each rack, its devices under it (top unit first, tree lines), then the rest of the network beside the racks; each device once |
| - | Midrange control panels | NEW | `MidrangePanelScreen` (`menu/MidrangePanelMenu`; layouts `mrctl`, `imctl`): MRCTL / IMCTL; the status line (system, code and word, threads, max job, batch); the drives or the magazine's four positions - 4=Eject (4=Remove from magazine), 5=Display recipes (F12 back), 8=Make default library; the jobs - 3=Hold, 4=End, 6=Release; the command line; F7 IPL, F10 Hold queue, F11 Release |
| - | Card Punch, Card Reader, Line Printer | NEW | `KeypunchScreen`, `CardReaderScreen`, `LinePrinterScreen` (`menu/KeypunchMenu`, `CardReaderMenu`, `LinePrinterMenu`; layouts `keypunch`, `cardrdr`, `printer`): item names in the grid (F4: the item list), the deck and its read plan, the report and spooled file (F4: the network's files), pages, paper and status; F6 punches, reads or prints |
| - | Disk Drive, Tape Drive | NEW | `DiskDriveScreen`, `TapeDriveScreen` (`menu/DiskDriveMenu`, `TapeDriveMenu`; layouts `dskdrv`, `tapdrv`): DSKDRV / TAPDRV; status, the pack or reel and what's on it; 4=Unload, 5=Display contents (F12 back), 7=Rewind (tape); the command line |
| - | Printout reader | NEW | `client/printout/PrintoutReaderScreen`: the game's GUI kit, not a green screen; a page at the largest scale that fits, arrows, Page Up / Page Down, the wheel |
| - | The Midrange machines' frame | NEW | `CrtMachineScreen`: the desk's CRT (`CrtDisplay`, shared with `CrtScreen`) and the same frame as every screen (the panel's id: MRCTL, CARDPUNCH, ...). Text only (HANDOFF v4 3): no buttons, no slots, no inventory; the hotbar and HUD hidden while it's open. Fields: values, options beside a list's rows (sent on Enter, then cleared), the command line; F1 help, F4 a list, F12 back or close; Tab and Up / Down move between fields |
| - | Integrated Midrange System console | EXTENDED | The Terminal Desk's session (`TerminalDeskMenu` opened at the system's master), while the system is running |
| - | Work with Machines | NEW | `WrkMchPanel` (WRKMCH; `ScreenQueries` "machines"): Arcforge machines with a Small Wireless Bridge on - Machine, Type, Status, Prog, Energy, Rate; 2=Change (`CHGMCHCFG`), 5=Display (DSPMCH: `TextPanel`, `TerminalService` "machine"), 7=Enable/Disable (`CHGMCHSTS`), 8=Sides (`CHGMCHCFG SIDE() SIDEMODE()` a changed side); F4 steps a `FormWindow` field through its choices |
| - | Work with Files | NEW | `WrkFPanel` (WRKF LIB(); `elcl/screen/FileQueries` files): File, Attr, Chg, Records, Text; 2=Change data (`UpdDtaPanel`), 4=Delete (DLTF), 5=Display data (`DspPfmPanel`), 8=Display description (`DspFdPanel`); F6 prompts CRTPF. ELSYS: 2 and 4 refused (ELC0205). Layout `17_wrkf` |
| - | Work with Members' files | EXTENDED | `WrkMbrPanel`: PF members beside ELCLP; the library's files after the members (Type `*FILE`, the file options and 8); 14=Compile on a PF member runs CRTPF (CHGPF when its file is there). Layout `22_wrkmbr_files` |
| - | Display Physical File Member / Display Report | NEW | `DspPfmPanel` (DSPPFM FILE(), RUNQRY OUTPUT(*DISPLAY); FileQueries filedata / runqry, 200 records a window): headings bright, Position to (a key, or a record number), PageUp / PageDown, F19 / F20 shift 40. Layouts `18_dsppfm`, `19_runqry` |
| - | Display File Description | NEW | `DspFdPanel` (DSPFD FILE(); FileQueries filedesc). Layout `20_dspfd` |
| - | Update Data | NEW | `UpdDtaPanel` (UPDDTA FILE(); FileQueries record / putrecord / addrecord / delrecord): change and entry modes, F6 / F10, F7 / F8, F11 delete (confirmed), Position to key, values checked by type (`FieldDef.convert`) and the cursor to the field an error names; 12 fields a page. Layout `21_upddta` |
| - | Source Editor, PF members | EXTENDED | `EditorPanel`: the member's type on row 1; a PF member checked as DDS (`elcl/db/Dds`), no prompter; an ELCLP member's DCLFs checked against the files' formats (FileQueries fileformat, asked for as they're met) |
| - | Display Panel configuration | NEW | `client/screen/DisplayPanelScreen` (`menu/DisplayPanelMenu`, `DisplayConfigPayload`): Mode, Layout (drag edges to move or split regions, right-click removes), Widgets (a picker for the widget, its source and colour; a Table takes a file, LIB/NAME). Not a green screen: the L3 Switch panel's frame |

### Main Menu options

The menu and its F1 help panel come from one definition, `client/crt/MainMenu.java` (number, screen command,
availability, label and description in `crt.encodedlogistics.menu.<n>` / `.<n>.help`), so they can't drift apart. The help
lists all nine options in two columns on its first page. An option marked NOT_AVAILABLE shows "Option n not available."
on the message line; a number not on the menu shows "Option n is not on this menu." (`MainMenuTest`).

| Option | Label | Command | Screen | Status | Data behind it |
|---|---|---|---|---|---|
| 1 | Work with Inventory | WRKINV | `InventoryPanel` | Available | the network's storage (real) |
| 2 | Work with Jobs | WRKCRFJOB | `JobsPanel` | Available | crafting jobs (real); F10 the crafting job history (`CraftLog`) |
| 3 | Work with Devices | WRKDEV | `DevicesPanel` | Available | the network's topology (real) |
| 4 | Display Network Status | DSPNETSTS | `StatusPanel` | Available | the network's status (real) |
| 5 | Work with Libraries | WRKLIB | `WrkLibPanel` | Available | `elcl.store.StoredLibraryService`: libraries, members and programs in the system's saved data |
| 6 | Work with Active Jobs | WRKACTJOB | `WrkActJobPanel` | Available | `elcl.job.StoredJobService`: interactive and batch jobs with their host, status and budget use; hosts busy / total |
| 7 | Display Messages | DSPMSG | `DspMsgPanel` | Available | `elcl.store.StoredMessageService`: message queues in the system's saved data (500 per user by default) |
| 8 | Work with Output | WRKSPLF | `WrkSplfPanel` | Available | `elcl.store.StoredSpoolService`: spooled files in the system's saved data (200 by default); 6=Print through `elcl.device.Printers` (ELC1301 with no printer) |
| 90 | Sign Off | SIGNOFF | - | Available | - |

Every screen also opens from its command on any command line (`ScreenCommands`): WRKLIB, WRKMBR LIB(), WRKF LIB(), DSPPFM FILE(),
DSPFD FILE(), UPDDTA FILE(), RUNQRY (OUTPUT(*DISPLAY)), EDTMBR MBR(),
WRKACTJOB, WRKJOB JOB(), DSPJOBLOG JOB(), WRKJOBSCDE, WRKTRGEVT, DSPMSG, WRKSPLF JOB(), WRKSYSVAL, WRKINV, WRKDEV,
WRKMCH, DSPNETSTS, WRKCRFJOB; GO MAIN / GO HELP, SIGNOFF.

## ELCL packages

Every package in the ELCL handoff is in place (status per item: IMPLEMENTATION_STATUS.md):

- `elcl` - `ElclMessages` (MESSAGES.md, with the added IDs marked there), `ElclMessage`, `ElclException`, `Diagnostic`,
  `SourceLine`
- `elcl.cmd` - `CommandRegistry`, `CommandDefinition`, `ParamDef`, `BuiltinCommands` (every COMMANDS.md schema, plus
  CHGLIB, CRTMBR, CHGJOB, HLDJOBSCDE / RLSJOBSCDE, HLDTRGEVT / RLSTRGEVT, RNMDEV, WRKCRFJOB, GO, CLEAR), `Invocation`,
  `CommandExecutor`, `Wait`
- `elcl.lex` / `elcl.parse` / `elcl.compile` - lexer, parser, compiler (checks, cross reference), `Listing`
- `elcl.vm` - `Values` (types, conversions, expressions, built-ins), `Lowerer` (statements to flat code), `VmProgram`,
  `Vm` (budgeted, resumable, NBT), `VmHost`
- `elcl.exec` (game side) - `CommandRunner` (command lines), `ElclCommandLine`, `ElclContext`, `Authority`, `OsCommands`,
  `ModCommands` (COMMANDS.md 3-8), `RedstoneCommands`, `ElclDevices` (device names), `ElclItems`, `ElclEvents`,
  `ElclSetup`
- `elcl.store` - `ElclStore` (saved data, one `SystemData` per network), `JobData`, `StoredLibraryService`,
  `StoredMessageService`, `StoredSpoolService`, `StoredSysvalService`, `StoredUserService`, `ElclConfig` (the config's
  `elcl` section)
- `elcl.job` - `StoredJobService` (interactive and batch jobs, the job queue, logs, schedule entries, triggers),
  `JobManager` (runs programs each server tick within the budgets), `JobHost` / `JobHosts`, `BatchContext`,
  `JobVmHost`, `Waits`, `InteractiveCalls`, `Schedules`, `Triggers`, `RealTime`
- `elcl.sync` - `FolderSync`, `Resequence`
- `elcl.device` - `PrinterDevice` / `Printers`, `DisketteDevice` / `Diskette` / `Diskettes` / `LibraryImage`,
  `DisplayDevice` / `Displays`, `DeviceSources`; `crafting.RecipeLibrarySource` / `RecipeLibraries` (INTERFACES.md);
  implemented by the Midrange line in `midrange/` (registered by `Midranges.register`)
- `elcl.screen` - the screens' services (`ElclServices`, `LibraryService`, `JobService`, `MessageService`,
  `SpoolService`, `SysvalService`, `UserService`), `ScreenQueries`, `ElclSystem`

No stubs are left. ELC0107 "Command &1 is not available yet." is only answered by a screen command (WRKLIB, DSPMSG...)
run inside a program, which has no screen to open. The language
statements (PGM, DCL, IF, DO, MONMSG and the rest) are program-only: on a command line they're ELC0106.

## Tests

- Unit (`gradlew test`): `elcl.CompilerTest`, `elcl.LexerParserTest` (examples compile clean; one test per
  compile-time message; schemas), `elcl.VmTest` (arithmetic, strings, lists, every loop, SELECT, GOTO, subroutines,
  CALL by reference, monitors, runtime errors, budget, save and load mid-run, examples start), `elcl.SystemDataTest`,
  `elcl.ResequenceTest`, `client.crt.CrtGridFieldTest`, `client.crt.LayoutTest` (every screen composed and compared
  with `src/test/resources/layouts/*.txt`), `client.crt.EditorTest`, `client.crt.MainMenuTest`; database files:
  `elcl.db.DdsTest`, `DbFileTest`, `QueryCsvTest`, `elcl.DbVmTest`, `client.crt.DbScreensTest` (layouts 17-22).
- Game tests (`gradlew runGameTestServer`): `control_interface`, `elcl_os_commands`, `desk_commands`; `elcl_persistence`,
  `elcl_storage_full`, `elcl_retention`, `elcl_library_authority`; `device_names_migration`, `device_names_stable`,
  `device_names_moved`, `device_locate_box`; `elcl_mod_commands`, `elcl_interactive_call`, `elcl_examples`;
  `batch_compute_server`, `batch_queue_and_hosts`, `batch_restart`, `batch_budget`, `batch_logs`; `schedule_entries`,
  `trigger_items`, `trigger_events`, `trigger_power`, `trigger_storage`; `security_authority`, `security_signon`;
  `diskette_save_restore`, `printer_print`, `recipe_library`; `folder_sync`; `midrange_footprints`,
  `midrange_expansion_cabinet`, `midrange_peripherals`, `midrange_printer_pages`, `midrange_system_crafts`,
  `midrange_tiers`, `midrange_integrated_zones`, `midrange_screens`, `midrange_controller`, `midrange_storage_drives`; `rack_access`, `rack_access_open_and_offline`, `link_card_permissions`; `display_merging`,
  `display_on_network`, `display_commands`, `display_configuration`; `db_files`, `db_programs`, `db_query`,
  `db_system_files`, `db_copies`, `db_persistence`, `db_limits`, `db_authority`, `db_example`, `db_screens`,
  `db_save_restore`, `db_display_table`. Unit: `display.DisplayImagesTest`.
- Not automated: rendering in each phosphor at GUI scales 1-4 and small windows, the blinking locate box and the
  facade preview (client rendering), and the screens themselves in a running client (`LayoutTest` composes each one).

## Deviations from the layouts

- Sign On: the new fields' labels sit at column 0, lined up with the existing User / Password rows (kept as they were).
- Command Entry: history drawn as before (column 2).
- Editor: the column ruler is the real `*...+... 1` form, its numbers over the tens; the layout's is a column off.
  F19 / F20 show columns 1-72 / 9-80 (a shift of 8: "40" in the handoff can't give those windows).
- Display Spooled File: the real compile listing (full source, then cross reference and messages).
