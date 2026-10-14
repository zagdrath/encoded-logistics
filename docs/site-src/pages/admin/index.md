---
title: Server admins
section: admin
order: 0
summary: Running Encoded Logistics on a server - its config files, who may do what, the folders it reads and writes, the limits that keep it cheap, and its one chat command.
---
Encoded Logistics runs on a dedicated server with no setup beyond installing it on both the server and the players'
games (it needs NeoForge for Minecraft 26.3). A few features that read files from the server's disk or reach out to
the web are off on dedicated servers until you turn them on. This section is for the people who run the server.

1. {{page admin/config}}: where the two config files are and the options you are most likely to change.
2. {{page admin/permissions}}: the Firewall from an admin's view, what operators can always do, and the Terminal OS's
   security levels.
3. {{page admin/folders}}: the world's `encodedlogistics` folder - library sync, CSV files, Speaker audio and Display
   Panel images.
4. {{page admin/performance}}: every budget and limit, and the options that set them.
5. {{page admin/commands}}: `/encodedlogistics caches`.

## Off on a dedicated server until you turn it on {#off-by-default}

| Feature | Option | Default |
|---|---|---|
| Library folder sync and CSV import and export | {{config elcl.allowFolderSync}} | {{default elcl.allowFolderSync}} |
| Images on Display Panels | {{config rack.allowImages}} | {{default rack.allowImages}} |
| Web audio on Speakers (and only from the hosts you list) | {{config signals.allowWebAudio}}, {{config signals.webAudioHosts}} | {{default signals.allowWebAudio}} |

`AUTO` is on in single-player and off on a dedicated server. Each player must also opt in to web audio on their own
game ({{config audio.allowWebAudio}}).

## Things worth knowing {#notes}

- The main config file is per installation and isn't sent to players: see {{page admin/config}}.
- Operators (permission level 2) are never stopped by a network's Firewall, and are security officers in the Terminal
  OS.
- Nothing loads chunks except a linked Network Bridge (off by default) and an Exchange Switch with members (on by
  default): see [Chunk loading](page:admin/performance#chunk-loading).
- Arcforge, JEI and Jade are optional; see {{page integrations/index}}.
