---
title: Rack devices
section: racks
order: 20
summary: A catalogue of all 26 rack devices - their height, what each one does, its settings panel and its config options.
---
Everything you put in a [Server Rack](page:racks/server-rack) is a rack device. This page lists them all, with what
each does and where to read more. The table gives each device's height and the type code the Terminal OS uses for it
(the first one on a network is named with the code and `01`).

{{table rack-devices}}

## The basics {#basics}

- **Height:** devices are 1 to 10U. A device needs that many free units in a row.
- **Lanes:** a device uses one lane of the network unless noted below. An [L2 or L3 Switch](#switches) in the same
  rack can pool lanes for the others, so they cost the network nothing. The rack Network Controllers use none.
- **Power:** each device drains its own FE/t from the network's energy while the network runs. The figures below are
  config options in the `rack` section (`optical` for the optical gear and chassis); see the
  [config reference](page:reference/config).
- **Panel:** click a device in the [rack screen](page:racks/server-rack#screen) for its settings panel. Changing
  settings needs **Rack access**, and some need **Build** (noted below).
- **Popup:** look at a device through the open front door for its status and figures.

## Security and power

### Firewall {#firewall}

{{item firewall}} (1U) decides who may use its network: six permissions per player, a default policy, and an owner who
always has full access. One per network; a second one shows a fault ("another Firewall is in use") and is ignored.
Drains {{config rack.firewallDrain default}} FE/t. Its panel lists the players and their permissions. It's described in
full on {{page racks/security}}.

{{recipe firewall}}

### UPS {#ups}

{{item ups}} (2U) is a battery for its network. When the network's supply falls short of what it drains, the UPS covers
the difference from its own battery in the same tick, before the network's own buffers are touched. While supply is
good it recharges from the top half of the network's buffers.

- **Modes** (a button in its panel): **Online** always conditions the supply, covering any shortfall and recharging
  all the time; **Standby** only steps in once the supply has failed altogether, and draws less. Online drains
  {{config rack.upsDrainOnline default}} FE/t, Standby {{config rack.upsDrainStandby default}}.
- **Capacity and rates:** {{config rack.upsCapacity default}} FE, giving up to {{config rack.upsMaxOutput default}} FE/t
  and recharging at up to {{config rack.upsMaxInput default}} FE/t.
- **Several UPSes** on a network add up: they discharge together and recharge in parallel.
- **Overload:** a shortfall beyond what the UPSes can give is a fault.
- **On battery:** its status LED turns amber (blinking below {{config rack.upsLowBatteryPercent default}} % charge,
  "Low battery"), its LCD scrolls "ON BATTERY" with the runtime left, and it sounds an alarm: four beeps every 30
  seconds, a rapid beep while the battery is low, and one beep when power comes back.
- **Muting:** the panel's mute button silences the alarm for the current outage only. The server option
  {{config rack.upsAlarms default}} turns UPS alarms off for everyone; the client option
  {{config audio.upsAlarmSounds default}} silences them for you alone.
- **Panel:** battery gauge, charge, load, runtime and source, the mode, a load bar, and the last three switchovers.
- **Priority:** High by default, so it keeps its lane when the rack runs short.

{{recipe ups}}

### 2U and 4U Network Controllers {#network-controllers}

{{item network_controller_2u}} (2U) and {{item network_controller_4u}} (4U) are the network's core in rack form: a rack
holding one is a lane source and the network's energy buffer, as a [Network Controller](page:networks/controllers)
structure is.

- A 2U counts as {{config rack.rackController2uEnergyBlocks default}} controller blocks for energy, receive rate and
  drain, and gives {{config rack.rackController2uLaneFaces default}} times {{config lanes.perControllerFace}} lanes.
  A 4U is twice that.
- **Redundant pair:** two of the same size on one network are active and standby. The standby keeps its own buffer
  topped up, draws {{config rack.rackControllerStandbyFactor default}} of its drain, and takes over within a second if
  the active one loses power, faults or is taken out. The panel's "Switch over" button does it on purpose (it asks you
  to confirm).
- **Panel:** energy, status, usage, devices, a lanes bar, the pair's roles and last failover, and a chip for each of
  the rack's connection points (up, down, unused or mismatch).
- They use no lanes and have High priority. Their device names are `NETWORK_CONTROLLER_2U01` and
  `NETWORK_CONTROLLER_4U01`.

Their rules (sizes that can pair, conflicts with other controllers) are on {{page networks/controllers}}.

{{recipe network_controller_2u}}

## Networking

### 24- and 48-Port L2 Switches {#switches}

{{item l2_switch_24}} and {{item l2_switch_48}} (1U each) give the other devices in their rack lanes of their own: a
pool of {{config rack.l2Switch24Lanes default}} or {{config rack.l2Switch48Lanes default}} lanes, so the network only
carries the switch's one-lane uplink. Pooled devices can also be put on other segments, as access ports or VLAN trunks.
Drains {{config rack.switchDrain default}} FE/t, High priority by default. The panel lists the rack's devices, which are
pooled and which segment each serves, and has a Ports view. See {{page networking/switches}}.

### L3 Switch {#l3-switch}

{{item l3_switch}} (1U) is an L2 Switch with a {{config rack.l3SwitchLanes default}}-lane pool that also moves items
between the segments its rack knows: up to 8 routes and 8 QoS rules, at {{config rack.l3SwitchRate default}} items a
second. Drains {{config rack.l3SwitchDrain default}} FE/t. Panel tabs: Devices, Routes, QoS. See
{{page networking/switches#l3-switch}}.

### Router {#router}

{{item router}} (1U) moves items between separate networks, however far apart and in whichever dimension, linked to it
with a Link Card. Up to 6 networks and 6 routes, each Move or Share, with filters; static or dynamic routing; ACLs.
Three Optical Transceiver cages raise its rate. Drains {{config rack.routerDrain default}} FE/t plus
{{config rack.routerDrainPerTransceiver default}} per transceiver. Panel tabs: Routes, Mode, Table, ACLs. See
{{page networking/routers}}.

{{recipe router}}

### Wireless Controller {#wireless-controller}

{{item wireless_controller}} (1U) runs its network's wireless: it links Handheld Terminals so they work anywhere, and
it links Wireless Bridges, Wireless Ports and Small Wireless Bridges as its clients, served by the network's Access
Points. Drains {{config rack.wirelessControllerDrain default}} FE/t. It faults ("No access points") when it has clients
and no Access Point is online. Panel tabs: APs and Linked. See {{page networking/wireless}}.

### Directory Server (Rack) {#directory-server}

{{item directory_server_rack}} (1U) does the [Directory Server](page:networking/exchange-and-directory#directory)
block's job in a rack: it publishes chosen devices of its network so other networks' Terminal OS can reach them as
`SYSNAME/DEVICE`, and logs lookups. Drains {{config rack.directoryDrain default}} FE/t. Panel tabs: Published, Remote,
Lookups.

### Optical Multiplexer (Rack) {#optical-multiplexer}

{{item optical_multiplexer_rack}} (1U) does the [Optical Multiplexer](page:networking/optical#multiplexer) block's job
in a rack: up to {{config optical.multiplexerChannels default}} client networks over one fibre line to its partner. Its
line is a Fiber Cable leaving the rack at any connection point. Faults when it's paired but the line is down. Drains
{{config optical.multiplexerDrain default}} FE/t. Panel tabs: Channels, Line. It's made from the block:

{{recipe optical_multiplexer_rack}}

### Optical Amplifier (Rack) {#optical-amplifier}

{{item optical_amplifier_rack}} (1U) regenerates the optical lines that pass through its rack: a multiplexer's or
shelf's line can come in at one connection point and leave at another, and its span loss starts again from zero. It
needs Fiber Cable at two or more of the rack's points; with fewer it faults ("input loss of signal"). Unpowered, the
rack passes no line through. Drains {{config optical.amplifierDrain default}} FE/t. No panel. See
{{page networking/optical#rack-amplifier}}.

{{recipe optical_amplifier_rack}}

## Crafting and compute

### Compute Server {#compute-server}

{{item compute_server}} (2U) gives {{config rack.computeServerThreads default}} crafting threads to its network's rack
Scheduler. Every Compute Server and Memory Server on the network, in any rack, pools into one Scheduler. It also runs
up to {{config elcl.computeServerJobs default}} Terminal OS batch jobs at once. Drains
{{config rack.computeServerDrain default}} FE/t. Its lights blink while the pool uses it and stay solid while it's
idle. The panel shows what it gives, what the pool uses of it, whether the Scheduler is active, and the pool's jobs with
a cancel button. See {{page crafting/schedulers}}.

{{recipe compute_server}}

### Memory Server {#memory-server}

{{item memory_server}} (1U) gives {{config rack.memoryServerMemory default}} job memory to the rack Scheduler, pooled as
above. Drains {{config rack.memoryServerDrain default}} FE/t. Same panel as the Compute Server.

{{recipe memory_server}}

### Fabrication Server {#fabrication-server}

{{item fabrication_server}} (2U) is a [Fabricator](page:crafting/fabricators-and-gateways) in a rack: nine Crafting
Schematic slots and two Throughput Module slots. Any Scheduler on its network offers it crafts, the rack Scheduler
first. Each craft costs {{config autocrafting.fabricatorEnergyPerCraft default}} FE and takes
{{config autocrafting.fabricatorCraftTicks default}} ticks with 0, 1 or 2 Throughput Modules. Drains
{{config rack.fabricationServerDrain default}} FE/t. The panel has the schematic and module slots, progress and the
current craft.

### Monitoring Server {#monitoring-server}

{{item monitoring_server}} (1U) samples its network once a second: item flow (items in or out of storage a minute),
energy use (FE/t), lane use, and crafting jobs finished a minute. It keeps a day of history, saved with the device:
a sample a second for the last 10 minutes, one every 10 seconds for the last hour, and one every 2 minutes for the last
day. Its panel graphs any of the four over 1 minute, 10 minutes, 1 hour or 1 day, with the maximum and current values.
It also keeps (unsaved) series for each machine with a Small Wireless Bridge, for [Display Panel](page:control/display-panels)
graphs. Drains {{config rack.monitoringServerDrain default}} FE/t.

{{recipe monitoring_server}}

## Storage

### NAS {#nas}

{{item nas}} (2U) holds six Storage Drives as network storage, with a priority (-999 to 999; Shift steps by 10) and an
access mode (read-write, read-only or write-only), like a Drive Bay. Drains {{config rack.nasDrain default}} FE/t plus
{{config rack.rackDriveDrain default}} per drive. See {{page storage/nas-san}}.

{{recipe nas}}

### SAN {#san}

{{item san}} (4U) holds 24 Storage Drives, reached through Optical Transceivers in its four rear uplink cages. With no
transceiver it faults ("Offline - no uplink") and its drives can't be reached. Each transceiver beyond the first adds
one to its storage priority; transceivers don't make it faster. Drains {{config rack.sanDrain default}} FE/t plus
{{config rack.rackDriveDrain}} per drive. See {{page storage/nas-san}}.

### 4U and 6U Tape Libraries {#tape-libraries}

{{item tape_library_4u}} (4U: 24 tape slots, 2 drive bays) and {{item tape_library_6u}} (6U: 48 tape slots, 4 drive bays)
hold LTO tapes as the network's cold tier, archiving items nobody has touched for a while and recalling them when
asked. They need {{item lto_tape_drive}}s in their drive bays to read and write. Drains
{{config rack.tapeLibrary4uDrain default}} or {{config rack.tapeLibrary6uDrain default}} FE/t, plus
{{config rack.tapeDriveBusyDrain default}} per busy drive and {{config rack.tapePickerDrain default}} while the picker
moves. Panel tabs: Magazine and Policy. See {{page storage/tapes}}.

## Terminals

### Rack Console, Rack Fabrication Console, Rack Schematic Console {#consoles}

{{item rack_console}}, {{item rack_fabrication_console}} and {{item rack_schematic_console}} (1U each) are terminals in
a drawer. They must be at least 8U apart. Each drains {{config rack.rackConsoleDrain default}} FE/t, plus
{{config rack.rackConsoleOpenDrain default}} while open. No panel. See {{page terminals/rack-consoles}}.

## Modular chassis {#chassis}

Three large devices are built up from cards, blades and modules you put in their bays. They drain
{{config optical.chassisDrain default}} FE/t, plus {{config optical.chassisCardDrain default}} for each part fitted.
All three are on {{page racks/chassis}}.

| Device | Height | What it is |
|---|---|---|
| {{item chassis_switch}} | 10U | An L3 Switch built from cards, with redundant supervisors and power |
| {{item blade_enclosure}} | 10U | Sixteen blade bays for compute, memory, fabrication and storage blades |
| {{item optical_transport_shelf}} | 6U | Long-haul optical transport between two shelves, with 1+1 protection |
