# Inventory: Networks and connectivity

Scope: `network/`, `network/routing/`, `part/`, `block/cable/`, `wireless/`, `optical/`, the relevant `net/` payloads, and the blocks, block entities, menus and screens for the controller, power, cables, parts, bridges, wireless and optical gear.

How this was checked: by reading the code (registries in `registry/ModBlocks.java` and `ModItems.java`, the block and BE classes, `LaneSolver`, `NetworkDiscovery`, `ControllerStructures`, `Config.java`). Recipes come from `src/main/resources/data/encodedlogistics/recipe/*.json`. The config file is registered as type LOCAL (COMMON before FML 12.0.8) in `EncodedLogistics.java:93`, so it is `config/encodedlogistics-common.toml` (or the LOCAL name). Option paths below are `section.key = default`.

Java paths are relative to `src/main/java/net/zagdrath/encodedlogistics/`.

---

## Features

### Network Controller (block)
- **Registry:** block and item `encodedlogistics:network_controller`. BE `NetworkControllerBlockEntity`. Menu `NetworkControllerMenu`, screen `client/screen/NetworkControllerScreen`.
- **What it does:** it is the source of a network. Touching controller blocks merge into one structure, which shares an FE buffer and hands out lanes.
- **Shape rule** (`multiblock/ControllerFrame`): a connected group is valid when its bounding box is at most `maxSize` on every axis and the group is exactly the edge set of that box (every position with at least two of its three coordinates on the box's min or max). So these are valid: a single block, a line, a 2x2x2 cube, a flat ring, a hollow frame. A filled face (for example a solid 3x3 wall) is INVALID_SHAPE. A box over the limit is TOO_LARGE. A missing edge block is INVALID_SHAPE. Flood fill stops at 4096 blocks (`MAX_GROUP`).
- **Blockstates:** `formed` is true for a valid structure of more than one block, which joins the textures. `state` is offline, online or error. Online gives light level 4.
- **Use:**
  - Right-click a valid structure to open the Network screen. It shows an energy gauge, the status line, lanes used and capacity, Stored / Usage / Generation, the structure ("3x3x3 frame, 20 blocks" or "Single block"), and a scrolling grid of device types (count, drain, and how many are missing a lane).
  - On an invalid shape you get a red overlay message and no screen.
  - The comparator output is 0 to 15 from the stored energy of the whole network: `1 + stored*14/capacity`, or 0 when empty.
- **Energy:**
  - Each block buffers `controller.energyPerBlock = 25000` FE and accepts `controller.maxReceivePerBlock = 4096` FE/t on its faces.
  - The capability on every face is insert-only and covers the whole structure: FE fills that block first, then spills into the others.
  - The structure drains `controller.drainPerBlock = 2.0` FE/t per block while it runs.
  - Generation is the FE received, averaged over 20 ticks.
- **Lanes:** each controller face linked to a non-controller node adds `lanes.perControllerFace = 32` to the capacity. Faces between controller blocks don't count. Example: one block with cables on all six faces has a capacity of 192, though each cable still carries only its tier's lanes.
- **Size limit:** `controller.maxSize = 7` (range 1 to 32).
- **Statuses** (`network/NetworkStatus`): ONLINE, NO_POWER (buffer empty), INVALID_SHAPE, TOO_LARGE, CONFLICT, ADHOC_OVERLOAD, FAILOVER (rack controller pair switching over).
- **Jade** (`compat/jade/NetworkControllerProvider`): "Online", "0 / 32 lanes", "3x3x3 frame, 20 blocks".
- **Recipe:** shaped `ICI/LDL/ICI` (iron, circuit substrate, logic die, diamond).
- **Sources:** `block/NetworkControllerBlock.java`, `blockentity/NetworkControllerBlockEntity.java`, `multiblock/ControllerFrame.java`, `multiblock/ControllerStructures.java` (tickStructure, around lines 434-638), `network/NetworkSnapshot.java`, `menu/NetworkControllerMenu.java`.

### Power Inlet
- **Registry:** `encodedlogistics:power_inlet`. BE `PowerInletBlockEntity`.
- **What it does:** FE pushed into its port goes straight into its network's energy pool.
- **Placement and connections:** placed facing the player, like a dispenser (FACING = the opposite of the nearest look direction). FE goes in only through the FRONT face. Cables connect on the other five faces.
- **States:** `powered` is true while it has received FE within the last 20 ticks.
- **Cost:** it uses no lanes and drains nothing (it is a BlockNode with 0 drain).
- **Limits:**
  - `power.inletMaxInput = 16384` FE/t.
  - `power.inletBuffer = 0`. If above 0, it holds that much FE while the network is full and passes it on when there is room.
  - It accepts nothing when it isn't on a controller's network, or when the network is in an error state (fill refuses on isError).
- **Order of filling:** Energy Storage Drives first, then controllers, then Capacitor Banks, then a rack standby controller (`ControllerStructures.fill`, around line 1802). The block's own comment says "controllers first, then banks", which is stale.
- **Jade:** "Receiving N FE/t".
- **Recipe:** shaped `ICI/FRF/INI` (iron, circuit substrate, copper foil, redstone block, network cable).
- **Sources:** `block/PowerInletBlock.java`, `blockentity/PowerInletBlockEntity.java`.

### Capacitor Bank
- **Registry:** `encodedlogistics:capacitor_bank`. BE `CapacitorBankBlockEntity`. Menu `CapacitorBankMenu`, screen `CapacitorBankScreen`.
- **What it does:** on a controller's network it is part of the energy pool. Cables connect on all six faces. It uses no lanes and drains nothing.
- **Energy rules:**
  - The network's drain comes out of banks and Energy Storage Drives first, then controllers.
  - Fills go to drives, then controllers, then banks.
  - With `power.capacitorDirectIO = true`, every face also takes and gives FE directly. If false, only the side-less capability is exposed (for Jade).
- **Numbers:** `power.capacitorCapacity = 2000000` FE. `power.capacitorMaxTransfer = 16384` FE/t in and out, applied to the network and direct IO alike.
- **GUI:** no slots. It shows the energy bar, the fill %, and Stored / Input / Output / Capacity (20-tick averages). It syncs every 10 ticks.
- **Gauge and comparator:** blockstate `fill` (0 to 4, `ceil(4*stored/cap)`) is updated at most once a second. The comparator reads 0 to 15.
- **Charging a Handheld Terminal:** use and hold the terminal against the bank. It charges at `reach.handheldChargeRate = 4096` FE/t.
- **Recipe:** shaped `ITI/FRF/ITI` (iron, tantalum capacitor, copper foil, redstone block).
- **Sources:** `block/CapacitorBankBlock.java`, `blockentity/CapacitorBankBlockEntity.java`, `menu/CapacitorBankMenu.java`.

### Creative Capacitor Bank
- **Registry:** `encodedlogistics:creative_capacitor_bank`. Epic rarity. No recipe and no loot table. Strength -1, so it can't be broken in survival.
- **What it does:** it is always full and reports 2^40 FE (`CREATIVE_FE`). It gives all the FE asked of it and voids all it's offered, on the network and on every face, with no per-tick limit.
- **Order on the network:** controllers are filled before banks, so it only voids what they can't take.
- **Screen:** shows "Infinite" (figures sent as -1).
- **Sources:** `block/CreativeCapacitorBankBlock.java`, plus the creative branch of `CapacitorBankBlockEntity`.

### Segment Isolator
- **Registry:** `encodedlogistics:segment_isolator`. No block entity.
- **What it does:** an inline housing that splits a network in two. It connects only on the two ends of its AXIS, which is set from the clicked face, like a log. Network discovery adds it to the graph but doesn't walk past it (`passesThrough=false`). Each side is a separate network: lanes, storage, devices and energy don't cross.
- **Cost:** drains `power.isolatorDrain = 0.5` FE/t. Because it is a node on both networks, each side pays it.
- **States:** `active` (amber light) while both ends are attached to network blocks.
- **Use with segments:** sneak-use a Link Card on the half you want to store that side's segment. The card can then be used on a rack switch, a Trunk Port or an Optical Multiplexer channel.
- **Jade:** "Isolating two segments" or "Idle".
- **Recipe:** shaped ` I /NFN/ I ` (iron, network cable, ferrite).
- **Sources:** `block/SegmentIsolatorBlock.java`, `item/LinkCardItem.java` (segment()).

### Cables: Network Cable, Dense Network Cable, Fiber Cable (13 colours each)
- **Registry:** 39 blocks with items: `[<dye>_]network_cable`, `[<dye>_]dense_network_cable`, `[<dye>_]fiber_cable`. The dyes are white, orange, magenta, light_blue, yellow, lime, pink, cyan, purple, blue, green and red, plus neutral (no prefix). Black, brown, gray and light gray don't exist (`block/cable/CableColor`). Display names are like "White Network Cable".
- **Tiers** (`CableTier`):

  | Tier | Lanes | Drain per block | Profile |
  |---|---|---|---|
  | Network | `lanes.perCable = 8` | `lanes.cableDrain = 0.05` FE/t | slim |
  | Dense | `lanes.perDenseCable = 32` | `lanes.denseCableDrain = 0.2` FE/t | thick |
  | Fiber | `lanes.perFiberCable = 32` | `lanes.fiberCableDrain = 0.1` FE/t | slim |

  Only Fiber has span loss and can carry optical lines.
- **Connections:**
  - Neutral joins every colour. A dyed cable joins only its own colour and neutral.
  - All tiers join each other. A link between two nodes carries the smaller of their two capacities, so 8 next to 32 gives 8.
  - Cables connect to network blocks that accept that face (`NetworkNodeBlock.connectsOn`), to controllers, and to any `NetworkNodeHost` BE.
  - A cable uses no lanes itself, unless parts are mounted on it (see Concepts).
- **Recolouring:** use a dye on a placed cable to recolour it (uses up one dye; attachments are kept). A water bucket washes it back to neutral. A water bucket on a neutral cable waterlogs it.
- **Recipes:**
  - `network_cable`: shaped `III/RQR/III` (iron, redstone, quartz) gives 8.
  - Dense: 4 network cables + redstone (shapeless) gives 1.
  - Fiber: 4 dense + fiberglass + gallium ingot (shapeless) gives 4.
  - Dyeing in a grid: 8 cables of a tier tag around a dye gives 8 of that colour (`#encodedlogistics:network_cables`, `#...:dense_network_cables`, and fiber likewise). Coloured dense and fiber cables also have direct recipes from coloured cables.
  - There is no `<dye>_network_cable.json` direct recipe, only `_from_dye`.
- **Mining:** mining a cable takes off the facade or part you're looking at first. Pick-block gives the part or facade you're looking at.
- **Redstone:** a cable carrying a Threshold Sensor (or a redstone P2P output) is a signal source. Redstone dust connects to it only then.
- **Jade:** "Lanes used / carried" (`InfrastructureProviders`, around line 131). It is red when full. Uses `ControllerStructures.cableLanes`.
- **Fiber popup:** looking at a Fiber Cable shows a HUD popup (`client/rack/WirelessHud`, `net/WirelessInfoPayloads`) with its span length since the last amplifier and its lanes after loss.
- **Sources:** `block/cable/NetworkCableBlock.java`, `CableTier.java`, `CableColor.java`, `CableShapes.java`, `CableConnection.java`, `blockentity/CableBlockEntity.java`, `client/model/CableModel.java`.

### Cable Anchor
- **Registry:** item `encodedlogistics:cable_anchor`. It is an attachment, not a block.
- **What it does:** use it on the cable face you click. That side never connects, for this cable and its neighbour alike. Use it to keep two parallel cables from joining.
- **Removal:** a wrench (`#c:tools/wrench`) or sneak-use with an empty hand takes it off.
- **Recipe:** iron ingot + 2 iron nuggets gives 4.
- **Sources:** `block/cable/CableAttachments.java`, `NetworkCableBlock.attach/detach`.

### Cable Facade
- **Registry:** item `encodedlogistics:cable_facade` (`item/CableFacadeItem`). The data component `encodedlogistics:facade_target` holds the copied block.
- **Mounting:** a blank facade goes on any of the six sides, connected or not. The side is the one the crosshair points toward from the cable's centre; if that side is taken, the clicked face is used. A facade does NOT stop the side connecting.
- **Dressing:** use a block item on a mounted facade to dress it in that block (uses one block; the old block comes back). The block must be a full, non-translucent, model-rendered cube with no block entity, and not on `facades.blocklist = []`.
- **Removal:** the first sneak-use with an empty hand removes the dressing block. The next one removes the facade. A wrench removes it as well.
- **Effects:** an opaque facade hides the neighbour's face. Its drops are a blank facade plus the dressing block.
- **Preview:** a ghost preview shows while a facade is in hand and you look at a cable (`client/FacadePreview`).
- **Item name:** a facade item that carries a target is named "<Block> Cable Facade". In normal play the item is blank; dressing happens in the world.
- **Recipe:** 3 iron nuggets + network cable gives 6.
- **Sources:** `item/CableFacadeItem.java`, `NetworkCableBlock` (facadeSide, facadeAt, dress), `client/model/FacadeQuads.java`.

### Part Host (internal block)
- **Registry:** block `encodedlogistics:part_host`. It has no item and no loot table. It uses a `CableBlockEntity`.
- **What it does:**
  - Using a part item on a non-cable block's face puts the part on the cable in front of that face, if there is one, on the side facing the block (AE2-style).
  - If there is no cable, it places a Part Host in the replaceable space in front, holding the part against that face.
  - This happens in `onItemUseFirst`, before the block's own screen opens.
- **Orientation:** terminals and the sensor face away from the block. Ports, taps, planes and the P2P link face it.
- **Network:** it doesn't connect to cables (`connectsOn=false`). It is on the network only when the block it's mounted on is a network node: a DeviceNode whose one connection is toward that block, so the part's lanes go through that block. Otherwise the part is offline.
- **Use:** right-click opens the part's menu. Sneak-use with an empty hand, or mining it, drops the part and its contents.
- **Sources:** `block/PartHostBlock.java`, `item/PartItem.java`, `part/PartHosting.java`.

### Cable parts (PartType enum: `part/PartType.java`)
Each part is an item named by its id, registered from the enum in `ModItems`.

**Placement:**
- Use the item on a cable face. This works sneaking too, so you can sneak to place by a chest without opening it.
- Or use it on a block face (see Part Host).
- Right-click a mounted part to open its menu. A wrench or sneak-use with an empty hand removes it and drops its contents.

**Network cost:**
- Terminals use `storage.terminalLanes = 1` lane and `storage.terminalDrain = 0.5` FE/t.
- Other parts use `storage.partLanes = 1` and `storage.partDrain = 0.5`.
- Point-to-Point Links use 0 lanes and `reach.p2pDrain = 0.5`.

**General behaviour:**
- A part works only while its host is online (powered network, lanes granted) and it is enabled. ELCL CHGDEVSTS can disable it.
- Opening a part's menu checks Firewall permission: VIEW for terminals, BUILD for other parts.
- The ELCL type codes are in `elcl/exec/ElclDevices.code(CablePart)`.

| PartType id | ELCL code | Ticks | Notes |
|---|---|---|---|
| access_terminal | TERM | no | Browses network storage (terminal area) |
| fabrication_terminal | FABTERM | no | Access Terminal + 3x3 crafting grid of real items |
| schematic_encoder | ENCODER | no | Writes schematics (crafting area) |
| schematic_terminal | SCHTERM | no | Schematics and jobs (crafting area) |
| ingress_port | INGRESS | yes | See Ports |
| egress_port | EGRESS | yes | See Ports |
| inventory_tap | TAP | yes | See Inventory Tap |
| threshold_sensor | SENSOR | yes | See Threshold Sensor |
| point_to_point_link | P2P | yes | See P2P |
| collector_plane | COLLECTOR | yes | See Planes |
| deployer_plane | DEPLOYER | yes | See Planes |
| trunk_port | TRK | no | See Trunk Port |

Terminals are summarised only. Their screens belong to the terminal and crafting inventories. Recipes: access_terminal (circuit substrate, logic die, glass pane, network cable).

#### Ingress Port and Egress Port
- **Registry:** `encodedlogistics:ingress_port` and `egress_port`. Menu `PortMenu`, screen `PortScreen`.
- **Items mode:**
  - Once every 20 ticks (`OPERATION`) it moves up to `storage.portRates = [4,16,32,64]` items, indexed by 0 to 3 Throughput Modules.
  - It pays `storage.portEnergyPerItem = 0.5` FE per item from the network (an energy credit is topped up by `drawEnergy`).
  - Ingress pulls whatever passes the filter (an empty filter passes everything). Items the network can't store stay where they are. Ingress overflows into Read/Write Share routes.
  - Egress pushes only what passes the filter (an empty filter passes nothing), one candidate item type per operation, round-robin. Egress draws only from the network's own storage.
- **Fluids and gases** (Pressurized appears only when a gas source is loaded): `storage.portFluidRates = [1000,4000,8000,16000]` mB per operation, at `storage.portEnergyPerBucket = 2.0` FE per 1000 mB.
- **Energy mode:** every tick, `storage.portEnergyRates = [1024,4096,16384,65536]` FE/t.
  - Ingress moves FE from the faced block into the pool.
  - Egress gives FE from the pool to the faced block, but never takes the pool below `rack.machinePowerReserve = 0.25` of capacity.
  - Energy mode has no filter.
- **Modules:** 4 module slots (`#encodedlogistics:port_modules`). Limits: 1 Filter Module, up to 3 Throughput Modules, 1 Fuzzy Match Module, 1 Redstone Control Module.
- **Buttons:**
  - Redstone mode, which needs the Redstone module: ignore, high (work while the host block is powered), low, or pulse (one operation per rising edge). Without the module it ignores redstone.
  - The Filter Module's deny, tag and component toggles.
  - Resource type (item, fluid, pressurized, energy).
- **Other behaviour:** lit while it moved something in its last operation. The screen shows "Moved per minute".
- **Recipes:** circuit substrate, logic die, tantalum capacitor, iron, plus a hopper (ingress) or a dropper (egress).
- **Sources:** `part/PortPart.java`, `part/PartFilter.java`, `menu/PortMenu.java`.

#### Port modules
Items: `filter_module`, `throughput_module`, `fuzzy_match_module`, `redstone_control_module`. Tag `#encodedlogistics:port_modules`. Filter and Fuzzy are also in `#encodedlogistics:plane_modules`.

- **Filter (PartFilter):** 9 ghost entries.
  - With no Filter Module, it is an item allow-list.
  - With the module it gains 3 options: deny (everything except the entries), tags (match on a shared `c:` tag), and exact components.
- **Fuzzy Match Module:** each entry can be set from the slot's context menu to match any item in one of the entry's tags, or the entry's item by damage: any damage, or worn 0-25%, 25-50%, 50-75% or 75-100%.
- **Throughput Module:** steps through the rate lists above. Fabricators use it too.
- **Redstone Control Module:** unlocks the redstone modes.
- **JEI:** you can drag from JEI onto ghost slots (`net/GhostSlotPayload`).
- **Recipes:** all shapeless on circuit substrate. Filter: logic die + paper. Throughput: memory die + tantalum capacitor. Fuzzy: memory die + string. Redstone: logic die + redstone torch.

#### Inventory Tap
- **Registry:** `encodedlogistics:inventory_tap`. Menu `InventoryTapMenu`.
- **What it does:** makes the faced inventory part of network storage in place. Nothing is copied. This covers item slots, and fluid and gas tanks via ResourceIO.
- **Settings:**
  - Access mode: read/write, read only (items only come out), or write only.
  - Its 3x3 filter limits what goes in and what the network sees. An empty filter passes everything.
  - Priority -999 to 999. Drive Bays sit at 0. Higher priority fills first and empties last. You can type it in or step it.
- **Other behaviour:** lit while online and facing an inventory or tank (checked every 20 ticks).
- **Recipe:** circuit substrate, logic die, memory die, chest, ferrite.
- **Source:** `part/InventoryTapPart.java`.

#### Threshold Sensor
- **Registry:** `encodedlogistics:threshold_sensor`. Menu `ThresholdSensorMenu`.
- **What it does:** every 4 ticks it compares the network's count of a ghost item against a typed threshold, using above, below or equal.
- **Output:** while the condition is true it emits a strong 15 into the block its face is against and a weak signal around it, like a lever. It is off while offline or when no item is set. Its lamp is lit while emitting.
- **Recipe:** circuit substrate, logic die, redstone torch, comparator.
- **Source:** `part/ThresholdSensorPart.java`.

#### Point-to-Point Link (P2P)
- **Registry:** `encodedlogistics:point_to_point_link`. Menu `PointToPointMenu`, screen `PointToPointScreen`.
- **What it does:** an input endpoint paired with one or more outputs, passing one thing straight across without going through storage.
- **Settings:** in its screen, pick the type (buttons 0-5) and flip in/out (button 10). Both are locked while paired. Button 11 unpairs. The screen shows status and the first partner's position.
- **Types** (`part/LinkType`):
  - **Items:** every 10 ticks, up to `reach.p2pItemsPerOperation = 64` items from the inventory the input faces into the outputs' inventories, round-robin.
  - **Fluids and Pressurized:** every 10 ticks, up to `reach.p2pFluidPerOperation = 8000` mB.
  - **Energy:** up to `reach.p2pEnergyPerTick = 8192` FE/t.
  - **Redstone:** the outputs emit the signal the input reads.
  - **Lanes:** the outputs' cables get `reach.p2pLanes = 8` lanes from the input's network as a remote link. The lanes are split evenly between outputs, minimum 1 each. In effect the outputs' cables join the input's network.
- **Conditions:** items, fluids, energy and redstone cross only while both ends are on the same running network. These moves draw no per-item FE.
- **Pairing:**
  - Use a Link Card. Sneak-use on one endpoint stores it; use on the other pairs them.
  - Both must be the same type and opposite directions, in the same dimension.
  - An output has one input. An input can have many outputs: the card keeps its address, so you can pair more outputs.
- **Upkeep:** every 40 ticks it drops partners that no longer point back. Its ring glows while paired.
- **Models:** 12 (6 types x in/out). The code comment says "eight", which is stale.
- **Recipe:** optical transceiver + logic die + network cable gives 2.
- **Sources:** `part/PointToPointPart.java`, `part/LinkType.java`, `item/LinkCardItem.java`.

#### Collector Plane and Deployer Plane
- **Registry:** `collector_plane` and `deployer_plane`. Menus `CollectorPlaneMenu` and `DeployerPlaneMenu`.
- **Shape:** a flat plate over a whole cable face. Planes of the same kind side by side on one face join visually (connected textures). Lit for 12 ticks after each action.
- **Collector:**
  - Breaks the block in front as an iron pickaxe would. It takes `reach.collectorTicksPerHardness = 30` ticks per point of hardness.
  - It never breaks unbreakable blocks, network blocks, or blocks holding items. It only breaks a block when all the drops pass the filter and fit in the network.
  - It also vacuums dropped items in that space every 10 ticks, and picks up fluid source blocks as 1000 mB.
  - Its one module slot takes a Filter or Fuzzy module. Without one it takes everything.
- **Deployer:**
  - Every `reach.deployerInterval = 10` ticks it takes the first entry in its 3x3 ghost list that the network has.
  - It either places that as a block (place mode; a fluid entry places a source block, using 1000 mB) or drops one item (drop mode).
- **Recipes:**
  - Collector: logic die, circuit substrate, iron pickaxe, hopper, ferrite.
  - Deployer: logic die, circuit substrate, dispenser, ferrite.
- **Sources:** `part/PlanePart.java`, `CollectorPlanePart.java`, `DeployerPlanePart.java`.

#### Trunk Port
- **Registry:** `encodedlogistics:trunk_port`. It doesn't tick. Its screen is a BlockPanel (`panelKind "trunk"`, `client/screen/TrunkPortPanel`).
- **What it does:** VLAN trunking on a cable. It carries segments (the networks beyond Segment Isolators) into the network it's on. Every Server Rack on that network can then put its pooled devices on those segments without a Link Card of its own.
- **Linking segments:** use a Link Card that holds a segment or network on the port, without sneaking. This toggles link/unlink. It holds at most 8 segments. IDs are assigned 10, 20, 30, ... up to 4094.
- **Modes:**
  - TRUNK (the default): all segments, or the picked ones, plus a native segment.
  - ACCESS: only the native segment.
- **Panel:** mode, allow toggles, native segment, unlink. Changes need Firewall BUILD.
- **Lanes:** the panel shows the lanes the devices on its segments use against `rack.trunkPortLanes = 64`. This is only a guide; nothing enforces it.
- **Isolation:** segments on a trunk still can't see each other. Only an L3 Switch or Router routes between them.
- **Recipe:** network cable + logic die + memory die.
- **Source:** `part/TrunkPortPart.java`.

### Network Bridge
- **Registry:** `encodedlogistics:network_bridge`. BE `NetworkBridgeBlockEntity`. Menu `NetworkBridgeMenu`, screen `NetworkBridgeScreen`.
- **What it does:** two paired Bridges join their networks as if a cable of `reach.bridgeLanes = 32` lanes ran between them. This works at any distance, and across dimensions when `reach.bridgeCrossDimension = true`. Both ends must be loaded.
- **Placement and connections:** faces the player when placed (6 directions). Cables connect on every face except the front (the optical port).
- **Cost:** it uses no lanes itself and drains `reach.bridgeDrain = 8.0` FE/t.
- **Pairing:** sneak-use a Link Card on one Bridge, then use the card on the other. Pairing replaces old partners. It fails for the same Bridge, or across dimensions while cross-dimension is off.
- **Upkeep:** every 10 ticks it checks that its partner still points back.
- **Chunk loading:** with `reach.bridgeChunkLoading = false` by default. When true, a linked Bridge keeps its own chunk loaded.
- **Status blockstate:** unlinked (amber), linked_idle, linked_active (lanes crossing; the lens pulses).
- **Screen:** status light and line (linked / not linked / partner offline), the partner's position and dimension, and a 32-segment meter of lanes crossing.
- **Recipe:** shaped `TOT/PCM/EOE` (tantalum ingot, optical transceiver, processor die, circuit substrate, memory die, ender eye).
- **Sources:** `block/NetworkBridgeBlock.java`, `blockentity/NetworkBridgeBlockEntity.java`, `menu/NetworkBridgeMenu.java`.

### Link Card
- **Registry:** item `encodedlogistics:link_card`. The data component `encodedlogistics:link_address` (`LinkAddress`) holds what it stored.
- **Stacking:** blank cards stack. Writing a card splits one off the stack and sets its max stack size to 1.
- **Clearing:** sneak-use in the air clears it.
- **Uses** (`item/LinkCardItem.onItemUseFirst`):
  - **Network Bridge:** sneak-use stores the Bridge; use on another Bridge pairs them.
  - **P2P endpoint:** sneak-use stores it; use on the partner pairs them (see P2P).
  - **Any other network block:** sneak-use stores that network ("network stored").
  - **Segment Isolator:** sneak-use stores the segment on the half clicked.
  - **Rack switch unit** (with a stored segment, rack open): toggles the rack serving that segment.
  - **Router unit** (with a stored network or segment): links the Router to it. Results: linked, already, or full.
  - **Trunk Port** (with a stored segment or network): toggles that segment.
  - **Wireless Controller unit** (rack front, door open): takes the controller. Then use the card on a Wireless Bridge, Wireless Port, or Small Wireless Bridge (on a machine) to link it. A card from another controller relinks it.
  - **Optical Multiplexer** (block, or its rack unit) **or Optical Transport Shelf unit:** sneak-use stores it; use on another of the same kind pairs them. Using a stored network or segment on a multiplexer gives it a channel: the one selected in its panel, otherwise the first free channel, or "full". On a shelf, it goes to the Transponder Card you're looking at.
- **Permissions:** Firewall BUILD on the network at each end, or RACK for rack units. This is checked both when writing and when applying. If denied you get "Access denied: SYSNAME Firewall".
- **Recipe:** schematic card + memory die gives 2.
- **Source:** `item/LinkCardItem.java`.

### Relay Antenna
- **Registry:** `encodedlogistics:relay_antenna`. BE `RelayAntennaBlockEntity`. Menu `RelayAntennaMenu`, screen `RelayAntennaScreen`.
- **What it does:** wireless coverage for the Handheld Terminals linked to its network.
- **Range:** a sphere of `reach.relayBaseRange = 32` blocks, plus `reach.relayRangePerTransceiver = 32` per Optical Transceiver in its 4 slots. That gives 160 blocks at most by default. Coverage is same-dimension only, while online.
- **Placement and cost:** faces the player. Cables connect on every face except the top (the mast). It is a device with 1 lane (hard-coded) and drains `reach.relayDrain = 2.0` FE/t.
- **States:** `online`; the mast tip blinks.
- **Screen:** range, 4 transceiver slots, and the players in range carrying a terminal linked to this network, with their distance (synced every 20 ticks via `net/RelayTerminalsPayload`).
- **Linking terminals:** use a Handheld Terminal on the antenna to link it to the antenna's network.
- **Recipe:** shaped ` B /POP/FBF` (iron bars, processor die, optical transceiver, ferrite).
- **Sources:** `block/RelayAntennaBlock.java`, `blockentity/RelayAntennaBlockEntity.java`, `menu/RelayAntennaMenu.java`.

### Handheld Terminal (wireless reach)
- **Registry:** item `encodedlogistics:handheld_terminal`, stacks to 1. Menu `HandheldTerminalMenu`, screen `HandheldTerminalScreen`.
- **What it does:** an Access Terminal you carry.
- **Linking:**
  - Use it on a Relay Antenna or a Network Controller to link it to that network. This also clears any Wireless Controller link.
  - Use it on a Wireless Controller's unit or rack to link it to that controller. It then works anywhere: through that controller, or through any online one on the network with `rack.wirelessAnyController = true`. This works in other dimensions with `rack.wirelessCrossDimension = true`. No Access Point is needed for terminals.
- **Opening:** use it in the air. It needs a link, charge, and reach (an antenna covering you, preferring the nearest relative to range, or a Wireless Controller).
- **Signal bars:** 4 within 25% of range, then 3, 2, 1. Full bars through a controller.
- **Battery:**
  - `reach.handheldCapacity = 200000` FE.
  - `reach.handheldDrainPerSecond = 10` while the screen is open.
  - `reach.handheldEnergyPerItem = 1` per item moved.
  - It charges in any FE charger (at most `handheldChargeRate`), or by use-and-hold against a Capacitor Bank at `reach.handheldChargeRate = 4096` FE/t.
- **Icon:** shows the link state (UNLINKED / LINKED / OUT_OF_RANGE), updated every second.
- **Recipe:** access terminal + optical transceiver + 2 tantalum capacitors + circuit substrate.
- **Sources:** `item/HandheldTerminalItem.java`, `menu/HandheldTerminalMenu.java`.

### Exchange Switch (Internet Exchange)
- **Registry:** `encodedlogistics:exchange_switch`. 2 blocks tall (DoubleBlockHalf). BE `ExchangeSwitchBlockEntity` on the lower half. Panel via BlockPanelMenu (`client/screen/ExchangePanel`).
- **Placement:** needs the space above to be free. Faces the player. The placer owns it.
- **Network:** the lower block is its node, connecting on every side except the top. It is a device with 1 lane and drains `rack.exchangeDrain = 8.0` FE/t. Breaking either half breaks both (one item). `online` lights the LEDs and member count.
- **Joining:** Routers join with a Cross-Connect: use it on the Router's unit, then on the exchange. Membership is OPEN (join at once) or APPROVAL (the owner approves).
- **Ports:** `rack.exchangePorts = 32`. The owner can offer fewer.
- **Lanes:** `rack.exchangePortLanes = 64` lanes per port. A peering's throughput and cost is the smaller of this and the Routers' rates.
- **Peering:**
  - A route server: on by default; members opt in.
  - Or bilateral peering, once each side accepts.
  - Each member sets, per peer, whether to exchange routes and whether to allow transit, and can apply an ACL.
- **Chunk loading:** keeps its chunk loaded while it has members (`rack.exchangeChunkLoading = true`).
- **Naming:** renamed with an anvil or its Setup tab (8 characters max). It appears in route tables as `IX:name`. ELCL code XSW.
- **Panel tabs:** Members, Peers, Setup. Actions: approve, remove, membership, route server, ports, rename, opt in, accept, peer routes, peer transit.
- **Recipe:** 2 L3 switches + 2 processor dies + 4 optical transceivers + iron block.
- **Sources:** `block/ExchangeSwitchBlock.java`, `blockentity/ExchangeSwitchBlockEntity.java`, `network/routing/ExchangeRegistry.java`, `RoutingDomain.java`.

### Cross-Connect
- **Registry:** item `encodedlogistics:cross_connect`.
- **Use:** use it on a Router's unit in an open rack to record that Router (needs BUILD on its network). Then use it on an Exchange Switch to join, or to request to join. This uses up one Cross-Connect. Sneak-use in the air clears it.
- **Recipe:** 2 fiber cables + optical transceiver + copper foil gives 4.
- **Source:** `item/CrossConnectItem.java`.

### Directory Server (block)
- **Registry:** `encodedlogistics:directory_server`. A rack form also exists: `directory_server_rack` (rack area). BE `DirectoryServerBlockEntity`. Panel `DirectoryPanel`.
- **What it does:** publishes chosen devices of its network so that other networks can reach them as `SYSNAME/DEVICE` in any ELCL command that takes a device.
- **Resolution:** a name resolves when all of these hold:
  - a Router on the caller's network has a route to the target network;
  - an online Directory Server there publishes the device;
  - one of those Routers has a CONTROL policy from the caller's network, with a path and an ACL that permits `*CONTROL`;
  - the caller has the Firewall permission on the target network.
- **Caching:** the location is cached for `rack.directoryCacheSeconds = 30`. A route or network change clears it.
- **Network:** connects on all faces. A device with 1 lane, draining `rack.directoryDrain = 2.0` FE/t. `online` makes the display glow. ELCL code DIR.
- **Panel:** Published (click a device to publish or unpublish it), Remote (reachable networks with Directory Servers, their hops, CONTROL policy), Lookups (the last 16).
- **Recipe:** circuit substrate + processor die + 2 memory dies + iron.
- **Sources:** `block/DirectoryServerBlock.java`, `blockentity/DirectoryServerBlockEntity.java`, `network/routing/Directory.java`, `Directories.java`, `DirectoryPanels.java`.

### Optical Transceiver
- **Registry:** item `encodedlogistics:optical_transceiver`.
- **Uses:** it is a component. Relay Antenna slots add range. Router cages add throughput (`rack.routerRatePerTransceiver = 16`, `rack.routerDrainPerTransceiver = 1.0`). Chassis Uplink Card cages. Many recipes.
- **Recipe:** fiberglass, logic die, glass, copper foil, gallium ingot.

### Optical Amplifier (block)
- **Registry:** `encodedlogistics:optical_amplifier`. BE `OpticalAmplifierBlockEntity`. A rack form `optical_amplifier_rack` is made shapeless from the block (rack area).
- **Shape and placement:** an 8x8x16 inline housing along FACING, placed along the player's horizontal look. Cables join only at its two ends.
- **Network node:** passes lanes like a Fiber Cable (capacity = fiber lanes). It takes 1 lane itself and drains `optical.amplifierDrain = 1.0` FE/t.
- **Span loss:** when powered (state ON) it ends the span, so span loss counts again from it. When unpowered it counts as fiber and the run continues through it.
- **Powered when:** online on its network, or when an online Optical Multiplexer or Transport Shelf whose line runs through it powers the line.
- **States:** off, on, fault (no fibre at its back: input loss of signal).
- **Popup:** input and output lanes, and the spans on either side. ELCL code AMP.
- **Recipe:** optical transceiver, 2 gallium ingots, tantalum capacitor, circuit substrate.
- **Sources:** `block/OpticalAmplifierBlock.java`, `blockentity/OpticalAmplifierBlockEntity.java`.

### Optical Multiplexer (block)
- **Registry:** `encodedlogistics:optical_multiplexer`. BE `OpticalMultiplexerBlockEntity`. Panel `OpticalMuxPanel`. A rack form `optical_multiplexer_rack` is made shapeless from the block.
- **Placement and connections:**
  - Faces the player (horizontal).
  - Its FRONT is the line port: the Fiber Cable there is its line. The line joins nothing; the networks on either end of the line stay apart.
  - Every other side joins the network it draws power from. It is a device with 1 lane and drains `optical.multiplexerDrain = 4.0` FE/t.
- **Channels:** `optical.multiplexerChannels = 8` (max 16).
  - Give a channel a client network, or a segment, with a Link Card.
  - Pair with another multiplexer with a Link Card. There must be a Fiber Cable line between them (traced by `optical/FiberTrace`, through amplifiers, bridge pairs and racks with an online rack amplifier).
  - Channel n at one end joins its network with channel n's at the other. Each channel carries up to a Fiber Cable's lanes (32) less the line's span loss.
  - Channels never mix. A channel is up while the line is up, both multiplexers are online, and both ends have a network.
  - It rechecks every 40 ticks.
- **Panel:** Channels and Line tabs. Actions: select a channel for the next Link Card, clear, unpair (BUILD).
- **States:** off, on, fault (paired but line down). ELCL code MUX.
- **Recipe:** 4 optical transceivers, 2 fiber cables, processor die, circuit substrate.
- **Sources:** `block/OpticalMultiplexerBlock.java`, `blockentity/OpticalMultiplexerBlockEntity.java`, `optical/Multiplexer.java`, `OpticalChannels.java`, `FiberTrace.java`.

### Fiber span loss
- **Rule:** a continuous Fiber Cable run longer than `optical.fiberSpanThreshold = 256` blocks loses one lane for every further `optical.fiberSpanLossStep = 32` blocks, never dropping below 1. `optical.fiberSpanLoss = true` turns it on.
- **Signal quality:** 100% minus 4% per lane lost (`SpanLoss.quality`).
- **How it's applied:** on network discovery, each Fiber run's span is its longest path between its ends. Every link in the run, and every link into it, is capped at the lanes left.
- **What ends a run:** the run is only Fiber Cable blocks plus unpowered amplifiers. Anything else ends it, including a powered amplifier, a remote link, or any other block.
- **What's unaffected:** Network and Dense Cable, bridges and links.
- **Display:** shown in the Fiber Cable HUD popup.
- **Source:** `optical/SpanLoss.java`.

### Access Point (wireless)
- **Registry:** `encodedlogistics:access_point`. BE `AccessPointBlockEntity`.
- **Placement:** a disc placed against the clicked face. FACING is the clicked face, and it faces away from the block.
- **Connections:** it connects ONLY on the side it's mounted on. It is a device with 1 lane, drains `rack.accessPointDrain = 2.0` FE/t, and doesn't pass the network through (`passesThrough=false`).
- **Capacity:** each online AP gives its network's Wireless Controller `rack.wirelessApClients = 8` client slots.
- **States:** off (no uplink), linking (no controller on its network; yellow blink), online (light blue). It never shows fault.
- **HUD popup:** Clients n/8 and Uplink. ELCL code AP.
- **Recipe:** 2 radio modules, circuit substrate, logic die, quartz block.
- **Sources:** `block/AccessPointBlock.java`, `blockentity/AccessPointBlockEntity.java`.

### Wireless Bridge
- **Registry:** `encodedlogistics:wireless_bridge`. BE `WirelessBridgeBlockEntity`.
- **What it does:** once linked to a Wireless Controller (Link Card), whatever is cabled to it (on its sides and bottom; the mast is on top) is on the controller's network. Range is unlimited within the dimension, and covers other dimensions when `rack.wirelessCrossDimension = true`. It acts like a `rack.wirelessBridgeLanes = 32` lane cable from the controller's rack.
- **Cost:** it uses no lanes itself and drains `reach.bridgeDrain x rack.wirelessEnergyMultiplier` (8.0 x 2.0 = 16 FE/t). It counts as one client.
- **States:** off, linking, online, active (lanes crossing), fault (no APs or over capacity).
- **GUI:** none. It has a HUD popup: Controller, Devices behind it, Lanes. ELCL code WBRIDGE.
- **Recipe:** 2 radio modules, optical transceiver, processor die, 2 iron.
- **Sources:** `block/WirelessBridgeBlock.java`, `blockentity/WirelessBridgeBlockEntity.java`.

### Wireless Ingress Port and Wireless Egress Port
- **Registry:** `wireless_ingress_port` and `wireless_egress_port`. BE `WirelessPortBlockEntity`, which extends the part-host BE and holds an Ingress or Egress Port part.
- **Placement:** a 12x12x4 panel against the inventory it faces (FACING points toward that inventory). There is no cable; it connects to nothing.
- **Behaviour:** identical to the cabled port: filters, modules, redstone modes, the PortScreen. The title is the block name, and a link light and signal bars sit under the redstone button.
- **Cost:** 1 lane over its link to the controller's rack. It is one client. Drain is `storage.partDrain x wirelessEnergyMultiplier` = 1.0 FE/t.
- **States:** off, linking, online, fault. ELCL codes WINGRESS / WEGRESS.
- **Recipes:** an ingress or egress port + a radio module.
- **Sources:** `block/WirelessPortBlock.java`, `blockentity/WirelessPortBlockEntity.java`.

### Wireless Controller (rack device, summarised because it runs wireless)
- **Registry:** item `wireless_controller` (1U RackDevice). Drain `rack.wirelessControllerDrain = 4.0`.
- **What it does:** links Handheld Terminals (use the terminal on its unit or rack) and wireless clients (Link Card). Clients are admitted oldest link first, up to APs x 8. The rest are over capacity.
- **Links:** each admitted client is a remote link from the controller's rack: a bridge's 32 lanes, or 1 for a port or Small Wireless Bridge.
- **Fault:** with clients and no AP online it faults "No access points".
- **Panel:** APs and Linked tabs. You can unlink a client from the panel. ELCL code WLC.
- **Source:** `rack/device/WirelessControllerDevice.java`, `wireless/Wireless.java` (Problem: NONE, NOT_LINKED, NO_CONTROLLER, NO_ACCESS_POINTS, OVER_CAPACITY).

### Radio Module
- **Registry:** item `encodedlogistics:radio_module`. It is only a crafting component (Access Point, Wireless Bridge, wireless ports, Wireless Controller, Small Wireless Bridge). Verified by grep: no Java use other than registry and creative tab.
- **Recipe:** gallium ingot, logic die, copper foil, circuit substrate.

### Small Wireless Bridge (Arcforge machines)
- **Registry:** item `encodedlogistics:small_wireless_bridge` (`SmallWirelessBridgeItem`). The block `small_wireless_bridge` has no item and holds only the model.
- **Placement:** use it on a face of an Arcforge machine. The face must be free in front, the machine must expose Arcforge machine control, and you must be allowed to use the machine. One per machine (multiblock: one per structure). It is stored in level data (`machine/MachineBridges`), not as a BE.
- **Linking:** link it with a Wireless Controller's Link Card. It is then a 1-lane device on that network over the air, draining `rack.smallWirelessBridgeDrain = 1.0` FE/t. The network can see and work the machine.
- **Power from network:** with power from network on, it tops up the machine's FE: `rack.machinePowerRate = 1000` FE/t max, efficiency `rack.machinePowerEfficiency = 0.9`, and it never takes the pool below `rack.machinePowerReserve = 0.25`.
- **Gateway feeding:** a Gateway named in its settings feeds the machine over the air.
- **Removal:** sneak-use with an empty hand on its face (BUILD while linked).
- **Without Arcforge:** it needs Arcforge 2.5.0+ (API 1.0). Without it, bridges are kept but do nothing.
- **Recipe:** radio module, logic die, copper foil, 4 iron nuggets.
- **Sources:** `item/SmallWirelessBridgeItem.java`, `machine/MachineBridge.java`, `machine/MachineBridges.java`, `compat/arcforge/ArcforgeCompat.java`. This probably also belongs to the machines inventory.

### net/ packets (brief)
These are plumbing, not player features:
- `NetworkSnapshotPayload`: the controller screen.
- `CapacitorBankPayload`
- `WirelessInfoPayloads`: crosshair popups for wireless and optical blocks and Fiber Cable span.
- `RelayTerminalsPayload`
- `BlockPanelPayloads`: Exchange, Directory, Trunk, Mux panels.
- `MenuValuePayload`: typed tap priority or sensor threshold.
- `GhostSlotPayload`: JEI drags onto ghost slots.
- `MachineBridgesPayload`
- `LocatePayload`

All are registered in `net/ModNetwork.java`.

---

## Concepts

### What a network is
- A network is everything reachable from one controller structure through nodes that connect toward each other. Sides must match: a cable side and the neighbour's `connectsOn`.
- Remote links also join nodes: Bridge pairs, lanes P2P, Wireless Controller to its clients, and optical channels. Both ends must be loaded and must list each other (`NetworkDiscovery.walk`).
- Kinds of structure: a group of Network Controller blocks, a Server Rack holding rack Network Controllers (2U/4U), or a Midrange System (a SourceNode). One network runs from each structure.
- Size cap: discovery stops at 16,384 nodes (`NetworkDiscovery.MAX_NODES`).
- A network spans dimensions only through remote links. Bridges need `bridgeCrossDimension`; wireless needs `wirelessCrossDimension` (both default true).
- When it's recomputed: placing, breaking or reconnecting any node calls `markTopologyChanged`, which rediscovers and re-solves every network next tick. Otherwise the graph is cached.
- A structure partly in unloaded chunks waits, with its devices offline, until all of it is loaded.

### Devices vs. pass-through blocks
- **Devices** use lanes and are switched online and offline every tick. Online means the network is ONLINE (or FAILOVER) and the device got its lanes.
- **Pass-through nodes** use no lanes and carry lanes through: cables, Power Inlet, Capacitor Bank, Network Bridge, Wireless Bridge.
- **Nodes that don't pass through** stop discovery: the Segment Isolator and the Access Point.
- **A cable carrying parts** is still a link of its tier's capacity, and also a device needing the sum of its parts' lanes. Its drain is the cable's plus its parts'.

### Lanes
- Each link carries at most the smaller capacity of its two ends.
  - Controller faces: 32.
  - Cables: 8, 32 or 32.
  - Most devices and blocks: unlimited.
  - Bridges: 32.
  - P2P lanes: 8 split between outputs.
  - Wireless bridge: 32. Wireless port: 1.
  - Optical channel: 32 less loss.
  - Fiber spans: capped by loss.
- **How lanes are assigned** (`network/LaneSolver`, AE2-style):
  1. A multi-source BFS from the lane sources builds a shortest-path tree. Ties are broken by Direction order DOWN, UP, NORTH, SOUTH, WEST, EAST, then remote links in position order, so results are stable.
  2. Devices are visited nearest first.
  3. Each device walks its tree path to a source and gets its lanes only if every link on the path has room, along with the source budget.
  4. Otherwise it is "missing a lane": connected but inactive. The controller screen counts these, and Jade shows a red "full" on the cable.
- **Lane sources and capacity:**
  - Each controller block face linked to a non-controller adds 32.
  - A rack with a usable rack controller has one shared budget: 2U = `rack.rackController2uLaneFaces 6` x 32 = 192, and 4U = 384. A redundant pair shares one budget.
  - A Midrange System: `rack.midrangeLaneFaces 4` x 32 = 128. An Integrated one: 6 x 32 = 192.
- **Server Racks** take lanes demand by demand, by priority (High first, then highest unit first, so the lowest units are shed first), over whichever uplinks reach a source. A rack is "degraded" when a demand is shed or an uplink is down. The details belong to the rack area.
- **Conflict:** no device gets lanes, and the whole network is offline, when the network has controller blocks from two or more structures, a controller structure plus a rack controller (or a Midrange System), more than two rack controllers, or rack controllers of different sizes.
  - Consequence (from code reading): joining two networks that each have a controller, by Bridge, lanes P2P, optical channel or wireless, makes a CONFLICT. Only one side may have a controller.
- **Ad-hoc networks:** with no controller, `LaneSolver` allows up to `lanes.adHocMaxDevices = 8` lanes with no routing limits; past that, ADHOC_OVERLOAD. But networks are only ever discovered from a controller structure (ControllerStructures), so in game, devices with no controller are simply offline. See Open questions.
- **Per-device lane costs:**
  - 1 lane: terminals (`terminalLanes`), other parts (`partLanes`), Relay Antenna, Access Point, Optical Multiplexer, Optical Amplifier, Directory Server, Exchange Switch, wireless ports, Small Wireless Bridge. The block devices' 1 is hard-coded.
  - 0 lanes: P2P endpoints, Bridges, Wireless Bridge.

### Power model (FE)
- **The pool:** the network's energy is its controller buffers (or the working rack or Midrange controller), plus its Capacitor Banks, plus the Energy Storage Drives in its drive holders.
- **Every tick:**
  1. Drain = `controller.drainPerBlock` x controller blocks (0 for rack controllers; theirs is in the rack's drain) plus the sum of every non-controller node's passive drain. Fractions carry over between ticks.
  2. UPSes cover a shortfall first (rack area).
  3. The rest is drained from banks and drives first, then controllers.
- **Status:** if stored > 0 the network is ONLINE, otherwise NO_POWER (every device loses its lanes and goes offline).
- **Into the pool:** FE enters through Power Inlets (16384 FE/t each), controller faces (4096 FE/t per block), Capacitor Bank faces (with direct IO), Ingress Ports in Energy mode, and rack power ports (`rack.rackPowerMaxInput = -1`, meaning the same as inletMaxInput). The fill order is drives, then controllers, then banks, then the standby rack controller.
- **Work costs on top of passive drain:** port items 0.5 FE each, port fluid 2 FE per bucket, Handheld per item (from its own battery), Fabricator crafts, and so on, via `drawEnergy`.
- **Default passive drains in this area (FE/t):**

  | Block | Drain |
  |---|---|
  | Controller block | 2 |
  | Network / Dense / Fiber cable (per block) | 0.05 / 0.2 / 0.1 |
  | Isolator (on each side) | 0.5 |
  | Terminal or part | 0.5 |
  | P2P endpoint | 0.5 |
  | Relay Antenna | 2 |
  | Network Bridge | 8 |
  | Wireless Bridge | 16 |
  | Wireless port | 1 |
  | Small Wireless Bridge | 1 |
  | Access Point | 2 |
  | Directory Server | 2 |
  | Exchange Switch | 8 |
  | Optical Multiplexer | 4 |
  | Optical Amplifier | 1 |
  | Wireless Controller | 4 |

- **Comparators:** controllers and banks output 0 to 15 by fill.

### Segments
- A Segment Isolator cuts a cable run into two separate networks, called segments.
- A segment is referred to by a Link Card storing one end of the isolator (`LinkAddress.Kind.SEGMENT`).
- Racks can serve a segment with their pooled devices (Link Card on a switch unit).
- A Trunk Port carries up to 8 segments into a network for every rack on it.
- Multiplexer channels can carry a segment.
- Segments don't see each other's storage; only an L3 Switch or a Router routes between them.

### Wireless model
- **Two separate systems:**
  - Relay Antennas: range-limited, same dimension, Handheld Terminals only.
  - The Wireless Controller (rack): unlimited range, for Handheld Terminals and for clients (Wireless Bridges, Wireless Ports, Small Wireless Bridges).
- **Clients need Access Points:** each needs at least one online AP on the controller's network. Each AP gives 8 slots, and clients past the slots wait (fault, over capacity).
- **Handheld Terminals** don't need APs.
- **Client problems** (shown on the client): NOT_LINKED or NO_CONTROLLER is a warning (linking state). NO_ACCESS_POINTS or OVER_CAPACITY is a fault.
- **Popups:** a crosshair popup (`client/rack/WirelessHud`) shows status for APs, bridges, ports, Small Wireless Bridges, optical blocks and Fiber Cable.

### Optical model
- Fiber Cable runs suffer span loss.
- Amplifiers regenerate the light (end the span) when powered.
- Multiplexers carry up to 8 (max 16) isolated client networks or segments over one fibre line. The line itself joins no network.
- The Optical Transport Shelf (6U rack chassis: up to `optical.shelfChannels = 40` channels, transponder, mux, amplifier and protection cards with 1+1 protection, revertive or not) does the same at larger scale. It belongs to the rack and chassis area.

### Firewall interaction (rack area, but it affects networking)
On a network with a Firewall:
- placing or breaking network blocks, and putting parts, anchors or facades on cables, needs BUILD (`rack/FirewallEvents`);
- opening a terminal needs VIEW;
- opening other parts needs BUILD;
- Link Card actions need BUILD (or RACK for rack units).

---

## Not implemented (from older docs)

Each item gives what the doc says and how it was checked.

1. **Cable Certifier** (`docs/optical/HANDOFF.txt` sections 0 and 1: "show span length / lanes after loss on it"). Not in the mod. `grep -rli certifier src/` returns nothing: no item, class, lang key or recipe. Span info is shown only by the Fiber Cable crosshair popup and the amplifier popup.
2. **Access Point "cables connect on its full sides and bottom"** (`docs/wireless/HANDOFF.txt` section 2). Not as described. `AccessPointBlock.connectsOn` returns true only for `FACING.getOpposite()`, the side it's mounted on. The node has that one connection and `passesThrough=false`.
3. **Access Point fault state** (wireless handoff section 2: "state off | linking | online | fault"; ring red blink). The AP never shows fault. `AccessPointBlockEntity` (around line 90) sets only OFF, ONLINE or LINKING. Fault exists only for bridges and ports (`Wireless.Problem`).
4. **Wireless Remote and Handheld Fabrication Terminal** (wireless handoff header: "dropped"). Confirmed absent: they're not in `ModItems.java` and there's no class.
5. **`wirelessCrossDimension` "default off"** (wireless handoff open question 2). The code default is **true** (`Config.java`, `rack.wirelessCrossDimension`). `bridgeCrossDimension` is also true. Document the code defaults.
6. **Trunk "A trunk's lanes are shared by its segments"** (`docs/routing/HANDOFF.txt` section 4). Not enforced. `rack.trunkPortLanes` is used only in `TrunkPortPart.writePanel` for display ("as a guide"; grep finds `TRUNK_PORT_LANES` nowhere else). Its IDs are assigned 10, 20, ... (`ID_STEP=10`) rather than chosen 1-4094, and there is a maximum of 8 segments per Trunk Port.
7. **Chassis power "200 FE/t per card / blade, 1,000 per chassis base"** (optical handoff open question 4). The code uses `optical.chassisDrain = 10.0` and `optical.chassisCardDrain = 2.0`.
8. **"compute_blade (crafting threads: 8 per blade)"** (optical handoff section 6). The code derives it instead: `BladeEnclosureDevice.threadsPerBlade() = computeServerThreads/2 x BLADE_UNITS x (1 + bladeBonusPercent/100)`. It isn't a fixed 8 (rack area).
9. **Optical handoff section 0: "Work with Hardware NOT in the repo … Rack Elevation templates and VLAN trunking not in the repo"**. Outdated: all of these are now implemented (WRKHW in `client/crt/WrkHwPanel.java`, VLAN in `TrunkPortPart` and the switch Ports view, Rack Elevation `item/RackElevationItem`). Not a gap, but don't copy that text.
10. **LaneResult comment "linkUsage … for smart cables to draw lit strands"** (a code comment, not a doc). No cable rendering of lane usage exists: `client/model/CableModel` has no usage input, and grepping client/ for "strand" or `cableLanes` finds nothing. Lane use is shown only via Jade and the controller screen.
11. **Everything else in the three handoffs was found implemented:**
    - ELCL `DSPROUTE`, `DSPNBR`, `WRKACL`, `WRKIX`, `WRKDIR`, `RTVRTE`, `RTVNBRSTS`, `CHGNBR`, `ADDACLE`, `RMVACLE`, `CHGACLE`, `RTVDIRENT`, `CHGDIRENT`, `APVIXMBR`, `RMVIXMBR`, `WRKHW`, `DSPOPT`, `RTVCHSSTS`, `RTVOPTSTS`, `CHGPROT`.
    - Messages ELC2601-2608 and ELC2701-2705.
    - Triggers `*NBRUP`, `*NBRDOWN`, `*RTECHG`, `*ACLDENY`, `*CARDFAULT`, `*SUPFAILOVER`, `*PROTSWITCH`, `*SPANLOSS`.
    - Router STATIC/DYNAMIC, transit NONE/LISTED/ALL, ACL `*ANY`/`*FLUID`/`*PRES`/`*ENERGY`/`*CONTROL` with rate and quota, the CONTROL route mode.
    - Switch Ports view, revertive protection, all 18 ChassisPart items.
    - ELCL codes AP, WBRIDGE, WINGRESS, WEGRESS, XSW, TRK, DIR, MUX, AMP.

    Checked by grepping `src/main/java` and resources for each token. I didn't check the internal algorithm details (split horizon, the 20-tick Dijkstra debounce) beyond seeing that `RoutingDomain` has HELLO_TICKS=20 and an LSA max age of 60 s.

README and CHANGELOG: every feature in this area that they list is registered: Access Points, Wireless Bridges and Ports, Network Bridge across dimensions, the 13 cable colours, anchors, facades with ghost preview, Segment Isolator, Power Inlet, Capacitor Banks, Jade lanes, the Creative Capacitor Bank, span loss, amplifier, multiplexer, Exchange, Directory and Trunk Port. Nothing they mention is missing.

---

## Open questions

1. **Ad-hoc networks.** `lanes.adHocMaxDevices` (8) and the status ADHOC_OVERLOAD ("Too many devices (no controller)") exist, and `LaneSolver` handles a graph with no controller. But `ControllerStructures` only discovers graphs from a controller structure (controller blocks, a rack with controllers, a Midrange System), so it looks unreachable in play: devices without a controller are just offline. Should the docs omit ad-hoc networks, or is a path missing?
   - Related: the config comment says "devices", but the solver compares the sum of lane costs (equal with the default 1 lane each).
2. **Joining two controlled networks** by Network Bridge, lanes P2P, optical channel or wireless gives CONFLICT, by code reading (`NetworkDiscovery` adds the far controller to the graph, and `LaneSolver` sees two groups). Is that intended, and should the docs warn about it? Not verified in game.
3. **Span loss resets at any non-fiber node.** `SpanLoss.apply` treats any node that isn't Fiber Cable (or an unpowered amplifier) as the end of a run. So one Dense Cable block (32 lanes, no loss) between two fiber runs restarts the count, which makes the amplifier unnecessary. Exploit or intended? (The multiplexer line trace, `FiberTrace`, follows only Fiber Cable, so lines are stricter.)
4. **Optical channel to a controller block.** `NetworkDiscovery.walk` skips a remote target that `isController()`. A channel or Link Card network address stored by sneak-using a controller block may therefore not join. Recommend docs say "use the card on a cable or device of the network" until this is confirmed.
5. **Stale comments:**
   - Power Inlet and Capacitor Bank comments say the fill order is "controllers first, then banks", but the code fills drives, then controllers, then banks.
   - The P2P comment says "eight models" but there are 12.
   - The cable class comment mentions "devices later".
   - The docs should follow the code.
6. **Amplifier lane cost.** An Optical Amplifier takes 1 lane. If the lanes along a fiber run are exhausted, it may not get one and then counts as unpowered (no span reset) unless a multiplexer or shelf line powers it. Worth a note in the docs?
7. **Config file name.** It is registered as LOCAL (new FML) or COMMON. Confirm the actual file name the player sees (`encodedlogistics-common.toml`?) before writing it into the docs.
8. **The Relay Antenna range sphere uses the block centre and is same-dimension only.** The Handheld can still reach other dimensions only via a Wireless Controller. Fine to document as is.
