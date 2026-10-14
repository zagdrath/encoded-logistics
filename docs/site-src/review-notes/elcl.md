# Inventory: Terminal OS and ELCL

Java root: `src/main/java/net/zagdrath/encodedlogistics` (written `…/` below).
Old reference: `docs/terminal-os-elcl.html` (389 KB, 140 anchors). Older notes: `docs/elcl/*.md`, `docs/elcl/HANDOFF.txt`, `docs/elcl/examples/`.

Headline numbers (from code, 2026-10-13): **167 commands**, **182 messages**, **7 system values**, **24 trigger events**, **44 terminal screen ids** (plus 10 machine/PLC screen ids).
The old HTML is out of date: it says "132 commands" (its tables actually hold 144) and "141 message IDs" (its tables hold 155). It has nothing on the 23 commands and 27 messages added for schematics, routing, exchanges, directory and chassis/optical gear.

---

## 1. Loading the data programmatically

### Commands: `net.zagdrath.encodedlogistics.elcl.cmd.CommandRegistry`
- `…/elcl/cmd/CommandRegistry.java`. Static init runs `BuiltinCommands.register()`, so touching the class loads every schema. API:
  - `static Collection<CommandDefinition> all()`: an immutable copy, **in registration order** (LinkedHashMap, so BuiltinCommands source order).
  - `static @Nullable CommandDefinition get(String name)` (case-insensitive)
  - `register(CommandDefinition)`, `bind(String, CommandExecutor)`: game-side only.
