# Inventory: materials, storage, crafting and automation, Midrange

Source root: `src/main/java/net/zagdrath/encodedlogistics` (abbreviated `J/`), resources `src/main/resources` (`R/`).
All numbers are from code/JSON at commit 24c1f57. Config paths are `<section>.<key>` in the common config built by
`J/Config.java` (sections: controller, lanes, power, storage, autocrafting, reach, lithography, facades, rack, optical,
elcl, plc, signals). Note: the Midrange, Disk Drive, Tape Drive, Tape Reel and job-toast options all live in the
**`rack`** section, not a section of their own.

---

## Features

### 1. Ores and worldgen

| Ore block (registry id) | Drops | Feature (vein size) | Placement | Air-exposure discard |
|---|---|---|---|---|
| `neodymium_ore`, `deepslate_neodymium_ore` | `raw_neodymium` | `ore_neodymium`, size 8, stone + deepslate replaceables | count 8 per chunk, **trapezoid y -32 to 48** | 0.0 |
| `tantalum_ore`, `deepslate_tantalum_ore` | `raw_tantalum` | `ore_tantalum`, size 6, stone + deepslate | count 4, **uniform from bottom (y -64) to y -8** | 0.4 |
| `deepslate_gallium_ore` (no stone variant) | `raw_gallium` | `ore_gallium`, size 4, deepslate only | count 2, **trapezoid bottom (y -64) to y -32** | 0.6 |

- Biomes: all three via NeoForge biome modifiers `R/data/encodedlogistics/neoforge/biome_modifier/add_ore_*.json`,
  type `neoforge:add_features`, biomes `#minecraft:is_overworld`, step `underground_ores`. Overworld only.
- Loot (`R/data/encodedlogistics/loot_table/blocks/*ore*.json`): silk touch drops the ore; otherwise one raw metal with
  Fortune `ore_drops` bonus and explosion decay.
- Mining: stone ores strength 3/3, deepslate 4.5/3; all ores in `minecraft:needs_iron_tool` (iron pickaxe).
- Tags: `c:ores`, `c:ores/<metal>`, `c:ores_in_ground/stone|deepslate` (blocks and items).
- Because tantalum's range is y -64 to -8, the stone variant only appears where stone dips below -8 (mostly deepslate
  in practice). Gallium is the deepest and rarest (2 veins of 4).
- Files: `J/registry/ModBlocks.java` (stoneOre/deepslateOre), `R/data/encodedlogistics/worldgen/{feature,placed_feature}/ore_*.json`.

### 2. Raw metals, dusts, ingots, storage blocks

- Items: `raw_neodymium`, `raw_tantalum`, `raw_gallium`; `neodymium_ingot`, `tantalum_ingot`, `gallium_ingot`;
  `neodymium_dust`, `tantalum_dust`, `gallium_dust`.
- Ingots: smelting (furnace) and blasting from raw metal, from ore (`#c:ores/<metal>`) and from dust
  (`#c:dusts/<metal>`): 6 recipes per metal, 18 in all (9 smelting + 9 blasting; the 10th smelting recipe is the
  Silicon Boule, the 10th blasting one is Silica).
- Dusts: only made by **Arcforge's Arc Crusher** (`arcforge:crushing` recipes in `R/data/encodedlogistics/recipe/crushing/`,
  each gated by `neoforge:conditions` `mod_loaded arcforge`): ore -> 2 dust (+1 at 25%), raw -> 1 (+1 at 25%),
  ingot -> 1; 200 ticks. Without Arcforge dusts are uncraftable (but still smeltable if obtained).
- Storage blocks (new in Unreleased): `neodymium_block` ("Block of Neodymium"), `tantalum_block`, `gallium_block`,
  `ferrite_block`. 9 ingots (by `#c:ingots/<metal>`; Ferrite by item) -> block (3x3 shaped), block (by
  `#c:storage_blocks/<name>`) -> 9 (shapeless). Strength 5/6, need iron tool, `c:storage_blocks[/<name>]` tags; the
  three metal blocks (not Ferrite) are in `minecraft:beacon_base_blocks`.
- Tags: `c:raw_materials/<metal>`, `c:ingots/<metal>`, `c:dusts[/<metal>]`.
- Files: `J/registry/ModItems.java` ("Phase 2/3" blocks), `J/registry/ModBlocks.java` (`storage(...)`), recipes in `R/data/encodedlogistics/recipe/`.

### 3. Fabrication-chain materials (all plain `Item`s unless noted)

| Item (id) | Recipe (type) | Used for |
|---|---|---|
| Silica `silica` | blast `minecraft:sand` (blasting only) | Silica Blend |
| Silica Blend `silica_blend` | 4 silica + `#minecraft:coals` (shapeless) | Silicon Boule |
| Silicon Boule `silicon_boule` | smelt Silica Blend | wafers |
| Silicon Wafer `silicon_wafer` | stonecut 1 boule -> 4 | Logic Die, 8K Storage Die, Doped Silicon |
| Doped Silicon `doped_silicon` | wafer + redstone (shapeless) | Memory/Processor Die, Memory Photomask |
| Ferrite `ferrite` | iron ingot + redstone (shapeless) | everywhere (photomasks, drives, heatsink, ...) |
| Copper Foil `copper_foil` | stonecut 1 copper ingot -> 2 | substrate, capacitors, fluid drives |
| Fiberglass `fiberglass` | glass + 2 string -> 2 | substrate, fiber cable |
| Solder Paste `solder_paste` | copper ingot + `#c:slime_balls` -> 2 | substrate, capacitors, dies |
| Circuit Substrate `circuit_substrate` | fiberglass + foil + solder | nearly every device |
| Logic Photomask (stack 1) | GFG/FRF/GFG: glass, ferrite, redstone | Logic Die |
| Storage Photomask (stack 1) | GFG/FFF/GFG: glass, ferrite | 8K Storage Die |
| Memory Photomask (stack 1) | GFG/FDF/GFG: glass, ferrite, doped silicon | Memory Die |
| Processor Photomask (stack 1) | GFG/FAF/GFG: glass, ferrite, gallium ingot | Processor Die |
| Logic Die `logic_die` | lithography: wafer + redstone + logic mask, 4,000 FE / 100 t | tier-1 devices |
| 8K Storage Die `storage_die_8k` | lithography: wafer + ferrite + storage mask, 4,000 FE / 100 t | 8K drives, higher dies |
| Memory Die `memory_die` | lithography: doped silicon + ferrite + memory mask, 6,000 FE / 140 t | tier-2 devices |
| Processor Die `processor_die` | lithography: doped silicon + gallium ingot + processor mask, 8,000 FE / 180 t | tier-3 (autocrafting, servers) |
| Heatsink `heatsink` | CCC/CFC: 5 copper ingot + 1 ferrite | Scheduler Core, Thread Unit, servers |
| Tantalum Capacitor `tantalum_capacitor` | tantalum ingot + foil + solder -> 2 | energy drives, Gateway, ports, capacitor bank, UPS |

