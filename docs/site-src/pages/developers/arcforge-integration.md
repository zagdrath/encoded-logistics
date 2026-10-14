---
title: The Arcforge integration
nav: Arcforge integration
section: developers
order: 20
summary: How compat/arcforge uses Arcforge's API - a compile-only dependency, a version check by reflection, and two classes loaded only when the check passes - as a worked example of an optional integration.
---
The mod's Arcforge support (`compat/arcforge`) is a small, complete example of an integration that stays optional: the
mod loads and works without Arcforge, with an older Arcforge, or with an Arcforge whose API has moved on, and never
touches a class that might not be there. What it does for players is on {{page integrations/arcforge}}; this page is how
it is built.

## The dependency {#dependency}

- **Compile time only.** `build.gradle` has `compileOnly "net.zagdrath.arcforge:arcforge-api:${arcforge_api_version}"`
  (`1.1.0+26.3` in `gradle.properties`). The API jar and its pom are kept in the repository under `libs/maven`, so local
  and CI builds resolve it the same way. Players install Arcforge itself, which contains the API; nothing of Arcforge's
  ships in Encoded Logistics.
- **Optional in the mod metadata.** `neoforge.mods.toml` lists `arcforge` as `type="optional"` with any version, so
  every Arcforge loads alongside, and the version question is settled at startup (below).
- **Running with Arcforge in development:** `-Parcforge_jar=<path to an Arcforge 2.5.0+ jar>` adds it to the dev runs
  and game tests (`localRuntime`).

## Three classes {#classes}

| Class | Touches Arcforge's classes? | Does |
|---|---|---|
| `ArcforgeCompat` | No | Checks Arcforge is there and its API version is one this was built for, then loads the other two by name |
| `ArcforgeMachines` | Yes | Implements the mod's `MachineAccess` with Arcforge's machine control API, for the Small Wireless Bridge |
| `ArcforgeGases` | Yes | Implements the mod's `PressurizedSource` with Arcforge's gas API, for Pressurized drives |

The two classes that use Arcforge's API are package-private and referred to only by name, so the JVM never loads them
(or Arcforge's classes) unless the check passes.

## The version check {#version-check}

`ArcforgeCompat.init()` is called from the mod's constructor:

1. **Is Arcforge loaded?** If `ModList` has no `arcforge`, it returns: nothing else happens.
2. **Read the API version by reflection.** It loads `net.zagdrath.arcforge.api.ArcforgeApi` with `Class.forName` and
   reads its `API_VERSION_MAJOR`, `API_VERSION_MINOR` and `API_VERSION` fields. Reading them by reflection matters:
   `static final` constants referenced directly would be inlined at compile time, giving the version the mod was
   built against instead of the one installed. A missing class (Arcforge before 2.5.0) logs a warning and stops.
3. **Compare.** The major version must equal the one built against (1); the minor must be at least 0 for machines and
   at least 1 for gases. A newer minor version is fine (minor versions only add); another major version turns it off,
   with a warning naming both versions.
4. **Machines.** `MachineBridges.setAccess(...)` with a new `ArcforgeMachines`, created by reflection.
5. **Gases,** if the minor version allows: `PressurizedSources.register(...)` with a new `ArcforgeGases`.

Every step catches `ReflectiveOperationException` and `LinkageError`, so an Arcforge that is there but different from
what was expected disables the integration with a log line instead of crashing the game. The system property
`-Dencodedlogistics.arcforgeApiMajor=N` overrides the major version it reads, to test the check without another
Arcforge.

```java
// The shape of ArcforgeCompat.init(), shortened.
Class<?> api = Class.forName("net.zagdrath.arcforge.api.ArcforgeApi");
int major = api.getField("API_VERSION_MAJOR").getInt(null);
int minor = api.getField("API_VERSION_MINOR").getInt(null);
if (major == 1 && minor >= 0) {
    MachineBridges.setAccess((MachineAccess) Class.forName(MACHINES_CLASS).getDeclaredConstructor().newInstance());
    if (minor >= 1) {
        PressurizedSources.register((PressurizedSource) Class.forName(GASES_CLASS).getDeclaredConstructor().newInstance());
    }
}
```

## Machines: `ArcforgeMachines` {#machines}

It implements `machine.MachineAccess` (see [Machines](page:developers/extension-points#machines)) with the Arcforge
API's `api.machine` packages:

- **Finding a machine:** the `MachineCapabilities.MACHINE_CONTROL` block capability. A bridged machine is followed with
  a `BlockCapabilityCache`, so a machine unloaded and loaded again, or a multiblock re-formed, is found again and its
  listener moved to the new instance.
- **Reading it:** `MachineControl`'s status, reason, progress, outputs, `energy()`, `heat()`, `fluids()`
  (`MachineFluids`), `statistics()` and `settings()` become the mod's own `MachineInfo`, so nothing outside this class
  sees an Arcforge type. A status added to the API after 1.0 maps to `UNKNOWN`.
- **Changing it:** enable, redstone mode, side modes and auto-eject go through Arcforge's `MachineSettings`, whose
  `SettingResult` maps onto the mod's `Result` (applied, unchanged, unsupported, invalid, rejected), plus the mod's own
  "no machine" and "not formed". Arcforge's rules decide; nothing is forced.
- **Items and fluids:** the machine's own item and fluid handlers, with its slot and tank rules, for the Gateway.
- **Events:** a `MachineListener` passes status changes and completed operations on, which the bridge turns into the
  `*MCHIDLE`, `*MCHFAULT`, `*MCHNOPWR` and `*MCHDONE` trigger events.

## Gases: `ArcforgeGases` {#gases}

It implements `storage.PressurizedSource` (see [Gases](page:developers/extension-points#pressurized)) with
`api.gas`:

- Arcforge's gases are NeoForge fluids. `GasRegistry.isGas(fluid)` (lighter than air, or tagged `#arcforge:gases`) says
  which fluids are gases, and the network stores those as pressurized resources under the id `arcforge`, in mB, under
  their fluid id. Because they are fluids, every block that moves fluids moves them; no gas capability is needed on
  the mod's blocks.
- Names and colours come from `GasRegistry`.
- Gas Cartridges are reached through `GasCapabilities.ITEM`: the cartridge's first tank becomes a `Container`.

## Data that needs Arcforge {#data}

The nine Arc Crusher recipes in `data/encodedlogistics/recipe/crushing/` are of Arcforge's type `arcforge:crushing`,
and each carries a NeoForge load condition so it loads only when Arcforge is there:

```json
"neoforge:conditions": [{ "type": "neoforge:mod_loaded", "modid": "arcforge" }]
```

They are the only conditional recipes in the mod.

## Tests {#tests}

- `ArcforgeCompatTest` (a unit test) covers the version check and the device-name prefixes without Arcforge.
- `ArcforgeGameTests` (game tests) are registered only while the integration is on, so they run only with
  `-Parcforge_jar=...`: attaching and linking a bridge, taking it off, a save and reload, a crafting job through a
  Gateway, power from the network, every machine command, and the machine events.

## Doing the same for your mod {#yours}

The pattern carries over to an integration of your own with Encoded Logistics: depend on it `compileOnly`, mark it
optional, check `ModList` before touching anything, keep every class that names its types behind a class loaded by
name, and catch `LinkageError` around it. Remember that Encoded Logistics' hooks are not a stable API (see
{{page developers/extension-points}}), so check its version too.
