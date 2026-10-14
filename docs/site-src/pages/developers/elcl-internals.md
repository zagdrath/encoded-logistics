---
title: Terminal OS and ELCL internals
nav: ELCL internals
section: developers
order: 30
summary: Contributor notes on the Terminal OS and ELCL code - the package layout, how a command line runs, how to add a command, the services behind the screens, and the tests.
legacy: [developers]
---
Notes for people working on the mod's own Terminal OS and ELCL code. Adding a command from another mod is on
{{page developers/extension-points}}; the language itself is on {{page elcl/language}}. The design documents the code
started from are in the repository's [docs/elcl/](docs/elcl/ELCL_SPEC.md) folder; where they and the code differ, the
code wins, and this site follows the code.

## Package layout {#packages legacy="dev-packages"}

All under `net.zagdrath.encodedlogistics` in `src/main/java`.

| Package | Contents |
|---|---|
| `elcl` | `ElclMessages` (every message ID, severity and text; `monitors()` for `MONMSG` ranges), `ElclMessage`, `ElclException`, `Diagnostic`, `SourceLine` (sequence numbers, dates, 80-column lines) |
| `elcl.cmd` | `CommandRegistry` (every command by name), `CommandDefinition` (name, description, parameters, positional count, context, authority, executor), `ParamDef` (`Kind`, `ValueList`, `VarType`), `BuiltinCommands` (the built-in commands' schemas), `Invocation`, `CommandExecutor`, `Wait` (an async wait a command asks for) |
| `elcl.lex` | `Lexer`, `Token` |
| `elcl.parse` | `Parser` (statements against the registry's schemas), `Stmt`, `Expr` |
| `elcl.compile` | `Compiler` (every check, the cross reference; `checkCommand()` for a single command line; the PLC targets), `CompiledProgram`, `VarDecl`, `Listing`, `FileResolver` and `DeclaredFile` (`DCLF`) |
| `elcl.vm` | `Lowerer` (statements to flat code: every control-flow form, subroutines, monitors as instruction ranges), `VmProgram`, `Vm` (budgeted and resumable; frames, the subroutine stack, loop counters and the async wait, all saved to NBT), `Values` (types, conversions, `*DEC` rounding, overflow, expressions, built-ins), `VmHost` (what the VM needs from the game) |
| `elcl.exec` | The game side. `CommandRunner` (runs one command line: parse, check, context, authority, executor), `ElclCommandLine` (output for Command Entry and the job log), `ElclContext`; the executors by area: `OsCommands`, `ModCommands` (inventory, crafting, devices, power, reports), `RedstoneCommands`, `DisplayCommands`, `SignalCommands`, `PlcCommands`, `MachineCommands`, `DbCommands`, `RoutingCommands`, `ExchangeCommands`, `DirectoryCommands`, `HardwareCommands`; `ElclItems` (item IDs), `ElclDevices` and `NamedDevice` (stored device names), `Authority` (the one Firewall check for terminals and batch jobs), `ElclEvents` (trigger events from the game), `LiveFiles` (ELSYS's live system files), `Reports` (printed reports), `ElclSetup` (binds the executors once at startup) |
| `elcl.job` | `StoredJobService` (interactive and batch jobs, the job queue, hold, release and end, logs, schedule entries, triggers), `JobManager` (runs every job's VM each server tick within the budgets), `JobVmHost`, `InteractiveCalls` (`CALL` on a command line), `BatchContext`, `JobHost` / `JobHosts` (Compute Servers built in), `Waits` (recalls and crafts), `Schedules`, `Triggers`, `RealTime` (the real-time clock for `INTERVAL` and the debounce) |
| `elcl.store` | `ElclStore` (saved data with the overworld, one `SystemData` per network), `SystemData` (libraries, queues, spool, system values, profiles, device names, sync hashes; ELSYS rebuilt on load; storage cost), `JobData` (batch jobs with their VM state, ended jobs' logs, schedule entries, triggers), the stored services (`StoredLibraryService`, `StoredFileService`, `StoredMessageService`, `StoredSpoolService`, `StoredSysvalService`, `StoredUserService`), `ElclConfig` (the config's `elcl` section) |
| `elcl.device` | The interfaces for hardware ELCL talks to, each with a registry of sources (`DeviceSources`): `PrinterDevice` / `Printers`, `DisketteDevice` / `Diskette` / `Diskettes` / `LibraryImage`, `DisplayDevice` / `Displays`. `RecipeLibrarySource` / `RecipeLibraries` are in `crafting`. See [docs/elcl/INTERFACES.md](docs/elcl/INTERFACES.md) |
| `elcl.db` | Database files, free of the game: `FieldDef`, `RecordFormat`, `Dds` (a PF member to a format, and its listing), `DbFile`, `DbRecord`, `Query` (`QRYSLT` and `SORT`), `Csv`, `Report`, `Timestamps`, `SystemFiles` (ELSYS's live files' formats), `FileAccess` (what the VM reads and writes through) |
| `elcl.sync` | `FolderSync` (the world folder, both ways), `Resequence` (sequence numbers kept for unchanged lines) |
| `elcl.screen` | `ElclServices` (the service holder), `ElclSystem` (a network on a server, the game clock and DATFMT), the service interfaces (`LibraryService`, `FileService`, `JobService`, `MessageService`, `SpoolService`, `SysvalService`, `UserService`), `ScreenQueries` and `FileQueries` (the screens' data requests, and the sign-on) |
| `client.crt` | The terminal: `CrtScreen` (drawing and game input), `CrtTerminal` (screen stack, windows, frame, keys), `CrtPanel`, `CrtGrid`, `CrtField`, `CrtWindow`, `FormWindow`, `ValueListWindow`, `ListPanel`, `OsListPanel`, `InfoPanel`, `TextPanel`, `ScreenCommands`, `PrompterPanel`, `EditorPanel` / `EditorModel`, one panel per screen, and `CrtMachineScreen` (the machine and PLC green screens) |
| `terminal` | The desk's server side: the command-line class (every line is ELCL; Tab completion), `TerminalService` (request kinds `COMMAND`, `QUERY`, `COMPLETE`, `SCREEN`), `TerminalContext` (implements `ElclContext`), `TerminalOutput`, `TerminalLine`, `TerminalActions`, `TerminalItems`, `HardwareScreens` and `RoutingScreens` (those screens' lines) |
| `machine`, `compat.arcforge` | The Small Wireless Bridge: `MachineBridges`, `MachineBridge`, `MachineInfo`, `MachineAccess`, `MachineSeries`; `ArcforgeCompat` and the classes it loads (see {{page developers/arcforge-integration}}) |
| elsewhere | `plc` (the PLC and `PlcVmHost`), `display`, `signal`, `midrange`; `menu/TerminalDeskMenu` (starts and ends the session's interactive job, refuses requests until sign-on, sends the unread count); `net/CrtResponsePayload`; `EncodedLogistics` (the server tick runs FolderSync, Schedules, Triggers, the job service and JobManager); the game tests in `gametest` |

Resources: `assets/encodedlogistics/lang/en_us.json` (the `crt.encodedlogistics.*` keys hold every screen's titles,
function-key lines and help), `data/encodedlogistics/elcl/ELSYS/` (the shipped members and `members.txt` listing
them), and `textures/font/terminal*.png`.

### How a command line runs {#flow legacy="dev-flow"}

1. **`CrtTerminal.runCommand`** (client): `clear` is handled here; `ScreenCommands.open` opens a screen for a screen
   command; anything else goes to the server as a `COMMAND` request.
2. **`TerminalDeskMenu`** starts the session's interactive job if it needs one and calls `TerminalService.handle`.
3. **The `terminal` package's command-line class** hands the line to `ElclCommandLine.run`. (The screens' own requests, such as withdrawing
   an item, go to `TerminalService`, not the command line.)
4. **`CommandRunner.run`**: `Parser.parseCommand`; an unknown command is {{msg ELC0101}}; a command that can't run
   on a command line is {{msg ELC0106}}; then `Compiler.checkCommand`; the Firewall permission through `Authority`
   ({{msg ELC0401}}; CONFIGURE means BUILD); no executor is {{msg ELC0107}}; then the executor, with an `Invocation`
   whose values are worked out from the literals typed. `CALL`'s executor (`InteractiveCalls`) starts a VM in the
   interactive job and returns at once ({{msg ELC0108}}); `JobManager` runs it on the following ticks and sends its
   messages to the terminal when it ends.
5. **`ElclCommandLine`** logs the command and its messages in the interactive job and formats the output; the
   response carries the unread message count for the MW indicator.

Inside a program the VM does the same for each command (`Vm.registered`): the context check ({{msg ELC0105}} /
{{msg ELC0106}}), the authority through the run's `VmHost` (a batch job uses its submitter's permissions; a PLC its
loader's), then the executor with an `Invocation` over the frame's variables. An executor that has to wait calls
`call.await(Wait)` when `call.canWait()`; the VM parks the job and runs the command again (with `call.resumed()` set)
once `Waits` says the wait is done.

The OS screens fetch their data with `SCREEN` requests that `ScreenQueries` answers (libraries, members, source,
locks, saving a member in pieces, jobs, job logs, call stacks, schedules, triggers, messages, spooled files, system
values, sign-on, the prompter's value lists, and the database screens through `FileQueries`). These need the
Firewall's VIEW permission, and until a session has signed on (where it must), only `info` and `signon` are answered.

## How to add a command {#add-command legacy="dev-add-command"}

Register the schema once. The built-in ones go in `BuiltinCommands.register()`, which `CommandRegistry` runs when it
loads:

```java
CommandRegistry.register(CommandDefinition.of("RTVFOO", "Retrieve Foo")
        .auth(CommandDefinition.Auth.VIEW)         // Firewall permission (default NONE)
        .positional(1)                             // the first N parameters may omit their keywords
        .p(ParamDef.of("DEV", "Device", ParamDef.Kind.DEVICE).req())
        .p(ParamDef.of("MODE", "Mode", ParamDef.Kind.SPECIAL).sv("*FAST", "*SLOW").dft("*FAST"))
        .p(ParamDef.of("RTNVAL", "Return value", ParamDef.Kind.VARIABLE).returns(ParamDef.VarType.INT))
        .build());
```

The context defaults to `BOTH`; use `.context(CommandDefinition.Context.INTERACTIVE)` (or `BATCH`, `PROGRAM`) as
needed. Other parameter builder calls: `.list(n)`, `.range(min, max)`, `.len(n)` (the prompter's field), `.values(ValueList.X)`
(the F4 list). Registering a name twice throws.

Bind the executor. The built-ins are bound in `ElclSetup.init`, through each area's `bind()` (`OsCommands.bind`,
`RedstoneCommands.bind`, ...):

```java
CommandRegistry.bind("RTVFOO", call -> {
    ElclContext context = call.context(ElclContext.class);   // server, network, user, allowed()
    String dev = call.text("DEV");                           // names and special values upper-cased
    if (notFound) throw new ElclException("ELC1301", dev);   // an escape message
    call.returns("RTNVAL", 42);                              // a RTN* value
    call.send(ElclMessage.of("ELC0222", "FOO"));             // an informational or completion message
});
```

`call.integer()` raises {{msg ELC0003}} and {{msg ELC0004}} itself; `call.given()` tells a given value from a default;
`call.list()` returns every value of a list parameter; `call.interactive()` says whether it runs at a terminal;
`call.canWait()`, `call.await()` and `call.resumed()` make it asynchronous in a program.

That's all for the language side: the parser, the compiler's checks, the F4 prompter, F1 help, the command line and
the VM all read the definition, and the same executor runs on the command line, in interactive programs and in batch
jobs. Then:

- **Messages:** new ones go in `ElclMessages` (and `docs/elcl/MESSAGES.md`). A completion message should be added to
  `ElclCommandLine`'s `COMPLETION` set so it shows bright.
- **Parameters required only together** are checked in the switch in the compiler's statement check.
- **On a PLC without a network** only the commands in `Compiler`'s PLC-local set run; add a command there only if it
  needs nothing from a network.
- **A device scripts talk to:** give it a name (`NamedDevice`, with its type code; `ElclDevices` gives and keeps the
  names), and if it is a printer, diskette drive, display or job host, implement the interface in `elcl.device` or
  `elcl.job` and register a source at setup.
- **A command that opens a screen:** add it to `ScreenCommands`, write a `CrtPanel` (or `OsListPanel`) subclass, add
  the `crt.encodedlogistics.*` lang keys (title, F-keys, `help.<id>` and `help.<id>.<field>`), and a layout under
  `src/test/resources/layouts/` for `LayoutTest`.
- **The documentation:** every command needs a category in `docs/site-src/notes/command-categories.json` (the docs build
  fails otherwise), and may have extra text in `docs/site-src/notes/commands/<NAME>.md`. Its reference page is
  generated from the schema; see {{page developers/building-the-docs}}.

## Services {#services legacy="dev-services"}

`ElclServices` holds one implementation of each service the screens and commands use. All of them keep their data in
the system's saved data (`elcl.store`), and each can be replaced for tests:

```java
ElclServices.setLibraries(LibraryService)   // StoredLibraryService
ElclServices.setFiles(FileService)          // StoredFileService
ElclServices.setJobs(JobService)            // elcl.job.StoredJobService
ElclServices.setMessages(MessageService)    // StoredMessageService
ElclServices.setSpool(SpoolService)         // StoredSpoolService
ElclServices.setSysvals(SysvalService)      // StoredSysvalService
ElclServices.setUsers(UserService)          // StoredUserService
```

| Service | Covers |
|---|---|
| `StoredLibraryService` | Libraries, members, source, save, create, copy, rename, delete, edit locks, compile, programs (kept as source and compiled again on load), storage cost ({{msg ELC0207}}), diskette images and restore; a member listener that `FolderSync` uses |
| `StoredFileService` | Physical files and their records; ELSYS's live files through `LiveFiles` |
| `StoredJobService` | Interactive jobs (in memory, per session) and batch jobs (saved), the job queue and dispatch to hosts, hold, release, end and change, logs and ended jobs' logs, call stacks from the VM, schedule entries, triggers, `*JOBCTL` checks |
| `StoredMessageService` | Message queues (one per user, QSYSOPR for `*SYSOPR`), unread counts, the cap, chat notices |
| `StoredSpoolService` | Spooled files, the cap, printing through `Printers` |
| `StoredSysvalService` | System values with their defaults and allowed values; only changed values are stored; `*SECOFR` to change |
| `StoredUserService` | User profiles, sign-on ({{msg ELC0402}}), `*SECOFR`, whether sign-on and authority checks apply (SECLVL), who may manage whose jobs |

## Tests {#tests legacy="dev-tests"}

Unit tests run with `./gradlew test` (JUnit 5), without a game:

| Test | Covers |
|---|---|
| `elcl.CompilerTest` | Every shipped example in `docs/elcl/examples` compiles with no messages; a negative test per compile-time message ID on the right line; the listing's parts |
| `elcl.LexerParserTest` | Lexing and parsing, the examples, the schemas |
| `elcl.VmTest` | The VM on a fake host: arithmetic and precedence, strings, lists and `FOREACH`, loops, `SELECT` / `IF` / `ELSE` / `GOTO`, subroutines, `CALL` by reference, monitors, an unmonitored escape ending the job, parameter count and call depth, an endless loop kept to its budget, a saved job carrying on, every example starting |
| `elcl.PlcProgramTest` | The PLC's ELCL: `RETAIN` and {{msg ELC1506}}, the PLC-local target ({{msg ELC1502}}, nested too; `*SELF` and the modules allowed), `DLYTICK`, a scan's variables carried into the next, a fault's line, retained values kept as text |
| `elcl.db.DdsTest`, `DbFileTest`, `QueryCsvTest`, `elcl.DbVmTest` | Database files: field types and definition errors, the listing; records, keys and positions; `QRYSLT` and `SORT`, reports, CSV both ways; `DCLF` and every file operation in the VM |
| `elcl.exec.CommandRunnerTest` | A command line's values and escapes |
| `elcl.SystemDataTest` | A system's data round-trips through NBT; members' storage cost |
| `elcl.ResequenceTest` | Folder sync's renumbering |
| `elcl.DocsExamplesTest` | Every ELCL program on this site compiles (see {{page developers/building-the-docs}}) |
| `client.crt.LayoutTest` | Every screen composed with the real lang file and compared with `src/test/resources/layouts/*.txt` |
| `client.crt.EditorTest` | Every example round-trips through the editor byte for byte; line commands, editor commands, the syntax check, F4 |
| `client.crt.MainMenuTest`, `WrkMbrTest`, `WrkMchTest`, `DbScreensTest`, `JobHistoryTest`, `HardwareLayoutTest`, `RoutingLayoutTest`, `SignOffTest`, `CrtGridFieldTest` | Single screens and the grid |

Because three tests (`CompilerTest`, `VmTest`, `EditorTest`) go through every file in `docs/elcl/examples`, and
`CompilerTest` checks how many there are, add new examples there only together with that count.

Game tests run with `./gradlew runGameTestServer` (registered in `gametest/EncodedLogisticsGameTests`). The Terminal
OS ones are in `ElclGameTests` (OS commands, completion messages, screen queries), `ElclVmGameTests` (the mod commands
on Command Entry, `CALL`, the examples running), `ElclStoreGameTests` (persistence, storage, caps, authority),
`DeviceNameGameTests`, `BatchJobGameTests`, `SchedulingGameTests`, `SecurityGameTests`, `DatabaseGameTests`,
`FolderSyncGameTests`, `InterfaceGameTests` (diskettes, printers and recipe libraries against fakes), `DeskGameTests`,
`CraftHistoryGameTests`, and for the devices `ControlInterfaceGameTests`, `PlcGameTests`, `DisplayGameTests`,
`SignalGameTests` and `ArcforgeGameTests` (only with Arcforge in the run).

Not automated: rendering in each phosphor at every GUI scale.
