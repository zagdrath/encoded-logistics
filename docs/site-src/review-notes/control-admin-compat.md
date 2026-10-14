# Inventory: Control and facility, Config, Admin, Compat, Developer API, Tests

Source root: `src/main/java/net/zagdrath/encodedlogistics/`. Paths below are relative to it unless they start with `src/`, `docs/` or `build.gradle`. This is a read-only survey at HEAD 24c1f57 (mod_version 1.2.1, MC 26.3, NeoForge 26.3.0.40-beta).

## Control and facility features

### Control Interface (`block/ControlInterfaceBlock`, `blockentity/ControlInterfaceBlockEntity`, `blockentity/RedstoneChannels`)
- **What it does:** redstone in and out for ELCL scripts. Each of its six faces is its own channel.
  - `RTVRSIN` reads the level arriving on a face. `SIDE(*MAX)` gives the highest of the six.
  - `CHGRSOUT` sets what a face outputs, 0-15. A null face or `*ALL` means every face.
- **No GUI.** It is configured from the terminal.
- **As a network device:** 1 lane, draws `controlInterfaceDrain` FE/t (default 1.0). It is named `CTLIF01`, `CTLIF02`, ... with the lowest free number, given the first time it is on a network. The name stays on the item.
- **Output:** weak power only (never through the next block), and only while ONLINE. Output levels are kept while it is offline and given out again when it is back.
- **Reading:** it never reads its own output. Dust it powers only counts if something else drives it higher.
- **Events and LEDs:** a change of input fires the ELCL trigger event `*RSCHANGE` (side, new level). Each face's LED shows max(in, out): off, dim (1-7) or bright (8-15).
- **Recipe:** shaped ` C ` / `RLR` / `ISI`: comparator, redstone x2, logic die, iron ingot x2, circuit substrate.
- **Game test:** `gametest/ControlInterfaceGameTests`. It covers names, RTVRSIN and *MAX, CHGRSOUT, the LEDs, *RSCHANGE firing once per change, ELC1301/1302/1303/0004, and offline behaviour.

### PLC (`plc/`, `menu/PlcMenu`, `elcl/exec/PlcCommands`)
- **Block** `encodedlogistics:plc` (`PlcBlock`): wall-mounted, facing away from the wall. Made of a steel backplate, a DIN rail, the CPU module (LCD, RUN/ERROR LEDs, an I/O LED per face) and four module slots. Block state: facing, mode (off / stop / run / fault), slot1..slot4.
- **I/O:** six faces. Each reads and drives redstone exactly like the Control Interface (shared `RedstoneChannels`, weak power). A face's I/O LED lights for 10 ticks after its level changes.
- **Power:**
  - Uses `plcEnergy` FE/t (default 2) from its own buffer, `plcBuffer` (default 1,000 FE). Any face fills the buffer (FE capability in `registry/ModCapabilities`).
  - Cabled to a network on any face except its front, it becomes a device there (`PLC01`..., type `PLC`, one lane) and the network powers it.
  - With no power it goes OFF: outputs dark, program halted. When power returns it runs again from the top, as STOP -> RUN would.
- **Scan model** (`PlcBlockEntity`):
  - The program runs continuously, `plcInstructionsPerTick` instructions a tick (default 50). A pass that needs more carries on next tick.
  - On ENDPGM the next pass starts from the top next tick, and variables carry over.
  - `VALUE()` is set again only on STOP->RUN and at power-up.
  - `RETAIN(*YES)` variables are kept in the program itself. They survive STOP/RUN, power loss, world reloads and cartridges.
  - A world reload counts as a power-up.
- **FAULT:**
  - An unmonitored escape message puts the PLC in FAULT. The message and its line are kept for the status screen and the LCD.
  - Outputs HOLD their levels or drop to ZERO, depending on `plcFaultOutputs`.
  - F9 or `STRPLC` clears the fault and runs from the top. STOP clears the outputs.
- **Limits:** at most `plcMaxPerChunk` (16) can run per chunk and `plcMaxPerServer` (512) per server. Any others stay in STOP with the note "PLC limit reached".
- **Authority:** on a network, the program runs with the Firewall authority of the player who last loaded it (`PlcContext.allowed` -> `Authority.allowed`). Off a network, everything it can run is allowed.
- **The program** (`PlcProgram`, data component `encodedlogistics:plc_program`):
  - It holds the name, source lines, file record formats, loadedBy name/UUID/authority, a saved timestamp, and retained variables as text.
  - It is recompiled from source with the same ELCL compiler. The target is `Compiler.Target.PLC` when networked and `PLC_LOCAL` when not cabled.
  - The default program name is `PLCPGM`.
- **Four ways to load a program:**
  1. Save it in the source editor (PLCSTS option 1). `PlcBlockEntity.save` compiles it and returns the first error, e.g. "ELC1502 ... (line 14)", or ELC0213 when it goes in.
  2. Load from an EEPROM Cartridge held in hand (PLCSTS option 5). Option 4 saves to the cartridge.
  3. Run `SNDPLCPGM PGM(lib/name) DEV(PLCnn) RUN(*YES|*NO)` from the network. The program must be compiled with `CRTELPGM TGT(*PLC)` (ELC1504 otherwise). The PLC stops first, then runs from the top if RUN(*YES). Whoever ran the command becomes the loader. Loading the same program again keeps its retained variables.
  4. Place a PLC item that carries a program. Breaking a PLC keeps its program and device name on the item; its modules drop separately.
- **ELCL without a network (PLC_LOCAL):**
  - Allowed: `PGM ENDPGM DCL CHGVAR IF ELSE DO ENDDO DOWHILE DOUNTIL DOFOR FOREACH ENDFOR LEAVE ITERATE SELECT WHEN OTHERWISE ENDSELECT GOTO RETURN SUBR ENDSUBR CALLSUBR MONMSG RCVMSG SNDPGMMSG ADDLSTE RMVLSTE CLRLST DLYJOB DLYTICK RTVSNSVAL RTVRSIN CHGRSOUT`, with DEV omitted or `*SELF`.
  - Anything else is ELC1502 (`Compiler.PLC_LOCAL_COMMANDS`, `Compiler.plcLocal`).
  - Networked, every command works. Messages go to the PLC's own log (16 entries).
- **PLC commands:**
  - `RTVSNSVAL`: MODULE 1-4, RTNVAL, RTNSTS, RTNAUX, TYPE, DEV (default *SELF). Without RTNSTS, a missing module or target is ELC1501 / ELC1503.
  - `SNDPLCPGM`: loads a program (above).
  - `STRPLC`: RUN; ELC1505 when there is no program.
  - `ENDPLC`: STOP.
  - `DLYTICK TICKS(1-1200)`.
  - `DCL ... RETAIN(*YES|*NO)`: ELC1506 outside a PLC program.
  - Message IDs ELC1501-ELC1509 exist in the code.
- **Sensor modules** (`PlcModule`, `PlcModuleItem`, `PlcSensors`):
  - Use a module on the PLC and it goes into the first free slot. Sneak-use with an empty hand removes the last one.
  - Each reading gives a value, a status (`*OK`, `*NOMODULE`, `*NOTARGET`) and an aux value:

  | Module (item id) | TYPE() | Reads | Value | Aux | Setting (PLCMOD 2) |
  |---|---|---|---|---|---|
  | presence_sensor | *PRESENCE | players/mobs within radius | count | nearest's name | radius 1-16 (default 8), *PLAYERS / *MOBS / *ALL (default *PLAYERS) |
  | inventory_sensor | *INVENTORY | container on its face | % full (comparator-style, by slot) | item count | face (default: behind the PLC) |
  | fluid_sensor | *FLUID | tank on its face | % full | amount mB | face |
  | light_sensor | *LIGHT | light at the PLC | 0-15 | *DAY / *NIGHT | none |
  | timer_module | *TIMER | game clock | game ticks | "Day 2 14:32" | none |

- **EEPROM Cartridge** (`encodedlogistics:eeprom_cartridge`): either blank or written (it then carries `plc_program`). The tooltip shows the program, or says it is blank. It is used from the PLC screen while held; there is no slot for it.
- **GUIs** (`PlcMenu`, green-screen views):
  - **PLCSTS:** status. It shows the program, size, mode, scan ticks, instructions/tick, budget, loaded-by, authority, network, last error and energy. Menu: 1 edit, 2 I/O, 3 modules, 4/5 save to or load from the cartridge, 6 retained variables. Anything else typed on its command line runs as an ELCL command on its network.
  - **PLCIO:** per face, the input, the output and what drives it (the CHGRSOUT line or the neighbour).
  - **PLCMOD:** the modules. 2=Change setting, 4=Remove.
  - **SETTING:** one module's settings.
  - **RETAINED:** retained variables plus the PLC's log.
  - **Keys:** F6 RUN, F7 STOP, F9 clear fault, F10 I/O, F11 modules, F12 back.
- **Who may open it:**
  - On a network: the Firewall's BUILD permission.
  - Off a network: anyone. With `plcOpenToAnyone=false`, only the player who placed it and gamemaster-level operators.
- **Recipes** (all shapeless):

  | Item | Ingredients |
  |---|---|
  | plc | circuit substrate, logic die x2, redstone x2, iron ingot, ferrite |
  | presence_sensor | substrate, logic die, stone pressure plate |
  | inventory_sensor | substrate, logic die, comparator |
  | fluid_sensor | substrate, logic die, bucket |
  | light_sensor | substrate, logic die, daylight detector |
  | timer_module | substrate, logic die, clock |
  | eeprom_cartridge | logic die, copper foil, iron nugget x2 |

- **Tests:** `gametest/PlcGameTests`, `src/test/.../elcl/PlcProgramTest`.

### Display Panels (`display/`, `client/display/`, `menu/DisplayPanelMenu`, `client/screen/DisplayPanelScreen`)
- **Block:** an 8 px slab on a wall.
- **Merging** (`DisplayScreens`):
  - Panels with the same facing that sit side by side or stacked in one plane are flooded together (flood cap 1,024).
  - They are split greedily into the largest rectangles, up to `displayMaxWidth` x `displayMaxHeight` (default 8 x 6). Largest area goes first; ties go to the top-most, then the left-most.
  - Each rectangle is one screen, one device and one lane, drawing `displayPanelDrain` FE/t (0.5). It is named `DSP01`....
  - The master is the bottom-left block and holds the content. Bezels are drawn only on the rectangle's outer edges. The status LED is on the bottom-right block.
  - A re-merge keeps content: a screen of the same size first, else the largest. A resize scales the regions in proportion.
- **States:** off (no lane), booting (test pattern for `BOOT_TICKS` = 40), online, or no signal (off a running network: shows "NO SIGNAL").
- **Connection:** the back and bottom (a cable, or the device or cable behind it), and the panels beside it.
- **Canvas:**
  - 32 x 32 px per panel (`CANVAS = 32`), so an 8x6 screen is 256 x 192. The terminal font is 6 x 10.
  - Drawn client-side into a dynamic texture (`DisplayCanvases`, painted by `CanvasPainter`; the reference is `tools/dsp_canvas.py`).
  - Shown as one full-bright quad by `DisplayRenderer`.
- **Modes** (`DisplayContent.Mode`): only TEXT and DASHBOARD. The handoff's "Script-controlled" mode was merged into DASHBOARD; old saves that say "SCRIPT" load as DASHBOARD.
- **Text:** lines with colour, alignment and size. Sizes are `*SMALL` (4 x 6 font), `*NORMAL` (6 x 10), `*LARGE` (2x) and `*HUGE` (3x).
- **Regions:** named rectangles (A, B, C ...), never overlapping, at most 16 (`DisplayPanelMenu.MAX_REGIONS`). With none set up, A is the whole screen.
- **Widgets:** `*NONE, *TEXT, *STORAGE, *COLD, *ENERGY, *LANES, *JOBS, *ITEM, *CLOCK, *DEVICES, *UPS, *GRAPH, *IMAGE, *TABLE`.
- **Live data** (`DisplayData`): worked out on the server once a second for players near the screen. It covers storage hot and cold, energy, lanes, active jobs, an item's count, devices by type, UPSes, and tables (a file's records through a RUNQRY QRYSLT and SORT, up to 64 rows).
- **Graphs:**
  - Stats: `*ITEMFLOW *ENERGY *LANES *STORAGE *CRAFTING *ITEM *MCHOPS *MCHFE`. Ranges: `*1M *10M *1H *1D`. Types: `*LINE` or `*BAR`.
  - Data comes from a Monitoring Server on the network when there is one.
  - Otherwise, and always for *STORAGE and *ITEM, it comes from the screen's own history: 1 sample a second for `displayHistorySeconds` (default 600), kept while loaded.
