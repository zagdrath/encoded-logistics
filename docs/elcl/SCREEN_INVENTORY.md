# Terminal OS screens: inventory

Status of everything in the Control Interface / Terminal OS screens handoff (section A), after implementation.
**EXISTS**: left alone. **EXTENDED**: existing code with the handoff's additions. **NEW**: added.
Client classes are in `client/crt/` unless a path is given; server classes under `net.zagdrath.encodedlogistics`.

## Common elements

| Element | Status | Implemented by |
|---|---|---|
| Frame (id, title, system, clock, prompt, command line, message line, keys) | EXTENDED | `CrtTerminal.compose()` (logic moved out of `CrtScreen`, which now only draws and passes the game's input to it) |
| Character grid 80x24, attributes, underline, reverse | EXTENDED | `CrtGrid`: the 12 extra glyphs (`EXTRA`, `glyph()`), `box()`, `ruler()`, `columnRuler()` (the editor's and listing's `*...+... 1` form) |
| Glyph drawing, glow, scanlines, vignette, bezel | EXTENDED | `CrtScreen.extractRenderState()`: glyph index map, 96x70 / 160x98 sheets |
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
| 1 | Sign On | EXTENDED | `SignOnPanel` |
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
| - | Work with Devices topology | EXTENDED | `ControllerStructures.deviceRows`: each rack, its devices under it (top unit first, tree lines), then the rest of the network beside the racks; each device once |

### Main Menu options

The menu and its F1 help panel come from one definition, `client/crt/MainMenu.java` (number, screen command,
availability, label and description in `crt.encodedlogistics.menu.<n>` / `.<n>.help`), so they can't drift apart. The help
lists all nine options in two columns on its first page. An option marked NOT_AVAILABLE shows "Option n not available."
on the message line; a number not on the menu shows "Option n is not on this menu." (`MainMenuTest`).

| Option | Label | Command | Screen | Status | Data behind it |
|---|---|---|---|---|---|
| 1 | Work with Inventory | WRKINV | `InventoryPanel` | Available | the network's storage (real) |
| 2 | Work with Jobs | WRKCRFJOB | `JobsPanel` | Available | crafting jobs (real) |
| 3 | Work with Devices | WRKDEV | `DevicesPanel` | Available | the network's topology (real) |
| 4 | Display Network Status | DSPNETSTS | `StatusPanel` | Available | the network's status (real) |
| 5 | Work with Libraries | WRKLIB | `WrkLibPanel` | Available | `elcl.store.StoredLibraryService`: libraries, members and programs in the system's saved data |
| 6 | Work with Active Jobs | WRKACTJOB | `WrkActJobPanel` | Available | `StubJobService`: each session's interactive job; batch jobs never appear until `elcl.job` / `elcl.vm` |
| 7 | Display Messages | DSPMSG | `DspMsgPanel` | Available | `elcl.store.StoredMessageService`: message queues in the system's saved data (500 per user by default) |
| 8 | Work with Output | WRKSPLF | `WrkSplfPanel` | Available | `elcl.store.StoredSpoolService`: spooled files in the system's saved data (200 by default); 6=Print through `elcl.device.Printers` (ELC1301 with no printer) |
| 90 | Sign Off | SIGNOFF | - | Available | - |

Every screen also opens from its command on any command line (`ScreenCommands`): WRKLIB, WRKMBR LIB(), EDTMBR MBR(),
WRKACTJOB, WRKJOB JOB(), DSPJOBLOG JOB(), WRKJOBSCDE, WRKTRGEVT, DSPMSG, WRKSPLF JOB(), WRKSYSVAL, WRKINV, WRKDEV,
DSPNETSTS, WRKCRFJOB; GO MAIN / GO HELP, SIGNOFF.

## ELCL core in place

The precondition packages (ELCL HANDOFF order 1-2) are implemented and unit-tested:

- `elcl` - `ElclMessages` (MESSAGES.md + ELC0107, below), `ElclMessage`, `ElclException`, `Diagnostic`, `SourceLine`
- `elcl.cmd` - `CommandRegistry`, `CommandDefinition`, `ParamDef`, `BuiltinCommands` (every COMMANDS.md schema, plus
  CHGLIB, CRTMBR, CHGJOB, HLDJOBSCDE / RLSJOBSCDE, HLDTRGEVT / RLSTRGEVT, WRKCRFJOB, GO, CLEAR), `Invocation`,
  `CommandExecutor`
- `elcl.lex` / `elcl.parse` / `elcl.compile` - lexer, parser, compiler (checks, cross reference), `Listing`
- `elcl.exec` (game side) - `CommandRunner` (interactive command line), `ElclCommandLine`, `ElclContext`, `OsCommands`,
  `RedstoneCommands`, `ElclDevices`, `ElclEvents`, `ElclSetup`

- `elcl.store` - `ElclStore` (saved data, one `SystemData` per network), `StoredLibraryService`, `StoredMessageService`,
  `StoredSpoolService`, `StoredSysvalService`, `StoredUserService`, `ElclConfig` (the config's `elcl` section)
- `elcl.device` - `PrinterDevice`, `Printers` (see INTERFACES.md)

Not in place yet: `elcl.vm`, `elcl.job`, `elcl.sync`. See IMPLEMENTATION_STATUS.md.

## Stubs waiting on ELCL packages

Each stub class and method is marked `// STUB: waiting on <package>` in the code. They keep their data in memory per
system (network) for as long as the server runs: **nothing here survives a restart.** They're replaced through
`elcl.screen.ElclServices.set*()`.

| Stub | Methods | Waits for |
|---|---|---|
| `StubJobService` | jobs, job, interactive, endInteractive, hold, release, end, change, log, logCommand, logMessage, scheduleEntries, addScheduleEntry, removeScheduleEntry, holdScheduleEntry, triggers, addTrigger, removeTrigger, holdTrigger | `elcl.job`: job hosts, the job queue, tick budgets, persistence; schedule entries and triggers never fire yet |
| `StubJobService.submit` | SBMJOB, WRKJOBSCDE 10=Submit now | `elcl.job` job hosts: always ELC0301 |
| `StubJobService.callStack` | WRKJOB 11 | `elcl.vm` |
| `ScreenQueries.jobs` | WRKACTJOB row 2 | `elcl.job`: hosts busy / total are 0/0, budget the jobs' sum |
| `ElclEvents` | `*RSCHANGE` (fired by the Control Interface) | `elcl.job` triggers listening (nothing listens yet) |

Commands with a schema but no executor answer **ELC0107** "Command &1 is not available yet." (an ID added for this;
not in MESSAGES.md):

| Commands | Wait for |
|---|---|
| CALL, DLYJOB | `elcl.vm` |
| RTVITMCNT, RTVITMLST, MOVITM, IMPITM, CHGITMTIER, RTVSTGSTS, STRCRAFT, RTVCRFSTS, ENDCRAFT, RTVDEVSTS, RTVDEVLST, CHGDEVSTS, CHGDEVFTR, RTVLANES, RTVPWRSTS, SNDDSPTXT, PRTRPT | the mod commands (ELCL HANDOFF order 5) |
| SAVLIB, RSTLIB | `elcl.sync` and the 8" Diskette |

The language statements (PGM, DCL, IF, DO, MONMSG and the rest, 29 in all) are program-only: on the command line
they're ELC0106.

## Tests

- Unit (`gradlew test`): `elcl.CompilerTest`, `elcl.LexerParserTest` (examples compile clean; one test per
  compile-time message; schemas), `client.crt.CrtGridFieldTest`, `client.crt.LayoutTest` (every screen composed and
  compared with `src/test/resources/layouts/*.txt`), `client.crt.EditorTest` (round trip, line commands, editor
  commands, syntax check per message ID, F4 rewrite, SBMJOB prompting).
- Game tests (`gradlew runGameTestServer`): `control_interface`, `elcl_os_commands` (each D.1 completion message, job
  log, screen queries, MW), and the updated `desk_commands`.
- Not automated: rendering in each phosphor at GUI scales 1-4 and small windows (`CrtScreen` drawing is unchanged
  apart from the sheet sizes and glyph map), and per-screen game tests (the screens are client-side; `LayoutTest`
  composes each one instead).

## Deviations from the layouts

- Sign On: the new fields' labels sit at column 0, lined up with the existing User / Password rows (kept as they were).
- Command Entry: history drawn as before (column 2).
- Editor: the column ruler is the real `*...+... 1` form, its numbers over the tens; the layout's is a column off.
  F19 / F20 show columns 1-72 / 9-80 (a shift of 8: "40" in the handoff can't give those windows).
- Display Spooled File: the real compile listing (full source, then cross reference and messages).
