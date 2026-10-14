---
title: Developers and add-ons
section: developers
order: 0
summary: What another mod can hook into, how the Arcforge integration is built, notes for contributors on the Terminal OS's code, and how this site is generated.
---
This section is for mod developers: people writing an add-on or integration for Encoded Logistics, and people working
on the mod itself. Players and server admins don't need any of it.

> [!WARNING] No stable API
> Encoded Logistics has no published API package. The hooks on these pages are public Java classes that the mod uses
> for its own devices and integrations, so another mod can call them, but they can change in any release without
> notice. Build against a specific version, and check again when you update.

What exists, in short:

- The **standard NeoForge capabilities** on the mod's blocks and items (energy and items), which any mod can use as it
  would anyone's.
- A few **registries** the mod itself uses to add devices and integrations: new ELCL commands, batch job hosts,
  displays, a gas mod's pressurized resources, and the machine access the Small Wireless Bridge goes through.

There is no ServiceLoader, no IMC, no custom NeoForge event and no custom capability type. ELCL's trigger events
(`*RSCHANGE`, `*MCHDONE` and the rest) are internal to the Terminal OS, not NeoForge events.

## Pages

1. {{page developers/extension-points}}: every hook, with short examples.
2. {{page developers/arcforge-integration}}: how the mod's Arcforge support uses those hooks, as a worked example of an
   integration that stays optional.
3. {{page developers/elcl-internals}}: contributor notes on the Terminal OS and ELCL code - the packages, how a command
   line runs, adding a command, the services and the tests.
4. {{page developers/building-the-docs}}: how this site is generated from the code.

The mod's data components (for pack makers and add-ons) are listed on {{page reference/data-components}}, its tags on
{{page reference/tags}}.