- **Images** (`DisplayImages`):
  - PNG files from `<world>/encodedlogistics/images/<SYSNAME>/`.
  - Switched by `allowImages` (AUTO = on in single-player, off on dedicated servers).
  - Size limits: `displayMaxImageBytes` (MB), `displayMaxImageSize` (px per side) and `displayMaxImagePixels`. They are checked from the file size and the PNG header before any decoding, by the same reader that then decodes it.
  - Colour modes: 16 (the fixed panel palette, `textures/display/palette.json`), 64 or 256 (an adaptive median-cut palette), or FULL (24-bit). The server caps them with `displayImageColors`.
  - The palette modes use a 4x4 Bayer dither (strength 48 / 24 / 12). FULL and `SCALE(*NEAREST)` are not dithered.
  - Images are decoded off the server thread, at the region's size, and cached.
  - **Clients only ever get the rendered region, never the file.**
  - Messages: ELC1312 (not found, or not a PNG), ELC1313 (too big), ELC1315 (images disabled), ELC1317 (colour mode above the server's limit; a warning, the image still shows).
- **Configuration GUI** (`DisplayPanelScreen`):
  - Opened with an empty hand, and needs BUILD. Once the screen has touch triggers, you must sneak to open it.
  - **Mode tab:** Text or Dashboard, the device name, the background.
  - **Layout tab:** a preview of the canvas at up to 1.5x. Drag an edge to move it, drag in from the border to split a region, right-click to remove one. Edges snap to 4 px.
  - **Widgets tab:** a row per region and a "Change widget" picker.
  - Changes go to the server as a `DisplayConfigPayload` and are checked there.
- **Touch** (`DisplayTouch`):
  - Using the screen (rather than configuring it) fires `*DSPTOUCH` with &DATA = `"DSP01 A 40 12"`: the screen, the region (or `*NONE`) and the canvas x, y.
  - It needs no permission. It is rate-limited to 4 a second per player.
- **ELCL commands:**
  - `SNDDSPTXT DEV TEXT(64) LINE(1-64|*NEXT) CLEAR SIZE COLOR ALIGN`
  - `CLRDSP DEV RGN(*ALL)`
  - `CHGDSPRGN DEV RGN X Y W H` (canvas px; X and Y 0-1024, W and H 1-1024) `BG`
  - `SNDDSPWDG DEV RGN WDG(*STORAGE *COLD *ENERGY *LANES *JOBS *ITEM *CLOCK *DEVICES *UPS *TEXT *TABLE *NONE) ITEM DEVTYPE COLOR FILE QRYSLT SORT`
  - `SNDDSPGPH DEV RGN STAT ITEM(a machine name for *MCHOPS/*MCHFE) RANGE TYPE COLOR`
  - `SNDDSPIMG DEV RGN FILE SCALE(*DITHER|*NEAREST) COLORS(*DFT|*FULL|16|64|256)`
  - `RTVDSPSIZ DEV` plus the size in panels and in px
  - Message IDs ELC1310-ELC1319 exist.
- **Recipe:** shaped `GSG` / `LDI` / `G G`, makes 4 (glass pane x4, circuit substrate, logic die, glowstone dust, iron ingot).
- **Tests:** `gametest/DisplayGameTests`, `src/test/.../display/DisplayImagesTest`.

### Signal devices: what they share (`signal/SignalBlock`, `SignalBlockEntity`, `SignalMenu`, `client/screen/SignalScreen`)
- They mount on any face of a block.
- Standalone, they work from the redstone at their block.
- Cabled to a network, or placed against a networked block, each one is also a device there: 1 lane, `signalDeviceDrain` FE/t (0.25), named `LGT01`, `SRN01` or `SPK01`.
- **Settings screen:** right-click opens it. Click a row to cycle or edit it. The bottom field edits text rows and the device name. On a network it needs the Firewall's BUILD permission. Changes go as a `SignalConfigPayload`.
- **Trigger** (sirens and speakers): `REDSTONE`, `NETWORK` or `BOTH` (sirens default to BOTH).
- **ELCL:** every signal command has Auth CONFIGURE, which maps to the Firewall's BUILD permission (`elcl/exec/CommandRunner`). DEV takes a list of up to 32 devices, or `*ALL`.

### Cage Lights (`signal/CageLightBlock`, `CageLightBlockEntity`, `client/signal/SignalTints`)
- **Blocks:** 16 `cage_light_<dye>`, one per vanilla dye: white, orange, magenta, light_blue, yellow, lime, pink, gray, light_gray, cyan, purple, blue, brown, green, red, black. They share one model pair, and the bulb is tinted with the dye colour.
- **Light level:** 1-15 while lit (default 15).
- **Control:** `REDSTONE` (default; on with a signal), `INVERTED`, `ALWAYS` or `NETWORK` only. The network state (`CHGLGT STATUS`) is ORed with the redstone. ALWAYS ignores both.
- **ELCL:** `CHGLGT DEV STATUS(*SAME|*ON|*OFF|*TOGGLE) LVL(1-15|*SAME)`. A bad level is ELC2401. Type code LGT.
- **Recipe:** shaped ` B ` / `GLG` / `NDN`, makes 4 (iron bars, glass x2, glowstone dust, iron nugget x2, the dye).

### Alarm Strobes (`signal/SirenBlock`, `SirenBlockEntity`, `SirenSound`)
- **Blocks:** `siren_green`, `siren_amber`, `siren_red`. Their lenses use the lime, orange and red dyes, so they match the Cage Lights.
- **Light modes:** OFF, SOLID, SLOW, MEDIUM, FAST.
- **Tones:** each has a sample length in ticks.

  | Tone | Ticks |
  |---|---|
  | WAIL | 80 |
  | YELP | 40 |
  | KLAXON | 32 |
  | BELL | 40 |
  | HORN | 40 |
  | BEEP | 24 |
  | NONE (light only) | 0 |

  The tones are synthesised OGGs in `sounds/block/siren/`.
- **Looping:** the client loops a tone by re-triggering it at the end of each sample. Samples are counted from a start tick shared by the network's active sirens with the same tone, so a hall of them wails together.
- **Settings:** volume 0-100 % (default 80); range in blocks (default 48, up to `sirenMaxRange` = 96); trigger (default BOTH).
- **ELCL:** `STRSRN DEV SOUND(*SAME|*WAIL..*NONE) MODE(*SAME|*SOLID|*SLOW|*MEDIUM|*FAST)` and `ENDSRN DEV`. Type code SRN.
- **Recipe:** shapeless: redstone, note block, iron ingot, glass, and lime, orange or red dye.

### Speaker (`signal/SpeakerBlock`, `SpeakerBlockEntity`, `AudioFiles`, `MidiFile`, `MidiParts`, `NoteInstruments`, `net/AudioPayloads`, `client/signal/*`)
- **Block:** a flat 14x14x2 panel on any face. Its LED shows idle, playing (green) or error (amber). Type code SPK.
- **Defaults:** volume 70, range 32 (up to `speakerMaxRange` = 64), loop off.
- **Sources** (`Source`): `FILE`, `URL`, `NOTE`, `MIDI`. A rising redstone edge plays. The signal going off stops a looping source.
- **Files:**
  - Read from `<world>/encodedlogistics/audio/<SYSNAME>/`. A Speaker on no network uses the `audio/` folder itself.
  - Formats: OGG Vorbis and MP3.
  - The server checks each file: ELC2402 if not found, ELC2403 if over `audioMaxBytes` (MB) or `audioMaxSeconds`, ELC2406 if it can't be decoded.
- **Getting files to clients:**
  - Clients are told only the content hash, and ask for the bytes (`AudioPayloads`: 24 KB chunks, 4 chunks a tick per player, one file at a time).
  - Clients keep the bytes in memory by hash (`client/signal/AudioCache`: 64 MB budget, least recently played dropped first, cleared on leaving the world).
  - The server remembers the last 256 checked files by hash.
- **Decoding** (`AudioDecoding`):
  - OGG uses Minecraft's own decoder.
  - MP3 uses **JLayer 1.0.1.4** (LGPL 2.1). It is carried jar-in-jar, with its licence at `META-INF/licenses/jlayer-LGPL-2.1.txt`.
  - Audio is mixed down to mono 16-bit, started where the play should be by now, and cut at the server's limit. It is streamed with `StreamSound`.
- **Web URLs:**
  - http(s) only.
  - Server side, `allowWebAudio` must allow it (AUTO: on in single-player, off on dedicated servers; ELC2404 otherwise). The host must be on `webAudioHosts`, which includes subdomains; empty means none (ELC2405 otherwise).
  - Each listening client fetches the URL itself, and only if that player has opted in with the client option `audio.allowWebAudio` (default false).
  - The client checks the URL again itself (`WebAudioFetch`): no user info; every address the host resolves to must be public (no loopback, private, link-local, multicast or unspecified, with IPv4-mapped and NAT64 addresses judged as IPv4); it connects only to the checked address; no redirects; 200 only; the size limit applies; 30 s in total.
  - Failure messages start with "web:" and show on the Speaker's popup.
- **Notes:** `PLYNOTE DEV INST(*HARP..*PLING, 16 instruments) NOTE(0-24)`. NOTE takes a list of up to 32, which makes a sequence: each rising edge plays the next note. A bad note is ELC2407.
- **MIDI:**
  - `PLYMID DEV FILE LOOP VOL MAP((n SPKnn)...) MAPBY(*AUTO|*TRACK|*CHANNEL)`.
  - Format 0 and 1 Standard MIDI Files (RIFF RMID too), from the same audio folder. Limits: `midiMaxKilobytes` and `midiMaxSeconds`. ELC2409 if it isn't MIDI, ELC2410 for format 2.
  - It plays on the server as note-block notes sent as block events, so the file never goes to clients.
  - The speaker that starts it is the conductor. Up to 32 parts are mapped to speakers.
  - Each speaker gets at most `midiMaxNotes` notes a tick; the loudest are kept.
  - `STPAUD` on any of the speakers stops them all.
- **Other commands:** `PLYAUD DEV SRC(a file name or URL, 256) VOL LOOP` and `STPAUD DEV`.
- **Recipe:** shapeless: note block, copper foil, circuit substrate, iron nugget x4.
- **Tests:** `gametest/SignalGameTests`, plus unit tests `signal/AudioFilesTest`, `signal/MidiFileTest`, `client/signal/AudioDecodingTest` and `client/signal/WebAudioFetchTest`.

### Inventory Tap and Threshold Sensor (cable parts in `part/`, not in this area)
- **Inventory Tap** (`part/InventoryTapPart`):
  - Makes the inventory it faces, and that block's fluid and gas tanks, part of the network's storage in place.
  - Access: read and write, read only, or write only. It has a filter.
  - Priority -999..999 (Drive Bays are 0). Checked every 20 ticks.
  - GUI: `InventoryTapScreen`.
- **Threshold Sensor** (`part/ThresholdSensorPart`):
  - Emits redstone while the network's count of an item is ABOVE, BELOW or EQUAL to its threshold: strong 15 into the block its face is against, weak around it.
  - Checked every 4 ticks. Off while the network is offline or no item is set.
  - GUI: `ThresholdSensorScreen`.
  - Recipe: shapeless: substrate, logic die, redstone torch, comparator.

## Config options

Two specs are registered.

- **`Config.SPEC`** (`Config.java`):
  - Registered in the `EncodedLogistics` constructor with `modContainer.registerConfig(localConfigType(), Config.SPEC)`.
  - `localConfigType()` looks up the `ModConfig.Type` named "LOCAL" (FML 12.0.8 / NeoForge 26.3.0.37-beta renamed COMMON to LOCAL), and falls back to COMMON.
  - The file is `config/encodedlogistics-local.toml`, or `encodedlogistics-common.toml` on older betas. This is inferred from the lang keys `...section.encodedlogistics.local.toml` and `...common.toml`.
  - The code and its comments call it "the server config", but it is a LOCAL/COMMON type, not SERVER. It is neither per-world nor synced to clients (see Open questions).
- **`TerminalSettings.SPEC`** (`client/screen/TerminalSettings.java`):
  - `ModConfig.Type.CLIENT`, file `encodedlogistics-client.toml`.
  - Registered in `client/EncodedLogisticsClient` (client dist only), which also registers `IConfigScreenFactory` -> NeoForge's `ConfigurationScreen`.

**Config-screen labels:**
- They come from `assets/encodedlogistics/lang/en_us.json`, under the keys `encodedlogistics.configuration.<key>` and `.<section>`, looked up by key name alone.
- Keys with no label are marked "(none)" in the tables.
- The two `allowWebAudio` keys (server `signals`, client `audio`) share one label.
- Section labels:
  - Local spec: controller "Network Controller", lanes "Lanes", power "Power", storage "Storage", autocrafting "Autocrafting", reach "Reach", lithography "Lithography Press", facades "Cable Facades", rack "Server Rack", optical "Optical and Chassis", elcl (none), plc "PLCs", signals "Lights, Sirens and Speakers".
  - Client spec: terminal "Terminals", toasts "Job Toasts", audio "Sounds".

**The `rack` section is a catch-all.** It also holds routing, UPS, job toasts, wireless, Arcforge machine power, Midrange, disk and tape drives, Display Panels, access points and the Terminal Desk.

### Config.SPEC: 212 options
Generated by parsing Config.java. The Default and Range columns are the Java source literals, verbatim.

| # | Path | Key | Java type | Builder call | Default | Range / allowed | Comment | Config-screen label | Field |
|---|---|---|---|---|---|---|---|---|---|
| 1 | controller | `maxSize` | IntValue | defineInRange | `7` | [1 .. 32] | Largest Network Controller structure, in blocks along each axis. | Max Structure Size | CONTROLLER_MAX_SIZE |
| 2 | controller | `energyPerBlock` | IntValue | defineInRange | `25_000` | [1 .. Integer.MAX_VALUE] | FE each controller block adds to the structure's buffer. | Energy per Block | CONTROLLER_ENERGY_PER_BLOCK |
| 3 | controller | `maxReceivePerBlock` | IntValue | defineInRange | `4_096` | [0 .. Integer.MAX_VALUE] | FE per tick each controller block accepts. | Max Receive per Block | CONTROLLER_MAX_RECEIVE |
| 4 | controller | `drainPerBlock` | DoubleValue | defineInRange | `2.0` | [0.0 .. 1_000_000.0] | FE per tick each controller block drains while the network runs. | Drain per Block | CONTROLLER_DRAIN |
| 5 | lanes | `perControllerFace` | IntValue | defineInRange | `32` | [1 .. 1024] | Lanes each controller face with a network connection provides. | Per Controller Face | LANES_PER_CONTROLLER_FACE |
| 6 | lanes | `perCable` | IntValue | defineInRange | `8` | [1 .. 1024] | Lanes a Network Cable carries. | Per Network Cable | LANES_PER_CABLE |
| 7 | lanes | `perDenseCable` | IntValue | defineInRange | `32` | [1 .. 1024] | Lanes a Dense Network Cable carries. | Per Dense Network Cable | LANES_PER_DENSE_CABLE |
| 8 | lanes | `perFiberCable` | IntValue | defineInRange | `32` | [1 .. 1024] | Lanes a Fiber Cable carries. | Per Fiber Cable | LANES_PER_FIBER_CABLE |
| 9 | lanes | `cableDrain` | DoubleValue | defineInRange | `0.05` | [0.0 .. 1_000.0] | FE per tick each Network Cable block drains while its network runs. | Network Cable Drain | CABLE_DRAIN |
| 10 | lanes | `denseCableDrain` | DoubleValue | defineInRange | `0.2` | [0.0 .. 1_000.0] | FE per tick each Dense Network Cable block drains while its network runs. | Dense Network Cable Drain | DENSE_CABLE_DRAIN |
| 11 | lanes | `fiberCableDrain` | DoubleValue | defineInRange | `0.1` | [0.0 .. 1_000.0] | FE per tick each Fiber Cable block drains while its network runs. | Fiber Cable Drain | FIBER_CABLE_DRAIN |
| 12 | lanes | `adHocMaxDevices` | IntValue | defineInRange | `8` | [0 .. 1024] | Lane-using devices a network without a controller can run. | Ad-hoc Device Limit | ADHOC_MAX_DEVICES |
| 13 | power | `inletMaxInput` | IntValue | defineInRange | `16_384` | [0 .. Integer.MAX_VALUE] | FE per tick a Power Inlet accepts through its port. | Power Inlet Max Input | INLET_MAX_INPUT |
| 14 | power | `inletBuffer` | IntValue | defineInRange | `0` | [0 .. Integer.MAX_VALUE] | FE a Power Inlet holds when the network's energy is full, passed on as soon as there's room (0: none). | Power Inlet Buffer | INLET_BUFFER |
| 15 | power | `capacitorCapacity` | IntValue | defineInRange | `2_000_000` | [1 .. Integer.MAX_VALUE] | FE a Capacitor Bank stores. | Capacitor Bank Capacity | CAPACITOR_CAPACITY |
| 16 | power | `capacitorMaxTransfer` | IntValue | defineInRange | `16_384` | [0 .. Integer.MAX_VALUE] | FE per tick a Capacitor Bank takes in, and gives out, at most. | Capacitor Bank Max Transfer | CAPACITOR_MAX_TRANSFER |
| 17 | power | `capacitorDirectIO` | BooleanValue | define | `true` | true / false | Whether Capacitor Banks also take and give FE directly on every face, not just through the network. | Capacitor Bank Direct IO | CAPACITOR_DIRECT_IO |
| 18 | power | `isolatorDrain` | DoubleValue | defineInRange | `0.5` | [0.0 .. 1_000.0] | FE per tick a Segment Isolator drains from each network it separates. | Segment Isolator Drain | ISOLATOR_DRAIN |
| 19 | storage | `driveBayDrain` | DoubleValue | defineInRange | `2.0` | [0.0 .. 1_000.0] | FE per tick a Drive Bay drains while its network runs, on top of its drives. | Drive Bay Drain | DRIVE_BAY_DRAIN |
| 20 | storage | `driveBayDrainPerDrive` | DoubleValue | defineInRange | `0.5` | [0.0 .. 1_000.0] | FE per tick each Storage Drive in a Drive Bay drains. | Drive Bay Drain per Drive | DRIVE_BAY_DRAIN_PER_DRIVE |
| 21 | storage | `driveTypeLimit` | IntValue | defineInRange | `63` | [1 .. 4096] | Different item types one Storage Drive holds, whatever its size. | Types per Drive | DRIVE_TYPE_LIMIT |
| 22 | storage | `energyDriveMaxTransfer` | IntValue | defineInRange | `65_536` | [0 .. Integer.MAX_VALUE] | FE per tick an Energy Storage Drive takes in or gives out as a battery item, out of a drive holder (in one it is part of the network's energy pool, with no limit of its own). | (none) | ENERGY_DRIVE_MAX_TRANSFER |
| 23 | storage | `terminalDrain` | DoubleValue | defineInRange | `0.5` | [0.0 .. 1_000.0] | FE per tick an Access Terminal drains. | Access Terminal Drain | TERMINAL_DRAIN |
| 24 | storage | `terminalLanes` | IntValue | defineInRange | `1` | [0 .. 32] | Lanes an Access Terminal uses (0: terminals are free). | Access Terminal Lanes | TERMINAL_LANES |
| 25 | storage | `partDrain` | DoubleValue | defineInRange | `0.5` | [0.0 .. 1_000.0] | FE per tick each other cable part (ports, taps, sensors) drains. | Part Drain | PART_DRAIN |
| 26 | storage | `partLanes` | IntValue | defineInRange | `1` | [0 .. 32] | Lanes each other cable part (ports, taps, sensors) uses. | Part Lanes | PART_LANES |
| 27 | storage | `portRates` | ConfigValue<List<? extends Integer>> | defineList | `List.of(4, 16, 32, 64)` | list elements: entry instanceof Integer value && value >= 0 (new-element default 4) | Items an Ingress or Egress Port moves per operation (one every 20 ticks), with 0, 1, 2 and 3 Throughput Modules. | Port Rates | PORT_RATES |
| 28 | storage | `portEnergyPerItem` | DoubleValue | defineInRange | `0.5` | [0.0 .. 1_000.0] | FE a port spends from its network for each item it moves. | Port Energy per Item | PORT_ENERGY_PER_ITEM |
| 29 | storage | `portFluidRates` | ConfigValue<List<? extends Integer>> | defineList | `List.of(1_000, 4_000, 8_000, 16_000)` | list elements: entry instanceof Integer value && value >= 0 (new-element default 1_000) | mB of fluid or gas an Ingress or Egress Port moves per operation (one every 20 ticks), with 0, 1, 2 and 3 Throughput Modules. | (none) | PORT_FLUID_RATES |
| 30 | storage | `portEnergyPerBucket` | DoubleValue | defineInRange | `2.0` | [0.0 .. 1_000.0] | FE a port spends from its network for each 1,000 mB of fluid or gas it moves. | (none) | PORT_ENERGY_PER_BUCKET |
| 31 | storage | `portEnergyRates` | ConfigValue<List<? extends Integer>> | defineList | `List.of(1_024, 4_096, 16_384, 65_536)` | list elements: entry instanceof Integer value && value >= 0 (new-element default 1_024) | FE per tick an Ingress or Egress Port in Energy mode moves, with 0, 1, 2 and 3 Throughput Modules. | (none) | PORT_ENERGY_RATES |
| 32 | autocrafting | `schedulerMaxSize` | IntValue | defineInRange | `7` | [1 .. 16] | Largest Scheduler structure, in blocks along each axis. | Scheduler Max Size | SCHEDULER_MAX_SIZE |
| 33 | autocrafting | `schedulerDrain` | DoubleValue | defineInRange | `2.0` | [0.0 .. 1_000.0] | FE per tick a formed Scheduler drains, on top of its blocks. | Scheduler Drain | SCHEDULER_DRAIN |
| 34 | autocrafting | `schedulerDrainPerBlock` | DoubleValue | defineInRange | `0.5` | [0.0 .. 1_000.0] | FE per tick each block of a formed Scheduler drains. | Scheduler Drain per Block | SCHEDULER_DRAIN_PER_BLOCK |
| 35 | autocrafting | `schedulerBaseMemory` | IntValue | defineInRange | `4_096` | [0 .. Integer.MAX_VALUE] | Job memory a Scheduler Core provides on its own (items across its jobs' ingredient trees). | Scheduler Base Memory | SCHEDULER_BASE_MEMORY |
| 36 | autocrafting | `jobBufferMemory` | IntValue | defineInRange | `16_384` | [0 .. Integer.MAX_VALUE] | Job memory each Job Buffer adds. | Job Buffer Memory | JOB_BUFFER_MEMORY |
| 37 | autocrafting | `schedulerBaseThreads` | IntValue | defineInRange | `1` | [1 .. 1024] | Jobs a Scheduler Core runs at once on its own. | Scheduler Base Threads | SCHEDULER_BASE_THREADS |
| 38 | autocrafting | `threadUnitThreads` | IntValue | defineInRange | `1` | [0 .. 1024] | Jobs each Thread Unit adds. | Thread Unit Threads | THREAD_UNIT_THREADS |
| 39 | autocrafting | `jobStallSeconds` | IntValue | defineInRange | `30` | [5 .. 3_600] | Seconds a running crafting job can go without progress before it shows as stalled (the Schematic Terminal's Jobs tab, WRKCRFJOB's steps, the *CRFSTALL trigger). | (none) | JOB_STALL_SECONDS |
| 40 | autocrafting | `threadCrafts` | IntValue | defineInRange | `1` | [0 .. 100_000] | Crafts a thread keeps going at once (Fabricators and Fabrication Servers run every craft they're given side by side). A job with more crafts left takes more of its Scheduler's free threads, and gives them back as it nears its end; every job gets at least one. 0: a thread runs any number of crafts. | (none) | THREAD_RUNS |
| 41 | autocrafting | `fabricatorCraftTicks` | ConfigValue<List<? extends Integer>> | defineList | `List.of(20, 10, 5)` | list elements: entry instanceof Integer value && value >= 1 (new-element default 20) | Ticks a Fabricator takes per craft with 0, 1 and 2 Throughput Modules. | Fabricator Craft Ticks | FABRICATOR_CRAFT_TICKS |
| 42 | autocrafting | `fabricatorEnergyPerCraft` | IntValue | defineInRange | `50` | [0 .. Integer.MAX_VALUE] | FE a Fabricator spends from its network per craft. | Fabricator Energy per Craft | FABRICATOR_ENERGY_PER_CRAFT |
| 43 | autocrafting | `fabricatorDrain` | DoubleValue | defineInRange | `1.0` | [0.0 .. 1_000.0] | FE per tick a Fabricator drains while its network runs. | Fabricator Drain | FABRICATOR_DRAIN |
| 44 | autocrafting | `gatewayDrain` | DoubleValue | defineInRange | `1.0` | [0.0 .. 1_000.0] | FE per tick a Gateway drains while its network runs. | Gateway Drain | GATEWAY_DRAIN |
| 45 | reach | `relayBaseRange` | IntValue | defineInRange | `32` | [0 .. 4096] | Blocks a Relay Antenna covers on its own (a sphere around it). | Relay Base Range | RELAY_BASE_RANGE |
| 46 | reach | `relayRangePerTransceiver` | IntValue | defineInRange | `32` | [0 .. 4096] | Blocks each Optical Transceiver in a Relay Antenna adds to its range. | Relay Range per Transceiver | RELAY_RANGE_PER_TRANSCEIVER |
| 47 | reach | `relayDrain` | DoubleValue | defineInRange | `2.0` | [0.0 .. 1_000.0] | FE per tick a Relay Antenna drains while its network runs. | Relay Drain | RELAY_DRAIN |
| 48 | reach | `controlInterfaceDrain` | DoubleValue | defineInRange | `1.0` | [0.0 .. 1_000.0] | FE per tick a Control Interface drains while its network runs. | (none) | CONTROL_INTERFACE_DRAIN |
| 49 | reach | `handheldCapacity` | IntValue | defineInRange | `200_000` | [1 .. Integer.MAX_VALUE] | FE a Handheld Terminal's battery holds. | Handheld Capacity | HANDHELD_CAPACITY |
| 50 | reach | `handheldDrainPerSecond` | IntValue | defineInRange | `10` | [0 .. Integer.MAX_VALUE] | FE a Handheld Terminal uses each second while its screen is open. | Handheld Drain per Second | HANDHELD_DRAIN_PER_SECOND |
| 51 | reach | `handheldEnergyPerItem` | IntValue | defineInRange | `1` | [0 .. Integer.MAX_VALUE] | FE a Handheld Terminal uses for each item it moves in or out of the network. | Handheld Energy per Item | HANDHELD_ENERGY_PER_ITEM |
| 52 | reach | `handheldChargeRate` | IntValue | defineInRange | `4_096` | [0 .. Integer.MAX_VALUE] | FE per tick a Handheld Terminal takes from a Capacitor Bank while held against it (use and hold), and at most from any other charger. | Handheld Charge Rate | HANDHELD_CHARGE_RATE |
| 53 | reach | `bridgeLanes` | IntValue | defineInRange | `32` | [1 .. 1024] | Lanes a linked pair of Network Bridges carries between its two networks. | Bridge Lanes | BRIDGE_LANES |
| 54 | reach | `bridgeDrain` | DoubleValue | defineInRange | `8.0` | [0.0 .. 1_000.0] | FE per tick each Network Bridge drains while its network runs. | Bridge Drain | BRIDGE_DRAIN |
| 55 | reach | `bridgeCrossDimension` | BooleanValue | define | `true` | true / false | Whether a pair of Network Bridges links networks in different dimensions. | Bridge Cross Dimension | BRIDGE_CROSS_DIMENSION |
| 56 | reach | `bridgeChunkLoading` | BooleanValue | define | `false` | true / false | Whether a linked Network Bridge keeps its own chunk loaded (so its partner's network reaches it while nobody is near). | Bridge Chunk Loading | BRIDGE_CHUNK_LOADING |
| 57 | reach | `p2pLanes` | IntValue | defineInRange | `8` | [1 .. 1024] | Lanes a lanes Point-to-Point Link carries from its input to its outputs (split evenly between them). | Point-to-Point Lanes | P2P_LANES |
| 58 | reach | `p2pDrain` | DoubleValue | defineInRange | `0.5` | [0.0 .. 1_000.0] | FE per tick each Point-to-Point Link endpoint drains while its network runs. | Point-to-Point Drain | P2P_DRAIN |
| 59 | reach | `p2pItemsPerOperation` | IntValue | defineInRange | `64` | [1 .. 4096] | Items an items Point-to-Point Link moves per operation (one every 10 ticks). | Point-to-Point Items per Operation | P2P_ITEMS_PER_OPERATION |
| 60 | reach | `p2pFluidPerOperation` | IntValue | defineInRange | `8_000` | [1 .. 1_000_000] | mB a fluids or pressurized Point-to-Point Link moves per operation (one every 10 ticks). | (none) | P2P_FLUID_PER_OPERATION |
| 61 | reach | `p2pEnergyPerTick` | IntValue | defineInRange | `8_192` | [1 .. Integer.MAX_VALUE] | FE per tick an energy Point-to-Point Link moves from its input to its outputs at most. | Point-to-Point Energy per Tick | P2P_ENERGY_PER_TICK |
| 62 | reach | `collectorTicksPerHardness` | IntValue | defineInRange | `30` | [1 .. 1_000] | Ticks a Collector Plane takes to break a block, per point of the block's hardness. | Collector Ticks per Hardness | COLLECTOR_TICKS_PER_HARDNESS |
| 63 | reach | `deployerInterval` | IntValue | defineInRange | `10` | [1 .. 1_000] | Ticks between a Deployer Plane's operations. | Deployer Interval | DEPLOYER_INTERVAL |
| 64 | lithography | `pressCapacity` | IntValue | defineInRange | `32_000` | [1 .. Integer.MAX_VALUE] | FE a Lithography Press buffers. | Energy Buffer | PRESS_CAPACITY |
| 65 | lithography | `pressMaxInput` | IntValue | defineInRange | `512` | [0 .. Integer.MAX_VALUE] | FE per tick a Lithography Press takes in. | Max Input | PRESS_MAX_INPUT |
| 66 | facades | `blocklist` | ConfigValue<List<? extends String>> | defineListAllowEmpty | `List.of()` | list elements: entry instanceof String (new-element default "minecraft:stone") | Blocks a Cable Facade can't copy, by id (e.g. "minecraft:bedrock"), on top of the built-in rules: the block must be a full, non-translucent cube without a block entity. | Blocked Facade Blocks | FACADE_BLOCKLIST |
| 67 | rack | `rackCullDistance` | IntValue | defineInRange | `24` | [0 .. 256] | Blocks away beyond which a Server Rack with both doors closed doesn't draw its devices. | Rack Device Cull Distance | RACK_CULL_DISTANCE |
| 68 | rack | `rackElevationCreativeFree` | BooleanValue | define | `true` | true / false | Whether a player in creative mode applying a Rack Elevation installs its devices (and their contents) without using up items. | Rack Elevations Free in Creative | RACK_ELEVATION_CREATIVE_FREE |
| 69 | rack | `firewallDrain` | DoubleValue | defineInRange | `2.0` | [0.0 .. 1_000.0] | FE per tick a Firewall drains while its network runs. | Firewall Drain | FIREWALL_DRAIN |
| 70 | rack | `firewallFailClosed` | BooleanValue | define | `true` | true / false | Whether a Firewall that's offline (no lane, no power) still enforces its rules. | Offline Firewall Still Enforces | FIREWALL_FAIL_CLOSED |
| 71 | rack | `rackBreakProtection` | BooleanValue | define | `true` | true / false | Whether breaking a Server Rack (and so pulling its devices out) needs the Firewall's rack access permission on its network. | (none) | RACK_BREAK_PROTECTION |
| 72 | rack | `rackBreakConfirm` | BooleanValue | define | `true` | true / false | Whether breaking a Server Rack takes a sneak, or a second left click within a second of the first, so a stray click can't start taking one down. | (none) | RACK_BREAK_CONFIRM |
| 73 | rack | `routerDrain` | DoubleValue | defineInRange | `4.0` | [0.0 .. 1_000.0] | FE per tick a Router drains while its network runs. | Router Drain | ROUTER_DRAIN |
| 74 | rack | `routerDrainPerTransceiver` | DoubleValue | defineInRange | `1.0` | [0.0 .. 1_000.0] | FE per tick each Optical Transceiver in a Router adds to its drain. | Router Drain per Transceiver | ROUTER_DRAIN_PER_TRANSCEIVER |
| 75 | rack | `routerBaseRate` | IntValue | defineInRange | `16` | [0 .. 4096] | Items per second a Router moves, shared between its routes. | Router Base Rate | ROUTER_BASE_RATE |
| 76 | rack | `routerRatePerTransceiver` | IntValue | defineInRange | `16` | [0 .. 4096] | Items per second each Optical Transceiver in a Router adds. | Router Rate per Transceiver | ROUTER_RATE_PER_TRANSCEIVER |
| 77 | rack | `routingBaseCost` | IntValue | defineInRange | `100` | [1 .. 100_000] | Dynamic routing: a hop costs this x 1000 / (what it carries + 1), rounded, at least 1 (what it carries: the smaller of its link's lanes and its Router's items per second). Lower costs win. | Routing Base Cost | ROUTING_BASE_COST |
| 78 | rack | `reconvergeSeconds` | IntValue | defineInRange | `5` | [2 .. 60] | Dynamic routing: at most how long Routers take to find new paths after a neighbour goes down (missed hellos, then the recompute). | Reconvergence (s) | ROUTING_RECONVERGE_SECONDS |
| 79 | rack | `maxHops` | IntValue | defineInRange | `16` | [1 .. 64] | Dynamic routing: the most hops a path may take. | Max Hops | ROUTING_MAX_HOPS |
| 80 | rack | `aclDefaultPermit` | BooleanValue | define | `true` | true / false | Router ACLs: whether what no rule matches is permitted (*PERMIT) or denied (*DENY). | ACL Default Permit | ACL_DEFAULT_PERMIT |
| 81 | rack | `trunkPortLanes` | IntValue | defineInRange | `64` | [1 .. 4096] | Lanes a Trunk Port is rated for: its panel shows what the devices on its segments use against them (each device's lanes are its rack's to grant). | Trunk Port Lanes | TRUNK_PORT_LANES |
| 82 | rack | `directoryDrain` | DoubleValue | defineInRange | `2.0` | [0.0 .. 1_000.0] | FE per tick a Directory Server (block or rack) drains while its network runs. | Directory Server Drain | DIRECTORY_DRAIN |
| 83 | rack | `directoryCacheSeconds` | IntValue | defineInRange | `30` | [0 .. 3_600] | How long a resolved SYSNAME/DEVICE name is kept (a route or network change clears it sooner). | Directory Cache (s) | DIRECTORY_CACHE_SECONDS |
| 84 | rack | `exchangePorts` | IntValue | defineInRange | `32` | [1 .. 256] | Ports an Exchange Switch has (each member Router takes one; its owner can offer fewer). | Exchange Ports | EXCHANGE_PORTS |
| 85 | rack | `exchangePortLanes` | IntValue | defineInRange | `64` | [1 .. 4096] | Lanes an Exchange Switch port carries: a peering's throughput (and cost) is the smaller of these and its Routers' rates. | Exchange Port Lanes | EXCHANGE_PORT_LANES |
| 86 | rack | `exchangeDrain` | DoubleValue | defineInRange | `8.0` | [0.0 .. 1_000.0] | FE per tick an Exchange Switch drains while its network runs. | Exchange Drain | EXCHANGE_DRAIN |
| 87 | rack | `exchangeChunkLoading` | BooleanValue | define | `true` | true / false | Whether an Exchange Switch keeps its chunk loaded while it has members. | Exchange Chunk Loading | EXCHANGE_CHUNK_LOADING |
| 88 | rack | `upsCapacity` | IntValue | defineInRange | `1_000_000` | [1 .. Integer.MAX_VALUE] | FE a UPS's battery holds. | UPS Capacity | UPS_CAPACITY |
| 89 | rack | `upsMaxOutput` | IntValue | defineInRange | `8_192` | [1 .. Integer.MAX_VALUE] | FE per tick a UPS supplies at most (more is an overload). | UPS Max Output | UPS_MAX_OUTPUT |
| 90 | rack | `upsMaxInput` | IntValue | defineInRange | `8_192` | [0 .. Integer.MAX_VALUE] | FE per tick a UPS recharges at most, from the network's surplus. | UPS Max Input | UPS_MAX_INPUT |
| 91 | rack | `upsDrainOnline` | DoubleValue | defineInRange | `2.0` | [0.0 .. 1_000.0] | FE per tick a UPS in Online mode drains while its network runs. | UPS Drain (Online) | UPS_DRAIN_ONLINE |
| 92 | rack | `upsDrainStandby` | DoubleValue | defineInRange | `1.0` | [0.0 .. 1_000.0] | FE per tick a UPS in Standby mode drains while its network runs. | UPS Drain (Standby) | UPS_DRAIN_STANDBY |
| 93 | rack | `allowJobToasts` | BooleanValue | define | `true` | true / false | Whether players get a toast when a crafting job finishes, fails or is cancelled (each player chooses which in their client config). | Allow Job Toasts | ALLOW_JOB_TOASTS |
| 94 | rack | `upsLowBatteryPercent` | IntValue | defineInRange | `20` | [0 .. 100] | Charge (%) below which a UPS on battery shows Low battery and sounds its rapid alarm. | UPS Low Battery (%) | UPS_LOW_BATTERY_PERCENT |
| 95 | rack | `upsAlarms` | BooleanValue | define | `true` | true / false | Whether UPSes sound their alarms on battery, for every player. (Each player can also turn them off for themselves: upsAlarmSounds in their client config.) | UPS Alarms | UPS_ALARMS |
| 96 | rack | `rackController2uEnergyBlocks` | IntValue | defineInRange | `20` | [1 .. 343] | Controller blocks' worth of energy buffer, receive rate and drain a 2U Network Controller has (a 4U has twice). | 2U Controller Energy (blocks) | RACK_CONTROLLER_2U_ENERGY_BLOCKS |
| 97 | rack | `rackController2uLaneFaces` | IntValue | defineInRange | `6` | [1 .. 1024] | Connected controller faces' worth of lanes a 2U Network Controller hands out (a 4U has twice). | 2U Controller Lanes (faces) | RACK_CONTROLLER_2U_LANE_FACES |
| 98 | rack | `rackControllerStandbyFactor` | DoubleValue | defineInRange | `0.1` | [0.0 .. 1.0] | The share of its drain a standby rack Network Controller draws while idle. | Standby Controller Draw | RACK_CONTROLLER_STANDBY_FACTOR |
| 99 | rack | `rackPowerMaxInput` | IntValue | defineInRange | `-1` | [-1 .. Integer.MAX_VALUE] | FE per tick a Server Rack takes in through its connection points (across all of them); -1: the same as inletMaxInput. | Rack Power Max Input | RACK_POWER_MAX_INPUT |
| 100 | rack | `l2Switch24Lanes` | IntValue | defineInRange | `16` | [0 .. 1024] | Rack-local lanes a 24-Port L2 Switch gives the other devices in its rack. | 24-Port L2 Switch Lanes | L2_SWITCH_24_LANES |
| 101 | rack | `l2Switch48Lanes` | IntValue | defineInRange | `32` | [0 .. 1024] | Rack-local lanes a 48-Port L2 Switch gives the other devices in its rack. | 48-Port L2 Switch Lanes | L2_SWITCH_48_LANES |
| 102 | rack | `l3SwitchLanes` | IntValue | defineInRange | `32` | [0 .. 1024] | Rack-local lanes an L3 Switch gives the other devices in its rack. | L3 Switch Lanes | L3_SWITCH_LANES |
| 103 | rack | `switchDrain` | DoubleValue | defineInRange | `2.0` | [0.0 .. 1_000.0] | FE per tick an L2 Switch drains while its network runs. | Switch Drain | SWITCH_DRAIN |
| 104 | rack | `l3SwitchDrain` | DoubleValue | defineInRange | `3.0` | [0.0 .. 1_000.0] | FE per tick an L3 Switch drains while its network runs. | L3 Switch Drain | L3_SWITCH_DRAIN |
| 105 | rack | `l3SwitchRate` | IntValue | defineInRange | `32` | [0 .. 4096] | Items per second an L3 Switch routes between segments, shared between its routes. | L3 Switch Rate | L3_SWITCH_RATE |
| 106 | rack | `computeServerThreads` | IntValue | defineInRange | `2` | [0 .. 1024] | Crafting threads each Compute Server gives its network's rack scheduler (the pool of every rack on the network). | Compute Server Threads | COMPUTE_SERVER_THREADS |
| 107 | rack | `memoryServerMemory` | IntValue | defineInRange | `32_768` | [0 .. Integer.MAX_VALUE] | Job memory each Memory Server gives its network's rack scheduler. | Memory Server Memory | MEMORY_SERVER_MEMORY |
| 108 | rack | `computeServerDrain` | DoubleValue | defineInRange | `6.0` | [0.0 .. 1_000.0] | FE per tick a Compute Server drains while its network runs. | Compute Server Drain | COMPUTE_SERVER_DRAIN |
| 109 | rack | `memoryServerDrain` | DoubleValue | defineInRange | `3.0` | [0.0 .. 1_000.0] | FE per tick a Memory Server drains while its network runs. | Memory Server Drain | MEMORY_SERVER_DRAIN |
| 110 | rack | `fabricationServerDrain` | DoubleValue | defineInRange | `4.0` | [0.0 .. 1_000.0] | FE per tick a Fabrication Server drains while its network runs (each craft also costs fabricatorEnergyPerCraft). | Fabrication Server Drain | FABRICATION_SERVER_DRAIN |
| 111 | rack | `monitoringServerDrain` | DoubleValue | defineInRange | `2.0` | [0.0 .. 1_000.0] | FE per tick a Monitoring Server drains while its network runs. | Monitoring Server Drain | MONITORING_SERVER_DRAIN |
| 112 | rack | `nasDrain` | DoubleValue | defineInRange | `2.0` | [0.0 .. 1_000.0] | FE per tick a NAS drains while its network runs, on top of its drives. | Nas Drain | NAS_DRAIN |
| 113 | rack | `sanDrain` | DoubleValue | defineInRange | `4.0` | [0.0 .. 1_000.0] | FE per tick a SAN drains while its network runs, on top of its drives. | San Drain | SAN_DRAIN |
| 114 | rack | `rackDriveDrain` | DoubleValue | defineInRange | `0.5` | [0.0 .. 1_000.0] | FE per tick each Storage Drive in a NAS or SAN drains. | Rack Drive Drain | RACK_DRIVE_DRAIN |
| 115 | rack | `rackConsoleDrain` | DoubleValue | defineInRange | `1.0` | [0.0 .. 1_000.0] | FE per tick a Rack Console drains while its network runs. | Rack Console Drain | RACK_CONSOLE_DRAIN |
| 116 | rack | `rackConsoleOpenDrain` | DoubleValue | defineInRange | `2.0` | [0.0 .. 1_000.0] | FE per tick a Rack Console drains on top of that while its drawer is out. | Rack Console Open Drain | RACK_CONSOLE_OPEN_DRAIN |
| 117 | rack | `wirelessControllerDrain` | DoubleValue | defineInRange | `4.0` | [0.0 .. 1_000.0] | FE per tick a Wireless Controller drains while its network runs. | Wireless Controller Drain | WIRELESS_CONTROLLER_DRAIN |
| 118 | rack | `wirelessCrossDimension` | BooleanValue | define | `true` | true / false | Whether Handheld Terminals linked to a Wireless Controller work in other dimensions too. | Wireless Across Dimensions | WIRELESS_CROSS_DIMENSION |
| 119 | rack | `wirelessAnyController` | BooleanValue | define | `true` | true / false | Whether any online Wireless Controller on the network serves a linked Handheld Terminal (else only the one it was linked to). | Any Wireless Controller Serves | WIRELESS_ANY_CONTROLLER |
| 120 | rack | `wirelessApClients` | IntValue | defineInRange | `8` | [1 .. 256] | Wireless Bridges and Wireless Ports each online Access Point lets a network's Wireless Controller serve. | (none) | WIRELESS_AP_CLIENTS |
| 121 | rack | `wirelessBridgeLanes` | IntValue | defineInRange | `32` | [1 .. 1_024] | Lanes a Wireless Bridge carries between its Wireless Controller and what's cabled to it. | (none) | WIRELESS_BRIDGE_LANES |
| 122 | rack | `wirelessEnergyMultiplier` | DoubleValue | defineInRange | `2.0` | [0.0 .. 100.0] | Wireless Bridges and Wireless Ports drain this many times their cabled equivalent's (Network Bridge, Ingress / Egress Port). | (none) | WIRELESS_ENERGY_MULTIPLIER |
| 123 | rack | `smallWirelessBridgeDrain` | DoubleValue | defineInRange | `1.0` | [0.0 .. 1_000.0] | FE per tick a linked Small Wireless Bridge (on an Arcforge machine) drains from its network over the air. | (none) | SMALL_WIRELESS_BRIDGE_DRAIN |
| 124 | rack | `machinePowerEfficiency` | DoubleValue | defineInRange | `0.9` | [0.01 .. 1.0] | With power from network on, the share of the FE taken from the network that reaches the machine. | (none) | MACHINE_POWER_EFFICIENCY |
| 125 | rack | `machinePowerRate` | IntValue | defineInRange | `1_000` | [0 .. 1_000_000] | With power from network on, the most FE per tick a machine is given. | (none) | MACHINE_POWER_RATE |
| 126 | rack | `machinePowerReserve` | DoubleValue | defineInRange | `0.25` | [0.0 .. 1.0] | Power from network never takes a network's stored energy below this share of its capacity, so the network keeps running. | (none) | MACHINE_POWER_RESERVE |
| 127 | rack | `midrangeDrain` | DoubleValue | defineInRange | `4.0` | [0.0 .. 1_000.0] | FE per tick a Midrange System drains while its network runs. | (none) | MIDRANGE_DRAIN |
| 128 | rack | `expansionCabinetDrain` | DoubleValue | defineInRange | `1.0` | [0.0 .. 1_000.0] | FE per tick an attached Expansion Cabinet adds to its Midrange System's drain. | (none) | EXPANSION_CABINET_DRAIN |
| 129 | rack | `integratedMidrangeDrain` | DoubleValue | defineInRange | `8.0` | [0.0 .. 1_000.0] | FE per tick an Integrated Midrange System drains while its network runs. | (none) | INTEGRATED_MIDRANGE_DRAIN |
| 130 | rack | `midrangeEnergyBlocks` | IntValue | defineInRange | `4` | [1 .. 343] | Controller blocks' worth of energy buffer and receive rate a Midrange System has as its network's controller. | (none) | MIDRANGE_ENERGY_BLOCKS |
| 131 | rack | `midrangeLaneFaces` | IntValue | defineInRange | `4` | [1 .. 1024] | Connected controller faces' worth of lanes a Midrange System hands out as its network's controller. | (none) | MIDRANGE_LANE_FACES |
| 132 | rack | `integratedMidrangeEnergyBlocks` | IntValue | defineInRange | `20` | [1 .. 343] | Controller blocks' worth of energy buffer and receive rate an Integrated Midrange System has (a 2U rack controller's by default). | (none) | INTEGRATED_MIDRANGE_ENERGY_BLOCKS |
| 133 | rack | `integratedMidrangeLaneFaces` | IntValue | defineInRange | `6` | [1 .. 1024] | Connected controller faces' worth of lanes an Integrated Midrange System hands out (a 2U rack controller's by default). | (none) | INTEGRATED_MIDRANGE_LANE_FACES |
| 134 | rack | `midrangeCraftEnergy` | IntValue | defineInRange | `20` | [0 .. 1_000_000] | FE a Midrange System (or Integrated Midrange System) spends per craft it runs itself. | (none) | MIDRANGE_CRAFT_ENERGY |
| 135 | rack | `midrangeIplTicks` | IntValue | defineInRange | `100` | [0 .. 6_000] | Ticks a Midrange System takes to IPL (boot) before it runs. | (none) | MIDRANGE_IPL_TICKS |
| 136 | rack | `midrangeStepTicks` | IntValue | defineInRange | `40` | [1 .. 6_000] | Ticks a Midrange System takes for each craft it runs itself. | (none) | MIDRANGE_STEP_TICKS |
| 137 | rack | `integratedMidrangeStepTicks` | IntValue | defineInRange | `25` | [1 .. 6_000] | Ticks an Integrated Midrange System takes for each craft it runs itself. | (none) | INTEGRATED_MIDRANGE_STEP_TICKS |
| 138 | rack | `diskDriveDrain` | DoubleValue | defineInRange | `1.5` | [0.0 .. 1_000.0] | FE per tick a Disk Drive drains while its network runs. | (none) | DISK_DRIVE_DRAIN |
| 139 | rack | `diskSpinUpTicks` | IntValue | defineInRange | `40` | [0 .. 6_000] | Ticks a Disk Drive spins up after a Storage Drive goes in, before the network can read it. | (none) | DISK_SPIN_UP_TICKS |
| 140 | rack | `diskSpinDownTicks` | IntValue | defineInRange | `20` | [0 .. 6_000] | Ticks a Disk Drive spins down before its Storage Drive comes out. | (none) | DISK_SPIN_DOWN_TICKS |
| 141 | rack | `tapeDriveDrain` | DoubleValue | defineInRange | `2.0` | [0.0 .. 1_000.0] | FE per tick a Tape Drive drains while its network runs (twice that while reading or writing). | (none) | TAPE_DRIVE_DRAIN |
| 142 | rack | `tapeReelItems` | IntValue | defineInRange | `65_536` | [8 .. 1 << 30] | Items a Tape Reel holds. | (none) | TAPE_REEL_ITEMS |
| 143 | rack | `tapeReelTypes` | IntValue | defineInRange | `1_024` | [1 .. 65_536] | Item types a Tape Reel holds. | (none) | TAPE_REEL_TYPES |
| 144 | rack | `tapeLoadTicks` | IntValue | defineInRange | `100` | [0 .. 6_000] | Ticks a Tape Drive takes to thread a reel mounted on it. | (none) | TAPE_LOAD_TICKS |
| 145 | rack | `tapeRewindTicks` | IntValue | defineInRange | `40` | [0 .. 6_000] | Ticks a Tape Drive takes to rewind its reel (before unloading it, or on 7=Rewind). | (none) | TAPE_REWIND_TICKS |
| 146 | rack | `tapeDriveBaseTicks` | IntValue | defineInRange | `60` | [1 .. 72_000] | Ticks a Tape Drive takes for each read or write, plus tapeDriveTicksPer4k for each 4,096 items. | (none) | TAPE_DRIVE_BASE_TICKS |
| 147 | rack | `tapeDriveTicksPer4k` | IntValue | defineInRange | `20` | [0 .. 72_000] | Ticks a Tape Drive adds to a read or write for each 4,096 items. | (none) | TAPE_DRIVE_TICKS_PER_4K |
| 148 | rack | `displayMaxWidth` | IntValue | defineInRange | `8` | [1 .. 16] | The widest a Display Panel screen merges, in panels. | (none) | DISPLAY_MAX_WIDTH |
| 149 | rack | `displayMaxHeight` | IntValue | defineInRange | `6` | [1 .. 16] | The tallest a Display Panel screen merges, in panels. | (none) | DISPLAY_MAX_HEIGHT |
| 150 | rack | `displayPanelDrain` | DoubleValue | defineInRange | `0.5` | [0 .. 1_000] | FE/t each Display Panel of a screen draws from its network. | (none) | DISPLAY_PANEL_DRAIN |
| 151 | rack | `displayHistorySeconds` | IntValue | defineInRange | `600` | [10 .. 3_600] | Seconds of graph history a Display Panel keeps itself (one sample a second, while loaded) for graphs no Monitoring Server serves. | (none) | DISPLAY_HISTORY_SECONDS |
| 152 | rack | `allowImages` | EnumValue<ImagesAllowed> | defineEnum | `AUTO` | enum ImagesAllowed: AUTO / TRUE / FALSE | Whether Display Panels show images from <world>/encodedlogistics/images/<SYSNAME>/: AUTO is on in single-player and off on dedicated servers. | (none) | ALLOW_IMAGES |
| 153 | rack | `displayMaxImageSize` | IntValue | defineInRange | `4_096` | [16 .. 16_384] | The largest source image a Display Panel takes, in pixels each way. | (none) | DISPLAY_MAX_IMAGE_SIZE |
| 154 | rack | `displayMaxImagePixels` | IntValue | defineInRange | `16_777_216` | [256 .. 268_435_456] | The most pixels (width x height) a source image for a Display Panel may have. Read from the file's header before it's decoded, so a small file can't unpack into a huge image. | (none) | DISPLAY_MAX_IMAGE_PIXELS |
| 155 | rack | `displayMaxImageBytes` | IntValue | defineInRange | `4` | [1 .. 64] | The largest source image file a Display Panel takes, in MB. | (none) | DISPLAY_MAX_IMAGE_BYTES |
| 156 | rack | `displayImageColors` | ConfigValue<String> | define | `"256"` | validator: value instanceof String text && java.util.List.of("16", "64", "256", "FULL").contains(text) | The most colours a player may pick for an image on a Display Panel: 16, 64, 256 or FULL. | (none) | DISPLAY_IMAGE_COLORS |
| 157 | rack | `accessPointDrain` | DoubleValue | defineInRange | `2.0` | [0.0 .. 1_000.0] | FE per tick an Access Point drains while its network runs. | (none) | ACCESS_POINT_DRAIN |
| 158 | rack | `tapeLibrary4uDrain` | DoubleValue | defineInRange | `3.0` | [0.0 .. 1_000.0] | FE per tick a 4U Tape Library drains while its network runs, idle. | 4U Tape Library Drain | TAPE_LIBRARY_4U_DRAIN |
| 159 | rack | `tapeLibrary6uDrain` | DoubleValue | defineInRange | `4.0` | [0.0 .. 1_000.0] | FE per tick a 6U Tape Library drains while its network runs, idle. | 6U Tape Library Drain | TAPE_LIBRARY_6U_DRAIN |
| 160 | rack | `tapeDriveBusyDrain` | DoubleValue | defineInRange | `8.0` | [0.0 .. 1_000.0] | FE per tick a Tape Library drains for each of its drives reading or writing. | Busy Tape Drive Drain | TAPE_DRIVE_BUSY_DRAIN |
| 161 | rack | `tapePickerDrain` | DoubleValue | defineInRange | `4.0` | [0.0 .. 1_000.0] | FE per tick a Tape Library drains while its picker moves. | Tape Picker Drain | TAPE_PICKER_DRAIN |
| 162 | rack | `tapeBaseTicks` | IntValue | defineInRange | `40` | [1 .. 72_000] | Ticks an LTO-6 tape takes to read or write, before the items (each newer generation is 15% faster). | Tape Base Ticks | TAPE_BASE_TICKS |
| 163 | rack | `tapeTicksPer4k` | IntValue | defineInRange | `20` | [0 .. 72_000] | Ticks an LTO-6 tape takes to read or write each 4,096 items. | Tape Ticks per 4K Items | TAPE_TICKS_PER_4K |
| 164 | rack | `tapeArchiveInterval` | IntValue | defineInRange | `200` | [20 .. 72_000] | Ticks between a Tape Library's looks for items to archive. | Tape Archive Interval | TAPE_ARCHIVE_INTERVAL |
| 165 | rack | `tapeDefaultAgeHours` | IntValue | defineInRange | `2` | [1 .. 9_999] | A new Tape Library's archive age: hours (game time) an item goes untouched before it's archived. | Default Archive Age (Hours) | TAPE_DEFAULT_AGE_HOURS |
| 166 | rack | `terminalDeskDrain` | DoubleValue | defineInRange | `1.0` | [0.0 .. 1_000.0] | FE per tick a Terminal Desk drains while its network runs. | Terminal Desk Drain | TERMINAL_DESK_DRAIN |
| 167 | rack | `terminalDeskScreenDrain` | DoubleValue | defineInRange | `2.0` | [0.0 .. 1_000.0] | FE per tick a Terminal Desk drains on top of that while its screen is on (whenever it's online). | Terminal Desk Screen Drain | TERMINAL_DESK_SCREEN_DRAIN |
| 168 | rack | `tapeDefaultHotPercent` | IntValue | defineInRange | `80` | [0 .. 100] | A new Tape Library's free-space trigger: it only archives while hot storage is fuller than this (%). | Default Hot Storage Trigger (%) | TAPE_DEFAULT_HOT_PERCENT |
| 169 | optical | `fiberSpanLoss` | BooleanValue | define | `true` | true / false | Whether long Fiber Cable runs lose lanes (span loss); an Optical Amplifier in the run starts the count again. | Fiber Span Loss | FIBER_SPAN_LOSS |
| 170 | optical | `fiberSpanThreshold` | IntValue | defineInRange | `256` | [1 .. 1_000_000] | Fiber Cable blocks a run (since the last amplifier) can be before it loses lanes. | Span Loss Threshold (blocks) | FIBER_SPAN_THRESHOLD |
| 171 | optical | `fiberSpanLossStep` | IntValue | defineInRange | `32` | [1 .. 1_000_000] | Blocks beyond fiberSpanThreshold for each lane lost (a run never drops below 1 lane). | Blocks per Lane Lost | FIBER_SPAN_LOSS_STEP |
| 172 | optical | `multiplexerChannels` | IntValue | defineInRange | `8` | [1 .. 16] | Channels an Optical Multiplexer carries over its line, each a client network of up to a Fiber Cable's lanes. | Multiplexer Channels | MULTIPLEXER_CHANNELS |
| 173 | optical | `multiplexerDrain` | DoubleValue | defineInRange | `4.0` | [0.0 .. 1_000.0] | FE per tick an Optical Multiplexer (block or rack) drains while its network runs. | Multiplexer Drain | MULTIPLEXER_DRAIN |
| 174 | optical | `amplifierDrain` | DoubleValue | defineInRange | `1.0` | [0.0 .. 1_000.0] | FE per tick an Optical Amplifier (block or rack) drains while its network runs. | Amplifier Drain | AMPLIFIER_DRAIN |
| 175 | optical | `shelfChannels` | IntValue | defineInRange | `40` | [1 .. 40] | Channels an Optical Transport Shelf carries at most. | Shelf Channels | SHELF_CHANNELS |
| 176 | optical | `chassisDrain` | DoubleValue | defineInRange | `10.0` | [0.0 .. 100_000.0] | FE per tick a modular chassis (Optical Transport Shelf, Chassis Switch, Blade Enclosure) drains on its own. | Chassis Drain | CHASSIS_DRAIN |
| 177 | optical | `chassisCardDrain` | DoubleValue | defineInRange | `2.0` | [0.0 .. 100_000.0] | FE per tick each card, blade, power supply or module in a chassis drains on top of it. | Chassis Drain per Part | CHASSIS_CARD_DRAIN |
| 178 | optical | `accessCardLanes` | IntValue | defineInRange | `48` | [0 .. 1024] | Rack-local lanes each Access Card in a Chassis Switch gives the other devices in its rack. | Access Card Lanes | ACCESS_CARD_LANES |
| 179 | optical | `routingCardRate` | IntValue | defineInRange | `128` | [1 .. 1_000_000] | Items a second each Routing Card in a Chassis Switch moves between segments (the cards add up). | Routing Card Rate (items/s) | ROUTING_CARD_RATE |
| 180 | optical | `bladeBonusPercent` | IntValue | defineInRange | `25` | [0 .. 1_000] | How much more a blade gives per rack unit than its server (Compute, Memory): % on top of the server's rate per U. | Blade Bonus per Rack Unit (%) | BLADE_BONUS_PERCENT |
| 181 | optical | `interconnectLanes` | IntValue | defineInRange | `64` | [1 .. 1024] | Lanes a Blade Enclosure's Interconnect Module gives its blades (the blades take none of the rack's). | Interconnect Lanes | INTERCONNECT_LANES |
| 182 | elcl | `spooledFileCap` | IntValue | defineInRange | `200` | [1 .. 100_000] | Spooled files a system keeps; past this the oldest is removed. | (none) | ELCL_SPOOLED_FILE_CAP |
| 183 | elcl | `messageCap` | IntValue | defineInRange | `500` | [1 .. 100_000] | Messages each user's message queue keeps; past this the oldest is removed. | (none) | ELCL_MESSAGE_CAP |
| 184 | elcl | `charsPerStorageByte` | IntValue | defineInRange | `64` | [1 .. 1_000_000] | Characters of source member text that take one byte of the network's drive storage (rounded up per member). | (none) | ELCL_CHARS_PER_STORAGE_BYTE |
| 185 | elcl | `messageChatNotice` | BooleanValue | define | `true` | true / false | Whether SNDMSG also tells online recipients in chat that a message is waiting. | (none) | ELCL_MESSAGE_CHAT_NOTICE |
| 186 | elcl | `interactiveBudget` | IntValue | defineInRange | `200` | [1 .. 1_000_000] | VM instructions an interactive job runs per tick before it yields. | (none) | ELCL_INTERACTIVE_BUDGET |
| 187 | elcl | `batchBudget` | IntValue | defineInRange | `100` | [1 .. 1_000_000] | VM instructions each batch job runs per tick before it yields. | (none) | ELCL_BATCH_BUDGET |
| 188 | elcl | `globalBudget` | IntValue | defineInRange | `2_000` | [1 .. 10_000_000] | VM instructions all jobs on the server run per tick, together. | (none) | ELCL_GLOBAL_BUDGET |
| 189 | elcl | `maxSourceLines` | IntValue | defineInRange | `5_000` | [1 .. 100_000] | Lines a source member may have. | (none) | ELCL_MAX_SOURCE_LINES |
| 190 | elcl | `maxListSize` | IntValue | defineInRange | `4_096` | [1 .. 1_000_000] | Elements a *LIST variable may hold. | (none) | ELCL_MAX_LIST_SIZE |
| 191 | elcl | `computeServerJobs` | IntValue | defineInRange | `4` | [0 .. 64] | Batch jobs each Compute Server runs at once. | (none) | ELCL_COMPUTE_SERVER_JOBS |
| 192 | elcl | `disketteBytes` | IntValue | defineInRange | `65_536` | [1 .. 16_777_216] | Bytes of library source an 8" Diskette holds (SAVLIB). | (none) | ELCL_DISKETTE_BYTES |
| 193 | elcl | `craftLogRetention` | IntValue | defineInRange | `200` | [0 .. 999] | Ended crafting jobs each network's history keeps by default (the CRFLOGRTN system value's default); past this the oldest is removed. | (none) | ELCL_CRAFT_LOG_RETENTION |
| 194 | elcl | `maxRecordsPerFile` | IntValue | defineInRange | `10_000` | [1 .. 1_000_000] | Records a physical file (CRTPF) may hold; a write past this is refused (ELC2208). | (none) | ELCL_MAX_RECORDS_PER_FILE |
| 195 | elcl | `allowFolderSync` | EnumValue<FolderSync> | defineEnum | `AUTO` | enum FolderSync: AUTO / TRUE / FALSE | Whether libraries sync with <world>/encodedlogistics/libraries/: AUTO is on in single-player and off on dedicated servers. | (none) | ELCL_ALLOW_FOLDER_SYNC |
| 196 | plc | `plcEnergy` | IntValue | defineInRange | `2` | [0 .. 1_000] | FE per tick a PLC uses while it's powered (from its own buffer, or from its network when cabled to one). | PLC Energy | PLC_ENERGY |
| 197 | plc | `plcBuffer` | IntValue | defineInRange | `1_000` | [1 .. 1_000_000] | FE a PLC's own buffer holds (filled from any face). | PLC Buffer | PLC_BUFFER |
| 198 | plc | `plcInstructionsPerTick` | IntValue | defineInRange | `50` | [1 .. 100_000] | VM instructions a PLC runs per tick; a scan that needs more carries on next tick. | PLC Instructions Per Tick | PLC_INSTRUCTIONS_PER_TICK |
| 199 | plc | `plcMaxPerChunk` | IntValue | defineInRange | `16` | [1 .. 4_096] | PLCs that may run in one chunk; another stays in STOP (PLC limit reached). | PLCs Per Chunk | PLC_MAX_PER_CHUNK |
| 200 | plc | `plcMaxPerServer` | IntValue | defineInRange | `512` | [1 .. 1_000_000] | PLCs that may run on the server at once; another stays in STOP (PLC limit reached). | PLCs Per Server | PLC_MAX_PER_SERVER |
| 201 | plc | `plcFaultOutputs` | EnumValue<PlcFaultOutputs> | defineEnum | `HOLD` | enum PlcFaultOutputs: HOLD / ZERO | What a PLC's outputs do on FAULT: HOLD keeps their last levels, ZERO drops them to 0. | PLC Outputs on Fault | PLC_FAULT_OUTPUTS |
| 202 | plc | `plcOpenToAnyone` | BooleanValue | define | `true` | true / false | Whether anyone may open a PLC that isn't cabled to a network (false: only the player who placed it, and operators). | Anyone May Open an Unnetworked PLC | PLC_OPEN_TO_ANYONE |
| 203 | signals | `signalDeviceDrain` | DoubleValue | defineInRange | `0.25` | [0.0 .. 1_000.0] | FE per tick a Cage Light, Alarm Strobe or Speaker drains from its network while it's cabled to one. | Signal Device Drain | SIGNAL_DEVICE_DRAIN |
| 204 | signals | `sirenMaxRange` | IntValue | defineInRange | `96` | [8 .. 256] | The farthest an Alarm Strobe's siren can be set to carry, in blocks. | Siren Max Range | SIREN_MAX_RANGE |
| 205 | signals | `speakerMaxRange` | IntValue | defineInRange | `64` | [8 .. 256] | The farthest a Speaker can be set to carry, in blocks. | Speaker Max Range | SPEAKER_MAX_RANGE |
| 206 | signals | `audioMaxBytes` | IntValue | defineInRange | `4` | [1 .. 64] | The largest audio file (or web audio) a Speaker plays, in MB. | Audio Max Size (MB) | AUDIO_MAX_BYTES |
| 207 | signals | `audioMaxSeconds` | IntValue | defineInRange | `180` | [1 .. 3_600] | The longest audio file (or web audio) a Speaker plays, in seconds. | Audio Max Length (s) | AUDIO_MAX_SECONDS |
| 208 | signals | `midiMaxKilobytes` | IntValue | defineInRange | `256` | [1 .. 8_192] | The largest MIDI file a Speaker plays, in KB. | (none) | MIDI_MAX_KILOBYTES |
| 209 | signals | `midiMaxSeconds` | IntValue | defineInRange | `600` | [1 .. 3_600] | The longest MIDI file a Speaker plays, in seconds. | (none) | MIDI_MAX_SECONDS |
| 210 | signals | `midiMaxNotes` | IntValue | defineInRange | `8` | [1 .. 64] | The most notes one Speaker starts at once (in one tick) from a MIDI file; the quietest beyond it are left out. | (none) | MIDI_MAX_NOTES |
| 211 | signals | `allowWebAudio` | EnumValue<WebAudioAllowed> | defineEnum | `AUTO` | enum WebAudioAllowed: AUTO / TRUE / FALSE | Whether Speakers may play audio from web URLs (fetched by each listening player's own game): AUTO is on in single-player and off on dedicated servers. | Allow Web Audio | ALLOW_WEB_AUDIO |
| 212 | signals | `webAudioHosts` | ConfigValue<List<? extends String>> | defineListAllowEmpty | `List.of()` | list elements: entry instanceof String host && !host.isBlank() (new-element default "example.com") | The hosts Speakers may play web audio from (example.com also allows its subdomains); empty: none. | Web Audio Hosts | WEB_AUDIO_HOSTS |

### TerminalSettings.SPEC: CLIENT, 16 options
File `encodedlogistics-client.toml`. The fields are private. They are read through static getters that fall back to `getDefault()` while `SPEC.isLoaded()` is false.

| # | Path | Key | Java type | Builder call | Default | Range / allowed | Comment | Config-screen label |
|---|---|---|---|---|---|---|---|---|
| C1 | terminal | `sortMode` | EnumValue<SortMode> | defineEnum | `NAME` | NAME / COUNT / MOD | Sort the grid by name, count or mod. | Sort Mode |
| C2 | terminal | `descending` | BooleanValue | define | `false` | true / false | Sort in descending order. | Sort Descending |
| C3 | terminal | `craftablesAlways` | BooleanValue | define | `true` | true / false | Show what the network can craft but has none of always (true) or only while searching (false). | Always Show Craftable |
| C4 | terminal | `height` | EnumValue<TerminalLayout.Height> | defineEnum | `FILL` | values of `client/screen/TerminalLayout.Height` | The grid's height: a fixed size, or as many rows as fit the window. | Grid Height |
| C5 | terminal | `typeTab` | EnumValue<TypeTab> | defineEnum | `ALL` | ALL / ITEMS / FLUIDS / PRESSURIZED / ENERGY | The grid's tab: everything, items, fluids, pressurized gases, or the energy summary. | (none) |
| C6 | terminal | `searchSynced` | BooleanValue | define | `false` | true / false | Keep the search field in step with JEI's search bar (needs JEI). | Sync Search with JEI |
| C7 | terminal | `phosphor` | ConfigValue<String> | define | `"*SYSVAL"` | no validator (meant to be green / amber / white from screens/crt/phosphor.json, or *SYSVAL) | The Terminal Desk's screen colour: green, amber or white (screens/crt/phosphor.json), or *SYSVAL for the system's PHOSPHOR value. | Terminal Desk Phosphor |
| C8 | toasts | `enabled` | BooleanValue | define | `true` | true / false | Show a toast when a crafting job finishes, fails or is cancelled. | Show Job Toasts |
| C9 | toasts | `jobs` | EnumValue<ToastJobs> | defineEnum | `MINE` | MINE / ALL | Whose jobs: MINE (the ones you asked for) or ALL (every job on networks you can view). | Whose Jobs |
| C10 | toasts | `completed` | BooleanValue | define | `true` | true / false | Toast when a job completes. | When Completed |
| C11 | toasts | `failed` | BooleanValue | define | `true` | true / false | Toast when a job fails. | When Failed |
| C12 | toasts | `cancelled` | BooleanValue | define | `false` | true / false | Toast when a job is cancelled. | When Cancelled |
| C13 | toasts | `minimumSeconds` | IntValue | defineInRange | `5` | [0 .. 3_600] | Only toast for jobs that ran at least this many seconds (failures always toast). | Minimum Job Duration (s) |
| C14 | toasts | `sound` | BooleanValue | define | `true` | true / false | A quiet chime with each toast (a lower tone for failures). | Chime |
| C15 | audio | `allowWebAudio` | BooleanValue | define | `false` | true / false | Play web audio from Speakers. Off by default: playing it means this game fetches the URL itself, which shows your IP address to whoever hosts it. | Allow Web Audio |
| C16 | audio | `upsAlarmSounds` | BooleanValue | define | `true` | true / false | Hear UPS alarms (the beeps on battery, the rapid low-battery beep, and the beep when power's back). Off silences every UPS for you only. | Hear UPS Alarms |

### Enum types used by Config.SPEC
- `Config.ImagesAllowed { AUTO, TRUE, FALSE }`: allowImages
- `Config.FolderSync { AUTO, TRUE, FALSE }`: allowFolderSync
- `Config.PlcFaultOutputs { HOLD, ZERO }`: plcFaultOutputs
- `Config.WebAudioAllowed { AUTO, TRUE, FALSE }`: allowWebAudio

AUTO means on when `!server.isDedicatedServer()` (see `ElclConfig.folderSync`, `DisplayImages`, `AudioFiles`).

### ElclConfig (`elcl/store/ElclConfig.java`)
- It is not a spec. It is a static facade over Config's `elcl` section: `spooledFileCap, messageCap, charsPerStorageByte, messageChatNotice, interactiveBudget, batchBudget, globalBudget, maxSourceLines, maxListSize, computeServerJobs, craftLogRetention, disketteBytes, maxRecordsPerFile`, plus `folderSync(boolean dedicated)`.
- Each accessor catches the `IllegalStateException` thrown while the config isn't loaded and returns `getDefault()`. The class comment says this is for "unit tests".
- There is no other ModConfigSpec. A grep for `ModConfigSpec.Builder` and `registerConfig` finds only these two.

## Config spec construction notes
- **Builder:** `Config` has one `private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder()`.
- **Sections:** `BUILDER.push("<name>")` and `BUILDER.pop()` inside `static { }` blocks between the field declarations. The nesting is one level deep: 13 sections, no sub-sections.
- **Fields:** each option is `public static final ModConfigSpec.<X>Value FIELD = BUILDER.comment(String...).define...(key, ...)`.
- **Builder calls used:**
  - `defineInRange`: 185
  - `define`: 17 (16 booleans, plus `displayImageColors`, a String with a validator predicate)
  - `defineEnum`: 4
  - `defineList`: 4, each with a `() -> newElement` supplier and an element predicate
  - `defineListAllowEmpty`: 2
- **Not used:** `translation()`, `worldRestart()`, `gameRestart()`.
- **Comments:** some span several lines (several String arguments to `comment`). The table joins them with a space.
- **Build:** `public static final ModConfigSpec SPEC = BUILDER.build();` comes last. Config.java has no helper methods.
- **TerminalSettings:** built differently. It starts with `new ModConfigSpec.Builder().push("terminal")` and switches sections with `BUILDER.pop().push("toasts")` and `.pop().push("audio")` inside field initialisers. Its fields are `private static final`, so reflection needs `setAccessible`.
- **Reading reflectively (for an exporter):** two ways work.
  - Walk `Config.SPEC.getSpec()` (an UnmodifiableConfig whose leaves are `ModConfigSpec.ValueSpec`, recursing by path). ValueSpec gives `getComment()`, `getRange()`, `getDefault()`, `getClazz()` and `test()`.
  - Or iterate `Config`'s public static fields of type `ModConfigSpec.ConfigValue` and call `getPath()` and `getDefault()`. Neither needs the config to be loaded.
  - The order of paths is the order of declaration.
- **Building outside a running game (e.g. in JUnit):** very likely possible, but no existing test does it.
  - For: `ModConfigSpec.Builder` is plain NightConfig plus NeoForge code, with no registry or FML dependency at build time. The test classpath has NeoForge and Minecraft (NeoGradle userdev 7.1.39, `implementation "net.neoforged:neoforge"`, JUnit 5.11.4). Unit tests already load game classes (Codecs, `CommandRegistry`). `ElclConfig`'s fallback implies `Config` has been class-initialised under unit tests.
  - Against: no `src/test` file references `Config`, `ElclConfig` or `TerminalSettings` directly.
  - `TerminalSettings` is in a client package, but its only client import is `TerminalLayout.Height` (from `client/screen`).
  - Calling `get()` on a value before the config loads throws `IllegalStateException`. `getDefault()` is safe.

## Admin: permissions, Firewall, commands, folders, limits

### Firewall permissions (`rack/RackPermission`, `rack/device/FirewallDevice`, `rack/NetworkAccess`, `rack/FirewallEvents`, `client/screen/FirewallPanel`)
- **The permissions:** the enum `RackPermission { VIEW, INSERT, EXTRACT, CRAFT, BUILD, RACK }` has six values. There is **no "CONFIGURE" Firewall permission.**
  - CONFIGURE exists only as an ELCL command authority level: `CommandDefinition.Auth { NONE, VIEW, INSERT, EXTRACT, CRAFT, CONFIGURE }`.
  - `CommandRunner` maps `CONFIGURE -> RackPermission.BUILD`.
- **What each permission covers:**
  - VIEW: see the network's items.
  - INSERT: put items in.
  - EXTRACT: take items out.
  - CRAFT: ask for crafts.
  - BUILD: place, break and configure the network's blocks and parts.
  - RACK: open Server Racks and install, remove or set up their devices.
- **The Firewall:** a 1U rack device. A network uses one Firewall; any other shows a fault and is ignored.
  - Its owner is whoever installed it, and always has every permission.
  - Its default policy is `Policy { DENY, VIEW, FULL }` (default FULL).
- **Granting players:** each listed player (at most 64, `MAX_PLAYERS`) has every permission set to INHERIT, ON or OFF.
  - INHERIT follows the default policy, except that RACK on INHERIT follows that player's BUILD.
  - Players are added in the Firewall panel's name field. It takes online players only (Tab completes; Enter or Add). Click a toggle to cycle INHERIT -> ON -> OFF; right-click a row to remove the player.
  - Only players who may BUILD on the network may change the rules. Everyone else sees the panel dimmed.
- **Enforcement** (`NetworkAccess`):
  - A network without a Firewall lets everyone do everything. A block on no network is open to everyone.
  - An offline Firewall still enforces its rules unless `firewallFailClosed=false`.
  - **Gamemaster-level operators are never stopped.**
  - `FirewallEvents` requires BUILD to place or break network blocks (on or next to a network) and to mount parts, anchors or facades.
  - Breaking a Server Rack also needs RACK (`rackBreakProtection`). `rackBreakConfirm` makes it take a sneak or a second left click.
- **Terminal OS security** (`elcl/exec/Authority`, `elcl/screen/UserService`, `SysvalService`):
  - Each player has a user profile, with a library list ELGPL ELSYS.
  - The network owner (the Firewall's owner) and server operators are `*SECOFR` class; others are `*USER`.
  - System value SECLVL: at 30, terminals on a network with a Firewall need sign-on, and the Firewall's authority applies in the OS. At 10 there is no sign-on and full authority in the OS (the Firewall still guards the blocks).
  - Changing system values (SYSNAME, DATFMT, SECLVL, QMAXJOB, LOGRTN, CRFLOGRTN, PHOSPHOR) needs *SECOFR (ELC0401).
  - Batch jobs run with their submitter's authority. PLCs run with the authority of whoever last loaded the program.
  - Error IDs: ELC0401 (authority check failed), ELC0402 (sign-on failed).

### Server and operator commands (Brigadier)
There is exactly **one** Brigadier command: `multiblock/CacheCommand`, registered in the main class with `NeoForge.EVENT_BUS.addListener(CacheCommand::register)` on `RegisterCommandsEvent`.
- `/encodedlogistics caches` shows how often each network cache was rebuilt over the last minute (`ControllerStructures.cacheReport`).
- `/encodedlogistics caches log <true|false>` turns on or off a per-minute log of rebuilds in the server log (`CacheStats.setLogging`).
- It needs `Commands.LEVEL_GAMEMASTERS`.
- A grep for `Commands.literal` and `RegisterCommandsEvent` finds nothing else.
- Everything else admin-like is an ELCL / Terminal OS command (WRKSYSVAL etc., covered by the ELCL inventory), not Brigadier.
- Vanilla `/test` runs the game tests in dev.

### Folders on disk (all under the world save, `server.getWorldPath(LevelResource.ROOT)/encodedlogistics/`)
| Folder | What reads it | Gate |
|---|---|---|
| `libraries/<SYSNAME>/<LIB>/<MEMBER>.elclp` (program source) and `<MEMBER>.pf` (PF definition) | `elcl/sync/FolderSync` | `allowFolderSync` (AUTO: single-player on, dedicated off). Tests can force it with `FolderSync.setEnabled(Boolean)`. |
| `libraries/<SYSNAME>/*.csv` | `CPYTOIMPF` / `CPYFRMIMPF` (`elcl/exec/DbCommands.stream`). Plain names matching `[A-Za-z0-9_][A-Za-z0-9_.-]{0,59}\.csv`; ELC2245 otherwise. Written to a temp file, then moved atomically. | Needs folder sync on (ELC2241). |
| `audio/<SYSNAME>/` (and `audio/` itself for Speakers on no network): OGG, MP3, MID / MIDI | `signal/AudioFiles` | Always on. Limited by audioMaxBytes, audioMaxSeconds and the midi* options. |
| `images/<SYSNAME>/*.png` | `display/DisplayImages` | `allowImages` |

How folder sync behaves:
- **Out:** saving, making, copying, renaming or restoring a member writes its file.
- **In:** the folder is polled every 40 ticks (`POLL_TICKS`, i.e. every 2 s, from `ServerTickEvent.Post`).
  - A new or changed file becomes the member. Unchanged lines keep their sequence numbers (`elcl/sync/Resequence`).
  - A new folder becomes a library.
- **Tracking:** per-member hashes in `SystemData.syncHashes`.
- **Conflicts:** when both sides changed, the last write wins, and an in-game save always counts as the later one. The losing version is kept as `<MEMBER>.elclp.bak` (or `.pf.bak`).
- **Deletes:** deleting a file never deletes its member. Deleting a member moves its file to `<LIB>/.deleted/`.
- **ELSYS** is never synced in, and is not written out either.
- **Records:** a file's records don't sync; they move through the CSV commands.

What clients keep:
- Audio bytes are held in memory only (64 MB, by hash). Web audio is fetched by each client.
- Images never reach clients; only rendered regions do.
- Nothing is written to the client's disk.

### Performance and abuse limits (all in the Config table unless noted)
- **ELCL:** `interactiveBudget` 200, `batchBudget` 100 and `globalBudget` 2,000 (instructions a tick). Also `maxSourceLines` 5,000, `maxListSize` 4,096, `maxRecordsPerFile` 10,000, `spooledFileCap` 200, `messageCap` 500, `computeServerJobs` 4, `craftLogRetention` 200, `disketteBytes` 64 KB, and `Compiler.MAX_FILES` = 5 DCLF per program (code).
- **PLC:** `plcInstructionsPerTick` 50, `plcMaxPerChunk` 16, `plcMaxPerServer` 512. The log holds 16 entries (code).
- **Display:** `displayMaxWidth` / `displayMaxHeight` 8 x 6, `displayMaxImageSize` 4,096, `displayMaxImagePixels` 16,777,216, `displayMaxImageBytes` 4 MB, `displayImageColors` 256, `displayHistorySeconds` 600. In code: MAX_REGIONS 16, touches 4 a second per player, table rows 64, flood 1,024 panels.
- **Audio:** `audioMaxBytes` 4 MB, `audioMaxSeconds` 180, `midiMaxKilobytes` 256, `midiMaxSeconds` 600, `midiMaxNotes` 8 per speaker per tick, `sirenMaxRange` 96, `speakerMaxRange` 64. In code: 24 KB chunks x 4 a tick per player, client cache 64 MB, web fetch 30 s, server hash memory 256 files, 32 notes in a sequence, 32 MIDI parts.
- **Network caches:** `/encodedlogistics caches` for diagnosis. `directoryCacheSeconds` 30.
- **Chunk loading:** `bridgeChunkLoading` (false) and `exchangeChunkLoading` (true). The ticket controller is `NetworkBridgeBlockEntity.CHUNKS`.

## Compat

### Arcforge (`compat/arcforge/`; API jar in `libs/maven/net/zagdrath/arcforge/arcforge-api/1.1.0+26.3`)
- **Build:**
  - `compileOnly "net.zagdrath.arcforge:arcforge-api:${arcforge_api_version}"` (1.1.0+26.3), resolved from the in-repo maven `libs/maven`. Players install Arcforge itself, which contains the API.
  - For dev runs and game tests, `-Parcforge_jar=<path to arcforge-2.5.0+ jar>` adds Arcforge to `localRuntime`.
- **`ArcforgeCompat.init()`**, called from the main constructor:
  - Does nothing if mod id `arcforge` isn't loaded.
  - Reads `net.zagdrath.arcforge.api.ArcforgeApi.API_VERSION_MAJOR`, `API_VERSION_MINOR` and `API_VERSION` **by reflection**, so it uses the runtime values rather than compile-time constants.
  - Needs major == 1 and minor >= 0 for machines, and minor >= 1 for gases.
  - Then loads `ArcforgeMachines` (as `MachineBridges.setAccess(...)`) and `ArcforgeGases` (as `PressurizedSources.register(...)`) by class name.
  - Logs why if any step fails. An older Arcforge (without the API, pre-2.5.0) gets a warning.
  - Test hook: `-Dencodedlogistics.arcforgeApiMajor=N` overrides the major version it sees.
- **Arcforge API packages used:**
  - `api.machine`: `MachineCapabilities`, `MachineControl`, `MachineOwner`.
  - `api.machine.resource.MachineFluids`.
  - `api.machine.settings`: `MachineSettings`, `MachineSide`, `RedstoneMode`, `SettingResult`.
  - `api.machine.status`: `CompletedOperation`, `MachineListener`, `MachineStatistics`, `MachineStatus`.
  - `api.gas`: `Gas`, `GasCapabilities` (`.ITEM` for Gas Cartridges), `GasHandler`, `GasRegistry` (`isGas`: lighter than air or `#arcforge:gases`), `GasTank`.
- **What the Small Wireless Bridge does** (`display/SmallWirelessBridgeBlock`, `machine/MachineBridge`, `machine/MachineBridges`, `machine/MachineAccess`):
  - **Placing it:** a 10x10x4 module flush on a machine's face. It is kept per machine (one per machine; a multiblock is tracked by its controller) in level saved data `encodedlogistics:machine_bridges`. Mining it, or breaking the machine, drops it. Sneak-use with an empty hand takes it off (needs BUILD while it is linked).
  - **Linking:** linked with a Link Card to a Wireless Controller, it becomes a wireless client like a Wireless Port. While the controller admits it, it is a one-lane device on that network drawing `smallWirelessBridgeDrain`. Its device name comes from the machine (e.g. `ARCCRU01` for an Arc Crusher).
  - **LED:** amber for a fault (no Access Points or no slot for it, or the machine missing, unformed or faulted). Light blue while online. Yellow and blinking when not linked or not connected.
  - **What the network can then do with the machine:**
    - Read its MachineInfo: status, current operation and outputs, energy, heat, tanks, statistics, settings.
    - Enable or disable it; set its redstone mode (ignore, high, low, pulse, throttle), side modes per side (top, bottom, left, right, back, front -> input, output, energy, ...) and auto-eject. Every write goes through Arcforge's own rules.
    - Top up its FE from the network: up to `machinePowerRate` FE/t, costing 1 / `machinePowerEfficiency`, never taking the network below `machinePowerReserve`.
    - A Gateway named in the bridge's settings feeds the machine its inputs and collects its outputs (items, fluids and gases) over the air, for crafting jobs.
    - Machine events reach ELCL as `*MCHIDLE`, `*MCHDONE` and `*MCHFAULT`.
    - Monitoring Servers and Display Panels graph `*MCHOPS` (operations a minute) and `*MCHFE`.
  - **ELCL and screens:** `WRKMCH`, `DSPMCH`, `CHGMCHCFG`, `CHGMCHSTS`, `RTVMCHLST`, `RTVMCHSTAT`, `RTVMCHSTS`.
  - Without the integration, bridges are kept but do nothing.
- **Gases:**
  - Arcforge gases are stored as PRESSURIZED resources under the source id `arcforge`, in mB under their fluid id.
  - They move through NeoForge's fluid capability.
  - Gas Cartridges work through `GasCapabilities.ITEM`.
- **Arc Crusher recipes:** 9 files in `src/main/resources/data/encodedlogistics/recipe/crushing/`, all of type `arcforge:crushing` and conditional on `"neoforge:conditions": [{"type":"neoforge:mod_loaded","modid":"arcforge"}]`. All take time 200. For each metal M (gallium, neodymium, tantalum):
  - `#c:ores/M` -> 2 M_dust, bonus 1 at 0.25, ore=true
  - `#c:raw_materials/M` -> 1 dust, bonus 1 at 0.25, ore=true
  - `#c:ingots/M` -> 1 dust, ore=false
  These are the only `mod_loaded` conditional recipes in the mod.
- **Tests:**
  - `gametest/ArcforgeGameTests`: registered only while the integration is on. They have a longer timeout, and the reload test runs in its own batch.
  - `src/test/.../compat/arcforge/ArcforgeCompatTest`: the version checks and the device-name prefixes.
  - `gametest/MaterialGameTests` covers the dusts and mentions the crusher recipes.

### JEI (`compat/jei/`; `compileOnly` jei-26.3-neoforge-api 31.8.0.53)
`@JeiPlugin EncodedLogisticsJeiPlugin` registers:
- **Categories:** one, `LithographyCategory`. Laid out like the press (photomask above, wafer on the left, additive below, result on the right, energy and time underneath). The photomask is not used up. Its recipes are the ones the server synced (`OnDatapackSyncEvent.sendRecipes(LITHOGRAPHY)`; `LithographyRecipes.clientRecipes()`).
- **Catalysts:** the Lithography Press for Lithography; the Fabricator for `RecipeTypes.CRAFTING`.
- **Info page:** on the Network Controller, `jei.encodedlogistics.network_controller.info`.
- **Transfer handlers:**
  - `FabricationTransferHandler` (crafting -> Fabrication Terminal grid). It fills from the network first, then the inventory. Missing ingredients show red; missing but craftable on the network shows blue.
  - The universal `EncoderTransferHandler` (any recipe -> Schematic Encoder ghost slots: crafting mode, or processing with up to 9 inputs and 3 outputs).
- **Ghost ingredient handlers:**
  - `RackGhostHandler` (Server Rack filter boxes: Router/L3 routes, QoS).
  - `RackElevationJei.Ghosts`, plus `.Bounds` as the GUI screen handler.
  - `GhostSlotHandler` on Port, InventoryTap, CollectorPlane, DeployerPlane, Gateway, ThresholdSensor and SchematicEncoder screens. Fluids, including Arcforge gases, set Resource Entries.
- **Runtime:** `ExternalSearch.set(...)` binds JEI's filter text for the terminals' search-sync mode (client option `searchSynced`).

### Jade (`compat/jade/`; `compileOnly maven.modrinth:jade:26.3.1+neoforge`)
`@WailaPlugin EncodedLogisticsJadePlugin`:
- **Server data providers:** `NetworkControllerProvider` (on `NetworkControllerBlockEntity`); `InfrastructureProviders.PowerInlet`, `.LithographyPress` and `.Cable`.
- **Client components:**
  - Network Controller: "Online", "0 / 32 lanes", "3x3x3 frame, 20 blocks".
  - Power Inlet: what it is receiving.
  - Segment Isolator: whether it is separating two segments.
  - Lithography Press.
  - Drive Bay.
  - Network Cable: its lanes used / carried.
- **Pick-block naming:** all cables and the part host are named after the part or facade being looked at.
- **Hidden from Jade** (they have their own HUD popups): Server Rack, Small Wireless Bridge, Disk Drive, Tape Drive.
- **Ray-trace callback:** looking at any block of a multi-block `FootprintBlock` (Tape Drive, Integrated System) shows its master block.
- Energy bars come from Jade's own FE-capability reading.

## Developer extension points
The mod has **no advertised public API package**, no ServiceLoader (no `META-INF/services`), no IMC handling, no custom NeoForge events (nothing extends `Event` or posts to `NeoForge.EVENT_BUS`), and no custom capability types. What exists:
- **Capabilities, all standard NeoForge ones** (`registry/ModCapabilities`):
  - `Capabilities.Energy.BLOCK` on the Network Controller (the whole structure's buffer), Power Inlet, Capacitor Bank, Lithography Press, PLC, and the Server Rack's power ports (any connection point feeds its network).
  - `Capabilities.Item.BLOCK` on the Lithography Press, the Gateway, and the Terminal Desk's drawer (through its pedestal half).
  - `Capabilities.Energy.ITEM` on the Handheld Terminal (charges in any FE charger) and the Energy Storage Drives.
  Other mods can use these like any NeoForge capability.
- **Public static registries.** They are Java-public, but documented only as internal device plumbing (`docs/elcl/INTERFACES.md`, written for the Midrange line and the Mainframe). There is no stability promise. Each is meant to be called once at mod setup.
  - `elcl.cmd.CommandRegistry.register(CommandDefinition)` and `.bind(name, CommandExecutor)`: add an ELCL command (schema now, executor later). Throws if a name is registered twice. The unit tests `VmTest` and `CommandRunnerTest` use it to add `TSTSAY`, `TSTSBM` and `TSTLINE`.
  - `elcl.job.JobHosts.register(DeviceSources.Source<JobHost>)`: batch job hosts.
  - `elcl.device.Diskettes.register(Source<DisketteDevice>)`
  - `elcl.device.Printers.register(Source<PrinterDevice>)`
  - `elcl.device.Displays.register(Source<DisplayDevice>)`: text displays (SNDDSPTXT).
  - `crafting.RecipeLibraries.register(Source<RecipeLibrarySource>)`
  - `storage.PressurizedSources.register(PressurizedSource)`: another mod's gases or chemicals as PRESSURIZED storage, each under its own id and unit. Arcforge's is `ArcforgeGases`.
  - `machine.MachineBridges.setAccess(MachineAccess)`: **a single slot**, set by the Arcforge integration. A second machine mod can't add itself without replacing Arcforge's.
- **Device-side interfaces** (internal): `crafting.CraftingProvider` (Fabricator, Gateway), `rack.TapeSource`, `elcl.store.StoredFileService.LiveSource`, `rack.ItemRouting.ShareSource`.
- **Audio:** **no** audio source or decoder provider interface. `client/signal/AudioDecoding.PcmSource` is package-private, and OGG and MP3 are hard-wired.
- **ELCL trigger events** (`*RSCHANGE`, `*DSPTOUCH`, `*MCHIDLE/DONE/FAULT`, `*PWRUPS`, `*PWRRESTORED`, `*CRAFTEND`, `*CRFSTALL`, ...) are internal to ELCL (`elcl/exec/ElclEvents.fire`). They are not NeoForge events.

## Tests

### Main class `EncodedLogistics.java` (what it wires up)
- **DeferredRegisters:** ModBlocks, ModItems, ModDataComponents, ModRecipeSerializers, ModRecipeTypes, ModBlockEntityTypes, ModEntityTypes, ModMenuTypes, ModCreativeTabs, ModSounds. Plus `ModCapabilities.register`, `ModNetwork.register` (payloads) and `EncodedLogisticsGameTests.register`.
- **Mod bus:** `RegisterTicketControllersEvent` -> `NetworkBridgeBlockEntity.CHUNKS`.
- **Game bus:**
  - `LevelTickEvent.Post` runs SchedulerStructures, ControllerStructures and MachineBridges for each server level.
  - `ServerTickEvent.Post` runs FolderSync, Schedules, Triggers, the StoredJobService tick, JobManager, AudioPayloads and OpticalChannels.
  - `PlayerTickEvent.Post` runs `DriveCopies.creativeTick` for creative players.
  - `OnDatapackSyncEvent` sends the lithography recipes.
  - `RegisterCommandsEvent` -> `CacheCommand`.
  - `RackElevationPayloads::onCustomClick`.
- **Static init:** `FirewallEvents.register()`, `MachineBridges.register()`, `ElclSetup.init()` (binds the built-in ELCL executors; `Midranges.register()`, `Displays.register`, ...), `ArcforgeCompat.init()`.
- **Config:** `registerConfig(LOCAL or COMMON, Config.SPEC)`.
- **Helper:** `EncodedLogistics.id(path)`.
- The client entry point `client/EncodedLogisticsClient` (`@Mod(dist = CLIENT)`) registers the config screen, the CLIENT spec, `SignalSounds.register()` and terminal/rack row sizing, and silences UPS sounds through `PlaySoundEvent`.

### Game tests (`gametest/`, run with `gradlew runGameTestServer` or `/test runall`)
- **Registration:** `EncodedLogisticsGameTests` registers everything through `RegisterGameTestsEvent`, as `FunctionGameTestInstance`s in environment `encodedlogistics:default`. Three maps: TESTS, ROUTING_TESTS (longer timeouts) and ARCFORGE_TESTS (only when Arcforge is on). About 230 `TESTS.put` entries across 46 files.
- **Files and what they cover:**
  - Network basics: Phase1-4, Cable, Infrastructure, Placement, Terminal.
  - Typed storage: TypedStorage, TypedDevice, TypedCrafting, DriveCopy.
  - Crafting: CraftingCompletion, CraftHistory, Recipe, Material.
  - Racks: Rack, Rack2, Rack3, RackCabling, RackController, RackElevation, RackSecurity.
  - Networking: Routing, Sharing, Wireless, Optical.
  - Midrange: Midrange, Interface.
  - ELCL: Elcl, ElclStore, ElclVm, BatchJob, Scheduling, Database, Security, DeviceName, FolderSync, SchematicTerminal.
  - Devices: Desk, ControlInterface, Plc, Display, Signal.
  - Arcforge.
- **Most relevant here:**
  - `PlcGameTests`.
  - `SignalGameTests`: standalone and networked lights, strobes and speakers; the ELC24xx errors; MIDI MAP across three speakers on one clock.
  - `DisplayGameTests`: merging, boot, online, DSP01, SNDDSPTXT, no signal.
  - `ControlInterfaceGameTests`.
  - `FolderSyncGameTests`: write-out, read-in with sequence numbers kept, a new folder becomes a library, ELSYS not synced, .bak, .deleted, off on dedicated.
  - `SecurityGameTests`: Firewall default deny, GUESTP view+insert, ELC0401, owner *SECOFR, SECLVL 10, sign-on.
  - `RackSecurityGameTests`.

### Unit tests (`src/test/java`, JUnit 5 via `useJUnitPlatform()`, run with `gradlew test`)
- **client:** JobToastBatchTest.
- **client/crt:** CrtGridFieldTest, DbScreensTest, EditorTest, HardwareLayoutTest, JobHistoryTest, LayoutTest, MainMenuTest, RoutingLayoutTest, SignOffTest, WrkMbrTest, WrkMchTest. The screen layouts are compared against `src/test/resources/layouts/01..29_*.txt`.
- **client/signal:** AudioDecodingTest, WebAudioFetchTest.
- **compat/arcforge:** ArcforgeCompatTest.
- **display:** DisplayImagesTest.
- **elcl:** CompilerTest, DbVmTest, LexerParserTest, PlcProgramTest, ResequenceTest, SystemDataTest, VmTest.
- **elcl/db:** DbFileTest, DdsTest, QueryCsvTest.
- **elcl/exec:** CommandRunnerTest.
- **multiblock:** ControllerFrameTest.
- **network:** LaneSolverTest, routing/RoutingDomainTest, routing/RoutingGraphTest.
- **rack:** RouterAclTest.
- **signal:** AudioFilesTest, MidiFileTest.

### Tests that compile ELCL source (to copy for docs-example compile tests)
- **`src/test/java/net/zagdrath/encodedlogistics/elcl/CompilerTest`:**
  - `static final Path EXAMPLES = Path.of("docs/elcl/examples")`. The path is relative, because Gradle runs tests from the project root.
  - `static Stream<Path> examples()` lists the `*.elclp` files.
  - `static List<String> read(Path)` is `SourceLine.split(Files.readString(path))`, with `net.zagdrath.encodedlogistics.elcl.SourceLine`.
  - `static Map<String, RecordFormat> shippedFormats()` gives ELSYS's `SystemFiles.formats()` plus `ELGPL/ITEMHIST`, compiled from `ITEMHIST.pf` with `Dds.compile`.
  - `static FileResolver shippedFiles()` is `FileResolver.of(shippedFormats())`.
  - `@ParameterizedTest @MethodSource("examples") void examplesCompileClean(Path)` asserts that `Compiler.compileTexts(read(example), shippedFiles()).diagnostics().isEmpty()` and that `program()` is not null.
  - **`examplesExist()` asserts exactly 7 examples.** Adding docs examples to `docs/elcl/examples/` breaks it unless the count is updated, so use a separate folder and test.
- **Compiler API** (`elcl/compile/Compiler`):
  - `compileTexts(List<String>)`, `compileTexts(texts, FileResolver)` and `compileTexts(texts, FileResolver, Target)`, with `Target { JOB, PLC, PLC_LOCAL }`. There are also `compile(List<SourceLine>, ...)` overloads.
  - `Result` has `program()`, `diagnostics()`, `variables()`, `ok()` and `firstError()`.
  - `FileResolver.NONE` is available.
- **`PlcProgramTest`** compiles with `Compiler.Target.PLC` and `PLC_LOCAL`. It is the pattern for PLC doc examples, e.g. checking ELC1502 for networked-only commands.
- **`VmTest.examplesStart()`** compiles and starts every example on a fake host. It reuses `CompilerTest.shippedFormats()` and `shippedFiles()`.
- **`EditorTest`** round-trips every `docs/elcl/examples/*.elclp` through the editor byte for byte.
- Both of those also iterate `docs/elcl/examples`, so new files there are picked up by three tests.
- **`ElclGameTests` / `ElclVmGameTests`** run commands on Command Entry in game, for examples that need a real network.

## Not implemented (from older docs)
Each item was checked by grepping the Java sources and resources at HEAD.

| Older doc says | Status | How verified |
|---|---|---|
| docs/plc 3 and open question 4: a `CRTPLCCTG` command / the Terminal Desk writing a cartridge from a compiled program | **Not implemented.** Cartridges are written only from PLCSTS option 4. | `grep -rn CRTPLCCTG src/main/java` finds nothing. The only cartridge use is `PlcMenu` (cartridge held, options 4/5). |
| docs/plc open question 3: a ladder-logic editor | **Not implemented** | `grep -ri ladder src/main/java` finds nothing |
| docs/plc 5: opening the PLC needs "Firewall CONFIGURE" | Wording only: it is the Firewall's **BUILD** (no CONFIGURE permission exists) | `PlcMenu.mayOpen` -> `RackPermission.BUILD` |
| docs/plc 2: timer module is a "real-time clock" | It reads the **game** clock (game ticks; aux "Day N HH:MM") | `PlcSensors` header comment |
| docs/plc 1: the program component holds "compiled CompiledProgram" | Only the source is stored; it is recompiled on load | `PlcProgram` record fields |
| docs/plc 4: `plcFaultOutputs: *HOLD / *ZERO` | Config values are `HOLD` / `ZERO`, without the asterisk | `Config.PlcFaultOutputs` |
| docs/display 3/4: a third content mode, "Script-controlled" | **Removed.** Only TEXT and DASHBOARD; old "SCRIPT" saves load as DASHBOARD | `DisplayContent.Mode` |
| docs/display 4: Layout tab "drag a region edge to split / resize; footer region count" | Splitting is by dragging in from the screen's border, and right-click removes a region (no footer count found in the class comment) | `DisplayPanelScreen` header comment |
| docs/display 4/10: needs "Firewall CONFIGURE" to configure | It is **BUILD** | `DisplayPanelMenu` / `DisplayPanelBlock` comments |
| docs/display 2: text at 1x / 2x only | Four sizes exist: *SMALL (4x6), *NORMAL, *LARGE (2x), *HUGE (3x) | `DisplayContent`, SNDDSPTXT schema |
| docs/display 6: SNDDSPWDG widget list | More exist: also `*TEXT`, `*TABLE` (FILE / QRYSLT / SORT), `*NONE`; SNDDSPGPH adds `*MCHOPS` / `*MCHFE` | `BuiltinCommands` |
| docs/display 7: Small Wireless Bridge "6 x 6 x 3 module" and "behaviour defined separately" | It is 10 x 10 x 4, and its behaviour is implemented (Arcforge only) | `SmallWirelessBridgeBlock` comment, `machine/` |
| docs/display 3: displayMaxImageSize as the only pixel limit | `displayMaxImagePixels` (16,777,216) was added too | Config, CHANGELOG 1.1.0 Security |
| docs/signals 6: a client option `neverPlayWebAudio` (default off) | **Replaced** by the opt-in client `audio.allowWebAudio` (default **false**) | `TerminalSettings`; CHANGELOG 1.1.0 "replaces the client option Never Play Web Audio" |
| docs/signals 6: "Only players with Firewall build permission can set a web source" | Not a separate check. The whole settings screen and every signal command need BUILD (CONFIGURE -> BUILD). | `SignalMenu`, `CommandRunner` |
| docs/signals 6: MP3 decoder "shaded" | JLayer is jar-in-jar (not shaded) | `build.gradle` jarJar block, README Credits |
| docs/signals open question 3: coloured light through a lighting-mod integration | **Not implemented** (vanilla light level 1-15 only) | no lighting-mod compat package; `compat/` has only arcforge, jei, jade |
| docs/signals 7: sounds.sirens.json / lang en_us.signals.json "MERGE" files | Merged; the only lang file is `en_us.json` | `ls assets/encodedlogistics/lang` |
| docs/signals 5: no MIDI | MIDI (`PLYMID`, MAP/MAPBY, ELC2408-2412) was added beyond the handoff | `BuiltinCommands`, `SpeakerBlockEntity` |
| CHANGELOG 1.1.0: client web-audio option is "under Sounds" | Correct as a screen label; the TOML section is `audio` (label "Sounds") | `TerminalSettings` push("audio"), lang `encodedlogistics.configuration.audio` |
| README: "NeoForge 26.3.0.0-beta or later" | Matches `neo_version_range=[26.3.0.0-beta,)` | gradle.properties |

Nothing in README.md is unimplemented. Its feature list matches the code. CHANGELOG's entries in this area (PLC, displays, signals, Arcforge, JEI/Jade) all have matching code.

## Open questions
1. **Config type and file:** Config.SPEC is LOCAL (formerly COMMON), not SERVER, despite the comments calling it "server config". It is one file per installation, not per world, and is not synced. Should the docs say "common/local config (`config/encodedlogistics-local.toml`)"? Client tooltips (`client/ItemInfoTooltips`, `ServerPanel`, `DriveBayScreen`, `RackRenderer`) read the **client's** local copy, so they can differ from a dedicated server's values. The exact file name under the LOCAL type is inferred from the lang keys, not confirmed by running.
2. **Building the spec in JUnit:** expected to work but untested. An exporter test should touch only `Config.SPEC` and `getDefault()`/`getSpec()`, never `get()`. Worth a quick spike.
3. **Section placement:** many display, wireless, Midrange and tape options sit under `rack`. Should the docs group them by feature while keeping the real TOML path?
4. **`phosphor`** (client) has no validator; it accepts any string. **`typeTab`** and the whole `elcl` section have no config-screen label.
5. **MachineBridges.setAccess** is a single slot. Should the docs say the Small Wireless Bridge supports Arcforge only?
6. **Extension registries** (`CommandRegistry`, `JobHosts`, `Displays`, `PressurizedSources`, ...) are public but undocumented for add-ons. Should they be listed as "unstable internal hooks" or left out of player and modder docs?
7. **Docs examples:** `CompilerTest.examplesExist` pins the example count at 7. New doc examples need their own folder and test, or that count must be raised.
8. The **Firewall panel** class comment says "five permissions", but the code loops over all six `RackPermission` values (VIEW, INSERT, EXTRACT, CRAFT, BUILD, RACK). A screenshot check would confirm six toggles.