Photomasks stack to 1 and are never consumed (`ModItems` comment; `LithographyRecipe` consumes only wafer and additive).

### 4. Lithography Press (`lithography_press`)

- Block: `J/block/LithographyPressBlock.java`, BE `J/blockentity/LithographyPressBlockEntity.java`, menu
  `J/menu/LithographyPressMenu.java`, screen `J/client/screen/LithographyPressScreen.java`, JEI
  `J/compat/jei/LithographyCategory.java`. Recipe: `IGI / FRF / ICI` = iron, glass, ferrite, redstone, circuit substrate
  (no dies needed: no bootstrap loop).
- Standalone FE machine: **no network needed**; accepts FE on every face. Buffer `lithography.pressCapacity` = 32,000 FE;
  input `lithography.pressMaxInput` = 512 FE/t.
- Slots: 0 photomask (never used up), 1 wafer, 2 additive, 3 output. Automation: top face feeds the photomask slot,
  sides feed wafer/additive (wafers go to the wafer slot first), bottom extracts output only (photomask never comes out by automation).
- While a recipe matches and the output has room it spends the recipe's `energy` evenly over `time` ticks
  (`energyOnTick`), pausing (keeping progress) when the buffer is empty; at the end consumes 1 wafer + 1 additive.
- Faces the player when placed; ACTIVE state lights the UV lamp/window (light level); comparator reads progress 0-15.
- Mining: strength 3/6, pickaxe, needs correct tool.
- The mod has no FE generator of its own (only Power Inlet / Capacitor Banks / creative bank), so the press needs FE from
  another mod (see Open questions).

### 5. Recipe type `encodedlogistics:lithography`

- `J/registry/ModRecipeTypes.java` (`LITHOGRAPHY`, the only custom RecipeType), serializer in `ModRecipeSerializers`,
  record `J/recipe/LithographyRecipe.java`, input `J/recipe/LithographyInput.java`, lookup `J/recipe/LithographyRecipes.java`
  (client copy synced on datapack sync, for slots and JEI).
- JSON: `{"type":"encodedlogistics:lithography","wafer":<ingredient>,"additive":<ingredient>,"photomask":<ingredient>,"result":{"id":..,"count":..},"energy":4000,"time":100}`
  (`energy` default 4000, `time` default 100, min 1). Marked special, not placeable, recipe book category CRAFTING_MISC.
- 4 recipes: logic_die, storage_die_8k, memory_die, processor_die (table above).

### 6. Storage Dies and Storage Drives

- Tiers (`J/storage/StorageTier.java`): 8K, 32K, 128K, 512K, 2M (bytes = K x 1,024). All five are REGISTERED.
- Dies: `storage_die_<8k|32k|128k|512k|2m>` (`StorageTierItem`, tooltip names tier). 8K by lithography; higher dies are
  shaped crafts combining **4 dies of the tier below**:
  - 32K: `RDR/DLD/RSR` redstone, 4x 8K die, logic die, solder
  - 128K: `NDN/DMD/NSN` neodymium ingot, 4x 32K die, memory die, solder
  - 512K: `NDN/DMD/NTN` neodymium, 4x 128K, memory die, tantalum capacitor
  - 2M: `GDG/DPD/GTG` gallium ingot, 4x 512K, processor die, tantalum capacitor
- Drives: 4 resource types (`J/storage/ResourceType.java`) x 5 tiers = **20 drive items**:
  `storage_drive_<tier>` (items), `fluid_storage_drive_<tier>`, `pressurized_storage_drive_<tier>`, `energy_storage_drive_<tier>`
  (`J/item/StorageDriveItem.java`). Recipes:
  - Item 8K/32K: `IFI/IDI/ICI` iron, ferrite, die, substrate; 128K/512K/2M: `TFT/IDI/ICI` (tantalum ingots on top).
  - Fluid: `IGI/FDF/III` iron, glass, copper foil, die.
  - Pressurized: `IGI/TDT/III` iron, glass, tantalum ingot, die.
  - Energy: `ICI/CDC/ICI` iron, tantalum capacitor, die.
  - Tag `encodedlogistics:storage_drives`.
