# Interfaces for the Midrange line and the Mainframe

The Terminal OS and ELCL are built against these interfaces, so the Midrange line (the Midrange System, Expansion
Cabinet, Integrated Midrange System, Keypunch, Card Reader, Line Printer and 8" Diskettes) and the Mainframe only have
to implement them and register. Every one is tested against a fake implementation in the game tests; copy those when
you test the real devices.

Paths are under `src/main/java/net/zagdrath/encodedlogistics/`.

## How a device joins in

Each interface has a registry of **sources**. A source is a function from a system (`elcl.screen.ElclSystem`: a
server and a network) to the devices of that kind on that network:

```java
JobHosts.register(system -> myHostsOn(system.server(), system.network()));
```

Register once, at mod setup (next to `ElclSetup.init()`), not per block. The source is asked whenever a device is
needed, so it should list what is loaded and on that network right now (`ControllerStructures.rackDevicesServing`,
`ElclDevices.list` or your own index). A device that's unloaded simply isn't listed.

**Names.** Every device is known by its ELCL device name (`MIDRANGE01`, `PRT01`, `CARDRDR01`, `MAINFRM01`): the name
`SBMJOB HOST()`, `SAVLIB DEV()`, `PRTRPT DEV()` and the screens use. Return the name `ElclDevices` gives the device:
- **Rack devices:** named already, with their type code (add a case to `ElclDevices.code`).
- **Blocks:** add them to `ElclDevices.candidates` with a type prefix, and store the name on the block entity like the
  Terminal Desk does. Implement `applyImplicitComponents` / `collectImplicitComponents` with
  `ModDataComponents.DEVICE_NAME`, and add `copy_components` to its loot table, so the name goes with its item.

## JobHost — batch job hosts

`elcl/job/JobHost.java`, registry `elcl/job/JobHosts.java`. Compute Servers are built in (`JobHosts.computeServers`).

| Method | Meaning |
|---|---|
| `String name()` | Its device name. Jobs remember the host by name, so it must stay the same while the host stands. |
| `int capacity()` | Batch jobs it runs at once. Read every tick, so it can change (an Expansion Cabinet added). |
| `boolean resumes()` | Whether its jobs carry on after it's unloaded, the server restarts or it loses power. `false`: they end with ELC0310. |
| `boolean online()` | Loaded and powered, able to run jobs now. A non-resuming host going offline ends its jobs (ELC0310); a resuming one pauses them until it's back. |

The job service (`elcl/job/StoredJobService`) does the rest: the job queue (`*JOBQ`), QMAXJOB, priorities, ELC0301 when
a system has no host, saving each running job's VM with the system, and after a restart resuming the jobs of hosts that
resume and ending the others.

| Device | Implements | Values |
|---|---|---|
| Midrange System | `JobHost` | `capacity()` 1, or 2 with an Expansion Cabinet attached; `resumes()` false |
| Expansion Cabinet | nothing itself | adds 1 to its Midrange System's `capacity()` |
| Integrated Midrange System | `JobHost` | `capacity()` 4; `resumes()` false |
| Mainframe | `JobHost` | `capacity()` 32 per CPC Drawer; `resumes()` **true** (its jobs are journaled) |

Tested by `gametest/BatchJobGameTests` with `FakeHost`: `batch_queue_and_hosts` (Midrange 1 then 2, Integrated 4) and
`batch_restart` (Mainframe resumes, Midrange ends with ELC0310).

## DisketteDevice, Diskette, LibraryImage — 8" Diskettes (SAVLIB / RSTLIB)

`elcl/device/DisketteDevice.java`, `Diskette.java`, `LibraryImage.java`, registry `elcl/device/Diskettes.java`.

**`DisketteDevice`** is anything with a diskette drive:

| Method | Meaning |
|---|---|
| `String name()` | Its device name: what `DEV()` names. |
| `boolean online()` | Offline: ELC1302. |
| `List<Diskette> mounted()` | The diskettes in it now. Empty: ELC1310. SAVLIB writes to the first; RSTLIB reads from the first that holds the library. |

**`Diskette`** is one diskette:

| Method | Meaning |
|---|---|
| `String label()` | Its volume label, shown in the completion message. |
| `long capacity()` | Bytes of source it holds. By default `ElclConfig.disketteBytes()` (65,536 unless configured, OS.md 4's 64 KB). |
| `List<LibraryImage> libraries()` | The libraries saved on it. |
| `void write(LibraryImage)` | Saves a library, replacing one of the same name. SAVLIB calls it only once the image fits (ELC1311 otherwise). |

**`LibraryImage`** is the library as saved: its members, with their sequence numbers and change dates, and its
programs, as source compiled again on restore. `save()` / `load()` turn it into NBT, so a diskette item can keep it in
a `CustomData` component.

SAVLIB and RSTLIB are in `elcl/exec/OsCommands.java`. RSTLIB creates the library (owned by the user restoring it) or
replaces its members and programs. It needs `*CHANGE` on an existing library (ELC0401), never writes to ELSYS
(ELC0205), and the network must have storage for the members (ELC0207).

| Device | Implements |
|---|---|
| Midrange System | `DisketteDevice` (its drive) |
| Integrated Midrange System | `DisketteDevice`, if it has a drive |
| Card Reader | `DisketteDevice` |
| 8" Diskette (item) | `Diskette`, over its item data |
| Keypunch | nothing in ELCL |

Tested by `gametest/InterfaceGameTests.saveRestore` with `FakeDrive` / `FakeDiskette`.

## PrinterDevice — the Line Printer

`elcl/device/PrinterDevice.java`, registry `elcl/device/Printers.java`.

| Method | Meaning |
|---|---|
| `String name()` | Its device name (PRT01). `*DFT` means the first online printer. |
| `boolean online()` | Offline: ELC1302. |
| `boolean hasPaper()` | No paper: ELC1306. |
| `void print(String title, List<String> lines)` | Prints a spooled file or report. Only called while it's online with paper. |

Users:
- `PRTRPT RPT(*INV|*DEV|*JOBLOG|*SPLF) DEV()` (`elcl/exec/ModCommands`).
- Work with Output 6=Print (`StoredSpoolService.print`).

Both answer ELC1301 with no printer and ELC1307 when it's printed.

| Device | Implements |
|---|---|
| Line Printer | `PrinterDevice` (paper from its own inventory) |

Tested by `gametest/InterfaceGameTests.printing` and `ElclVmGameTests` (`modCommands`, `examples`) with `FakePrinter`.

## RecipeLibrarySource — a diskette job library for Schedulers

`crafting/RecipeLibrarySource.java`, registry `crafting/RecipeLibraries.java`.

| Method | Meaning |
|---|---|
| `String name()` | Its device name. |
| `boolean online()` | Only an online library's recipes count. |
| `List<Schematic> recipes()` | The recipes it offers. |

An online library's recipes count as the network's own (`CraftRequests.schematics`): they're craftable, planned like
any other (STRCRAFT, the terminals' Craft) and shown to Schedulers.

Each step of a job still runs on a `CraftingProvider` that **accepts** the recipe. `CraftingProvider.accepts`
defaults to the recipes the provider holds, and the Fabricator, Gateway and Fabrication Server check it when offered a
task. So the Midrange System can either:
- be a `CraftingProvider` itself, accepting its library's recipes, or
- have the providers next to it accept them, by overriding `accepts`.

| Device | Implements |
|---|---|
| Midrange System | `RecipeLibrarySource` (recipes from the diskette in its drive); and, if it runs them, `CraftingProvider` with `accepts` |

Tested by `gametest/InterfaceGameTests.recipeLibrary`: its recipe becomes craftable and plans complete.

## DisplayDevice — text displays (SNDDSPTXT)

`elcl/device/DisplayDevice.java`, registry `elcl/device/Displays.java`. This isn't part of the Midrange line. It's here
for the Status Display, NOC Video Wall and Rack Console screen when they come.

| Method | Meaning |
|---|---|
| `String name()` | Its device name (NOCDSP01). |
| `boolean online()` | Offline: ELC1302. |
| `int lines()` | Lines it shows. `LINE(n)` past them: ELC0004. |
| `void write(int line, String text, boolean clear)` | One line (`0` for `*NEXT`), clearing first if asked. |

Tested by `ElclVmGameTests` with `FakeDisplay`.