- **No running game needed.** The class comment says so ("the schemas stay usable without a game - the compiler's tests use them as they are"). Executors are bound later by `…/elcl/exec/ElclSetup.init()` (game side), so in a test `executor()` is `null`. Everything else is filled in.
- Dependencies on the static-init path: `BuiltinCommands` imports `display.DisplayContent` (it uses Mojang DFU `Codec`, which is on the test classpath through NeoForge moddev) for the colour names, and `signal.NoteInstruments` (NAMES, MAX_NOTES, MAX_MIDI_PARTS). `CompilerTest` and `LexerParserTest` already load the registry in plain JUnit (`gradlew test`), so this works today.
- `BuiltinCommands` is package-private (`final class BuiltinCommands`). Always go through `CommandRegistry`.
- `ParamDef.max()` for list-position params (ADDLSTE/RMVLSTE POS) reads `listLimit`, which defaults to 4096 when nothing is bound at setup (the `maxListSize` config).
- Record shapes:
  - `CommandDefinition(String name, String description, List<ParamDef> params, int positional, Context context, Auth auth, @Nullable CommandExecutor executor)`. `title()` returns `"Start Crafting (STRCRAFT)"`. `Context` is INTERACTIVE, BATCH, BOTH or PROGRAM (helpers `interactive()` and `batch()`). `Auth` is NONE, VIEW, INSERT, EXTRACT, CRAFT or CONFIGURE. CONFIGURE is the Firewall's "build" permission, and ELC0401 calls it BUILD.
  - `ParamDef(String keyword, String label, Kind kind, boolean required, @Nullable String defaultValue, List<String> specials, int maxValues, long min, long max, int length, @Nullable VarType returns, ValueList values)`. Helpers: `isList()`, `hasRange()`, `isReturn()` (RTN* params), `hint()` (the prompter's hint text, e.g. "1-999999", "*FAIL, *PARTIAL", "*CHAR variable").
  - `Kind` is CHAR, INT, DEC, LGL, NAME, QUALIFIED, ITEM, DEVICE, SPECIAL, MSGID, LABEL, VARIABLE, COMMAND, VALUE or TIME. `ValueList` (where F4 gets values) is NONE, SPECIALS, ITEMS, DEVICES, LIBRARIES, MEMBERS, PROGRAMS, JOBS, SYSVALS, COMMAND or FILES. `VarType` is CHAR, INT, DEC, LGL or LIST.
- **Categories are not data.** The only grouping is the `// --- 1. Program control ---` comments in `BuiltinCommands.java`, which match COMMANDS.md sections 1–11; routing and hardware sit under "5. Devices" there. A doc generator needs a hand-kept name→category map (section 2 below has one) or has to parse the source comments.
- **There is no per-command help prose.** F1 help is generated from the schema (`…/client/crt/PrompterPanel.help()`: label, keyword, hint, specials, default, required). The lang file has no keys per command.

### Messages: `net.zagdrath.encodedlogistics.elcl.ElclMessages`
- `…/elcl/ElclMessages.java`: pure Java, no Minecraft imports. `static Map<String, Definition> all()` (LinkedHashMap in define order; note it **returns the live map**, so don't mutate it). `record Definition(String id, int severity, String text)`.
- Each message is defined in the static block as `define("ELC1201", SEVERE, "Item &1 not found in network.")`. Severity constants: `INFO=0, WARNING=10, ERROR=20, SEVERE=30, ABEND=40`. The text uses `&1`…`&9` for its data.
- Helpers: `exists(id)`, `severity(id)` (an unknown ID gets 30, as user messages do), `text(id, data...)`, `monitors(monitorId, id)` (ELCnn00 covers its range, ELC0000/XXX0000 covers everything), `isId(text)`.
- `…/elcl/ElclMessage.java`: `record ElclMessage(String id, int severity, List<String> data)` with `of(id, data...)`, `user(id, sev, text)`, `text()`, `isError()` (severity ≥ 20), and `toString()` giving "ID: text".
- **There is no second-level help** (no cause/recovery text) in code. The only extra wording is the Java comment above some defines ("Not in MESSAGES.md originally: …"). On screen a message shows as `ID  text` (Command Entry, job logs) or `ID: text` (message line). DSPMSG 5=Display shows the full text plus ID, severity, sender and time.
- Every defined ID is referenced somewhere in non-gametest main code (checked by script). None are dead.

### Terminal OS screens: Java panels, not data files
- `src/main/resources/assets/encodedlogistics/screens/*.json` are **not** Terminal OS screens. They are layouts for the mod's ordinary block GUIs (access_terminal.json, controller.json, …, `terminal/base_terminal.json`, `common/palette.json`). The only Terminal-OS-related data file there is `screens/crt/phosphor.json` (the green/amber/white palettes: normal, bright, dim, bg, glow; default "green").
- Each Terminal OS screen is a `CrtPanel` subclass in `…/client/crt/`. Each has `String id()` (row-0 id, e.g. "WRKLIB") and `String title()`, which reads lang key `crt.encodedlogistics.<x>.title` in `src/main/resources/assets/encodedlogistics/lang/en_us.json`. F1 help text is in lang keys `crt.encodedlogistics.help.<screen>` (45 keys: withdraw, craft, dspitm, dspdev, wrkf, dsppfm, runqry, dspfd, upddta, main, signon, wrklib, dsplib, wrkmbr, cmdent, wrkinv, wrkcrfjob, wrkdev, dspnetsts, morekeys, prompt, edtmbr, wrksplf, dspsplf, wrkactjob, wrkjob, dspjob, dspjoblog, wrkjobscde, wrktrgevt, dspmsg, wrksysval, wrkmch, deposit, wrksch, dsproute, dspnbr, wrkacl, wrkix, wrkdir, wrkhw, dspopt, plus title, keys and none).
- Enumerating them:
  - Command → screen: the switch in `…/client/crt/ScreenCommands.panel(...)` (package-private). It is the only map from a typed command to a screen. `handled()` covers GO, SIGNOFF and UPDDTA on ELSYS.
  - Main Menu: `…/client/crt/MainMenu.OPTIONS` (`record Option(int number, String command, Availability, int page, int row)`). All 14 are AVAILABLE. Labels are `crt.encodedlogistics.menu.<n>` and descriptions `….<n>.help`.
  - Sub-screens opened by options (InfoPanel/TextPanel with an id string) are found by grepping `new TextPanel(` / `new InfoPanel(` (see section 5).
  - Screens are client classes (they need `net.minecraft.client.resources.language.Language`), but `LayoutTest` shows they can be built headless: inject a `Language` reading en_us.json, `new CrtTerminal(new Host())`, `terminal.start()`, then compose a panel into a `CrtGrid` and read `grid.chars[row]`. Expected layouts are in `src/test/resources/layouts/01_signon.txt … 29_dspopt.txt` (31 files). These could be used as doc screenshots-as-text.

### Compiling ELCL from tests (so doc examples can be compile-checked)
- `…/elcl/compile/Compiler.java`, static API:
  - `Result compileTexts(List<String> texts)`
  - `Result compileTexts(List<String> texts, FileResolver files)`
  - `Result compileTexts(List<String> texts, FileResolver files, Target target)`
  - `Result compile(List<SourceLine> source)` and `compile(List<SourceLine>, FileResolver)` / `(…, Target)`
  - `List<Diagnostic> checkCommand(Stmt)` (single command line check, used by ScreenCommands)
  - `enum Target { JOB, PLC, PLC_LOCAL }`. PLC_LOCAL allows only the no-network command set (else ELC1502). RETAIN outside PLC gives ELC1506.
  - `record Result(@Nullable CompiledProgram program, List<Diagnostic> diagnostics, Map<String,VarDecl> variables, Map<String,List<Ref>> variableRefs, labelRefs, subroutineRefs, Map<String,RecordFormat> formats)` with `ok()` (program != null) and `maxSeverity()`. `Diagnostic` has `line()` (0-based) and `message()` (an ElclMessage). A warning (sev 10, e.g. ELC0008) still produces a program. Severity 20 or more produces none.
- Source text: `SourceLine.split(String)` → `List<String>`. `SourceLine.number(List<String>, int date)` → `List<SourceLine>` (for listings).
- Files (DCLF): `FileResolver` (`…/elcl/compile/FileResolver.java`). `FileResolver.NONE`, or `FileResolver.of(Map<String, RecordFormat>)` keyed `"LIB/FILE"` (upper case). An unknown file gives ELC2205 unless `lenient()`. System-file formats come from `db.SystemFiles.formats()` (INVITEMS, DEVICES, CRFHIST, JOBS, keyed by bare name, so put them under "ELSYS/"). A PF definition compiles with `db.Dds.compile(List<String>)` → `Dds.Result` (`ok()`, `format()`, `diagnostics()`).
- **CALL targets are not resolved at compile time** (no ELC0203 in Compiler), so examples that CALL other programs compile on their own.
- Existing tests (JUnit 5, working dir = project root):
  - `src/test/java/net/zagdrath/encodedlogistics/elcl/CompilerTest.java`. `static final Path EXAMPLES = Path.of("docs/elcl/examples")`. `static Stream<Path> examples()` lists `*.elclp`. `static List<String> read(Path)` calls `SourceLine.split(Files.readString(path))`. `static Map<String,RecordFormat> shippedFormats()` puts SystemFiles under "ELSYS/" and compiles `ITEMHIST.pf` into "ELGPL/ITEMHIST". `static FileResolver shippedFiles()`. `@ParameterizedTest @MethodSource("examples") void examplesCompileClean(Path)` asserts no diagnostics at all. `examplesExist()` asserts exactly 7 `.elclp` files. Also: one negative test per compile-time ID, `listing()` (with `Listing.build("ZAGLIB","NOCWALL","Day 2 07:13","ELNET01", source, result)`), `monitors()`.
  - `PlcProgramTest` (Target.PLC / PLC_LOCAL via `Compiler.compileTexts(lines, FileResolver.NONE, target)`), `LexerParserTest` (schemas), `VmTest` (runs programs through a fake `VmHost`: `Vm.start(host, program, args)`, `vm.run(budget)`), `DbVmTest`, `db/DdsTest`, `db/DbFileTest`, `db/QueryCsvTest`, `exec/CommandRunnerTest`, `SystemDataTest`, `ResequenceTest`. Client: `client/crt/LayoutTest`, `DbScreensTest`, `EditorTest`, `MainMenuTest`, `WrkMbrTest`, `WrkMchTest`, `HardwareLayoutTest`, `RoutingLayoutTest`, `JobHistoryTest`, `SignOffTest`, `CrtGridFieldTest`.
- Recipe for docs: extract each `<pre>` ELCL block, wrap fragments in `PGM`/`ENDPGM` the way `CompilerTest.program(...)` does, and call `Compiler.compileTexts(lines, CompilerTest.shippedFiles())`. Assert `diagnostics().isEmpty()`, or `ok()` for snippets that show a warning on purpose. PLC snippets use `Target.PLC`.
- `docs/elcl/examples/*` is byte-identical to `src/main/resources/data/encodedlogistics/elcl/ELSYS/*` (checked with diff). ELSYS also has `members.txt`, which lists the member order: ARCHIVE ITEMHIST ITEMSETUP LOGITEMS NOCWALL RESTOCK SORTDEMO UPSALERT. 7 programs and 1 PF.

---

## 2. Counts and commands by category

**Commands: 167** (`CommandRegistry.all().size()`). By context: PROGRAM 37 (language statements, list ops, file I/O), INTERACTIVE 31 (screen/session commands), BOTH 99, BATCH 0. 23 of them are not in the old HTML at all (marked below).
COMMANDS.md still lacks these 7 that the code registers: CHGLIB, CHGJOB, HLDJOBSCDE, RLSJOBSCDE, HLDTRGEVT, RLSTRGEVT, WRKSPLF. CRTMBR, WRKCRFJOB, GO and CLEAR are now in COMMANDS.md.

**Messages: 182** (`ElclMessages.all().size()`), the same ID set and severities as docs/elcl/MESSAGES.md (checked by script).
By range: ELC00xx 16, ELC01xx 10, ELC02xx 21, ELC03xx 15, ELC04xx 2, ELC12xx 8, ELC13xx 22, ELC14xx 4, ELC15xx 9, ELC22xx 36, ELC24xx 12, **ELC25xx 4, ELC26xx 18, ELC27xx 5** (the last three are new: schematics/craft steps, routing/ACL/exchange/directory, chassis/optical). None of them are in the old HTML's message tables.
By severity: 30 → 127, 00 → 45, 10 → 7, 40 → 2 (ELC0303, ELC0310), 20 → 1 (ELC1503).
The old HTML has no `msg-25`, `msg-26` or `msg-27` sections. USRxxxx are user messages (SNDPGMMSG), with no definitions.

Old-HTML category ids are kept. The two new groups match COMMANDS.md 7c/7d.

### Program control (`cmd-program`) - 31

| Command | Description | Context | Firewall auth |
|---|---|---|---|
| PGM | Program | PROGRAM | NONE |
| ENDPGM | End Program | PROGRAM | NONE |
| DCL | Declare Variable | PROGRAM | NONE |
| CHGVAR | Change Variable | PROGRAM | NONE |
| IF | If | PROGRAM | NONE |
| ELSE | Else | PROGRAM | NONE |
| DO | Do Group | PROGRAM | NONE |
| ENDDO | End Do Group | PROGRAM | NONE |
| DOWHILE | Do While | PROGRAM | NONE |
| DOUNTIL | Do Until | PROGRAM | NONE |
| DOFOR | Do For | PROGRAM | NONE |
| FOREACH | For Each Element | PROGRAM | NONE |
| ENDFOR | End For Each | PROGRAM | NONE |
| LEAVE | Leave Loop | PROGRAM | NONE |
| ITERATE | Iterate Loop | PROGRAM | NONE |
| SELECT | Select | PROGRAM | NONE |
| WHEN | When | PROGRAM | NONE |
| OTHERWISE | Otherwise | PROGRAM | NONE |
| ENDSELECT | End Select | PROGRAM | NONE |
| GOTO | Go To | PROGRAM | NONE |
| RETURN | Return | PROGRAM | NONE |
| SUBR | Subroutine | PROGRAM | NONE |
| ENDSUBR | End Subroutine | PROGRAM | NONE |
| CALLSUBR | Call Subroutine | PROGRAM | NONE |
| MONMSG | Monitor Message | PROGRAM | NONE |
| RCVMSG | Receive Message | PROGRAM | NONE |
| SNDPGMMSG | Send Program Message | BOTH | NONE |
| CALL | Call Program | BOTH | NONE |
| DLYJOB | Delay Job | BOTH | NONE |
| DLYTICK | Delay Ticks | BOTH | NONE |
| RTVJOBA | Retrieve Job Attributes | BOTH | NONE |

### Lists [EXT] (`cmd-lists`) - 3

| Command | Description | Context | Firewall auth |
|---|---|---|---|
| ADDLSTE | Add List Element | PROGRAM | NONE |
| RMVLSTE | Remove List Element | PROGRAM | NONE |
| CLRLST | Clear List | PROGRAM | NONE |

### Inventory (`cmd-inventory`) - 6

| Command | Description | Context | Firewall auth |
|---|---|---|---|
| RTVITMCNT | Retrieve Item Count | BOTH | VIEW |
| RTVITMLST | Retrieve Item List | BOTH | VIEW |
| MOVITM | Move Item | BOTH | EXTRACT |
| IMPITM | Import Items | BOTH | INSERT |
| CHGITMTIER | Change Item Tier | BOTH | CONFIGURE |
| RTVSTGSTS | Retrieve Storage Status | BOTH | VIEW |

### Crafting (`cmd-crafting`) - 6

| Command | Description | Context | Firewall auth |
|---|---|---|---|
| STRCRAFT | Start Crafting | BOTH | CRAFT |
| RTVCRFSTS | Retrieve Craft Status | BOTH | VIEW |
| RTVCRFLOG | Retrieve Craft Log | BOTH | VIEW |
| ENDCRAFT | End Crafting | BOTH | CRAFT |
| RTVSCHLST **(new since old HTML)** | Retrieve Schematic List | BOTH | VIEW |
| RTVCRFSTP **(new since old HTML)** | Retrieve Craft Steps | BOTH | VIEW |

### Devices (`cmd-devices`) - 6

| Command | Description | Context | Firewall auth |
|---|---|---|---|
| RTVDEVSTS | Retrieve Device Status | BOTH | VIEW |
| RTVDEVLST | Retrieve Device List | BOTH | VIEW |
| CHGDEVSTS | Change Device Status | BOTH | CONFIGURE |
| CHGDEVFTR | Change Device Filter | BOTH | CONFIGURE |
| RNMDEV | Rename Device | BOTH | CONFIGURE |
| RTVLANES | Retrieve Lanes | BOTH | VIEW |

### Routing and interconnect [EXT] (COMMANDS.md 7c; not in old HTML) (`(new) cmd-routing`) - 10

| Command | Description | Context | Firewall auth |
|---|---|---|---|
| RTVRTE **(new since old HTML)** | Retrieve Route | BOTH | VIEW |
| RTVNBRSTS **(new since old HTML)** | Retrieve Neighbour Status | BOTH | VIEW |
| CHGNBR **(new since old HTML)** | Change Neighbour | BOTH | CONFIGURE |
| ADDACLE **(new since old HTML)** | Add ACL Entry | BOTH | CONFIGURE |
| RMVACLE **(new since old HTML)** | Remove ACL Entry | BOTH | CONFIGURE |
| CHGACLE **(new since old HTML)** | Change ACL Entry | BOTH | CONFIGURE |
| RTVDIRENT **(new since old HTML)** | Retrieve Directory Entry | BOTH | VIEW |
| CHGDIRENT **(new since old HTML)** | Change Directory Entry | BOTH | CONFIGURE |
| APVIXMBR **(new since old HTML)** | Approve Exchange Member | BOTH | CONFIGURE |
| RMVIXMBR **(new since old HTML)** | Remove Exchange Member | BOTH | CONFIGURE |

### Hardware: chassis and optical [EXT] (COMMANDS.md 7d; not in old HTML) (`(new) cmd-hardware`) - 3

| Command | Description | Context | Firewall auth |
|---|---|---|---|
| RTVCHSSTS **(new since old HTML)** | Retrieve Chassis Status | BOTH | VIEW |
| RTVOPTSTS **(new since old HTML)** | Retrieve Optical Status | BOTH | VIEW |
| CHGPROT **(new since old HTML)** | Change Protection | BOTH | CONFIGURE |

### Machines (Arcforge) [EXT] (`cmd-machines`) - 5

| Command | Description | Context | Firewall auth |
|---|---|---|---|
| RTVMCHSTS | Retrieve Machine Status | BOTH | VIEW |
| RTVMCHSTAT | Retrieve Machine Statistics | BOTH | VIEW |
| RTVMCHLST | Retrieve Machine List | BOTH | VIEW |
| CHGMCHSTS | Change Machine Status | BOTH | CONFIGURE |
| CHGMCHCFG | Change Machine Configuration | BOTH | CONFIGURE |

### Power (`cmd-power`) - 1

| Command | Description | Context | Firewall auth |
|---|---|---|---|
| RTVPWRSTS | Retrieve Power Status | BOTH | VIEW |

### Redstone (Control Interface, PLC) (`cmd-redstone`) - 2

| Command | Description | Context | Firewall auth |
|---|---|---|---|
| RTVRSIN | Retrieve Redstone Input | BOTH | VIEW |
| CHGRSOUT | Change Redstone Output | BOTH | CONFIGURE |

### PLCs (`cmd-plc`) - 4

| Command | Description | Context | Firewall auth |
|---|---|---|---|
| RTVSNSVAL | Retrieve Sensor Value | BOTH | VIEW |
| SNDPLCPGM | Send PLC Program | BOTH | CONFIGURE |
| STRPLC | Start PLC | BOTH | CONFIGURE |
| ENDPLC | End PLC | BOTH | CONFIGURE |

### Cage Lights, Alarm Strobes and Speakers (`cmd-signals`) - 7

| Command | Description | Context | Firewall auth |
|---|---|---|---|
| CHGLGT | Change Light | BOTH | CONFIGURE |
| STRSRN | Start Siren | BOTH | CONFIGURE |
| ENDSRN | End Siren | BOTH | CONFIGURE |
| PLYAUD | Play Audio | BOTH | CONFIGURE |
| PLYNOTE | Play Note | BOTH | CONFIGURE |
| PLYMID | Play MIDI | BOTH | CONFIGURE |
| STPAUD | Stop Audio | BOTH | CONFIGURE |

### Messages, displays and output (`cmd-messages`) - 11

| Command | Description | Context | Firewall auth |
|---|---|---|---|
| SNDMSG | Send Message | BOTH | NONE |
| DSPMSG | Display Messages | INTERACTIVE | NONE |
| SNDDSPTXT | Send Display Text | BOTH | NONE |
| CLRDSP | Clear Display | BOTH | NONE |
| CHGDSPRGN | Change Display Region | BOTH | NONE |
| SNDDSPWDG | Send Display Widget | BOTH | NONE |
| SNDDSPGPH | Send Display Graph | BOTH | NONE |
| SNDDSPIMG | Send Display Image | BOTH | NONE |
| RTVDSPSIZ | Retrieve Display Size | BOTH | NONE |
| PRTTXT | Print Text | BOTH | NONE |
| PRTRPT | Print Report | BOTH | NONE |

### Libraries, members and programs (`cmd-os-libraries`) - 12

| Command | Description | Context | Firewall auth |
|---|---|---|---|
| WRKLIB | Work with Libraries | INTERACTIVE | NONE |
| CRTLIB | Create Library | BOTH | NONE |
| CHGLIB | Change Library | BOTH | NONE |
| DLTLIB | Delete Library | BOTH | NONE |
| WRKMBR | Work with Members | INTERACTIVE | NONE |
| EDTMBR | Edit Member | INTERACTIVE | NONE |
| CRTMBR | Create Member | BOTH | NONE |
| CPYMBR | Copy Member | BOTH | NONE |
| RNMMBR | Rename Member | BOTH | NONE |
| DLTMBR | Delete Member | BOTH | NONE |
| CRTELPGM | Create ELCL Program | BOTH | NONE |
| DLTPGM | Delete Program | BOTH | NONE |

### Jobs, schedule entries and triggers (`cmd-os-jobs`) - 18

| Command | Description | Context | Firewall auth |
|---|---|---|---|
| SBMJOB | Submit Job | BOTH | NONE |
| WRKACTJOB | Work with Active Jobs | INTERACTIVE | NONE |
| WRKJOB | Work with Job | INTERACTIVE | NONE |
| DSPJOBLOG | Display Job Log | INTERACTIVE | NONE |
| CHGJOB | Change Job | BOTH | NONE |
| HLDJOB | Hold Job | BOTH | NONE |
| RLSJOB | Release Job | BOTH | NONE |
| ENDJOB | End Job | BOTH | NONE |
| ADDJOBSCDE | Add Job Schedule Entry | BOTH | NONE |
| RMVJOBSCDE | Remove Job Schedule Entry | BOTH | NONE |
| HLDJOBSCDE | Hold Job Schedule Entry | BOTH | NONE |
| RLSJOBSCDE | Release Job Schedule Entry | BOTH | NONE |
| WRKJOBSCDE | Work with Job Schedule Entries | INTERACTIVE | NONE |
| ADDTRGEVT | Add Trigger Event | BOTH | NONE |
| RMVTRGEVT | Remove Trigger Event | BOTH | NONE |
| HLDTRGEVT | Hold Trigger Event | BOTH | NONE |
| RLSTRGEVT | Release Trigger Event | BOTH | NONE |
| WRKTRGEVT | Work with Trigger Events | INTERACTIVE | NONE |

### System values, save and restore (`cmd-os-sysvals`) - 5

| Command | Description | Context | Firewall auth |
|---|---|---|---|
| WRKSYSVAL | Work with System Values | INTERACTIVE | NONE |
| RTVSYSVAL | Retrieve System Value | BOTH | NONE |
| CHGSYSVAL | Change System Value | BOTH | NONE |
| SAVLIB | Save Library | BOTH | NONE |
| RSTLIB | Restore Library | BOTH | NONE |

### Database files (`cmd-db`) - 20

| Command | Description | Context | Firewall auth |
|---|---|---|---|
| DCLF | Declare File | PROGRAM | NONE |
| RCVF | Receive File | PROGRAM | NONE |
| POSDBF | Position Database File | PROGRAM | NONE |
| CLOF | Close File | PROGRAM | NONE |
| CHNRCD | Chain to Record | PROGRAM | NONE |
| WRTRCD | Write Record | PROGRAM | NONE |
| UPDRCD | Update Record | PROGRAM | NONE |
| DLTRCD | Delete Record | PROGRAM | NONE |
| CRTPF | Create Physical File | BOTH | NONE |
| CHGPF | Change Physical File | BOTH | NONE |
| DLTF | Delete File | BOTH | NONE |
| CLRPFM | Clear Physical File Member | BOTH | NONE |
| CPYF | Copy File | BOTH | NONE |
| CPYTOIMPF | Copy To Import File | BOTH | NONE |
| CPYFRMIMPF | Copy From Import File | BOTH | NONE |
| RUNQRY | Run Query | BOTH | NONE |
| WRKF | Work with Files | INTERACTIVE | NONE |
| DSPPFM | Display Physical File Member | INTERACTIVE | NONE |
| DSPFD | Display File Description | INTERACTIVE | NONE |
| UPDDTA | Update Data | INTERACTIVE | NONE |

### Screen and session commands (`cmd-screens`) - 17

| Command | Description | Context | Firewall auth |
|---|---|---|---|
| WRKDEV | Work with Devices | INTERACTIVE | NONE |
| WRKMCH | Work with Machines | INTERACTIVE | NONE |
| WRKINV | Work with Inventory | INTERACTIVE | NONE |
| WRKCRFJOB | Work with Crafting Jobs | INTERACTIVE | NONE |
| DSPNETSTS | Display Network Status | INTERACTIVE | NONE |
| WRKSPLF | Work with Spooled Files | INTERACTIVE | NONE |
| SIGNOFF | Sign Off | INTERACTIVE | NONE |
| GO | Go to Menu | INTERACTIVE | NONE |
| CLEAR | Clear Command Entry | INTERACTIVE | NONE |
| WRKSCH **(new since old HTML)** | Work with Schematics | INTERACTIVE | NONE |
| DSPROUTE **(new since old HTML)** | Display Route Table | INTERACTIVE | NONE |
| DSPNBR **(new since old HTML)** | Display Neighbours | INTERACTIVE | NONE |
| WRKDIR **(new since old HTML)** | Work with Directory | INTERACTIVE | NONE |
| WRKHW **(new since old HTML)** | Work with Hardware | INTERACTIVE | NONE |
| DSPOPT **(new since old HTML)** | Display Optical Status | INTERACTIVE | NONE |
| WRKIX **(new since old HTML)** | Work with Exchange | INTERACTIVE | NONE |
| WRKACL **(new since old HTML)** | Work with ACLs | INTERACTIVE | NONE |

(check: grouped 167 of 167; unassigned []; duplicates 0)

---

## 3. Old HTML structure: all 140 anchors (document order)

`docs/terminal-os-elcl.html` has 140 unique `id` values and 118 distinct `href="#..."` targets. Sections are `<section id>` (numbered 1-13, plus `top` on the header); subsections are h3/h4; `device-names` is a `<p id>` inside cmd-devices. The new page needs no anchors for msg-25/26/27, routing or hardware, because the old page never had them.

| Anchor | Element | Heading |
|---|---|---|
| `top` | header | Terminal OS and ELCL Reference |
| `overview` | section | 1. Overview |
| `what-is` | h3 | The Terminal Desk, the Terminal OS, ELCL and the Control Interface |
| `systems` | h3 | Systems and networks |
| `persistence` | h3 | What is saved |
| `sign-on` | h3 | Signing on |
| `security` | h3 | Users, security and the Firewall |
| `seclvl` | h4 | SECLVL |
| `rack-security` | h4 | Server Racks and Link Cards |
| `using` | section | 2. Using the terminal |
| `screen-layout` | h3 | Screen layout |
| `keys` | h3 | Keys |
| `mouse` | h3 | Mouse |
| `command-line` | h3 | The command line |
| `prompter` | h3 | The prompter (F4) |
| `help-windows` | h3 | Help windows (F1) |
| `phosphor` | h3 | Phosphor colours |
| `screens` | section | 3. Screens |
| `screen-list` | h3 | All screens at a glance |
| `main-menu` | h3 | Main Menu |
| `scr-signon` | h3 | Sign On |
| `scr-cmdent` | h3 | Command Entry |
| `scr-desk` | h3 | Inventory, crafting jobs, devices and network status |
| `crafthistory` | h3 | Crafting job history |
| `scr-wrklib` | h3 | Work with Libraries (WRKLIB) |
| `scr-wrkmbr` | h3 | Work with Members (WRKMBR) |
| `scr-wrkactjob` | h3 | Work with Active Jobs (WRKACTJOB) |
| `scr-wrkjob` | h3 | Work with Job (WRKJOB) |
| `scr-dspjoblog` | h3 | Display Job Log (DSPJOBLOG) |
| `scr-wrkjobscde` | h3 | Work with Job Schedule Entries (WRKJOBSCDE) |
| `scr-wrktrgevt` | h3 | Work with Trigger Events (WRKTRGEVT) |
| `scr-dspmsg` | h3 | Display Messages (DSPMSG) |
| `scr-wrksplf` | h3 | Work with Output (WRKSPLF) |
| `scr-dspsplf` | h3 | Display Spooled File (DSPSPLF) |
| `scr-wrksysval` | h3 | Work with System Values (WRKSYSVAL) |
| `scr-wrkmch` | h3 | Work with Machines (WRKMCH) |
| `scr-morekeys` | h3 | More Keys and Settings (F24) |
| `list-screens` | h3 | How option lists work |
| `libraries` | section | 4. Libraries, members and the editor |
| `libs-members` | h3 | Libraries and members |
| `editor` | h3 | The source editor (EDTMBR) |
| `editor-line-commands` | h3 | Line commands |
| `editor-commands` | h3 | Editor commands (command line) |
| `editor-keys` | h3 | Editor keys |
| `syntax-check` | h3 | Syntax check |
| `compiling` | h3 | Compiling |
| `listing` | h3 | The compile listing |
| `storage-cost` | h3 | Storage cost |
| `folder-sync` | h3 | Folder sync |
| `savlib` | h3 | Saving to diskette (SAVLIB / RSTLIB) |
| `database` | section | 5. Database files |
| `db-objects` | h3 | Files, records and system files |
| `db-define` | h3 | Defining a file |
| `db-create` | h3 | Creating, changing and deleting files |
| `db-programs` | h3 | Files in programs |
| `db-screens` | h3 | The screens |
| `db-query` | h3 | Queries (RUNQRY) |
| `db-copy` | h3 | Copying, export and import |
| `db-limits` | h3 | Storage, limits and authority |
| `db-example` | h3 | Example: logging item counts (ITEMSETUP, LOGITEMS) |
| `language` | section | 6. The ELCL language |
| `lexical` | h3 | Lexical rules |
| `structure` | h3 | Program structure |
| `grammar` | h3 | Grammar |
| `types` | h3 | Variables and types |
| `expressions` | h3 | Expressions and operators |
| `builtins` | h3 | Built-in functions |
| `control-flow` | h3 | Control flow |
| `subroutines` | h3 | Subroutines |
| `errors` | h3 | MONMSG and error handling |
| `calling` | h3 | CALL and parameters |
| `running` | h3 | Running a program |
| `return-params` | h3 | RTN* return parameters |
| `limits` | h3 | Limits and runtime model |
| `examples` | h3 | Annotated examples |
| `ex-restock` | h4 | RESTOCK: keep an item stocked |
| `ex-sortdemo` | h4 | SORTDEMO: a language tour |
| `ex-upsalert` | h4 | UPSALERT: a trigger program |
| `ex-nocwall` | h4 | NOCWALL: a long-running batch job |
| `ex-archive` | h4 | ARCHIVE: move items to tape |
| `commands` | section | 7. Command reference |
| `cmd-overview` | h3 | Reading the tables |
| `cmd-program` | h3 | Program control |
| `cmd-lists` | h3 | Lists [EXT] |
| `cmd-inventory` | h3 | Inventory |
| `cmd-crafting` | h3 | Crafting |
| `cmd-devices` | h3 | Devices |
| `device-names` | p | Each device that takes a name keeps it stored with the device: CTLIF01</co |
| `cmd-machines` | h3 | Machines (Arcforge) [EXT] |
| `cmd-power` | h3 | Power |
| `cmd-redstone` | h3 | Redstone (Control Interface, PLC) |
| `cmd-plc` | h3 | PLCs |
| `cmd-signals` | h3 | Cage Lights, Alarm Strobes and Speakers |
| `cmd-messages` | h3 | Messages, displays and output |
| `cmd-os-libraries` | h3 | Libraries, members and programs |
| `cmd-os-jobs` | h3 | Jobs, schedule entries and triggers |
| `cmd-os-sysvals` | h3 | System values, save and restore |
| `cmd-db` | h3 | Database files |
| `cmd-screens` | h3 | Screen and session commands |
| `cmd-desk` | h3 | The desk's old words (removed) |
| `cmd-extra` | h3 | Commands in the code but not in COMMANDS.md |
| `messages` | section | 8. Message reference |
| `msg-00` | h3 | ELC00xx: language and runtime |
| `msg-01` | h3 | ELC01xx: commands |
| `msg-02` | h3 | ELC02xx: objects |
| `msg-03` | h3 | ELC03xx: jobs |
| `msg-04` | h3 | ELC04xx: security |
| `msg-12` | h3 | ELC12xx: inventory and storage |
| `msg-13` | h3 | ELC13xx: devices |
| `msg-14` | h3 | ELC14xx: crafting |
| `msg-15` | h3 | ELC15xx: PLCs |
| `msg-22` | h3 | ELC22xx: database files |
| `msg-24` | h3 | ELC24xx: lights, sirens and speakers |
| `msg-usr` | h3 | USRxxxx: user messages |
| `jobs` | section | 9. Jobs, scheduling, triggers, message queues, spooled output and system values |
| `job-basics` | h3 | Jobs |
| `job-logs` | h3 | Job logs |
| `schedules` | h3 | Job schedule entries |
| `triggers` | h3 | Trigger events |
| `msgqueues` | h3 | Message queues |
| `spooled` | h3 | Spooled output |
| `printing` | h3 | Printing |
| `midrange` | h3 | The Midrange line |
| `display` | h3 | Display Panels |
| `arcforge` | h3 | Arcforge machines (the Small Wireless Bridge) |
| `sysvals` | h3 | System values |
| `control-interface` | section | 10. The Control Interface and PLCs |
| `plc` | h3 | Programmable Logic Controllers |
| `signals` | h3 | Cage Lights, Alarm Strobes and Speakers |
| `status` | section | 11. Status, limits and server config |
| `hardware` | h3 | Hardware still to come |
| `known-limits` | h3 | Known limits |
| `config` | h3 | Server config (elcl) |
| `developers` | section | 12. For developers |
| `dev-packages` | h3 | Package layout |
| `dev-flow` | h4 | How a command line runs |
| `dev-add-command` | h3 | How to add a command |
| `dev-services` | h3 | Services |
| `dev-tests` | h3 | Tests |
| `differences` | section | 13. Where the spec and the code differ |

---

## 4. Accuracy audit of the old HTML (and docs/elcl/*.md)

Key: **OK** = still true · **STALE** = the code has changed · **PARTLY** = true with corrections.

### Section 13 "Where the spec and the code differ" (`#differences`)
| Claim | Verdict | What the code does |
|---|---|---|
| Added messages ELC0107–0110, 1307–1322, 2201–2247 marked in MESSAGES.md | PARTLY | MESSAGES.md now has all 182, including ELC25xx/26xx/27xx (also marked added). The HTML's list is incomplete. |
| *1M/*10M/*1H/*1D lexed as one special (`*` + digits + letter) | OK | `…/elcl/lex/Lexer.java:104` `digitsThenLetter`. |
| Extra commands CHGLIB, CRTMBR, CHGJOB, HLD/RLS JOBSCDE, HLD/RLS TRGEVT, WRKCRFJOB, GO, CLEAR not in COMMANDS.md; WRKSPLF not in its table | PARTLY | Still missing from COMMANDS.md: CHGLIB, CHGJOB, HLDJOBSCDE, RLSJOBSCDE, HLDTRGEVT, RLSTRGEVT, WRKSPLF. CRTMBR, WRKCRFJOB, GO and CLEAR now appear there. |
| Extra params CRTLIB TYPE, SBMJOB LOG, RTVITMCNT NOTFND, WRKINV FILTER, WRKSPLF JOB | OK | All in `BuiltinCommands`. COMMANDS.md now mentions NOTFND in RTVITMCNT's error column. |
| Defaults (MOVITM PARTIAL(*YES), PRTRPT SPLF(*LAST), SNDMSG TOTRM(*NONE), ADDTRGEVT ITEM/DEV(*ANY) VALUE(*NONE), …) | OK | PRTRPT SPLF *LAST, TOTRM *NONE, ADDTRGEVT ITEM/DEV *ANY VALUE *NONE all confirmed in BuiltinCommands. ADDTRGEVT also has `TYPE(*ITEM|*FLUID|*PRES)`, dft *ITEM, which the HTML doesn't list. |
| CHGVAR, MONMSG, RCVMSG, control flow are program-only (ELC0106); SNDPGMMSG runs on the command line | OK | context PROGRAM for PGM…RCVMSG, ADDLSTE/RMVLSTE/CLRLST and the 8 file ops. SNDPGMMSG, CALL, DLYJOB, DLYTICK, RTVJOBA are BOTH. |
| ENDSUBR RTNVAL is an INT value (dft 0); CALLSUBR RTNVAL receives it | OK | `BuiltinCommands:132`. |
| Operators *NG, *NL, ¬= accepted | OK | `…/elcl/parse/Parser.java`. It also accepts `<>`, `>=`, `<=`, `|<`, `|>`, `||`, `|`. |
| Trailing `-` is continuation like `+` outside strings | OK | `Lexer.java:90,205`. |
| Sysvals' allowed values; "SYSNAME isn't shown in the screen header (the network name is)" | **STALE** (header part) | Allowed values match (`…/elcl/store/StoredSysvalService.valid`). But the header now shows **SYSNAME**: `…/terminal/TerminalService.info()` sends `system.name()` (the SYSNAME sysval) as line 0, and `…/client/crt/CrtTerminal.java:340,394` draws it as "System: …". CHGSYSVAL re-queries info. The header clock still always shows "Day n" (`FunctionKeys.clock`), so DATFMT doesn't affect it. That part is OK. |
| *SECOFR = the Firewall's owner and server operators; with no Firewall, everyone | OK | `…/elcl/store/StoredUserService.java:24,82,101`. |
| SNDMSG TOUSR(*SYSOPR) → QSYSOPR, "which no screen shows" | **STALE** | `DSPMSG USR(*SYSOPR)` (or any user's name) now opens that queue for a *SECOFR, or anyone with full authority (`…/elcl/screen/ScreenQueries.queue()`; `…/client/crt/DspMsgPanel`). ELC0401 (*USE) otherwise. *ALL behaviour unchanged (`StoredMessageService:53`). |
| Unknown trigger → ELC0103 (TRG); unknown schedule entry → ELC0302 | OK | `…/elcl/job/StoredJobService.java:571,583,618,630`. |
| Silent commands: CHGJOB, HLD/RLS JOBSCDE, HLD/RLS TRGEVT have no completion message; SNDMSG, PRTTXT, CHGDEVSTS, CHGDEVFTR, CHGMCHSTS send none | OK | `…/elcl/exec/OsCommands.java:225-237` (no `call.send`), and ModCommands/MachineCommands. |
| Device names: no Label Maker | OK | No Label Maker anywhere in code. Names come from ElclDevices; RNMDEV / WRKDEV 2=Change. |
| CHGITMTIER through the Tape Libraries' lists | OK (not re-verified in depth) | Matches IMPLEMENTATION_STATUS §6. |
| Trigger &EVENT *CHAR 10 cuts *DEVOFFLINE/*PWRRESTORED | PARTLY | The code passes the full name (`Triggers.fire` → `CALL … PARM('*EVENT' 'data')`). It only gets cut when the program declares LEN(10), as UPSALERT does. It now affects **more** events: *SUPFAILOVER (12 chars) arrives as *SUPFAILOV and *PROTSWITCH (11) as *PROTSWITC. The docs should say "declare &EVENT LEN(12)". |
| DLYJOB seconds are game seconds; on a command line DLYJOB completes at once | OK | `OsCommands:173` (no-op binding); `Vm.java:687`. DLY range 1–86400, RSMTIME HHMMSS. |
| Editor F19/F20 shift 8 columns; DSPSPLF shifts 40 | OK | `EditorPanel.SHIFT = 8`; `DspSplfPanel.SHIFT = 40`, and `DspPfmPanel.SHIFT = 40` too. |
| Sign On only with a Firewall and SECLVL 30 | OK | `CrtTerminal.java:349-350`. |

### Section 11 "Status": intro, "Hardware still to come" (`#hardware`), "Known limits" (`#known-limits`)
| Claim | Verdict | Code |
|---|---|---|
| Intro: "all 99 commands (and RTVSNSVAL, DLYTICK, SNDPLCPGM, STRPLC, ENDPLC)" | STALE | 167 commands. |
| Mainframe not in the mod (32 jobs per CPC Drawer, resumes) | OK (not implemented) | It appears only in comments (`…/elcl/job/JobHost.java`) and gametest fakes. Job hosts registered: Compute Servers (`…/elcl/job/JobHosts.computeServers`, `computeServerJobs` = 4, `resumes()=false`) and Midrange Systems (`…/midrange/Midranges.register` → `MidrangeSystemBlockEntity.batchHost()`, capacity `batchJobs()` = 4 integrated / 2 expanded / 1, `resumes()=false`). **No host resumes**, so "a job on a resuming host pauses…" (HTML §9) describes code paths no real host uses. |
| Status Display, NOC Video Wall, Rack Console screen as DisplayDevice | OK (not implemented) | The only DisplayDevice source is Display Panels (master blocks), registered in `…/elcl/exec/ElclSetup.java:48`. |
| Label Maker | OK (not implemented) | |
| Reading files: every library *USE, no *EXCLUDE | OK | `StoredLibraryService:176` creates libraries with "*USE"; CHGLIB AUT takes only *SAME/*USE/*CHANGE. |
| Update Data, 8,000-character record limit | OK | `…/net/CrtRequestPayload.MAX_TEXT = 8_000`; `UpdDtaPanel:354`. |
| Source storage counted, not reserved | OK | Matches IMPLEMENTATION_STATUS §6. |
| %NAME on dedicated servers may be a translation key | OK (by design) | |
| Long event names cut | PARTLY | See above. More events are affected now. |
| Interactive jobs aren't saved | OK | |
| Device status after restart isn't saved | OK | `…/elcl/job/Triggers.java` `DEVICES` WeakHashMap ("not saved: the first look after a restart only records them"). |
| "*SYSOPR messages go to a QSYSOPR queue no screen shows" | **STALE** | DSPMSG USR(*SYSOPR) shows it (see above). |
| "SNDMSG TOTRM(), DSPMSG USR() and WRKINV FILTER() are accepted but not used" | PARTLY | TOTRM: still unused (only in the schema). WRKINV FILTER: still unused (`ScreenCommands` → `new InventoryPanel(screen)` ignores it). **DSPMSG USR(): now used.** |
| "SYSNAME doesn't change the screen header" | **STALE** | It does. |
| "DATFMT doesn't change the header's clock" | OK | |
| CHGJOB / HLD / RLS JOBSCDE / TRGEVT succeed silently | OK | |
| *MSGW defined but never used | OK | Only appears in comments and status lists (`JobService`, `WrkActJobPanel`). |
| Server config (elcl) table: 14 options and defaults | OK | Exactly `…/Config.java:806-863`. The plc table also matches `Config.java:873-899`. controlInterfaceDrain 1.0 and firewallFailClosed true, both OK. |

### "Commands in the code but not in COMMANDS.md" (`#cmd-extra`)
PARTLY. Still true for CHGLIB, CHGJOB, HLDJOBSCDE, RLSJOBSCDE, HLDTRGEVT, RLSTRGEVT, WRKSPLF. CRTMBR, WRKCRFJOB, GO and CLEAR are now in COMMANDS.md. The "parameters not shown" list is still basically right (WRKSPLF JOB, WRKINV FILTER, CRTLIB TYPE, SBMJOB LOG). This section is about doc bookkeeping, not features, so the new docs can drop it.

### "The desk's old words (removed)" (`#cmd-desk`)
OK. The only literal word left is `clear` (any case), caught client-side in `…/client/crt/CrtTerminal.runCommand:308`, which clears history ("Command history cleared."). The `withdraw` in `TerminalService:118` is a screen QUERY topic for WithdrawPanel, not a command word. `help`, `show …`, `withdraw`, `craft` and `cancel job` typed on a command line give ELC0101. The new docs could reduce this to one migration note.

### Spot checks elsewhere
- **Screen list** (`#screen-list`) is STALE and incomplete. It is missing WRKSCH (Work with Schematics), DSPROUTE, DSPNBR, WRKACL, WRKIX, WRKDIR, WRKHW, DSPOPT, and the sub-screens DSPSCH, DSPHW, DSPIXMBR, DSPJOBSTP (Display Job Steps, WRKCRFJOB 8). It also lacks the PLC screens PLCSTS/PLCIO/PLCMOD/PLCRTN (described in §10 but not in the table). The rest of the table (ids, titles) matches the lang keys and panel `id()`s.
- **Work with Jobs (crafting)**: the HTML lists only 4=Cancel and 5=Display for active jobs. The code also has **8=Display steps** (DSPJOBSTP; `…/client/crt/JobsPanel.java:291`). The history options 4/5/7 are OK.
- **DSPMSG** (`#scr-dspmsg`): "always shows your own queue: DSPMSG USR() is accepted but not used" is **STALE**.
- **Systems** (`#systems`): "Changing it does not change the screen header" is **STALE**.
- **System values table** (`#sysvals`): defaults and allowed values are OK. The "screen header always shows the network's name" note is STALE.
- **Main Menu**: 14 options, all AVAILABLE, so the "Option 6 not available." example is only theoretical. OK.
- **Keys** (`#keys`): F19/F20, F13 clear, F24 and the others match `EditorPanel`/`DspSplfPanel`. Not exhaustively re-checked per screen.
- **Limits** (`#limits`): call depth via `VmHost.maxCallDepth()` (ELC0011), *CHAR length checked ≤1024 (`Compiler.java:565`), DLYTICK 1–1200 ticks, RTVITMLST MAX 1–4096. OK.
- **Built-in functions**: code, spec and HTML all have the same 17: %ABS %CHAR %DEC %ELEM %INT %LEN %LOWER %MAX %MIN %NAME %SCAN %SIZE %SST %TRIM %TRIML %TRIMR %UPPER. OK.
- **Job types / hosts** (`#job-basics`): the host table is OK for Compute Server (4), Midrange (1 / 2 with Expansion Cabinet) and Integrated (4). "Mainframe: Not yet" is OK (not implemented).
- **Triggers** (`#triggers`) are STALE/incomplete. The code has **24 events** (`BuiltinCommands:584-586`, `…/elcl/job/Triggers.java:39-62,225-232`): *ITMBELOW *ITMABOVE *STGFULL *DEVFAULT *DEVONLINE *DEVOFFLINE *PWRUPS *PWRRESTORED *CRAFTEND **\*CRFSTALL** *RSCHANGE *DSPTOUCH *MCHIDLE *MCHDONE *MCHFAULT *MCHNOPWR *NBRUP *NBRDOWN *RTECHG *ACLDENY **\*CARDFAULT \*SUPFAILOVER \*PROTSWITCH \*SPANLOSS**. The HTML lacks the bold five. &DATA for the new ones: *CRFSTALL `C0042 <step item> <reason>`; *CARDFAULT `CSW01 P2 power supply removed`; *SUPFAILOVER `CSW01 A failover to B`; *PROTSWITCH `OTS01 OTS01-OTS02 *PROTECT`; *SPANLOSS `MUX01 MUX01-MUX02 2`. Debounce is 1 s (`DEBOUNCE_MS`) and the poll is every 10 ticks. OK.
- **Spooled files** (`#spooled`): the source table is incomplete. Creators in code: program compile listings (`StoredLibraryService:428`), **PF compile listings** (`StoredFileService:231`), printed members (`ScreenQueries:248`), PRTTXT (`OsCommands:271`), and **RUNQRY OUTPUT(*PRINT)** as file `QPQUPRFIL` (`DbCommands:42,272`). 60 lines a page (`SpoolService.LINES_PER_PAGE`). Statuses *RDY/*PRT/*HLD. The WRKSPLF help text says "job logs", but nothing spools a job log (PRTRPT *JOBLOG prints straight to paper).
- **Printing** (`#printing`): PRTRPT RPT(*INV|*DEV|*JOBLOG|*SPLF), SPLF(*LAST), DEV(*DFT). The printer is the Line Printer (`…/midrange/LinePrinterBlockEntity implements PrinterDevice`). OK. IMPLEMENTATION_STATUS.md row 13 "6=Print Blocked: printer" is STALE.
- **PLC** (`#plc`): config defaults OK. Screen ids PLCSTS/PLCIO/PLCMOD/PLCRTN (`…/client/crt/PlcScreen.java:45-48`). PLC_LOCAL command set in `Compiler.PLC_LOCAL_COMMANDS`. OK.
- **Display Panels** (`#display`): config names and defaults OK (`Config.java:659-693`). SNDDSPWDG WDG list (*STORAGE *COLD *ENERGY *LANES *JOBS *ITEM *CLOCK *DEVICES *UPS *TEXT *TABLE *NONE) and SNDDSPGPH STAT list (*ITEMFLOW *ENERGY *LANES *STORAGE *CRAFTING *ITEM *MCHOPS *MCHFE) match. **Modes:** code has two, `DisplayContent.Mode { TEXT, DASHBOARD }`, with "SCRIPT" loaded as DASHBOARD. The HTML is self-contradictory: it explains Script-controlled is gone, yet the configuration-screen paragraph says "Mode: the three modes". Worth knowing: every Display Panel command (SNDDSPTXT, CLRDSP, CHGDSPRGN, SNDDSPWDG, SNDDSPGPH, SNDDSPIMG, RTVDSPSIZ) is registered with **Auth NONE**, and so are PRTTXT/PRTRPT and the OS commands. None of the old docs say this.
- **Firewall auth per command**: VIEW for RTV*, CONFIGURE for CHG*/device control, EXTRACT MOVITM, INSERT IMPITM, CRAFT STRCRAFT/ENDCRAFT. All OS, display and print commands are NONE (the OS checks its own library, job and sysval authority).
- **Midrange numbers** (`#midrange`): step ticks 40 / 25 (`Config.java:611,615`) OK. batchJobs 1/2/4 OK.

---

## 5. Terminal OS screens in code (id → title → how it opens)

Terminal OS sessions run on the **Terminal Desk** (`TerminalDeskBlockEntity` → `TerminalDeskMenu.open`) and on the **Integrated Midrange System's console** (`IntegratedMidrangeBlock:148`, the same menu). The PLC editor reuses `EditorPanel` in `PlcEditorScreen`. The Midrange control panel, Disk Drive and Tape Drive screens have command lines that run commands too.

### Opened by a command (`…/client/crt/ScreenCommands.panel`)
| Id | Title (en_us) | Command | Main Menu | Class |
|---|---|---|---|---|
| WRKINV | Work with Inventory | `WRKINV [FILTER()]` (FILTER ignored) | 1 | InventoryPanel |
| WRKCRFJOB | Work with Jobs / Crafting Job History (F10) | `WRKCRFJOB` | 2 | JobsPanel |
| WRKDEV | Work with Devices | `WRKDEV` | 3 | DevicesPanel |
| DSPNETSTS | Display Network Status | `DSPNETSTS` | 4 | StatusPanel |
| WRKLIB | Work with Libraries | `WRKLIB` | 5 | WrkLibPanel |
| WRKACTJOB | Work with Active Jobs | `WRKACTJOB` | 6 | WrkActJobPanel |
| DSPMSG | Display Messages | `DSPMSG [USR(*CURRENT|*SYSOPR|name)]` | 7 | DspMsgPanel |
| WRKSPLF | Work with Output | `WRKSPLF [JOB()]` | 8 | WrkSplfPanel |
| WRKMCH | Work with Machines | `WRKMCH` | 9 | WrkMchPanel |
| WRKF | Work with Files | `WRKF [LIB(*CURLIB)]` | 10 | WrkFPanel |
| WRKJOBSCDE | Work with Job Schedule Entries | `WRKJOBSCDE` | 11 | WrkJobScdePanel |
| WRKTRGEVT | Work with Trigger Events | `WRKTRGEVT` | 12 | WrkTrgEvtPanel |
| WRKSYSVAL | Work with System Values | `WRKSYSVAL` | 13 | WrkSysvalPanel |
| (Sign Off) | – | `SIGNOFF` | 90 | CrtTerminal.signOff |
| WRKMBR | Work with Members | `WRKMBR [LIB(*CURLIB)]`; WRKLIB 12 | – | WrkMbrPanel |
| EDTMBR | Edit Member / Display Member (ELSYS) / Edit Member - Full screen (F11) | `EDTMBR MBR(lib/name)`; WRKMBR 2, 5 | – | EditorPanel |
| WRKJOB | Work with Job | `WRKJOB [JOB(*)]`; WRKACTJOB 5 | – | WrkJobPanel |
| DSPJOBLOG | Display Job Log | `DSPJOBLOG [JOB(*)]`; WRKJOB 10 | – | DspJobLogPanel |
| WRKSCH | Work with Schematics | `WRKSCH [HOLDER(*ALL)]` | – | SchematicsPanel |
| DSPROUTE | Display Route Table | `DSPROUTE RTR()` | – | RoutingPanel |
| DSPNBR | Display Neighbours | `DSPNBR RTR()` | – | RoutingPanel |
| WRKDIR | Work with Directory | `WRKDIR [DIR(*FIRST)]` | – | WrkDirPanel |
| WRKHW | Work with Hardware | `WRKHW [DEV(*ALL)]` | – | WrkHwPanel |
| DSPOPT | Display Optical Status | `DSPOPT` | – | DspOptPanel |
| WRKIX | Work with Exchange | `WRKIX IX()` | – | WrkIxPanel |
| WRKACL | Work with ACLs | `WRKACL RTR() ACL()` | – | WrkAclPanel |
| DSPPFM | Display Physical File Member | `DSPPFM FILE()`; WRKF 5 | – | DspPfmPanel |
| RUNQRY | Display Report | `RUNQRY FILE() OUTPUT(*DISPLAY)` | – | DspPfmPanel (query mode) |
| DSPFD | Display File Description | `DSPFD FILE()`; WRKF 8 | – | DspFdPanel |
| UPDDTA | Update Data | `UPDDTA FILE()`; WRKF 2 (ELSYS files: ELC0205) | – | UpdDtaPanel |
| MAIN | ENCODED LOGISTICS MAIN MENU | `GO [MENU(MAIN)]`; opening the desk | – | MainMenuPanel |
| (help window) | "<title> - Help" | `GO HELP`, F1 | – | CrtTerminal.help |

### Opened by keys or options only
| Id | Title | Opens from |
|---|---|---|
| SIGNON | Sign On | Opening on a network with a Firewall at SECLVL 30; SIGNOFF |
| CMDENT | Command Entry | F9; opens by itself when a command has output |
| PROMPT | `<description> (<CMD>)` | F4 |
| MOREKEYS | More Keys and Settings | F24 |
| (window) | Select Value - %s | F4 on a prompter field (ValueListWindow) |
| DEPOSIT | Deposit Items | WRKINV F6 |
| WITHDRAW | Withdraw Item (WITHDRAW) | WRKINV 1 |
| CRAFT | Craft Item (CRAFT) | WRKINV 7; WRKCRFJOB history 7=Craft again |
| DSPITM | Display Item Details | WRKINV 5 |
| DSPDEV | Display Device | WRKDEV 5 (also 2=Change → RNMDEV, 8=Locate) |
| DSPJOB | Display Job / Display Ended Job | WRKCRFJOB 5 (active / history) |
| DSPJOBSTP | Display Job Steps | WRKCRFJOB 8 |
| DSPJOB | Display job status / definition attributes / call stack | WRKJOB 1, 2, 11 (30 = all) |
| DSPLIB | Display Library | WRKLIB 5 |
| DSPSPLF | Display Spooled File | WRKSPLF 5 |
| DSPMCH | Display Machine %s | WRKMCH 5 (2=Change, 7=Enable/Disable, 8=Sides) |
| DSPSCH | Display Schematic | WRKSCH 5 (also 4, 8) |
| DSPHW | Display Hardware | WRKHW 5 (also 8) |
| DSPIXMBR | Exchange member %s | WRKIX 5 (8=Approve) |
| (window) | Display System Value %s | WRKSYSVAL 5 |

### Machine and PLC green screens (`CrtMachineScreen` subclasses, opened by right-clicking the block)
MRCTL Midrange Control Panel / IMCTL Integrated System Control Panel (`MidrangePanelScreen`), CARDPUNCH Card Punch - Punch a Card (`KeypunchScreen`), CARDRDR Card Reader, PRINTER Line Printer, DSKDRV Disk Drive, TAPDRV Tape Drive, PLCSTS PLC Status / PLCIO PLC I/O Table / PLCMOD PLC Modules / PLCRTN PLC Retained Variables (`PlcScreen`), plus the PLC program editor (`PlcEditorScreen` → EditorPanel).

Option codes per list screen, from the `case` labels; confirm-type 4=… options are handled by the shared confirmation path. WRKLIB 2,4,5,12 · WRKMBR 2,3,4,5,6,7,9,14,16 · WRKACTJOB 2,3,4,5,6,8 · WRKJOBSCDE 2,3,4,10 · WRKTRGEVT 2,3,4 · DSPMSG 4,5 · WRKSPLF 4,5,6 · WRKSYSVAL 2,5 · WRKF 2,4,5,8 · WRKINV 1,5,7 · WRKDEV 2,5,8 · WRKMCH 2,5,7,8 · WRKCRFJOB 4,5,8 (history 4,5,7) · WRKSCH 4,5,8 · WRKHW 5,8 · WRKDIR 2,3,5 · WRKACL 2,4,6,7 · WRKIX 4,5,8.

---

## 6. System values (WRKSYSVAL)

From `…/elcl/store/StoredSysvalService.java` (`DEFINITIONS`, `defaults()`, `valid()`). Changing one takes *SECOFR (ELC0401 naming *SECOFR); a bad value gives ELC0103; success gives ELC0222. Values are upper-cased, and only changed ones are saved.

| Name | Description (code) | Default | Allowed (as validated) |
|---|---|---|---|
| SYSNAME | System name | `ELNETnn` (`TerminalService.networkName` → `"ELNET%02d"` of the network id) | `[A-Z][A-Z0-9]{0,7}` |
| DATFMT | Date display format | `*DAY` | *DAY *MDY *DMY *YMD (30-day months; `ElclSystem` date code) |
| SECLVL | Security level (10 / 30) | `30` | 10, 30 |
| QMAXJOB | Maximum batch jobs | `16` | 1–999 |
| LOGRTN | Job logs retained | `50` | 0–999 |
| CRFLOGRTN | Crafting jobs retained | `200` (= config `elcl.craftLogRetention`) | 0–999; lowering it trims the history at once (`CraftLog.trim`) |
| PHOSPHOR | Default screen colour | `*GREEN` | *GREEN *AMBER *WHITE |

Related server config (`Config.java` `elcl` section): spooledFileCap 200, messageCap 500, charsPerStorageByte 64, messageChatNotice true, interactiveBudget 200, batchBudget 100, globalBudget 2000, maxSourceLines 5000, maxListSize 4096, computeServerJobs 4, disketteBytes 65536, craftLogRetention 200, maxRecordsPerFile 10000, allowFolderSync AUTO. Read in tests through `…/elcl/store/ElclConfig` (falls back to defaults when the config isn't loaded).

---

## Not implemented (from older docs) — leave these out

1. **Mainframe** as a job host: 32 jobs per CPC Drawer, journaled jobs that **resume** after restart or power loss (OS.md §5, INTERFACES.md, HTML #hardware/#job-basics). There is no Mainframe and no `JobHost` with `resumes()==true`. The VM state does save with batch jobs, but every real host ends jobs with ELC0310. Drop the "pauses while the host is down and carries on" wording.
2. **Status Display, NOC Video Wall, Rack Console screen** as SNDDSPTXT targets (DisplayDevice comment, INTERFACES.md, IMPLEMENTATION_STATUS §5). Only Display Panels exist.
3. **Label Maker** for naming devices (COMMANDS.md, IMPLEMENTATION_STATUS §5).
4. **\*MSGW** job status / messages needing a reply (OS.md §5: "waiting for the user to answer a message"). No inquiry messages exist.
5. **SNDMSG TOTRM()**: accepted, does nothing.
6. **WRKINV FILTER()**: accepted, ignored by the screen.
7. **DATFMT in the screen header clock**: not applied (header is always "Day n  hh:mm:ss").
8. **Completion messages for every OS command** (AMENDMENTS.md #2): CHGJOB, HLDJOBSCDE, RLSJOBSCDE, HLDTRGEVT, RLSTRGEVT (and SNDMSG, PRTTXT, CHGDEVSTS, CHGDEVFTR, CHGMCHSTS) send none.
9. **Per-item storage tier**: CHGITMTIER works through Tape Library lists, not a real per-item tier.
10. **Display Panel "Script-controlled" third mode**: gone; loads as DASHBOARD.
11. **Spooled job logs**: WRKSPLF help/HTML mention "job logs" among spooled output, but no code spools a job log.
12. **Message second-level help** (cause/recovery): never existed. Don't invent it.
13. **SECOFR "network owner" per OS.md**: it is really the Firewall's owner plus server operators.
14. Batch context: no command is BATCH-only (Context.BATCH unused). ELC0105 still fires for INTERACTIVE-only commands in batch.

Stale statements to fix rather than drop: SYSNAME not in the header; QSYSOPR not viewable / DSPMSG USR() unused; "132 commands", "141 messages", "all 99 commands"; IMPLEMENTATION_STATUS "6=Print Blocked: printer"; the trigger list missing 5 events; the screen list missing the routing, hardware and schematics screens; HTML message tables missing ELC25xx–27xx.

---

## Open questions

1. **Command categories for docs**: there is no category field. Should the new docs keep the old HTML groups plus "Routing and interconnect" and "Hardware: chassis and optical" (matching COMMANDS.md 7c/7d), or follow the code comments, which put those under "5. Devices"? A generator needs a checked-in name→category map, plus a test asserting it covers `CommandRegistry.all()`.
2. **Per-command prose**: descriptions and parameter labels are the only text in code. Long-form docs (what a command does, errors, examples) have to come from COMMANDS.md/HTML. Should COMMANDS.md become the source of truth, with a test that cross-checks keywords against the registry? The script check found no doc keyword that's missing from code (apart from nested-command false positives).
3. **Trigger &EVENT length**: should the docs (and the UPSALERT sample) move to LEN(12) now that *SUPFAILOVER and *PROTSWITCH exist?
4. **DSPMSG USR() authority**: the check is `UserService.mayManage` (owner, no Firewall, SECLVL 10, or *SECOFR) and the error names *USE. Confirm this wording before documenting.
5. **Display commands have Auth NONE**: is that intended (any player's script can drive any panel), or should they need CONFIGURE? It affects what the docs promise.
6. **Screen help text** lives in lang keys (`crt.encodedlogistics.help.*`). Should the docs quote them verbatim, generated from en_us.json, so they can't drift?
7. **`ElclMessages.all()` returns the mutable backing map.** Harmless for a reader, but a doc-generator test must not modify it.
8. The old HTML's `#dev-tests` table and `#dev-*` sections weren't audited in detail. They are developer-facing and may move elsewhere.
9. The HTML's other ~100 sections (language reference, database, editor, Midrange, Arcforge) were only spot-checked (functions, operators, limits, config, options). A line-by-line pass would be needed to guarantee every sentence.