- Byte model (`J/storage/DriveCapacity.java`, `DriveStorage.room`): a medium of B bytes; each stored type reserves
  B/128 bytes; at most `storage.driveTypeLimit` (default **63**, range 1-4096) types per drive "whatever its size".
  Units per byte: ITEM 8 items, FLUID 1,000 mB (a bucket), PRESSURIZED 1,000 (the gas mod's unit; Arcforge mB),
  ENERGY 1,000 FE. Free = B - types x B/128; room = free x unitsPerByte - stored.

  | Tier | Bytes | Items raw (1 type / 63 types) | Fluid/gas buckets raw (1 type) | Energy FE |
  |---|---|---|---|---|
  | 8K | 8,192 | 65,536 (65,024 / 33,280) | 8,192 (8,128) | 8,192,000 |
  | 32K | 32,768 | 262,144 (260,096 / 133,120) | 32,768 (32,512) | 32,768,000 |
  | 128K | 131,072 | 1,048,576 (1,040,384 / 532,480) | 131,072 (130,048) | 131,072,000 |
  | 512K | 524,288 | 4,194,304 (4,161,536 / 2,129,920) | 524,288 (520,192) | 524,288,000 |
  | 2M | 2,097,152 | 16,777,216 (16,646,144 / 8,519,680) | 2,097,152 (2,080,768) | 2,097,152,000 |

  (Energy drives don't reserve per-type bytes: `EnergyDrives` stores FE = tier bytes x 1,000; comment "an 8K drive holds 8,192,000 FE".)
- A drive holds only its own resource type. Item/fluid/pressurized contents live in world data `DriveStorage`
  (`J/storage/DriveStorage.java`, saved with the overworld) under the drive's `drive_id`; the item caches `drive_stats`.
  Energy drives keep FE on the item (`drive_energy`), so they keep charge out of a holder and work as **battery items**
  (item energy capability, up to `storage.energyDriveMaxTransfer` = 65,536 FE/t). In a holder on a network an energy
  drive is part of the network's energy pool (no own rate limit): incoming FE fills drives first, then controllers, then
  Capacitor Banks; drain comes out of banks and drives before controllers (`J/multiblock/ControllerStructures.java` comment).
- Pressurized drives store gases only when a `PressurizedSource` is registered (Arcforge integration,
  `J/compat/arcforge/ArcforgeGases.java`); without Arcforge "nothing is pressurized" (`J/storage/PressurizedSource.java`).
  Arcforge gases are NeoForge fluids but stored as PRESSURIZED keys, never mixed with FLUID.
- Stacking/copies (`J/storage/DriveCopies.java`): new drives stack to 64; a drive with an id stacks to 1; an empty drive
  in a player's inventory reverts to blank; a copy (creative middle-click, Ctrl+pick-block of a holder) gets a new id with
  a copy of the contents.
- Fill light (`DriveStats.light`): green <50%, yellow to 75%, orange <100%, red full (fill = max of bytes% and types%).
  Energy drives: charge light (full / >=75 / >=50 / >=25 amber / off) plus a "charging" light.
- Holders: Drive Bay (10), Disk Drive (1), NAS/SAN rack devices and Blade Enclosure storage blades (rack agent). "Every
  drive holder takes every type."

### 7. Drive Bay (`drive_bay`)

- `J/block/DriveBayBlock.java`, `J/blockentity/DriveBayBlockEntity.java`, `J/menu/DriveBayMenu.java`,
  `J/client/screen/DriveBayScreen.java`, model `J/client/model/DriveBayModel.java`.
- 10 drive slots (two columns of five). A network device: 1 lane; cables connect on every face except the front.
  Drain `storage.driveBayDrain` 2.0 FE/t + `storage.driveBayDrainPerDrive` 0.5 FE/t per drive.
- Its drives are network hot storage at priority 0 (`J/storage/DriveView.java`). Front shows each drive's sled + status light; dark when offline.
- GUI: the ten slots laid out like the front, each with a fill bar in its light colour, Online/Offline in title strip.
- Recipe: `ISI/NCN/ILI` iron, substrate, network cable, chest, logic die.

### 8. Network storage behaviour (hot/cold)

- `J/storage/NetworkStorage.java`: network storage = drives in online Drive Bays, Disk Drives, NAS/SAN, plus inventories
  faced by online Inventory Taps (hot tier); fluids and gases go to their drive types/taps; energy is the pool, not storage.
- Insert order: highest priority first, drives before taps on a tie, and within a priority to places already holding that
  item. Extract: lowest priority first, taps before drives. Shared storage (another segment through a Share route) comes after the network's own.
- Crafting jobs waiting for outputs claim incoming items first (any path: Gateway, Ingress Port, tap, terminal).
- Cold tier (`J/storage/ColdTier.java`): tapes in Tape Libraries and the Midrange Tape Drive's reel (items only).
  Terminals show hot + cold; taking more than is hot triggers a **recall** of the rest from tape (taken once back).
  The crafting planner counts cold items as stored, as recalls (shown light-blue with a tape marker).
- Each drive type remembers the tick it was last touched; archiving moves untouched items to tape.

### 9. LTO tapes, LTO Tape Drive, tiering (storage-side facts; rack agent covers Tape Library devices)

- Tapes `lto_tape_6 .. lto_tape_10` (`J/storage/TapeGeneration.java`, `J/item/LtoTapeItem.java`), tag
  `encodedlogistics:lto_tapes`. Capacity in the same byte model: LTO-6 8M bytes (8 x 2^20), LTO-7 32M, LTO-8 128M,
  LTO-9 512M, LTO-10 2G (2^31). So LTO-6 = 67,108,864 items raw. Type limit: `storage.driveTypeLimit` (63) applies
  to tapes too (DriveCapacity default), with B/128 reserved per type. Each generation is 15% faster (speed 0.85^n; LTO-10 ~0.52x).
- Recipes: LTO-6 shapeless (2 ferrite, foil, string, substrate). Upgrades via custom type `encodedlogistics:tape_upgrade`
  (`J/recipe/TapeUpgradeRecipe.java`, shapeless that copies the old tape's `drive_id`, keeping contents):
  6->7 (+2 ferrite + logic die), 7->8 (+2 neodymium + memory die), 8->9 (+2 neodymium + tantalum capacitor),
  9->10 (+2 gallium + processor die).
- `lto_tape_drive` (item, stack 16): goes into Tape Library bays (rack). Recipe: substrate, logic die, memory die, neodymium, iron.
- Tapes stack/copy like drives (new stack 64; with id stack 1; empty reverts).
- Tape Library configs (rack section): `tapeLibrary4uDrain` 3.0, `tapeLibrary6uDrain` 4.0, `tapeDriveBusyDrain` 8.0,
  `tapePickerDrain` 4.0, `tapeBaseTicks` 40, `tapeTicksPer4k` 20, `tapeArchiveInterval` 200, `tapeDefaultAgeHours` 2,
  `tapeDefaultHotPercent` 80. Logic: `J/rack/TapeTier.java`, `J/rack/device/TapeLibraryDevice.java` (rack agent).
  Tape Libraries accept only `LtoTapeItem` (not Tape Reels).

### 10. Resource Entry (`resource_entry`)

- `J/item/ResourceEntryItem.java`. A stand-in item (stack 1) for a fluid or gas plus an amount, in component
  `resource_entry`: used in ghost filter slots, Processing Schematic inputs/outputs, the Gateway's stock row, job
  holdings. Name = the resource's name; tooltip shows amount, type and source mod. "Not obtainable and never stored"
  (storage never stores it as an item; `NetworkStorage` skips it). No recipe.

### 11. Schematic Card and Encoded Schematics

- Schematic Card `schematic_card`: blank; recipe substrate + memory die + paper -> 4. (Also an ingredient of Link Card,
  Schematic Encoder, Schematic Terminal.)
- `encoded_schematic_crafting` "Encoded Schematic (Crafting)" and `encoded_schematic_processing` (both `SchematicItem`,
  stack 1), component `schematic` (`J/crafting/Schematic.java`). No crafting recipe: made only by the Schematic Encoder.
  - Crafting: up to 9 grid slots, one item each, plus the recipe result; carried out by a Fabricator / Fabrication Server.
  - Processing: up to 9 input stacks and up to 3 outputs (`Schematic.INPUTS = 9`, `PROCESSING_OUTPUTS = 3`); entries may
    be fluids/gases (Resource Entries with amounts); carried out by a Gateway.
  - Tooltip lists makes/takes; holding Shift shows output as icon (`J/client/model/SchematicOutputModel.java`).
- There is no recipe or action that wipes an encoded schematic back to a blank card; the encoder can overwrite one.

### 12. Schematic Encoder (cable part `schematic_encoder`)

- `J/part/SchematicEncoderPart.java`, `J/menu/SchematicEncoderMenu.java`, `J/client/screen/SchematicEncoderScreen.java`,
  JEI `J/compat/jei/EncoderTransferHandler.java`. Recipe: Fabrication Terminal + processor die + schematic card.
- A terminal (uses `storage.terminalLanes` 1, `storage.terminalDrain` 0.5 FE/t) with the Access Terminal's network grid
  above an encoding section. Modes: Crafting / Processing (button 0), Clear (1), Encode (2).
- 3x3 ghost grid; Crafting mode shows the matched crafting-table result (read-only), items only, one each.
  Processing mode adds 3 ghost outputs; ghost amounts 1-64 for items, fluids/gases up to 1,000,000 (default 1,000 mB, a
  bucket); scroll/right-click to change (Shift x10; fluids by 100 mB, 1,000 with Shift); right-click an empty slot to pick
  a fluid/gas from a list; filled buckets/tanks/gas containers set fluid entries.
- Real slots: blank cards in, encoded card out. Encode writes to a blank card (or rewrites the card in the encoded slot);
  putting an encoded card in the encoded slot loads it for editing. JEI fills the ghost slots.
- Shift-click: blank cards and encoded cards to their slots, anything else into the network.

### 13. Fabrication Terminal (cable part `fabrication_terminal`)

- `J/part/FabricationTerminalPart.java`, `J/menu/FabricationTerminalMenu.java`, `J/client/screen/FabricationTerminalScreen.java`,
  JEI `FabricationTransferHandler`. Recipe: Access Terminal + crafting table + memory die.
- Access Terminal + a real-item 3x3 crafting grid (kept in the part, drops with it). Taking the result crafts and refills
  emptied grid slots from the network; Shift-click result crafts as many as fit; Clear returns the grid to the network
  (or the player); JEI fills from network, then player inventory. Terminal lanes/drain as above.

### 14. Fabricator (`fabricator`)

- `J/block/FabricatorBlock.java`, `J/blockentity/FabricatorBlockEntity.java`, `J/menu/FabricatorMenu.java`,
  `J/client/screen/FabricatorScreen.java`, queue `J/crafting/CraftQueue.java`.
- 9 slots for Crafting Schematics + 2 Throughput Module slots. Device: 1 lane, `autocrafting.fabricatorDrain` 1.0 FE/t;
  cables on every face but the front window.
- Takes every craft its Schedulers offer and runs them side by side; each costs `autocrafting.fabricatorEnergyPerCraft`
  50 FE and takes `autocrafting.fabricatorCraftTicks` [20, 10, 5] ticks with 0/1/2 Throughput Modules (pauses offline).
  Returns result + remainders (buckets) to the job; broken mid-craft gives inputs back to the jobs.
- GUI: schematics, progress arrow, module slots; Working/Idle. ACTIVE lights while working.
- Recipe: `IPI/CTC/IKI` iron, processor die, substrate, crafting table, piston.

### 15. Gateway (`gateway`)

- `J/block/GatewayBlock.java`, `J/blockentity/GatewayBlockEntity.java`, `J/menu/GatewayMenu.java`, `J/client/screen/GatewayScreen.java`.
- 9 Processing Schematic slots; a stock row of 9 ghost items with amounts; item handler on every face with 9 buffer
  slots (stocked items: neighbours may take, not insert) and 9 intake slots (accept only outputs its runs are waiting for).
- A run pushes its inputs into adjacent machines (through the face touching the Gateway), then waits: outputs are pushed
  back in by machines or pulled from neighbours every 10 ticks (`PULL_INTERVAL`, touching face first, then others, e.g.
  a furnace's bottom). Only one run pushes at a time; up to 64 runs outstanding (`MAX_RUNS`). Fluid/gas entries go through
  neighbours' tanks (`J/storage/ResourceIO.java`).
- Stock: every 20 ticks each buffer slot is topped up from / trimmed back into the network to its amount.
- Also feeds Arcforge machines over the air: a Small Wireless Bridge whose Gateway setting names this Gateway (GATEWAY01).
- Stonecutter neighbour (1.2.1): a one-item-in / one-item-out stonecutting run is cut by the Gateway itself; excess goes to the network.
- Device: 1 lane, `autocrafting.gatewayDrain` 1.0 FE/t. ACTIVE while it moved items in the last 20 ticks.
- Recipe: `HLH/CTC/IMI` hopper, logic die, memory die, substrate, tantalum capacitor, iron (no processor die).

### 16. Scheduler multiblock (`scheduler_core`, `job_buffer`, `thread_unit`)

- `J/block/SchedulerCoreBlock.java`, `J/block/SchedulerBlock.java`, `J/block/ThreadUnitBlock.java`,
  `J/blockentity/SchedulerCoreBlockEntity.java`, `J/multiblock/SchedulerStructures.java`, menu/screen `SchedulerCore*`,
  model `J/client/model/SchedulerModel.java`. Block tag `encodedlogistics:scheduler_parts`.
- Forming rules: touching scheduler blocks form one structure when they **fill their bounding box (solid cuboid)**, no
  axis longer than `autocrafting.schedulerMaxSize` (7, range 1-16), and contain **exactly one Core**. FORMED blockstate +
  connected model. Rechecked when blocks are placed/broken.
- Capacity: threads = `autocrafting.schedulerBaseThreads` (1) + `autocrafting.threadUnitThreads` (1) per Thread Unit; job
  memory = `autocrafting.schedulerBaseMemory` (4,096) + `autocrafting.jobBufferMemory` (16,384) per Job Buffer. Job memory
  = items across the job's ingredient tree; a job is accepted when its memory fits what's free.
- One network device: cables connect to any block, the Core uses one lane, drains `autocrafting.schedulerDrain` 2.0 +
  `autocrafting.schedulerDrainPerBlock` 0.5 per block. Using any block opens the Core's screen. Thread Units glow while jobs run.
- Core GUI: running jobs (icon, name x amount, progress, cancel X), the queue, threads (gold) and memory (mint) meters,
  last 5 finished jobs (`CraftLog.RECENT`), with hint to Work with Jobs' history; if unformed, says why. Click a job for Job Status.
- Breaking the Core drops what jobs hold; those jobs fail.
- Recipes: Core `FPF/CHC/FPF` ferrite, processor die, substrate, heatsink; Job Buffer `MFM/MCM` memory dies, ferrite,
  substrate; Thread Unit `FHF/FPF/FTF` ferrite, heatsink, processor die, tantalum capacitor.

### 17. Crafting jobs (planning, running, threads, spreading)

- Request: in any terminal (Access, Fabrication, Handheld, consoles) middle-click or Ctrl-click a craftable entry, or
  click one the network has 0 of (`J/client/screen/AbstractTerminalScreen.java`) -> Craft Amount screen
  (`CraftAmountScreen`, typed or +/-; Next/Enter) -> Craft Plan screen (`CraftPlanScreen`) -> Start -> Job Status screen.
- Plan (`J/crafting/CraftPlanner.java`): ingredient tree with Have / Make / Miss per item (foldable), missing count, recall
  time for tape items; Scheduler selector cycling "Auto" or a specific Scheduler / Rack Scheduler / Midrange System; Start
  only with nothing missing and a host with room. Leftovers from the job's own crafts are used first, then storage, then
  schematics. **Schematic fallback** (1.2.0): multiple schematics for an item are tried in network order; the first whose
  whole tree can be had wins; else the first (to show what's missing). Limits: MAX_DEPTH 32, MAX_LINES 2,000, MAX_WORK 50,000 nodes.
- Auto host (`J/crafting/CraftRequests.java`): first with a free thread and room for the memory, else first with room (queues).
  Starting takes hot items out of storage at once and starts recalls for cold ones.
- Hosts (`J/crafting/JobHost.java`): Scheduler Core, the network's Rack Scheduler pool (Compute/Memory Servers;
  `rack.computeServerThreads` 2, `rack.memoryServerMemory` 32,768), Midrange System.
- Running (`J/crafting/JobRunner.java`): each thread keeps `autocrafting.threadCrafts` (default 1; 0 = unlimited) crafts in
  flight; a job wants ceil(crafts left / threadCrafts) threads; every queued job gets one thread first (taking one from
  the job with most if none free), free threads then go round running jobs one at a time; jobs give threads back near the
  end. Up to 64 dispatches per job thread per tick.
- **Least-busy spreading** (commit 24c1f57): each run is offered to the least busy provider holding the schematic
  (`CraftingProvider.load()` = tasks taken and not returned; ties keep network order), so identical Fabricators/Gateways/
  Fabrication Servers/Blade Enclosures share work instead of the first ones filling. Files: `J/crafting/CraftingProvider.java`, `JobRunner.java` ~L299-321.
- Hold/release (Midrange 3=Hold, Schematic Terminal, Terminal OS): held job starts no new steps; saved across restarts.
- Stalls (`J/crafting/JobStalls.java`): no progress for `autocrafting.jobStallSeconds` (30) -> stalled, with reason:
  tape, missing_machine, unpowered, output_full, gateway_blocked, ingredients. Fires ELCL `*CRFSTALL`.
- End (`J/crafting/JobEvents.java`): completed / failed (host broken) / cancelled. Cancelling returns held items to storage
  (drops what doesn't fit; fluids/gases that can't fit are lost). Toasts to requester (and optionally other viewers) gated by
  `rack.allowJobToasts` (true) and per-player client settings (on, whose jobs, which ends, min duration; `JobToasts`,
  `JobToastBatch` batches same-kind ends). Offline requesters get a Terminal OS message queue entry.

### 18. Job Status screen

- `J/client/screen/JobStatusScreen.java` (`screens/job_status.json`): item and overall progress, steps two per row each with
  progress (scroll), items still awaited from tape, **Cancel job** button; polls the server; says finished when the job is
  gone. Opened when a job starts and from the Scheduler Core screen; closing returns there.

### 19. Crafting history

- `J/crafting/CraftLog.java`: every ended job recorded (number like C0042, item, asked/made, outcome + failure reason,
  requester user and ELCL job/schedule/trigger, scheduler and position, start/end clock, duration, consumed items).
  Kept per network's Terminal OS data; retention = system value CRFLOGRTN, default `elcl.craftLogRetention` 200 (0-999).
  Read by Work with Jobs history view, RTVCRFSTS, RTVCRFLOG, `*CRAFTEND`, Scheduler and server panels' Recent list
  (`J/client/screen/RecentJobs.java`, 5 most recent).

### 20. Schematic Terminal (cable part `schematic_terminal`; also Rack Schematic Console)

- `J/part/SchematicTerminalPart.java`, `J/menu/SchematicTerminalMenu.java`, `J/client/screen/SchematicTerminalScreen.java`,
  server `J/schematic/SchematicTerminalService.java`, `SchematicHolders.java`, `SchematicData.java`. Recipe: Access Terminal + schematic card + memory die.
- Tabs: **Schematics** (every crafting provider on the network: Fabricators named FAB01.., Gateways GATEWAY01 > machine,
  Fabrication Servers FABSRV01, Midrange Systems (diskette recipes read-only); take/put schematics, Sneak-click inventory
  schematic into selected holder, right-click Locate (outline 10 s + chat coords) / Rename; badges parallel, conflict,
  Gateway problem; search, sort, filters, height) and **Jobs** (jobs with number, output, status, progress, scheduler,
  time; click for step tree with reasons and Locate; right-click cancel (confirmed) / hold / release; filters by scheduler,
  requester, active-only or also last 20 ended).
- Permissions (Firewall): VIEW to see, BUILD to move/rename, CRAFT to cancel/hold others' jobs (own always). One lane, lit online.

### 21. Machine bridge (machine/ package: Small Wireless Bridge for Arcforge machines)

- `J/machine/MachineBridge(s).java`, `MachineAccess.java`, `MachineInfo.java`, `MachineSeries.java`, item
  `J/item/SmallWirelessBridgeItem.java`. Goes on a face of an Arcforge machine (not placed as a normal block), linked to a
  Wireless Controller by Link Card; one-lane wireless device; `rack.smallWirelessBridgeDrain` 1.0. Optional "power from
  network": `rack.machinePowerEfficiency` 0.9, `rack.machinePowerRate` 1,000 FE/t, `rack.machinePowerReserve` 0.25.
  Gateways can feed bridged machines over the air. Works only with Arcforge loaded and compatible; device names like ARCCRU01.
  (Wireless/rack agents may cover in more depth.)

### 22. Midrange line

General: all Midrange screens are green-screen text panels (`J/client/crt/CrtMachineScreen.java` and per-machine screens)
with F-keys; no slots; HUD/hotbar hidden while open (`EncodedLogisticsClient.CRT_HIDDEN`). Items go in by using them on
the block; sneak-use with empty hand takes things out. Peripherals work beside a Midrange System / Expansion Cabinet /
Integrated system or cabled to its network (`J/midrange/Midranges.java`). Device names via ELCL (MIDRANGE01, KEYPUNCH01,
CARDRDR01, PRT01, DISK01, TAPE01), kept on the item (`device_name`). Multi-block footprints: `J/midrange/FootprintBlock.java`,
shapes `R/data/encodedlogistics/shapes/midrange.json`.

**Midrange System** (`midrange_system`, tier 1; `J/midrange/MidrangeSystemBlock.java`, `MidrangeSystemBlockEntity.java`, screen `MidrangePanelScreen` "MRCTL")
- One block (18 px wide). **Is its network's controller**: lane source of `rack.midrangeLaneFaces` 4 faces' worth
  (x `lanes.perControllerFace` 32 = 128 lanes) and energy buffer of `rack.midrangeEnergyBlocks` 4 controller blocks'
  worth (x `controller.energyPerBlock` 25,000 = 100,000 FE). Any other controller on its network = conflict (E8).
  Drain `rack.midrangeDrain` 4.0 (+ `rack.expansionCabinetDrain` 1.0 with cabinet).
- Holds 1 8" Diskette (slot A; slot B with Expansion Cabinet); diskette recipes become network recipes (`RecipeLibrarySource`).
- Job host: threads 1, max job memory 64 (2/128 with cabinet). Crafts its diskettes' recipes itself:
  `rack.midrangeStepTicks` 40 ticks/craft, `rack.midrangeCraftEnergy` 20 FE/craft; other steps go to network Fabricators/Gateways.
- ELCL batch-job host (1 job; 2 with cabinet) and diskette drive for SAVLIB/RSTLIB.
- States: off when network not running; IPL `rack.midrangeIplTicks` 100 ticks on coming online or F7; run, busy, attn.
  Status codes Cn (IPL), A6 (running, A6b busy), E1 no diskette, E2 job waits for items, E8 controller conflict, E9 network offline.
- MRCTL: drives list (4=Eject, 5=Display recipes, 8=Make default library), jobs (3=Hold, 4=End, 6=Release), F7=IPL,
  F10=Hold queue, F11=Release. Recipe `SLS/FIC/ L ` substrate, logic die, ferrite, iron, copper foil (no memory/processor die).

**Expansion Cabinet** (`expansion_cabinet`; `ExpansionCabinetBlock.java`): placed directly beside a Midrange System along
its width, same facing, one per system: +1 thread, +1 diskette slot (B), +1 batch job, max job 128. Elsewhere does nothing.
Recipe: substrate, logic die, iron, ferrite.

**Integrated Midrange System** (`integrated_midrange`, tier 2; `IntegratedMidrangeBlock.java`, screen "IMCTL")
- 3x2 footprint. Controller with `rack.integratedMidrangeLaneFaces` 6 (192 lanes) and `rack.integratedMidrangeEnergyBlocks` 20 (500,000 FE);
  cabled at the master's bottom and back only. Drain `rack.integratedMidrangeDrain` 8.0.
- 4 threads, max job 512, 4 batch jobs, takes a **Diskette Magazine** (4 diskettes), crafts at `rack.integratedMidrangeStepTicks` 25.
- Click zones: console hood/keyboard opens the Terminal OS (like a Terminal Desk); left body/magazine opens IMCTL
  (4=Remove from magazine). Recipe: Midrange System + Expansion Cabinet + Terminal Desk + 2 memory dies.

**Card Punch** (`keypunch`, display name "Card Punch"; `KeypunchBlockEntity.java`, `KeypunchScreen`): 1x2. Type item names
into a 3x3 grid (F4 item list; nothing consumed); F6=Punch writes the crafting recipe onto a blank Punch Card into its
stacker (component `punched_recipe`); F13 clears. Blank cards go in its hopper. Needs an online Midrange System on its network.
Recipe: substrate, logic die, crafting table, iron.

**Card Reader** (`card_reader`; `CardReaderBlockEntity.java`, `CardReaderScreen`): 8-card hopper + diskette slot. F6=Read
adds each card's recipe to the diskette (replacing one that makes the same item), max 8 recipes per diskette
(`DisketteData.MAX_RECIPES`); cards stay in the hopper. Also an ELCL diskette drive (SAVLIB/RSTLIB DEV(CARDRDR01)). Needs an
online Midrange System. Recipe: substrate, logic die, hopper, iron.

**Line Printer** (`line_printer`; `LinePrinterBlockEntity.java`, `LinePrinterScreen`): 1x2. Prints reports as Printouts, 1
paper per page: 1 inventory, 2 newest job's log, 3 device list, 4 spooled file (name or *LAST); F6=Print; ELCL PRTRPT and
Work with Output 6=Print (DEV(PRT01) or *DFT). Out of paper mid-report: pages so far come out, the rest waits (ELC1306).
Works on any running network (no Midrange System needed). Recipe: substrate, logic die, ink sac, iron, paper.

**Disk Drive** (`disk_drive`; `DiskDriveBlock(Entity).java`, `DiskDriveScreen` DSKDRV): holds one Storage Drive of any tier and
any type, served as hot storage exactly like a Drive Bay slot. 1 lane (not from front). Spin-up `rack.diskSpinUpTicks` 40
before readable; 4=Unload / sneak-use spins down `rack.diskSpinDownTicks` 20 then ejects to the player (or out the front);
5=Display contents. Drain `rack.diskDriveDrain` 1.5. No Midrange System needed. Recipe: substrate, logic die, 2 ferrite, 3 iron, blue dye.

**Tape Drive** (`tape_drive`; `TapeDriveBlock(Entity).java`, `TapeDriveScreen` TAPDRV): 1x3 footprint (2.5 blocks tall).
One Tape Reel as cold storage (same tier as Tape Libraries). Threading `rack.tapeLoadTicks` 100; recalls read back into
hot storage; archives every `rack.tapeArchiveInterval` (200) while hot storage > `rack.tapeDefaultHotPercent` (80%), the
oldest item untouched for `rack.tapeDefaultAgeHours` (2 h). Read/write takes `rack.tapeDriveBaseTicks` 60 +
`rack.tapeDriveTicksPer4k` 20 per 4,096 items. 7=Rewind (`rack.tapeRewindTicks` 40); 4=Unload rewinds then ejects.
Drain `rack.tapeDriveDrain` 2.0 (x2 while busy). 1 lane. Recipe: substrate, logic die, ferrite, 3 iron, glass pane, blue dye.

**Tape Reel** (`tape_reel`, stack 1; `J/item/TapeReelItem.java`): `rack.tapeReelItems` 65,536 items, `rack.tapeReelTypes`
1,024 types (no per-type byte reservation); contents in DriveStorage under `drive_id`; volume serial VOL001-VOL999; tooltip
"n / 65,536 items". Recipe: ferrite + 2 paper + 2 iron nuggets -> **1**.

**Punch Card** (`punch_card`): 4 paper -> 8. Blank and punched are the same item (punched carries `punched_recipe`).

**8" Diskette** (`diskette_8in`, stack 16): ferrite + paper + copper foil. Holds `diskette_recipes` (label, up to 8 recipes,
ELCL libraries saved by SAVLIB; `elcl.disketteBytes` 65,536 bytes of library source).

**Diskette Magazine** (`diskette_magazine`, stack 1; `DisketteMagazineItem.java`): 4 diskettes, bundle-style insert/remove;
`magazine_contents`. Recipe: 2 iron + paper. For the Integrated system.

**Printout** (`printout`, stack 1; `J/item/PrintoutItem.java`, `J/midrange/Printout.java`, client `J/client/printout/*`): pages
of 56 cols x 48 rows (42 body rows), max 100 pages, inks normal/light/red. Held shows the page like a map; first page in item
frames; use opens reader (Page Up/Down, wheel). Only from the Line Printer (no recipe).

---

## Recipe types and counts

Custom types/serializers registered (`J/registry/ModRecipeTypes.java`, `ModRecipeSerializers.java`):
- RecipeType: only `encodedlogistics:lithography`.
- Serializers: `lithography`, `tape_upgrade` (a ShapelessRecipe subclass; uses the vanilla crafting RecipeType),
  `rack_elevation_copy`, `rack_elevation_clear` (special crafting recipes; `J/recipe/RackElevationRecipes.java`; rack agent).

Recipe JSON files: 277 in `R/data/encodedlogistics/recipe/` + 9 in `recipe/crushing/` = **286**.

| Type | Count |
|---|---|
| `minecraft:crafting_shapeless` | 130 |
| `minecraft:crafting_shaped` | 114 |
| `minecraft:smelting` | 10 (9 ingots + silicon boule) |
| `minecraft:blasting` | 10 (9 ingots + silica) |
| `arcforge:crushing` (conditional on Arcforge) | 9 |
| `encodedlogistics:lithography` | 4 |
| `encodedlogistics:tape_upgrade` | 4 |
| `minecraft:stonecutting` | 2 (copper foil, silicon wafer) |
| `minecraft:crafting_dye` | 1 (Swivel Chair) |
| `encodedlogistics:rack_elevation_copy` | 1 |
| `encodedlogistics:rack_elevation_clear` | 1 |

Of the 244 shaped/shapeless, 82 are colour/variant families outside this area: 63 cable recipes (network 13 shaped;
dense 12 shaped + 13 shapeless; fiber 12 shaped + 13 shapeless), 16 Cage Lights (shaped), 3 Sirens (shapeless).
Items with no recipe at all: creative_capacitor_bank, all ore blocks, raw metals, encoded schematics (encoder), printout
(printer), resource_entry, part_host (internal); dusts only via Arcforge crushing.

---

## Data components (`J/registry/ModDataComponents.java`, 19 total, all persistent + network-synced)

| Id | Type | Purpose |
|---|---|---|
| `facade_target` | BlockState | block a Cable Facade copies; none = blank |
| `device_name` | String | script name of Terminal Desk / Control Interface / Midrange devices etc. kept on the item when broken |
| `plc_program` | PlcProgram | PLC program in the PLC item / written EEPROM Cartridge |
| `drive_id` | UUID | Storage Drive / LTO tape / Tape Reel id; contents in DriveStorage; given on first mount |
| `drive_stats` | DriveStats(bytesUsed, bytesTotal, typesUsed) | cached fill for tooltip, bays, status light |
| `schematic` | Schematic | Encoded Schematic recipe (crafting) or inputs/outputs (processing) |
| `punched_recipe` | Schematic | a punched Punch Card's crafting recipe |
| `diskette_recipes` | DisketteData | 8" Diskette label, up to 8 recipes, SAVLIB libraries |
| `printout` | Printout | Printout pages and metadata |
| `magazine_contents` | ItemContainerContents | diskettes in a Diskette Magazine (up to 4) |
| `link_address` | LinkAddress | written Link Card's Network Bridge / P2P endpoint |
| `handheld_network` | NetworkIndex.NetworkRef | network a Handheld Terminal is linked to |
| `handheld_link_state` | HandheldLinkState | whether a Handheld is in range (icon) |
| `wireless_link` | UUID | Wireless Controller a Handheld was linked to |
| `energy` | Integer | FE in a Handheld Terminal's battery |
| `resource_entry` | ResourceEntryItem.Entry(key, amount) | Resource Entry's fluid/gas and amount |
| `drive_energy` | Long | FE in an Energy Storage Drive |
| `rack_device_state` | CustomData | rack device settings while out of a rack |
| `rack_template` | RackTemplate | Rack Elevation's plan; none = blank |

---

## Progression (deduced from recipes)

1. **Vanilla start**: iron, copper, redstone, sand, glass, string, slime balls, coal. Make Ferrite (iron+redstone), Copper
   Foil (stonecutter), Fiberglass, Solder Paste -> **Circuit Substrate**. Silica (blast sand) -> Silica Blend -> Silicon
   Boule (smelt) -> 4 Silicon Wafers (stonecutter).
2. **Lithography Press** (substrate + ferrite + iron/glass/redstone) powered by external FE. Logic and Storage Photomasks
   (glass/ferrite/redstone) -> **Logic Die** and **8K Storage Die**.
3. **First network**: Network Controller (diamond + 2 logic dies + substrates), Power Inlet, Network Cable, Access
   Terminal, Drive Bay, 8K/32K Storage Drives, 8K Fluid Drives (copper foil only). Midrange line is also reachable here:
   Midrange System, Expansion Cabinet, Card Punch, Card Reader, Punch Cards, Diskettes, Line Printer, Disk Drive, Tape Drive,
   Tape Reel need only substrate/logic die/ferrite/vanilla - giving **early autocrafting** (8 recipes per diskette, crafted
   by the Midrange System itself) before processor dies, and the Midrange System doubles as the network controller.
4. **Neodymium and tantalum** (y -32..48; y <= -8): Tantalum Capacitors, Doped Silicon -> Memory Photomask -> **Memory Die**.
   Unlocks 128K/512K dies, tantalum-topped item drives, Pressurized and Energy drives, Capacitor Bank, ports and modules,
   Fabrication Terminal, Schematic Card, Schematic Terminal, Gateway, Job Buffer, LTO tapes 7-9.
5. **Gallium** (deepslate y <= -32): Processor Photomask -> **Processor Die**; Heatsink (copper+ferrite). Unlocks Fabricator,
   Scheduler Core, Thread Unit, Schematic Encoder (Fabrication Terminal + processor die + card), 2M die/drives,
   LTO-10, servers and rack gear.
6. **Full autocrafting**: Schematic Encoder writes Encoded Schematics -> Fabricators (crafting) and Gateways (processing,
   next to machines) -> Scheduler multiblock (Core + Job Buffers + Thread Units) or rack Compute/Memory Servers; request from
   any terminal (middle/Ctrl-click), manage via Schematic Terminal and Job Status.

---

## Not implemented (from older docs) - leave out of docs

Verified by grepping the code/resources noted.
1. **docs/midrange/HANDOFF.txt section 6: "tape_reel ... -> 2"** - the actual `R/data/encodedlogistics/recipe/tape_reel.json`
   yields 1 Tape Reel (no count). Document 1.
2. **HANDOFF section 2: optional block entity renderers** spinning the Disk Drive's pack and turning the Tape Drive's reels -
   not implemented: `EncodedLogisticsClient.registerRenderers` registers BERs only for server_rack, swivel_chair,
   display_panel, plc (grep `registerBlockEntityRenderer`). Only the ACTIVE lamp/state changes.
3. **HANDOFF section 3 screen name "CARDPUNCH"** - code panel is "KEYPUNCH" (`KeypunchScreen`), block display name "Card Punch".
4. **HANDOFF section 9 tooltip "Printed on PRINTER01"** - the printer's device type is `PRT` (`LinePrinterBlockEntity.TYPE`),
   so names are PRT01; lang `tooltip.encodedlogistics.printout.from` = "Printed on %s, %s".
5. **HANDOFF section 8 open question 2 "whether Tape Libraries also accept reels"** - they don't:
   `rack/device/TapeLibraryDevice.java` only accepts `LtoTapeItem`; no reference to `TapeReelItem` in `rack/`.
6. **HANDOFF section 8 open question 3 (Disk Drive limited to 8K-128K)** - not done; it accepts any tier and any type
   (`DiskDriveBlockEntity` comment "one Storage Drive (any tier)").
7. **docs/crt/HANDOFF.txt frame numbers**: panel 502 x 270, grid at (11, 19), "soft glow (35%), light scanlines" - code
   `J/client/crt/CrtDisplay.java` uses a 514 x 282 panel, grid at (17, 25) with padding, and explicitly "No glow, scanlines,
   CRT bezel...". `textures/gui/crt/scanlines.png` exists but no Java references it (grep "scanlines" finds only that comment).
   The bezel/vignette retirement itself IS done.
8. **CHANGELOG 1.1.0 "threadRuns (8)"** - superseded: 1.2.0 replaced it with `autocrafting.threadCrafts` default 1; no
   `threadRuns` key in Config.java (the Java field is still named `THREAD_RUNS`, key `threadCrafts`).
9. **CHANGELOG 1.0.0 "two material tiers"** is vague; actual: three die tiers (logic, memory, processor) and three ores.
   (Not wrong, just don't reuse.)
Things in the older docs that ARE implemented as written (OK to document): Midrange controller sizes 128 lanes/100k FE and
192/500k (match config defaults), tape reel 65,536/1,024, Disk Drive spin 40/20 ticks, Tape Drive load 100 and 3 s + 1 s per
4K, Printout held/item frame/reader, hotbar hidden on CRT screens, Integrated click zones, device types DISK/TAPE.

---

## Open questions

1. The mod has no FE generator (only Power Inlet, Capacitor Bank, creative bank); confirm the docs should tell players to
   bring FE from another mod (Lithography Press and every network need it).
2. Dusts are only obtainable with Arcforge installed; worth stating explicitly.
3. `storage.driveTypeLimit` (63) also limits LTO tapes (DriveCapacity default not overridden by TapeGeneration) - intended?
   The config comment says "one Storage Drive".
4. Encoded schematics can't be reset to blank cards (no recipe/action) - intended?
5. ModItems comment "TODO: the recipes are a first pass" (Phase 1) - recipes may change before docs are final.
6. Tantalum stone ore only exists below y -8 where stone remains; practically mostly deepslate.
7. Config `rack.allowJobToasts`, Midrange, Disk/Tape Drive options sit in the `rack` section - docs should cite that path.
8. Item/fluid drive sled codes/priority: Drive Bay drives are fixed priority 0 (no per-bay priority setting found); racks have per-device priorities (rack agent).
